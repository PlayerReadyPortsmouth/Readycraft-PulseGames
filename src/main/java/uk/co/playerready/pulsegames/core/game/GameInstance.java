package uk.co.playerready.pulsegames.core.game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.stats.StatsService;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.core.team.TeamAllocator;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One live round of a game: owns its cloned world, players, teams and state machine.
 * Many instances of the same game/arena can run side by side.
 */
public final class GameInstance {

    /** Consecutive throws from game logic before the instance is force-ended. */
    private static final int MAX_LOGIC_FAILURES = 5;

    private final PulseGamesPlugin plugin;
    private final String id;
    private final GameType type;
    private final GameMode mode;
    private final Arena arena;
    private final MiniGame logic;

    private World world;
    private GameState state = GameState.LOADING;
    private int countdown;
    private int gameTime;
    private long frozenUntil;
    private int lastStartAnnouncement = -1;
    private long emptySince;
    private int logicFailures;

    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> botIds = new LinkedHashSet<>();
    private final Set<UUID> respawning = new LinkedHashSet<>();
    private final Set<UUID> handlingDeaths = new LinkedHashSet<>();
    private boolean competitiveEligible = true;
    private boolean practice;
    private uk.co.playerready.pulsegames.core.bot.BotDifficulty practiceDifficulty = uk.co.playerready.pulsegames.core.bot.BotDifficulty.NORMAL;
    private int practiceBotTarget;
    private final Map<UUID, Integer> roundKills = new HashMap<>(), roundDeaths = new HashMap<>();
    /** Engagement: kill tokens already paid this match (capped), ready votes, and seconds survived. */
    private final Map<UUID, Integer> killTokens = new HashMap<>(), survivedAt = new HashMap<>();
    private final java.util.Set<UUID> readyVotes = new java.util.HashSet<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final List<GameTeam> teams = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Map<UUID, Long> lastDamagerAt = new HashMap<>();
    private final Map<UUID, Integer> scores = new HashMap<>();
    // Placement bookkeeping: finish order is best first, out order is first out first.
    private final List<UUID> finishOrder = new ArrayList<>();
    private final List<UUID> outOrder = new ArrayList<>();
    /** Participants (bots included) who left mid-round; they still count in the field and get a late result. */
    private final Set<UUID> departed = new LinkedHashSet<>();
    private final Map<UUID, String> names = new HashMap<>();
    private Map<UUID, Integer> finalPlacements;
    private final SpectatorTools spectatorTools = new SpectatorTools(this);

    public GameInstance(PulseGamesPlugin plugin, GameType type, GameMode mode, Arena arena) {
        this.plugin = plugin;
        this.type = type;
        this.mode = mode;
        this.arena = arena;
        this.id = UUID.randomUUID().toString().replace("-", "") + "_" + arena.id();
        this.logic = type.createLogic(this);
    }

    public void note(String event, String detail) {
        if (plugin.diagnostics() != null) plugin.diagnostics().record(id,type.id(),mode.id(),arena.id(),event,detail);
    }

