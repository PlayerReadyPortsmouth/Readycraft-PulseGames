package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.games.dodgeball.DodgeballGame;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Owns ephemeral contestants and bounded game-specific decisions, all on the server thread. */
public final class BotService implements AutoCloseable {
    private static final Set<String> SUPPORTED = Set.of("duels:classic", "duels:op", "duels:sumo",
            "koth:solo", "koth:teams", "kitpvp:ffa", "dodgeball:solo", "dodgeball:teams",
            "villagerdefense:easy", "villagerdefense:hard",
            "readyplayerz:classic", "readyplayerz:frenzy", "blockparty:classic", "blockparty:hardcore",
            "tntrun:classic", "volcano:classic", "volcano:eruption", "avalanche:classic", "spleef:classic", "spleef:decay", "spleef:splegg");
    private static final class Slot {
        final GameInstance game;
        final BotActor actor;
        Location lastPosition, destination;
        Player entity;
        long nextAttack, nextPath;
        Slot(GameInstance game, BotActor actor) { this.game = game; this.actor = actor; }
    }
    private final PulseGamesPlugin plugin;
    private final Map<UUID, Slot> slots = new LinkedHashMap<>();
    private final Map<GameInstance, Integer> waiting = new HashMap<>();
    private BotProvider provider;
    private boolean failed;
    private int sequence;
    private long tick;
    private java.util.function.IntUnaryOperator random = bound -> ThreadLocalRandom.current().nextInt(bound);

