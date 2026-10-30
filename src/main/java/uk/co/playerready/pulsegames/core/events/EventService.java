package uk.co.playerready.pulsegames.core.events;

import uk.co.playerready.pulsegames.core.command.Routes;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import uk.co.playerready.pulsegames.core.events.EventSchedule.Format;
import uk.co.playerready.pulsegames.core.events.EventSchedule.Pick;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.util.Text;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduled events: an hourly featured tournament (bracket or points) on private rounds of an existing game.
 * Joining is always opt-in (/event join, or the queue link from another server); nobody is moved unless they
 * signed up, and the only cross-server effect is a queue row plus chat lines. Everything runs on the main thread.
 */
public final class EventService {

    enum Phase { SIGNUP, RUNNING }

    record Entrant(String id, List<UUID> members, String label) { }

    /** One private round: a bracket match (two sides) or one points round (everyone). */
    private static final class Live {
        final Bracket.Match match;
        final List<Entrant> sides;
        GameInstance game;
        long createdAt, startAt;
        int tries;
        boolean admitted, resolved, done;
        Set<UUID> winners = Set.of();
        Map<UUID, Integer> places = Map.of(), scores = Map.of();
        Entrant forfeitWinner;

        Live(Bracket.Match match, List<Entrant> sides) { this.match = match; this.sides = sides; }
    }

    private final class Event {
        final String id;
        final Instant start;
        final Pick pick;
        final GameType type;
        final GameMode mode;
        Phase phase = Phase.SIGNUP;
        final Set<UUID> signups = new LinkedHashSet<>();
        final Set<UUID> withdrawn = new HashSet<>();
        final Map<UUID, String> names = new LinkedHashMap<>();
        final Map<String, Entrant> entrants = new LinkedHashMap<>();
        final List<Live> live = new ArrayList<>();
        Bracket bracket;
        PointsTally tally;
        int round;
        long nextRoundAt;
        int humans;
        boolean cancelled;

        Event(Instant start, Pick pick, GameType type, GameMode mode) {
            this.id = "event-" + start.getEpochSecond() / 60;
            this.start = start;
            this.pick = pick;
            this.type = type;
            this.mode = mode;
        }

        String title() { return type.displayName() + (mode.id().equals(type.defaultMode().id()) ? "" : " " + mode.displayName()); }
    }

    private final PulseGamesPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Random random = new Random();
    private final Set<UUID> sidebarShown = new HashSet<>();
    private EventSchedule schedule = new EventSchedule(ZoneId.of("Europe/London"), List.of(), List.of());
    private Event event;
    private Instant lastOpened = Instant.EPOCH;
    private final EventMenu menu;

    public EventService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "events.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.menu = new EventMenu(plugin, this);
        load();
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public EventMenu menu() { return menu; }
    public ZoneId zone() { return schedule.zone(); }
    public boolean enabled() { return plugin.getConfig().getBoolean("events.enabled", true); }

    // ---- configuration -------------------------------------------------------------

    /** (Re)reads events.* from config.yml; bad lines are skipped with a warning, never fatal. */
    public void load() {
        var cfg = plugin.getConfig();
        ZoneId zone;
        try { zone = ZoneId.of(cfg.getString("events.timezone", "Europe/London")); }
        catch (RuntimeException bad) { plugin.getLogger().warning("events.timezone is not valid; using Europe/London"); zone = ZoneId.of("Europe/London"); }
        double multiplier = cfg.getDouble("events.tokens-multiplier", 2.0);
        List<Pick> rotation = new ArrayList<>();
        for (Map<?, ?> raw : cfg.getMapList("events.games")) {
            Pick pick = EventSchedule.parsePick(raw, multiplier);
            if (pick == null) plugin.getLogger().warning("events.games: skipped an entry without a valid game/format");
            else rotation.add(pick);
        }
        List<EventSchedule.Entry> entries = new ArrayList<>();
        for (Map<?, ?> raw : cfg.getMapList("events.schedule")) {
            var at = raw.get("at") == null ? null : EventSchedule.parseAt(String.valueOf(raw.get("at")));
            var every = raw.get("every") == null ? null : EventSchedule.parseEvery(String.valueOf(raw.get("every")));
            if ((at == null) == (every == null)) {
                plugin.getLogger().warning("events.schedule: each line needs exactly one valid 'at: HH:mm' or 'every: 60m' (15m to 24h)");
                continue;
            }
            Pick fixed = raw.get("game") == null ? null : EventSchedule.parsePick(raw, multiplier);
            if (raw.get("game") != null && fixed == null) {
                plugin.getLogger().warning("events.schedule: skipped a line with an invalid format");
                continue;
            }
            entries.add(new EventSchedule.Entry(at, every, fixed));
        }
        schedule = new EventSchedule(zone, entries, rotation);
    }

