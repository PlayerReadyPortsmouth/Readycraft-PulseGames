package uk.co.playerready.pulsegames.core.ranked;

import java.util.List;
import java.util.Locale;

/** Plain wording for the ranked screens, shared by the chest menu and the Bedrock form. Pure. */
public final class RankedText {
    private RankedText() { }

    public static String kitName(String kit) {
        return switch (kit) {
            case "classic" -> "Regular PvP";
            case "sumo" -> "Sumo";
            case "crystal" -> "Crystal";
            case "mace" -> "Mace";
            case "op" -> "OP";
            default -> kit.isEmpty() ? kit : kit.substring(0, 1).toUpperCase(Locale.ROOT) + kit.substring(1);
        };
    }

    /** "Gold, 1342" once placed; before that the rating stays hidden. */
    public static String standing(Divisions divisions, RankedStore.Rating r) {
        if (!Elo.placed(r.games()))
            return "Unranked (" + r.games() + " of " + Elo.PLACEMENT_GAMES + " placement games)";
        return divisions.of(r.rating()).name() + ", " + r.rating();
    }

    public static String nextStep(Divisions divisions, RankedStore.Rating r) {
        if (!Elo.placed(r.games())) return (Elo.PLACEMENT_GAMES - r.games()) + " more games to get a rank";
        var next = divisions.next(r.rating());
        return next == null ? "Top division!" : divisions.distanceToNext(r.rating()) + " points to " + next.name();
    }

    public static String signed(int delta) { return (delta >= 0 ? "+" : "") + delta; }

    /** One history entry is stored as {@code kit|W/L/D|delta|opponent|epochSecond}. */
    public static String historyRecord(String kit, double score, int delta, String opponent, long epochSecond) {
        String result = score > 0.75 ? "W" : score < 0.25 ? "L" : "D";
        return kit + "|" + result + "|" + delta + "|" + opponent.replace('|', ' ') + "|" + epochSecond;
    }

    public static String historyLine(String record) {
        String[] f = record.split("\\|", 5);
        if (f.length < 4) return "Match";
        String word = switch (f[1]) { case "W" -> "Won"; case "L" -> "Lost"; default -> "Drew"; };
        String change;
        try { change = " (" + signed(Integer.parseInt(f[2])) + ")"; } catch (NumberFormatException bad) { change = ""; }
        return word + " against " + f[3] + change + " in " + kitName(f[0]);
    }

    public static List<String> boardLines(List<RankedStore.Row> rows, Divisions divisions) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            out.add((i + 1) + ". " + row.name() + " - " + divisions.of(row.rating()).name() + ", " + row.rating());
        }
        return out;
    }

    public static String searching(int range) { return "Searching... (range ±" + range + ")"; }
}
