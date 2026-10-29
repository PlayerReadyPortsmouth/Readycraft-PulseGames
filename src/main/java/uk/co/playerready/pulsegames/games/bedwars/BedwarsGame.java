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
import uk.co.playerready.pulsegames.core.game.GameInstance;
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
    private final BedwarsShopMenu shopMenu = new BedwarsShopMenu(game.plugin(), new Backend());
    private List<Cuboid> bedRegions = List.of();
    private int generatorTier = 1;
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

    private void dropAtTeamGenerators(Material material) {
        for (GameTeam team : game.aliveTeams()) {
            Location gen = game.arena().settingLocation("generator" + (team.index() + 1), game.world());
            if (gen != null) drop(gen, material);
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
     * TNT is a shop item, so beds do get blown up. Route those through destroyBed or the
     * team keeps respawning off a bed that is no longer there.
     */
    @Override
    public boolean canExplode(Block block) {
        if (block.getType().name().endsWith("_BED")) {
            int bedTeam = bedTeamIndex(block.getLocation());
            if (bedTeam < 0) return false;
            destroyBed(bedTeam, null);
            return true;
        }
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

    // ---- shop --------------------------------------------------------------------

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.EMERALD
                && event.getAction().isRightClick()) {
            event.setCancelled(true);
            shopMenu.open(event.getPlayer());
        } else if (event.getItem() != null && event.getItem().getType() == Material.FIRE_CHARGE
                && event.getAction().isRightClick()) {
            // Fireball from the shop: right-click to launch it (blocks and beds still go through canExplode).
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
        return product;
    }

    private ShopBackend.Result buyItem(Player player, ShopCatalogue.Item entry) {
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
        game.broadcast(team != null
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
        // TODO(BotMelee): once core/bot/BotMelee is wired by the releaser, melee for this branch moves there; item use stays here.
        if(botActive(bot) && useCombatItem(bot,enemy,tick)) return true;
        return botBow.combat(game,bot,enemy,tick,difficulty);
    }

    private boolean useCombatItem(Player bot,org.bukkit.entity.LivingEntity enemy,long tick) {
        if(bot.getWorld()!=enemy.getWorld()) return false;
        var id=bot.getUniqueId(); var inv=bot.getInventory();
        GameTeam team=game.teamOf(bot);
        double distance=bot.getLocation().distance(enemy.getLocation());
        boolean safe=true;
        if(team!=null && team.index()<bedRegions.size()) safe=enemy.getLocation().distanceSquared(bedRegions.get(team.index()).center(game.world()))>8*8
                && bot.getLocation().distanceSquared(bedRegions.get(team.index()).center(game.world()))>6*6;
        var use=BedwarsBotItems.choose(new BedwarsBotItems.Facts(bot.getHealth(),distance,
                BedwarsPurchase.count(inv,Material.GOLDEN_APPLE)>0,BedwarsPurchase.count(inv,Material.ENDER_PEARL)>0,
                BedwarsPurchase.count(inv,Material.FIRE_CHARGE)>0,tick,botFireballReady.getOrDefault(id,0L),safe && bot.hasLineOfSight(enemy)),botCfg);
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
            default -> { return false; }
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
            botPurchase(bot,role);
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
        var plan=new BedwarsBotPlan.Facts(role,game.gameTime(),closeEnemy,state.threatened(tick),
                bedCentre!=null && here.distanceSquared(bedCentre)<=24*24,alive,blocks>=0,armed,defence!=null,
                gen!=null && here.distanceSquared(gen)<=Math.pow(botCfg.spendRadius(),2),botCanSpend.getOrDefault(id,false),
                role==BedwarsBotTeams.Role.ATTACK && !closeEnemy && diamondTrip(bot,tick),gatherDue(bot,role,tick));
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
    private BedwarsShopPlan.Loadout loadout(Player bot,BedwarsBotTeams.Role role) {
        var inv=bot.getInventory(); Map<Material,Integer> counts=new java.util.EnumMap<>(Material.class);
        for(Material m:List.of(Material.IRON_INGOT,Material.GOLD_INGOT,Material.DIAMOND,Material.GOLDEN_APPLE,Material.FIRE_CHARGE,
                Material.ENDER_PEARL,Material.BOW,Material.ARROW)) counts.put(m,BedwarsPurchase.count(inv,m));
        int blocks=0;
        for(int i=0;i<36;i++) { ItemStack s=inv.getItem(i); if(s!=null && isBlockMaterial(s.getType())) blocks+=s.getAmount(); }
        GameTeam team=game.teamOf(bot); int index=team==null?0:team.index();
        Map<ShopCatalogue.Effect,Integer> levels=new java.util.EnumMap<>(ShopCatalogue.Effect.class);
        for(var effect:ShopCatalogue.Effect.values()) levels.put(effect,level(effect,index));
        return new BedwarsShopPlan.Loadout(counts,heldMaterials(bot),blocks,role,game.gameTime(),teamUpgradesOn(),levels);
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
    private void botPurchase(Player bot,BedwarsBotTeams.Role role) {
        var loadout=loadout(bot,role);
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
    @Override public void onCleanup() {
        botTeams.clear(); botTripUntil.clear(); botTripNext.clear(); botGatherUntil.clear(); botGatherNext.clear();
        botTargetUntil.clear(); botFireballReady.clear(); botCanSpend.clear(); bridgeAnchor.clear(); botSneaking.clear();
        defenceCache.clear(); defenceCacheUntil.clear();
        botBow.clear(); botTraversal.clear(); botNextAction.clear(); botPlaced.clear(); botStatuses.clear();
        botMining.clear(); botNextPlace.clear(); shopArmour.clear();
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
