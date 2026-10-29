package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Skywars: loot your island, bridge out, knock everyone else into the void.
 * Modes: solo, doubles, insane (OP loot).
 * Chests are filled lazily (random loot) the first time each one is opened.
 */
public final class SkywarsGame extends MiniGame {

    private record Loot(Material material, int amount) { Loot(Material material) { this(material,1); } }
    private static final List<Loot> BASIC_LOOT = List.of(
            new Loot(Material.STONE_SWORD), new Loot(Material.IRON_SWORD),
            new Loot(Material.BOW), new Loot(Material.ARROW, 8),
            new Loot(Material.IRON_HELMET), new Loot(Material.IRON_CHESTPLATE),
            new Loot(Material.IRON_LEGGINGS), new Loot(Material.IRON_BOOTS),
            new Loot(Material.CHAINMAIL_CHESTPLATE), new Loot(Material.LEATHER_BOOTS),
            new Loot(Material.OAK_PLANKS, 32), new Loot(Material.COBBLESTONE, 32),
            new Loot(Material.GOLDEN_APPLE, 2), new Loot(Material.COOKED_BEEF, 6),
            new Loot(Material.STONE_AXE), new Loot(Material.IRON_PICKAXE),
            new Loot(Material.SNOWBALL, 8), new Loot(Material.ENDER_PEARL, 1),
            new Loot(Material.WATER_BUCKET), new Loot(Material.FISHING_ROD));

    private static final List<Loot> INSANE_LOOT = List.of(
            new Loot(Material.DIAMOND_SWORD), new Loot(Material.DIAMOND_CHESTPLATE),
            new Loot(Material.DIAMOND_HELMET), new Loot(Material.DIAMOND_LEGGINGS),
            new Loot(Material.DIAMOND_BOOTS), new Loot(Material.ENDER_PEARL, 2),
            new Loot(Material.GOLDEN_APPLE, 4), new Loot(Material.BOW),
            new Loot(Material.ARROW, 24), new Loot(Material.OAK_PLANKS, 64),
            new Loot(Material.TNT, 4), new Loot(Material.LAVA_BUCKET));

    private final SkywarsBotTraversal botTraversal = new SkywarsBotTraversal();
    private final SkywarsBotBow botBow = new SkywarsBotBow();
    private final Set<Location> filledChests = new HashSet<>();
    private final Set<Location> playerBlocks = new HashSet<>();
    private final java.util.Map<java.util.UUID, Set<Location>> botLooted = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> botNextLoot = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> botNextBridge = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> botBridged = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, List<Block>> botChests = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> botNextScan = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Location> botChestAt = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> botChestSince = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Set<Location>> botGaveUp = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> botNextPearl = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, String> botStatuses = new java.util.HashMap<>();

    public SkywarsGame(GameInstance game) {
        super(game);
    }

    private boolean isInsane() { return game.mode().rulesId().equals("insane"); }

    @Override
    public void onStart() {
        game.broadcast("Loot up and fight! Last " + (game.mode().isTeams() ? "team" : "player")
                + " standing wins!");
    }

    private int refillAt() {
        return game.arena().settings().getInt("refill-seconds", 300);
    }

