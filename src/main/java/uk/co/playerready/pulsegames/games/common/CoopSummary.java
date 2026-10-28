package uk.co.playerready.pulsegames.games.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Co-op end-of-match summary: positive framing, never "game over", plus an MVP pick. */
public final class CoopSummary {
    public record Entry(String name, int kills, int revives, int points) {}

    private CoopSummary() {}

    /** MVP = most points, then revives, then kills, then name (stable). Null for an empty squad. */
    public static Entry mvp(List<Entry> entries) {
        return entries.stream()
                .max(Comparator.comparingInt(Entry::points).thenComparingInt(Entry::revives)
                        .thenComparingInt(Entry::kills).thenComparing(Entry::name, Comparator.reverseOrder()))
                .orElse(null);
    }

    public static String title(boolean cleared) {
        return cleared ? "<gold><b>Squad victory!" : "<gold><b>Well played!";
    }

    public static String subtitle(int round, boolean newBest) {
        return "<gray>Round " + round + " reached" + (newBest ? " <yellow>- new map best!" : "");
    }

    /** MiniMessage lines: one headline, one per player, then the MVP. */
    public static List<String> lines(int round, int bestRound, List<Entry> entries) {
        List<String> lines = new ArrayList<>();
        lines.add("<gold>Round reached: <white>" + round + (bestRound > 0 ? " <gray>(map best " + bestRound + ")" : ""));
        for (Entry e : entries)
            lines.add("<yellow>" + e.name() + " <gray>- kills <white>" + e.kills() + "<gray>, revives <white>"
                    + e.revives() + "<gray>, points <white>" + e.points());
        Entry mvp = mvp(entries);
        if (mvp != null) lines.add("<light_purple>MVP: <white>" + mvp.name());
        return lines;
    }
}
