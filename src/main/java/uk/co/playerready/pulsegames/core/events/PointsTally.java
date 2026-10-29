package uk.co.playerready.pulsegames.core.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Points format: each round gives placement points. Order is points, then round wins, then the better
 * total of places, then the better last round; players equal on all four share a rank.
 */
public final class PointsTally {

    public record Standing(String id, int points, int wins, int placeTotal, int lastPlace, int rank) { }

    private static final class Row {
        int points, wins, placeTotal, lastPlace = Integer.MAX_VALUE;
    }

    private final List<Integer> table;
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private int rounds;

    /** @param table points for 1st, 2nd, ...; anyone placed beyond the table gets the last value. */
    public PointsTally(List<Integer> table, List<String> entrants) {
        if (table.isEmpty()) throw new IllegalArgumentException("Points table is empty");
        this.table = List.copyOf(table);
        entrants.forEach(id -> rows.put(id, new Row()));
    }

    public int pointsForPlace(int place) {
        return place < 1 ? 0 : table.get(Math.min(place, table.size()) - 1);
    }

    public int rounds() { return rounds; }

    /** Places for this round (ties share a place). Entrants missing from the map score nothing but count the round. */
    public void addRound(Map<String, Integer> places) {
        rounds++;
        int absent = rows.size() + 1;
        rows.forEach((id, row) -> {
            int place = places.getOrDefault(id, 0);
            row.points += pointsForPlace(place);
            if (place == 1) row.wins++;
            row.placeTotal += place < 1 ? absent : place;
            row.lastPlace = place < 1 ? absent : place;
        });
    }

    public List<Standing> standings() {
        record Keyed(String id, Row row) { }
        List<Keyed> sorted = new ArrayList<>();
        rows.forEach((id, row) -> sorted.add(new Keyed(id, row)));
        sorted.sort(Comparator.<Keyed>comparingInt(k -> -k.row().points)
                .thenComparingInt(k -> -k.row().wins)
                .thenComparingInt(k -> k.row().placeTotal)
                .thenComparingInt(k -> k.row().lastPlace));
        List<Standing> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Row r = sorted.get(i).row();
            int rank = i + 1;
            if (i > 0) {
                Standing prev = out.get(i - 1);
                if (prev.points() == r.points && prev.wins() == r.wins && prev.placeTotal() == r.placeTotal
                        && prev.lastPlace() == r.lastPlace) rank = prev.rank();
            }
            out.add(new Standing(sorted.get(i).id(), r.points, r.wins, r.placeTotal, r.lastPlace, rank));
        }
        return out;
    }
}
