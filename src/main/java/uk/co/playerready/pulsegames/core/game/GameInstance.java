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
import java.util.concurrent.ThreadLocalRandom;

/**
 * One live round of a game: owns its cloned world, players, teams and state machine.
 * Many instances of the same game/arena can run side by side.
 */
public final class GameInstance {

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
    /** Practice instances start solo with a short countdown - pressure-free learning. */
    private boolean practice;

    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    /** Buddy assistants: present and visible, but protected and non-competing. */
    private final Set<UUID> assistants = new LinkedHashSet<>();
    private final List<GameTeam> teams = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Map<UUID, Long> lastDamagerAt = new HashMap<>();
    private final Map<UUID, Integer> scores = new HashMap<>();

    public GameInstance(PulseGamesPlugin plugin, GameType type, GameMode mode, Arena arena) {
        this.plugin = plugin;
        this.type = type;
        this.mode = mode;
        this.arena = arena;
        this.id = Long.toHexString(ThreadLocalRandom.current().nextLong(0xFFFFFF)) + "_" + arena.id();
        this.logic = type.createLogic(this);
    }

    public void init() {
        plugin.worlds().cloneAndLoad(arena.worldTemplate(), id, loaded -> {
            this.world = loaded;
            this.state = GameState.WAITING;
            logic.onWorldReady();
            plugin.npcs().spawnForInstance(this);
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L));
        }, () -> {
            broadcast("<red>Failed to load the map. The game was cancelled.");
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
    public StatsService stats() { return plugin.stats(); }
    public List<GameTeam> teams() { return teams; }
    public boolean frozen() { return System.currentTimeMillis() < frozenUntil; }

    public int maxPlayers() { return Math.min(arena.maxPlayers(), mode.maxPlayers()); }
    public int minPlayers() { return practice ? 1 : Math.max(arena.minPlayers(), mode.minPlayers()); }
    public boolean practice() { return practice; }
    public void setPractice(boolean practice) { this.practice = practice; }

    public boolean joinable(int count) {
        return (state == GameState.WAITING || state == GameState.COUNTDOWN)
                && participants.size() + count <= maxPlayers();
    }

    public List<Player> players() {
        return participants.stream().map(Bukkit::getPlayer).filter(p -> p != null && p.isOnline()).toList();
    }

    public List<Player> alivePlayers() {
        return alive.stream().map(Bukkit::getPlayer).filter(p -> p != null && p.isOnline()).toList();
    }

    public List<Player> everyone() {
        Set<UUID> all = new LinkedHashSet<>(participants);
        all.addAll(spectators);
        all.addAll(assistants);
        return all.stream().map(Bukkit::getPlayer).filter(p -> p != null && p.isOnline()).toList();
    }

    public boolean isAlive(Player player) { return alive.contains(player.getUniqueId()); }
    public boolean isParticipant(Player player) { return participants.contains(player.getUniqueId()); }
    public boolean isSpectator(Player player) { return spectators.contains(player.getUniqueId()); }
    public boolean isAssistant(Player player) { return assistants.contains(player.getUniqueId()); }

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

    // ---- joining / leaving ---------------------------------------------------

    public boolean add(Player player) {
        if (!joinable(1) || world == null) return false;
        participants.add(player.getUniqueId());
        plugin.instances().index(player, this);
        plugin.playerState().save(player);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.teleport(arena.lobby(world));
        giveLeaveItem(player);
        broadcast("<yellow>" + player.getName() + "</yellow> joined <gray>(" + participants.size() + "/" + maxPlayers() + ")");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
        logic.onJoin(player);
        return true;
    }

    private void giveLeaveItem(Player player) {
        ItemStack bed = new ItemStack(Material.RED_BED);
        bed.editMeta(meta -> meta.displayName(Text.mm("<red>Leave game <gray>(right-click)")));
        player.getInventory().setItem(8, bed);
    }

    /** Joins as a protected, non-competing buddy assistant (helper/support worker). */
    public boolean addAssistant(Player player) {
        if (!joinable(0) && state != GameState.RUNNING) return false;
        if (world == null) return false;
        assistants.add(player.getUniqueId());
        plugin.instances().index(player, this);
        plugin.playerState().save(player);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.teleport(arena.lobby(world));
        player.sendMessage(Text.msg("<aqua>You're here as a <b>buddy assistant</b></aqua> <gray>- you can move "
                + "around with your buddy but can't be hurt, fight or win. <yellow>/lobby</yellow> to leave."));
        broadcast("<aqua>" + player.getName() + "</aqua> <gray>is supporting as a buddy assistant.");
        return true;
    }

    /** Removes a player at any stage (quit, /lobby, kicked). */
    public void remove(Player player, boolean teleportToLobby) {
        boolean wasAlive = alive.remove(player.getUniqueId());
        participants.remove(player.getUniqueId());
        spectators.remove(player.getUniqueId());
        assistants.remove(player.getUniqueId());
        teams.forEach(t -> t.members().remove(player.getUniqueId()));
        plugin.instances().unindex(player);
        logic.onQuit(player);
        plugin.playerState().restore(player);
        plugin.sidebar().clear(player);
        if (teleportToLobby && player.isOnline()) {
            plugin.lobby().sendToLobby(player);
        }
        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            broadcast("<yellow>" + player.getName() + "</yellow> left <gray>(" + participants.size() + "/" + maxPlayers() + ")");
        } else if (wasAlive && state == GameState.RUNNING) {
            broadcast("<red>" + player.getName() + "</red> disconnected");
            checkEnd();
        }
        if (participants.isEmpty() && spectators.isEmpty() && assistants.isEmpty() && state != GameState.RESETTING
                && state != GameState.WAITING && state != GameState.COUNTDOWN) {
            cleanup();
        }
    }

