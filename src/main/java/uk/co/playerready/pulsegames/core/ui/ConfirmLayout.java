package uk.co.playerready.pulsegames.core.ui;

import org.bukkit.Material;

import java.util.List;

/** Pure layout rules for {@link Confirm}: a 27-slot window with Yes left, the summary centre and No right. */
public final class ConfirmLayout {
    private ConfirmLayout() { }

    public static final int ROWS = 3;
    public static final int YES = 11;
    public static final int SUMMARY = 13;
    public static final int NO = 15;
    /** Lines beyond this are dropped so the tooltip always fits a phone screen. */
    public static final int MAX_SUMMARY = 7;

    /** Green for an ordinary question, orange when the choice cannot easily be undone. */
    public static Material yesMaterial(boolean danger) {
        return danger ? Material.ORANGE_CONCRETE : Material.LIME_CONCRETE;
    }

    /** At most {@link #MAX_SUMMARY} lines, with null and blank lines removed. */
    public static List<String> summary(List<String> lines) {
        return lines.stream().filter(l -> l != null && !l.isBlank()).limit(MAX_SUMMARY).toList();
    }
}
