package uk.co.playerready.pulsegames.core.practice;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import uk.co.playerready.pulsegames.core.bot.QueueFillPolicy;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;

/** Admin-only isolated scenarios. Repeats the setup, not Minecraft's random outcome. */
public final class PracticeService {
    public record Scenario(String game, String mode, String map, BotDifficulty difficulty, int bots) {}
    private static final class Session {
        final UUID owner;
        final GameInstance game;
        final PracticeReport report = new PracticeReport();
        int seconds;
        boolean admitted;
        Session(UUID owner, GameInstance game) { this.owner=owner; this.game=game; }
    }
    private final PulseGamesPlugin plugin;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private final Map<UUID, Scenario> previous = new LinkedHashMap<>();
    private final Map<UUID, List<String>> reports = new LinkedHashMap<>();
    public PracticeService(PulseGamesPlugin plugin) { this.plugin=plugin; }
    public void start() { plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20, 20); }

    public void handle(Player player, String[] args) {
        if (!player.hasPermission("pulsegames.admin")) { say(player,"No permission."); return; }
        if (args.length==1 && args[0].equalsIgnoreCase("report")) {
            var session=sessions.get(player.getUniqueId());
            var rows=session==null ? reports.get(player.getUniqueId()) : session.report.lines(session.game.id(),session.game.state().name());
            if(rows==null) say(player,"No practice report yet."); else rows.forEach(row->say(player,row));
            return;
        }
        if (args.length==1 && args[0].equalsIgnoreCase("end")) {
            var session=sessions.get(player.getUniqueId());
            if(session==null) say(player,"No active practice session.");
            else { finish(session,"Stopped by owner"); session.game.cleanup(); }
            return;
        }
        if (args.length==1 && args[0].equalsIgnoreCase("repeat")) {
            var scenario=previous.get(player.getUniqueId());
            if(scenario==null) say(player,"Start a practice scenario first."); else begin(player,scenario);
            return;
        }
        if ((args.length==5 || args.length==6) && args[0].equalsIgnoreCase("start")) {
            try {
                var difficulty=BotDifficulty.valueOf(args[4].toUpperCase(Locale.ROOT));
                int bots=args.length==6?Integer.parseInt(args[5]):1;
                begin(player,new Scenario(args[1],args[2],args[3],difficulty,bots));
            } catch(IllegalArgumentException invalid) { say(player,"Use easy, normal or hard, and a whole bot count from 1 to 31."); }
            return;
        }
        say(player,"/pulse practice start <game> <mode> <map> <easy|normal|hard> [bots]");
        say(player,"/pulse practice <report|end|repeat> — private, no records/rewards or skill calibration.");
    }

    public void begin(Player owner, Scenario scenario) {
        if (!owner.hasPermission("pulsegames.admin")) { say(owner,"No permission."); return; }
        if (sessions.containsKey(owner.getUniqueId()) || plugin.instances().byPlayer(owner)!=null || plugin.playService().hasPending(owner)) {
            say(owner,"Leave your current game/queue or end practice before starting another scenario."); return;
        }
        if (!plugin.canUseNewFeatures(owner) || !plugin.getConfig().getBoolean("bots.enabled",false)) {
            say(owner,"Practice requires bot enablement and your existing trial access."); return;
        }
        var type=plugin.registry().get(scenario.game());
        var mode=type==null?null:type.mode(scenario.mode());
        var arena=type==null?null:plugin.arenas().byId(type.id(),scenario.map());
        if(type==null || mode==null || arena==null || !plugin.gameAvailable(type) || !arena.supportsMode(mode.id())
                || !plugin.bots().supports(type.id(),mode.rulesId()) || !arena.settings().getBoolean("bots-enabled",false)) {
            say(owner,"Choose an available bot-supported game/mode and an opted-in map configured for that mode."); return;
        }
        if(scenario.bots()<1 || scenario.bots()>31) { say(owner,"Bot count must be 1-31."); return; }
        var game=plugin.instances().createPractice(type,mode,arena,scenario.difficulty(),scenario.bots());
        if(game==null) { say(owner,"The map cannot host this mode or the instance limit was reached."); return; }
        int maximum=QueueFillPolicy.desiredBots(1,game.minPlayers(),game.maxPlayers(),
                Math.min(31,plugin.getConfig().getInt("bots.max-per-match",31)),
                Math.min(64,plugin.getConfig().getInt("bots.max-total",64)));
        if(scenario.bots()>maximum || scenario.bots()+1<game.minPlayers()) {
            game.cleanup(); say(owner,"This map needs at least " + Math.max(1,game.minPlayers()-1)
                    + " bots and permits at most " + maximum + " with one tester under the normal fill limits."); return;
        }
        var session=new Session(owner.getUniqueId(),game);
        sessions.put(owner.getUniqueId(),session);
        previous.put(owner.getUniqueId(),scenario); trim(previous);
        game.note("practice-scenario","difficulty="+scenario.difficulty()+" bots="+scenario.bots());
        say(owner,"Loading private practice: " + scenario.map()+" / "+scenario.difficulty()+" / "+scenario.bots()+" bots. Reference: "+game.id());
    }
    public void repeatFromResults(Player player) {
        var previousScenario=previous.get(player.getUniqueId());
        var game=plugin.instances().byPlayer(player);
        if(game!=null && (!game.practice() || game.state()!=GameState.ENDING)) { say(player,"Finish your current round first."); return; }
        var session=sessions.get(player.getUniqueId());
        if(session!=null) finish(session,"Repeating completed scenario");
        if(game!=null) { game.remove(player,false); game.cleanup(); }
        if(previousScenario==null) say(player,"No practice scenario to repeat."); else begin(player,previousScenario);
    }

    void tick() {
        for(var session:List.copyOf(sessions.values())) {
            var game=session.game;
            session.seconds++;
            Player owner=plugin.getServer().getPlayer(session.owner);
            if(game.state()==GameState.RESETTING) { finish(session,"Instance closed"); continue; }
            if(owner==null || !owner.isOnline()) { finish(session,"Owner disconnected"); game.cleanup(); continue; }
            if(!session.admitted) {
                if(session.seconds>30 || plugin.instances().byPlayer(owner)!=null || plugin.playService().hasPending(owner)) {
                    finish(session,"Loading timed out or owner joined another queue"); game.cleanup(); continue;
                }
                if(game.state()==GameState.WAITING) {
                    if(!game.add(owner)) { finish(session,"Admission refused"); game.cleanup(); continue; }
                    session.admitted=true;
                }
            } else if(!game.isParticipant(owner) && game.state()!=GameState.ENDING) {
                finish(session,"Owner left practice"); game.cleanup(); continue;
            }
            if(session.seconds%5==0) session.report.sample(plugin.bots().diagnostics().stream().filter(b->game.id().equals(b.gameId())).toList());
            if(game.state()==GameState.ENDING) { finish(session,"Round ended; see match results and diagnostic events"); }
            else if(session.admitted && session.seconds>120 && game.state()==GameState.WAITING) { finish(session,"Bots or minimum roster unavailable after two minutes"); game.cleanup(); }
            else if(session.seconds>1800) { finish(session,"30 minute practice limit"); game.cleanup(); }
        }
    }
    private void finish(Session session,String outcome) {
        sessions.remove(session.owner);
        var rows=session.report.lines(session.game.id(),outcome);
        reports.put(session.owner,rows); trim(reports);
        rows.forEach(row->session.game.note("practice-report",row));
        var owner=plugin.getServer().getPlayer(session.owner);
        if(owner!=null && owner.isOnline()) rows.forEach(row->say(owner,row));
    }
    private static <T> void trim(Map<UUID,T> map) { while(map.size()>128) map.remove(map.keySet().iterator().next()); }
    private static void say(Player player,String text) { player.sendMessage(Component.text(text)); }
}