    // ---- state machine -------------------------------------------------------

    private void tick() {
        switch (state) {
            case WAITING -> {
                if (participants.size() >= minPlayers()) {
                    state = GameState.COUNTDOWN;
                    countdown = practice ? 5 : plugin.getConfig().getInt("countdown.lobby-seconds", 30);
                }
            }
            case COUNTDOWN -> tickCountdown();
            case RUNNING -> {
                gameTime++;
                logic.onSecond(gameTime);
                if (state == GameState.RUNNING && gameTime >= logic.timeLimitSeconds()) {
                    logic.onTimeUp();
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

    private void tickCountdown() {
        if (participants.size() < minPlayers()) {
            state = GameState.WAITING;
            broadcast("<red>Not enough players, countdown cancelled.");
            return;
        }
        int full = plugin.getConfig().getInt("countdown.full-seconds", 10);
        if (participants.size() >= maxPlayers() && countdown > full) {
            countdown = full;
            broadcast("<green>Arena full! Starting soon.");
        }
        logic.onCountdownTick(countdown);
        if (countdown <= 5 || countdown == 10 || countdown == 15 || countdown == 30) {
            broadcast("<gray>Starting in <yellow>" + countdown + "s");
            for (Player p : players()) {
                if (!Text.calm(p)) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, countdown <= 3 ? 2f : 1f);
                }
                if (countdown <= 5) {
                    Text.title(p, countdown <= 3 ? "<red><b>" + countdown : "<yellow><b>" + countdown,
                            "<gray>" + arena.displayName());
                }
            }
        }
        for (Player p : players()) p.setLevel(countdown);
        if (--countdown < 0) start();
    }

    public void start() {
        if (state == GameState.RUNNING) return;
        state = GameState.RUNNING;
        gameTime = 0;
        alive.clear();
        alive.addAll(participants);
        if (mode.isTeams()) {
            teams.clear();
            teams.addAll(TeamAllocator.allocate(players(), mode.teamSize(), plugin.parties()));
        }
        int i = 0;
        for (Player p : players()) {
            plugin.playerState().reset(p);
            p.setGameMode(logic.playGameMode());
            GameTeam team = teamOf(p);
            p.teleport(arena.spawn(team != null ? team.index() : i++, world));
            p.setLevel(0);
            Text.title(p, "<green><b>GO!", "<gray>" + type.displayName() + " <dark_gray>• <gray>" + mode.displayName());
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
            plugin.stats().addPlayed(p, type.id());
        }
        if (mode.isTeams()) {
            for (GameTeam team : teams) {
                for (UUID member : team.members()) {
                    Player p = Bukkit.getPlayer(member);
                    if (p != null) p.sendMessage(Text.msg("You are on team " + team.coloredName()));
                }
            }
        }
        // Assistants follow the action from the spectator point, visible and safe.
        for (UUID id : assistants) {
            Player assistant = Bukkit.getPlayer(id);
            if (assistant != null) assistant.teleport(arena.spectator(world));
        }
        frozenUntil = System.currentTimeMillis() + logic.startFreezeSeconds() * 1000L;
        logic.onStart();
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
        Player killer = Bukkit.getPlayer(lastDamager.get(victim.getUniqueId()));
        return killer != null && killer != victim ? killer : null;
    }

    public void handleDeath(Player victim, Player killer) {
        if (state != GameState.RUNNING || !isAlive(victim)) return;
        victim.setFallDistance(0);
        victim.setFireTicks(0);
        Player resolvedKiller = killer != null ? killer : killerOf(victim);
        if (resolvedKiller != null) {
            plugin.cosmetics().playKillEffect(resolvedKiller, victim.getLocation());
            plugin.economy().addTokens(resolvedKiller,
                    plugin.getConfig().getInt("economy.kill-reward", 5), "kill");
            plugin.levels().addXp(resolvedKiller, 10);
            plugin.quests().record(resolvedKiller, type.id(), uk.co.playerready.pulsegames.core.quest.QuestService.Type.KILL);
            plugin.achievements().check(resolvedKiller);
        }
        logic.onDeath(victim, resolvedKiller);
    }

    private static final String[] ENCOURAGEMENT = {
            "<green>Nice try! <gray>Every round makes you better.",
            "<green>Great effort! <gray>You lasted longer than last time?",
            "<green>So close! <gray>You'll get them next round.",
            "<green>Well played! <gray>Watch the others for new tricks.",
            "<green>Good game! <gray>Want to try a different mode next?",
    };

    public void eliminate(Player player) {
        if (!alive.remove(player.getUniqueId())) return;
        spectators.add(player.getUniqueId());
        Location deathSpot = player.getLocation().add(0, 1, 0);
        // Per-viewer effects so calm-mode players aren't startled.
        for (Player p : everyone()) {
            if (Text.calm(p)) continue;
            p.spawnParticle(org.bukkit.Particle.CLOUD, deathSpot, 30, 0.3, 0.5, 0.3, 0.05);
            p.spawnParticle(org.bukkit.Particle.FLAME, deathSpot, 15, 0.2, 0.3, 0.2, 0.02);
            p.playSound(deathSpot, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 1.6f);
        }
        plugin.playerState().reset(player);
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.teleport(arena.spectator(world));
        Text.title(player, "<red><b>ELIMINATED", "<gray>You are now spectating");
        if (plugin.getConfig().getBoolean("messages.encouragement", true)) {
            player.sendMessage(Text.msg(
                    ENCOURAGEMENT[ThreadLocalRandom.current().nextInt(ENCOURAGEMENT.length)]));
        }
        player.sendMessage(Text.msg("<gray>You're spectating - use the vanilla spectator menu "
                + "(<yellow>1</yellow>) to follow players, or <yellow>/lobby</yellow> to leave."));
        logic.onEliminated(player);
        checkEnd();
    }

    public void respawn(Player player, int delaySeconds) {
        if (state != GameState.RUNNING) return;
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.teleport(arena.spectator(world));
        Text.title(player, "<red>You died!", "<gray>Respawning in " + delaySeconds + "s");
        runLater(delaySeconds * 20L, () -> {
            if (state != GameState.RUNNING || !participants.contains(player.getUniqueId()) || !player.isOnline()) return;
            plugin.playerState().reset(player);
            player.setGameMode(logic.playGameMode());
            player.teleport(logic.respawnLocation(player));
            logic.onRespawn(player);
        });
    }

    public void checkEnd() {
        if (state != GameState.RUNNING) return;
        if (mode.isTeams()) {
            List<GameTeam> remaining = aliveTeams();
            if (remaining.size() <= 1) {
                end(remaining.isEmpty() ? List.of() : alivePlayers(), "last-team");
            }
        } else if (alive.size() <= 1) {
            end(alivePlayers(), "last-standing");
        }
    }

    public void end(List<Player> winners, String reason) {
        if (state == GameState.ENDING || state == GameState.RESETTING) return;
        state = GameState.ENDING;
        countdown = plugin.getConfig().getInt("end.celebrate-seconds", 10);
        String names = winners.isEmpty() ? "Nobody"
                : String.join("<gray>, </gray><yellow>", winners.stream().map(Player::getName).toList());
        broadcast("<gold><b>GAME OVER!</b></gold> <gray>Winner(s): <yellow>" + names);
        for (Player p : everyone()) {
            if (winners.contains(p)) {
                Text.title(p, "<gold><b>VICTORY!", "<gray>You won " + type.displayName());
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                plugin.stats().addWin(p, type.id());
                plugin.economy().addTokens(p, plugin.getConfig().getInt("economy.win-reward", 50), "victory");
                plugin.levels().addXp(p, 75);
                plugin.quests().record(p, type.id(), uk.co.playerready.pulsegames.core.quest.QuestService.Type.WIN);
                spawnFirework(p.getLocation());
            } else {
                if (isParticipant(p)) {
                    plugin.economy().addTokens(p,
                            plugin.getConfig().getInt("economy.participation-reward", 10), "game played");
                    plugin.levels().addXp(p, 25);
                }
                Text.title(p, "<red><b>GAME OVER", "<gray>Winner(s): " + names);
                if (isParticipant(p)) plugin.stats().addLoss(p, type.id());
            }
            p.setGameMode(org.bukkit.GameMode.SPECTATOR);
            p.sendMessage(Text.msg("<gray>Record: <green>" + plugin.stats().get(p, type.id(), "wins")
                    + "W</green> <red>" + plugin.stats().get(p, type.id(), "losses") + "L</red> <gray>in "
                    + type.displayName() + " <dark_gray>(" + plugin.stats().get(p, type.id(), "played") + " played)"));
        }
        for (Player p : everyone()) {
            if (isParticipant(p)) {
                plugin.quests().record(p, type.id(), uk.co.playerready.pulsegames.core.quest.QuestService.Type.PLAY);
                plugin.achievements().check(p);
            }
        }
        // Celebration: fireworks rain on the winners until cleanup.
        runRepeating(20L, 30L, () -> {
            for (Player winner : winners) {
                if (!winner.isOnline() || winner.getWorld() != world) continue;
                spawnFirework(winner.getLocation());
                plugin.cosmetics().playWinEffect(winner);
            }
        });
        logic.onEnd(winners);
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
        tasks.forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        tasks.clear();
        for (Player p : everyone()) {
            plugin.instances().unindex(p);
            plugin.playerState().restore(p);
            plugin.sidebar().clear(p);
            plugin.lobby().sendToLobby(p);
        }
        participants.clear();
        alive.clear();
        spectators.clear();
        assistants.clear();
        if (world != null) {
            plugin.npcs().despawnForInstance(this);
            plugin.worlds().unloadAndDelete(world);
            world = null;
        }
        plugin.instances().remove(this);
        plugin.stats().flush();
    }

    // ---- helpers -----------------------------------------------------------------

    public void broadcast(String miniMessage) {
        Component component = Text.msg(miniMessage);
        for (Player p : everyone()) p.sendMessage(component);
    }

    public void broadcastRaw(String miniMessage) {
        Component component = Text.mm(miniMessage);
        for (Player p : everyone()) p.sendMessage(component);
    }

    public BukkitTask runLater(long delayTicks, Runnable runnable) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        tasks.add(task);
        return task;
    }

    public BukkitTask runRepeating(long delayTicks, long periodTicks, Runnable runnable) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        tasks.add(task);
        return task;
    }

    private void updateSidebars() {
        String title = "<gradient:#ff5f6d:#ffc371><b>" + type.displayName().toUpperCase() + "</b></gradient>";
        for (Player p : everyone()) {
            List<String> lines = new ArrayList<>();
            lines.add("<dark_gray>" + arena.displayName());
            lines.add("");
            switch (state) {
                case WAITING -> {
                    lines.add("<gray>Players: <white>" + participants.size() + "/" + maxPlayers());
                    lines.add("<gray>Waiting for players...");
                }
                case COUNTDOWN -> {
                    lines.add("<gray>Players: <white>" + participants.size() + "/" + maxPlayers());
                    lines.add("<gray>Starting in: <green>" + countdown + "s");
                }
                case RUNNING -> {
                    List<String> custom = logic.sidebar(p);
                    if (custom.isEmpty()) {
                        lines.add("<gray>Alive: <white>" + alive.size());
                        lines.add("<gray>Time: <white>" + Text.time(gameTime));
                    } else {
                        lines.addAll(custom);
                    }
                }
                default -> { }
            }
            lines.add("");
            lines.add("<gray>Mode: <yellow>" + mode.displayName());
            lines.add("<dark_gray>readycraft");
            plugin.sidebar().show(p, title, lines);
        }
    }
}
