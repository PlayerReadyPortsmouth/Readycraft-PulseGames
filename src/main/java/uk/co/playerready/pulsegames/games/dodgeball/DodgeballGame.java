package uk.co.playerready.pulsegames.games.dodgeball;

import org.bukkit.Material;
import org.bukkit.Particle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.Comeback;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.game.MiniGame;

import java.util.*;
import java.util.function.Function;
import uk.co.playerready.pulsegames.games.common.HazardFeedback;
import uk.co.playerready.pulsegames.games.common.RoundController;
import uk.co.playerready.pulsegames.games.common.RoundTracker;

/** Three-hit elimination; every accepted throw is tracked and expires after five seconds. */
public final class DodgeballGame extends MiniGame {
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Map<UUID, Long> nextThrow = new HashMap<>();
    private final Map<UUID, Snowball> balls = new HashMap<>();

    private static final int SUDDEN_DEATH_SECONDS = 45;
    private boolean suddenDeath;
    private RoundController rounds;

    /**
     * Players on the side(s) with the most lives between them: each player is their own side in free-for-all,
     * a team is one side otherwise. Everyone on a trailing side is out when the clock runs down.
     */
    static Set<UUID> leadingSides(Map<UUID, Integer> lives, Collection<UUID> alive, Function<UUID, Object> sideOf) {
        Map<Object, Integer> perSide = new HashMap<>();
        for (UUID id : alive) perSide.merge(sideOf.apply(id), lives.getOrDefault(id, 3), Integer::sum);
        int best = perSide.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        Set<UUID> leaders = new LinkedHashSet<>();
        for (UUID id : alive) if (perSide.get(sideOf.apply(id)) == best) leaders.add(id);
        return leaders;
    }

    /** Round ranking on the clock: more lives first, then by who lasted longest among the rest. */
    static List<List<UUID>> livesTiers(Map<UUID, Integer> lives, List<List<UUID>> eliminationTiers, Set<UUID> remaining) {
        List<List<UUID>> tiers = new ArrayList<>();
        Map<Integer, List<UUID>> byLives = new TreeMap<>(Comparator.reverseOrder());
        for (UUID id : remaining) byLives.computeIfAbsent(lives.getOrDefault(id, 3), k -> new ArrayList<>()).add(id);
        tiers.addAll(byLives.values());
        for (List<UUID> tier : eliminationTiers) if (!remaining.containsAll(tier)) tiers.add(tier);
        return tiers;
    }

    @Override public List<Player> mercyWinners() { return rounds == null ? null : rounds.mercyWinners(); }

    /** Players owed one extra throw (ignoring the cooldown once) because their side was smaller at the last reset. */
    private final Set<UUID> bonusThrows = new HashSet<>();

    /** Comeback: at a round reset the side with fewer players gets one extra ball each. */
    private void grantBonusBalls(List<Player> contestants) {
        bonusThrows.clear();
        if (!Comeback.enabled(game.plugin().getConfig(), "dodgeball")) return;
        Map<Object, Integer> sizes = new HashMap<>();
        for (Player p : contestants) {
            var team = game.teamOf(p);
            if (team != null) sizes.merge(team, 1, Integer::sum);
        }
        int biggest = sizes.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (Player p : contestants) {
            var team = game.teamOf(p);
            if (team != null && Comeback.smallerSide(sizes.get(team), biggest)) {
                bonusThrows.add(p.getUniqueId());
                p.sendActionBar(Component.text("Comeback: you have an extra ball this round", NamedTextColor.GREEN));
            }
        }
    }

    @Override public String comebackLine(Player player) {
        return bonusThrows.contains(player.getUniqueId()) ? "Comeback: extra ball" : null;
    }

    private boolean isRounds() { return RoundController.isRounds(game); }

    private void out(Player player) {
        if (rounds != null) rounds.knockOut(player); else game.eliminate(player);
    }

    private void resetLives() {
        for (Player player : rounds.contestants()) {
            lives.put(player.getUniqueId(), 3);
            equip(player);
        }
        grantBonusBalls(rounds.contestants());
        balls.values().forEach(Snowball::remove);
        balls.clear();
    }

