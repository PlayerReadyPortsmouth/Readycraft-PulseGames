package uk.co.playerready.pulsegames.games.bedwars;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.Comeback;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.Mercy;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.bedwars.shop.BedwarsShopMenu;
import uk.co.playerready.pulsegames.games.bedwars.shop.ShopBackend;
import uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue;
import uk.co.playerready.pulsegames.games.bedwars.shop.ShopRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bedwars: defend your bed, break theirs. No bed = no respawn.
 * Modes: duos, quads.
 *
 * Arena requirements: one spawn per team, regions bed1..bedN (one per team),
 * settings generator1..generatorN ("x,y,z" per team) and diamond-generators (list).
 */
public final class BedwarsGame extends MiniGame {

    private static final org.bukkit.Color[] TEAM_COLORS = {
            org.bukkit.Color.RED, org.bukkit.Color.BLUE, org.bukkit.Color.LIME, org.bukkit.Color.YELLOW,
            org.bukkit.Color.AQUA, org.bukkit.Color.FUCHSIA, org.bukkit.Color.WHITE, org.bukkit.Color.GRAY
    };

    private final Map<Integer, Boolean> bedAlive = new HashMap<>();
    private final Set<Location> playerBlocks = new HashSet<>();
    /** Team upgrade levels: effect -> team index -> level (0 = not bought). */
    private final Map<ShopCatalogue.Effect, Map<Integer, Integer>> upgradeLevels = new java.util.EnumMap<>(ShopCatalogue.Effect.class);
    /** Armour bought from the shop: worn again after every respawn. */
    private final Map<java.util.UUID, Material> shopArmour = new HashMap<>();
    private ShopCatalogue catalogue;
    /** Shop custom items (grappling hook, bridge egg, traps' milk and the rest); owns its own cleanup. */
    private final BedwarsCustomItems custom = new BedwarsCustomItems(game, this::catalogue,
            block -> bedTeamIndex(block.getLocation()) >= 0,
            block -> playerBlocks.add(block.getLocation()), block -> playerBlocks.remove(block.getLocation()), this::homeOf);
    private final Map<String, Integer> forgeEvents = new HashMap<>();
    private final BedwarsShopMenu shopMenu = new BedwarsShopMenu(game.plugin(), new Backend());
    private List<Cuboid> bedRegions = List.of();
    private int generatorTier = 1;
    /** Teams with the free comeback perk right now (no bed, outnumbered); recomputed every second. */
    private final Set<Integer> comebackTeams = new HashSet<>();
    private final uk.co.playerready.pulsegames.games.skywars.SkywarsBotBow botBow=new uk.co.playerready.pulsegames.games.skywars.SkywarsBotBow();
    private final uk.co.playerready.pulsegames.games.skywars.SkywarsBotTraversal botTraversal=new uk.co.playerready.pulsegames.games.skywars.SkywarsBotTraversal();
    private final Map<java.util.UUID,Long> botNextAction=new HashMap<>();
    private final Map<java.util.UUID,Integer> botPlaced=new HashMap<>();
    private final Map<java.util.UUID,String> botStatuses=new HashMap<>();
    private record Mining(Location block,Material type,long lastTick,double progress) {}
    private final Map<java.util.UUID,Mining> botMining=new HashMap<>();

    public BedwarsGame(GameInstance game) {
        super(game);
    }

    @Override
    public void onStart() {
        bedRegions = game.arena().regionsByPrefix("bed");
        catalogue = loadCatalogue();
        loadBotConfig();
        for (GameTeam team : game.teams()) bedAlive.put(team.index(), true);
        game.alivePlayers().forEach(this::equipBase);
        game.broadcast("Protect your bed! If it's destroyed you can't respawn.");
        game.broadcast("<gray>Generators upgrade at <yellow>5:00</yellow> and <yellow>10:00</yellow>!");
    }

    private int level(ShopCatalogue.Effect effect, int teamIndex) {
        return upgradeLevels.getOrDefault(effect, Map.of()).getOrDefault(teamIndex, 0);
    }

