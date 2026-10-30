package uk.co.playerready.pulsegames.core.admin;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.ui.forms.FormLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One admin screen described once and drawn twice: as a chest menu (Java) and as a button form (Bedrock).
 * The pure parts (button list, target decoding) are unit tested.
 */
public final class AdminScreen {
    private AdminScreen() { }

    /**
     * @param name   MiniMessage item name
     * @param lines  plain description lines, shown as lore and as the form button's second line
     * @param action null for an information-only tile
     */
    public record Entry(Material icon, String name, List<String> lines, Consumer<Player> action) {
        public static Entry info(Material icon, String name, String... lines) { return new Entry(icon, name, List.of(lines), null); }
        public static Entry button(Material icon, String name, Consumer<Player> action, String... lines) { return new Entry(icon, name, List.of(lines), action); }
    }

    public static final String ENTRY = "e:";

    /** Bedrock buttons: every entry in order (information tiles too, tapping them does nothing), then Back when there is one. */
    public static List<FormLayout.Button> buttons(List<Entry> entries, boolean hasBack) {
        List<FormLayout.Button> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            String label = uk.co.playerready.pulsegames.core.util.Text.plain(e.name());
            if (!e.lines().isEmpty()) label += "\n" + e.lines().getFirst();
            out.add(new FormLayout.Button(label, ENTRY + i));
        }
        if (hasBack) out.add(new FormLayout.Button("Back", FormLayout.BACK));
        return out;
    }

    /** Index for an entry target, or -1 for Back and anything else. */
    public static int indexOf(String target) {
        if (target == null || !target.startsWith(ENTRY)) return -1;
        try { return Integer.parseInt(target.substring(ENTRY.length())); } catch (NumberFormatException bad) { return -1; }
    }
}
