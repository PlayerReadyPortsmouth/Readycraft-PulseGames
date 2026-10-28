package uk.co.playerready.pulsegames.games.dropper;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.common.RaceAwards;
import uk.co.playerready.pulsegames.games.common.RaceFinish;

import java.util.*;

/**
 * Sequential water landings. Completing a stage saves its next launch as the retry point.
 * Finishers get places and times inside a grace window (setting {@code finish-grace}); a
 * single-player practice run is a time trial against the per-map personal best.
 */
public class DropperGame extends MiniGame {
    private List<Cuboid> checkpoints = List.of();
    private Cuboid finish;
    private final Map<UUID, Integer> progress = new HashMap<>();
    private final Map<UUID, Location> retries = new HashMap<>();
    private final Map<UUID, Integer> misses = new HashMap<>();
    private final Map<UUID, Long> stageStart = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private RaceFinish finishes;
    private long startMs;
    private boolean closed;
    public DropperGame(GameInstance game) { super(game); }
    @Override public boolean cooperative() { return true; } // Leaving never awards an unfinished race.
    @Override public boolean managesCompletion() { return true; }
    @Override public boolean fallDamage() { return true; }
    @Override public int timeLimitSeconds() { return 600; }
    protected long nowMs() { return System.currentTimeMillis(); }
    @Override public String startProblem(List<Player> participants) {
        if (game.arena().region("finish") == null) return "Dropper requires a finish landing";
        int count = game.arena().regionsByPrefix("checkpoint").size();
        for (int i = 1; i <= count; i++) {
            try {
                Location launch = game.arena().settingLocation("checkpoint-spawn" + i, game.world());
                if (launch == null) return "Missing checkpoint-spawn" + i;
                launch.checkFinite();
            } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
                return "Invalid checkpoint-spawn" + i;
            }
        }
        return null;
    }
    @Override public void onStart() {
        checkpoints = game.arena().regionsByPrefix("checkpoint");
        finish = game.arena().region("finish");
        startMs = nowMs() + startFreezeSeconds() * 1000L;
        for (Player player : game.alivePlayers()) {
            progress.put(player.getUniqueId(), 0);
            retries.put(player.getUniqueId(), game.spawnFor(player));
            names.put(player.getUniqueId(), player.getName());
            stageStart.put(player.getUniqueId(), 0L);
        }
        finishes = new RaceFinish(game.alivePlayers().stream().map(Player::getUniqueId).toList(),
                game.arena().settings().getInt("finish-grace", RaceFinish.DEFAULT_GRACE_SECONDS));
        game.broadcast(game.practice()
                ? "Time trial! Land in each water pool as fast as you can - misses just return you to your latest launch."
                : "Land in each water pool! Misses return you to your latest launch - no penalty, just time.");
    }
    long elapsedMs() { return Math.max(0, nowMs() - startMs); }
    @Override public void onMove(Player player, Location from, Location to) {
        if (!game.isActive(player) || to.getWorld() != game.world() || !to.getBlock().isLiquid()
                || to.getBlock().getType() != org.bukkit.Material.WATER) return;
        UUID id = player.getUniqueId();
        int stage = progress.getOrDefault(id, 0);
        if (stage < checkpoints.size()) {
            if (!checkpoints.get(stage).contains(to)) return;
            Location next = game.arena().settingLocation("checkpoint-spawn" + (stage + 1), game.world());
            progress.put(id, stage + 1);
            retries.put(id, next);
            stageClear(player, stage + 2, elapsedMs() - stageStart.getOrDefault(id, 0L));
            stageStart.put(id, elapsedMs());
            retry(player);
        } else if (finish != null && finish.contains(to)) {
            complete(player);
        }
    }
    /** Title "Stage 2/5", level-up sound and a splash; {@code stageMs} is the time the last stage took. */
    private void stageClear(Player player, int entering, long stageMs) {
        Text.title(player, "<aqua><b>Stage " + entering + "/" + (checkpoints.size() + 1),
                "<gray>Cleared in <white>" + RaceFinish.time(stageMs) + " <gray>| total <white>" + RaceFinish.time(elapsedMs()));
        player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 1f, 1.1f);
        game.world().spawnParticle(Particle.SPLASH, player.getLocation().add(0, 1, 0), 50, 0.6, 0.5, 0.6, 0.1);
    }
    private void complete(Player player) {
        UUID id = player.getUniqueId();
        long ms = elapsedMs();
        if (closed || !finishes.finish(id, ms)) return;
        int place = finishes.placeOf(id);
        RaceAwards.announceFinish(game, player, place, ms);
        game.world().spawnParticle(Particle.SPLASH, player.getLocation().add(0, 1, 0), 80, 0.8, 0.6, 0.8, 0.1);
        game.broadcast("<gold>#" + place + " <yellow>" + player.getName() + " <gray>finished in <white>" + RaceFinish.time(ms)
                + (misses.getOrDefault(id, 0) == 0 ? " <green>- clean run!" : ""));
        game.finishParticipant(player);
        onSecond(game.gameTime());
    }
    @Override public void onDamage(Player player, EntityDamageEvent event) {
        event.setCancelled(true);
        miss(player);
    }
    @Override public void onDeath(Player victim, Player killer) { miss(victim); }
    /** Friendly miss counter: it is shown, never penalised. */
    private void miss(Player player) {
        int n = misses.merge(player.getUniqueId(), 1, Integer::sum);
        player.sendActionBar(Text.mm("<aqua>Splash! Not this time <gray>- misses: <white>" + n));
        retry(player);
    }
    private void retry(Player player) {
        player.setFallDistance(0);
        player.setVelocity(new org.bukkit.util.Vector());
        player.teleport(retries.getOrDefault(player.getUniqueId(), game.spawnFor(player)).clone());
    }
    @Override public void onQuit(Player player) {
        if (finishes == null || closed) return;
        finishes.depart(player.getUniqueId());
        onSecond(game.gameTime());
    }
    @Override public void onSecond(int gameTime) {
        if (finishes == null || closed) return;
        long now = elapsedMs();
        if (finishes.shouldEnd(now) || game.alivePlayers().isEmpty()) { close("finished"); return; }
        boolean closing = finishes.hasFinishers() && finishes.graceRemainingSeconds(now) <= 10;
        for (Player player : game.alivePlayers()) {
            UUID id = player.getUniqueId();
            int stage = progress.getOrDefault(id, 0);
            player.sendActionBar(Text.mm(closing
                    ? "<red>Race closes in " + finishes.graceRemainingSeconds(now) + "s <gray>| stage " + (stage + 1) + "/" + (checkpoints.size() + 1)
                    : "<aqua>Stage " + (stage + 1) + "/" + (checkpoints.size() + 1) + " <white>"
                    + RaceFinish.time(now - stageStart.getOrDefault(id, 0L)) + " <gray>| total <white>" + RaceFinish.time(now)
                    + " <gray>| misses <white>" + misses.getOrDefault(id, 0)));
        }
    }
    /** Finishers by time, then everyone else by furthest stage. */
    List<UUID> placings() {
        List<UUID> rest = new ArrayList<>(game.alivePlayers().stream().map(Player::getUniqueId).toList());
        rest.sort(Comparator.comparingInt((UUID u) -> progress.getOrDefault(u, 0)).reversed());
        return finishes.fullOrder(rest);
    }
    private void close(String reason) {
        if (closed) return;
        closed = true;
        List<UUID> order = placings();
        for (int i = 0; i < Math.min(order.size(), 5); i++) {
            long t = finishes.timeOf(order.get(i));
            game.broadcast("<gray>" + (i + 1) + ". <white>" + names.getOrDefault(order.get(i), "Player") + " <gray>"
                    + (t >= 0 ? RaceFinish.time(t) : "stage " + (progress.getOrDefault(order.get(i), 0) + 1)));
        }
        List<Player> winners = finishes.results().stream().filter(r -> r.place() == 1)
                .map(r -> game.player(r.player())).filter(Objects::nonNull).toList();
        game.end(winners, winners.isEmpty() ? "time-unfinished" : reason);
    }
    @Override public void onTimeUp() {
        if (finishes == null) game.end(List.of(), "time-unfinished");
        else close("time-unfinished");
    }
    @Override public List<String> sidebar(Player player) {
        UUID me = player.getUniqueId();
        List<String> lines = new ArrayList<>();
        lines.add("<aqua>Stage: <white>" + (progress.getOrDefault(me, 0) + 1) + "/" + (checkpoints.size() + 1));
        lines.add("<gray>Misses: <white>" + misses.getOrDefault(me, 0));
        if (finishes == null || game.practice()) return lines;
        List<UUID> order = placings();
        for (int i = 0; i < order.size() && i < 5; i++) {
            long t = finishes.timeOf(order.get(i));
            lines.add((order.get(i).equals(me) ? "<yellow>#" : "<gray>#") + (i + 1) + " <white>"
                    + names.getOrDefault(order.get(i), "Player") + (t >= 0 ? " <gray>" + RaceFinish.time(t) : ""));
        }
        return lines;
    }
}
