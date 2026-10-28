package uk.co.playerready.pulsegames.core.lobby;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Pure Quick Play and featured-game selection rules (no Bukkit, so they are unit-testable). */
public final class QuickPlay {
    private QuickPlay() { }

    /** An open (waiting or counting down) queue that a player could join right now. */
    public record Open(String gameId, String instanceId, int humans) { }

    /** Busiest open queue; ties go to the featured game, then a random one. Null when none. */
    public static Open pickQueue(List<Open> opens, String featuredId, Random random) {
        if (opens.isEmpty()) return null;
        int best = opens.stream().mapToInt(Open::humans).max().orElse(0);
        List<Open> top = opens.stream().filter(o -> o.humans() == best).toList();
        List<Open> featured = top.stream().filter(o -> o.gameId().equals(featuredId)).toList();
        List<Open> pool = featured.isEmpty() ? top : featured;
        return pool.get(random.nextInt(pool.size()));
    }

    /** No queue is open: the featured game if it can be played, else any eligible game. */
    public static String pickGame(List<String> eligible, String featuredId, Random random) {
        if (eligible.isEmpty()) return null;
        if (featuredId != null && eligible.contains(featuredId)) return featuredId;
        return eligible.get(random.nextInt(eligible.size()));
    }

    /**
     * Daily featured game: the configured override when it is in the pool, otherwise a
     * rotation through the sorted pool keyed on the date, so every server agrees all day.
     */
    public static String featured(LocalDate day, List<String> pool, String override) {
        if (pool.isEmpty()) return null;
        if (override != null && !override.isBlank() && pool.contains(override.trim())) return override.trim();
        List<String> sorted = new ArrayList<>(pool);
        sorted.sort(Comparator.naturalOrder());
        return sorted.get((int) Math.floorMod(day.toEpochDay(), (long) sorted.size()));
    }
}
