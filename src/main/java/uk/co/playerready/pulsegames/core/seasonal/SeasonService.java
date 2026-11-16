package uk.co.playerready.pulsegames.core.seasonal;

import net.kyori.adventure.text.Component;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.util.YamlFiles;
import java.io.*;
import java.time.*;
import java.util.*;

/** Runtime seasonal gate. Preview is read-only; set overrides the calendar until clear. */
public final class SeasonService implements CommandExecutor,TabCompleter {
    private final PulseGamesPlugin plugin;
    private YamlConfiguration config=new YamlConfiguration();
    private List<SeasonCalendar.Window> windows=List.of();
    private String override;
    private ZoneId zone=ZoneId.of("Europe/London");
    private boolean valid;
    private SeasonOverlay overlay;
    private String syncedSeason;
    private org.bukkit.plugin.Plugin syncedContent;
    public SeasonService(PulseGamesPlugin plugin) {
        this.plugin=plugin;
        if(!new File(plugin.getDataFolder(),"seasons.yml").exists())plugin.saveResource("seasons.yml",false);
        overlay=new SeasonOverlay(plugin);
        reload();
        plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,20,20);
        Objects.requireNonNull(plugin.getCommand("season")).setExecutor(this);
        plugin.getCommand("season").setTabCompleter(this);
    }
    public void reload() {
        valid=false;windows=List.of();
        try {
            YamlConfiguration next=new YamlConfiguration();next.load(new File(plugin.getDataFolder(),"seasons.yml"));
            var root=next.getConfigurationSection("seasons");if(root==null)throw new IllegalArgumentException("Missing seasons map");
            List<SeasonCalendar.Window> parsed=new ArrayList<>();
            for(String id:root.getKeys(false)) {
                var s=Objects.requireNonNull(root.getConfigurationSection(id));
                parsed.add(new SeasonCalendar.Window(id,MonthDay.parse("--"+(s.getBoolean("easter",false)?"01-01":s.getString("start"))),MonthDay.parse("--"+(s.getBoolean("easter",false)?"01-01":s.getString("end"))),
                        Set.copyOf(s.getStringList("games")),Set.copyOf(s.getStringList("cosmetic-rewards")),s.getBoolean("wear-out-of-season",true),s.getBoolean("easter",false),Math.max(0,Math.min(30,s.getInt("days-before",7))),Math.max(0,Math.min(30,s.getInt("days-after",1)))));
            }
            zone=ZoneId.of(next.getString("timezone","Europe/London"));
            YamlConfiguration state=new YamlConfiguration();File file=new File(plugin.getDataFolder(),"season-override.yml");if(file.exists())state.load(file);
            String selected=state.getString("season");if(selected!=null&&parsed.stream().noneMatch(w->w.id().equals(selected)))throw new IllegalArgumentException("Unknown saved override");
            config=next;windows=List.copyOf(parsed);override=selected;valid=true;syncedSeason="__reload__";
        } catch(Exception invalid) {plugin.getLogger().severe("Seasonal features disabled: "+invalid.getMessage());}
    }
    private Optional<SeasonCalendar.Window> current() {return valid&&config.getBoolean("enabled",false)?SeasonCalendar.active(windows,LocalDate.now(zone),override):Optional.empty();}
    public String active() {return current().map(SeasonCalendar.Window::id).orElse(null);}
    public boolean seasonalGame(String id) {return Set.of("presentpanic","grinchhunt").contains(id)||windows.stream().anyMatch(w->w.games().contains(id));}
    public boolean gameEnabled(String id) {return !seasonalGame(id)||current().map(w->w.games().contains(id)).orElse(false);}
    public boolean canView(Player player) {
        return config.getStringList("canary-players").isEmpty() || config.getStringList("canary-players").contains(player.getUniqueId().toString());
    }
    public boolean customVisualsReady(Player player) {return overlay!=null&&overlay.ready(player);}
    private void tick() {
        String season=active();
        var provider=plugin.getServer().getPluginManager().getPlugin("PulseContent");
        if(!Objects.equals(syncedSeason,season)||syncedContent!=provider) {
            overlay.configure(config,season);syncedSeason=season;syncedContent=provider;
            if(provider!=null&&provider.isEnabled())try {
                Map<String,String> mapping=new HashMap<>();Set<String> wearable=new HashSet<>();
                for(var window:windows)for(String id:config.getStringList("seasons."+window.id()+".content-cosmetics")) {
                    mapping.put(id,window.id());if(window.wearable())wearable.add(id);
                }
                if(Integer.valueOf(1).equals(provider.getClass().getMethod("seasonalCosmeticsApiVersion").invoke(provider)))
                    provider.getClass().getMethod("seasonalCosmetics",org.bukkit.plugin.Plugin.class,String.class,Map.class,Set.class)
                            .invoke(provider,plugin,season==null?"":season,mapping,wearable);
            } catch(ReflectiveOperationException|RuntimeException|LinkageError ignored) { /* Vanilla cosmetics remain usable. */ }
        }
        overlay.tick();
        if(season==null)return;
        for(Player player:plugin.getServer().getOnlinePlayers()) {
            if(!canView(player)||plugin.instances().byPlayer(player)!=null||!plugin.lobby().isLobbyWorld(player.getWorld()))continue;
            for(String decoration:config.getStringList("seasons."+season+".decorations"))try {
                var particle=org.bukkit.Particle.valueOf(decoration);
                if(particle.getDataType()==Void.class)player.spawnParticle(particle,player.getLocation().add(0,2,0),3,2,1,2,0.01);
            }catch(IllegalArgumentException ignored) { /* Unknown decorations do not interrupt a match. */ }
        }
    }
    public void close() {
        if(overlay!=null)overlay.close();
        var provider=plugin.getServer().getPluginManager().getPlugin("PulseContent");
        if(provider!=null)try {provider.getClass().getMethod("clearSeasonalCosmetics",org.bukkit.plugin.Plugin.class).invoke(provider,plugin);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError ignored) {}
    }
    public boolean canPlay(Player player,String game) {
        return gameEnabled(game)&&(!seasonalGame(game)||player.hasMetadata("NPC")||config.getStringList("canary-players").isEmpty()||config.getStringList("canary-players").contains(player.getUniqueId().toString()));
    }
    public boolean wearable(String cosmetic) {
        return windows.stream().filter(w->w.rewards().contains(cosmetic)).allMatch(w->w.wearable()||Objects.equals(active(),w.id()));
    }
    public void award(Player player,String game) {
        if(player.hasMetadata("NPC")||!canPlay(player,game))return;
        current().filter(w->w.games().contains(game)).ifPresent(w->w.rewards().forEach(id-> {
            if(uk.co.playerready.pulsegames.core.economy.CosmeticsService.byId(id)!=null)plugin.economy().unlock(player,id);
        }));
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!sender.hasPermission("pulsegames.admin"))return true;
        try {
            String verb=args.length==0?"preview":args[0];
            if(verb.equals("reload")&&args.length==1) {reload();say(sender,valid?"Season config reloaded.":"Invalid season config; features disabled.");return true;}
            if(verb.equals("preview")&&args.length<=2) {
                String id=args.length==2?args[1]:active();
                var w=windows.stream().filter(s->s.id().equals(id)).findFirst();
                if(w.isEmpty()) {say(sender,"No active season. Available: "+String.join(", ",windows.stream().map(SeasonCalendar.Window::id).toList()));return true;}
                var s=w.get();say(sender,s.id()+": "+s.start()+" to "+s.end()+"; games="+s.games()+"; rewards="+s.rewards());
                say(sender,"Overlay="+config.getString("seasons."+id+".overlay","")+"; decorations="+config.getStringList("seasons."+id+".decorations")+"; enabled="+config.getBoolean("enabled",false));return true;
            }
            String selected;
            if(verb.equals("clear")&&args.length==1)selected=null;
            else if(verb.equals("set")&&args.length==2&&windows.stream().anyMatch(w->w.id().equals(args[1])))selected=args[1];
            else {say(sender,"/season preview [id] | set <id> | clear | reload");return true;}
            YamlConfiguration state=new YamlConfiguration();state.set("season",selected);
            YamlFiles.writeAtomically(new File(plugin.getDataFolder(),"season-override.yml"),state.saveToString());override=selected;syncedSeason="__override__";
            say(sender,"Season: "+active()+". Clear follows the calendar; enabled and canary settings still apply.");
        } catch(IOException failure) {say(sender,"Could not save override; unchanged: "+failure.getMessage());}
        return true;
    }
    private static void say(CommandSender s,String text) {s.sendMessage(Component.text(text));}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args) {
        if(!s.hasPermission("pulsegames.admin"))return List.of();
        return (args.length==1?List.of("preview","set","clear","reload"):windows.stream().map(SeasonCalendar.Window::id).toList()).stream().filter(x->x.startsWith(args[args.length-1])).toList();
    }
}
