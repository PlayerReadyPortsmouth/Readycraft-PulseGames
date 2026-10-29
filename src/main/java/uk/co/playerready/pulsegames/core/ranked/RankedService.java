package uk.co.playerready.pulsegames.core.ranked;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.util.Text;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ranked Duels: the queue, pairing, rating changes, seasons and badges. The maths and rules live in the pure
 * classes beside it; this class only joins them to the server.
 */
public final class RankedService {
    /** Player metadata PulseNetwork's tab list can read later: the division name, or "Unranked". */
    public static final String DIVISION_METADATA = "pulsegames.ranked.division";
    private static final Set<String> NO_CHANGE = Set.of("forced", "shutdown", "aborted", "error");

    private final PulseGamesPlugin plugin;
    private Divisions divisions;
    private RankedQueue queue;
    private RepeatDampener dampener;

    /** Two players (or one plus bots) heading into a fresh arena. */
    private static final class Pairing {
        final GameInstance instance; final List<RankedQueue.Ticket> tickets; int polls; boolean cancelled;
        Pairing(GameInstance instance, List<RankedQueue.Ticket> tickets) { this.instance = instance; this.tickets = tickets; }
    }
    private static final class Match {
        final String kit; final List<UUID> humans; final Map<UUID, Long> disconnected = new HashMap<>();
        Match(String kit, List<UUID> humans) { this.kit = kit; this.humans = humans; }
    }
    private record Settlement(String kit, UUID a, String nameA, UUID b, String nameB, double scoreA) { }
    private record Pending(Settlement settlement, long disconnectedMs, BukkitTask task, UUID leaver) { }

    private final Map<UUID, Pairing> pairings = new HashMap<>();
    private final Map<GameInstance, Match> matches = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, List<String>> lastLines = new HashMap<>();
    private final Map<UUID, Long> botCheckedAt = new HashMap<>();
    private BukkitTask ticker;
    private boolean admitting;
    private int ticks;

