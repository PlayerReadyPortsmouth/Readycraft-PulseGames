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

    private final Set<Location> filledChests = new HashSet<>();
    private final Set<Location> playerBlocks = new HashSet<>();
    private final java.util.Map<java.util.UUID, Set<Location>> botLooted = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> botNextLoot = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> botNextBridge = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> botBridged = new java.util.HashMap<>();
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
        int items = random.nextInt(4, 7);
        for (int i = 0; i < items; i++) {
            int slot = random.nextInt(chest.getBlockInventory().getSize());
            Loot loot=pool.get(random.nextInt(pool.size()));
            chest.getBlockInventory().setItem(slot,new ItemStack(loot.material(),loot.amount()));
        }
    }

    public String botStatus(Player bot) { return botStatuses.getOrDefault(bot.getUniqueId(), "Preparing to loot"); }

    /** Called after ordinary visible-enemy selection; null means stop, never use an unvalidated fallback. */
    public Location botObjective(Player bot, Location proposedGoal, long tick,
            uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        if (game.state() != uk.co.playerready.pulsegames.core.game.GameState.RUNNING || game.frozen()
                || !game.isActive(bot) || !game.isBot(bot) || bot.getWorld() != game.world()) return null;
        var id = bot.getUniqueId();
        if (tick >= botNextLoot.getOrDefault(id,0L)) {
            botNextLoot.put(id,tick+40);
            lootNearby(bot);
            SkywarsBotInventory.equip(bot.getInventory());
        }
        if (!bot.isOnGround()) { botStatuses.put(id,"Waiting for safe footing"); return null; }
        Location here=bot.getLocation();
        Location step=SkywarsBotTerrain.step(here,proposedGoal);
        if (step==null) { botStatuses.put(id,"No nearby objective"); return null; }
        if (SkywarsBotTerrain.corridor(here,step)) { botStatuses.put(id,"Walking on supported terrain"); return step; }
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
        if(slot>=9) {
            ItemStack displaced=bot.getInventory().getItem(8);
            bot.getInventory().setItem(8,bot.getInventory().getItem(slot)); bot.getInventory().setItem(slot,displaced); slot=8;
        }
        bot.getInventory().setHeldItemSlot(slot);
        ItemStack stack=bot.getInventory().getItem(slot), held=stack.clone();
        var before=target.getState();
        if(!canPlace(bot,target)) return false;
        target.setType(stack.getType(),false);
        var place=new org.bukkit.event.block.BlockPlaceEvent(target,before,support,held,bot,true,org.bukkit.inventory.EquipmentSlot.HAND);
        boolean accepted=false;
        try {
            game.plugin().getServer().getPluginManager().callEvent(place);
            ItemStack remaining=bot.getInventory().getItem(slot);
            if(place.isCancelled() || !place.canBuild() || !game.isActive(bot) || game.frozen()
                    || remaining==null || !remaining.isSimilar(held) || remaining.getAmount()!=held.getAmount()
                    || bot.getInventory().getHeldItemSlot()!=slot || bot.getWorld()!=game.world()
                    || game.state()!=uk.co.playerready.pulsegames.core.game.GameState.RUNNING
                    || target.getType()!=held.getType()) return false;
            if(remaining.getAmount()==1) bot.getInventory().setItem(slot,null);
            else { ItemStack reduced=remaining.clone(); reduced.setAmount(remaining.getAmount()-1); bot.getInventory().setItem(slot,reduced); }
            botBridged.merge(id,1,Integer::sum); bot.swingMainHand(); accepted=true; return true;
        } finally {
            if(!accepted) { before.update(true,false); botStatuses.put(id,"Bridge placement denied"); }
            SkywarsBotInventory.equip(bot.getInventory());
        }
    }

    @Override public void onCleanup() {
        botLooted.clear(); botNextLoot.clear(); botNextBridge.clear(); botBridged.clear(); botStatuses.clear();
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
