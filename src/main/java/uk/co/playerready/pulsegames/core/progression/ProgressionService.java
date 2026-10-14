package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import net.kyori.adventure.text.Component;
import java.util.*;

/** Small durable outbox; counters and cosmetic awards belong to shared PulseContent SQL. */
public final class ProgressionService {
    private final PulseGamesPlugin plugin;
    private ProgressionOutbox outbox;
    private final Set<String> inFlight=new HashSet<>();
    private long nextWarning;
    private int cursor;
    public ProgressionService(PulseGamesPlugin plugin) {
        this.plugin=plugin;
        try {outbox=new ProgressionOutbox(plugin.getDataFolder().toPath().resolve("progression-outbox"));}
        catch(Exception failure){plugin.getLogger().severe("Progression outbox unavailable; preserve existing receipts and repair before collecting rewards.");}
        plugin.getServer().getScheduler().runTaskTimer(plugin,this::retry,40,600);
    }
    public static boolean eligible(boolean ranked,boolean participant,boolean bot,String reason) {
        return ranked && participant && !bot && !Set.of("forced","error","shutdown","aborted").contains(reason);
    }
    public void completed(GameInstance game,Player player,boolean won,int kills,String reason) {
        if(!plugin.getConfig().getBoolean("progression.enabled",false) || !eligible(game.ranked(),game.isParticipant(player),game.isBot(player),reason))return;
        try {
            if(outbox==null)throw new IllegalStateException();
            outbox.add(new ProgressionOutbox.Result(player.getUniqueId(),UUID.nameUUIDFromBytes(game.id().getBytes(java.nio.charset.StandardCharsets.UTF_8)),game.type().id(),won,kills,
                    plugin.getConfig().getString("progression.event-id","")));
        }catch(Exception failure){plugin.getLogger().severe("Could not retain progression receipt for match "+game.id()+" player "+player.getUniqueId()+"; manual review required.");}
    }
    private void retry() {
        if(outbox==null || !plugin.getConfig().getBoolean("progression.enabled",false))return;
        // Bounded concurrency. Failed records remain on disk and are never acknowledged as success.
        var pending=outbox.pending();
        int size=pending.size();
        for(int checked=0;checked<size;checked++) {
            if(inFlight.size()>=8)break;
            var row=pending.get(Math.floorMod(cursor++,size));
            if(!inFlight.add(row.key()))continue;
            plugin.content().recordCompetitiveMatch(row).whenComplete((accepted,failure)->{
                try { plugin.getServer().getScheduler().runTask(plugin,()->{
                    inFlight.remove(row.key());
                    if(failure==null && Boolean.TRUE.equals(accepted)) {
                        try {outbox.acknowledge(row);}catch(java.io.IOException ignored){warn();}
                    }else warn();
                });}catch(org.bukkit.plugin.IllegalPluginAccessException ignored){/* Receipt survives shutdown. */}
            });
        }
    }
    private void warn() {
        long now=System.currentTimeMillis();if(now<nextWarning)return;nextWarning=now+60000;
        plugin.getLogger().warning("Progression delivery pending. Check PulseContent progression enablement, schema and goal IDs; receipts retained for retry.");
    }
    public void show(Player player) {
        if(!plugin.getConfig().getBoolean("progression.enabled",false)) {player.sendMessage(Component.text("Minigame challenges are not enabled yet."));return;}
        player.sendMessage(Component.text("Competitive challenges: eligible completed rounds count; delivery can take up to 30 seconds."));
        plugin.content().matchProgression(player.getUniqueId()).whenComplete((rows,failure)->{
            try {plugin.getServer().getScheduler().runTask(plugin,()->{
                if(!player.isOnline())return;
                if(failure!=null){player.sendMessage(Component.text("Challenges are temporarily unavailable; pending results are retained."));return;}
                if(rows.isEmpty())player.sendMessage(Component.text("No challenges are configured yet."));
                rows.stream().limit(64).forEach(line->player.sendMessage(Component.text(line)));
            });}catch(org.bukkit.plugin.IllegalPluginAccessException ignored){}
        });
    }
}