    private int lead() { return Math.max(30, plugin.getConfig().getInt("events.announce-lead-minutes", 5) * 60); }
    private int maxPlayers() { return Math.max(2, Math.min(16, plugin.getConfig().getInt("events.max-players", 16))); }
    private int minPlayers() { return Math.max(2, plugin.getConfig().getInt("events.min-players", 2)); }

    // ---- queries (menus, commands, network helper) ----------------------------------

    public List<EventSchedule.Occurrence> upcoming(int count) {
        return enabled() ? schedule.next(Instant.now(), count) : List.of();
    }

    /** Short description of a pick for lists: "Spleef (points)". */
    public String describe(Pick pick) {
        GameType type = plugin.registry().get(pick.game());
        String name = type == null ? pick.game() : type.displayName();
        GameMode mode = type == null || pick.mode() == null ? null : type.mode(pick.mode());
        return name + (mode != null && !mode.id().equals(type.defaultMode().id()) ? " " + mode.displayName() : "")
                + " (" + (pick.format() == Format.BRACKET ? "bracket" : "points") + ")";
    }

    public boolean active() { return event != null; }
    public boolean signupOpen() { return event != null && event.phase == Phase.SIGNUP; }
    public boolean running() { return event != null && event.phase == Phase.RUNNING; }
    public boolean bracketRunning() { return running() && event.bracket != null; }
    public boolean pointsRunning() { return running() && event.tally != null; }
    public boolean signedUp(Player p) { return event != null && event.signups.contains(p.getUniqueId()) && !event.withdrawn.contains(p.getUniqueId()); }
    public int signupCount() { return event == null ? 0 : event.signups.size(); }
    public List<String> lastResult() { return data.getStringList("last-result"); }
    public String lastResultTitle() { return data.getString("last-result-title", ""); }

    public String statusLine() {
        if (event == null) return "No tournament right now.";
        String name = event.title() + (event.pick.format() == Format.BRACKET ? " bracket" : " points");
        if (event.phase == Phase.SIGNUP)
            return name + " starts in " + EventText.countdown(event.start.getEpochSecond() - Instant.now().getEpochSecond());
        return name + " is under way: " + (event.bracket != null ? "round " + (currentRoundNumber() + 1)
                : "round " + Math.min(event.round, event.pick.rounds()) + " of " + event.pick.rounds());
    }

    private int currentRoundNumber() {
        for (var row : event.bracket.rounds()) if (row.stream().anyMatch(m -> !m.decided())) return row.getFirst().round();
        return event.bracket.rounds().size() - 1;
    }

    /** "Tournament winner" for the week, shown in the menu and announcements. */
    public String weeklyChampions() {
        long cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        List<String> names = new ArrayList<>();
        var section = data.getConfigurationSection("champions");
        if (section != null) for (String key : section.getKeys(false))
            if (section.getLong(key + ".at") >= cutoff) names.add(section.getString(key + ".name", "A player"));
        return String.join(", ", names);
    }

    public boolean isChampion(UUID id) {
        return data.getLong("champions." + id + ".at") >= System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
    }

    /** Live bracket matches as (label, game) for the spectator buttons. */
    public Map<String, GameInstance> watchable() {
        Map<String, GameInstance> out = new LinkedHashMap<>();
        if (event == null) return out;
        for (Live l : event.live)
            if (l.game != null && l.game.state() == GameState.RUNNING && !l.done)
                out.put(String.join(" vs ", l.sides.stream().map(Entrant::label).toList()), l.game);
        return out;
    }

    public Bracket bracket() { return event == null ? null : event.bracket; }
    public PointsTally tally() { return event == null ? null : event.tally; }
    public String labelOf(String entrantId) {
        Entrant e = event == null ? null : event.entrants.get(entrantId);
        return e == null ? "?" : e.label();
    }

    // ---- network helper hooks ---------------------------------------------------------