    @Override
    public void onSecond(int gameTime) {
        if (gameTime == refillAt() - 60) {
            game.broadcast("<yellow>Chests refill in <gold>60s</gold>!");
        } else if (gameTime == refillAt()) {
            filledChests.clear();
            botLooted.clear();
            game.broadcast("<gold><b>CHESTS REFILLED!</b></gold> <gray>Open them again for fresh loot.");
            for (org.bukkit.entity.Player p : game.alivePlayers()) {
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1.2f);
            }
        }
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        if (event.getClickedBlock().getState() instanceof Chest chest) {
            Location key = event.getClickedBlock().getLocation();
            if (filledChests.add(key)) {
                fillChest(chest);
            }
        }
    }

    private void fillChest(Chest chest) {
        var random = ThreadLocalRandom.current();
        List<Loot> pool = isInsane() ? INSANE_LOOT : BASIC_LOOT;
        chest.getBlockInventory().clear();
        int items = Math.min(random.nextInt(4, 7), chest.getBlockInventory().getSize());
        List<Integer> slots = new java.util.ArrayList<>();
        for (int slot = 0; slot < chest.getBlockInventory().getSize(); slot++) slots.add(slot);
        java.util.Collections.shuffle(slots, random);
        for (int i = 0; i < items; i++) {
            int slot = slots.get(i);
            Loot loot=pool.get(random.nextInt(pool.size()));
            chest.getBlockInventory().setItem(slot,new ItemStack(loot.material(),loot.amount()));
        }
    }

    public boolean botCombat(Player bot,org.bukkit.entity.LivingEntity enemy,long tick,
            uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        if (pearlEscape(bot,enemy,tick,difficulty)) return true;
        return botBow.combat(game,bot,enemy,tick,difficulty);
    }

    /** Hurt with an enemy on top of us: throw a real pearl at our own spawn island (never at the enemy). */
    private boolean pearlEscape(Player bot,org.bukkit.entity.LivingEntity enemy,long tick,
            uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        var id=bot.getUniqueId();
        int slot=bot.getInventory().first(Material.ENDER_PEARL);
        double health=Math.min(1,bot.getHealth()/Math.max(1,bot.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
        if (slot<0 || tick<botNextPearl.getOrDefault(id,0L) || bot.getWorld()!=enemy.getWorld()
                || !SkywarsTactics.shouldPearlEscape(health,bot.getLocation().distance(enemy.getLocation()),true,difficulty)) return false;
        Location home=game.spawnFor(bot);
        if (home==null || home.getWorld()!=bot.getWorld() || home.distanceSquared(bot.getLocation())<8*8
                || home.distanceSquared(bot.getLocation())>40*40) return false;
        botNextPearl.put(id,tick+200);
        var aim=home.toVector().add(new org.bukkit.util.Vector(0,1,0)).subtract(bot.getEyeLocation().toVector());
        aim.setY(aim.getY()+Math.sqrt(aim.lengthSquared())*.06);
        var pearl=bot.launchProjectile(org.bukkit.entity.EnderPearl.class,aim.normalize().multiply(1.5));
        if (!pearl.isValid()) return false;
        var stack=bot.getInventory().getItem(slot);
        if (stack!=null) { if (stack.getAmount()<=1) bot.getInventory().setItem(slot,null); else stack.setAmount(stack.getAmount()-1); }
        bot.swingMainHand();
        botStatuses.put(id,"Pearling back to the home island");
        return true;
    }

    public String botStatus(Player bot) { return botStatuses.getOrDefault(bot.getUniqueId(), "Preparing to loot"); }

    /** Called after ordinary visible-enemy selection; null means stop, never use an unvalidated fallback. */
    public Location botObjective(Player bot, Location proposedGoal, long tick,
            uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        return botObjective(bot, proposedGoal, tick, difficulty, true);
    }

    /** {@code engaged} is true while a visible opponent is being fought; otherwise the bot plans its own errand. */
    public Location botObjective(Player bot, Location proposedGoal, long tick,
            uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty, boolean engaged) {
        if (game.state() != uk.co.playerready.pulsegames.core.game.GameState.RUNNING || game.frozen()
                || !game.isActive(bot) || !game.isBot(bot) || bot.getWorld() != game.world()) return null;
        var id = bot.getUniqueId();
        if (tick >= botNextLoot.getOrDefault(id,0L)) {
            botNextLoot.put(id,tick+40);
            lootNearby(bot);
            SkywarsBotInventory.equip(bot.getInventory());
        }
        Location airborne=botTraversal.airborne(bot,tick);
        if(airborne!=null) return airborne;
        if (!bot.isOnGround()) { botStatuses.put(id,"Waiting for safe footing"); return null; }
        Location here=bot.getLocation();
        if (!engaged) proposedGoal=errand(bot,here,tick);
        Location step=SkywarsBotTerrain.step(here,proposedGoal);
        if (step==null) { botStatuses.put(id,"No nearby objective"); return null; }
        if (SkywarsBotTerrain.corridor(here,step)) { botStatuses.put(id,"Walking on supported terrain"); return step; }
        Location raised=botTraversal.start(game,bot,step,tick);
        if(raised!=null) { botStatuses.put(id,"Traversing a checked adjacent step"); return raised; }
        Location around=sidestep(here,step,tick,id);
        if(around!=null) { botStatuses.put(id,"Going round an obstacle"); return around; }
        if (!SkywarsBotTerrain.safe(here,true) || !SkywarsBotTerrain.safe(step,false)) {
            botStatuses.put(id,"Route obstructed or unsafe"); return null;
        }
        if (tick < botNextBridge.getOrDefault(id,0L)) return null;
        botNextBridge.put(id,tick+Math.max(10,difficulty.meleeDelayTicks()));
        if (placeBridge(bot,here,step) && SkywarsBotTerrain.corridor(here,step)) {
            botStatuses.put(id,"Bridging with inventory blocks"); return step;
        }
        return null;
    }

    private final uk.co.playerready.pulsegames.core.bot.BotSidestep botSidestep=new uk.co.playerready.pulsegames.core.bot.BotSidestep();
    /** A solid block (not a gap) is ahead: step to a walkable side cell instead of standing at it. */
    private Location sidestep(Location here,Location step,long tick,java.util.UUID id) {
        int fx=(int)Math.signum(step.getBlockX()-here.getBlockX()), fz=(int)Math.signum(step.getBlockZ()-here.getBlockZ());
        if(fx==0 && fz==0) return null;
        var ahead=game.world().getBlockAt(step.getBlockX(),here.getBlockY(),step.getBlockZ());
        if(ahead.isPassable() && game.world().getBlockAt(step.getBlockX(),here.getBlockY()+1,step.getBlockZ()).isPassable()) return null;
        var pick=botSidestep.choose(id,fx,fz,tick,(dx,dz)->
                SkywarsBotTerrain.corridor(here,new Location(game.world(),here.getBlockX()+dx+.5,here.getY(),here.getBlockZ()+dz+.5)));
        return pick==null?null:new Location(game.world(),here.getBlockX()+pick.dx()+.5,here.getY(),here.getBlockZ()+pick.dz()+.5);
    }

    /** Nothing in view: loot the nearest chest, then bridge to the best-kitted player, and never idle on spawn. */
    private Location errand(Player bot,Location here,long tick) {
        var id=bot.getUniqueId();
        Set<Location> visited=botLooted.computeIfAbsent(id,ignored->new HashSet<>());
        List<Block> chests=botChests.computeIfAbsent(id,ignored->new java.util.ArrayList<>());
        if (tick>=botNextScan.getOrDefault(id,0L)) {
            botNextScan.put(id,tick+100);
            chests.clear();
            int cx=here.getBlockX()>>4,cz=here.getBlockZ()>>4;
            for(int x=cx-2;x<=cx+2;x++) for(int z=cz-2;z<=cz+2;z++) {
                if(!game.world().isChunkLoaded(x,z)) continue;
                for(var state:game.world().getChunkAt(x,z).getTileEntities(b->b.getType()==Material.CHEST || b.getType()==Material.TRAPPED_CHEST,false))
                    chests.add(state.getBlock());
            }
        }
        double[][] spots=new double[chests.size()][];
        boolean[] looted=new boolean[spots.length];
        for(int i=0;i<spots.length;i++) {
            Location at=chests.get(i).getLocation();
            spots[i]=new double[]{at.getX()+.5,at.getY(),at.getZ()+.5};
            looted[i]=visited.contains(at) || botGaveUp.getOrDefault(id,Set.of()).contains(at);
        }
        int chest=SkywarsTactics.nearestChest(here.getX(),here.getY(),here.getZ(),spots,looted);
        boolean canBridge=SkywarsBotInventory.blockSlot(bot.getInventory())>=0;
        if (SkywarsBotOrder.next(chest>=0,canBridge,true)==SkywarsBotOrder.Step.LOOT) {
            Location at=chests.get(chest).getLocation();
            Long since=botChestSince.get(id);
            if (since==null || !at.equals(botChestAt.get(id))) { botChestAt.put(id,at); botChestSince.put(id,tick); }
            else if (tick-since>400) { botGaveUp.computeIfAbsent(id,ignored->new HashSet<>()).add(at); botChestSince.remove(id); }
            botStatuses.put(id,"Heading for a chest");
            return new Location(game.world(),spots[chest][0],here.getY(),spots[chest][2]);
        }
        List<Player> others=game.alivePlayers().stream().filter(p->!p.equals(bot) && game.isActive(p) && p.getWorld()==bot.getWorld()
                && (game.teamOf(bot)==null || !game.teamOf(bot).contains(p))).toList();
        double[][] players=new double[others.size()][];
        for(int i=0;i<players.length;i++) {
            Location at=others.get(i).getLocation();
            var inv=others.get(i).getInventory();
            int gear=SkywarsBotInventory.rank(inv.getHelmet())+SkywarsBotInventory.rank(inv.getChestplate())
                    +SkywarsBotInventory.rank(inv.getLeggings())+SkywarsBotInventory.rank(inv.getBoots())
                    +SkywarsBotInventory.rank(inv.getItemInMainHand());
            players[i]=new double[]{at.getX(),at.getZ(),gear};
        }
        int target=SkywarsTactics.richestTarget(here.getX(),here.getZ(),players);
        if (SkywarsBotOrder.next(false,canBridge,target>=0)==SkywarsBotOrder.Step.BRIDGE) {
            botStatuses.put(id,"Heading for a rival");
            return others.get(target).getLocation();
        }
        double[] offset=SkywarsTactics.wanderOffset(bound->ThreadLocalRandom.current().nextInt(bound));
        botStatuses.put(id,"Pacing the island");
        return here.clone().add(offset[0],0,offset[1]);
    }

    private void lootNearby(Player bot) {
        Location here=bot.getLocation(), eye=bot.getEyeLocation();
        Set<Location> visited=botLooted.computeIfAbsent(bot.getUniqueId(),ignored->new HashSet<>());
        if (visited.size() >= 512) return;
        Block nearest=null; double distance=Double.POSITIVE_INFINITY;
        for(int x=here.getBlockX()-3;x<=here.getBlockX()+3;x++)
            for(int z=here.getBlockZ()-3;z<=here.getBlockZ()+3;z++) {
                if(!game.world().isChunkLoaded(x>>4,z>>4)) continue;
                for(int y=Math.max(game.world().getMinHeight(),here.getBlockY()-1);
                        y<=Math.min(game.world().getMaxHeight()-1,here.getBlockY()+1);y++) {
                    Block block=game.world().getBlockAt(x,y,z);
                    if((block.getType()!=Material.CHEST && block.getType()!=Material.TRAPPED_CHEST) || visited.contains(block.getLocation())) continue;
                    Location centre=block.getLocation().add(.5,.5,.5);
                    double squared=eye.distanceSquared(centre);
                    if(squared>4.5*4.5 || squared>=distance) continue;
                    if(!SkywarsBotTerrain.loadedSegment(eye,centre)) continue;
                    var direction=centre.toVector().subtract(eye.toVector());
                    var hit=game.world().rayTraceBlocks(eye,direction.normalize(),Math.sqrt(squared)+.05,
                            org.bukkit.FluidCollisionMode.NEVER,true);
                    if(hit==null || !block.equals(hit.getHitBlock())) continue;
                    nearest=block; distance=squared;
                }
            }
        if(nearest==null) return;
        var interact=new PlayerInteractEvent(bot,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                bot.getInventory().getItemInMainHand(),nearest,org.bukkit.block.BlockFace.UP,org.bukkit.inventory.EquipmentSlot.HAND);
        game.plugin().getServer().getPluginManager().callEvent(interact);
        if(interact.useInteractedBlock()==org.bukkit.event.Event.Result.DENY || !game.isActive(bot) || game.frozen()) return;
        // The normal listener performs lazy filling; this is idempotent if no listener consumed the event.
        onInteract(interact);
        if(nearest.getState() instanceof Chest chest) {
            int moved=lootChest(bot,chest,()->game.state()==uk.co.playerready.pulsegames.core.game.GameState.RUNNING
                    && game.isActive(bot) && !game.frozen() && bot.getWorld()==game.world());
            if (moved >= 0) visited.add(nearest.getLocation());
            botStatuses.put(bot.getUniqueId(),moved < 0 ? "Chest opening denied" : "Looted "+moved+" items from a visible chest");
        }
    }

    static int lootChest(Player bot, Chest chest) { return lootChest(bot,chest,()->true); }
    private static int lootChest(Player bot, Chest chest, java.util.function.BooleanSupplier stillPlaying) {
        var inventory=chest.getBlockInventory();
        // Paper fires InventoryOpenEvent here, allowing normal container protection to veto access.
        var view=bot.openInventory(inventory);
        if(view==null) return -1;
        try {
            if(!stillPlaying.getAsBoolean() || bot.isDead() || bot.getWorld()!=chest.getWorld()
                    || !inventory.equals(view.getTopInventory())) return -1;
            return SkywarsBotInventory.transfer(inventory,bot.getInventory(),slot->{
                if(inventory.getItem(slot)==null) return false;
                var click=new org.bukkit.event.inventory.InventoryClickEvent(view,
                        org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,slot,
                        org.bukkit.event.inventory.ClickType.SHIFT_LEFT,
                        org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY);
                bot.getServer().getPluginManager().callEvent(click);
                return !click.isCancelled() && click.getResult()!=org.bukkit.event.Event.Result.DENY
                        && stillPlaying.getAsBoolean() && !bot.isDead() && bot.getWorld()==chest.getWorld();
            });
        } finally { bot.closeInventory(); }
    }

    boolean placeBridge(Player bot,Location here,Location step) {
        var id=bot.getUniqueId();
        int limit=Math.max(0,Math.min(128,game.arena().settings().getInt("bot-bridge-limit",64)));
        if(botBridged.getOrDefault(id,0)>=limit) { botStatuses.put(id,"Map bridge budget exhausted"); return false; }
        if(Math.abs(here.getY()-Math.rint(here.getY()))>.04 || here.distanceSquared(step)>2.6) {
            botStatuses.put(id,"Cannot bridge from this height"); return false;
        }
        Block support=game.world().getBlockAt(here.getBlockX(),here.getBlockY()-1,here.getBlockZ());
        Block target=game.world().getBlockAt(step.getBlockX(),step.getBlockY()-1,step.getBlockZ());
        if(Math.abs(target.getX()-support.getX())+Math.abs(target.getZ()-support.getZ())!=1
                || target.getY()!=support.getY() || !SkywarsBotTerrain.air(target.getType()) || support.isPassable()
                || bot.getEyeLocation().distanceSquared(target.getLocation().add(.5,.5,.5))>4.5*4.5) {
            botStatuses.put(id,"Bridge requires an adjacent clear floor cell"); return false;
        }
        int slot=SkywarsBotInventory.blockSlot(bot.getInventory());
        if(slot<0) { botStatuses.put(id,"No bridge blocks: needs planks or cobblestone from loot"); return false; }
        boolean placed=IslandBotPlacement.place(game,bot,target,support,slot,block->canPlace(bot,block));
        if(placed) botBridged.merge(id,1,Integer::sum);
        else botStatuses.put(id,"Bridge placement denied");
        return placed;
    }

    @Override public void onCleanup() {
        botTraversal.clear(); botSidestep.clear(); botBow.clear(); botLooted.clear(); botNextLoot.clear(); botNextBridge.clear(); botBridged.clear(); botStatuses.clear();
        botChests.clear(); botNextScan.clear(); botChestAt.clear(); botChestSince.clear(); botGaveUp.clear(); botNextPearl.clear();
    }

    @Override
    public boolean canBreak(Player player, Block block) { return true; }

    @Override
    public boolean canPlace(Player player, Block block) {
        playerBlocks.add(block.getLocation());
        return true;
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public boolean fallDamage() { return true; }

    @Override
    public boolean hunger() { return true; }

    @Override
    public boolean itemDrops() { return true; }

    @Override
    public boolean itemPickup(Player player, org.bukkit.entity.Item item) { return true; }

    @Override
    public void onDeath(Player victim, Player killer) {
        // Drop their loot for the killer.
        for (ItemStack item : victim.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                game.world().dropItemNaturally(victim.getLocation(), item);
            }
        }
        super.onDeath(victim, killer);
    }

    @Override
    public int startFreezeSeconds() { return 5; }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 900);
    }
}