    private void equip(Player player) {
        ItemStack launcher = new ItemStack(Material.BLAZE_ROD);
        launcher.editMeta(meta -> {
            meta.displayName(Component.text("Dodgeball launcher", NamedTextColor.GOLD));
            meta.lore(List.of(Component.text("Use it to throw. Three hits and you are out.")));
        });
        player.getInventory().setItem(0, launcher);
        player.setCooldown(Material.BLAZE_ROD, 0);
    }

    private final LongSupplier clock;
    public DodgeballGame(GameInstance game) { this(game, System::nanoTime); }
    DodgeballGame(GameInstance game, LongSupplier clock) { super(game); this.clock = clock; }
    int throwCooldownTicks() {
        return Math.max(1, Math.min(40, game.arena().settings().getInt("throw-cooldown-ticks", 7)));
    }
    @Override public boolean environmentalDamage() { return false; }
    @Override public int timeLimitSeconds() {
        if (isRounds()) return RoundController.matchSeconds(game, 90);
        return 240 + (suddenDeath ? SUDDEN_DEATH_SECONDS : 0);
    }
    @Override public void onStart() {
        if (isRounds()) {
            rounds = new RoundController(game, new RoundController.Host() {
                @Override public void beginRound(int round) { resetLives(); }
                @Override public List<List<UUID>> timeUpTiers(RoundTracker tracker) {
                    return livesTiers(lives, tracker.eliminationTiers(), tracker.remaining());
                }
                @Override public void resetWorld() { balls.values().forEach(Snowball::remove); balls.clear(); }
            }, null, 90, "Most lives left scores most");
            rounds.start();
            return;
        }
        for (Player player : game.alivePlayers()) {
            lives.put(player.getUniqueId(), 3);
            equip(player);
        }
        game.broadcast("Use your blaze rod to throw! Three hits and you're out.");
    }
    /** Shared human/bot action. Aim with the player's look direction before calling. */
    public boolean throwBall(Player player) {
        if (game.state() != GameState.RUNNING || game.frozen() || !game.isActive(player)
                || player.getWorld() != game.world()) return false;
        long now = clock.getAsLong();
        Long deadline = nextThrow.get(player.getUniqueId());
        if (deadline != null && now - deadline < 0 && !bonusThrows.remove(player.getUniqueId())) return false;
        int ticks = throwCooldownTicks();
        nextThrow.put(player.getUniqueId(), now + ticks * 50_000_000L);
        player.setCooldown(Material.BLAZE_ROD, ticks);
        player.playSound(player.getLocation(), "minecraft:entity.snowball.throw", 0.6f, 1.2f);
        Snowball ball = player.launchProjectile(Snowball.class,
                player.getEyeLocation().getDirection().multiply(1.7));
        balls.put(ball.getUniqueId(), ball);
        game.runLater(100, () -> { balls.remove(ball.getUniqueId()); ball.remove(); });
        return true;
    }
    @Override public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && event.getAction().isRightClick()
                && event.getItem() != null && event.getItem().getType() == Material.BLAZE_ROD) {
            event.setCancelled(true);
            throwBall(event.getPlayer());
        }
    }
    @Override public void onProjectileHit(ProjectileHitEvent event) {
        if (balls.remove(event.getEntity().getUniqueId()) == null) return;
        event.setCancelled(true);
        event.getEntity().remove();
        if (game.state() != GameState.RUNNING || game.frozen()
                || !(event.getEntity().getShooter() instanceof Player shooter)
                || !(event.getHitEntity() instanceof Player victim)
                || !game.isActive(shooter) || !game.isActive(victim) || shooter.equals(victim)
                || victim.getWorld() != game.world()) return;
        if (game.teamOf(shooter) != null && game.teamOf(shooter) == game.teamOf(victim)) return;
        int remaining = lives.compute(victim.getUniqueId(), (id, count) -> Math.max(0, (count == null ? 3 : count) - 1));
        game.addScore(shooter, 1);
        shooter.sendActionBar(Component.text((remaining == 0 ? "OUT! " : "HIT! ") + victim.getName()
                + " · " + remaining + "/3 lives", NamedTextColor.GREEN));
        shooter.playSound(shooter.getLocation(), "minecraft:entity.arrow.hit_player", 0.9f, remaining == 0 ? 1.5f : 1.1f);
        victim.sendActionBar(Component.text(remaining == 0 ? "YOU'RE OUT! Hit by " + shooter.getName()
                : "HIT by " + shooter.getName() + " · " + remaining + "/3 lives left", NamedTextColor.RED));
        victim.playSound(victim.getLocation(), "minecraft:entity.player.hurt", 0.8f, 1f);
        victim.playHurtAnimation(shooter.getLocation().getYaw());
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.03);
        if (remaining == 0) {
            out(victim);
            victim.showTitle(Title.title(Component.text("YOU'RE OUT!", NamedTextColor.RED),
                    Component.text("Hit by " + shooter.getName()),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(250))));
        }
    }
    /**
     * Time: the side with the most lives wins on lives; if sides are level, the tied sides play a sudden-death
     * overtime on one life each, and a still-level overtime is shared between the finalists.
     */
    @Override public void onTimeUp() {
        if (rounds != null) { rounds.onTimeUp(); return; }
        List<Player> alive = game.alivePlayers();
        Map<UUID, Player> byId = new LinkedHashMap<>();
        alive.forEach(p -> byId.put(p.getUniqueId(), p));
        if (byId.isEmpty()) { game.end(List.of(), "time-draw"); return; }
        if (suddenDeath) { game.end(alive, "time-draw"); return; }
        Set<UUID> leaders = leadingSides(lives, byId.keySet(), id -> {
            var team = game.teamOf(byId.get(id));
            return team != null ? team : id;
        });
        for (Player p : List.copyOf(alive)) {
            if (!leaders.contains(p.getUniqueId())) {
                p.sendActionBar(Component.text("Time! Fewer lives than the leaders", NamedTextColor.RED));
                game.eliminate(p);
            }
        }
        if (game.state() != GameState.RUNNING) return;
        suddenDeath = true;
        for (Player p : game.alivePlayers()) {
            lives.put(p.getUniqueId(), 1);
            p.showTitle(Title.title(Component.text("SUDDEN DEATH", NamedTextColor.RED),
                    Component.text("One life each - next hit wins"),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(250))));
        }
        game.broadcast("<red><b>SUDDEN DEATH!</b> <gray>Level on lives - one hit decides it.");
    }
    @Override public void onEnd(List<Player> winners) { onCleanup(); }
    @Override public void onQuit(Player player) { if (rounds != null) rounds.playerLeft(player); }
    @Override public MiniGame.Cue eliminationSound() { return HazardFeedback.LOUD_ELIMINATION; }
    @Override public void onEliminated(Player player) {
        HazardFeedback.finalThree(game.players(), game.alivePlayers().size(), game.players().size());
    }
    @Override public void onCleanup() {
        balls.values().forEach(Snowball::remove);
        game.everyone().forEach(player -> player.setCooldown(Material.BLAZE_ROD, 0));
        balls.clear(); nextThrow.clear(); lives.clear(); bonusThrows.clear();
        if (rounds != null) rounds.close();
    }
    @Override public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
        if (rounds != null) lines.addAll(rounds.sidebar(player));
        lines.add("<yellow>Your lives: <white>" + lives.getOrDefault(player.getUniqueId(), 0));
        if (suddenDeath) lines.add("<red><b>SUDDEN DEATH");
        // Every side's lives, best first, so you can see who to hunt. Capped to leave the sidebar room.
        List<Map.Entry<UUID, Integer>> others = new ArrayList<>(lives.entrySet());
        others.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        int shown = 0;
        for (var other : others) {
            if (shown++ == 5) { lines.add("<gray>+" + (others.size() - 5) + " more"); break; }
            Player p = game.player(other.getKey());
            lines.add((other.getValue() == 0 ? "<dark_gray>" : "<white>") + (p == null ? "?" : p.getName())
                    + " <red>" + "♥".repeat(other.getValue()));
        }
        return lines;
    }
}
