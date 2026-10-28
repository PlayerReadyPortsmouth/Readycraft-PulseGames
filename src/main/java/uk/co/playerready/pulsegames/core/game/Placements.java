package uk.co.playerready.pulsegames.core.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Pure placement maths, kept free of Bukkit so it can be unit tested. */
final class Placements {

    private Placements() {}

    /**
     * Competition ranking (1, 1, 3): recorded finishers in finish order, then declared
     * winners (shared), then everyone still alive (shared), then the eliminated and
     * departed from last-out to first-out, then anyone unaccounted for (shared).
     *
     * @param outOrder players in the order they were eliminated or left (first out first)
     * @return insertion-ordered map, best place first
     */
    static Map<UUID, Integer> compute(Collection<UUID> field, List<UUID> finishers, Collection<UUID> winners,
                                      Collection<UUID> alive, List<UUID> outOrder) {
        Map<UUID, Integer> place = new LinkedHashMap<>();
        Set<UUID> pool = new LinkedHashSet<>(field);
        int next = 1;
        for (UUID id : finishers) if (pool.remove(id)) place.put(id, next++);
        next = tier(place, pool, winners, next);
        next = tier(place, pool, alive, next);
        for (int i = outOrder.size() - 1; i >= 0; i--) {
            UUID id = outOrder.get(i);
            if (pool.remove(id)) place.put(id, next++);
        }
        tier(place, pool, new ArrayList<>(pool), next);
        return place;
    }

    private static int tier(Map<UUID, Integer> place, Set<UUID> pool, Collection<UUID> members, int next) {
        List<UUID> hit = members.stream().filter(pool::contains).distinct().toList();
        for (UUID id : hit) { pool.remove(id); place.put(id, next); }
        return next + hit.size();
    }

    static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) return n + "th";
        return n + switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
    }

    /** One line per medal place (1st-3rd), ties joined: "1st: Ana, Bo". */
    static List<String> podium(Map<UUID, Integer> placements, Function<UUID, String> name) {
        List<String> lines = new ArrayList<>();
        for (int p = 1; p <= 3; p++) {
            final int wanted = p;
            List<String> names = placements.entrySet().stream().filter(e -> e.getValue() == wanted)
                    .map(e -> name.apply(e.getKey())).toList();
            if (names.isEmpty()) continue;
            String joined = names.size() <= 4 ? String.join(", ", names)
                    : String.join(", ", names.subList(0, 4)) + " +" + (names.size() - 4);
            lines.add(ordinal(p) + ": " + joined);
        }
        return List.copyOf(lines);
    }

    /** Non-punishing outcome label for the results screen. */
    static String outcome(boolean participant, boolean winner, boolean anyWinner, boolean cooperative,
                          int place, int field) {
        if (!participant) return "Spectated";
        if (winner) return "Victory";
        if (cooperative || place <= 0) return "Round ended";
        if (!anyWinner && place == 1) return "Draw (joint 1st)";
        return "Finished " + ordinal(place) + " of " + field;
    }

    /** Placement token bonus for a non-winner; zero when the field is too small to mean anything. */
    static int bonus(int place, boolean anyWinner, int field, int minField, int second, int third) {
        if (field < minField) return 0;
        if (place == 1) return anyWinner ? 0 : second;
        return place == 2 ? second : place == 3 ? third : 0;
    }
}
