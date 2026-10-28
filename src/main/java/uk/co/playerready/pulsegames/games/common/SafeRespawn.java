package uk.co.playerready.pulsegames.games.common;

import org.bukkit.Location;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.game.GameInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Safe respawn choice from a fixed anchor list (the arena spawns): random among the third of
 * anchors that are farthest from the nearest enemy, so nobody spawns on top of a fight.
 * Same idea as the Quake spawn picker, without needing a spawn-area region.
 */
public final class SafeRespawn {
    private SafeRespawn() {}

    /** Index into {@code anchors}, or -1 if there are none. */
    public static int pick(List<Location> anchors, List<Location> enemies, RandomGenerator random) {
        if (anchors.isEmpty()) return -1;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) order.add(i);
        order.sort(Comparator.comparingDouble((Integer i) -> nearest(anchors.get(i), enemies)).reversed());
        int pool = Math.max(1, (int) Math.ceil(anchors.size() / 3.0));
        return order.get(random.nextInt(pool));
    }

    /** Squared distance to the nearest enemy; infinite when nobody is around. */
    static double nearest(Location point, List<Location> enemies) {
        double best = Double.POSITIVE_INFINITY;
        for (Location enemy : enemies) {
            double dx = point.getX() - enemy.getX(), dy = point.getY() - enemy.getY(), dz = point.getZ() - enemy.getZ();
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return best;
    }

    /** Safe arena spawn for a respawning player: far from every enemy still in the game. */
    public static Location forPlayer(GameInstance game, Player player, RandomGenerator random) {
        List<Location> anchors = new ArrayList<>();
        for (int i = 0; i < game.arena().spawnCount(); i++) anchors.add(game.arena().spawn(i, game.world()));
        List<Location> enemies = game.alivePlayers().stream()
                .filter(other -> !other.equals(player) && game.isActive(other) && other.getWorld().equals(game.world())
                        && (game.teamOf(player) == null || game.teamOf(player) != game.teamOf(other)))
                .map(Player::getLocation).toList();
        int index = pick(anchors, enemies, random);
        return index < 0 ? game.spawnFor(player) : anchors.get(index);
    }
}
