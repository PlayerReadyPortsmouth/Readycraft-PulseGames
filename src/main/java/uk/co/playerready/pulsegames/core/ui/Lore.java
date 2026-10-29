package uk.co.playerready.pulsegames.core.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

/** Fluent, non-italic lore builder. Descriptions wrap at a width that stays readable on a phone through Geyser. */
public final class Lore {
    /** Characters per line; wide tooltips run off the edge of a Bedrock phone screen. */
    public static final int WIDTH = 30;

    private final List<Component> lines = new ArrayList<>();

    private Lore() { }

    public static Lore of() { return new Lore(); }

    /** Plain-text description, wrapped and grey. Never parsed as MiniMessage. */
    public Lore desc(String text) {
        for (String line : wrap(text, WIDTH))
            lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        return this;
    }

    /** One MiniMessage line, already short. */
    public Lore line(String miniMessage) { lines.add(Theme.mm(miniMessage)); return this; }

    public Lore blank() { lines.add(Component.empty().decoration(TextDecoration.ITALIC, false)); return this; }

    public Lore kv(String label, String value) { return line(Theme.TEXT + label + ": <white>" + value); }

    public Lore status(String miniMessage) { return line(miniMessage); }

    /** The one click prompt; every clickable button ends with it, unclickable ones never have it. */
    public Lore hint(String text) { return line(Theme.GOOD + text); }

    public List<Component> build() { return List.copyOf(lines); }

    /** Greedy word wrap. A word longer than the width keeps its own line. */
    public static List<String> wrap(String text, int width) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                result.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) result.add(line.toString());
        return result;
    }
}
