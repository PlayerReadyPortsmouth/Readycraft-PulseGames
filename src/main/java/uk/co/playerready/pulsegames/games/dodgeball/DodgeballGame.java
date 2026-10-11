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
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.game.MiniGame;

import java.util.*;

/** Three-hit elimination; every accepted throw is tracked and expires after five seconds. */
public final class DodgeballGame extends MiniGame {
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Map<UUID, Long> nextThrow = new HashMap<>();
    private final Map<UUID, Snowball> balls = new HashMap<>();

    private final LongSupplier clock;
    public DodgeballGame(GameInstance game) { this(game, System::nanoTime); }
    DodgeballGame(GameInstance game, LongSupplier clock) { super(game); this.clock = clock; }
    int throwCooldownTicks() {
        return Math.max(1, Math.min(40, game.arena().settings().getInt("throw-cooldown-ticks", 7)));
    }
    @Override public boolean environmentalDamage() { return false; }
    @Override public int timeLimitSeconds() { return 240; }
    @Override public void onStart() {
        for (Player player : game.alivePlayers()) {
            lives.put(player.getUniqueId(), 3);
            ItemStack launcher = new ItemStack(Material.BLAZE_ROD);
            launcher.editMeta(meta -> {
                meta.displayName(Component.text("Dodgeball launcher", NamedTextColor.GOLD));
                meta.lore(List.of(Component.text("Right-click to throw. Three hits and you are out.")));
            });
            player.getInventory().setItem(0, launcher);
            player.setCooldown(Material.BLAZE_ROD, 0);
        }
        game.broadcast("Right-click your blaze rod to throw! Three hits and you're out.");
    }
    /** Shared human/bot action. Aim with the player's look direction before calling. */
    public boolean throwBall(Player player) {
        if (game.state() != GameState.RUNNING || game.frozen() || !game.isActive(player)
                || player.getWorld() != game.world()) return false;
        long now = clock.getAsLong();
        Long deadline = nextThrow.get(player.getUniqueId());
        if (deadline != null && now - deadline < 0) return false;
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
            game.eliminate(victim);
            victim.showTitle(Title.title(Component.text("YOU'RE OUT!", NamedTextColor.RED),
                    Component.text("Hit by " + shooter.getName()),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(250))));
        }
    }
    @Override public void onTimeUp() { game.end(List.of(), "time-draw"); }
    @Override public void onEnd(List<Player> winners) { onCleanup(); }
    @Override public void onCleanup() {
        balls.values().forEach(Snowball::remove);
        game.everyone().forEach(player -> player.setCooldown(Material.BLAZE_ROD, 0));
        balls.clear(); nextThrow.clear(); lives.clear();
    }
    @Override public List<String> sidebar(Player player) {
        return List.of("<yellow>Lives: <white>" + lives.getOrDefault(player.getUniqueId(), 0),
                "<gray>Right-click: throw", "<gray>Last side standing wins");
    }
}
