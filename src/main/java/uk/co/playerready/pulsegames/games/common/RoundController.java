package uk.co.playerready.pulsegames.games.common;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code rounds} mode for one-life games: several short rounds with a full reset between
 * them, placement points per round and a match winner on points. The engine still sees every
 * player as alive for the whole match; a round-out player is simply parked as a spectator until
 * the next round, so the game must call {@link #knockOut} instead of {@code game.eliminate}.
 */
public final class RoundController {

    public static final String MODE_ID = "rounds";
    public static final int INTERMISSION_SECONDS = 5;
    private static final int FINAL_PAUSE_SECONDS = 3;
    private static final int RESTORE_BLOCKS_PER_TICK = 4096;

    /** What a game supplies; the controller does everything else. */
    public interface Host {
        /** Reset game state and equip players. They are already at their spawns and playable. */
        void beginRound(int round);

        /** Ranking when the round clock runs out. Defaults to survivors tied, then last-out first. */
        default List<List<UUID>> timeUpTiers(RoundTracker tracker) { return tracker.eliminationTiers(); }

        /** Reset anything beyond the region snapshot (game state, stray entities) while the intermission starts. */
        default void resetWorld() {}
    }

    private enum Phase { ROUND, INTERMISSION, DONE }

    private final GameInstance game;
    private final Host host;
    private final RegionSnapshot snapshot;
    private final RoundTracker tracker;
    private final int roundSeconds;
    private final String objective;
    private boolean autoEnd = true;
    private Phase phase = Phase.ROUND;
    private int elapsed;
    private int intermissionLeft;
    private boolean finalPause;
    private BukkitTask restoreTask;
    private int fieldSize;

    public static boolean isRounds(GameInstance game) { return MODE_ID.equals(game.mode().id()); }

    public static int configuredRounds(GameInstance game) {
        return clamp(game.arena().settings().getInt("round-count", 3), 1, 5);
    }

    public static int configuredRoundSeconds(GameInstance game, int fallback) {
        return clamp(game.arena().settings().getInt("round-seconds", fallback), 20, 180);
    }

    /** Engine time limit that comfortably covers every round, intermission and the closing pause. */
    public static int matchSeconds(GameInstance game, int roundSecondsFallback) {
        return configuredRounds(game) * (configuredRoundSeconds(game, roundSecondsFallback) + INTERMISSION_SECONDS) + 10;
    }

    public RoundController(GameInstance game, Host host, RegionSnapshot snapshot, int roundSecondsFallback, String objective) {
        this.game = game;
        this.host = host;
        this.snapshot = snapshot;
        this.roundSeconds = configuredRoundSeconds(game, roundSecondsFallback);
        this.tracker = new RoundTracker(configuredRounds(game));
        this.objective = objective;
    }

    /** Games that decide a round themselves (Prop Hunt) turn off the "one left" rule. */
    public RoundController autoEnd(boolean value) { this.autoEnd = value; return this; }

    public RoundTracker tracker() { return tracker; }
    public boolean active() { return phase == Phase.ROUND; }
    public int round() { return tracker.round(); }
    public int roundElapsed() { return elapsed; }
    public int roundSeconds() { return roundSeconds; }
    public int roundSecondsLeft() { return Math.max(0, roundSeconds - elapsed); }

    /** Players still contesting the current round. */
    public List<Player> contestants() {
        if (phase != Phase.ROUND) return List.of();
        return game.alivePlayers().stream().filter(p -> tracker.isRemaining(p.getUniqueId())).toList();
    }

    public boolean isContesting(Player player) {
        return phase == Phase.ROUND && tracker.isRemaining(player.getUniqueId());
    }

    // ---- lifecycle -------------------------------------------------------------

    /** Call from the game's onStart, after its own setup and snapshot. */
    public void start() {
        fieldSize = game.players().size();
        tracker.startRound(ids(game.players()));
        phase = Phase.ROUND;
        elapsed = 0;
        announceRound();
        host.beginRound(tracker.round());
    }

    /** Call once a second from the game's onSecond. */
    public void tick() {
        if (phase == Phase.DONE || game.frozen()) return;
        if (phase == Phase.ROUND) {
            if (++elapsed >= roundSeconds) endRound(host.timeUpTiers(tracker));
            return;
        }
        if (--intermissionLeft > 0) {
            if (!finalPause) {
                for (Player p : game.players()) {
                    p.sendActionBar(Text.mm("<yellow>Round " + (tracker.round() + 1) + " of " + tracker.totalRounds()
                            + " in <white>" + intermissionLeft));
                    if (intermissionLeft <= 3) p.playSound(p.getLocation(), "minecraft:block.note_block.hat", 1f, 0.8f + 0.3f * (4 - intermissionLeft));
                }
            }
            return;
        }
        if (finalPause) finishMatch(); else startNextRound();
    }

    /** Engine time limit hit: the match is decided on the points banked so far. */
    public void onTimeUp() { finishMatch(); }

    public void close() {
        phase = Phase.DONE;
        cancelRestore();
    }

    /** Total engine time limit for this controller's match. */
    public int totalSeconds() { return tracker.totalRounds() * (roundSeconds + INTERMISSION_SECONDS) + 10; }

    // ---- knock-outs ------------------------------------------------------------

    /** Take a player out of the current round. False when they were not in it. */
    public boolean knockOut(Player player) {
        if (phase != Phase.ROUND || !tracker.knockOut(player.getUniqueId())) return false;
        Location spot = player.getLocation();
        MiniGame.Cue cue = game.logic().eliminationSound();
        if (cue != null) for (Player p : game.everyone()) p.playSound(spot, cue.sound(), cue.volume(), cue.pitch());
        park(player);
        Text.title(player, "<red><b>OUT!", "<gray>" + tracker.remainingCount() + " left in round " + tracker.round());
        if (autoEnd) HazardFeedback.finalThree(game.players(), tracker.remainingCount(), fieldSize);
        if (autoEnd && tracker.lastStanding()) endRound(tracker.eliminationTiers());
        return true;
    }

    /** A participant disconnected. */
    public void playerLeft(Player player) {
        tracker.leave(player.getUniqueId());
        if (phase == Phase.DONE) return;
        if (game.players().size() < 2) { finishMatch(); return; }
        if (phase == Phase.ROUND && autoEnd && tracker.lastStanding()) endRound(tracker.eliminationTiers());
    }

    // ---- round flow ------------------------------------------------------------

    /** End the current round with an explicit ranking (best tier first) and bank the points. */
    public void endRound(List<List<UUID>> tiers) {
        if (phase != Phase.ROUND) return;
        phase = Phase.INTERMISSION;
        int round = tracker.round();
        Map<UUID, Integer> earned = tracker.award(tiers);
        Map<UUID, Integer> places = RoundTracker.placementsFor(tiers);
        for (Player p : game.players()) {
            Integer pts = earned.get(p.getUniqueId());
            if (pts == null) continue;
            game.addScore(p, pts);
            Text.title(p, "<gold><b>Round " + round + " over",
                    "<gray>You were " + ordinal(places.get(p.getUniqueId())) + " · <yellow>+" + pts + " points");
            p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 0.7f, 1.2f);
        }
        List<String> winners = new ArrayList<>();
        if (!tiers.isEmpty()) for (UUID id : tiers.get(0)) {
            Player w = game.player(id);
            if (w != null) winners.add(w.getName());
        }
        game.broadcast("<gold>Round " + round + " of " + tracker.totalRounds() + ":</gold> <yellow>"
                + (winners.isEmpty() ? "nobody" : String.join(", ", winners)) + "</yellow> <gray>took it.");
        for (Player p : game.players()) park(p);
        host.resetWorld();
        clearFallingBlocks();
        finalPause = tracker.lastRound();
        intermissionLeft = finalPause ? FINAL_PAUSE_SECONDS : INTERMISSION_SECONDS;
        if (!finalPause && snapshot != null) {
            snapshot.beginRestore();
            cancelRestore();
            restoreTask = game.runRepeating(1L, 1L, () -> {
                if (snapshot.restoreStep(RESTORE_BLOCKS_PER_TICK)) cancelRestore();
            });
        }
    }

    private void startNextRound() {
        cancelRestore();
        if (snapshot != null) snapshot.finishRestore();
        clearFallingBlocks();
        List<Player> present = game.players();
        if (present.size() < 2) { finishMatch(); return; }
        fieldSize = present.size();
        tracker.startRound(ids(present));
        for (Player p : present) {
            resetPlayer(p);
            p.teleport(game.spawnFor(p));
        }
        phase = Phase.ROUND;
        elapsed = 0;
        announceRound();
        host.beginRound(tracker.round());
    }

    private void finishMatch() {
        if (phase == Phase.DONE) return;
        phase = Phase.DONE;
        cancelRestore();
        List<Player> present = game.players();
        List<Player> winners = tracker.matchWinners(ids(present)).stream().map(game::player)
                .filter(java.util.Objects::nonNull).toList();
        List<Player> ranked = new ArrayList<>(present);
        ranked.sort((a, b) -> Integer.compare(tracker.total(b.getUniqueId()), tracker.total(a.getUniqueId())));
        StringBuilder board = new StringBuilder("<gold>Final points:</gold> ");
        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            if (i > 0) board.append("<gray>, ");
            board.append("<yellow>").append(ranked.get(i).getName()).append("</yellow> <white>")
                    .append(tracker.total(ranked.get(i).getUniqueId()));
        }
        game.broadcast(board.toString());
        game.end(winners, "rounds");
    }

    // ---- helpers ---------------------------------------------------------------

    private void announceRound() {
        String sub = "<gray>" + objective;
        for (Player p : game.players()) {
            Text.title(p, "<gold><b>Round " + tracker.round() + " of " + tracker.totalRounds(), sub);
            p.playSound(p.getLocation(), "minecraft:block.note_block.pling", 1f, 1.5f);
        }
    }

    private void park(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        if (game.world() != null) player.teleport(game.arena().spectator(game.world()));
    }

    private void resetPlayer(Player p) {
        p.getInventory().clear();
        for (PotionEffect effect : List.copyOf(p.getActivePotionEffects())) p.removePotionEffect(effect.getType());
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setFoodLevel(20);
        p.setAllowFlight(false);
        p.setGameMode(game.logic().playGameMode());
    }

    private void clearFallingBlocks() {
        if (game.world() == null) return;
        for (FallingBlock block : game.world().getEntitiesByClass(FallingBlock.class)) block.remove();
    }

    private void cancelRestore() {
        if (restoreTask != null) restoreTask.cancel();
        restoreTask = null;
    }

    private static List<UUID> ids(List<Player> players) {
        return players.stream().map(Player::getUniqueId).toList();
    }

    static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) return n + "th";
        return n + switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    /** Sidebar lines for the host game to append. */
    public List<String> sidebar(Player player) {
        return List.of("<gray>Round: <white>" + Math.min(tracker.round(), tracker.totalRounds()) + "/" + tracker.totalRounds(),
                "<gray>Points: <yellow>" + tracker.total(player.getUniqueId()),
                "<gray>Still in: <white>" + tracker.remainingCount());
    }
}
