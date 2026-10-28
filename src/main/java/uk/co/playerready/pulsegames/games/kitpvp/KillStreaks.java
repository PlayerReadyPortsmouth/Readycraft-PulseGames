package uk.co.playerready.pulsegames.games.kitpvp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Consecutive kills without dying, and what each milestone pays. */
public final class KillStreaks {
    public enum Reward { NONE, GOLDEN_APPLE, SPEED }

    private final Map<UUID, Integer> streaks = new HashMap<>();

    public int kill(UUID killer) { return streaks.merge(killer, 1, Integer::sum); }
    public int streak(UUID player) { return streaks.getOrDefault(player, 0); }
    public void died(UUID player) { streaks.remove(player); }
    public void clear() { streaks.clear(); }

    public static Reward reward(int streak) {
        return switch (streak) {
            case 3 -> Reward.GOLDEN_APPLE;
            case 5 -> Reward.SPEED;
            default -> Reward.NONE;
        };
    }
}
