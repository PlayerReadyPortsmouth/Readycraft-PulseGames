package uk.co.playerready.pulsegames.games.bedwars;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
    private final BedwarsShopMenu shopMenu = new BedwarsShopMenu(new Backend());
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
    }

    // ---- shop --------------------------------------------------------------------

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.EMERALD
                && event.getAction().isRightClick()) {
            event.setCancelled(true);
            shopMenu.open(event.getPlayer());
        }
    }

    @Override
    public void onInventoryClick(Player player, InventoryClickEvent event) { shopMenu.click(player, event); }

    @Override
    public void onInventoryDrag(Player player, InventoryDragEvent event) { shopMenu.drag(event); }

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
    public boolean botCombat(Player bot,org.bukkit.entity.LivingEntity enemy,long tick,
            uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) { return botBow.combat(game,bot,enemy,tick,difficulty); }

    public Location botObjective(Player bot,Location combatGoal,long tick,uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        if(!botActive(bot)) return null;
        GameTeam team=game.teamOf(bot); if(team==null) return null;
        var id=bot.getUniqueId();
        if(tick>=botNextAction.getOrDefault(id,0L)) {
            botNextAction.put(id,tick+20);
            BedwarsBotResources.collect(game,bot);
            botPurchase(bot);
            uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.equip(bot.getInventory());
        }
        Location flight=botTraversal.airborne(bot,tick); if(flight!=null) return flight;
        if(!bot.isOnGround()) return null;
        Location goal;
        boolean closeEnemy=combatGoal!=null && combatGoal.getWorld()==bot.getWorld()
                && bot.getLocation().distanceSquared(combatGoal)<8*8;
        if(closeEnemy) { botMining.remove(id); goal=combatGoal; }
        else {
            Block defence=nextDefence(team);
            int blocks=uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.blockSlot(bot.getInventory());
            if(defence!=null && blocks>=0) {
                goal=defence.getLocation().add(.5,0,.5); goal.setY(bot.getLocation().getY());
                if(placeNear(bot,defence,blocks,tick)) { botStatuses.put(id,"Building a finite bed defence"); return null; }
            } else if(blocks<0 || (!hasSword(bot,Material.STONE_SWORD) && !hasSword(bot,Material.IRON_SWORD)
                    && !hasSword(bot,Material.DIAMOND_SWORD))) {
                goal=generator(team); botStatuses.put(id,"Collecting actual generator drops");
            } else {
                Cuboid enemy=enemyBed(team,bot.getLocation());
                goal=enemy==null?combatGoal:enemy.center(game.world());
                if(enemy!=null) {
                    botStatuses.put(id,"Approaching a public enemy bed objective");
                    if(mineVisibleDefence(bot,enemy,tick)) return null;
                }
            }
        }
        if(goal==null) goal=generator(team);
        Location here=bot.getLocation(),step=uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.step(here,goal);
        if(step==null) return null;
        if(uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.corridor(here,step)) return step;
        Location height=botTraversal.start(game,bot,step,tick); if(height!=null) return height;
        if(uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.safe(here,true)
                && uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.safe(step,false)
                && Math.abs(here.getY()-Math.rint(here.getY()))<.04) {
            Block target=game.world().getBlockAt(step.getBlockX(),step.getBlockY()-1,step.getBlockZ());
            int slot=uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.blockSlot(bot.getInventory());
            if(slot>=0 && placeNear(bot,target,slot,tick)
                    && uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.corridor(here,step)) return step;
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
    private boolean hasSword(Player bot,Material material) { return BedwarsPurchase.count(bot.getInventory(),material)>0; }
    private void botPurchase(Player bot) {
        Material pick = null; var inventory = bot.getInventory();
        if (uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.blockSlot(inventory) < 0) pick = Material.WHITE_WOOL;
        else if (!hasSword(bot, Material.DIAMOND_SWORD) && BedwarsPurchase.count(inventory, Material.DIAMOND) >= 4) pick = Material.DIAMOND_SWORD;
        else if (!hasSword(bot, Material.IRON_SWORD) && !hasSword(bot, Material.DIAMOND_SWORD)
                && BedwarsPurchase.count(inventory, Material.GOLD_INGOT) >= 7) pick = Material.IRON_SWORD;
        else if (!hasSword(bot, Material.STONE_SWORD) && !hasSword(bot, Material.IRON_SWORD) && !hasSword(bot, Material.DIAMOND_SWORD)) pick = Material.STONE_SWORD;
        else if (uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.rank(inventory.getChestplate()) < 3) pick = Material.CHAINMAIL_CHESTPLATE;
        else if (uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.rank(inventory.getChestplate()) < 4
                && BedwarsPurchase.count(inventory, Material.GOLD_INGOT) >= 12) pick = Material.IRON_CHESTPLATE;
        else if (BedwarsPurchase.count(inventory, Material.BOW) == 0) pick = Material.BOW;
        else if (BedwarsPurchase.count(inventory, Material.ARROW) < 12) pick = Material.ARROW;
        if (pick == null) return;
        ShopCatalogue.Item entry = catalogue().byMaterial(pick);
        if (entry == null || BedwarsPurchase.count(inventory, entry.currency()) < entry.cost()) return;
        if (botActive(bot)) buyItem(bot, entry);
    }
    private Block nextDefence(GameTeam team) {
        if(!bedAlive.getOrDefault(team.index(),false) || team.index()>=bedRegions.size()) return null;
        Cuboid region=bedRegions.get(team.index()); if(region.volume()>64) return null;
        for(int x=region.minX();x<=region.maxX();x++) for(int z=region.minZ();z<=region.maxZ();z++) {
            if(!game.world().isChunkLoaded(x>>4,z>>4)) continue;
            for(int y=Math.max(region.minY(),game.world().getMinHeight()+1);y<=Math.min(region.maxY(),game.world().getMaxHeight()-2);y++) {
                Block bed=game.world().getBlockAt(x,y,z); if(!bed.getType().name().endsWith("_BED")) continue;
                for(var face:List.of(org.bukkit.block.BlockFace.UP,org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,
                        org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST)) {
                    int nx=x+face.getModX(),nz=z+face.getModZ(); if(!game.world().isChunkLoaded(nx>>4,nz>>4)) continue;
                    Block target=game.world().getBlockAt(nx,y+face.getModY(),nz);
                    if(uk.co.playerready.pulsegames.games.skywars.SkywarsBotTerrain.air(target.getType())) {
                        var box=new org.bukkit.util.BoundingBox(nx,y+face.getModY(),nz,nx+1,y+face.getModY()+1,nz+1);
                        if(game.world().getNearbyEntities(box).stream().noneMatch(entity->entity instanceof org.bukkit.entity.LivingEntity
                                && entity.getBoundingBox().overlaps(box))) return target;
                    }
                }
            }
        }
        return null;
    }
    private final Map<java.util.UUID,Long> botNextPlace=new HashMap<>();
    private boolean placeNear(Player bot,Block target,int slot,long tick) {
        var id=bot.getUniqueId();
        if(tick<botNextPlace.getOrDefault(id,0L) || botPlaced.getOrDefault(id,0)>=128) return false;
        if(bot.getEyeLocation().distanceSquared(target.getLocation().add(.5,.5,.5))>4.5*4.5) return false;
        botNextPlace.put(id,tick+15);
        for(var face:List.of(org.bukkit.block.BlockFace.DOWN,org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST)) {
            int x=target.getX()+face.getModX(),z=target.getZ()+face.getModZ();
            if(!game.world().isChunkLoaded(x>>4,z>>4)) continue;
            Block support=game.world().getBlockAt(x,target.getY()+face.getModY(),z);
            if(!support.isPassable() && uk.co.playerready.pulsegames.games.skywars.IslandBotPlacement.place(game,bot,target,support,slot,b->canPlace(bot,b))) {
                botPlaced.merge(id,1,Integer::sum); return true;
            }
        }
        return false;
    }
    private Cuboid enemyBed(GameTeam team,Location from) {
        Cuboid nearest=null; double distance=Double.POSITIVE_INFINITY;
        for(int i=0;i<Math.min(bedRegions.size(),64);i++) if(i!=team.index() && bedAlive.getOrDefault(i,false)) {
            double candidate=from.distanceSquared(bedRegions.get(i).center(game.world()));
            if(candidate<distance) { nearest=bedRegions.get(i); distance=candidate; }
        }
        return nearest;
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
    @Override public void onCleanup() {
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
