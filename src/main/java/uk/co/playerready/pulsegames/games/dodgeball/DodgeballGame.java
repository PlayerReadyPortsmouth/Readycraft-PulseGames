package uk.co.playerready.pulsegames.games.dodgeball;

import org.bukkit.Material;
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

    public DodgeballGame(GameInstance game) { super(game); }
    @Override public int maxTeams() { return 2; }
    @Override public boolean environmentalDamage() { return false; }
    @Override public int timeLimitSeconds() { return 240; }
    @Override public void onStart() {
        for (Player player : game.alivePlayers()) {
            lives.put(player.getUniqueId(), 3);
            player.getInventory().setItem(0, new ItemStack(Material.BLAZE_ROD));
        }
        game.broadcast("Right-click your blaze rod to throw! Three hits and you're out.");
    }
    /** Shared human/bot action. Aim with the player's look direction before calling. */
    public boolean throwBall(Player player) {
        if (game.state() != GameState.RUNNING || game.frozen() || !game.isActive(player)
                || player.getWorld() != game.world()) return false;
        long now = System.nanoTime();
        Long deadline = nextThrow.get(player.getUniqueId());
        if (deadline != null && now - deadline < 0) return false;
        nextThrow.put(player.getUniqueId(), now + 750_000_000L);
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
        if (remaining == 0) game.eliminate(victim);
    }
    @Override public void onTimeUp() { game.end(List.of(), "time-draw"); }
    @Override public void onEnd(List<Player> winners) { onCleanup(); }
    @Override public void onCleanup() {
        balls.values().forEach(Snowball::remove);
        balls.clear(); nextThrow.clear(); lives.clear();
    }
    @Override public List<String> sidebar(Player player) {
        return List.of("<yellow>Lives: <white>" + lives.getOrDefault(player.getUniqueId(), 0),
                "<gray>Right-click: throw", "<gray>Last side standing wins");
    }
}
