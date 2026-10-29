package uk.co.playerready.pulsegames.core.stats;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure formatting and ordering for the stats profile menu. */
public final class StatsFormat {
    private StatsFormat() { }

    public record Row(String gameId, String name, int played, int wins) { }

    /** Whole-number win rate such as "50%"; "0%" when nothing has been played, never above 100%. */
    public static String winRate(int played, int wins) {
        if (played <= 0 || wins <= 0) return "0%";
        return Math.min(100, Math.round(100f * wins / played)) + "%";
    }

    /** Only games the player has actually played, most played first, then by name. */
    public static List<Row> playedGames(List<Row> rows) {
        return rows.stream().filter(r -> r.played() > 0)
                .sorted(Comparator.comparingInt(Row::played).reversed()
                        .thenComparing(r -> r.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** "1 win" / "3 wins". */
    public static String wins(int wins) { return wins + (wins == 1 ? " win" : " wins"); }

    /** "1 game" / "3 games". */
    public static String games(int played) { return played + (played == 1 ? " game" : " games"); }
}
