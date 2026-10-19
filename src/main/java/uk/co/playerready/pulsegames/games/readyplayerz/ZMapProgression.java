package uk.co.playerready.pulsegames.games.readyplayerz;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.LocUtil;
import uk.co.playerready.pulsegames.core.util.Text;

/** Map-authored, team-wide doors and fixed-price wall purchases. Never touches a template world. */
public final class ZMapProgression implements org.bukkit.event.Listener {
    /** {@code anchor} is optional: without one the door is bought by sneaking next to its region. */
    public record Door(int id, Location anchor, Cuboid blocks, int cost, int round, String name) {}
    public record Wall(int id, Location anchor, Offering type, int cost, int round) {}
    public record Definition(List<Door> doors, List<Wall> walls, Map<Integer,Integer> gates) {
        public Definition { doors=List.copyOf(doors); walls=List.copyOf(walls); gates=Map.copyOf(gates); }
    }
    public enum Offering {
        STONE_SWORD(Material.STONE_SWORD), IRON_SWORD(Material.IRON_SWORD), DIAMOND_SWORD(Material.DIAMOND_SWORD),
        NETHERITE_SWORD(Material.NETHERITE_SWORD), BOW(Material.BOW), CROSSBOW(Material.CROSSBOW), TRIDENT(Material.TRIDENT),
        ARROWS(Material.ARROW), HEAL(null), SPEED(null), STRENGTH(null), RESISTANCE(null);
        final Material material;
        Offering(Material material) { this.material=material; }
    }
    private final GameInstance game;
    private final Definition definition;
    private final Set<Integer> opened=new HashSet<>();
    private final Map<Block,BlockData> originals=new LinkedHashMap<>();
    static final double PROMPT_RANGE=3;
    private final Map<Integer,org.bukkit.entity.TextDisplay> holograms=new HashMap<>();
    private int round;
    private boolean registered;
    public ZMapProgression(GameInstance game) {
        this.game=game;
        definition=parse(game.arena().settings().getValues(true),game.arena().regions());
    }
    public void setRound(int round) { this.round=Math.max(0,round); }
    public boolean spawnUnlocked(int index) { return !definition.gates().containsKey(index)||opened.contains(definition.gates().get(index)); }
    public static String validationError(Arena arena) {
        try { parse(arena.settings().getValues(true),arena.regions()); return null; }
        catch(IllegalArgumentException ex) { return ex.getMessage(); }
    }
    public static void validate(Map<String,Object> settings, Map<String,Cuboid> regions) { parse(settings,regions); }
    public static Definition parse(Map<String,Object> settings, Map<String,Cuboid> regions) {
        Set<Integer> doorIds=new TreeSet<>(), wallIds=new TreeSet<>();
        for(String key:settings.keySet()) {
            if(key.startsWith("z-door")||key.startsWith("z-wall")) {
                var m=java.util.regex.Pattern.compile("z-(door|wall)([1-9][0-9]*)-(anchor|cost|round|type|name)").matcher(key);
                if(!m.matches())throw invalid("Unknown progression setting: "+key);
                int id=number(m.group(2),key,1,32);
                if(m.group(1).equals("door")) {
                    if(id>16||m.group(3).equals("type"))throw invalid("Invalid door setting: "+key);
                    doorIds.add(id);
                } else wallIds.add(id);
            }
        }
        for(String key:regions.keySet()) if(key.startsWith("z-door")) {
            if(!key.matches("z-door[1-9][0-9]*"))throw invalid("Invalid door region: "+key);
            doorIds.add(number(key.substring(6),key,1,16));
        }
        boolean gatesPresent=settings.keySet().stream().anyMatch(k->k.matches("mobspawn.*-door"));
        if(doorIds.isEmpty()&&wallIds.isEmpty()&&!gatesPresent)return new Definition(List.of(),List.of(),Map.of());
        Cuboid bounds=regions.get("bounds");
        if(bounds==null)throw invalid("ReadyPlayerZ doors/wall buys require an arena bounds region.");
        Set<String> anchors=new HashSet<>(); List<Door> doors=new ArrayList<>(); List<Wall> walls=new ArrayList<>();
        consecutive(doorIds,"doors"); consecutive(wallIds,"wall buys");
        for(int id:doorIds) {
            String key="z-door"+id; Cuboid box=regions.get(key);
            if(box==null||box.volume()>512||!inside(bounds,box))throw invalid(key+" must be a region of at most 512 blocks inside bounds.");
            for(Door other:doors)if(overlap(box,other.blocks()))throw invalid("Door regions overlap.");
            doors.add(new Door(id,settings.containsKey(key+"-anchor")?anchor(settings,key,bounds,anchors):null,box,
                    number(settings.get(key+"-cost"),key+"-cost",1,100000),number(settings.getOrDefault(key+"-round",1),key+"-round",1,50),doorName(settings,key,id)));
        }
        for(int id:wallIds) {
            String key="z-wall"+id; Offering type;
            try { type=Offering.valueOf(Objects.toString(settings.get(key+"-type"),"").toUpperCase(Locale.ROOT).replace('-','_')); }
            catch(IllegalArgumentException ex) { throw invalid(key+"-type must be a supported weapon, arrows, heal, speed, strength or resistance."); }
            walls.add(new Wall(id,anchor(settings,key,bounds,anchors),type,
                    number(settings.get(key+"-cost"),key+"-cost",1,100000),number(settings.getOrDefault(key+"-round",1),key+"-round",1,50)));
        }
        for(Door door:doors)for(Wall wall:walls)if(door.blocks().contains(wall.anchor()))throw invalid("Wall-buy anchors cannot be inside removable doors.");
        for(Door door:doors)for(Door other:doors)if(door.id()!=other.id()&&other.anchor()!=null&&door.blocks().contains(other.anchor()))throw invalid("Door anchor would be removed by another door.");
        Map<Integer,Integer> gates=new HashMap<>();
        for(String key:settings.keySet())if(key.matches("mobspawn.*-door")) {
            if(!key.matches("mobspawn[1-9][0-9]*-door"))throw invalid("Invalid spawn gate: "+key);
            int index=number(key.substring(8,key.length()-5),key,1,128);
            if(!regions.containsKey("mobspawn"+index))throw invalid("Spawn gate references missing mobspawn"+index);
            int door=number(settings.get(key),key,1,16);
            if(!doorIds.contains(door))throw invalid(key+" references an undefined door.");
            gates.put(index,door);
        }
        long spawns=regions.keySet().stream().filter(k->k.matches("mobspawn[1-9][0-9]*")).count();
        if(spawns==0||gates.size()>=spawns)throw invalid("At least one mobspawn region must be available before any doors open.");
        return new Definition(doors,walls,gates);
    }
    private static String doorName(Map<String,Object> settings,String key,int id) {
        String name=Objects.toString(settings.get(key+"-name"),"").replaceAll("[<>\\p{Cntrl}]","").trim();
        if(name.length()>24)throw invalid(key+"-name must be at most 24 characters.");
        return name.isEmpty()?"Door "+id:name;
    }
    private static Location anchor(Map<String,Object> settings,String key,Cuboid bounds,Set<String> used) {
        Location loc;
        try { loc=LocUtil.parse(Objects.toString(settings.get(key+"-anchor"),""),null); }
        catch(RuntimeException ex) { throw invalid(key+"-anchor needs finite x,y,z coordinates."); }
        if(Math.abs(loc.getX())>29999984||Math.abs(loc.getZ())>29999984||!bounds.contains(loc))throw invalid(key+" anchor is outside bounds.");
        String cell=loc.getBlockX()+","+loc.getBlockY()+","+loc.getBlockZ();
        if(!used.add(cell))throw invalid("Duplicate purchase anchor: "+cell);
        return new Location(null,loc.getBlockX(),loc.getBlockY(),loc.getBlockZ());
    }
    private static int number(Object raw,String label,int min,int max) {
        try { int n=Integer.parseInt(Objects.toString(raw,""));if(n<min||n>max)throw new NumberFormatException();return n; }
        catch(RuntimeException ex){throw invalid(label+" must be a whole number from "+min+" to "+max+".");}
    }
    private static void consecutive(Set<Integer> ids,String label) {int next=1;for(int n:ids)if(n!=next++)throw invalid("Number "+label+" consecutively from 1.");}
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
    private static boolean inside(Cuboid a,Cuboid b){return b.minX()>=a.minX()&&b.maxX()<=a.maxX()&&b.minY()>=a.minY()&&b.maxY()<=a.maxY()&&b.minZ()>=a.minZ()&&b.maxZ()<=a.maxZ();}
    private static boolean overlap(Cuboid a,Cuboid b){return a.minX()<=b.maxX()&&a.maxX()>=b.minX()&&a.minY()<=b.maxY()&&a.maxY()>=b.minY()&&a.minZ()<=b.maxZ()&&a.maxZ()>=b.minZ();}

