package uk.co.playerready.pulsegames.core.lobby;

import java.util.ArrayList;
import java.util.List;

/** MiniMessage lines for the hologram boards and the NPC label. */
public final class LobbyBoardText {
    private LobbyBoardText() { }

    public record Line(String name, String value) { }

    public static String title(String kind, String gameName, String stat) {
        String window = kind.equals("alltime") ? "All-time" : "This week";
        String what = stat.equals("tokens") ? "tokens" : stat.equals("kills") ? "kills" : "wins";
        return "<gold><b>" + window + "</b> <yellow>" + what + (gameName == null ? "" : " <gray>· <aqua>" + clean(gameName));
    }

    /** Title, then the rows ranked 1 to {@code slots}; an empty board says so kindly. */
    public static List<String> lines(String title, List<Line> rows, int slots) {
        List<String> out = new ArrayList<>();
        out.add(title);
        if (rows.isEmpty()) out.add("<gray>Nobody yet. Could it be you?");
        for (int i = 0; i < rows.size() && i < slots; i++) {
            String colour = i == 0 ? "<gold>" : i == 1 ? "<white>" : i == 2 ? "<#cd7f32>" : "<gray>";
            out.add(colour + (i + 1) + ". <white>" + clean(rows.get(i).name()) + " <gray>- <yellow>" + rows.get(i).value());
        }
        return out;
    }

    public static String npcName(String gameName) { return "<gold><b>" + clean(gameName); }

    public static String npcCounts(int waiting, int playing) {
        return "<gray>" + waiting + " in queue · " + playing + " playing";
    }

    private static String clean(String s) { return s.replace("<", "").replace(">", ""); }
}
