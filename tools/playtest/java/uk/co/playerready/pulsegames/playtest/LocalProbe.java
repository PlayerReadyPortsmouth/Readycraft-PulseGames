package uk.co.playerready.pulsegames.playtest;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.bot.*;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.seasonal.ChristmasTestMaps;
import uk.co.playerready.pulsegames.core.setup.SetupRequirement;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.world.WorldService;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Guarded, test-only server instrumentation. NPC injection for unsupported games is a lifecycle
 * probe, not a claim that those games have objective-solving queue controllers. */
public final class LocalProbe extends JavaPlugin implements CommandExecutor {
    private PulseGamesPlugin games;
    private final Map<String,Arena> arenas = new LinkedHashMap<>();
    private GameInstance current;
    private int samples;

    @Override public void onEnable() {
        if (!getServer().getIp().equals("127.0.0.1") || !new File("RC1_LOCAL_ONLY").isFile()) {
            getLogger().severe("Local probe requires loopback binding and RC1_LOCAL_ONLY sentinel.");
            getServer().getPluginManager().disablePlugin(this); return;
        }
        games = (PulseGamesPlugin)getServer().getPluginManager().getPlugin("PulseGames");
        getCommand("rc1").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) return true;
        try {
            switch (args[0]) {
                case "prepare" -> prepare();
                case "round" -> round(args[1], args.length > 2 ? args[2] : null);
                case "sample" -> sample();
                case "guide" -> {
                    Method pick=games.gameMenu().getClass().getDeclaredMethod("pick",Player.class,GameType.class);pick.setAccessible(true);
                    pick.invoke(games.gameMenu(),Bukkit.getPlayerExact("Rc1Tester"),games.registry().get(args[1]));
                }
                case "timeout" -> { if(current != null && current.state()==GameState.RUNNING) current.logic().onTimeUp(); }
                case "end" -> { if(current != null) current.forceEnd(); }
                case "cleanup" -> cleanup();
                case "catalogue" -> log("CATALOGUE " + String.join(",", games.registry().all().stream().map(GameType::id).toList()));
                default -> throw new IllegalArgumentException("Unknown local probe command");
            }
        } catch (Exception e) { getLogger().log(java.util.logging.Level.SEVERE, "RC1 PROBE FAILED", e); }
        return true;
    }
    private void prepare() {
        games.getConfig().set("features.broad",true); games.getConfig().set("bots.enabled",true);
        games.getConfig().set("games.preview",games.registry().all().stream().map(GameType::id).toList());
        games.getConfig().set("bots.wait-seconds",1); games.getConfig().set("countdown.lobby-seconds",3);
        games.getConfig().set("countdown.start-seconds",0); games.getConfig().set("end.celebrate-seconds",2);
        games.getConfig().set("lobby.world","world");
        games.saveConfig();
        World hub=Bukkit.getWorld("world");
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)hub.getBlockAt(x,99,z).setType(Material.STONE,false);
        getServer().dispatchCommand(getServer().getConsoleSender(), "season set christmas");
        World flat = new WorldCreator("rc1_flat_build").generator(new WorldService.VoidGenerator()).createWorld();
        flat.setGameRule(GameRule.DO_MOB_SPAWNING,false);
        for(int x=-46;x<=46;x++)for(int z=-46;z<=46;z++) flat.getBlockAt(x,64,z).setType(Material.SNOW_BLOCK,false);
        // Visible cover, loot and a ramp for the general controller smoke maps.
        for(int i=0;i<8;i++)flat.getBlockAt(-18+i*5,65,8).setType(Material.CHEST,false);
        flat.setSpawnLocation(0,65,0);
        games.worlds().saveAsTemplate(flat,"rc1_flat",() -> {
            for(GameType type:games.registry().all()) if(!Set.of("presentpanic","grinchhunt").contains(type.id())) arenas.put(type.id(), arena(type));
            seasonal("presentpanic",() -> seasonal("grinchhunt",this::prepared));
        }, error -> log("PREPARE FAILED "+error));
    }
    private void prepared() {
        // Each import reloads ArenaService; apply local opt-ins to the final loaded objects.
        for(String id:List.of("presentpanic","grinchhunt")) {
            Arena arena=games.arenas().byId(id,"rc1_"+id);
            arena.settings().set("bots-enabled",true);arena.settings().set("time-limit",120);arenas.put(id,arena);
        }
        log("PREPARED arenas="+arenas.size());
    }
    private Arena arena(GameType type) {
        var settings = new YamlConfiguration(); var regions = new LinkedHashMap<String,Cuboid>();
        settings.set("bots-enabled",true); settings.set("team-count",2); settings.set("void-y",55);
        settings.set("time-limit",60); settings.set("hide-seconds",5);
        int spawns=4;
        for(var req:type.setup()) {
            if(!req.required())continue;
            switch(req.kind()) {
                case SPAWNS -> spawns=Math.max(spawns,req.min());
                case REGION -> regions.put(req.key(),region(req.key(),0));
                case REGION_PREFIX -> { for(int i=1;i<=Math.max(req.min(),req.key().equals("plot")?4:2);i++)regions.put(req.key()+i,region(req.key(),i)); }
                case LOCATION_SETTING -> settings.set(req.key(),"0,65,12");
                case VALUE_SETTING -> { if(!settings.contains(req.key())) settings.set(req.key(),60); }
                default -> { }
            }
        }
        settings.set("generator1","-12,65,0");settings.set("generator2","12,65,0");
        settings.set("bot-waypoint1","-10,65,-10");settings.set("bot-waypoint2","10,65,-10");
        settings.set("bot-waypoint3","10,65,10");settings.set("bot-waypoint4","-10,65,10");
        settings.set("laps",1);
        if(type.id().equals("karts")) {
            settings.set("checkpoint-spawn1","10,65,-10");settings.set("checkpoint-spawn2","10,65,10");
            regions.put("checkpoint1",new Cuboid(8,64,-12,12,68,-8));regions.put("checkpoint2",new Cuboid(8,64,8,12,68,12));
            regions.put("finish",new Cuboid(-12,64,8,-8,68,12));
        }
        var points = new ArrayList<String>();
        for(int i=0;i<spawns;i++)points.add((-12+i*6)+",65,-10,0,0");
        return new Arena("rc1_"+type.id(),type.id(),"RC1 flat smoke","rc1_flat",List.of(),2,Math.max(4,type.defaultMode().minPlayers()),
                "0,65,35","0,75,0",points,regions,settings);
    }
    private Cuboid region(String key,int i) {
        if(key.startsWith("floor") || key.equals("sumo"))return new Cuboid(-20,64,-20,20,64,20);
        if(key.equals("arena"))return new Cuboid(-20,65,-20,20,80,20);
        if(key.equals("plot"))return new Cuboid(-30+i*12,64,10,-22+i*12,70,18);
        if(key.equals("finish")||key.equals("checkout"))return new Cuboid(-3,65,25,3,70,28);
        if(key.equals("hill"))return new Cuboid(-4,65,-4,4,68,4);
        if(key.equals("bed"))return new Cuboid(i==1?-13:12,65,0,i==1?-12:13,65,0);
        return new Cuboid(-5+i*7,65,-2, -2+i*7,67,2);
    }
    private void seasonal(String id,Runnable done) {
        ChristmasTestMaps.build(games,getServer().getConsoleSender(),id);
        String worldName="rc_build_xm_"+(id.equals("presentpanic")?"pp":"gh");
        final int[] attempts={0}; final org.bukkit.scheduler.BukkitTask[] task={null};
        task[0]=getServer().getScheduler().runTaskTimer(this,() -> {
            World world=Bukkit.getWorld(worldName);
            if(++attempts[0]>60){task[0].cancel();log("PREPARE FAILED seasonal timeout");return;}
            if(world==null||world.getBlockAt(44,110,44).getType()!=Material.OAK_SIGN)return;
            task[0].cancel();
            games.markerImporter().scan(getServer().getConsoleSender(),world,id,"rc1_"+id,new int[]{-46,64,-46},new int[]{46,112,46},false);
            final org.bukkit.scheduler.BukkitTask[] imported={null};
            imported[0]=getServer().getScheduler().runTaskTimer(this,() -> {
                Arena arena=games.arenas().byId(id,"rc1_"+id);
                if(arena!=null) {imported[0].cancel();arena.settings().set("bots-enabled",true);arena.settings().set("time-limit",120);arenas.put(id,arena);done.run();}
            },20,20);
        },20,20);
    }
    private void round(String id,String modeId) throws Exception {
        Player human=Bukkit.getPlayerExact("Rc1Tester"); if(human==null)throw new IllegalStateException("Connect local Rc1Tester client first");
        GameType type=games.registry().get(id); var mode=modeId==null?(id.equals("dropper")?type.mode("race"):type.defaultMode()):type.mode(modeId);
        current=games.instances().createPractice(type,mode,arenas.get(id),BotDifficulty.NORMAL,id.equals("grinchhunt")?11:3);
        if(current==null)throw new IllegalStateException("Cannot create "+id);
        samples=0;
        GameInstance round=current;
        final GameState[] last={null}; final org.bukkit.scheduler.BukkitTask[] observer={null};
        observer[0]=getServer().getScheduler().runTaskTimer(this,() -> {
            if(round.state()!=last[0]) {last[0]=round.state();log("STATE "+id+" "+last[0]);}
            if(round.state()==GameState.RESETTING)observer[0].cancel();
        },1,1);
        final org.bukkit.scheduler.BukkitTask[] waiting={null};
        waiting[0]=getServer().getScheduler().runTaskTimer(this,() -> {
            if(round.state()!=GameState.WAITING)return;
            waiting[0].cancel();
            human.closeInventory();
            if(!round.add(human)) {log("ROUND FAILED human admission "+id);return;}
            boolean supported=games.bots().supports(id,mode.rulesId());
            if(!supported)try{injectProbeNpcs(round,Math.min(round.maxPlayers()-1,3));}catch(Exception e){throw new RuntimeException(e);}
            log("QUEUED "+id+" "+mode.id()+" controller="+(supported?"supported":"lifecycle-only"));
        },1,1);
    }
    @SuppressWarnings("unchecked") private void injectProbeNpcs(GameInstance round,int count) throws Exception {
        Field providerField=BotService.class.getDeclaredField("provider");providerField.setAccessible(true);
        BotProvider provider=(BotProvider)providerField.get(games.bots());
        Field slotsField=BotService.class.getDeclaredField("slots");slotsField.setAccessible(true);
        Map<UUID,Object> slots=(Map<UUID,Object>)slotsField.get(games.bots());
        Class<?> slotClass=Class.forName(BotService.class.getName()+"$Slot");
        Constructor<?> ctor=slotClass.getDeclaredConstructors()[0];ctor.setAccessible(true);
        for(int i=0;i<count;i++) {
            BotActor actor=provider.create("RC1_Probe"+i,round.arena().lobby(round.world()));
            Object slot=ctor.newInstance(round,actor,(java.util.function.Consumer<Player>)p -> {});
            slots.put(actor.player().getUniqueId(),slot);
            if(!round.add(actor.player()))throw new IllegalStateException("Probe NPC refused");
        }
    }
    private void sample() {
        if(current==null)return;
        String detail="";
        try {
            if(current.type().id().equals("presentpanic")) detail=" points="+field(current.logic(),"points")+" delivered="+field(current.logic(),"delivered");
            if(current.type().id().equals("grinchhunt")) detail=" banked="+field(current.logic(),"banked")+" stolen="+field(current.logic(),"stolen")+" caught="+field(current.logic(),"caught");
        }catch(Exception e){throw new RuntimeException(e);}
        log("SAMPLE "+current.type().id()+" state="+current.state()+" bots="+games.bots().count(current)+" time="+current.gameTime()+" entities="+(current.world()==null||current.state()==GameState.RESETTING?0:current.world().getEntities().size())+detail);
        if(current.state()==GameState.RUNNING && ++samples%2==1)for(Player p:current.players())log("ACTOR "+p.getName()+" at="+p.getLocation().toVector());
    }
    private Object field(Object owner,String name) throws Exception {Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void cleanup() {
        if(current!=null) {current.forceEnd();current.cleanup();current=null;}
        log("CLEANUP "+games.bots().status()+" instances="+games.instances().all().size());
    }
    private void log(String message) {getLogger().info("RC1 "+message);}
}
