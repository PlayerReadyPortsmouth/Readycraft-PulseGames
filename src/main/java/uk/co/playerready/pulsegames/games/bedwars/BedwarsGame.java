package uk.co.playerready.pulsegames.games.bedwars;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

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

    private static final class ShopHolder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private record ShopItem(Material material,int amount,Material currency,int cost) {
        ShopItem(Material material,Material currency,int cost) { this(material,1,currency,cost); }
        ItemStack product() { return new ItemStack(material,amount); }
    }

    private static final List<ShopItem> SHOP = List.of(
            new ShopItem(Material.WHITE_WOOL, 16, Material.IRON_INGOT, 4),
            new ShopItem(Material.OAK_PLANKS, 16, Material.GOLD_INGOT, 4),
            new ShopItem(Material.END_STONE, 12, Material.IRON_INGOT, 24),
            new ShopItem(Material.STONE_SWORD, Material.IRON_INGOT, 10),
            new ShopItem(Material.IRON_SWORD, Material.GOLD_INGOT, 7),
            new ShopItem(Material.DIAMOND_SWORD, Material.DIAMOND, 4),
            new ShopItem(Material.CHAINMAIL_CHESTPLATE, Material.IRON_INGOT, 30),
            new ShopItem(Material.IRON_CHESTPLATE, Material.GOLD_INGOT, 12),
            new ShopItem(Material.BOW, Material.GOLD_INGOT, 12),
            new ShopItem(Material.ARROW, 6, Material.GOLD_INGOT, 2),
            new ShopItem(Material.GOLDEN_APPLE, Material.GOLD_INGOT, 3),
            new ShopItem(Material.SHEARS, Material.IRON_INGOT, 20),
            new ShopItem(Material.IRON_PICKAXE, Material.IRON_INGOT, 25),
            new ShopItem(Material.TNT, Material.GOLD_INGOT, 8));

    private static final org.bukkit.Color[] TEAM_COLORS = {
            org.bukkit.Color.RED, org.bukkit.Color.BLUE, org.bukkit.Color.LIME, org.bukkit.Color.YELLOW,
            org.bukkit.Color.AQUA, org.bukkit.Color.FUCHSIA, org.bukkit.Color.WHITE, org.bukkit.Color.GRAY
    };

    private final Map<Integer, Boolean> bedAlive = new HashMap<>();
    private final Set<Location> playerBlocks = new HashSet<>();
    private final Set<Integer> sharpnessTeams = new HashSet<>();
    private final Set<Integer> protectionTeams = new HashSet<>();
    private final Set<Integer> hasteTeams = new HashSet<>();
    private List<Cuboid> bedRegions = List.of();
    private int generatorTier = 1;
    private final uk.co.playerready.pulsegames.games.skywars.SkywarsBotBow botBow=new uk.co.playerready.pulsegames.games.skywars.SkywarsBotBow();
    private final uk.co.playerready.pulsegames.games.skywars.SkywarsBotTraversal botTraversal=new uk.co.playerready.pulsegames.games.skywars.SkywarsBotTraversal();
    private final Map<java.util.UUID,Long> botNextAction=new HashMap<>();
    private final Map<java.util.UUID,Integer> botPlaced=new HashMap<>();
    private final Map<java.util.UUID,String> botStatuses=new HashMap<>();
    private final Set<java.util.UUID> botPurchaseProbes=new HashSet<>();
    private record Mining(Location block,Material type,long lastTick,double progress) {}
    private final Map<java.util.UUID,Mining> botMining=new HashMap<>();

    public BedwarsGame(GameInstance game) {
        super(game);
    }

    @Override
    public void onStart() {
        bedRegions = game.arena().regionsByPrefix("bed");
        for (GameTeam team : game.teams()) bedAlive.put(team.index(), true);
        game.alivePlayers().forEach(this::equipBase);
        game.broadcast("Protect your bed! If it's destroyed you can't respawn.");
        game.broadcast("<gray>Generators upgrade at <yellow>5:00</yellow> and <yellow>10:00</yellow>!");
    }

    private void equipBase(Player player) {
        GameTeam team = game.teamOf(player);
        int teamIndex = team != null ? team.index() : 0;
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        if (sharpnessTeams.contains(teamIndex)) {
            sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 1);
        }
        player.getInventory().setItem(0, sword);
        ItemStack shop = new ItemStack(Material.EMERALD);
        shop.editMeta(meta -> meta.displayName(Text.mm("<green><b>Item Shop</b> <gray>("+uk.co.playerready.pulsegames.core.util.PlatformDetector.useVerb(player)+")")));
        player.getInventory().setItem(8, shop);
        // Team-colored leather armor.
        org.bukkit.Color color = TEAM_COLORS[teamIndex % TEAM_COLORS.length];
        player.getInventory().setChestplate(dyed(Material.LEATHER_CHESTPLATE, color, protectionTeams.contains(teamIndex)));
        player.getInventory().setLeggings(dyed(Material.LEATHER_LEGGINGS, color, protectionTeams.contains(teamIndex)));
        player.getInventory().setBoots(dyed(Material.LEATHER_BOOTS, color, protectionTeams.contains(teamIndex)));
        if (hasteTeams.contains(teamIndex)) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HASTE, org.bukkit.potion.PotionEffect.INFINITE_DURATION,
                    0, false, false));
        }
    }

    /** Applies owned team upgrades to a player's current gear in place. */
    private void applyUpgrades(Player player, int teamIndex) {
        if (sharpnessTeams.contains(teamIndex)) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType().name().endsWith("_SWORD")) {
                    item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 1);
                }
            }
        }
        if (protectionTeams.contains(teamIndex)) {
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor != null) {
                    armor.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1);
                }
            }
        }
        if (hasteTeams.contains(teamIndex)) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HASTE, org.bukkit.potion.PotionEffect.INFINITE_DURATION,
                    0, false, false));
        }
    }

    private ItemStack dyed(Material material, org.bukkit.Color color, boolean protect) {
        ItemStack item = new ItemStack(material);
        item.editMeta(org.bukkit.inventory.meta.LeatherArmorMeta.class, meta -> meta.setColor(color));
        if (protect) item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1);
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
        int diamondEvery = generatorTier >= 3 ? 20 : generatorTier == 2 ? 25 : 30;
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
            openShop(event.getPlayer());
        }
    }

    private record TeamUpgrade(String name, Material icon, int diamondCost, Set<Integer> owners) {}

    private List<TeamUpgrade> teamUpgrades() {
        return List.of(
                new TeamUpgrade("Sharpened Swords (team)", Material.DIAMOND_SWORD, 8, sharpnessTeams),
                new TeamUpgrade("Reinforced Armor (team)", Material.DIAMOND_CHESTPLATE, 8, protectionTeams),
                new TeamUpgrade("Maniac Miner (team haste)", Material.GOLDEN_PICKAXE, 4, hasteTeams));
    }

    private void openShop(Player player) {
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm("<dark_gray>Item Shop"));
        holder.inventory = inv;
        for (int i = 0; i < SHOP.size(); i++) {
            ShopItem entry = SHOP.get(i);
            ItemStack display = entry.product().clone();
            display.editMeta(meta -> meta.lore(List.of(
                    (Component) Text.mm("<gray>Cost: <yellow>" + entry.cost() + " "
                            + entry.currency().name().toLowerCase().replace('_', ' ')))));
            inv.setItem(i, display);
        }
        GameTeam team = game.teamOf(player);
        int teamIndex = team != null ? team.index() : 0;
        List<TeamUpgrade> upgrades = teamUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            TeamUpgrade upgrade = upgrades.get(i);
            boolean owned = upgrade.owners().contains(teamIndex);
            ItemStack display = new ItemStack(owned ? Material.LIME_DYE : upgrade.icon());
            display.editMeta(meta -> {
                meta.displayName(Text.mm((owned ? "<green>" : "<aqua>") + upgrade.name()));
                meta.lore(List.of((Component) Text.mm(owned ? "<green>Purchased!"
                        : "<gray>Cost: <aqua>" + upgrade.diamondCost() + " diamonds")));
            });
            inv.setItem(18 + i, display);
        }
        player.openInventory(inv);
    }

    @Override
    public void onInventoryClick(Player player, InventoryClickEvent event) {
        if (botPurchaseProbes.contains(player.getUniqueId())) return;
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);
        int slot = event.getSlot();
        if (event.getClickedInventory() != event.getInventory() || slot < 0) return;
        if (slot >= 18 && slot < 18 + teamUpgrades().size()) {
            buyTeamUpgrade(player, teamUpgrades().get(slot - 18));
            return;
        }
        if (slot >= SHOP.size()) return;
        ShopItem entry = SHOP.get(slot);
        buyItem(player,entry);
    }

    private boolean buyItem(Player player,ShopItem entry) {
        ItemStack product=entry.product();
        if(product.getType()==Material.WHITE_WOOL) {
            GameTeam team=game.teamOf(player);
            if(team!=null) product=new ItemStack(team.palette().wool(),product.getAmount());
        }
        if(!BedwarsPurchase.buy(player.getInventory(),entry.currency(),entry.cost(),product)) {
            if(!game.isBot(player)) player.sendMessage(Text.msg("<red>Not enough currency or inventory space."));
            return false;
        }
        GameTeam team=game.teamOf(player);
        if(team!=null) applyUpgrades(player,team.index());
        player.playSound(player.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,1f,1.4f);
        return true;
    }

    private void buyTeamUpgrade(Player player, TeamUpgrade upgrade) {
        GameTeam team = game.teamOf(player);
        int teamIndex = team != null ? team.index() : 0;
        if (upgrade.owners().contains(teamIndex)) {
            player.sendMessage(Text.msg("<red>Your team already has that upgrade."));
            return;
        }
        if (!player.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), upgrade.diamondCost())) {
            player.sendMessage(Text.msg("<red>You need <aqua>" + upgrade.diamondCost() + " diamonds</aqua> for that."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, upgrade.diamondCost()));
        upgrade.owners().add(teamIndex);
        // Apply to every online teammate immediately (without resetting their gear).
        if (team != null) {
            for (java.util.UUID member : team.members()) {
                Player teammate = game.player(member);
                if (teammate != null && game.isAlive(teammate)) applyUpgrades(teammate, teamIndex);
            }
        }
        game.broadcast(team != null
                ? team.coloredName() + "</gray> unlocked <aqua>" + upgrade.name() + "</aqua>!"
                : "<aqua>" + upgrade.name() + "</aqua> unlocked!");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.4f);
        // Re-opening from inside InventoryClickEvent leaves the client with a ghost window.
        game.runLater(1L, () -> {
            if (player.isOnline() && game.isAlive(player)) openShop(player);
        });
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
        int selection=-1; var inventory=bot.getInventory();
        if(uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.blockSlot(inventory)<0) selection=0;
        else if(!hasSword(bot,Material.DIAMOND_SWORD) && BedwarsPurchase.count(inventory,Material.DIAMOND)>=4) selection=5;
        else if(!hasSword(bot,Material.IRON_SWORD) && !hasSword(bot,Material.DIAMOND_SWORD)
                && BedwarsPurchase.count(inventory,Material.GOLD_INGOT)>=7) selection=4;
        else if(!hasSword(bot,Material.STONE_SWORD) && !hasSword(bot,Material.IRON_SWORD) && !hasSword(bot,Material.DIAMOND_SWORD)) selection=3;
        else if(uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.rank(inventory.getChestplate())<3) selection=6;
        else if(uk.co.playerready.pulsegames.games.skywars.SkywarsBotInventory.rank(inventory.getChestplate())<4
                && BedwarsPurchase.count(inventory,Material.GOLD_INGOT)>=12) selection=7;
        else if(BedwarsPurchase.count(inventory,Material.BOW)==0) selection=8;
        else if(BedwarsPurchase.count(inventory,Material.ARROW)<12) selection=9;
        if(selection<0) return;
        ShopItem entry=SHOP.get(selection);
        if(BedwarsPurchase.count(inventory,entry.currency())<entry.cost()) return;
        openShop(bot);
        var view=bot.getOpenInventory();
        if(!(view.getTopInventory().getHolder() instanceof ShopHolder)) return;
        botPurchaseProbes.add(bot.getUniqueId());
        try {
            var click=new InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,selection,
                    org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
            game.plugin().getServer().getPluginManager().callEvent(click);
            if(!click.isCancelled() && botActive(bot)) buyItem(bot,entry);
        } finally { botPurchaseProbes.remove(bot.getUniqueId()); bot.closeInventory(); }
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
        botPurchaseProbes.clear(); botMining.clear(); botNextPlace.clear();
    }

    // ---- rules ----------------------------------------------------------------------

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
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