    /**
     * One queue row while sign-up is open. PulseNetwork already carries queue rows to the proxy over its signed
     * channel and announces a new one with a Join link, so no Velocity change is needed. TODO(PulseNetwork): a
     * dedicated ANNOUNCE message kind (Wire + VelocityBridge) would allow a custom "starting in 5 minutes" line.
     */
    public List<Map<String, String>> networkRows() {
        if (!signupOpen() || !plugin.getConfig().getBoolean("events.network-announce", true)) return List.of();
        return List.of(Map.of("id", event.id,
                "title", "Tournament: " + event.title() + " at " + EventText.when(Instant.now(), event.start, zone()),
                "players", Integer.toString(Math.max(1, Math.min(maxPlayers(), event.signups.size()))),
                "maximum", Integer.toString(maxPlayers())));
    }

    public boolean ownsQueue(String id) { return signupOpen() && event.id.equals(id); }

    /** The proxy must not send a signed-up player home between rounds. */
    public boolean holdsPlayer(Player player) { return signedUp(player); }

    // ---- player actions --------------------------------------------------------------

    public void join(Player player) {
        if (!signupOpen()) {
            player.sendMessage(Text.msg(running() ? "<yellow>The tournament has already started. You can watch from /event."
                    : "<yellow>There is no tournament to join right now. See /event for the next one."));
            return;
        }
        UUID id = player.getUniqueId();
        if (event.signups.contains(id) && !event.withdrawn.contains(id)) {
            player.sendMessage(Text.msg("<yellow>You are already in. Stay in the lobby and we will bring you to the arena."));
            return;
        }
        if (plugin.instances().byPlayer(player) != null || plugin.playService().hasPending(player)) {
            player.sendMessage(Text.msg("<red>Leave your game or queue first, then try again."));
            return;
        }
        if (event.signups.size() >= maxPlayers()) {
            player.sendMessage(Text.msg("<red>The tournament is full. You can still watch from /event."));
            return;
        }
        event.withdrawn.remove(id);
        event.signups.add(id);
        event.names.put(id, player.getName());
        player.sendMessage(Text.msg("<green>You are in the " + event.title() + " tournament!</green> <gray>It starts in "
                + EventText.countdown(event.start.getEpochSecond() - Instant.now().getEpochSecond())
                + ". Stay in the lobby. <yellow>/event leave</yellow> changes your mind."));
    }

    public void leave(Player player) {
        if (event == null || !event.signups.contains(player.getUniqueId())) {
            player.sendMessage(Text.msg("<yellow>You are not in a tournament."));
            return;
        }
        if (signupOpen()) event.signups.remove(player.getUniqueId());
        else event.withdrawn.add(player.getUniqueId());
        player.sendMessage(Text.msg("<yellow>You have left the tournament. Thanks for letting us know!"));
    }