    public boolean onInteract(PlayerInteractEvent event) {
        if(event.getAction()!=Action.RIGHT_CLICK_BLOCK||event.getClickedBlock()==null)return false;
        Block clicked=event.getClickedBlock();
        Door door=definition.doors().stream().filter(d->sameCell(d.anchor(),clicked)).findFirst().orElse(null);
        Wall wall=definition.walls().stream().filter(w->sameCell(w.anchor(),clicked)).findFirst().orElse(null);
        if(door==null&&wall==null) {
            if(clicked.getWorld()==game.world() && closedDoorCell(clicked)) {
                event.setCancelled(true);
                return true;
            }
            return false;
        }
        event.setCancelled(true);
        Player player=event.getPlayer();
        if(event.getHand()!=EquipmentSlot.HAND||game.state()!=GameState.RUNNING||!game.isActive(player)
                ||player.getGameMode()==org.bukkit.GameMode.SPECTATOR||player.getWorld()!=game.world()||clicked.getWorld()!=game.world()
                ||player.getLocation().distanceSquared(clicked.getLocation().add(.5,.5,.5))>16)return true;
        if(!ownedWorld()) { message(player,"This purchase is unavailable outside the match instance.");return true; }
        if(door!=null){purchaseDoor(player,door);return true;}
        if(round<wall.round()){message(player,"Unlocks in round "+wall.round()+".");return true;}
        if(game.score(player)<wall.cost()){message(player,"Requires "+wall.cost()+" points.");return true;}
        if(!apply(player,wall.type())){message(player,"Purchase unavailable: check space, health, effects or door blocks.");return true;}
        game.addScore(player,-wall.cost());
        message(player,"Bought "+wall.type().name().toLowerCase(Locale.ROOT).replace('_',' ')+" for "+wall.cost()+" points.");
        return true;
    }
    /** Shared door purchase: round, points and open-state checks; the team door opens at most once. */
    private boolean purchaseDoor(Player player,Door door) {
        if(!ownedWorld()){message(player,"This purchase is unavailable outside the match instance.");return false;}
        if(opened.contains(door.id())){message(player,"That team door is already open.");return false;}
        if(round<door.round()){message(player,"Unlocks in round "+door.round()+".");return false;}
        if(game.score(player)<door.cost()){message(player,"Requires "+door.cost()+" points.");return false;}
        if(!openDoor(door)){message(player,"Purchase unavailable: check door blocks.");return false;}
        game.addScore(player,-door.cost());
        removeHologram(door.id());
        game.broadcast("<green>"+door.name()+" opened!</green>");
        return true;
    }
    private boolean canAct(Player player) {
        return game.state()==GameState.RUNNING&&game.isActive(player)&&player.getGameMode()!=org.bukkit.GameMode.SPECTATOR
                &&player.getWorld()==game.world()&&game.world()!=null;
    }
    /** Distance from the player's feet to the nearest point of the door region. */
    static double distance(Location at,Cuboid box) {
        double dx=Math.max(Math.max(box.minX()-at.getX(),0),at.getX()-(box.maxX()+1));
        double dy=Math.max(Math.max(box.minY()-at.getY(),0),at.getY()-(box.maxY()+1));
        double dz=Math.max(Math.max(box.minZ()-at.getZ(),0),at.getZ()-(box.maxZ()+1));
        return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
    /** Closest still-closed door within {@code range} blocks, or null. */
    public Door nearestClosedDoor(Player player,double range) {
        Door best=null; double bestDistance=range;
        for(Door door:definition.doors()) {
            if(opened.contains(door.id()))continue;
            double d=distance(player.getLocation(),door.blocks());
            if(d<=bestDistance){best=door;bestDistance=d;}
        }
        return best;
    }
    public boolean nearDoorPrompt(Player player) { return !definition.doors().isEmpty()&&nearestClosedDoor(player,PROMPT_RANGE+1)!=null; }
    public int doorCount() { return definition.doors().size(); }
    public int doorsOpened() { return opened.size(); }
    /** Cheapest closed door whose unlock round has been reached, or null. */
    public Door cheapestAvailable() {
        return definition.doors().stream().filter(d->!opened.contains(d.id())&&round>=d.round()).min(Comparator.comparingInt(Door::cost)).orElse(null);
    }
    /** Cheapest closed door overall, for the sidebar. */
    public Door nextDoor() {
        return definition.doors().stream().filter(d->!opened.contains(d.id())).min(Comparator.comparingInt(Door::cost)).orElse(null);
    }
    /** Where a bot should walk to reach the door: its anchor, else the middle of its region. */
    public Location approach(Door door) {
        if(anchor(door)!=null)return anchor(door).add(.5,0,.5);
        Cuboid b=door.blocks(); return new Location(game.world(),(b.minX()+b.maxX()+1)/2.0,b.minY(),(b.minZ()+b.maxZ()+1)/2.0);
    }
    private Location anchor(Door door) { return door.anchor()==null?null:new Location(game.world(),door.anchor().getX(),door.anchor().getY(),door.anchor().getZ()); }
    /** Sneak-to-buy and bot entry point; the player must be within {@link #PROMPT_RANGE} of the door. */
    public boolean buyDoor(Player player,Door door) {
        if(!canAct(player)||opened.contains(door.id())||distance(player.getLocation(),door.blocks())>PROMPT_RANGE)return false;
        return purchaseDoor(player,door);
    }
    @org.bukkit.event.EventHandler(ignoreCancelled=true)
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        Player player=event.getPlayer();
        if(!event.isSneaking()||!canAct(player))return;
        Door door=nearestClosedDoor(player,PROMPT_RANGE);
        if(door!=null)purchaseDoor(player,door);
    }

