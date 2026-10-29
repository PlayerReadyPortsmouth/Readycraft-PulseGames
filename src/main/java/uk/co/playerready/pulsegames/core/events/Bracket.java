package uk.co.playerready.pulsegames.core.events;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-elimination bracket with byes for any field of two or more, and an optional third-place match.
 * Entrants are opaque ids, best seed first; the top seeds get the byes. Pure, so it is unit tested.
 */
public final class Bracket {

    public static final class Match {
        private final int round;
        private final int index;
        private final boolean thirdPlace;
        private String a;
        private String b;
        private String winner;
        private String loser;

        Match(int round, int index, boolean thirdPlace) { this.round = round; this.index = index; this.thirdPlace = thirdPlace; }

        public int round() { return round; }
        public int index() { return index; }
        public boolean thirdPlace() { return thirdPlace; }
        public String a() { return a; }
        public String b() { return b; }
        public String winner() { return winner; }
        public String loser() { return loser; }
        public boolean bye() { return winner != null && (a == null || b == null); }
        public boolean decided() { return winner != null; }
        public boolean ready() { return winner == null && a != null && b != null; }
    }

    private final int size;
    private final int entrants;
    private final List<List<Match>> rounds = new ArrayList<>();
    private final Match third;

    private Bracket(int entrants, boolean thirdPlace) {
        this.entrants = entrants;
        int s = 1;
        while (s < entrants) s <<= 1;
        this.size = s;
        int count = Integer.numberOfTrailingZeros(s);
        for (int r = 0; r < count; r++) {
            List<Match> row = new ArrayList<>();
            for (int i = 0; i < s >> (r + 1); i++) row.add(new Match(r, i, false));
            rounds.add(row);
        }
        this.third = thirdPlace && count >= 2 ? new Match(count - 1, 1, true) : null;
        if (third != null) rounds.getLast().add(third);
    }

    /** Standard seeding: 1 meets the lowest seed, and the top two seeds can only meet in the final. */
    static List<Integer> seedOrder(int size) {
        List<Integer> order = new ArrayList<>(List.of(1));
        for (int s = 2; s <= size; s <<= 1) {
            List<Integer> next = new ArrayList<>();
            for (int seed : order) { next.add(seed); next.add(s + 1 - seed); }
            order = next;
        }
        return order;
    }

    public static Bracket of(List<String> seeded, boolean thirdPlace) {
        if (seeded.size() < 2) throw new IllegalArgumentException("A bracket needs at least two entrants");
        Bracket bracket = new Bracket(seeded.size(), thirdPlace);
        List<Integer> order = seedOrder(bracket.size);
        List<Match> first = bracket.rounds.getFirst();
        for (int i = 0; i < first.size(); i++) {
            int seedA = order.get(2 * i), seedB = order.get(2 * i + 1);
            first.get(i).a = seedA <= seeded.size() ? seeded.get(seedA - 1) : null;
            first.get(i).b = seedB <= seeded.size() ? seeded.get(seedB - 1) : null;
        }
        for (Match m : List.copyOf(first)) if (m.a == null || m.b == null) bracket.settle(m, m.a != null ? m.a : m.b);
        return bracket;
    }

    public int entrants() { return entrants; }
    public int size() { return size; }
    public List<List<Match>> rounds() { return rounds; }
    public boolean hasThirdPlace() { return third != null; }

    private void settle(Match m, String winner) {
        m.winner = winner;
        m.loser = winner.equals(m.a) ? m.b : m.a;
        if (m.thirdPlace) return;
        List<Match> row = rounds.get(m.round);
        if (m.round + 1 < rounds.size()) {
            Match next = rounds.get(m.round + 1).get(m.index / 2);
            if (m.index % 2 == 0) next.a = winner; else next.b = winner;
        }
        if (third != null && m.round == rounds.size() - 2 && m.loser != null) {
            if (m.index == 0) third.a = m.loser; else third.b = m.loser;
        }
    }

    /** Matches ready to play in the earliest round that still has any. */
    public List<Match> playable() {
        for (List<Match> row : rounds) {
            List<Match> ready = row.stream().filter(Match::ready).toList();
            if (!ready.isEmpty()) return ready;
        }
        return List.of();
    }

    public void report(Match match, String winner) {
        if (match.decided() || !match.ready() || !winner.equals(match.a) && !winner.equals(match.b))
            throw new IllegalArgumentException("Cannot report that result");
        settle(match, winner);
    }

    private Match finalMatch() { return rounds.getLast().getFirst(); }

    public boolean finished() { return finalMatch().decided() && (third == null || third.decided()); }

    public String champion() { return finalMatch().winner(); }

    /** Final places, best first; knocked-out entrants in the same round share a place (1, 2, 3, 3, 5, 5, 5, 5). */
    public Map<String, Integer> placings() {
        Map<String, Integer> places = new LinkedHashMap<>();
        if (finalMatch().decided()) {
            places.put(finalMatch().winner(), 1);
            places.put(finalMatch().loser(), 2);
        }
        if (third != null && third.decided()) {
            places.put(third.winner(), 3);
            places.put(third.loser(), 4);
        }
        for (int r = rounds.size() - 2; r >= 0; r--) {
            int place = (1 << (rounds.size() - r - 1)) + 1;
            for (Match m : rounds.get(r)) {
                if (m.thirdPlace() || m.loser() == null || places.containsKey(m.loser())) continue;
                places.put(m.loser(), place);
            }
        }
        return places;
    }
}