    public void command(Player player, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> join(player);
            case "leave" -> leave(player);
            case "bracket" -> menu.openBracket(player);
            case "results" -> menu.openResults(player);
            default -> menu.open(player);
        }
    }

    // ---- admin ----------------------------------------------------------------------

    /** Start of the latest occurrence that has opened or been skipped; anything at or before it will not open. */
    public Instant lastOpened() { return lastOpened; }

    /** Admin menu: passes over the next scheduled start as if it had already opened. Returns it, or null when none is due. */
    public Instant skipNext() {
        if (!enabled()) return null;
        for (var o : schedule.next(Instant.now(), 5)) {
            if (!o.start().isAfter(lastOpened)) continue;
            lastOpened = o.start();
            return o.start();
        }
        return null;
    }

    public void admin(Player player, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                player.sendMessage(Text.msg("Event: <white>" + statusLine()));
                Instant now = Instant.now();
                for (var o : schedule.next(now, 5)) player.sendMessage(Text.mm(" <gray>- <yellow>"
                        + EventText.when(now, o.start(), zone()) + "</yellow> " + describe(o.pick())));
                if (schedule.isEmpty()) player.sendMessage(Text.msg("<gray>The schedule is empty (events.schedule / events.games)."));
            }
            case "cancel" -> {
                if (event == null) { player.sendMessage(Text.msg("<red>No event is active.")); return; }
                cancel(event, "A host stopped the tournament. Thanks for joining in!");
                player.sendMessage(Text.msg("Event cancelled."));
            }
            case "start" -> {
                if (event != null) { player.sendMessage(Text.msg("<red>An event is already active. /admin games event cancel first.")); return; }
                if (args.length < 3) { player.sendMessage(Text.msg("<red>Usage: /admin games event start <game> <mode> [bracket|points]")); return; }
                Format format = args.length > 3 && args[3].equalsIgnoreCase("bracket") ? Format.BRACKET : Format.POINTS;
                if (args.length > 3 && !args[3].equalsIgnoreCase("bracket") && !args[3].equalsIgnoreCase("points")) {
                    player.sendMessage(Text.msg("<red>Format must be bracket or points.")); return;
                }
                Pick pick = new Pick(args[1].toLowerCase(Locale.ROOT), args[2], format, 3,
                        Math.max(1.0, plugin.getConfig().getDouble("events.tokens-multiplier", 2.0)));
                Instant when = Instant.now().plusSeconds(Math.max(15, plugin.getConfig().getInt("events.admin-lead-seconds", 60)));
                if (!open(when, List.of(pick), true)) player.sendMessage(Text.msg("<red>That game or mode is not available, or has no maps."));
                else player.sendMessage(Text.msg("Tournament opened. It starts in " + plugin.getConfig().getInt("events.admin-lead-seconds", 60) + " seconds."));
            }
            default -> player.sendMessage(Text.msg("Usage: <yellow>/admin games event <start <game> <mode> [bracket|points]|cancel|list>"));
        }
    }

    // ---- main loop ------------------------------------------------------------------

    private void tick() {
        try {
            if (!enabled()) { if (event != null) cancel(event, "Tournaments are switched off for now."); return; }
            Instant now = Instant.now();
            if (event == null) { maybeOpen(now); return; }
            if (event.phase == Phase.SIGNUP) signupTick(now); else runTick(now);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Scheduled event failed", ex);
            if (event != null) cancel(event, "Something went wrong, so the tournament was stopped. Sorry about that!");
        }
    }

    private void maybeOpen(Instant now) {
        var next = schedule.next(now, 1);
        if (next.isEmpty()) return;
        var occ = next.getFirst();
        if (!occ.start().isAfter(lastOpened) || occ.start().getEpochSecond() - now.getEpochSecond() > lead()) return;
        lastOpened = occ.start();
        if (!open(occ.start(), occ.choices(), false)) plugin.getLogger().warning("Scheduled event at " + occ.start() + " skipped: no available game");
    }

    private record Resolved(GameType type, GameMode mode, Pick pick) { }

    private Resolved resolve(Pick pick, boolean admin) {
        GameType type = plugin.registry().get(pick.game());
        if (type == null || !plugin.gameAvailable(type) || !admin && type.wip() && !plugin.gamePublic(type)) return null;
        GameMode mode = pick.mode() == null ? type.defaultMode() : type.mode(pick.mode());
        if (mode == null || plugin.arenas().forGame(type.id(), mode.id()).isEmpty()) return null;
        if (mode.maxPlayers() < 2) return null;
        return new Resolved(type, mode, pick);
    }

    private boolean open(Instant start, List<Pick> choices, boolean admin) {
        Resolved r = null;
        for (Pick pick : choices) if ((r = resolve(pick, admin)) != null) break;
        if (r == null) return false;
        event = new Event(start, r.pick(), r.type(), r.mode());
        lastOpened = start.isAfter(lastOpened) ? start : lastOpened;
        long seconds = Math.max(0, start.getEpochSecond() - Instant.now().getEpochSecond());
        String when = seconds >= 90 ? Math.round(seconds / 60.0) + " minutes" : seconds + " seconds";
        String kind = event.pick.format() == Format.BRACKET ? "knockout bracket" : event.pick.rounds() + " rounds of points";
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasMetadata("NPC")) continue;
            p.sendMessage(Text.msg("<gold><b>Tournament starting in " + when + " on Minigames: " + event.title()
                    + "!</b> <gray>(" + kind + ", tokens x" + trim(event.pick.multiplier()) + ")"));
            p.sendMessage(Text.mm("<gray>   Join with <yellow>/event join</yellow> or <click:run_command:'" + Routes.slash(Routes.EVENT_JOIN) + "'><hover:show_text:'Join the tournament'>"
                    + "<green><b>[Join]</b></green></hover></click> <gray>- or use <yellow>/queues</yellow> from another server. Joining is optional."));
            if (plugin.instances().byPlayer(p) == null)
                Text.title(p, "<gold><b>Tournament time!", "<gray>" + event.title() + " in " + when + " - /event join");
        }
        return true;
    }

    private static String trim(double d) { return d == Math.rint(d) ? Integer.toString((int) d) : Double.toString(d); }

    private void signupTick(Instant now) {
        long left = event.start.getEpochSecond() - now.getEpochSecond();
        if (left <= 0) { begin(); return; }
        if (left == 60 || left == 10) for (UUID id : event.signups) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(Text.msg("<gold>Tournament: <white>" + event.title() + "</white> starts in " + EventText.countdown(left) + ". Stay in the lobby!"));
        }
        if (!plugin.getConfig().getBoolean("events.sidebar", true)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasMetadata("NPC") || plugin.instances().byPlayer(p) != null) { sidebarShown.remove(p.getUniqueId()); continue; }
            plugin.sidebar().show(p, "<gold><b>Tournament</b>", List.of(
                    "<white>" + event.title() + " in <yellow>" + EventText.countdown(left),
                    signedUp(p) ? "<green>You are in!" : "<aqua>[Join] <gray>/event join",
                    "<gray>Players: <white>" + event.signups.size()));
            sidebarShown.add(p.getUniqueId());
        }
    }

    private void clearSidebars() {
        for (UUID id : sidebarShown) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && plugin.instances().byPlayer(p) == null) plugin.sidebar().clear(p);
        }
        sidebarShown.clear();
    }

    private void tellSignups(Event e, String miniMessage) {
        for (UUID id : e.signups) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(Text.msg(miniMessage));
        }
    }

    private void begin() {
        Event e = event;
        clearSidebars();
        List<Player> ready = new ArrayList<>();
        for (UUID id : e.signups) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || e.withdrawn.contains(id)) continue;
            if (plugin.instances().byPlayer(p) != null || plugin.playService().hasPending(p)) {
                p.sendMessage(Text.msg("<yellow>The tournament started while you were in a game, so you missed it. See you next time!"));
                continue;
            }
            ready.add(p);
        }
        if (ready.size() < minPlayers()) {
            cancel(e, "The tournament was cancelled because not enough players joined. Another one will be along soon!");
            return;
        }
        ready.forEach(p -> e.names.put(p.getUniqueId(), p.getName()));
        Collections.shuffle(ready, random);
        int team = e.pick.format() == Format.BRACKET && e.mode.isTeams() ? e.mode.teamSize() : 1;
        for (int i = 0; i + team <= ready.size(); i += team) {
            List<Player> group = ready.subList(i, i + team);
            String label = String.join(" & ", group.stream().map(Player::getName).toList());
            e.entrants.put(group.getFirst().getUniqueId().toString(), new Entrant(group.getFirst().getUniqueId().toString(),
                    group.stream().map(Player::getUniqueId).toList(), label));
        }
        if (ready.size() % team != 0) for (Player p : ready.subList(ready.size() - ready.size() % team, ready.size()))
            p.sendMessage(Text.msg("<yellow>There was no partner for you this time, so you are out of the bracket. You can still watch from /event."));
        if (e.entrants.size() < 2) {
            cancel(e, "The tournament was cancelled because there were not enough full teams. Another one will be along soon!");
            return;
        }
        e.humans = e.entrants.values().stream().mapToInt(x -> x.members().size()).sum();
        e.phase = Phase.RUNNING;
        List<String> ids = new ArrayList<>(e.entrants.keySet());
        if (e.pick.format() == Format.BRACKET) {
            e.bracket = Bracket.of(ids, plugin.getConfig().getBoolean("events.third-place-match", true));
        } else {
            e.tally = new PointsTally(plugin.getConfig().getIntegerList("events.rewards.points-table").isEmpty()
                    ? List.of(10, 7, 5, 3, 2, 1) : plugin.getConfig().getIntegerList("events.rewards.points-table"), ids);
        }
        for (Player p : Bukkit.getOnlinePlayers()) if (!p.hasMetadata("NPC"))
            p.sendMessage(Text.msg("<gold>The " + e.title() + " tournament has begun with <white>" + e.entrants.size()
                    + "</white> " + (team > 1 ? "teams" : "players") + "! <gray>Watch with /event."));
        e.nextRoundAt = 0;
    }

    private void runTick(Instant now) {
        Event e = event;
        long ms = now.toEpochMilli();
        for (Live l : List.copyOf(e.live)) {
            tickLive(e, l, ms);
            if (event != e) return;
        }
        if (!e.live.isEmpty() && e.live.stream().allMatch(l -> l.done)) {
            e.live.clear();
            if (e.bracket != null && e.bracket.finished() || e.tally != null && e.round >= e.pick.rounds()) { finish(e); return; }
            e.nextRoundAt = ms + plugin.getConfig().getInt("events.between-rounds-seconds", 15) * 1000L;
            announceNext(e);
        }
        if (e.live.isEmpty() && ms >= e.nextRoundAt) startRound(e, ms);
    }

    private void announceNext(Event e) {
        if (e.bracket != null) {
            for (var m : e.bracket.playable()) tellSignups(e, "<gold>Next up: <white>" + e.entrants.get(m.a()).label() + " vs " + e.entrants.get(m.b()).label()
                    + (m.thirdPlace() ? " (third place)" : ""));
        } else tellSignups(e, "<gold>Round " + (e.round + 1) + " of " + e.pick.rounds() + " is coming up. Get ready!");
    }

    private void startRound(Event e, long ms) {
        if (e.bracket != null) {
            var playable = e.bracket.playable();
            if (playable.isEmpty()) { finish(e); return; }
            for (var m : playable) e.live.add(new Live(m, List.of(e.entrants.get(m.a()), e.entrants.get(m.b()))));
        } else {
            if (e.round >= e.pick.rounds()) { finish(e); return; }
            e.round++;
            List<Entrant> present = e.entrants.values().stream().filter(x -> present(e, x)).toList();
            if (present.size() < 2) { finish(e); return; }
            e.live.add(new Live(null, present));
        }
        e.live.forEach(l -> l.createdAt = ms);
    }

    private boolean present(Event e, Entrant x) {
        for (UUID id : x.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || e.withdrawn.contains(id)) return false;
        }
        return true;
    }

    private int perLive(Live l) { return l.sides.stream().mapToInt(s -> s.members().size()).sum(); }

    private GameInstance createGame(Event e, Live l) {
        List<Arena> arenas = new ArrayList<>(plugin.arenas().forGame(e.type.id(), e.mode.id()));
        Collections.shuffle(arenas, random);
        for (Arena arena : arenas) {
            if (e.type.id().equals("duels") && e.mode.rulesId().equals("crystal")
                    && (!arena.settings().getBoolean("crystal-enabled", false) || arena.region("combat") == null)) continue;
            if (e.type.id().equals("duels") && e.mode.rulesId().equals("mace") && !arena.settings().getBoolean("mace-enabled", false)) continue;
            GameInstance g = plugin.instances().createPractice(e.type, e.mode, arena, BotDifficulty.NORMAL, 0);
            if (g == null) continue;
            if (g.maxPlayers() < perLive(l)) { g.cleanup(); continue; }
            if (l.match != null && e.mode.isTeams()) g.presetTeams(l.sides.stream().map(Entrant::members).toList());
            g.onRoundEnd(winners -> captureResult(l, g, winners));
            return g;
        }
        return null;
    }

    private void captureResult(Live l, GameInstance g, List<Player> winners) {
        Set<UUID> ids = new HashSet<>();
        winners.forEach(w -> ids.add(w.getUniqueId()));
        l.winners = ids;
        l.places = new LinkedHashMap<>(g.placements());
        Map<UUID, Integer> scores = new LinkedHashMap<>();
        for (Player p : g.players()) scores.put(p.getUniqueId(), g.score(p));
        l.scores = scores;
        l.resolved = true;
    }

    private void tickLive(Event e, Live l, long ms) {
        if (l.done) return;
        if (l.forfeitWinner != null) { settle(e, l); return; }
        if (l.game == null) {
            // Both sides must still be here; otherwise it is a walkover (bracket) or a smaller field (points).
            l.game = createGame(e, l);
            if (l.game == null) {
                if (++l.tries > 20) cancel(e, "There was no free arena for the tournament, so it was stopped. Sorry about that!");
                return;
            }
            l.createdAt = ms;
            return;
        }
        GameInstance g = l.game;
        GameState state = g.state();
        if (l.resolved || state == GameState.ENDING || state == GameState.RESETTING) {
            settle(e, l);
            return;
        }
        if (state == GameState.LOADING) {
            if (ms - l.createdAt > 60_000) cancel(e, "An arena took too long to load, so the tournament was stopped. Sorry about that!");
            return;
        }
        if (state == GameState.RUNNING) {
            if (ms - l.startAt > plugin.getConfig().getInt("events.match-minutes", 10) * 60_000L) g.forceEnd();
            return;
        }
        if (!l.admitted) { admit(e, l, ms); return; }
        if (ms >= l.startAt) {
            if (++l.tries > 8) { g.cleanup(); l.game = null; l.admitted = false; l.tries = 0; return; }
            g.start();
            if (g.state() == GameState.RUNNING) l.startAt = ms;
        }
    }

    private void admit(Event e, Live l, long ms) {
        GameInstance g = l.game;
        List<Entrant> here = new ArrayList<>();
        for (Entrant side : l.sides) {
            boolean all = present(e, side);
            if (all) for (UUID id : side.members()) {
                Player p = Bukkit.getPlayer(id);
                GameInstance current = plugin.instances().byPlayer(p);
                if (current != null && current != g && current.state() != GameState.RUNNING && current.state() != GameState.WAITING
                        && current.state() != GameState.COUNTDOWN) current.remove(p, false);
                if (plugin.instances().byPlayer(p) == null && !g.add(p)) all = false;
                else if (plugin.instances().byPlayer(p) != g) all = false;
            }
            if (all) here.add(side);
            else for (UUID id : side.members()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && plugin.instances().byPlayer(p) == null)
                    p.sendMessage(Text.msg("<yellow>You were not ready for your tournament round in time, so it has gone on without you."));
            }
        }
        if (l.match != null && here.size() < 2) {
            l.forfeitWinner = here.isEmpty() ? l.sides.getFirst() : here.getFirst();
            l.game = null;
            g.cleanup();
            return;
        }
        if (l.match == null && here.size() < 2) {
            g.cleanup();
            l.game = null;
            l.done = true;
            e.round = e.pick.rounds();
            tellSignups(e, "<yellow>Not enough players made it to this round, so the tournament ends here.");
            return;
        }
        l.admitted = true;
        l.startAt = ms + 5000;
    }

    private void settle(Event e, Live l) {
        l.done = true;
        if (e.bracket != null) {
            Entrant winner = l.forfeitWinner != null ? l.forfeitWinner : decide(e, l);
            Entrant loser = l.sides.get(0) == winner ? l.sides.get(1) : l.sides.get(0);
            e.bracket.report(l.match, winner.id());
            tellAudience(e, "<gold>" + winner.label() + "</gold> <white>beat</white> " + loser.label()
                    + (l.forfeitWinner != null ? " <gray>(walkover)" : "") + "!");
            pay(e, winner.members(), plugin.getConfig().getInt("events.rewards.bracket-match-win", 10), "tournament match", l);
        } else {
            Map<String, Integer> places = new LinkedHashMap<>();
            for (Entrant s : l.sides) places.put(s.id(), l.places.getOrDefault(s.members().getFirst(), 0));
            e.tally.addRound(places);
            tellAudience(e, "<gold>Round " + e.round + " done!</gold> <white>Top: " + e.tally.standings().stream().limit(3)
                    .map(s -> e.entrants.get(s.id()).label() + " " + s.points()).reduce((a, b) -> a + ", " + b).orElse(""));
        }
    }

    /** Winner of a bracket match: credited winner, else best place, else higher score, else a coin toss. */
    private Entrant decide(Event e, Live l) {
        Entrant a = l.sides.get(0), b = l.sides.get(1);
        boolean aWon = a.members().stream().anyMatch(l.winners::contains), bWon = b.members().stream().anyMatch(l.winners::contains);
        if (aWon != bWon) return aWon ? a : b;
        int pa = best(a, l.places), pb = best(b, l.places);
        if (pa != pb) return pa < pb ? a : b;
        int sa = a.members().stream().mapToInt(id -> l.scores.getOrDefault(id, 0)).sum();
        int sb = b.members().stream().mapToInt(id -> l.scores.getOrDefault(id, 0)).sum();
        if (sa != sb) return sa > sb ? a : b;
        tellAudience(e, "<gray>That one was too close to call, so a coin toss decided it!");
        return random.nextBoolean() ? a : b;
    }

    private static int best(Entrant x, Map<UUID, Integer> places) {
        return x.members().stream().mapToInt(id -> places.getOrDefault(id, 99)).filter(p -> p > 0).min().orElse(99);
    }

    private void tellAudience(Event e, String miniMessage) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasMetadata("NPC")) continue;
            var g = plugin.instances().byPlayer(p);
            boolean theirs = e.signups.contains(p.getUniqueId()) || e.live.stream().anyMatch(l -> l.game != null && l.game == g);
            if (theirs) p.sendMessage(Text.msg(miniMessage));
        }
    }

    // ---- tokens ---------------------------------------------------------------------

    /** Pays through the normal economy, only when the round would count as competitive, at the event's multiplier. */
    private void pay(Event e, List<UUID> members, int base, String reason, Live l) {
        int humans = l == null ? e.humans : l.sides.stream().mapToInt(s -> s.members().size()).sum();
        int capacity = l == null ? maxPlayers() : Math.max(2, l.game != null ? l.game.maxPlayers() : humans);
        if (l != null && l.forfeitWinner != null) return;
        int amount = EventRewards.tokens(base, e.pick.multiplier(), EventRewards.eligible(humans, capacity));
        if (amount <= 0) return;
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.economy().addTokens(p, amount, reason + ", x" + trim(e.pick.multiplier()));
            else plugin.economy().grant(id, amount);
        }
    }

    // ---- finishing ------------------------------------------------------------------

    private void finish(Event e) {
        if (e.tally != null && e.tally.rounds() == 0) {
            cancel(e, "The tournament ended early because not enough players were ready. Another one will be along soon!");
            return;
        }
        Map<String, Integer> places = new LinkedHashMap<>();
        if (e.bracket != null) places.putAll(e.bracket.placings());
        else e.tally.standings().forEach(s -> places.put(s.id(), s.rank()));
        var cfg = plugin.getConfig();
        int first = cfg.getInt("events.rewards.first", 50), second = cfg.getInt("events.rewards.second", 30), third = cfg.getInt("events.rewards.third", 20);
        int participation = cfg.getInt("events.rewards.participation", 10);
        for (Entrant x : e.entrants.values()) {
            int place = places.getOrDefault(x.id(), 0);
            pay(e, x.members(), participation + EventRewards.prize(place, first, second, third), "tournament", null);
        }
        List<String> lines = new ArrayList<>(EventText.podium(places, id -> e.entrants.get(id).label()));
        if (e.tally != null) e.tally.standings().stream().limit(3).forEach(s -> lines.add(e.entrants.get(s.id()).label() + ": " + s.points() + " points"));
        data.set("last-result-title", e.title() + (e.bracket != null ? " bracket" : " points") + ", " + EventText.when(Instant.now(), e.start, zone()));
        data.set("last-result", lines);
        for (var entry : places.entrySet()) if (entry.getValue() == 1) for (UUID id : e.entrants.get(entry.getKey()).members()) {
            data.set("champions." + id + ".name", e.names.getOrDefault(id, "A player"));
            data.set("champions." + id + ".at", System.currentTimeMillis());
        }
        save();
        String champs = String.join(" and ", places.entrySet().stream().filter(x -> x.getValue() == 1).map(x -> e.entrants.get(x.getKey()).label()).toList());
        for (Player p : Bukkit.getOnlinePlayers()) if (!p.hasMetadata("NPC")) {
            p.sendMessage(Text.msg("<gold><b>Tournament over!</b></gold> <white>" + e.title() + " champion: <yellow>" + champs + "</yellow>. Well played, everyone!"));
            var g = plugin.instances().byPlayer(p);
            if (e.signups.contains(p.getUniqueId()) || e.live.isEmpty() && g != null && g.type() == e.type) lines.forEach(line -> p.sendMessage(Text.mm("<gray>  " + line)));
        }
        event = null;
    }

    private void cancel(Event e, String politeMessage) {
        e.cancelled = true;
        for (Live l : e.live) if (l.game != null && l.game.state() != GameState.RESETTING) {
            l.game.onRoundEnd(w -> { });
            l.game.forceEnd();
        }
        clearSidebars();
        Set<UUID> told = new HashSet<>(e.signups);
        for (Player p : Bukkit.getOnlinePlayers()) if (!p.hasMetadata("NPC") && (told.contains(p.getUniqueId()) || e.phase == Phase.SIGNUP))
            p.sendMessage(Text.msg("<yellow>" + politeMessage));
        if (event == e) event = null;
    }

    public void shutdown() {
        if (event != null) cancel(event, "The server is restarting, so the tournament was stopped. Sorry about that!");
    }

    private void save() {
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save events.yml: " + ex.getMessage()); }
    }
}