    public BotService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (plugin.getConfig().getBoolean("bots.enabled", false)) connect();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::think, 5L, 5L);
    }

    /** Injection point for an in-memory transport in unit tests. */
    BotService(PulseGamesPlugin plugin, BotProvider provider) {
        this.plugin = plugin;
        this.provider = provider;
    }

    BotService(PulseGamesPlugin plugin, BotProvider provider, java.util.function.IntUnaryOperator random) {
        this(plugin, provider);
        this.random = random;
    }

    private void connect() {
        if (provider != null || failed) return;
        provider = CitizensBotProvider.connect(plugin).orElse(null);
        if (provider == null) failed = true;
    }

    public boolean isBot(UUID id) { return slots.containsKey(id); }
    public boolean isBot(Player player) { return isBot(player.getUniqueId()); }
    public Player player(UUID id) {
        Slot slot = slots.get(id);
        return slot == null ? null : slot.actor.player();
    }
    public int count(GameInstance game) {
        return (int) slots.values().stream().filter(s -> s.game == game).count();
    }
    public String status() {
        return "Queue bots: " + (!plugin.getConfig().getBoolean("bots.enabled", false) ? "disabled"
                : failed ? "provider unavailable (check logs, then restart)" : provider == null ? "not connected" : "ready")
                + "; active=" + slots.size() + "; supported=" + String.join(", ", new TreeSet<>(SUPPORTED));
    }

    public boolean eligible(GameInstance game) {
        return plugin.getConfig().getBoolean("bots.enabled", false)
                && game.arena().settings().getBoolean("bots-enabled", false)
                && game.players().stream().filter(p -> !isBot(p)).allMatch(plugin::canUseNewFeatures)
                && SUPPORTED.contains(game.type().id() + ":" + game.mode().rulesId());
    }

    /** Called once per second by the instance before its lobby state transition. */
    public void reconcile(GameInstance game) {
        if (game.humanCount() == 0) {
            waiting.remove(game);
            if (count(game) > 0) game.cleanup();
            return;
        }
        if (game.state() != GameState.WAITING && game.state() != GameState.COUNTDOWN) {
            waiting.remove(game);
            return;
        }
        if (!eligible(game)) {
            waiting.remove(game);
            removeAll(game);
            return;
        }
        connect();
        if (provider == null || failed) return;
        int elapsed = waiting.merge(game, 1, Integer::sum);
        if (elapsed < Math.max(1, plugin.getConfig().getInt("bots.wait-seconds", 30))) return;
        int current = count(game);
        int desired = QueueFillPolicy.desiredBots(game.humanCount(), game.minPlayers(), game.maxPlayers(),
                Math.max(0, Math.min(63, plugin.getConfig().getInt("bots.max-per-match", 31))),
                Math.max(0, Math.min(64, plugin.getConfig().getInt("bots.max-total", 64))) - slots.size() + current);
        while (count(game) > desired) removeOne(game);
        for (int i = count(game); i < desired; i++) {
            try {
                BotActor actor = provider.create("Ready" + (++sequence), game.arena().lobby(game.world()));
                UUID id = actor.player().getUniqueId();
                slots.put(id, new Slot(game, actor));
                if (!game.add(actor.player())) { destroy(id); break; }
            } catch (RuntimeException failure) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not fill game with bots", failure);
                failed = true;
                removeAll(game);
                break;
            }
        }
    }

    /** Humans can reclaim waiting/countdown seats; running rounds do not admit late players. */
    public void beforeHumanJoin(GameInstance game, Player human) {
        if (!plugin.canUseNewFeatures(human)) removeAll(game);
        makeRoom(game, 1);
    }
    public void makeRoom(GameInstance game, int humansJoining) {
        if (game.state() != GameState.WAITING && game.state() != GameState.COUNTDOWN) return;
        while (game.players().size() + humansJoining > game.maxPlayers() && count(game) > 0) removeOne(game);
    }
    private void removeOne(GameInstance game) {
        slots.values().stream().filter(s -> s.game == game).findFirst()
                .ifPresent(slot -> game.remove(slot.actor.player(), false));
    }
    private void removeAll(GameInstance game) {
        for (Slot slot : List.copyOf(slots.values())) if (slot.game == game) {
            game.remove(slot.actor.player(), false);
            destroy(slot.actor.player().getUniqueId());
        }
    }
    public void destroy(UUID id) {
        Slot slot = slots.remove(id);
        if (slot == null) return;
        try { slot.actor.destroy(); }
        catch (RuntimeException failure) {
            failed = true;
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Bot removal failed; provider retains cleanup ownership", failure);
        }
    }
    public void forget(GameInstance game) {
        waiting.remove(game);
        for (var entry : Map.copyOf(slots).entrySet()) if (entry.getValue().game == game) destroy(entry.getKey());
    }

    void think() {
        tick += 5;
        if (!plugin.getConfig().getBoolean("bots.enabled", false) && !slots.isEmpty()) {
            for (GameInstance game : slots.values().stream().map(s -> s.game).distinct().toList()) {
                game.forceEnd();
                removeAll(game);
            }
        }
        for (Slot slot : List.copyOf(slots.values())) {
            if (!slots.containsKey(slot.actor.player().getUniqueId())) continue;
            try { control(slot); }
            catch (RuntimeException failure) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Bot controller failed; ending affected round", failure);
                failed = true;
                slot.game.forceEnd();
                slot.game.remove(slot.actor.player(), false);
            }
        }
    }

    private void control(Slot slot) {
        GameInstance game = slot.game;
        Player bot = slot.actor.player();
        if (slot.entity != bot) {
            slot.entity = bot;
            slot.destination = null;
            slot.lastPosition = null;
            slot.nextPath = 0;
        }
        if (!slot.actor.valid()) {
            game.forceEnd();
            game.remove(bot, false);
            return;
        }
        if (game.state() != GameState.RUNNING || game.frozen() || !game.isActive(bot) || !game.logic().acceptsDeath(bot)) {
            slot.actor.stopNavigation();
            slot.lastPosition = null;
            return;
        }
        Location here = bot.getLocation();
        if (!here.getWorld().equals(game.world())) {
            game.forceEnd();
            return;
        }
        if (here.getY() < game.logic().voidY()) {
            slot.actor.stopNavigation();
            game.handleDeath(bot, null);
            slot.lastPosition = null;
            return;
        }
        // Citizens navigation is not a network PlayerMoveEvent. Route one sampled move.
        if (slot.lastPosition != null && !slot.lastPosition.equals(here)) {
            game.logic().onMove(bot, slot.lastPosition, here);
        }
        slot.lastPosition = here.clone();
        if (game.state() != GameState.RUNNING || !game.isActive(bot)) return;
        BotDifficulty difficulty = BotDifficulty.parse(game.arena().settings().getString("bot-difficulty",
                plugin.getConfig().getString("bots.difficulty", "normal")));
        if (game.logic() instanceof BotMovement movement) {
            movement.botTick(bot, tick, difficulty);
            if (game.state() != GameState.RUNNING || !game.isActive(bot)) return;
            navigate(slot, here, movement.botDestination(bot), difficulty);
            return;
        }
        BotObjectives objectives = game.logic() instanceof BotObjectives coop ? coop : null;
        if (objectives != null) objectives.botTick(bot);
        LivingEntity enemy = objectives != null
                ? objectives.botEnemies().stream()
                    .filter(e -> e.isValid() && !e.isDead() && e.getWorld().equals(here.getWorld()))
                    .filter(e -> here.distanceSquared(e.getLocation()) <= 24 * 24 && bot.hasLineOfSight(e))
                    .min(Comparator.comparingDouble(e -> here.distanceSquared(e.getLocation()))).orElse(null)
                : game.alivePlayers().stream()
                .filter(p -> !p.equals(bot) && game.isActive(p) && p.getWorld().equals(here.getWorld()))
                .filter(p -> game.teamOf(bot) == null || !game.teamOf(bot).contains(p))
                .filter(p -> here.distanceSquared(p.getLocation()) <= 24 * 24 && bot.hasLineOfSight(p))
                .min(Comparator.comparingDouble(p -> here.distanceSquared(p.getLocation()))).orElse(null);
        Location goal = null;
        if (enemy != null) {
            double distance = here.distanceSquared(enemy.getLocation());
            slot.actor.face(enemy.getEyeLocation());
            if (game.logic() instanceof DodgeballGame dodgeball) {
                if (distance < 22 * 22 && tick >= slot.nextAttack) {
                    // Real throws use the game's cooldown and projectile/hit rules.
                    dodgeball.throwBall(bot);
                    slot.nextAttack = tick + difficulty.throwTicks + random.applyAsInt(16);
                }
                if (distance > 9 * 9) goal = enemy.getLocation();
                else if (distance > 0.01 && distance < 4 * 4) goal = here.clone().add(here.toVector().subtract(enemy.getLocation().toVector()).normalize().multiply(3));
            } else {
                if (distance <= 2.8 * 2.8 && tick >= slot.nextAttack) {
                    if (random.applyAsInt(100) < difficulty.accuracy) {
                        if (objectives != null) selectSword(bot);
                        bot.swingMainHand();
                        if (game.mode().combatStyle() != GameMode.CombatStyle.MODERN || bot.getAttackCooldown() >= 0.9f)
                            bot.attack(enemy);
                    }
                    slot.nextAttack = tick + difficulty.meleeTicks + random.applyAsInt(9);
                }
                if (distance > 2.3 * 2.3) goal = enemy.getLocation();
            }
        }
        if (game.type().id().equals("koth") && (enemy == null || here.distanceSquared(enemy.getLocation()) > 6 * 6)) {
            var hill = game.arena().region("hill");
            if (hill != null) goal = hill.center(game.world());
        }
        if (goal == null && enemy == null) {
            goal = objectives != null ? objectives.botGuardPoint() : game.arena().spawn((int) ((tick / 100 + bot.getEntityId()) % Math.max(1, game.arena().spawnCount())), game.world());
        }
        navigate(slot, here, goal, difficulty);
    }

    private void navigate(Slot slot, Location here, Location goal, BotDifficulty difficulty) {
        if (goal != null && !here.getWorld().equals(goal.getWorld())) {
            slot.actor.stopNavigation();
            slot.destination = null;
            return;
        }
        if (goal == null || here.distanceSquared(goal) < (slot.game.logic() instanceof BotMovement ? 0.04 : 1)) {
            slot.actor.stopNavigation();
            slot.destination = null;
        } else if (tick >= slot.nextPath && (slot.destination == null || slot.destination.distanceSquared(goal) > (slot.game.logic() instanceof BotMovement ? 0.01 : 2) || tick % 100 == 0)) {
            if (slot.game.logic() instanceof BotMovement movement && movement.straightNavigation()) slot.actor.navigateStraight(goal);
            else slot.actor.navigate(goal);
            slot.destination = goal.clone();
            slot.nextPath = tick + (slot.game.logic() instanceof BotMovement movement
                    ? Math.max(5, movement.navigationIntervalTicks(difficulty)) : difficulty.pathTicks);
        }
    }

    static void selectSword(Player bot) {
        int bestSlot = -1, bestRank = 0;
        for (int i = 0; i < 9; i++) {
            var item = bot.getInventory().getItem(i);
            if (item == null) continue;
            int rank = switch (item.getType()) {
                case WOODEN_SWORD, GOLDEN_SWORD -> 1;
                case STONE_SWORD -> 2;
                case IRON_SWORD -> 3;
                case DIAMOND_SWORD -> 4;
                case NETHERITE_SWORD -> 5;
                default -> 0;
            };
            if (rank > bestRank) { bestRank = rank; bestSlot = i; }
        }
        if (bestSlot >= 0) bot.getInventory().setHeldItemSlot(bestSlot);
    }

    @Override public void close() {
        for (UUID id : List.copyOf(slots.keySet())) destroy(id);
        waiting.clear();
        if (provider != null) provider.close();
        provider = null;
    }
}