    private void equipBase(Player player) {
        GameTeam team = game.teamOf(player);
        int teamIndex = team != null ? team.index() : 0;
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        int sharp = level(ShopCatalogue.Effect.SHARPNESS, teamIndex);
        if (sharp > 0) sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, sharp);
        player.getInventory().setItem(0, sword);
        ItemStack shop = new ItemStack(Material.EMERALD);
        shop.editMeta(meta -> meta.displayName(Text.mm("<!i><green><b>Item Shop</b> <gray>("+uk.co.playerready.pulsegames.core.util.PlatformDetector.useVerb(player)+")")));
        player.getInventory().setItem(8, shop);
        Material bought = shopArmour.get(player.getUniqueId());
        if (bought != null) {
            wear(player, bought, teamIndex);
        } else {
            // Team-colored leather armor.
            org.bukkit.Color color = TEAM_COLORS[teamIndex % TEAM_COLORS.length];
            int prot = level(ShopCatalogue.Effect.PROTECTION, teamIndex);
            player.getInventory().setChestplate(dyed(Material.LEATHER_CHESTPLATE, color, prot));
            player.getInventory().setLeggings(dyed(Material.LEATHER_LEGGINGS, color, prot));
            player.getInventory().setBoots(dyed(Material.LEATHER_BOOTS, color, prot));
        }
        applyHaste(player, teamIndex);
    }

    private void applyHaste(Player player, int teamIndex) {
        int haste = level(ShopCatalogue.Effect.HASTE, teamIndex);
        if (haste > 0) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HASTE, org.bukkit.potion.PotionEffect.INFINITE_DURATION,
                    haste - 1, false, false));
        }
    }

    /** Puts a bought armour set on the player's body, replacing what they wore. */
    private void wear(Player player, Material chestplate, int teamIndex) {
        int prot = level(ShopCatalogue.Effect.PROTECTION, teamIndex);
        Material[] set = ShopRules.armourSet(chestplate);
        player.getInventory().setChestplate(enchantedArmour(set[0], prot));
        player.getInventory().setLeggings(enchantedArmour(set[1], prot));
        player.getInventory().setBoots(enchantedArmour(set[2], prot));
    }

    private ItemStack enchantedArmour(Material material, int protection) {
        ItemStack item = new ItemStack(material);
        if (protection > 0) item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, protection);
        return item;
    }

    /** Applies owned team upgrades to a player's current gear in place. */
    private void applyUpgrades(Player player, int teamIndex) {
        int sharp = level(ShopCatalogue.Effect.SHARPNESS, teamIndex), prot = level(ShopCatalogue.Effect.PROTECTION, teamIndex);
        if (sharp > 0) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType().name().endsWith("_SWORD")) {
                    item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, sharp);
                }
            }
        }
        if (prot > 0) {
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor != null) {
                    armor.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, prot);
                }
            }
        }
        applyHaste(player, teamIndex);
    }

    private ItemStack dyed(Material material, org.bukkit.Color color, int protection) {
        ItemStack item = new ItemStack(material);
        item.editMeta(org.bukkit.inventory.meta.LeatherArmorMeta.class, meta -> meta.setColor(color));
        if (protection > 0) item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, protection);
        return item;
    }

    /** Tiered resource generators driven by game time. */
    @Override
    public void onSecond(int gameTime) {
        if (gameTime == 300 || gameTime == 600) {
            generatorTier = gameTime == 300 ? 2 : 3;
            game.broadcast("<aqua><b>GENERATORS UPGRADED</b></aqua> <gray>to Tier " + generatorTier + "!");
        }
        int ironEvery = generatorTier >= 2 ? 1 : 2;
        int goldEvery = generatorTier >= 3 ? 3 : generatorTier == 2 ? 5 : 8;
        int diamondEvery = BedwarsTimers.diamondEvery(generatorTier);
        custom.tick(gameTime);
        refreshComeback();
        guardBeds();
        if (gameTime % ironEvery == 0) dropAtTeamGenerators(Material.IRON_INGOT);
        if (gameTime % goldEvery == 0) dropAtTeamGenerators(Material.GOLD_INGOT);
        if (gameTime % diamondEvery == 0) {
            for (String raw : game.arena().settings().getStringList("diamond-generators")) {
                Location loc = uk.co.playerready.pulsegames.core.util.LocUtil.parse(raw, game.world());
                drop(loc, Material.DIAMOND);
                game.world().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 5, 0.3, 0.5, 0.3);
            }
        }
    }

    // ---- comeback and mercy ------------------------------------------------------------

    private int aliveCount(GameTeam team) {
        return (int) team.members().stream().map(game::player).filter(p -> p != null && game.isAlive(p)).count();
    }

    /** A bedless, outnumbered team gets a free forge tier and the Comeback shop tab; it ends when that stops being true. */
    private void refreshComeback() {
        Set<Integer> now = new HashSet<>();
        if (Comeback.enabled(game.plugin().getConfig(), "bedwars")) {
            Map<Integer, Integer> alive = new HashMap<>();
            for (GameTeam team : game.teams()) alive.put(team.index(), aliveCount(team));
            now = Comeback.bedwarsTeams(bedAlive, alive);
        }
        for (GameTeam team : game.teams()) {
            boolean was = comebackTeams.contains(team.index()), is = now.contains(team.index());
            if (was == is) continue;
            String message = is ? "<green><b>COMEBACK!</b> <gray>Your forge is a tier faster and the shop has a <green>Comeback</green> tab with cheap essentials."
                    : "<gray>Comeback bonus over. Your forge is back to normal.";
            for (java.util.UUID member : team.members()) {
                Player mate = game.player(member);
                if (mate != null) mate.sendMessage(Text.msg(message));
            }
        }
        comebackTeams.clear();
        comebackTeams.addAll(now);
    }

    private boolean comebackActive(Player player) {
        GameTeam team = game.teamOf(player);
        return team != null && comebackTeams.contains(team.index());
    }

    @Override
    public String comebackLine(Player player) { return comebackActive(player) ? "Comeback: faster forge" : null; }

    @Override public int mercyHoldSeconds() { return 60; }

    /** The leading team when every other team has lost its bed and the leader has twice their combined players. */
    @Override
    public List<Player> mercyWinners() {
        GameTeam leader = null;
        int leaderAlive = 0, othersAlive = 0;
        for (GameTeam team : game.teams()) {
            int n = aliveCount(team);
            if (n > leaderAlive) { leader = team; leaderAlive = n; }
        }
        if (leader == null) return null;
        boolean othersBedless = true;
        for (GameTeam team : game.teams()) {
            if (team == leader) continue;
            othersAlive += aliveCount(team);
            if (bedAlive.getOrDefault(team.index(), false)) othersBedless = false;
        }
        double ratio = game.plugin().getConfig().getDouble("mercy.bedwars.ratio", 2.0);
        if (!Mercy.bedwars(othersBedless, leaderAlive, othersAlive, ratio)) return null;
        final GameTeam winner = leader;
        return game.alivePlayers().stream().filter(winner::contains).toList();
    }

    /** Heal pool and traps, checked once a second around each living team's bed. */
    private void guardBeds() {
        for (GameTeam team : game.aliveTeams()) {
            if (team.index() >= bedRegions.size()) continue;
            Location centre = bedRegions.get(team.index()).center(game.world());
            int heal = level(ShopCatalogue.Effect.HEAL_POOL, team.index());
            for (Player p : game.alivePlayers()) {
                if (p.getWorld() != game.world() || !game.isActive(p)) continue;
                double d = p.getLocation().distanceSquared(centre);
                if (team.contains(p)) {
                    if (heal > 0 && d <= 14 * 14) p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.REGENERATION, 60, 0, false, false));
                } else if (d <= 8 * 8 && !custom.trapImmune(p)) springTrap(team, p);
            }
        }
    }

    /** The first armed trap (alarm, slowness, mining fatigue) goes off on the intruder and is used up. */
    private void springTrap(GameTeam team, Player intruder) {
        for (ShopCatalogue.Effect trap : List.of(ShopCatalogue.Effect.TRAP_ALARM, ShopCatalogue.Effect.TRAP_SLOW, ShopCatalogue.Effect.TRAP_FATIGUE)) {
            if (level(trap, team.index()) <= 0) continue;
            upgradeLevels.get(trap).put(team.index(), 0);
            switch (trap) {
                case TRAP_ALARM -> {
                    intruder.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 200, 0, false, false));
                    // Bots on the team hear it too: they all head for the bed (BedwarsBotPlan alarm rule).
                    Location at = intruder.getLocation();
                    botTeams.computeIfAbsent(team.index(), i -> new BedwarsBotTeams.TeamState()).alarm(game.gameTime(), at.getX(), at.getY(), at.getZ());
                }
                case TRAP_SLOW -> intruder.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 160, 0));
                default -> intruder.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE, 200, 0));
            }
            for (java.util.UUID member : team.members()) {
                Player mate = game.player(member);
                if (mate == null) continue;
                mate.sendMessage(Text.msg("<red><b>TRAP!</b> <gray>Someone is sneaking up on your bed."));
                mate.playSound(mate.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1f);
            }
            intruder.sendMessage(Text.msg("<yellow>You set off a trap!"));
            return;
        }
    }

    /** Where Recall Totem takes this team: just above a bed block, or null once the bed is gone. */
    private Location homeOf(GameTeam team) {
        if (!bedAlive.getOrDefault(team.index(), false) || team.index() >= bedRegions.size()) return null;
        Cuboid region = bedRegions.get(team.index());
        if (region.volume() > 512) return null;
        for (int x = region.minX(); x <= region.maxX(); x++) for (int y = region.minY(); y <= region.maxY(); y++)
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                Block b = game.world().getBlockAt(x, y, z);
                if (b.getType().name().endsWith("_BED")) return b.getLocation().add(0.5, 1, 0.5);
            }
        return null;
    }

    private void dropAtTeamGenerators(Material material) {
        for (GameTeam team : game.aliveTeams()) {
            Location gen = game.arena().settingLocation("generator" + (team.index() + 1), game.world());
            if (gen == null) continue;
            int event = forgeEvents.merge(material + "/" + team.index(), 1, Integer::sum) - 1;
            int copies = 1 + ForgeRates.extraDrops(ForgeRates.withComeback(
                    level(ShopCatalogue.Effect.FORGE, team.index()), comebackTeams.contains(team.index())), event);
            for (int i = 0; i < copies; i++) drop(gen, material);
        }
    }

    private void drop(Location location, Material material) {
        if (location.getWorld() != game.world()) return;
        int cap = switch (material) { case IRON_INGOT -> 64; case GOLD_INGOT -> 16; case DIAMOND -> 8; default -> 64; };
        int nearby = 0;
        for (var entity : game.world().getNearbyEntities(location, 2.5, 2.0, 2.5)) {
            if (!(entity instanceof org.bukkit.entity.Item existing) || !existing.isValid()) continue;
            ItemStack stack = existing.getItemStack();
            if (stack.getType() == material) nearby += stack.getAmount();
            if (nearby >= cap) return;
        }
        var item = game.world().dropItem(location.clone().add(0, 0.5, 0), new ItemStack(material));
        item.setVelocity(new org.bukkit.util.Vector(0, 0.1, 0));
    }

    // ---- beds -----------------------------------------------------------------

    @Override
    public boolean canBreak(Player player, Block block) {
        if (block.getType().name().endsWith("_BED")) {
            int bedTeam = bedTeamIndex(block.getLocation());
            GameTeam team = game.teamOf(player);
            if (bedTeam < 0) return false;
            if (team != null && team.index() == bedTeam) {
                player.sendMessage(Text.msg("<red>You can't break your own bed!"));
                return false;
            }
            if(!game.isBot(player)) destroyBed(bedTeam, player); // NPC mining commits only after Player.breakBlock succeeds.
            return true;
        }
        return playerBlocks.contains(block.getLocation());
    }

    private int bedTeamIndex(Location location) {
        for (int i = 0; i < bedRegions.size(); i++) {
            if (bedRegions.get(i).contains(location)) return i;
        }
        return -1;
    }

    /** @param breaker null when an explosion took the bed rather than a player's pickaxe. */
    private void destroyBed(int teamIndex, Player breaker) {
        if (!bedAlive.getOrDefault(teamIndex, false)) return;
        bedAlive.put(teamIndex, false);
        GameTeam victimTeam = game.teams().stream().filter(t -> t.index() == teamIndex).findFirst().orElse(null);
        String teamName = victimTeam != null ? victimTeam.coloredName() : "A team";
        String culprit = breaker != null ? "<yellow>" + breaker.getName() + "</yellow>" : "<gray>an explosion</gray>";
        game.broadcast("<red><b>BED DESTROYED!</b></red> " + teamName + "'s</gray> bed was broken by "
                + culprit + "!");
        for (Player p : game.everyone()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1f);
        }
    }

    /**
     * Standard Bedwars: explosions (fireball, TNT) never break a bed, anyone's, including the firer's own.
     * Beds only fall to mining, which goes through the break handler and destroyBed. Explosions only
     * remove blocks players placed.
     */
    @Override
    public boolean canExplode(Block block) {
        if (block.getType().name().endsWith("_BED")) return false;
        return playerBlocks.contains(block.getLocation());
    }

    /** One bed region per team: a map with fewer beds than teams can never end. */
    @Override
    public int maxTeams() {
        return game.arena().regionsByPrefix("bed").size();
    }

    @Override
    public boolean canPlace(Player player, Block block) {
        playerBlocks.add(block.getLocation());
        return true;
    }

    // ---- respawning ------------------------------------------------------------

    @Override
    public void onDeath(Player victim, Player killer) {
        custom.playerGone(victim);
        if (killer != null) {
            game.broadcast("<red>" + victim.getName() + "</red> was killed by <yellow>" + killer.getName() + "</yellow>");
            game.stats().addKill(killer, game.type().id());
        } else {
            game.broadcast("<red>" + victim.getName() + "</red> died");
        }
        GameTeam team = game.teamOf(victim);
        boolean canRespawn = team != null && bedAlive.getOrDefault(team.index(), false);
        if (canRespawn) {
            game.respawn(victim, 5);
        } else {
            victim.sendMessage(Text.msg("<red>Your bed is gone - you're out!"));
            game.eliminate(victim);
        }
    }

    @Override
    public void onRespawn(Player player) {
        equipBase(player);
        botTraversal.remove(player); botBow.remove(player); botMining.remove(player.getUniqueId());
        bridgeAnchor.remove(player.getUniqueId()); if(botSneaking.remove(player.getUniqueId())) player.setSneaking(false);
    }

    // ---- shop custom items: engine hooks ----------------------------------------------

    @Override public void onBlockPlaced(org.bukkit.event.block.BlockPlaceEvent event) { custom.onBlockPlaced(event); }
    @Override public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) { custom.onProjectileHit(event); }
    @Override public void onMove(Player player, Location from, Location to) { custom.onMove(player, to); }
    @Override public void onDamage(Player victim, org.bukkit.event.entity.EntityDamageEvent event) { custom.onDamage(victim, event); }
    @Override public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) { custom.onEntityDamage(event); }
    @Override public void onQuit(Player player) { custom.playerGone(player); }
    @Override public void onEnd(List<Player> winners) { custom.clear(); }

    // ---- shop --------------------------------------------------------------------

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.EMERALD
                && event.getAction().isRightClick()) {
            event.setCancelled(true);
            shopMenu.open(event.getPlayer());
        } else if (custom.onInteract(event)) {
            return;
        } else if (event.getItem() != null && event.getItem().getType() == Material.FIRE_CHARGE
                && event.getAction().isRightClick()) {
            // Fireball from the shop: right-click to launch it (placed blocks go through canExplode; beds never break to an explosion).
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (!player.hasCooldown(Material.FIRE_CHARGE) && take(player, Material.FIRE_CHARGE)) {
                player.setCooldown(Material.FIRE_CHARGE, 20);
                launchFireball(player, player.getLocation().getDirection());
            }
        }
    }

    /** The server's editable copy if it parses, otherwise the one inside the jar. */
    private ShopCatalogue loadCatalogue() {
        try {
            java.io.File file = new java.io.File(game.plugin().getDataFolder(), "bedwars-shop.yml");
            if (!file.exists()) game.plugin().saveResource("bedwars-shop.yml", false);
            try (var reader = java.nio.file.Files.newBufferedReader(file.toPath())) {
                return ShopCatalogue.parse(reader);
            }
        } catch (Exception e) {
            game.plugin().getLogger().warning("bedwars-shop.yml could not be used (" + e.getMessage() + "); using the built-in shop.");
            return ShopCatalogue.loadBundled();
        }
    }

    private ShopCatalogue catalogue() {
        if (catalogue == null) catalogue = ShopCatalogue.loadBundled();
        return catalogue;
    }

    private boolean teamUpgradesOn() { return game.arena().settings().getBoolean("team-upgrades", true); }

    private final class Backend implements ShopBackend {
        @Override public ShopCatalogue catalogue() { return BedwarsGame.this.catalogue(); }
        @Override public boolean live(Player player) {
            return game.state() == uk.co.playerready.pulsegames.core.game.GameState.RUNNING && game.isAlive(player);
        }
        @Override public boolean teamUpgradesOn() { return BedwarsGame.this.teamUpgradesOn(); }
        @Override public boolean comebackShop(Player player) {
            // A server's older copied bedwars-shop.yml has no Comeback shelf: the forge perk still works, the tab stays hidden.
            return comebackActive(player) && !catalogue().inCategory(ShopCatalogue.Category.COMEBACK).isEmpty();
        }
        @Override public Material woolFor(Player player) {
            GameTeam team = game.teamOf(player);
            return team == null ? null : team.palette().wool();
        }
        @Override public int upgradeLevel(Player player, ShopCatalogue.Upgrade upgrade) {
            GameTeam team = game.teamOf(player);
            return level(upgrade.effect(), team != null ? team.index() : 0);
        }
        @Override public Result buy(Player player, ShopCatalogue.Item item) { return buyItem(player, item); }
        @Override public Result buyUpgrade(Player player, ShopCatalogue.Upgrade upgrade) { return BedwarsGame.this.buyUpgrade(player, upgrade); }
        @Override public void runLater(Runnable task) { game.runLater(1L, task); }
    }

    private java.util.Set<Material> heldMaterials(Player player) {
        java.util.Set<Material> held = new HashSet<>();
        var inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) if (item != null) held.add(item.getType());
        for (ItemStack item : inventory.getArmorContents()) if (item != null) held.add(item.getType());
        return held;
    }

    private ItemStack product(Player player, ShopCatalogue.Item entry) {
        Material material = entry.material();
        GameTeam team = game.teamOf(player);
        if (entry.teamColour() && team != null) material = team.palette().wool();
        ItemStack product = new ItemStack(material, entry.amount());
        if (entry.potion() != null && product.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta meta) {
            var type = org.bukkit.Registry.POTION_EFFECT_TYPE.get(org.bukkit.NamespacedKey.minecraft(entry.potion().effect()));
            if (type != null) meta.addCustomEffect(new org.bukkit.potion.PotionEffect(type, entry.potion().seconds() * 20, entry.potion().level() - 1), true);
            product.setItemMeta(meta);
        }
        if (entry.custom() != null) custom.decorate(product, entry);
        return product;
    }

    private ShopBackend.Result buyItem(Player player, ShopCatalogue.Item entry) {
        if (entry.category() == ShopCatalogue.Category.COMEBACK && !comebackActive(player)) {
            player.sendMessage(Text.msg("<gray>Comeback deals are only for teams fighting back without a bed."));
            return ShopBackend.Result.ALREADY_OWNED;
        }
        var inventory = player.getInventory();
        var verdict = ShopRules.verdict(catalogue(), entry, BedwarsPurchase.count(inventory, entry.currency()), heldMaterials(player));
        switch (verdict.status()) {
            case OWNED_THIS, OWNED_BETTER -> { return ShopBackend.Result.ALREADY_OWNED; }
            case NEED_MORE -> { return ShopBackend.Result.NEED_MORE; }
            default -> { }
        }
        GameTeam team = game.teamOf(player);
        int teamIndex = team != null ? team.index() : 0;
        if (entry.equip()) {
            if (!BedwarsPurchase.charge(inventory, entry.currency(), entry.cost())) return ShopBackend.Result.NEED_MORE;
            shopArmour.put(player.getUniqueId(), entry.material());
            wear(player, entry.material(), teamIndex);
        } else if (!BedwarsPurchase.buy(inventory, entry.currency(), entry.cost(), product(player, entry), catalogue().lowerTiers(entry))) {
            return ShopBackend.Result.NO_ROOM;
        }
        applyUpgrades(player, teamIndex);
        return ShopBackend.Result.OK;
    }

    private ShopBackend.Result buyUpgrade(Player player, ShopCatalogue.Upgrade upgrade) {
        GameTeam team = game.teamOf(player);
        int teamIndex = team != null ? team.index() : 0;
        int current = level(upgrade.effect(), teamIndex);
        if (current >= upgrade.maxLevel()) return ShopBackend.Result.ALREADY_OWNED;
        if (!BedwarsPurchase.charge(player.getInventory(), Material.DIAMOND, upgrade.costs().get(current))) {
            return ShopBackend.Result.NEED_MORE;
        }
        upgradeLevels.computeIfAbsent(upgrade.effect(), e -> new HashMap<>()).put(teamIndex, current + 1);
        // Apply to every online teammate immediately (without resetting their gear).
        if (team != null) {
            for (java.util.UUID member : team.members()) {
                Player teammate = game.player(member);
                if (teammate != null && game.isAlive(teammate)) applyUpgrades(teammate, teamIndex);
            }
        }
        String label = upgrade.name() + " " + ShopRules.roman(current + 1);
        if (upgrade.effect().isTrap() && team != null) {
            // Traps are a secret: only the team hears.
            for (java.util.UUID member : team.members()) {
                Player mate = game.player(member);
                if (mate != null) mate.sendMessage(Text.msg("<aqua>" + upgrade.name() + "</aqua> <gray>is set near your bed."));
            }
        } else game.broadcast(team != null
                ? team.coloredName() + "</gray> unlocked <aqua>" + label + "</aqua>!"
                : "<aqua>" + label + "</aqua> unlocked!");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.4f);
        return ShopBackend.Result.OK;
    }

    public String botStatus(Player bot) { return botStatuses.getOrDefault(bot.getUniqueId(),"Gathering resources"); }

    // ---- bots: tunables and shared team state ---------------------------------------
    private BedwarsBotConfig botCfg=BedwarsBotConfig.defaults();
    private final Map<Integer,BedwarsBotTeams.TeamState> botTeams=new HashMap<>();
    private final Map<java.util.UUID,Long> botTripUntil=new HashMap<>(),botTripNext=new HashMap<>(),
            botGatherUntil=new HashMap<>(),botGatherNext=new HashMap<>(),botTargetUntil=new HashMap<>(),botFireballReady=new HashMap<>();
    private final Map<java.util.UUID,Boolean> botCanSpend=new HashMap<>();
    private final Map<java.util.UUID,BedwarsBridge.Cell> bridgeAnchor=new HashMap<>();
    private final Set<java.util.UUID> botSneaking=new HashSet<>();
    private final Map<Integer,Block> defenceCache=new HashMap<>();
    private final Map<Integer,Long> defenceCacheUntil=new HashMap<>();

    private void loadBotConfig() {
        try { botCfg=BedwarsBotConfig.from(game.plugin().getConfig().getConfigurationSection("bedwars.bots")); }
        catch(RuntimeException unusable) { botCfg=BedwarsBotConfig.defaults(); }
    }
    private BedwarsBridge.Terrain terrain() {
        return (x,y,z)->game.world().isChunkLoaded(x>>4,z>>4) && game.world().getBlockAt(x,y,z).getType().isSolid();
    }
    private static BedwarsBridge.Cell cellOf(Location l) { return new BedwarsBridge.Cell(l.getBlockX(),(int)Math.floor(l.getY()+.01),l.getBlockZ()); }
    private void setSneak(Player bot,boolean on) {
        if(on) { if(botSneaking.add(bot.getUniqueId())) bot.setSneaking(true); }
        else if(botSneaking.remove(bot.getUniqueId())) bot.setSneaking(false);
    }

    /** Roles, threats and the shared view of our bed, refreshed once a second per team and shared by every bot on it. */
    private BedwarsBotTeams.TeamState teamState(GameTeam team,long tick) {
        var state=botTeams.computeIfAbsent(team.index(),i->new BedwarsBotTeams.TeamState());
        if(tick<state.nextRoleRefresh) return state;
        state.nextRoleRefresh=tick+20;
        List<java.util.UUID> bots=new ArrayList<>();
        for(java.util.UUID member:team.members()) { Player p=game.player(member); if(p!=null && game.isBot(p) && game.isAlive(p)) bots.add(member); }
        boolean alive=bedAlive.getOrDefault(team.index(),false);
        state.roles.clear(); state.roles.putAll(BedwarsBotTeams.assign(bots,alive,game.gameTime(),botCfg));
        state.targets.keySet().retainAll(bots);
        if(alive && team.index()<bedRegions.size()) {
            Location bed=bedRegions.get(team.index()).center(game.world()); double radius=botCfg.alertRadius();
            for(Player enemy:game.alivePlayers()) {
                if(team.contains(enemy) || enemy.isInvisible() || enemy.getWorld()!=game.world()) continue;
                if(enemy.getLocation().distanceSquared(bed)<=radius*radius) { var at=enemy.getLocation(); state.seeThreat(tick,at.getX(),at.getY(),at.getZ()); break; }
            }
        }
        return state;
    }

    public boolean botCombat(Player bot,org.bukkit.entity.LivingEntity enemy,long tick,
            uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        // Melee (strafe, aim, sprint, jump) runs in the shared core BotMelee via BotService, limited by botStrafeSafe.
        // Decoys, defenders and bugs are only ever fought in melee.
        if(!(enemy instanceof Player)) return false;
        if(botActive(bot) && useCombatItem(bot,enemy,tick,tierOf(difficulty))) return true;
        return botBow.combat(game,bot,enemy,tick,difficulty);
    }

    /**
     * Where a strafing bot may step: a full, supported, hazard-free footprint at its own level, using the same
     * island-edge rule as bridging and running, so a strafe never walks off an island.
     */
    public static java.util.function.Predicate<Location> botStrafeSafe(Location level) {
        double y=level.getY();
        return at -> { Location feet=at.clone(); feet.setY(y); return uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.safe(feet,true); };
    }

    private boolean useCombatItem(Player bot,org.bukkit.entity.LivingEntity enemy,long tick,int tier) {
        if(bot.getWorld()!=enemy.getWorld()) return false;
        var id=bot.getUniqueId(); var inv=bot.getInventory();
        GameTeam team=game.teamOf(bot);
        double distance=bot.getLocation().distance(enemy.getLocation());
        boolean safe=true;
        if(team!=null && team.index()<bedRegions.size()) safe=enemy.getLocation().distanceSquared(bedRegions.get(team.index()).center(game.world()))>8*8
                && bot.getLocation().distanceSquared(bedRegions.get(team.index()).center(game.world()))>6*6;
        var use=BedwarsBotItems.choose(new BedwarsBotItems.Facts(bot.getHealth(),distance,
                BedwarsPurchase.count(inv,Material.GOLDEN_APPLE)>0,BedwarsPurchase.count(inv,Material.ENDER_PEARL)>0,
                BedwarsPurchase.count(inv,Material.FIRE_CHARGE)>0,tick,botFireballReady.getOrDefault(id,0L),safe && bot.hasLineOfSight(enemy)),
                new BedwarsBotItems.Kit(speedPotion(inv),BedwarsPurchase.count(inv,Material.EGG)>0,
                        BedwarsPurchase.count(inv,Material.CHEST)>0),botCfg);
        switch(use) {
            case GOLDEN_APPLE -> {
                if(!take(bot,Material.GOLDEN_APPLE)) return false;
                bot.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION,100,1));
                bot.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION,2400,0));
                bot.swingMainHand(); return true;
            }
            case PEARL_ESCAPE -> {
                Location home=team==null?null:generator(team);
                if(home==null || home.getWorld()!=bot.getWorld() || !take(bot,Material.ENDER_PEARL)) return false;
                var aim=home.toVector().subtract(bot.getEyeLocation().toVector()); double flat=Math.hypot(aim.getX(),aim.getZ());
                aim.setY(Math.max(.3*flat,0)+Math.min(flat,40)*.22); if(aim.lengthSquared()<.01) return false;
                bot.launchProjectile(org.bukkit.entity.EnderPearl.class,aim.normalize().multiply(1.5)); bot.swingMainHand(); return true;
            }
            case FIREBALL -> {
                if(!take(bot,Material.FIRE_CHARGE)) return false;
                botFireballReady.put(id,tick+botCfg.fireballCooldownTicks());
                launchFireball(bot,enemy.getLocation().toVector().subtract(bot.getEyeLocation().toVector()).normalize());
                return true;
            }
            case POTION -> {
                if(!take(bot,Material.POTION)) return false;
                bot.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED,900,1));
                bot.swingMainHand(); return true;
            }
            case BRIDGE_EGG -> { return custom.botUse(bot,BedwarsCustomItems.Custom.BRIDGE_EGG); }
            case POPUP_TOWER -> { return custom.botUse(bot,BedwarsCustomItems.Custom.POPUP_TOWER); }
            default -> { return useGadget(bot,enemy,tick,tier,distance); }
        }
    }
    private static boolean take(Player player,Material material) {
        var inv=player.getInventory();
        for(int i=0;i<36;i++) { ItemStack item=inv.getItem(i); if(item!=null && item.getType()==material && item.getAmount()>0) {
            if(item.getAmount()==1) inv.setItem(i,null); else item.setAmount(item.getAmount()-1); return true; } }
        return false;
    }
    private void launchFireball(Player shooter,org.bukkit.util.Vector direction) {
        var ball=shooter.launchProjectile(org.bukkit.entity.Fireball.class,direction.multiply(.6));
        ball.setYield(1.5f); ball.setIsIncendiary(false); shooter.swingMainHand();
    }

    // ---- bots: the objective -----------------------------------------------------
    public Location botObjective(Player bot,Location combatGoal,long tick,uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        if(!botActive(bot)) return null;
        GameTeam team=game.teamOf(bot); if(team==null) return null;
        var id=bot.getUniqueId();
        var state=teamState(team,tick);
        var role=state.role(id);
        if(tick>=botNextAction.getOrDefault(id,0L)) {
            botNextAction.put(id,tick+20);
            BedwarsBotResources.collect(game,bot);
            botPurchase(bot,role,tierOf(difficulty));
            if(!botMining.containsKey(id)) uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.equip(bot.getInventory());
        }
        Location flight=botTraversal.airborne(bot,tick); if(flight!=null) return flight;
        if(!bot.isOnGround()) return null;
        Location here=bot.getLocation();
        boolean closeEnemy=combatGoal!=null && combatGoal.getWorld()==bot.getWorld() && here.distanceSquared(combatGoal)<8*8;
        var inv=bot.getInventory();
        int blocks=uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.blockSlot(inv);
        boolean alive=bedAlive.getOrDefault(team.index(),false);
        Location bedCentre=team.index()<bedRegions.size()?bedRegions.get(team.index()).center(game.world()):null;
        Location gen=generator(team);
        Block defence=alive?nextDefence(team,tick):null;
        boolean armed=hasSword(bot,Material.STONE_SWORD) || hasSword(bot,Material.IRON_SWORD) || hasSword(bot,Material.DIAMOND_SWORD);
        boolean alarm=state.alarmActive(game.gameTime());
        if(alarm) state.seeThreat(tick,state.alarmX,state.alarmY,state.alarmZ);
        var plan=new BedwarsBotPlan.Facts(role,game.gameTime(),closeEnemy,state.threatened(tick),
                bedCentre!=null && here.distanceSquared(bedCentre)<=24*24,alive,blocks>=0,armed,defence!=null,
                gen!=null && here.distanceSquared(gen)<=Math.pow(botCfg.spendRadius(),2),botCanSpend.getOrDefault(id,false),
                role==BedwarsBotTeams.Role.ATTACK && !closeEnemy && diamondTrip(bot,tick),gatherDue(bot,role,tick),alarm);
        Location goal=null;
        switch(BedwarsBotPlan.decide(plan,botCfg)) {
            case FIGHT -> { botMining.remove(id); goal=combatGoal; }
            case RESPOND_BED -> {
                botStatuses.put(id,"Defending the bed from an approaching enemy");
                goal=new Location(game.world(),state.threatX,state.threatY,state.threatZ);
            }
            case REBUILD -> {
                botStatuses.put(id,"Building a finite bed defence");
                goal=defence.getLocation().add(.5,0,.5); goal.setY(here.getY());
                int slot=bestBlockSlot(inv);
                if(slot>=0 && placeNear(bot,defence,slot,tick,botCfg.defencePlaceTicks(),botCfg.defenceBlockCap())) return null;
            }
            case HOLD_BED -> { botStatuses.put(id,"Guarding the bed"); goal=gen!=null?gen:bedCentre; }
            case SPEND -> { botStatuses.put(id,"Heading home to spend resources"); goal=gen; }
            case DIAMONDS -> {
                botStatuses.put(id,"Fetching diamonds");
                goal=nearestDiamondGenerator(here);
            }
            case ATTACK -> {
                Cuboid enemy=chooseEnemyBed(bot,team,state,tick);
                goal=enemy==null?combatGoal:enemy.center(game.world());
                if(enemy!=null) {
                    botStatuses.put(id,"Approaching a public enemy bed objective");
                    if(mineVisibleDefence(bot,enemy,tick)) return null;
                }
            }
            default -> { botStatuses.put(id,"Collecting actual generator drops"); goal=gen; }
        }
        if(goal==null) goal=gen;
        if(goal==null || goal.getWorld()!=bot.getWorld()) return null;
        if(!closeEnemy) travelGadget(bot,team,state,role,tick,tierOf(difficulty),goal);
        return moveToward(bot,goal,tick);
    }

    /** Walk several cells ahead without stopping; fall back to a height step, then to bridging. */
    private Location moveToward(Player bot,Location goal,long tick) {
        var id=bot.getUniqueId(); Location here=bot.getLocation();
        Location run=uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.run(here,goal,botCfg.lookAheadCells());
        if(run!=null) { bridgeAnchor.remove(id); setSneak(bot,false); return run; }
        var cell=cellOf(here); var anchor=bridgeAnchor.computeIfAbsent(id,k->cell);
        var next=BedwarsBridge.next(anchor,cell,cellOf(goal));
        if(next==null) { setSneak(bot,false); return null; }
        Location flat=new Location(game.world(),next.x()+.5,here.getY(),next.z()+.5);
        if(uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.corridor(here,flat)) return flat;
        Location height=botTraversal.start(game,bot,flat,tick); if(height!=null) { setSneak(bot,false); return height; }
        if(uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.safe(here,true)
                && uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.safe(flat,false)
                && Math.abs(here.getY()-Math.rint(here.getY()))<.04) {
            Block target=game.world().getBlockAt(next.x(),next.y()-1,next.z());
            int slot=uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.blockSlot(bot.getInventory());
            if(botCfg.bridgeSneak()) setSneak(bot,true);
            if(slot>=0 && placeNear(bot,target,slot,tick,botCfg.bridgePlaceTicks(),-1)) {
                botStatuses.put(id,"Bridging to the target island");
                if(uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.corridor(here,flat)) return flat;
                return botTraversal.start(game,bot,flat,tick);
            }
        }
        botStatuses.put(id,"Waiting for materials or a safe route"); return null;
    }

    private boolean botActive(Player bot) {
        return game.state()==uk.co.playerready.pulsegames.core.game.GameState.RUNNING && !game.frozen()
                && game.isBot(bot) && game.isActive(bot) && bot.getWorld()==game.world();
    }
    private Location generator(GameTeam team) {
        try { return game.arena().settingLocation("generator"+(team.index()+1),game.world()); }
        catch(IllegalArgumentException badMap) { return null; }
    }
    private Location nearestDiamondGenerator(Location from) {
        Location best=null; double distance=Double.MAX_VALUE;
        for(String raw:game.arena().settings().getStringList("diamond-generators")) {
            try {
                Location loc=uk.co.playerready.pulsegames.core.util.LocUtil.parse(raw,game.world());
                if(loc==null) continue; double d=loc.distanceSquared(from);
                if(d<distance) { best=loc; distance=d; }
            } catch(RuntimeException badLine) { /* skip a malformed line */ }
        }
        return best;
    }
    /** A mid-game diamond trip: leave once, come back after diamondTripSeconds or enough diamonds. */
    private boolean diamondTrip(Player bot,long tick) {
        var id=bot.getUniqueId(); int diamonds=BedwarsPurchase.count(bot.getInventory(),Material.DIAMOND);
        Long until=botTripUntil.get(id);
        if(until!=null) {
            if(tick<until && diamonds<botCfg.diamondsWanted()) return true;
            botTripUntil.remove(id); botTripNext.put(id,tick+botCfg.gatherIntervalSeconds()*20L); return false;
        }
        if(game.gameTime()<botCfg.midGameSeconds() || diamonds>=botCfg.diamondsWanted() || tick<botTripNext.getOrDefault(id,0L)) return false;
        if(game.arena().settings().getStringList("diamond-generators").isEmpty()) return false;
        botTripUntil.put(id,tick+botCfg.diamondTripSeconds()*20L); return true;
    }
    /** Attackers top up from the team generator on a timer when they are running low. */
    private boolean gatherDue(Player bot,BedwarsBotTeams.Role role,long tick) {
        if(role!=BedwarsBotTeams.Role.ATTACK) return false;
        var id=bot.getUniqueId(); Long until=botGatherUntil.get(id);
        if(until!=null) { if(tick<until) return true; botGatherUntil.remove(id); }
        long due=botGatherNext.computeIfAbsent(id,k->tick+botCfg.gatherIntervalSeconds()*20L);
        var inv=bot.getInventory();
        if(tick<due || BedwarsPurchase.count(inv,Material.IRON_INGOT)>=20 || BedwarsPurchase.count(inv,Material.GOLD_INGOT)>=8) return false;
        botGatherUntil.put(id,tick+botCfg.gatherHoldSeconds()*20L); botGatherNext.put(id,tick+botCfg.gatherIntervalSeconds()*20L); return true;
    }
    private boolean hasSword(Player bot,Material material) { return BedwarsPurchase.count(bot.getInventory(),material)>0; }

    // ---- bots: shopping ------------------------------------------------------------
    private BedwarsShopPlan.Loadout loadout(Player bot,BedwarsBotTeams.Role role,int tier) {
        var inv=bot.getInventory(); Map<Material,Integer> counts=new java.util.EnumMap<>(Material.class);
        for(Material m:List.of(Material.IRON_INGOT,Material.GOLD_INGOT,Material.DIAMOND,Material.GOLDEN_APPLE,Material.FIRE_CHARGE,
                Material.ENDER_PEARL,Material.BOW,Material.ARROW,Material.EGG,Material.CHEST,Material.POTION,
                Material.SHIELD,Material.HONEYCOMB,Material.SLIME_BLOCK,Material.CARVED_PUMPKIN,Material.ARMOR_STAND,Material.PACKED_ICE,
                Material.GUNPOWDER,Material.SILVERFISH_SPAWN_EGG,Material.SNOWBALL,Material.FIREWORK_ROCKET,Material.MILK_BUCKET,
                Material.TOTEM_OF_UNDYING)) counts.put(m,BedwarsPurchase.count(inv,m));
        int blocks=0;
        for(int i=0;i<36;i++) { ItemStack s=inv.getItem(i); if(s!=null && isBlockMaterial(s.getType())) blocks+=s.getAmount(); }
        GameTeam team=game.teamOf(bot); int index=team==null?0:team.index();
        Map<ShopCatalogue.Effect,Integer> levels=new java.util.EnumMap<>(ShopCatalogue.Effect.class);
        for(var effect:ShopCatalogue.Effect.values()) levels.put(effect,level(effect,index));
        return new BedwarsShopPlan.Loadout(counts,heldMaterials(bot),blocks,role,game.gameTime(),teamUpgradesOn(),levels,tier,potionEffects(inv));
    }
    private static boolean isBlockMaterial(Material m) { return blockRank(m)>0; }
    private static int blockRank(Material m) {
        if(m==Material.END_STONE) return 3;
        if(m==Material.OAK_PLANKS || m==Material.COBBLESTONE) return 2;
        return m.name().endsWith("_WOOL")?1:0;
    }
    /** Strongest block held, so a guard builds with end stone once it has some. */
    private static int bestBlockSlot(org.bukkit.inventory.PlayerInventory inv) {
        int best=-1,rank=0;
        for(int i=0;i<36;i++) { ItemStack s=inv.getItem(i); if(s!=null && s.getAmount()>0 && blockRank(s.getType())>rank) { best=i; rank=blockRank(s.getType()); } }
        return best;
    }
    private void botPurchase(Player bot,BedwarsBotTeams.Role role,int tier) {
        var loadout=loadout(bot,role,tier);
        var pick=BedwarsShopPlan.next(catalogue(),loadout);
        botCanSpend.put(bot.getUniqueId(),pick!=null);
        if(pick==null || !botActive(bot)) return;
        boolean essential=pick.item()!=null && pick.item().id().equals("wool") && loadout.blocks()<8;
        GameTeam team=game.teamOf(bot); Location gen=team==null?null:generator(team);
        boolean atBase=gen!=null && gen.getWorld()==bot.getWorld() && bot.getLocation().distanceSquared(gen)<=Math.pow(botCfg.spendRadius(),2);
        if(!essential && !atBase) return;
        if(pick.item()!=null) buyItem(bot,pick.item()); else buyUpgrade(bot,pick.upgrade());
    }

    // ---- bots: defence, placement, attack target -------------------------------------
    /** Next empty cell to cover, inner ring first, out to defence.layers rings (never below the bed). */
    private Block nextDefence(GameTeam team,long tick) {
        int index=team.index();
        Block cached=defenceCache.get(index);
        if(tick<defenceCacheUntil.getOrDefault(index,0L)) {
            if(cached==null || uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.air(cached.getType())) return cached;
        }
        Block found=findDefence(team);
        defenceCache.put(index,found); defenceCacheUntil.put(index,tick+10);
        return found;
    }
    private static final org.bukkit.block.BlockFace[] RING={org.bukkit.block.BlockFace.UP,org.bukkit.block.BlockFace.NORTH,
            org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST};
    private Block findDefence(GameTeam team) {
        if(!bedAlive.getOrDefault(team.index(),false) || team.index()>=bedRegions.size()) return null;
        Cuboid region=bedRegions.get(team.index()); if(region.volume()>64) return null;
        var world=game.world(); List<Block> frontier=new ArrayList<>(); Set<List<Integer>> seen=new HashSet<>();
        for(int x=region.minX();x<=region.maxX();x++) for(int z=region.minZ();z<=region.maxZ();z++) {
            if(!world.isChunkLoaded(x>>4,z>>4)) continue;
            for(int y=Math.max(region.minY(),world.getMinHeight()+1);y<=Math.min(region.maxY(),world.getMaxHeight()-2);y++) {
                Block bed=world.getBlockAt(x,y,z);
                if(bed.getType().name().endsWith("_BED")) { frontier.add(bed); seen.add(List.of(x,y,z)); }
            }
        }
        for(int layer=1;layer<=botCfg.defenceLayers() && !frontier.isEmpty();layer++) {
            List<Block> next=new ArrayList<>();
            for(Block from:frontier) for(var face:RING) {
                int nx=from.getX()+face.getModX(),ny=from.getY()+face.getModY(),nz=from.getZ()+face.getModZ();
                if(!world.isChunkLoaded(nx>>4,nz>>4) || ny>=world.getMaxHeight()-1 || !seen.add(List.of(nx,ny,nz))) continue;
                Block target=world.getBlockAt(nx,ny,nz);
                if(uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.air(target.getType())) {
                    var box=new org.bukkit.util.BoundingBox(nx,ny,nz,nx+1,ny+1,nz+1);
                    if(world.getNearbyEntities(box).stream().noneMatch(e->e instanceof org.bukkit.entity.LivingEntity && e.getBoundingBox().overlaps(box))) return target;
                } else if(target.getType().isSolid()) next.add(target);
            }
            frontier=next;
        }
        return null;
    }
    private final Map<java.util.UUID,Long> botNextPlace=new HashMap<>();
    /** @param cap per-bot lifetime limit for this kind of placement, or negative for none (bridging uses finite stock instead). */
    private boolean placeNear(Player bot,Block target,int slot,long tick,int gapTicks,int cap) {
        var id=bot.getUniqueId();
        if(tick<botNextPlace.getOrDefault(id,0L) || (cap>=0 && botPlaced.getOrDefault(id,0)>=cap)) return false;
        if(bot.getEyeLocation().distanceSquared(target.getLocation().add(.5,.5,.5))>4.5*4.5) return false;
        botNextPlace.put(id,tick+gapTicks);
        for(var face:List.of(org.bukkit.block.BlockFace.DOWN,org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST,org.bukkit.block.BlockFace.UP)) {
            int x=target.getX()+face.getModX(),z=target.getZ()+face.getModZ();
            if(!game.world().isChunkLoaded(x>>4,z>>4)) continue;
            Block support=game.world().getBlockAt(x,target.getY()+face.getModY(),z);
            if(!support.isPassable() && uk.co.playerready.pulsegames.games.skywars.IslandBotPlacement.place(game,bot,target,support,slot,b->canPlace(bot,b))) {
                if(cap>=0) botPlaced.merge(id,1,Integer::sum);
                return true;
            }
        }
        return false;
    }
    /** Weighted cover on the bed's open faces: wool 1, wood 2, end stone 3; zero means wide open. */
    private double defenceWeight(Cuboid region) {
        double weight=0; var world=game.world();
        for(int x=region.minX();x<=region.maxX();x++) for(int z=region.minZ();z<=region.maxZ();z++) {
            if(!world.isChunkLoaded(x>>4,z>>4)) continue;
            for(int y=Math.max(region.minY(),world.getMinHeight()+1);y<=Math.min(region.maxY(),world.getMaxHeight()-2);y++) {
                if(!world.getBlockAt(x,y,z).getType().name().endsWith("_BED")) continue;
                for(var face:RING) {
                    Material m=world.getBlockAt(x+face.getModX(),y+face.getModY(),z+face.getModZ()).getType();
                    if(m.name().endsWith("_BED") || !m.isSolid()) continue;
                    weight+=m==Material.END_STONE || m==Material.OBSIDIAN?3:m.name().endsWith("_WOOL")?1:m.name().endsWith("_PLANKS")?2:1.5;
                }
            }
        }
        return weight;
    }
    /** The enemy bed this bot goes for: least defended, shortest bridge, nearest, and not the one teammates already chose. */
    private Cuboid chooseEnemyBed(Player bot,GameTeam team,BedwarsBotTeams.TeamState state,long tick) {
        var id=bot.getUniqueId(); Integer current=state.targets.get(id);
        if(current!=null && tick<botTargetUntil.getOrDefault(id,0L) && current<bedRegions.size() && bedAlive.getOrDefault(current,false)) return bedRegions.get(current);
        Location here=bot.getLocation(); var cell=cellOf(here); List<BedwarsBotTeams.BedCandidate> options=new ArrayList<>();
        for(int i=0;i<Math.min(bedRegions.size(),64);i++) {
            if(i==team.index() || !bedAlive.getOrDefault(i,false)) continue;
            Location centre=bedRegions.get(i).center(game.world());
            int claims=0; for(var entry:state.targets.entrySet()) if(!entry.getKey().equals(id) && entry.getValue()==i) claims++;
            int gap=BedwarsBridge.gap(terrain(),cell,cellOf(centre),botCfg.bridgeMaxGap());
            options.add(new BedwarsBotTeams.BedCandidate(i,Math.sqrt(here.distanceSquared(centre)),defenceWeight(bedRegions.get(i)),gap,claims));
        }
        Integer choice=BedwarsBotTeams.chooseBed(options);
        if(choice==null) { state.targets.remove(id); return null; }
        state.targets.put(id,choice); botTargetUntil.put(id,tick+botCfg.targetReassessSeconds()*20L);
        return bedRegions.get(choice);
    }
    private boolean mineVisibleDefence(Player bot,Cuboid bed,long tick) {
        Location centre=bed.center(game.world()),eye=bot.getEyeLocation();
        var direction=centre.toVector().subtract(eye.toVector());
        if(direction.lengthSquared()<.01) return false;
        direction.normalize();
        if(!uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.loadedSegment(eye,eye.clone().add(direction.clone().multiply(4.5)))) return false;
        var ray=game.world().rayTraceBlocks(eye,direction,4.5,org.bukkit.FluidCollisionMode.NEVER,true);
        if(ray==null || ray.getHitBlock()==null) { botMining.remove(bot.getUniqueId()); return false; }
        Block block=ray.getHitBlock();
        if(block.getLocation().distanceSquared(centre)>36 || (!playerBlocks.contains(block.getLocation())
                && !(block.getType().name().endsWith("_BED") && bed.contains(block.getLocation())))) return false;
        Mining mining=botMining.get(bot.getUniqueId());
        if(mining==null || !mining.block().equals(block.getLocation()) || mining.type()!=block.getType()) {
            botMining.put(bot.getUniqueId(),new Mining(block.getLocation(),block.getType(),tick,0)); return true;
        }
        equipToolFor(bot,block.getType());
        float speed=block.getBreakSpeed(bot);
        if(!Float.isFinite(speed) || speed<=0) return true;
        bot.swingMainHand();
        double progress=mining.progress()+speed*Math.min(5,Math.max(0,tick-mining.lastTick()));
        botMining.put(bot.getUniqueId(),new Mining(block.getLocation(),block.getType(),tick,progress));
        if(progress<1) return true;
        int bedTeam=block.getType().name().endsWith("_BED")?bedTeamIndex(block.getLocation()):-1;
        boolean broken=bot.breakBlock(block);
        if(broken && bedTeam>=0 && !block.getType().name().endsWith("_BED")) destroyBed(bedTeam,bot);
        botMining.remove(bot.getUniqueId()); return true;
    }
    /** Swap the right pickaxe, axe or shears into the hotbar and hold it; keeps the current item when nothing fits. */
    private void equipToolFor(Player bot,Material block) {
        var inv=bot.getInventory(); Set<Material> held=new HashSet<>();
        for(int i=0;i<36;i++) { ItemStack s=inv.getItem(i); if(s!=null) held.add(s.getType()); }
        Material tool=BedwarsBotTools.preferred(block,held); if(tool==null) return;
        int slot=-1; for(int i=0;i<36 && slot<0;i++) { ItemStack s=inv.getItem(i); if(s!=null && s.getType()==tool) slot=i; }
        if(slot<0) return;
        if(slot>=9) { ItemStack displaced=inv.getItem(6); inv.setItem(6,inv.getItem(slot)); inv.setItem(slot,displaced); slot=6; }
        inv.setHeldItemSlot(slot);
    }

    // ---- bots: gadgets (decisions in BedwarsBotFunky, performed through BedwarsCustomItems.botUse) ----------
    private final java.util.Random botDice=new java.util.Random();
    private final Map<java.util.UUID,Long> botFunkyNext=new HashMap<>();
    private final BedwarsBotDecoys botDecoys=new BedwarsBotDecoys();

    private static int tierOf(uk.co.playerready.pulsegames.core.bot.BotDifficulty d) {
        return d==null?1:switch(d) { case EASY->0; case NORMAL->1; case HARD->2; };
    }
    private static boolean speedPotion(org.bukkit.inventory.PlayerInventory inv) {
        for(ItemStack s:inv.getContents()) if(s!=null && s.getType()==Material.POTION && s.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta m
                && m.getCustomEffects().stream().anyMatch(e->e.getType().equals(org.bukkit.potion.PotionEffectType.SPEED))) return true;
        return false;
    }
    private static Set<String> potionEffects(org.bukkit.inventory.PlayerInventory inv) {
        Set<String> names=new HashSet<>();
        for(ItemStack s:inv.getContents()) if(s!=null && s.getType()==Material.POTION && s.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta m)
            for(var e:m.getCustomEffects()) names.add(e.getType().getKey().getKey());
        return names;
    }

    /** At most one roll per few ticks per bot; the dice are thrown only when a rule's conditions are met. */
    private String rollGadget(Player bot,long tick,int tier,java.util.function.BiFunction<Set<String>,java.util.function.IntUnaryOperator,String> pick) {
        var id=bot.getUniqueId();
        if(tick<botFunkyNext.getOrDefault(id,0L)) return null;
        Set<String> ready=custom.botReady(bot); if(ready.isEmpty()) return null;
        boolean[] rolled={false};
        String choice=pick.apply(ready,n->{ rolled[0]=true; return botDice.nextInt(n); });
        if(rolled[0] || choice!=null) botFunkyNext.put(id,tick+BedwarsBotFunky.rollEveryTicks(tier));
        return choice;
    }

    @SuppressWarnings("deprecation")
    private static double maxHealth(Player bot) { return bot.getMaxHealth()<=0?20:bot.getMaxHealth(); }

    private boolean solidAt(int x,int y,int z) {
        return game.world().isChunkLoaded(x>>4,z>>4) && game.world().getBlockAt(x,y,z).getType().isSolid();
    }
    /** No floor within three blocks below this column. */
    private boolean noFloor(int x,int y,int z) { return !solidAt(x,y-1,z) && !solidAt(x,y-2,z) && !solidAt(x,y-3,z); }
    private boolean nearEdge(Location at) {
        int x=at.getBlockX(),y=at.getBlockY(),z=at.getBlockZ();
        return noFloor(x+1,y,z) || noFloor(x-1,y,z) || noFloor(x,y,z+1) || noFloor(x,y,z-1);
    }
    private boolean cornered(Location at) {
        int x=at.getBlockX(),y=at.getBlockY(),z=at.getBlockZ(),walls=0;
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) if(solidAt(x+d[0],y,z+d[1]) && solidAt(x+d[0],y+1,z+d[1])) walls++;
        return walls>=2;
    }
    private Location bedCentre(int team) {
        return team>=0 && team<bedRegions.size() && bedAlive.getOrDefault(team,false)?bedRegions.get(team).center(game.world()):null;
    }
    private Location nearestEnemyBed(Location from,GameTeam mine) {
        Location best=null; double bd=Double.MAX_VALUE;
        for(int i=0;i<Math.min(bedRegions.size(),64);i++) {
            if(i==mine.index()) continue; Location c=bedCentre(i); if(c==null) continue;
            double d=c.distanceSquared(from); if(d<bd) { bd=d; best=c; }
        }
        return best;
    }
    private double nearestEnemy(Player bot,GameTeam team) {
        double best=Double.MAX_VALUE;
        for(Player p:game.alivePlayers()) if(p.getWorld()==bot.getWorld() && !team.contains(p)) best=Math.min(best,p.getLocation().distance(bot.getLocation()));
        return best==Double.MAX_VALUE?999:best;
    }

    /** With an enemy in sight: shield, shove, throw, bug, hook. */
    private boolean useGadget(Player bot,org.bukkit.entity.LivingEntity enemy,long tick,int tier,double distance) {
        GameTeam team=game.teamOf(bot); if(team==null) return false;
        boolean edge=nearEdge(enemy.getLocation());
        // The stick is only worth holding while a shove could send them over; otherwise back to the best sword.
        if(bot.getInventory().getItemInMainHand().getType()==Material.STICK && !(edge && distance<=3.5))
            uk.co.playerready.pulsegames.core.bot.BotService.selectSword(bot);
        String pick=rollGadget(bot,tick,tier,(ready,dice)->{
            boolean theirBed=false; GameTeam theirs=enemy instanceof Player p?game.teamOf(p):null;
            if(theirs!=null) { Location c=bedCentre(theirs.index()); theirBed=c!=null && c.distanceSquared(enemy.getLocation())<=12*12; }
            Location base=generator(team);
            var facts=new BedwarsBotFunky.Facts(bot.getHealth(),maxHealth(bot),distance,edge,theirBed,cornered(bot.getLocation()),
                    nearestEnemy(bot,team),base==null||base.getWorld()!=bot.getWorld()?0:base.distance(bot.getLocation()),
                    bedAlive.getOrDefault(team.index(),false),0,false,false,false,false,false,custom.trapImmune(bot));
            return BedwarsBotFunky.combat(facts,ready,tier,dice);
        });
        if(pick==null) return false;
        var c=BedwarsCustomItems.Custom.byId(pick); if(c==null) return false;
        return custom.botUse(bot,c,c==BedwarsCustomItems.Custom.BED_BUG?enemy.getLocation():enemy.getEyeLocation(),null);
    }

    /** The ground cell on the way in to our bed from the nearest enemy, five blocks out: where traps and decoys go. */
    private Block approachGround(GameTeam team) {
        Location bed=bedCentre(team.index()); if(bed==null) return null;
        Location enemy=nearestEnemyBed(bed,team); if(enemy==null) return null;
        double dx=enemy.getX()-bed.getX(),dz=enemy.getZ()-bed.getZ();
        int sx=Math.abs(dx)>=Math.abs(dz)?(int)Math.signum(dx):0,sz=sx==0?(int)Math.signum(dz):0;
        int x=bed.getBlockX()+sx*5,z=bed.getBlockZ()+sz*5;
        if(!game.world().isChunkLoaded(x>>4,z>>4)) return null;
        for(int y=bed.getBlockY()+2;y>=bed.getBlockY()-4;y--) {
            Block b=game.world().getBlockAt(x,y,z);
            if(b.getType().isSolid() && b.getRelative(org.bukkit.block.BlockFace.UP).getType().isAir()
                    && b.getRelative(org.bukkit.block.BlockFace.UP,2).getType().isAir()) return b;
        }
        return null;
    }

    private record Gap(int length,Location landing) {}
    /** A gap straight ahead on the way to the goal (starting within two blocks, floor again beyond), else null. */
    private Gap gapAhead(Location here,Location goal) {
        double dx=goal.getX()-here.getX(),dz=goal.getZ()-here.getZ();
        if(Math.abs(dx)<1 && Math.abs(dz)<1) return null;
        int sx=Math.abs(dx)>=Math.abs(dz)?(int)Math.signum(dx):0,sz=sx==0?(int)Math.signum(dz):0;
        int y=here.getBlockY(),run=0;
        for(int i=1;i<=24;i++) {
            int x=here.getBlockX()+sx*i,z=here.getBlockZ()+sz*i;
            if(!game.world().isChunkLoaded(x>>4,z>>4)) return null;
            boolean floor=solidAt(x,y-1,z) || solidAt(x,y-2,z);
            if(!floor) run++;
            else if(run>0) return new Gap(run,new Location(game.world(),x+.5,y,z+.5));
            else if(i>=2) return null;
        }
        return null;
    }

    /** Between fights: recall, defender, bed-approach traps, raid prep, gap crossing. */
    private void travelGadget(Player bot,GameTeam team,BedwarsBotTeams.TeamState state,BedwarsBotTeams.Role role,
            long tick,int tier,Location goal) {
        Location here=bot.getLocation(),bed=bedCentre(team.index());
        Block approach=approachGround(team);
        Gap gap=goal==null||goal.getWorld()!=bot.getWorld()?null:gapAhead(here,goal);
        String pick=rollGadget(bot,tick,tier,(ready,dice)->{
            Location base=generator(team);
            boolean entering=false;
            if(role==BedwarsBotTeams.Role.ATTACK) {
                Location enemy=nearestEnemyBed(here,team);
                if(enemy!=null) { double d=enemy.distance(here); entering=d>=9 && d<=22; }
            }
            boolean atApproach=approach!=null && Math.hypot(approach.getX()+.5-here.getX(),approach.getZ()+.5-here.getZ())<=7 && Math.abs(approach.getY()-here.getY())<=3;
            var facts=new BedwarsBotFunky.Facts(bot.getHealth(),maxHealth(bot),-1,false,false,false,nearestEnemy(bot,team),
                    base==null||base.getWorld()!=bot.getWorld()?0:base.distance(here),bed!=null,gap==null?0:gap.length(),
                    bed!=null && bed.distanceSquared(here)<=8*8,state.threatened(tick) || state.alarmActive(game.gameTime()),atApproach,
                    custom.defenderActive(team.index()),entering,custom.trapImmune(bot));
            return BedwarsBotFunky.travel(facts,ready,tier,dice);
        });
        if(pick==null) return;
        if(pick.equals(BedwarsBotFunky.INVISIBILITY)) { drinkInvisibility(bot); return; }
        var c=BedwarsCustomItems.Custom.byId(pick); if(c==null) return;
        switch(c) {
            case HONEY_TRAP,BOUNCE_PAD -> { if(approach!=null) custom.botUse(bot,c,approach.getLocation().add(.5,1,.5),approach); }
            case DECOY_DUMMY -> { if(approach!=null) custom.botUse(bot,c,approach.getLocation().add(.5,1,.5),null); }
            case ICE_ROAD,GRAPPLING_HOOK -> { if(gap!=null) custom.botUse(bot,c,gap.landing(),null); }
            default -> custom.botUse(bot,c);
        }
    }

    /** Drink an Invisibility Potion: the ordinary consume event, then the potion's own effects. */
    private boolean drinkInvisibility(Player bot) {
        var inv=bot.getInventory();
        for(int i=0;i<36;i++) {
            ItemStack s=inv.getItem(i);
            if(s==null || s.getType()!=Material.POTION || !(s.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta m)) continue;
            if(m.getCustomEffects().stream().noneMatch(e->e.getType().equals(org.bukkit.potion.PotionEffectType.INVISIBILITY))) continue;
            var event=new org.bukkit.event.player.PlayerItemConsumeEvent(bot,s,org.bukkit.inventory.EquipmentSlot.HAND);
            game.plugin().getServer().getPluginManager().callEvent(event);
            if(event.isCancelled()) return false;
            if(s.getAmount()<=1) inv.setItem(i,null); else s.setAmount(s.getAmount()-1);
            for(var effect:m.getCustomEffects()) bot.addPotionEffect(effect);
            bot.swingMainHand(); return true;
        }
        return false;
    }

    /**
     * The nearest thing besides a player a bot should fight: an enemy Decoy Dummy it has fallen for (for a few seconds,
     * decided once per bot and decoy, by difficulty), or a Dream Defender or Bed Bug that is hunting it.
     */
    public org.bukkit.entity.LivingEntity botFoe(Player bot,long tick,uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        if(!botActive(bot)) return null;
        int tier=tierOf(difficulty); Location here=bot.getLocation();
        org.bukkit.entity.LivingEntity best=null; double bd=Double.MAX_VALUE;
        for(var foe:custom.foesOf(bot)) {
            var e=foe.entity(); if(e.getWorld()!=bot.getWorld() || e.isDead()) continue;
            double d=e.getLocation().distanceSquared(here);
            if(d>24*24 || d>=bd || !bot.hasLineOfSight(e)) continue;
            if(foe.decoy() && !botDecoys.attracts(bot.getUniqueId(),foe.id(),tick,tier,botDice::nextInt)) continue;
            best=e; bd=d;
        }
        return best;
    }
    @Override public void onCleanup() {
        botFunkyNext.clear(); botDecoys.forget();
        botTeams.clear(); botTripUntil.clear(); botTripNext.clear(); botGatherUntil.clear(); botGatherNext.clear();
        botTargetUntil.clear(); botFireballReady.clear(); botCanSpend.clear(); bridgeAnchor.clear(); botSneaking.clear();
        defenceCache.clear(); defenceCacheUntil.clear();
        botBow.clear(); botTraversal.clear(); botNextAction.clear(); botPlaced.clear(); botStatuses.clear();
        botMining.clear(); botNextPlace.clear(); shopArmour.clear();
        custom.clear(); forgeEvents.clear();
    }

    // ---- rules ----------------------------------------------------------------------

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>(BedwarsTimers.lines(game.gameTime(), game.teams().size()));
        for (GameTeam team : game.teams()) {
            boolean alive = team.members().stream().map(game::player)
                    .anyMatch(p -> p != null && game.isAlive(p));
            String bed = bedAlive.getOrDefault(team.index(), false) ? "<green>✔" : (alive ? "<red>✘" : "<dark_gray>✖");
            lines.add(team.coloredName() + "<gray>: " + bed);
        }
        return lines;
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public boolean fallDamage() { return true; }

    @Override
    public boolean itemDrops() { return true; }

    @Override
    public boolean itemPickup(Player player, org.bukkit.entity.Item item) { return true; }

    @Override
    public int startFreezeSeconds() { return 5; }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 1800);
    }
}