    public void init() {
        note("loading", "Map clone requested");
        plugin.worlds().cloneAndLoad(arena.worldTemplate(), id, loaded -> {
            if (state == GameState.RESETTING) { plugin.worlds().unloadAndDelete(loaded); return; }
            this.world = loaded;
            this.state = GameState.WAITING;
            note("world-ready", "Map loaded");
            try { logic.onWorldReady(); } catch (RuntimeException ex) { note("world-ready-failed",ex.getClass().getSimpleName()); cleanup(); return; }
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L));
        }, () -> {
            note("load-failed", "Map clone/load failed");
            broadcast("<red>Failed to load the map. Reference: " + id);
            cleanup();
        });
    }

    // ---- accessors ---------------------------------------------------------

    public PulseGamesPlugin plugin() { return plugin; }
    public String id() { return id; }
    public GameType type() { return type; }
    public GameMode mode() { return mode; }
    public Arena arena() { return arena; }
    public World world() { return world; }
    public GameState state() { return state; }
    public MiniGame logic() { return logic; }
    public int gameTime() { return gameTime; }
    public int remainingSeconds() { return Math.max(0, logic.timeLimitSeconds() - gameTime); }
    public StatsService stats() { return plugin.stats(); }
    public List<GameTeam> teams() { return teams; }
    public boolean frozen() { return startCountdownSeconds() > 0; }
    public SpectatorTools spectatorTools() { return spectatorTools; }

    /** Same deadline as movement/combat protection; round upwards so zero means unlocked. */
    public int startCountdownSeconds() {
        if (state != GameState.RUNNING) return 0;
        return (int) Math.max(0L, (frozenUntil - System.currentTimeMillis() + 999L) / 1000L);
    }

    /**
     * Capacity is the arena's, not just the mode's: a map with fewer spawns/beds/plots
     * than the mode allows must not admit players it has nowhere to put.
     */
    public int maxPlayers() {
        int cap = Math.min(arena.maxPlayers(mode.id()), mode.maxPlayers());
        cap = Math.min(cap, logic.arenaPlayerCap());
        if (mode.isTeams()) cap = Math.min(cap, teamCount() * mode.teamSize());
        return Math.max(0, cap);
    }

    public int minPlayers() { return Math.max(arena.minPlayers(mode.id()), mode.minPlayers()); }

    /**
     * Available team groups, capped by configured slots, spawns and game features.
     * Fewer than two groups is invalid and rejected before a team match starts.
     */
    public int teamCount() {
        return Math.max(0, Math.min(32, Math.min(arena.teamCount(mode.id()), Math.min(arena.spawnCount(), logic.maxTeams()))));
    }

    public boolean joinable(int count) {
        return (state == GameState.WAITING || state == GameState.COUNTDOWN)
                && participants.size() - botIds.size() + count <= maxPlayers();
    }

    public List<Player> players() {
        return participants.stream().map(this::player).filter(java.util.Objects::nonNull).toList();
    }

    public List<Player> alivePlayers() {
        return alive.stream().map(this::player).filter(java.util.Objects::nonNull).toList();
    }

    public List<Player> everyone() {
        Set<UUID> all = new LinkedHashSet<>(participants);
        all.addAll(spectators);
        return all.stream().map(this::player).filter(java.util.Objects::nonNull).toList();
    }

    public Player player(UUID id) {
        if (id == null) return null;
        if (botIds.contains(id)) return plugin.bots() == null ? null : plugin.bots().player(id);
        Player player = plugin.getServer().getPlayer(id);
        return player != null && player.isOnline() ? player : null;
    }

    public boolean isBot(Player player) { return botIds.contains(player.getUniqueId()); }
    public int humanCount() { return (int) participants.stream().filter(id -> !botIds.contains(id)).count(); }
    /** Fixed when the round starts; later departures cannot change rewards. */
    public boolean ranked() { return !practice && competitiveEligible; }
    public boolean practice() { return practice; }
    public uk.co.playerready.pulsegames.core.bot.BotDifficulty practiceDifficulty() { return practiceDifficulty; }
    public int practiceBotTarget() { return practiceBotTarget; }
    public void configurePractice(uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty, int bots) {
        if (state != GameState.LOADING) throw new IllegalStateException("Configure practice before loading the world");
        practice = true;
        competitiveEligible = false;
        practiceDifficulty = difficulty == null ? uk.co.playerready.pulsegames.core.bot.BotDifficulty.NORMAL : difficulty;
        practiceBotTarget = Math.max(0, Math.min(63, bots));
    }
    public boolean isActive(Player player) {
        return isAlive(player) && !respawning.contains(player.getUniqueId())
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
    }

    public boolean isAlive(Player player) { return alive.contains(player.getUniqueId()); }
    public boolean isParticipant(Player player) { return participants.contains(player.getUniqueId()); }
    public boolean isSpectator(Player player) { return spectators.contains(player.getUniqueId()); }

    public GameTeam teamOf(Player player) {
        for (GameTeam team : teams) if (team.contains(player)) return team;
        return null;
    }

    public List<GameTeam> aliveTeams() {
        return teams.stream().filter(t -> t.members().stream().anyMatch(alive::contains)).toList();
    }

    // ---- scores (generic per-player points used by several games) ----------

    public void addScore(Player player, int amount) {
        scores.merge(player.getUniqueId(), amount, Integer::sum);
    }

    public int score(Player player) { return scores.getOrDefault(player.getUniqueId(), 0); }

    public List<Map.Entry<UUID, Integer>> topScores() {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue(Collections.reverseOrder()))
                .toList();
    }

    // ---- placements ----------------------------------------------------------------------

    /**
     * Race-style games call this when a player completes the course. Finish order decides
     * the top places; everyone else is ranked from elimination order. Idempotent, and it
     * does not remove the player from the round (use {@link #finishParticipant} for that).
     */
    public void recordFinish(Player player) {
        UUID id = player.getUniqueId();
        if (state != GameState.RUNNING || !participants.contains(id) || finishOrder.contains(id)) return;
        finishOrder.add(id);
    }

    private Set<UUID> field() {
        Set<UUID> field = new LinkedHashSet<>(participants);
        field.addAll(departed);
        return field;
    }

    /** Places for everyone in the round, best first; frozen once the round has ended. */
    public Map<UUID, Integer> placements() {
        if (finalPlacements != null) return finalPlacements;
        return Placements.compute(field(), finishOrder, List.of(), alive, outOrder);
    }

    /** 1 = best, ties share; provisional while the round runs. 0 for non-participants and co-op games. */
    public int placement(Player player) {
        if (logic.cooperative()) return 0;
        return placements().getOrDefault(player.getUniqueId(), 0);
    }

    /** Online participants ordered best place first. */
    public List<Player> placementOrder() {
        return placements().keySet().stream().map(this::player).filter(java.util.Objects::nonNull).toList();
    }

    private String nameOf(UUID id) {
        Player p = player(id);
        return p != null ? p.getName() : names.getOrDefault(id, "Player");
    }

    // ---- joining / leaving ---------------------------------------------------

    public boolean add(Player player) {
        boolean bot = plugin.bots() != null && plugin.bots().isBot(player);
        if (!joinable(1) || world == null || participants.contains(player.getUniqueId())) return false;
        if (bot && participants.size() >= maxPlayers()) return false;
        if (!bot && plugin.bots() != null) plugin.bots().beforeHumanJoin(this, player);
        if (bot) botIds.add(player.getUniqueId());
        participants.add(player.getUniqueId());
        names.put(player.getUniqueId(), player.getName());
        plugin.instances().index(player, this);
        if (bot) plugin.playerState().reset(player);
        else plugin.playerState().save(player);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        if (player.isInsideVehicle()) player.leaveVehicle();
        if (!player.teleport(arena.lobby(world))) {
            note("join-refused", "Lobby teleport cancelled");
            participants.remove(player.getUniqueId());botIds.remove(player.getUniqueId());plugin.instances().unindex(player);
            if (!bot) plugin.playerState().restore(player);
            return false;
        }
        if (!bot) {
            wardrobeContext(player, "playing");
            giveLeaveItem(player);
        }
        broadcast("<yellow>" + player.getName() + "</yellow> joined <gray>(" + participants.size() + "/" + maxPlayers() + ")");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
        try { logic.onJoin(player); }
        catch (RuntimeException ex) {
            note("join-failed",ex.getClass().getSimpleName());remove(player,true);return false;
        }
        if (!bot && plugin.results() != null) plugin.results().clearReturnHold(player);
        if (!bot) showObjectiveCard(player);
        note("joined", "participants="+participants.size());
        if (plugin.combat() != null) plugin.combat().refresh(player);
        return true;
    }

    private ObjectiveCard card() {
        ObjectiveCard card = logic.howToPlay();
        return card != null ? card : ObjectiveCard.fallback(type);
    }

    /** Objective title at queue-join. */
    private void showObjectiveCard(Player player) {
        try {
            ObjectiveCard card = card();
            Text.title(player, "<aqua><b>" + card.title(), "<gray>" + card.subtitle());
        } catch (RuntimeException ex) {
            note("card-failed", ex.getClass().getSimpleName());
        }
    }

    private String tipLine() {
        try {
            String tip = logic.tip();
            return tip == null || tip.isBlank() ? "Waiting for players..." : tip;
        } catch (RuntimeException ex) {
            return "Waiting for players...";
        }
    }

    private void giveLeaveItem(Player player) {
        ItemStack bed = new ItemStack(Material.RED_BED);
        bed.editMeta(meta -> meta.displayName(Text.mm("<red>Leave game <gray>(right-click)")));
        player.getInventory().setItem(8, bed);
        player.getInventory().setItem(4, ReadyVote.item());
    }

    /** Seconds this player survived: when they went out, or the whole round if still alive. */
    public int survivedSeconds(Player player) {
        return survivedAt.getOrDefault(player.getUniqueId(), gameTime);
    }

    public int readyCount() {
        return (int) players().stream().filter(p -> !isBot(p) && readyVotes.contains(p.getUniqueId())).count();
    }

    /** Waiting-room "ready" item: once every human has clicked, the countdown starts (or shortens). */
    public void voteReady(Player player) {
        if ((state != GameState.WAITING && state != GameState.COUNTDOWN) || !isParticipant(player) || isBot(player)) return;
        if (!readyVotes.add(player.getUniqueId())) {
            player.sendActionBar(Text.mm("<gray>You are already ready. Waiting for the others."));
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.6f);
        List<UUID> humans = players().stream().filter(p -> !isBot(p)).map(Player::getUniqueId).toList();
        broadcast("<green>" + player.getName() + " is ready <gray>(" + readyCount() + "/" + humans.size() + ")");
        if (ReadyVote.shouldStart(humans, readyVotes, participants.size(), minPlayers())) {
            int ready = Math.max(1, plugin.getConfig().getInt("countdown.ready-seconds", 5));
            countdown = state == GameState.WAITING ? ready : ReadyVote.shortenedCountdown(countdown, ready);
            state = GameState.COUNTDOWN;
            broadcast("<green>Everyone is ready! Starting in " + countdown + "s");
        }
    }

    void suspendWaiting(Player player) {
        plugin.instances().unindex(player);
        releaseStep("wardrobe", () -> { if (plugin.content() != null) plugin.content().clearWardrobeContext(player); });
        releaseStep("combat", () -> { if (plugin.combat() != null) plugin.combat().leave(player); });
        releaseStep("inventory", () -> plugin.playerState().restore(player));
        releaseStep("sidebar", () -> plugin.sidebar().clear(player));
        broadcast("<yellow>" + player.getName() + " disconnected. Holding their queue seat briefly.");
    }

    boolean resumeWaiting(Player player) {
        if (world == null || !isParticipant(player) || (state != GameState.WAITING && state != GameState.COUNTDOWN)) {
            remove(player, false); return false;
        }
        plugin.playerState().save(player);
        if (!player.teleport(arena.lobby(world))) {
            plugin.playerState().restore(player); remove(player, false); return false;
        }
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        plugin.instances().index(player, this);
        wardrobeContext(player, "playing");
        giveLeaveItem(player);
        player.sendMessage(Text.msg("<green>Your reserved queue seat has been restored."));
        return true;
    }

    boolean returnSpectator(Player player) {
        if (world == null || state != GameState.RUNNING || isParticipant(player)) return false;
        plugin.playerState().save(player);
        if (!player.teleport(arena.spectator(world))) { plugin.playerState().restore(player); return false; }
        spectators.add(player.getUniqueId());
        plugin.instances().index(player, this);
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.sendMessage(Text.msg("<yellow>You disconnected during the round. You can watch, but your life and rewards are not restored. Use /lobby to return."));
        return true;
    }

    /** Removes a player at any stage (quit, /lobby, kicked). */
    public void remove(Player player, boolean teleportToLobby) {
        if (!participants.contains(player.getUniqueId()) && !spectators.contains(player.getUniqueId())) return;
        boolean bot = isBot(player);
        boolean wasAlive = alive.remove(player.getUniqueId());
        if (state == GameState.RUNNING && participants.contains(player.getUniqueId())) {
            departed.add(player.getUniqueId());
            names.putIfAbsent(player.getUniqueId(), player.getName());
            if (wasAlive && !outOrder.contains(player.getUniqueId()) && !finishOrder.contains(player.getUniqueId()))
                outOrder.add(player.getUniqueId());
        }
        respawning.remove(player.getUniqueId());
        lastDamager.remove(player.getUniqueId());
        lastDamagerAt.remove(player.getUniqueId());
        participants.remove(player.getUniqueId());
        spectators.remove(player.getUniqueId());
        teams.forEach(t -> t.members().remove(player.getUniqueId()));
        plugin.instances().unindex(player);
        releaseStep("wardrobe", () -> { if (plugin.content() != null) plugin.content().clearWardrobeContext(player); });
        releaseStep("combat", () -> { if (plugin.combat() != null) plugin.combat().leave(player); });
        try { logic.onQuit(player); } catch (RuntimeException ex) { note("quit-hook-failed", ex.getClass().getSimpleName()); }
        if (!bot) {
            releaseStep("ghost", () -> spectatorTools.release(player));
            releaseStep("inventory", () -> plugin.playerState().restore(player));
            releaseStep("sidebar", () -> plugin.sidebar().clear(player));
            if (teleportToLobby && player.isOnline()) releaseStep("lobby", () -> plugin.lobby().sendToLobby(player));
        } else {
            plugin.bots().destroy(player.getUniqueId());
            botIds.remove(player.getUniqueId());
        }
        // An explicit last-human exit must retire the queue immediately. Disconnected
        // players retain their participant seat until their reconnect reservation expires.
        if (humanCount() == 0 && (!botIds.isEmpty()
                || state == GameState.WAITING || state == GameState.COUNTDOWN)) {
            cleanup();
            return;
        }
        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            broadcast("<yellow>" + player.getName() + "</yellow> left <gray>(" + participants.size() + "/" + maxPlayers() + ")");
        } else if (wasAlive && state == GameState.RUNNING) {
            broadcast("<red>" + player.getName() + "</red> disconnected");
            checkEnd();
        }
        if (participants.isEmpty() && spectators.isEmpty() && state != GameState.RESETTING
                && state != GameState.WAITING && state != GameState.COUNTDOWN) {
            cleanup();
        }
    }

    // ---- state machine -------------------------------------------------------

    private void tick() {
        tasks.removeIf(BukkitTask::isCancelled);
        if (plugin.bots() != null) plugin.bots().reconcile(this);
        if (state == GameState.RESETTING || reapIfAbandoned()) return;
        if ((state == GameState.WAITING || state == GameState.COUNTDOWN) && plugin.instances().hasReservedSeats(this)) {
            updateSidebars();
            return;
        }
        switch (state) {
            case WAITING -> {
                if (participants.size() >= minPlayers()) {
                    state = GameState.COUNTDOWN;
                    countdown = Math.max(1, plugin.getConfig().getInt("countdown.lobby-seconds", 30));
                }
            }
            case COUNTDOWN -> tickCountdown();
            case RUNNING -> {
                gameTime++;
                runLogic("onSecond", () -> logic.onSecond(gameTime));
                if (state == GameState.RUNNING && gameTime >= logic.timeLimitSeconds()) {
                    runLogic("onTimeUp", logic::onTimeUp);
                }
            }
            case ENDING -> {
                if (--countdown <= 0) cleanup();
            }
            default -> { }
        }
        if (state == GameState.WAITING || state == GameState.COUNTDOWN || state == GameState.RUNNING) {
            updateSidebars();
        }
    }

    /**
     * Fallback for queues abandoned before a player was admitted (for example a
     * cancelled world load). Explicit last-player exits clean up in remove().
     */
    private boolean reapIfAbandoned() {
        boolean pending = state == GameState.WAITING || state == GameState.COUNTDOWN;
        if (!pending || !participants.isEmpty() || !spectators.isEmpty()) {
            emptySince = 0L;
            return false;
        }
        long now = System.currentTimeMillis();
        if (emptySince == 0L) {
            emptySince = now;
            return false;
        }
        long idleMillis = plugin.getConfig().getInt("instances.empty-lobby-seconds", 60) * 1000L;
        if (now - emptySince < idleMillis) return false;
        cleanup();
        return true;
    }

    /**
     * Runs a game-logic hook. One repeatable throw would otherwise strand the instance
     * in RUNNING forever, holding a world and a concurrency slot.
     */
    private void runLogic(String hook, Runnable action) {
        try {
            action.run();
            logicFailures = 0;
        } catch (Exception ex) {
            note("logic-failed", hook+" "+ex.getClass().getSimpleName());
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Game logic error in " + type.id() + "." + hook + " (instance " + id + ")", ex);
            if (++logicFailures < MAX_LOGIC_FAILURES && !hook.equals("onStart")) return;
            broadcast("<red>This game hit an error and had to be stopped. Reference: " + id);
            try {
                forceEnd();
            } catch (Exception fatal) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Could not force-end instance " + id + "; discarding it", fatal);
                cleanup();
            }
        }
    }

    private void tickCountdown() {
        if (participants.size() < minPlayers()) {
            state = GameState.WAITING;
            broadcast("<red>Not enough players, countdown cancelled.");
            return;
        }
        int full = Math.max(1, plugin.getConfig().getInt("countdown.full-seconds", 10));
        if (participants.size() >= maxPlayers() && countdown > full) {
            countdown = full;
            broadcast("<green>Arena full! Starting soon.");
        }
        runLogic("onCountdownTick", () -> logic.onCountdownTick(countdown));
        if (countdown <= 5 || countdown == 10 || countdown == 15 || countdown == 30) {
            broadcast("<gray>Starting in <yellow>" + countdown + "s");
            for (Player p : players()) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, countdown <= 3 ? 2f : 1f);
                if (countdown <= 5) {
                    Text.title(p, countdown <= 3 ? "<red><b>" + countdown : "<yellow><b>" + countdown,
                            "<gray>" + arena.displayName());
                }
            }
        }
        for (Player p : players()) p.setLevel(countdown);
        if (--countdown <= 0) start();
    }

    public void start() {
        // Only a pre-start lobby may start: replaying a RUNNING/ENDING round re-awards
        // wins and tokens, and a LOADING one has no world to teleport into.
        if (state != GameState.WAITING && state != GameState.COUNTDOWN) return;
        if (plugin.playService() != null && plugin.playService().admitting(this)) return;
        if (plugin.instances().hasReservedSeats(this)) return;
        if (humanCount() == 0) { cleanup(); return; }
        if (mode.isTeams() && teamCount() < 2) {
            state = GameState.WAITING;
            broadcast("<red>This map needs at least two complete team spawn/base groups.");
            return;
        }
        if (players().size() > maxPlayers() || minPlayers() > maxPlayers()
                || (mode.isTeams() && players().size() < mode.minPlayers())) {
            state = GameState.WAITING;
            broadcast("<red>This map's player limits do not fit the selected mode.");
            return;
        }
        String problem = logic.startProblem(players());
        if (problem != null) {
            state = GameState.WAITING;
            broadcast("<red>Cannot start: " + problem);
            return;
        }
        if (mode.isTeams()) {
            teams.clear();
            try {
                teams.addAll(TeamAllocator.allocate(players(), mode.teamSize(), teamCount(), plugin.parties(),
                        player -> plugin.bots() == null ? 2 : plugin.bots().teamStrength(this, player)));
            } catch (IllegalArgumentException noPacking) {
                state = GameState.WAITING;
                broadcast("<yellow>Your parties cannot fit together in this map's teams. Choose a larger team mode or another map.");
                note("team-packing-blocked", noPacking.getMessage());
                return;
            }
            if (teams.stream().filter(team -> !team.members().isEmpty()).count() < 2) {
                teams.clear();
                state = GameState.WAITING;
                broadcast("<yellow>Waiting for an opposing team.");
                return;
            }
        }
        competitiveEligible = !practice && CompetitiveEligibility.eligible(humanCount(), botIds.size(), maxPlayers());
        if (!botIds.isEmpty()) {
            broadcast(competitiveEligible
                    ? "<green>This bot-assisted match counts for competitive records and match token rewards."
                    : "<yellow>Practice match: not enough human players for competitive records or match token rewards.");
        }
        state = GameState.RUNNING;
        gameTime = 0;
        alive.clear();
        alive.addAll(participants);
        int i = 0;
        for (Player p : players()) {
            plugin.playerState().reset(p);
            p.setGameMode(logic.playGameMode());
            GameTeam team = teamOf(p);
            p.teleport(arena.spawn(team != null ? team.index() : i++, world));
            if (!isBot(p)) wardrobeContext(p, "playing");
            p.setLevel(0);
            plugin.stats().addPlayed(p, type.id());
        }
        if (mode.isTeams()) {
            for (GameTeam team : teams) {
                for (UUID member : team.members()) {
                    Player p = player(member);
                    if (p != null) p.sendMessage(Text.msg("You are on team " + team.coloredName()));
                }
            }
        }
        frozenUntil = System.currentTimeMillis() + logic.startFreezeSeconds() * 1000L;
        note("started", "participants="+participants.size()+" bots="+botIds.size()+" competitive="+competitiveEligible);
        runLogic("onStart", logic::onStart);
        if (state == GameState.RUNNING && plugin.bots() != null) plugin.bots().onRoundStart(this);
        // Games without a preparation freeze own their opening announcement.
        if (state == GameState.RUNNING && logic.startFreezeSeconds() > 0) {
            // Leave the opening role/round title visible until the next second.
            lastStartAnnouncement = startCountdownSeconds();
            tasks.add(new org.bukkit.scheduler.BukkitRunnable() {
                @Override public void run() { if (!announceStartCountdown()) cancel(); }
            }.runTaskTimer(plugin, 1L, 1L));
        }
        if (state == GameState.RUNNING) updateSidebars();
    }

    /** Returns false when the presentation task must stop, including an aborted match. */
    boolean announceStartCountdown() {
        if (state != GameState.RUNNING) return false;
        int seconds = startCountdownSeconds();
        if (seconds != lastStartAnnouncement) {
            lastStartAnnouncement = seconds;
            if (seconds <= 3) {
                String phase = logic.phaseCountdown();
                for (Player p : players()) {
                    if (isBot(p)) continue;
                    Text.title(p, seconds > 0 ? "<yellow><b>" + seconds
                                    : phase == null ? "<green><b>GO!" : "<green><b>READY!",
                            phase != null ? "<gray>" + phase : seconds > 0 ? "<gray>Get ready" : "<yellow>" + goLine());
                    p.playSound(p.getLocation(), seconds > 0 ? "minecraft:block.note_block.hat"
                            : "minecraft:entity.ender_dragon.growl", 0.6f, seconds > 0 ? 2f : 1.4f);
                }
            }
        }
        return seconds > 0;
    }

    private String goLine() {
        try { return card().title(); } catch (RuntimeException ex) { return type.displayName(); }
    }

    /** Reserve space for timers and the shared footer before adding game-specific scores. */
    List<String> runningSidebar(Player player) {
        List<String> lines = new ArrayList<>();
        int preparation = startCountdownSeconds();
        String phase = logic.phaseCountdown();
        if (preparation > 0) lines.add("<yellow>Starting in: " + preparation + "s");
        if (phase != null && !phase.isBlank()) lines.add("<yellow>" + phase);
        lines.add("<gray>Match left: <white>" + Text.time(remainingSeconds()));
        List<String> custom = logic.sidebar(player);
        if (custom.isEmpty()) lines.add("<gray>Alive: <white>" + alive.size());
        else {
            int budget = 15 - 2 - (botIds.isEmpty() ? 3 : 4) - lines.size();
            lines.addAll(custom.subList(0, Math.min(custom.size(), Math.max(0, budget))));
        }
        return lines;
    }

    /** Player spawn point: their team's spawn, or their join-order spawn for FFA. */
    public Location spawnFor(Player player) {
        GameTeam team = teamOf(player);
        if (team != null) return arena.spawn(team.index(), world);
        int index = new ArrayList<>(participants).indexOf(player.getUniqueId());
        return arena.spawn(Math.max(index, 0), world);
    }

    // ---- death / elimination ---------------------------------------------------

    public void recordDamager(Player victim, Player damager) {
        lastDamager.put(victim.getUniqueId(), damager.getUniqueId());
        lastDamagerAt.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    public Player killerOf(Player victim) {
        Long at = lastDamagerAt.get(victim.getUniqueId());
        if (at == null || System.currentTimeMillis() - at > 8000) return null;
        Player killer = player(lastDamager.get(victim.getUniqueId()));
        return killer != null && killer != victim ? killer : null;
    }

    public void handleDeath(Player victim, Player killer) {
        if (state != GameState.RUNNING || !isActive(victim) || !logic.acceptsDeath(victim)
                || !handlingDeaths.add(victim.getUniqueId())) return;
        try {
            victim.setFallDistance(0);
            victim.setFireTicks(0);
            Player resolvedKiller = killer != null ? killer : killerOf(victim);
            if (resolvedKiller != null && (!isParticipant(resolvedKiller) || resolvedKiller.equals(victim))) resolvedKiller = null;
            roundDeaths.merge(victim.getUniqueId(), 1, Integer::sum);
            if (resolvedKiller != null) roundKills.merge(resolvedKiller.getUniqueId(), 1, Integer::sum);
            lastDamager.remove(victim.getUniqueId());
            lastDamagerAt.remove(victim.getUniqueId());
            if (resolvedKiller != null && ranked() && !isBot(resolvedKiller)) {
                plugin.cosmetics().playKillEffect(resolvedKiller, victim.getLocation());
                int killPay = uk.co.playerready.pulsegames.core.economy.RewardRules.killPayout(
                        killTokens.getOrDefault(resolvedKiller.getUniqueId(), 0),
                        plugin.getConfig().getInt("economy.kill-reward", 5),
                        plugin.getConfig().getInt("economy.kill-token-cap", 25));
                if (killPay > 0) {
                    killTokens.merge(resolvedKiller.getUniqueId(), killPay, Integer::sum);
                    plugin.economy().addMatchTokens(resolvedKiller, killPay, "kill");
                }
            }
            logic.onDeath(victim, resolvedKiller);
        } finally {
            handlingDeaths.remove(victim.getUniqueId());
        }
    }

    /** Finish without forfeiting participant credit or invoking last-standing completion.
     * Called on the server thread after the game has removed its owned vehicle/items. */
    public void finishParticipant(Player player) {
        if (state != GameState.RUNNING || !logic.managesCompletion() || !isParticipant(player)
                || !alive.remove(player.getUniqueId())) return;
        respawning.remove(player.getUniqueId());
        lastDamager.remove(player.getUniqueId());
        lastDamagerAt.remove(player.getUniqueId());
        spectators.add(player.getUniqueId());
        if (!finishOrder.contains(player.getUniqueId())) finishOrder.add(player.getUniqueId());
        if (plugin.combat() != null) plugin.combat().leave(player);
        plugin.playerState().reset(player);
        spectatorTools.enter(player);
        player.teleport(arena.spectator(world));
    }

    public void eliminate(Player player) {
        if (!alive.remove(player.getUniqueId())) return;
        survivedAt.put(player.getUniqueId(), gameTime);
        spectators.add(player.getUniqueId());
        if (!outOrder.contains(player.getUniqueId())) outOrder.add(player.getUniqueId());
        if (plugin.combat() != null) plugin.combat().leave(player);
        Location deathSpot = player.getLocation();
        world.spawnParticle(org.bukkit.Particle.CLOUD, deathSpot.add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);
        world.spawnParticle(org.bukkit.Particle.FLAME, deathSpot, 15, 0.2, 0.3, 0.2, 0.02);
        for (Player p : everyone()) {
            p.playSound(deathSpot, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 1.6f);
        }
        plugin.playerState().reset(player);
        spectatorTools.enter(player);
        player.teleport(arena.spectator(world));
        int outPlace = placement(player);
        Text.title(player, "<yellow><b>You're out!", "<gray>" + (outPlace > 0 ? "You're in " + Placements.ordinal(outPlace)
                + " place. " : "") + "Tap the items below to watch or queue.");
        // Bedrock clients have no vanilla spectator menu, so the hotbar items do that job.
        player.sendMessage(Text.msg("<gray>You're out - use the hotbar to <yellow>watch a player</yellow>, "
                + "<yellow>play the next game now</yellow>, or send a reaction. <yellow>/lobby</yellow> leaves."));
        logic.onEliminated(player);
        checkEnd();
    }

    public void respawn(Player player, int delaySeconds) {
        if (state != GameState.RUNNING || !respawning.add(player.getUniqueId())) return;
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.teleport(arena.spectator(world));
        Text.title(player, "<red>You died!", "<gray>Respawning in " + delaySeconds + "s");
        runLater(delaySeconds * 20L, () -> {
            if (state != GameState.RUNNING || !participants.contains(player.getUniqueId()) || player(player.getUniqueId()) == null) return;
            respawning.remove(player.getUniqueId());
            plugin.playerState().reset(player);
            player.setGameMode(logic.playGameMode());
            player.teleport(logic.respawnLocation(player));
            logic.onRespawn(player);
        });
    }

    public void checkEnd() {
        if (state != GameState.RUNNING || logic.managesCompletion()) return;
        if (mode.isTeams()) {
            List<GameTeam> remaining = aliveTeams();
            if (remaining.size() <= 1) {
                end(remaining.isEmpty() ? List.of() : alivePlayers(), "last-team");
            }
        } else if (alive.isEmpty()) {
            end(List.of(), "nobody-left");
        } else if (!logic.cooperative() && alive.size() <= 1) {
            // Co-op games are won against the map, so one survivor is not a winner.
            end(alivePlayers(), "last-standing");
        }
    }

    static List<Player> winningMembers(List<Player> participants, List<Player> declared,
            java.util.function.Function<Player, GameTeam> teamOf) {
        Set<GameTeam> winnerTeams = declared.stream().filter(participants::contains).map(teamOf)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return participants.stream().filter(p -> declared.contains(p) || winnerTeams.contains(teamOf.apply(p))).toList();
    }

    public void end(List<Player> winners, String reason) {
        note("ending", reason);
        if (state == GameState.ENDING || state == GameState.RESETTING) return;
        List<Player> creditedWinners = winningMembers(players(), winners, this::teamOf);
        if (plugin.bots() != null) plugin.bots().onRoundEnd(this, creditedWinners, reason);
        state = GameState.ENDING;
        if ("forced".equals(reason)) competitiveEligible = false;
        boolean coop = logic.cooperative();
        Set<UUID> winnerIds = new LinkedHashSet<>();
        creditedWinners.forEach(w -> winnerIds.add(w.getUniqueId()));
        finalPlacements = coop ? Map.of() : Placements.compute(field(), finishOrder, winnerIds, alive, outOrder);
        int fieldSize = field().size();
        List<String> podium = Placements.podium(finalPlacements, this::nameOf);
        boolean anyWinner = !creditedWinners.isEmpty();
        boolean decided = anyWinner || !reason.equals("forced");
        int minField = plugin.getConfig().getInt("economy.placement-min-players", 4);
        int secondReward = plugin.getConfig().getInt("economy.second-place-reward", 20);
        int thirdReward = plugin.getConfig().getInt("economy.third-place-reward", 10);
        countdown = plugin.getConfig().getInt("end.celebrate-seconds", 10);
        String winnerNames = creditedWinners.isEmpty() ? "Nobody"
                : String.join("<gray>, </gray><yellow>", creditedWinners.stream().map(Player::getName).toList());
        broadcast("<gold><b>Round over!</b></gold> <gray>" + (anyWinner ? "Winner(s): <yellow>" + winnerNames : "No single winner this time."));
        for (Player p : everyone()) {
            if (isBot(p)) { p.setGameMode(org.bukkit.GameMode.SPECTATOR); continue; }
            int balanceBefore = plugin.economy().tokens(p);
            wardrobeContext(p, "ended");
            if (isParticipant(p)) wardrobeCelebration(p, "finish_event");
            int place = coop ? 0 : finalPlacements.getOrDefault(p.getUniqueId(), 0);
            if (creditedWinners.contains(p)) {
                wardrobeCelebration(p, "win_animation");
                Text.title(p, "<gold><b>VICTORY!", "<gray>You won " + type.displayName());
                p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", 1f, 1f);
                plugin.stats().addWin(p, type.id());
                plugin.economy().addMatchTokens(p, plugin.getConfig().getInt("economy.win-reward", 50), "victory");
                spawnFirework(p.getLocation());
            } else {
                if (isParticipant(p)) {
                    plugin.economy().addMatchTokens(p,
                            plugin.getConfig().getInt("economy.participation-reward", 10), "game played");
                    int bonus = ranked() && decided ? Placements.bonus(place, anyWinner, fieldSize, minField, secondReward, thirdReward) : 0;
                    if (bonus > 0) plugin.economy().addMatchTokens(p, bonus, "placement");
                }
                String line = place > 0 ? (anyWinner || place > 1 ? "You finished " + Placements.ordinal(place) + " of " + fieldSize
                        : "Joint first - nobody took the win") : anyWinner ? "Winner(s): " + winnerNames : "The round is over";
                Text.title(p, "<aqua><b>Well played!", "<gray>" + line);
                // A draw or timeout with no declared winner is not a loss.
                // A co-op round nobody won is a genuine loss; a forced stop is neither.
                if (isParticipant(p) && !"forced".equals(reason)) {
                    if (anyWinner || coop) plugin.stats().addLoss(p, type.id());
                    else plugin.stats().addDraw(p, type.id());
                }
            }
            if (plugin.progression() != null) plugin.progression().completed(this, p, creditedWinners.contains(p), roundKills.getOrDefault(p.getUniqueId(), 0), reason);
            if (plugin.engagement() != null) plugin.engagement().matchEnded(this, p, creditedWinners.contains(p), !creditedWinners.isEmpty(), reason);
            p.setGameMode(org.bukkit.GameMode.SPECTATOR);
            int draws = plugin.stats().get(p, type.id(), "draws");
            p.sendMessage(Text.msg("<gray>Record: <green>" + plugin.stats().get(p, type.id(), "wins")
                    + "W</green> <red>" + plugin.stats().get(p, type.id(), "losses") + "L</red>"
                    + (draws > 0 ? " <yellow>" + draws + "D</yellow>" : "") + " <gray>in "
                    + type.displayName() + " <dark_gray>(" + plugin.stats().get(p, type.id(), "played") + " played)"));
            if (plugin.results() != null) {
                plugin.results().publish(p, resultFor(p, creditedWinners, balanceBefore, place, fieldSize, podium));
            }
        }
        // Players who left early (queued the next game, or went to the lobby) still get their result.
        if (plugin.results() != null) {
            for (UUID gid : departed) {
                Player gone = plugin.getServer().getPlayer(gid);
                if (gone == null || !gone.isOnline() || participants.contains(gid) || spectators.contains(gid)
                        || plugin.bots() != null && plugin.bots().isBot(gone)) continue;
                int gonePlace = coop ? 0 : finalPlacements.getOrDefault(gid, 0);
                plugin.results().publishLate(gone, new MatchResults.Result(id, type.id(), mode.id(), arena.displayName(),
                        Placements.outcome(true, false, anyWinner, coop, gonePlace, fieldSize),
                        "Solo", 0, 0, roundKills.getOrDefault(gid, 0), roundDeaths.getOrDefault(gid, 0), 0, false, practice,
                        System.currentTimeMillis() + 600_000L, gonePlace, fieldSize, podium));
            }
        }
        // Celebration: fireworks rain on the creditedWinners until cleanup.
        runRepeating(20L, 30L, () -> {
            for (Player winner : creditedWinners) {
                if (isBot(winner) || !winner.isOnline() || winner.getWorld() != world) continue;
                spawnFirework(winner.getLocation());
                plugin.cosmetics().playWinEffect(winner);
            }
        });
        runLogic("onEnd", () -> logic.onEnd(creditedWinners));
    }

    private MatchResults.Result resultFor(Player p, List<Player> creditedWinners, int balanceBefore, int place,
                                          int fieldSize, List<String> podium) {
        GameTeam team = teamOf(p);
        int teamScore = team == null ? score(p) : players().stream().filter(team::contains).mapToInt(this::score).sum();
        String outcome = Placements.outcome(isParticipant(p), creditedWinners.contains(p), !creditedWinners.isEmpty(),
                logic.cooperative(), place, fieldSize);
        return new MatchResults.Result(id, type.id(), mode.id(), arena.displayName(), outcome,
                team == null ? "Solo" : "Team " + (team.index() + 1), score(p), teamScore,
                roundKills.getOrDefault(p.getUniqueId(), 0), roundDeaths.getOrDefault(p.getUniqueId(), 0),
                Math.max(0, plugin.economy().tokens(p) - balanceBefore), ranked() && isParticipant(p), practice,
                System.currentTimeMillis() + 600_000L, place, fieldSize, podium);
    }

    private void wardrobeContext(Player player, String phase) {
        releaseStep("wardrobe-context", () -> {
            if (plugin.content() != null) plugin.content().wardrobeContext(player, type.id(), phase);
        });
    }

    private void wardrobeCelebration(Player player, String category) {
        releaseStep("wardrobe-celebration", () -> {
            if (plugin.content() != null) plugin.content().wardrobeCelebration(player, category);
        });
    }

    private void spawnFirework(Location loc) {
        if (loc.getWorld() == null) return;
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        var meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.ORANGE, Color.YELLOW)
                .withFlicker().build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    public void forceEnd() {
        if (state == GameState.RUNNING) {
            end(List.of(), "forced");
        } else if (state != GameState.RESETTING) {
            cleanup();
        }
    }

    public void cleanup() {
        if (state == GameState.RESETTING) return;
        state = GameState.RESETTING;
        note("cleanup", "Releasing players and world");
        try { logic.onCleanup(); }
        catch (RuntimeException ex) { plugin.getLogger().log(java.util.logging.Level.WARNING, "Game cleanup hook failed", ex); }
        tasks.forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        tasks.clear();
        for (Player p : everyone()) {
            plugin.instances().unindex(p);
            releaseStep("wardrobe", () -> { if (plugin.content() != null) plugin.content().clearWardrobeContext(p); });
            releaseStep("combat", () -> { if (plugin.combat() != null) plugin.combat().leave(p); });
            releaseStep("ghost", () -> spectatorTools.release(p));
            if (!isBot(p)) {
                releaseStep("inventory", () -> plugin.playerState().restore(p));
                releaseStep("sidebar", () -> plugin.sidebar().clear(p));
                releaseStep("lobby", () -> plugin.lobby().sendToLobby(p));
            }
        }
        if (plugin.bots() != null) plugin.bots().forget(this);
        botIds.clear();
        respawning.clear();
        participants.clear();
        alive.clear();
        spectators.clear();
        if (world != null) {
            plugin.worlds().unloadAndDelete(world);
            world = null;
        }
        plugin.instances().remove(this);
        plugin.stats().flushAsync();
    }

    private void releaseStep(String step, Runnable action) {
        try { action.run(); }
        catch (RuntimeException ex) { note("player-release-failed", step + " " + ex.getClass().getSimpleName()); }
    }

    // ---- helpers -----------------------------------------------------------------

    public void broadcast(String miniMessage) {
        Component component = Text.msg(miniMessage);
        for (Player p : everyone()) if (!isBot(p)) p.sendMessage(component);
    }

    public void broadcastRaw(String miniMessage) {
        Component component = Text.mm(miniMessage);
        for (Player p : everyone()) if (!isBot(p)) p.sendMessage(component);
    }

    /**
     * One-shot tasks drop themselves from the cancel-on-cleanup list once they have run.
     * Games schedule one per crumbling block, so keeping every finished handle grows the
     * list into the tens of thousands over a single round.
     */
    public BukkitTask runLater(long delayTicks, Runnable runnable) {
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(handle[0]);
            runnable.run();
        }, delayTicks);
        tasks.add(handle[0]);
        return handle[0];
    }

    public BukkitTask runRepeating(long delayTicks, long periodTicks, Runnable runnable) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        tasks.add(task);
        return task;
    }

    private void updateSidebars() {
        String title = "<gradient:#ff5f6d:#ffc371><b>" + type.displayName().toUpperCase() + "</b></gradient>";
        for (Player p : everyone()) {
            if (isBot(p)) continue;
            List<String> lines = new ArrayList<>();
            lines.add("<dark_gray>" + arena.displayName());
            lines.add("");
            switch (state) {
                case WAITING -> {
                    lines.add("<gray>Players: <white>" + participants.size() + "/" + maxPlayers());
                    lines.add("<gray>" + tipLine());
                    lines.add("<gray>Ready: <white>" + readyCount() + "/" + humanCount());
                    lines.add("<yellow>" + plugin.bots().queueStatus(this));
                }
                case COUNTDOWN -> {
                    lines.add("<gray>Players: <white>" + participants.size() + "/" + maxPlayers());
                    lines.add("<gray>Starting in: <green>" + countdown + "s");
                    lines.add("<yellow>" + plugin.bots().queueStatus(this));
                }
                case RUNNING -> {
                    lines.addAll(runningSidebar(p));
                }
                default -> { }
            }
            lines.add("");
            if (!botIds.isEmpty()) lines.add("<gray>Bots: <yellow>" + botIds.size());
            lines.add("<gray>Mode: <yellow>" + mode.displayName());
            lines.add("<dark_gray>readycraft");
            plugin.sidebar().show(p, title, lines);
        }
    }
}