    public RankedService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var c = plugin.getConfig();
        divisions = Divisions.fromConfig(c);
        var range = new QueueRange(c.getInt("ranked.matchmaking.start-range", 100), c.getInt("ranked.matchmaking.widen-by", 50),
                c.getInt("ranked.matchmaking.widen-every-seconds", 10), c.getInt("ranked.matchmaking.max-range", 400));
        if (queue == null) queue = new RankedQueue(range);
        else { var old = queue.all(); queue = new RankedQueue(range); old.forEach(queue::add); }
        List<Double> f = c.getDoubleList("ranked.repeat-opponent.factors");
        dampener = new RepeatDampener(c.getInt("ranked.repeat-opponent.window-minutes", 10) * 60_000L,
                f.isEmpty() ? new double[]{1.0, 0.5, 0.25} : new double[]{1.0, f.get(0), f.get(f.size() - 1)});
    }

    public void start() {
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getServer().getScheduler().runTask(plugin, this::checkSeason);
    }

    /** Server stop: nothing pending is settled, so no rating moves. */
    public void shutdown() {
        if (ticker != null) ticker.cancel();
        pending.values().forEach(p -> p.task().cancel());
        pending.clear();
        matches.clear();
    }

    // ---- settings -------------------------------------------------------------------------------

    public boolean enabled() { return plugin.getConfig().getBoolean("ranked.enabled", true); }
    public Divisions divisions() { return divisions; }
    public List<String> kits() { return plugin.getConfig().getStringList("ranked.kits"); }
    private boolean allowBots() { return plugin.getConfig().getBoolean("ranked.allow-bots", false); }
    private long graceMs() { return Math.max(0, plugin.getConfig().getInt("ranked.disconnect-grace-seconds", 30)) * 1000L; }
    public RankedStore store() { return plugin.stats().ranked(); }
    public String seasonId() { return plugin.getConfig().getString("ranked.season.id", "1"); }
    public boolean seasonOpen() { return !store().seasonClosed(); }

    public RankedStore.Rating rating(UUID id, String kit) { return store().get(id, kit); }
    public RankedQueue.Ticket queued(Player p) { return queue.get(p.getUniqueId()); }

    // ---- queue ----------------------------------------------------------------------------------

    public void join(Player player, String kit) {
        if (!enabled() || !kits().contains(kit)) { player.sendMessage(Text.msg("<red>Ranked is not available for that kit.")); return; }
        if (!seasonOpen()) { player.sendMessage(Text.msg("<yellow>Ranked is resting between seasons. A new one starts soon!")); return; }
        var type = plugin.registry().get("duels");
        if (type == null || !plugin.gameAvailable(type)) { player.sendMessage(Text.msg("<red>Duels is not enabled yet.")); return; }
        if (queue.get(player.getUniqueId()) != null || pairings.containsKey(player.getUniqueId())) {
            player.sendMessage(Text.msg("<yellow>You are already searching. Use <white>/ranked leave</white> to stop.")); return;
        }
        var party = plugin.parties().partyOf(player);
        if (party != null && party.size() > 1) { player.sendMessage(Text.msg("<red>Ranked is one against one. Leave your party to play.")); return; }
        GameInstance current = plugin.instances().byPlayer(player);
        if (current != null && current.state() != GameState.ENDING) {
            player.sendMessage(Text.msg("<red>Leave your current game first.")); return;
        }
        GameMode mode = plugin.duelMenu().mode(GameMode.CombatStyle.MODERN, kit, 1);
        if (mode == null || plugin.kits().duelChoices(mode).isEmpty() || plugin.duelMenu().kitFor(player, mode) == null) {
            player.sendMessage(Text.msg("<red>You have no usable kit for this one.")); return;
        }
        plugin.playService().cancelPending(player);
        lastLines.remove(player.getUniqueId());
        queue.add(new RankedQueue.Ticket(player.getUniqueId(), kit, rating(player.getUniqueId(), kit).rating(), System.currentTimeMillis()));
        player.sendMessage(Text.msg("<green>Searching for a <yellow>" + RankedText.kitName(kit) + "</yellow> ranked match. Use <white>/ranked leave</white> to stop."));
        showSearching(player);
    }

    /** Stops a search (also called when the player leaves, quits or joins another game). */
    public void leaveQueue(Player player) {
        if (admitting) return;
        UUID id = player.getUniqueId();
        boolean was = queue.remove(id) != null;
        Pairing pairing = pairings.get(id);
        if (pairing != null) cancelPairing(pairing, id);
        if (was || pairing != null) {
            player.sendMessage(Text.msg("<yellow>You left the ranked queue."));
            if (plugin.instances().byPlayer(player) == null) plugin.sidebar().clear(player);
        }
    }

    private void cancelPairing(Pairing pairing, UUID leaver) {
        if (pairing.cancelled) return;
        pairing.cancelled = true;
        pairing.tickets.forEach(t -> pairings.remove(t.player()));
        if (pairing.instance.players().isEmpty()) pairing.instance.cleanup();
        for (var t : pairing.tickets) {
            Player other = Bukkit.getPlayer(t.player());
            if (t.player().equals(leaver) || other == null || !other.isOnline()) continue;
            queue.add(t);
            other.sendMessage(Text.msg("<yellow>Your opponent left. Searching again..."));
        }
    }

    private void showSearching(Player player) {
        var t = queue.get(player.getUniqueId());
        if (t == null) return;
        var r = rating(player.getUniqueId(), t.kit());
        plugin.sidebar().show(player, "<gradient:#ff5f6d:#ffc371><b>RANKED</b></gradient>", List.of(
                "<dark_gray>" + RankedText.kitName(t.kit()), "",
                "<yellow>" + RankedText.searching(queue.rangeFor(t, System.currentTimeMillis())),
                "<gray>" + RankedText.standing(divisions, r), "",
                "<gray>/ranked leave to stop", "<dark_gray>readycraft"));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (var pair : queue.match(now)) startPairing(List.of(pair.a(), pair.b()));
        for (var t : queue.all()) {
            Player p = Bukkit.getPlayer(t.player());
            if (p == null || !p.isOnline()) { queue.remove(t.player()); continue; }
            showSearching(p);
            if (allowBots() && now - t.joinedMs() >= plugin.getConfig().getInt("ranked.bot-after-seconds", 15) * 1000L
                    && now - botCheckedAt.getOrDefault(t.player(), 0L) >= 30_000L) {
                botCheckedAt.put(t.player(), now);
                if (queue.remove(t.player()) != null && !startPairing(List.of(t))) queue.add(t);
            }
        }
        botCheckedAt.keySet().removeIf(id -> queue.get(id) == null);
        if (++ticks % 60 == 0) checkSeason();
    }

    /** Creates the arena for these tickets (two humans, or one human who will be given rating-matched bots). */
    private boolean startPairing(List<RankedQueue.Ticket> tickets) {
        var type = plugin.registry().get("duels");
        String kit = tickets.getFirst().kit();
        boolean bots = tickets.size() == 1;
        GameMode mode = plugin.duelMenu().mode(GameMode.CombatStyle.MODERN, kit, 1);
        var instance = type == null || mode == null ? null : plugin.instances().create(type, mode, tickets.size());
        if (instance == null) {
            requeue(tickets, "<yellow>No arena is free right now. Still searching...");
            return false;
        }
        if (bots && (plugin.bots() == null || !plugin.bots().eligible(instance))) {
            instance.cleanup();
            return false;
        }
        BotDifficulty tier = !bots ? null : BotTier.forRating(tickets.getFirst().rating(),
                plugin.getConfig().getInt("ranked.bots.easy-rating", 900), plugin.getConfig().getInt("ranked.bots.normal-rating", 1200),
                plugin.getConfig().getInt("ranked.bots.hard-rating", 1500));
        instance.markRanked(kit, tickets.stream().map(RankedQueue.Ticket::player).toList(), tier);
        Pairing pairing = new Pairing(instance, tickets);
        tickets.forEach(t -> {
            pairings.put(t.player(), pairing);
            Player p = Bukkit.getPlayer(t.player());
            if (p != null) p.sendMessage(Text.msg(bots ? "<green>No match nearby, so a practice opponent is joining. This game will not change your rating."
                    : "<green>Opponent found! Loading your arena..."));
        });
        poll(pairing);
        return true;
    }

    private void requeue(List<RankedQueue.Ticket> tickets, String message) {
        for (var t : tickets) {
            Player p = Bukkit.getPlayer(t.player());
            if (p == null || !p.isOnline()) continue;
            queue.add(t);
            p.sendMessage(Text.msg(message));
        }
    }

    private void poll(Pairing pairing) {
        if (pairing.cancelled) return;
        var state = pairing.instance.state();
        if (state == GameState.WAITING) { admit(pairing); return; }
        if (state != GameState.LOADING || ++pairing.polls > 60) {
            pairing.cancelled = true;
            pairing.tickets.forEach(t -> pairings.remove(t.player()));
            pairing.instance.cleanup();
            requeue(pairing.tickets, "<yellow>The arena did not load. Searching again...");
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> poll(pairing), 10L);
    }

    private void admit(Pairing pairing) {
        admitting = true;
        try {
            for (var t : pairing.tickets) {
                Player p = Bukkit.getPlayer(t.player());
                if (p != null && p.isOnline()) plugin.playService().joinNetworkQueue(p, pairing.instance.id());
            }
        } finally {
            admitting = false;
        }
        pairing.tickets.forEach(t -> pairings.remove(t.player()));
    }

    // ---- match life -------------------------------------------------------------------------------

    /** Called by the Duels logic when the first round starts. */
    public void matchStarted(GameInstance game) {
        if (!game.rankedMatch()) return;
        List<UUID> humans = game.players().stream().filter(p -> !game.isBot(p)).map(Player::getUniqueId).toList();
        matches.put(game, new Match(game.rankedKit(), humans));
        humans.forEach(lastLines::remove);
        if (humans.size() == 2)
            game.broadcast("<gray>Ranked match: <yellow>" + RankedText.kitName(game.rankedKit()) + "</yellow>. Good luck!");
    }

    /** A human left a running match. An explicit leave is a forfeit; a disconnect gets the grace period. */
    public void departed(GameInstance game, Player player, boolean explicit) {
        Match m = matches.get(game);
        if (m != null && !explicit) m.disconnected.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void matchEnded(GameInstance game, List<Player> winners, String reason) {
        Match m = matches.remove(game);
        if (m == null || m.humans.size() < 2) return;
        if (NO_CHANGE.contains(reason)) {
            m.humans.forEach(id -> tell(id, "<yellow>This match ended early, so ratings did not change."));
            return;
        }
        UUID a = m.humans.get(0), b = m.humans.get(1);
        boolean aWon = winners.stream().anyMatch(p -> p.getUniqueId().equals(a));
        boolean bWon = winners.stream().anyMatch(p -> p.getUniqueId().equals(b));
        double scoreA = aWon && !bWon ? 1.0 : bWon && !aWon ? 0.0 : 0.5;
        var settlement = new Settlement(m.kit, a, nameOf(game, a), b, nameOf(game, b), scoreA);
        var away = m.disconnected.entrySet().stream().findFirst().orElse(null);
        if (away == null) { settle(settlement); return; }
        long now = System.currentTimeMillis();
        long wait = DisconnectGrace.remainingMs(away.getValue(), now, graceMs());
        if (wait <= 0) { settle(settlement); return; }
        UUID leaver = away.getKey();
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> resolve(leaver, -1L), Math.max(1L, wait / 50L) + 1L);
        pending.put(leaver, new Pending(settlement, away.getValue(), task, leaver));
        String waitLine = "<yellow>Your opponent lost connection. Your result confirms in " + (wait / 1000 + 1) + " seconds, in case they come back.";
        UUID other = leaver.equals(a) ? b : a;
        tell(other, waitLine);
        lastLines.put(other, List.of("Rating update pending: opponent may reconnect"));
    }

    /** A pending disconnect result is settled, or voided if the player came back in time. */
    private void resolve(UUID leaver, long reconnectedMs) {
        Pending p = pending.remove(leaver);
        if (p == null) return;
        p.task().cancel();
        var verdict = DisconnectGrace.verdict(p.disconnectedMs(), reconnectedMs, System.currentTimeMillis(), graceMs());
        if (verdict == DisconnectGrace.Verdict.VOID) {
            for (UUID id : List.of(p.settlement().a(), p.settlement().b())) {
                tell(id, "<yellow>The connection came back in time, so this match does not change anyone's rating.");
                lastLines.put(id, List.of("No rating change: connection restored"));
            }
        } else settle(p.settlement());
    }

    public void reconnected(Player player) {
        if (pending.containsKey(player.getUniqueId())) resolve(player.getUniqueId(), System.currentTimeMillis());
        refreshMetadata(player);
    }

    private void settle(Settlement s) {
        long now = System.currentTimeMillis();
        var st = store();
        var ra = st.get(s.a(), s.kit());
        var rb = st.get(s.b(), s.kit());
        double factor = dampener.factor(s.a(), s.b(), now);
        dampener.record(s.a(), s.b(), now);
        int da = Elo.delta(ra.rating(), rb.rating(), s.scoreA(), ra.games(), factor);
        int db = Elo.delta(rb.rating(), ra.rating(), 1.0 - s.scoreA(), rb.games(), factor);
        applyResult(s.a(), s.nameA(), s.kit(), ra, da, s.scoreA(), s.nameB(), factor, now);
        applyResult(s.b(), s.nameB(), s.kit(), rb, db, 1.0 - s.scoreA(), s.nameA(), factor, now);
    }

    private void applyResult(UUID id, String name, String kit, RankedStore.Rating old, int delta, double score,
                             String opponent, double factor, long nowMs) {
        int rating = Elo.apply(old.rating(), delta);
        var next = new RankedStore.Rating(rating, old.games() + 1, Math.max(old.peak(), rating));
        store().put(id, name, kit, next);
        store().addHistory(id, RankedText.historyRecord(kit, score, rating - old.rating(), opponent, nowMs / 1000));
        List<String> lines = new ArrayList<>();
        if (!Elo.placed(next.games())) lines.add("Placement game " + next.games() + " of " + Elo.PLACEMENT_GAMES + ": rank shown after that");
        else lines.add("Rating " + old.rating() + " to " + rating + " (" + RankedText.signed(rating - old.rating()) + "), "
                + divisions.of(rating).name() + (factor < 1.0 ? ". Smaller change: you played them recently" : ""));
        lastLines.put(id, lines);
        tell(id, "<gold>Ranked: <white>" + lines.getFirst());
        Player p = Bukkit.getPlayer(id);
        if (p != null) refreshMetadata(p);
    }

    private void tell(UUID id, String message) {
        Player p = Bukkit.getPlayer(id);
        if (p != null && p.isOnline()) p.sendMessage(Text.msg(message));
    }

    private static String nameOf(GameInstance game, UUID id) {
        Player p = game.player(id);
        return p != null ? p.getName() : "Player";
    }

    /** Lines for the results screen: the rating change from the player's last ranked match. */
    public List<String> resultLines(Player player) { return lastLines.getOrDefault(player.getUniqueId(), List.of()); }

    // ---- badges -------------------------------------------------------------------------------------

    /** MiniMessage badge for a player in one kit; empty while unranked. */
    public String badge(UUID id, String kit) {
        var r = store().get(id, kit);
        return divisions.badge(r.rating(), r.games());
    }

    /** The player's best division across kits, or "Unranked". */
    public String bestDivision(UUID id) {
        String best = Divisions.UNRANKED;
        int top = -1;
        for (String kit : kits()) {
            var r = store().get(id, kit);
            if (Elo.placed(r.games()) && r.rating() > top) { top = r.rating(); best = divisions.of(r.rating()).name(); }
        }
        return best;
    }

    public void refreshMetadata(Player player) {
        if (player.hasMetadata("NPC")) return;
        player.setMetadata(DIVISION_METADATA, new FixedMetadataValue(plugin, bestDivision(player.getUniqueId())));
    }

    public void clearMetadata(Player player) { player.removeMetadata(DIVISION_METADATA, plugin); }

    /** Division tag before each human's name on every duellist's private scoreboard (never chat). */
    public void refreshBadges(GameInstance game) {
        if (!enabled() || !game.type().id().equals("duels")) return;
        String kit = game.mode().rulesId();
        List<Player> humans = game.players().stream().filter(p -> !game.isBot(p)).toList();
        var main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player viewer : humans) {
            var board = viewer.getScoreboard();
            if (board == main) continue;
            for (Player who : humans) {
                String teamName = "rk" + who.getUniqueId().toString().substring(0, 8);
                var team = board.getTeam(teamName);
                if (team == null) team = board.registerNewTeam(teamName);
                var prefix = Text.mm(badge(who.getUniqueId(), kit));
                if (!prefix.equals(team.prefix())) team.prefix(prefix);
                if (!team.hasEntry(who.getName())) team.addEntry(who.getName());
            }
        }
    }

    // ---- seasons ------------------------------------------------------------------------------------

    public void checkSeason() {
        var st = store();
        String config = seasonId();
        var action = SeasonRules.decide(st.seasonId(), st.seasonClosed(), config,
                SeasonRules.parseEnds(plugin.getConfig().getString("ranked.season.ends")), LocalDate.now(ZoneOffset.UTC));
        switch (action) {
            case ADOPT -> st.setSeason(config, false);
            case ROLLOVER -> {
                st.archiveAndSoftReset(st.seasonId(), divisions);
                st.setSeason(config, false);
                announce("<gold><b>Ranked season " + config + " has begun!</b> <gray>Everyone's rating moved halfway back to 1000. Good luck!");
            }
            case END -> {
                String ended = st.seasonId();
                st.archiveAndSoftReset(ended, divisions);
                st.setSeason(ended, true);
                queue.all().forEach(t -> { var p = Bukkit.getPlayer(t.player()); if (p != null) leaveQueue(p); });
                announce("<gold><b>Ranked season " + ended + " has ended!</b> <gray>Your best division is saved. The next season starts soon.");
            }
            case NONE -> { }
        }
        if (action != SeasonRules.Action.NONE) Bukkit.getOnlinePlayers().forEach(this::refreshMetadata);
    }

    private void announce(String message) { Bukkit.getServer().broadcast(Text.msg(message)); }

    public Set<UUID> queuedPlayers() {
        Set<UUID> ids = new HashSet<>();
        queue.all().forEach(t -> ids.add(t.player()));
        return ids;
    }
}
