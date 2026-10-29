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
    private static final Set<String> SUPPORTED = Set.of("karts:sprint", "karts:cup", "quake:classic", "duels:classic", "duels:op", "duels:sumo", "duels:crystal", "duels:mace",
            "koth:solo", "koth:teams", "kitpvp:ffa", "kitpvp:oitc", "prophunt:classic", "prophunt:infection", "skywars:solo", "skywars:doubles", "skywars:insane", "bedwars:duos", "bedwars:quads", "dodgeball:solo", "dodgeball:teams",
            "villagerdefense:easy", "villagerdefense:hard",
            "readyplayerz:classic", "readyplayerz:frenzy", "blockparty:classic", "blockparty:hardcore",
            "tntrun:classic", "volcano:classic", "volcano:eruption", "avalanche:classic", "spleef:classic", "spleef:decay", "spleef:splegg");
    private static final class Slot {
        final GameInstance game;
        final BotActor actor;
        Location lastPosition, destination, progressPosition;
        long progressTick, lastStallReportTick;
        Player entity;
        long nextAttack, nextPath, reactAt;
        UUID opponent;
        int routeIndex;
        Location recovery;
        long recoverUntil;
        String objective = "Waiting for the round", target = "", route = "", state = "WAITING", reason = "";
        BotDifficulty difficulty = BotDifficulty.NORMAL;
        boolean stalled;
        BotMelee melee;
        final BotKitUse kit;
        Slot(GameInstance game, BotActor actor, java.util.function.Consumer<Player> eatEffects) { this.game = game; this.actor = actor; this.kit = new BotKitUse(eatEffects); }
    }
    private final PulseGamesPlugin plugin;
    private final Map<UUID, Slot> slots = new LinkedHashMap<>();
    private final Map<GameInstance, Integer> waiting = new HashMap<>();
    private BotProvider provider;
    private AdaptiveBotSkillStore skills;
    private final Map<GameInstance, SkillRound> skillRounds = new HashMap<>();
    private record SkillRound(String mode, Map<UUID, BotDifficulty> levels, BotDifficulty group, double expected) {}
    private boolean failed;
    private int sequence;
    private long tick;
    /** Seams for unit tests, which have no server registries. */
    java.util.function.Predicate<Location> walkable = GroundNavigation::walkable;
    java.util.function.Consumer<Player> eatEffects = BotKitUse::goldenAppleEffects;
    private BotCombatSettings combat = BotCombatSettings.defaults();
    private java.util.function.IntUnaryOperator random = bound -> ThreadLocalRandom.current().nextInt(bound);

    public BotService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        skills = new AdaptiveBotSkillStore(new java.io.File(plugin.getDataFolder(), "bot-skill.yml"), plugin.getLogger());
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

    BotService(PulseGamesPlugin plugin, BotProvider provider, AdaptiveBotSkillStore skills) {
        this(plugin, provider);
        this.skills = skills;
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

    public boolean supports(String game, String rules) { return SUPPORTED.contains(game + ":" + rules); }

    /** Only bot opt-in is live-reloaded; running rounds retain their original map settings. */
    private boolean mapBotsEnabled(GameInstance game) {
        var arena = game.arena();
        if ((game.state() == GameState.WAITING || game.state() == GameState.COUNTDOWN)
                && plugin.arenas() != null) {
            var latest = plugin.arenas().byId(game.type().id(), arena.id());
            if (latest != null) arena = latest;
        }
        return arena.settings().getBoolean("bots-enabled", false);
    }

    private String blockedReason(GameInstance game) {
        if (!plugin.getConfig().getBoolean("bots.enabled", false)) return "disabled";
        if (!mapBotsEnabled(game)) return "disabled on this map";
        if (!supports(game.type().id(), game.mode().rulesId())) return "unsupported mode";
        if (!game.players().stream().filter(p -> !isBot(p)).allMatch(plugin::canUseNewFeatures))
            return "testing access required";
        if (game.type().id().equals("prophunt") && plugin.getConfig().getBoolean("content.prophunt.enabled", false)
                && (!plugin.getConfig().getBoolean("content.prophunt.block-snapping", false)
                    || plugin.content() == null || !plugin.content().npcPropVisualsReady())) return "disguises unavailable";
        if (game.type().id().equals("karts")) {
            String problem = uk.co.playerready.pulsegames.games.karts.KartBotRoute.validationError(game.arena());
            if (problem != null) return "kart route: " + problem;
        }
        return null;
    }

    public boolean eligible(GameInstance game) { return blockedReason(game) == null; }

    private int waitSeconds(GameInstance game) {
        return game.practice() ? 2 : Math.max(1, plugin.getConfig().getInt("bots.wait-seconds", 30));
    }

    private int desiredBots(GameInstance game) {
        int desired = QueueFillPolicy.desiredBots(game.humanCount(), game.minPlayers(), game.maxPlayers(),
                Math.max(0, Math.min(63, plugin.getConfig().getInt("bots.max-per-match", 31))),
                Math.max(0, Math.min(64, plugin.getConfig().getInt("bots.max-total", 64))) - slots.size() + count(game));
        return game.practice() ? Math.min(desired, game.practiceBotTarget()) : desired;
    }

    /** Read-only view of the same timer and gates used by reconcile. */
    public String queueStatus(GameInstance game) {
        String blocked = blockedReason(game);
        if (blocked != null) return "Bots: " + blocked;
        if (failed) return "Bots: unavailable (error)";
        if (provider == null) return "Bots: connecting";
        if (game.humanCount() == 0) return "Bots: waiting for players";
        if (game.humanCount() >= game.maxPlayers()) return "Bots: lobby full";
        int current = count(game), desired = desiredBots(game);
        if (current > 0 && current >= desired) return "Bots: " + current + " joined";
        if (desired == 0) return "Bots: capacity limit";
        int remaining = Math.max(0, waitSeconds(game) - waiting.getOrDefault(game, 0));
        return remaining > 0 ? "Bots join in: " + remaining + "s" : "Bots: joining...";
    }

    /** Coarse calibration for allocation only; does not expose a player's stored results. */
    public int teamStrength(GameInstance game, Player player) {
        if (game.practice()) return game.practiceDifficulty().ordinal() + 1;
        String key = game.type().id() + ":" + game.mode().id();
        if (!isBot(player)) return (skills == null ? BotDifficulty.NORMAL : skills.difficulty(player.getUniqueId(), key)).ordinal() + 1;
        String selection = game.arena().settings().getString("bot-difficulty", plugin.getConfig().getString("bots.difficulty", "adaptive"));
        if (!"adaptive".equalsIgnoreCase(selection)) return BotDifficulty.parse(selection).ordinal() + 1;
        var humanLevels = game.players().stream().filter(p -> !isBot(p))
                .map(p -> skills == null ? BotDifficulty.NORMAL : skills.difficulty(p.getUniqueId(), key)).sorted().toList();
        return (humanLevels.isEmpty() ? BotDifficulty.NORMAL : humanLevels.get((humanLevels.size() - 1) / 2)).ordinal() + 1;
    }

    public List<BotDiagnostics.Snapshot> diagnostics() {
        return slots.values().stream().limit(64).map(slot -> new BotDiagnostics.Snapshot(
                slot.actor.player().getUniqueId(), slot.actor.player().getName(), slot.game.id(), slot.objective,
                slot.difficulty, slot.target, slot.route, slot.state, diagnosticReason(slot),
                slot.progressPosition == null ? 0 : Math.max(0, tick - slot.progressTick))).toList();
    }

    private String diagnosticReason(Slot slot) {
        String detail;
        if(slot.game.practice()) detail="Fixed practice difficulty; this round does not train adaptive history.";
        else {
            String selection=slot.game.arena().settings().getString("bot-difficulty",plugin.getConfig().getString("bots.difficulty","adaptive"));
            Player target=slot.game.players().stream().filter(p->!isBot(p) && p.getName().equals(slot.target)).findFirst().orElse(null);
            if(!"adaptive".equalsIgnoreCase(selection)) detail="Fixed difficulty override: "+selection;
            else if(target==null || skills==null) detail="Adaptive group difficulty; no visible human target selected.";
            else detail=skills.calibration(target.getUniqueId(),slot.game.type().id()+":"+slot.game.mode().id()).reason();
        }
        return slot.reason.isBlank()?detail:slot.reason+"; "+detail;
    }

    /** Freeze calibration for the round; profiles never change during a fight. */
    public void onRoundStart(GameInstance game) {
        if (skills == null || game.practice()) return;
        String mode = game.type().id() + ":" + game.mode().id();
        Map<UUID, BotDifficulty> levels = new HashMap<>();
        for (Player p : game.players()) if (!isBot(p)) levels.put(p.getUniqueId(), skills.difficulty(p.getUniqueId(), mode));
        var sorted = levels.values().stream().sorted().toList();
        BotDifficulty group = sorted.isEmpty() ? BotDifficulty.NORMAL : sorted.get((sorted.size() - 1) / 2);
        long teams = game.teams().stream().filter(t -> !t.members().isEmpty()).count();
        double expected = game.logic().cooperative() || game.type().id().equals("prophunt") ? 0.5
                : teams > 1 ? 1.0 / teams : 1.0 / Math.max(2, game.players().size());
        skillRounds.put(game, new SkillRound(mode, Map.copyOf(levels), group, expected));
    }

    public BotDifficulty difficultyFor(GameInstance game, Player opponent) {
        if (game.practice()) return game.practiceDifficulty();
        String selection = game.arena().settings().getString("bot-difficulty",
                plugin.getConfig().getString("bots.difficulty", "adaptive"));
        if (!"adaptive".equalsIgnoreCase(selection)) return BotDifficulty.parse(selection);
        SkillRound round = skillRounds.get(game);
        if (round == null) return BotDifficulty.NORMAL;
        return opponent == null ? round.group : round.levels.getOrDefault(opponent.getUniqueId(), round.group);
    }

    /** Practice history is separate from competitive records, tokens and rewards. */
    public void onRoundEnd(GameInstance game, List<Player> winners, String reason) {
        SkillRound round = skillRounds.remove(game);
        if (game.practice() || round == null || skills == null || game.state() != GameState.RUNNING || game.gameTime() < 1
                || reason == null || reason.equals("forced") || reason.contains("draw") || reason.equals("no-seekers")
                || winners.isEmpty() && !game.logic().cooperative()) return;
        Set<UUID> won = new HashSet<>();
        winners.forEach(p -> won.add(p.getUniqueId()));
        // A departure invalidates the sample: winning an abandoned lobby is not a skill measurement.
        Set<UUID> present = new HashSet<>();
        game.players().stream().filter(p -> !isBot(p)).forEach(p -> present.add(p.getUniqueId()));
        if (!present.equals(round.levels.keySet())) return;
        for (Player player : game.players()) {
            UUID id = player.getUniqueId();
            if (!present.contains(id)) continue;
            boolean success = won.contains(id);
            if (game.logic() instanceof uk.co.playerready.pulsegames.games.prophunt.PropHuntGame prop)
                success = prop.botSkillWin(player, winners);
            else if (game.logic().cooperative()) success = !winners.isEmpty();
            else if (game.mode().isTeams() && game.teamOf(player) != null)
                success = winners.stream().anyMatch(game.teamOf(player)::contains);
            skills.record(id, round.mode, success, round.expected);
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, skills::save);
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
        if (elapsed < waitSeconds(game)) return;
        int desired = desiredBots(game);
        while (count(game) > desired) removeOne(game);
        for (int i = count(game); i < desired; i++) {
            try {
                BotActor actor = provider.create(BotNames.at(sequence++), game.arena().lobby(game.world()));
                UUID id = actor.player().getUniqueId();
                slots.put(id, new Slot(game, actor, bot -> eatEffects.accept(bot)));
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
        skillRounds.remove(game);
        for (var entry : Map.copyOf(slots).entrySet()) if (entry.getValue().game == game) destroy(entry.getKey());
    }

    void think() {
        tick += 5;
        combat = BotCombatSettings.read(plugin.getConfig());
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
            slot.state = game.state() == GameState.RUNNING ? "PAUSED" : game.state().name();
            slot.objective = game.frozen() ? "Waiting for start countdown" : "Waiting / inactive contestant";
            slot.target = ""; slot.route = ""; slot.opponent = null;
            return;
        }
        if (game.logic() instanceof uk.co.playerready.pulsegames.games.karts.PulseKartsGame karts) {
            // Mounted movement belongs exclusively to the 20 Hz chassis controller.
            slot.actor.stopNavigation();
            slot.lastPosition = null; slot.destination = null;
            slot.difficulty = difficultyFor(game, null);
            slot.objective = "Driving the mapped kart route";
            slot.state = "DRIVING"; slot.route = karts.botStatus(bot); slot.reason = "";
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
        if (slot.destination != null && slot.destination.getWorld().equals(here.getWorld()) && slot.destination.distanceSquared(here)>9) {
            if (slot.progressPosition==null || !slot.progressPosition.getWorld().equals(here.getWorld()) || slot.progressPosition.distanceSquared(here)>1) {
                slot.progressPosition=here.clone();slot.progressTick=tick;slot.stalled=false;
            } else if (tick-slot.progressTick>=300 && tick-slot.lastStallReportTick>=300) {
                game.note("bot-navigation-stalled", "No block of progress toward a distant goal for 15 seconds");slot.lastStallReportTick=tick;slot.stalled=true;
                if (!game.type().id().equals("skywars") && !game.type().id().equals("bedwars") && (!(game.logic() instanceof BotMovement) || game.type().id().equals("prophunt"))) {
                    Location recovery = BotNavigationHints.nearestSafe(game, here, "bot-recovery-points");
                    if (recovery != null && recovery.distanceSquared(here) <= 144 && recovery.distanceSquared(here) > 1) {
                        slot.recovery = recovery; slot.recoverUntil = tick + 100; slot.nextPath = 0;
                    }
                }
            }
        } else {slot.progressPosition=null;slot.progressTick=tick;slot.stalled=false;}
        slot.lastPosition = here.clone();
        if (game.state() != GameState.RUNNING || !game.isActive(bot)) return;
        BotDifficulty difficulty = difficultyFor(game, null);
        slot.difficulty = difficulty; slot.target = "";
        slot.state = slot.stalled ? "STALLED" : "ACTIVE";
        slot.reason = slot.stalled ? "No progress for 15 seconds; check route clearance and reachability" : "";
        slot.objective = "Looking for an objective";
        if (game.logic() instanceof BotMovement movement) {
            slot.objective = "Following " + game.type().id() + " controller";
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
                    .min(Comparator.comparingDouble(e -> BotPersonality.targetScore(here.distanceSquared(e.getLocation()), e.getUniqueId().equals(slot.opponent)))).orElse(null)
                : game.alivePlayers().stream()
                .filter(p -> !p.equals(bot) && game.isActive(p) && p.getWorld().equals(here.getWorld()))
                .filter(p -> game.teamOf(bot) == null || !game.teamOf(bot).contains(p))
                .filter(p -> bot.canSee(p) && !p.isInvisible() && here.distanceSquared(p.getLocation()) <= 24 * 24 && bot.hasLineOfSight(p))
                .min(Comparator.comparingDouble(p -> BotPersonality.targetScore(here.distanceSquared(p.getLocation()), p.getUniqueId().equals(slot.opponent)))).orElse(null);
        Location goal = null;
        if (enemy == null) { slot.opponent = null; if (slot.melee != null) { bot.setSprinting(false); slot.kit.stopDrawing(); } }
        if (enemy != null) {
            difficulty = difficultyFor(game, enemy instanceof Player target ? target : null);
            slot.difficulty = difficulty; slot.target = enemy.getName();
            if (!enemy.getUniqueId().equals(slot.opponent)) {
                slot.opponent = enemy.getUniqueId();
                slot.reactAt = tick + BotPersonality.reactionTicks(difficulty, random.applyAsInt(combat.profile(difficulty).reactionJitter() + 1));
                slot.nextAttack = Math.max(slot.nextAttack, slot.reactAt);
            }
            slot.objective = "Engaging a visible opponent"; slot.route = "";
            double distance = here.distanceSquared(enemy.getLocation());
            boolean pvp = objectives == null && enemy instanceof Player && pvpCore(game);
            BotDifficulty.Profile profile = combat.profile(difficulty);
            if (pvp) {
                if (slot.melee == null) slot.melee = new BotMelee(bot.getUniqueId(), new Random(System.nanoTime() ^ bot.getUniqueId().getLeastSignificantBits()), () -> tick);
                slot.actor.face(slot.melee.aim(bot.getEyeLocation(), enemy.getEyeLocation(), profile, difficulty == BotDifficulty.EASY));
            } else slot.actor.face(enemy.getEyeLocation());
            if (game.logic() instanceof DodgeballGame dodgeball) {
                if (distance < 22 * 22 && tick >= slot.nextAttack) {
                    // Real throws use the game's cooldown and projectile/hit rules.
                    dodgeball.throwBall(bot);
                    slot.nextAttack = tick + difficulty.throwTicks + random.applyAsInt(16);
                }
                if (distance > 9 * 9) goal = enemy.getLocation();
                else if (distance > 0.01 && distance < 4 * 4) goal = here.clone().add(here.toVector().subtract(enemy.getLocation().toVector()).normalize().multiply(3));
            } else {
                boolean handled = tick < slot.reactAt;
                if (!handled) handled = game.logic() instanceof uk.co.playerready.pulsegames.games.kitpvp.KitPvpGame kit
                        && kit.botCombat(bot, enemy, tick, difficulty);
                if (!handled && game.logic() instanceof uk.co.playerready.pulsegames.games.duels.DuelsGame duels)
                    handled = duels.botCombat(bot, enemy, tick, difficulty);
                if (!handled && game.logic() instanceof uk.co.playerready.pulsegames.games.skywars.SkywarsGame sky)
                    handled = sky.botCombat(bot, enemy, tick, difficulty);
                if (!handled && game.logic() instanceof uk.co.playerready.pulsegames.games.bedwars.BedwarsGame bed)
                    handled = bed.botCombat(bot, enemy, tick, difficulty);
                if (!handled && pvp) goal = pvpCombat(slot, game, bot, (Player) enemy, here, distance, difficulty, profile);
                else {
                    if (!handled && distance <= 2.8 * 2.8 && tick >= slot.nextAttack) {
                        if (random.applyAsInt(100) < difficulty.accuracy) {
                            if (objectives != null || game.type().id().equals("skywars") || game.type().id().equals("bedwars")) selectSword(bot);
                            bot.swingMainHand();
                            if (game.mode().combatStyle() != GameMode.CombatStyle.MODERN || bot.getAttackCooldown() >= 0.9f)
                                bot.attack(enemy);
                        }
                        slot.nextAttack = tick + difficulty.meleeTicks + random.applyAsInt(9);
                    }
                    if (distance > 2.3 * 2.3) goal = enemy.getLocation();
                }
            }
        }
        if (game.logic() instanceof uk.co.playerready.pulsegames.games.koth.KothGame koth) {
            var hill = koth.botHill();
            if (hill != null) goal = kothGoal(slot, game, bot, enemy, here, goal, hill);
        }
        if (goal == null && enemy == null) {
            slot.objective = objectives != null ? "Guarding the objective" : "Patrolling";
            goal = objectives != null ? objectives.botGuardPoint(bot) : patrolDestination(game, bot);
            if (goal == null) goal = game.arena().spawn((int) ((tick / 100 + bot.getEntityId()) % Math.max(1, game.arena().spawnCount())), game.world());
        }
        if (game.logic() instanceof uk.co.playerready.pulsegames.games.skywars.SkywarsGame skywars) {
            goal = skywars.botObjective(bot, goal, tick, difficulty);
            slot.objective = skywars.botStatus(bot);
            if (goal == null && enemy != null && here.distanceSquared(enemy.getLocation()) <= 2.8 * 2.8)
                slot.objective = "Fighting a visible opponent at melee range";
            else if (goal == null) { slot.state = "WAITING_FOR_SAFE_STEP"; slot.reason = slot.objective; }
        }
        if (game.logic() instanceof uk.co.playerready.pulsegames.games.bedwars.BedwarsGame bedwars) {
            goal = bedwars.botObjective(bot, goal, tick, difficulty);
            slot.objective = bedwars.botStatus(bot);
            if (goal == null && enemy != null && here.distanceSquared(enemy.getLocation()) <= 2.8 * 2.8)
                slot.objective = "Fighting a visible opponent at melee range";
            else if (goal == null) { slot.state = "WAITING_FOR_SAFE_STEP"; slot.reason = slot.objective; }
        }
        if (game.logic() instanceof uk.co.playerready.pulsegames.games.duels.DuelsGame duels
                && Set.of("crystal", "mace").contains(game.mode().rulesId())) {
            goal = duels.botObjective(bot, goal);
            slot.objective = duels.botStatus(bot);
        }
        navigate(slot, here, goal, difficulty);
    }


    private static boolean pvpCore(GameInstance game) {
        return switch (game.type().id()) { case "duels", "kitpvp", "koth" -> true; default -> false; };
    }

    @SuppressWarnings("deprecation")
    private static double healthFraction(Player bot) {
        double max = bot.getMaxHealth() <= 0 ? 20 : bot.getMaxHealth();
        return Math.min(1, bot.getHealth() / max);
    }

    /** Safe to stand on: solid, harmless ground at the bot's own level (so never a ledge, void or hazard). */
    private java.util.function.Predicate<Location> standable(Location level) {
        int y = level.getBlockY();
        return at -> { Location feet = at.clone(); feet.setY(y); return walkable.test(feet); };
    }

    /** The shared PvP core: strafe, kit, sprint, jump, retreat and sumo edge. Returns the movement goal. */
    private Location pvpCombat(Slot slot, GameInstance game, Player bot, Player enemy, Location here,
                               double distanceSquared, BotDifficulty difficulty, BotDifficulty.Profile profile) {
        BotMelee melee = slot.melee;
        double distance = Math.sqrt(distanceSquared);
        Location enemyAt = enemy.getLocation();
        var safe = standable(here);
        boolean sumo = game.type().id().equals("duels") && game.mode().rulesId().equals("sumo");
        boolean retreating = combat.retreat() && melee.retreating(healthFraction(bot), slot.kit.canHeal(bot), profile);
        if (combat.kitUse() && kitUse(slot, game, bot, enemy, here, distance, retreating, difficulty, profile, safe)) {
            slot.objective = slot.kit.drawing() ? "Shooting from range" : "Using the kit";
            return combat.strafe() ? melee.strafeGoal(here, enemyAt, profile, safe, true) : null;
        }
        slot.kit.stopDrawing();
        selectSwordIfNeeded(bot);
        boolean ready = bot.getAttackCooldown() >= 0.9f;
        boolean hurt = bot.getNoDamageTicks() > bot.getMaximumNoDamageTicks() / 2;
        if (!retreating && distance <= 4 && melee.jump(distance, bot.isOnGround(), ready, hurt, profile)) {
            bot.setJumping(true);
            game.runLater(2, () -> bot.setJumping(false));
            // A crit needs the hit on the way down, so hold the swing for the top of the jump.
            if (!hurt) slot.nextAttack = Math.max(slot.nextAttack, tick + 10);
        }
        if (!retreating && distance <= 2.8 && tick >= slot.nextAttack) {
            if (combat.sprint()) bot.setSprinting(melee.sprint(distance, true, profile));
            if (random.applyAsInt(100) < profile.hitChance()) {
                bot.swingMainHand();
                if (game.mode().combatStyle() != GameMode.CombatStyle.MODERN || bot.getAttackCooldown() >= 0.9f) {
                    bot.attack(enemy);
                    melee.afterHit();
                }
            } else {
                // A miss still swings, at a point beside the target, instead of freezing for a second.
                slot.actor.face(melee.missPoint(bot.getEyeLocation(), enemy.getEyeLocation()));
                bot.swingMainHand();
            }
            slot.nextAttack = tick + profile.meleeDelay() + random.applyAsInt(9);
        } else if (combat.sprint()) bot.setSprinting(melee.sprint(retreating ? 99 : distance, false, profile));
        if (retreating) {
            slot.objective = "Backing off to heal";
            Location away = melee.retreatGoal(here, enemyAt, safe);
            if (away != null) return away;
        }
        if (sumo) {
            Location edge = melee.sumoGoal(here, enemyAt, standable(here));
            if (edge != null) return edge;
        }
        if (distance > 3.5 || !combat.strafe()) return distance > 2.3 ? enemyAt : null;
        Location strafe = melee.strafeGoal(here, enemyAt, profile, safe, false);
        return strafe != null ? strafe : distance > 2.3 ? enemyAt : null;
    }

    /** Apples, bow, pearls. Returns true when the bot spent this think on one of them. */
    private boolean kitUse(Slot slot, GameInstance game, Player bot, Player enemy, Location here, double distance,
                           boolean retreating, BotDifficulty difficulty, BotDifficulty.Profile profile,
                           java.util.function.Predicate<Location> safe) {
        if (game.type().id().equals("kitpvp") && game.mode().rulesId().equals("oitc")) return false;
        boolean hurt = bot.getHealth() < 16;
        if (hurt && (retreating || distance > 5) && slot.kit.eat(bot, tick)) return true;
        if (difficulty != BotDifficulty.EASY && slot.kit.canPearl(bot) && bot.hasLineOfSight(enemy)) {
            boolean cornered = retreating && slot.melee.retreatGoal(here, enemy.getLocation(), safe) == null;
            if ((distance > 16 || cornered) && slot.kit.pearl(bot, enemy.getEyeLocation(), distance, tick)) return true;
        }
        if (!retreating && distance >= 8 && distance <= 24 && bot.hasLineOfSight(enemy) && slot.kit.canShoot(bot)) {
            Location aimed = slot.melee.aim(bot.getEyeLocation(), enemy.getEyeLocation(), profile, difficulty == BotDifficulty.EASY);
            return slot.kit.shoot(bot, aimed, distance, tick, profile.projectileDelay());
        }
        return false;
    }

    private static void selectSwordIfNeeded(Player bot) {
        var held = bot.getInventory().getItemInMainHand();
        if (held == null || !held.getType().name().endsWith("_SWORD")) selectSword(bot);
    }

    /** Hold the (current) hill from this bot's own spot. Hunters in team games go after rivals; holders only fight on or beside it. */
    private Location kothGoal(Slot slot, GameInstance game, Player bot, LivingEntity enemy, Location here, Location goal,
                              uk.co.playerready.pulsegames.core.util.Cuboid hill) {
        Location spot = BotMelee.spreadPoint(hill, game.world(), bot.getUniqueId());
        boolean hunter = game.mode().isTeams() && BotPersonality.aggression(bot.getUniqueId()) >= 0.5;
        if (enemy == null) { slot.objective = "Capturing the hill"; return spot; }
        double distance = here.distance(enemy.getLocation());
        boolean contested = hill.contains(enemy.getLocation());
        if (contested || distance <= (hunter ? 12 : 4)) return goal;
        slot.objective = "Capturing the hill";
        return spot;
    }

    /** Optional ordered map hints, only used when the controller has no immediate objective. */
    public Location patrolDestination(GameInstance game, Player bot) {
        Slot slot = slots.get(bot.getUniqueId());
        if (slot == null) return null;
        List<String> routes = game.arena().settings().getKeys(false).stream()
                .filter(k -> k.startsWith("bot-route-")).sorted().limit(32).toList();
        slot.route = routes.isEmpty() ? "bot-patrol-points" : routes.get(Math.floorMod(bot.getEntityId(), routes.size()));
        List<Location> points = routes.isEmpty() ? List.of() : BotNavigationHints.points(game,
                routes.get(Math.floorMod(bot.getEntityId(), routes.size())));
        if (points.size() < 2) { points = BotNavigationHints.points(game, "bot-patrol-points"); slot.route = "bot-patrol-points"; }
        if (points.isEmpty()) { slot.route = ""; return null; }
        int index = Math.floorMod(slot.routeIndex, points.size());
        if (bot.getLocation().distanceSquared(points.get(index)) < 1) index = (index + 1) % points.size();
        slot.routeIndex = index;
        return points.get(index).clone();
    }

    private void navigate(Slot slot, Location here, Location goal, BotDifficulty difficulty) {
        boolean skywars = slot.game.logic() instanceof uk.co.playerready.pulsegames.games.skywars.SkywarsGame
                || slot.game.logic() instanceof uk.co.playerready.pulsegames.games.bedwars.BedwarsGame;
        boolean precise = skywars || slot.game.logic() instanceof BotMovement;
        if (slot.recovery != null) {
            if (tick >= slot.recoverUntil || !here.getWorld().equals(slot.recovery.getWorld()) || here.distanceSquared(slot.recovery) < 1)
                slot.recovery = null;
            else if (goal != null) { goal = slot.recovery; slot.objective = "Walking to a recovery point"; slot.route = "bot-recovery-points"; }
            else slot.recovery = null;
        }
        if (goal != null && slot.target.isEmpty()) slot.target = String.format(Locale.ROOT, "%.1f, %.1f, %.1f", goal.getX(), goal.getY(), goal.getZ());
        if (goal != null && !here.getWorld().equals(goal.getWorld())) {
            slot.actor.stopNavigation();
            slot.destination = null;
            return;
        }
        if (goal == null || here.distanceSquared(goal) < (precise ? 0.04 : 1)) {
            slot.actor.stopNavigation();
            slot.destination = null;
        } else if (tick >= slot.nextPath && (slot.destination == null || slot.destination.distanceSquared(goal) > (precise ? 0.01 : 2) || tick % 100 == 0)) {
            if (skywars || slot.game.logic() instanceof BotMovement movement && movement.straightNavigation()) slot.actor.navigateStraight(goal);
            else slot.actor.navigate(goal);
            slot.destination = goal.clone();
            slot.nextPath = tick + (slot.game.logic() instanceof BotMovement movement
                    ? Math.max(5, movement.navigationIntervalTicks(difficulty)) : difficulty.pathTicks + random.applyAsInt(6));
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
        skillRounds.clear();
        if (skills != null) skills.save();
        if (provider != null) provider.close();
        provider = null;
    }
}
