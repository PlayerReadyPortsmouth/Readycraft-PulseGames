package uk.co.playerready.pulsegames.core.game;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Pure ordering rules for the spectator "Watch a player" menu. */
final class WatchList {
    private WatchList() { }

    /** One player still in the round. {@code close} = party member or teammate of the viewer. */
    record Candidate(UUID id, String name, boolean close, int score, double health, double maxHealth) { }

    /** Party members and teammates first, then highest score, then name. Nobody is dropped except by the cap. */
    static List<Candidate> order(List<Candidate> alive, int cap) {
        return alive.stream()
                .sorted(Comparator.comparing((Candidate c) -> !c.close())
                        .thenComparing(Comparator.comparingInt(Candidate::score).reversed())
                        .thenComparing(c -> c.name().toLowerCase(java.util.Locale.ROOT)))
                .limit(cap).toList();
    }

    /** The player with the highest score, or null when nobody has scored (then there is no leader to follow). */
    static Candidate leader(List<Candidate> alive) {
        Candidate best = null;
        for (Candidate c : alive) if (c.score() > 0 && (best == null || c.score() > best.score())) best = c;
        return best;
    }

    /** Lore line: score when the game keeps one, otherwise hearts. */
    static String stats(Candidate c) {
        StringBuilder sb = new StringBuilder();
        if (c.score() > 0) sb.append("Score: ").append(c.score());
        if (c.maxHealth() > 0) {
            if (sb.length() > 0) sb.append("  ");
            sb.append("Hearts: ").append((int) Math.ceil(c.health() / 2)).append('/').append((int) Math.ceil(c.maxHealth() / 2));
        }
        return sb.toString();
    }
}
