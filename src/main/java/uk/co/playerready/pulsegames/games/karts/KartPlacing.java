package uk.co.playerready.pulsegames.games.karts;

import java.util.*;

/** Race order for item tiers and the HUD: checkpoint progress, then distance to the next gate. Pure. */
final class KartPlacing {
    record Entry(UUID id, int progress, double distanceToGate) {}
    private KartPlacing() {}

    static List<UUID> order(Collection<Entry> entries) {
        return entries.stream().sorted(Comparator.comparingInt(Entry::progress).reversed()
                .thenComparingDouble(e -> Double.isFinite(e.distanceToGate()) ? e.distanceToGate() : Double.MAX_VALUE)
                .thenComparing(Entry::id)).map(Entry::id).toList();
    }
    /** One-based place, or the field size when unknown. */
    static int place(UUID id, Collection<Entry> entries) {
        int index = order(entries).indexOf(id);
        return index < 0 ? Math.max(1, entries.size()) : index + 1;
    }
}