    // ---- holograms and prompts -------------------------------------------------
    /** Floating price tags, one per closed door. */
    public void spawnHolograms() {
        if(!ownedWorld())return;
        for(Door door:definition.doors()) {
            if(opened.contains(door.id())||holograms.containsKey(door.id()))continue;
            Cuboid b=door.blocks();
            Location at=new Location(game.world(),(b.minX()+b.maxX()+1)/2.0,Math.min(b.maxY()+1.0,b.minY()+2.5),(b.minZ()+b.maxZ()+1)/2.0);
            org.bukkit.entity.TextDisplay display=game.world().spawn(at,org.bukkit.entity.TextDisplay.class,d->{
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15,15));
                d.setPersistent(false);
                d.text(hologramText(door,0));
            });
            if(display!=null)holograms.put(door.id(),display);
        }
    }
    private net.kyori.adventure.text.Component hologramText(Door door,int bestScore) {
        String colour=round<door.round()?"red":bestScore>=door.cost()?"green":"gold";
        String status=round<door.round()?"<red>Locked until round "+door.round():"<gray>Unlocked";
        return Text.mm("<white><b>"+door.name()+"</b><newline><"+colour+">"+door.cost()+" pts<newline>"+status);
    }
    private void removeHologram(int id) {
        var display=holograms.remove(id);
        if(display!=null)display.remove();
    }
    /** Once a second: refresh tag colours (green when anyone on the team can afford it) and action-bar prompts. */
    public void tick() {
        if(definition.doors().isEmpty())return;
        List<Player> squad=game.alivePlayers().stream().filter(game::isActive).toList();
        int best=squad.stream().mapToInt(game::score).max().orElse(0);
        for(Door door:definition.doors()) {
            var display=holograms.get(door.id());
            if(display!=null)display.text(hologramText(door,best));
        }
        for(Player player:squad) {
            if(game.isBot(player))continue;
            Door door=nearestClosedDoor(player,PROMPT_RANGE);
            if(door==null)continue;
            player.sendActionBar(Text.mm(round<door.round()?"<red>"+door.name()+": locked until round "+door.round()
                    :game.score(player)<door.cost()?"<yellow>"+door.name()+": need "+door.cost()+" pts"
                    :"<green>Sneak to open – "+door.cost()+" pts"));
        }
    }
    private boolean ownedWorld(){return game.world()!=null&&game.world().getName().equals(game.plugin().getConfig().getString("instances.world-prefix","pulse_")+game.id());}
    private static boolean sameCell(Location loc,Block block){return loc.getBlockX()==block.getX()&&loc.getBlockY()==block.getY()&&loc.getBlockZ()==block.getZ();}
    private static void message(Player player,String text){player.sendMessage(Text.msg("<yellow>"+text));}
    /** Physical check after the cloned world is loaded; read-only until listener registration. */
    public String startProblem() {
        if(definition.doors().isEmpty())return null;
        if(!ownedWorld())return "Door progression requires the owned match instance world.";
        for(Door door:definition.doors()) if(doorSnapshot(door)==null)
            return "Door "+door.id()+" must contain loaded inert barrier blocks only (no doors, gates, redstone, containers or liquids).";
        if(!registered) {
            game.plugin().getServer().getPluginManager().registerEvents(this,game.plugin());
            registered=true;
        }
        return null;
    }
    /** No gravity, burnable or redstone-controlled barriers: these could bypass a paid gate. */
    static boolean inert(Material material) {
        String name=material.name();
        return Set.of("STONE","SMOOTH_STONE","STONE_BRICKS","MOSSY_STONE_BRICKS","CRACKED_STONE_BRICKS",
                "CHISELED_STONE_BRICKS","COBBLESTONE","MOSSY_COBBLESTONE","DEEPSLATE","COBBLED_DEEPSLATE",
                "POLISHED_DEEPSLATE","DEEPSLATE_BRICKS","DEEPSLATE_TILES","POLISHED_ANDESITE","ANDESITE",
                "POLISHED_DIORITE","DIORITE","POLISHED_GRANITE","GRANITE","BRICKS","NETHER_BRICKS",
                "RED_NETHER_BRICKS","END_STONE","END_STONE_BRICKS","OBSIDIAN","CRYING_OBSIDIAN","BEDROCK",
                "BARRIER","IRON_BLOCK","GOLD_BLOCK","DIAMOND_BLOCK","NETHERITE_BLOCK","QUARTZ_BLOCK",
                "SMOOTH_QUARTZ","PRISMARINE","DARK_PRISMARINE","PRISMARINE_BRICKS","GLASS","TERRACOTTA")
                .contains(name) || name.matches("(WHITE|ORANGE|MAGENTA|LIGHT_BLUE|YELLOW|LIME|PINK|GRAY|LIGHT_GRAY|CYAN|PURPLE|BLUE|BROWN|GREEN|RED|BLACK)_(CONCRETE|TERRACOTTA|GLAZED_TERRACOTTA|STAINED_GLASS)");
    }
    private boolean closedDoorCell(Block block) {
        return definition.doors().stream().anyMatch(d->!opened.contains(d.id())&&d.blocks().contains(block.getLocation()));
    }
    private boolean pistonBlocked(org.bukkit.block.Block piston,List<Block> blocks,org.bukkit.block.BlockFace direction) {
        if(piston.getWorld()!=game.world()||!ownedWorld())return false;
        return blocks.stream().anyMatch(block->closedDoorCell(block)||closedDoorCell(block.getRelative(direction))
                ||closedDoorCell(block.getRelative(direction.getOppositeFace())));
    }
    @org.bukkit.event.EventHandler(ignoreCancelled=true)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        if(pistonBlocked(event.getBlock(),event.getBlocks(),event.getDirection()))event.setCancelled(true);
    }
    @org.bukkit.event.EventHandler(ignoreCancelled=true)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        if(pistonBlocked(event.getBlock(),event.getBlocks(),event.getDirection()))event.setCancelled(true);
    }
    private Map<Block,BlockData> doorSnapshot(Door door) {
        World world=game.world(); Cuboid box=door.blocks();
        if(box.minY()<world.getMinHeight()||box.maxY()>=world.getMaxHeight())return null;
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++)if(!world.isChunkLoaded(x,z))return null;
        Map<Block,BlockData> changed=new LinkedHashMap<>();
        for(Block block:box.blocks(world)) {
            if(!world.getWorldBorder().isInside(block.getLocation())||block.getState() instanceof TileState||block.isLiquid())return null;
            if(!block.isEmpty()) {
                if(!inert(block.getType()))return null;
                changed.put(block,block.getBlockData().clone());
            }
        }
        return changed.isEmpty()?null:changed;
    }
    private boolean openDoor(Door door) {
        Map<Block,BlockData> changed=doorSnapshot(door);
        if(changed==null)return false;
        try { for(Block block:changed.keySet())block.setType(Material.AIR,false); }
        catch(RuntimeException failure){changed.forEach((block,data)->block.setBlockData(data,false));throw failure;}
        originals.putAll(changed);opened.add(door.id());return true;
    }
    private boolean apply(Player player,Offering offering) {
        if(offering.material!=null) {
            int empty=player.getInventory().firstEmpty();if(empty<0)return false;
            player.getInventory().setItem(empty,new ItemStack(offering.material,offering==Offering.ARROWS?16:1));return true;
        }
        if(offering==Offering.HEAL) {
            double max=player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            if(player.getHealth()>=max)return false;player.setHealth(max);return true;
        }
        PotionEffectType effect=switch(offering){case SPEED->PotionEffectType.SPEED;case STRENGTH->PotionEffectType.STRENGTH;case RESISTANCE->PotionEffectType.RESISTANCE;default->throw new IllegalStateException();};
        if(player.hasPotionEffect(effect))return false;
        return player.addPotionEffect(new PotionEffect(effect,20*120,0,true,false,true));
    }
    public void cleanup() {
        new ArrayList<>(holograms.keySet()).forEach(this::removeHologram);
        if(registered){org.bukkit.event.HandlerList.unregisterAll(this);registered=false;}
        if(ownedWorld())for(var entry:originals.entrySet()) {
            Block block=entry.getKey();
            if(block.getWorld()==game.world()&&game.world().isChunkLoaded(block.getX()>>4,block.getZ()>>4)&&block.isEmpty())block.setBlockData(entry.getValue(),false);
        }
        originals.clear();opened.clear();
    }
}
