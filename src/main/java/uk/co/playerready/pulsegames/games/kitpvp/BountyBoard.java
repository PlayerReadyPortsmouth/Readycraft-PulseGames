package uk.co.playerready.pulsegames.games.kitpvp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The match leader carries a bounty; a limited number can be claimed per player so kills cannot be farmed. */
public final class BountyBoard {
    public static final int MIN_KILLS = 3;
    public static final int REWARD_TOKENS = 5;
    public static final int MAX_CLAIMS = 3;

    private final Map<UUID, Integer> claims = new HashMap<>();

    /** The strict leader once they have {@link #MIN_KILLS}; a tie at the top has no bounty. */
    public static Optional<UUID> leader(List<Map.Entry<UUID, Integer>> topScoresDescending) {
        if (topScoresDescending.isEmpty()) return Optional.empty();
        var first = topScoresDescending.get(0);
        if (first.getValue() < MIN_KILLS) return Optional.empty();
        if (topScoresDescending.size() > 1 && topScoresDescending.get(1).getValue().equals(first.getValue())) {
            return Optional.empty();
        }
        return Optional.of(first.getKey());
    }

    /** True if this claim pays out (and counts against the cap). */
    public boolean claim(UUID killer) {
        if (claims.getOrDefault(killer, 0) >= MAX_CLAIMS) return false;
        claims.merge(killer, 1, Integer::sum);
        return true;
    }

    public void clear() { claims.clear(); }
}
