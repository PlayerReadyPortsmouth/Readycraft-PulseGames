package uk.co.playerready.pulsegames.core.game;

import java.util.Map;
import java.util.UUID;

/**
 * Rules for a win that arrives only because everyone else left. Without them two friends (or one child with
 * two accounts) could queue a 2-player game, quit at second one and collect a ranked win each cycle.
 */
public final class LeaverWin {
    private LeaverWin() {}

    /** End reasons the engine uses when the last players standing are decided by departures. */
    public static boolean byDefault(String reason) {
        return "last-standing".equals(reason) || "last-team".equals(reason);
    }

    /**
     * Kills on one victim beyond {@code sameVictimCap} are repeat kills and count for nothing: neither token
     * rewards nor the half-target test below.
     */
    public static int validKills(Map<UUID, Integer> killsPerVictim, int sameVictimCap) {
        int total = 0;
        for (int n : killsPerVictim.values()) total += Math.min(n, Math.max(1, sameVictimCap));
        return total;
    }

    /**
     * Does a win by departures still count as ranked? It must have lasted {@code minSeconds}, and in a
     * kill-target game the winner needs at least half the target in valid (non-repeat) kills.
     */
    public static boolean counts(int roundSeconds, int minSeconds, int killTarget, int validKills) {
        if (roundSeconds < minSeconds) return false;
        return killTarget <= 0 || validKills * 2 >= killTarget;
    }
}
