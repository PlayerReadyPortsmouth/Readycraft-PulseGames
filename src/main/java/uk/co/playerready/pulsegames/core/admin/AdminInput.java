package uk.co.playerready.pulsegames.core.admin;

import uk.co.playerready.pulsegames.core.progression.ProgressionAdmin;

import java.util.Locale;
import java.util.Set;

/** Validation for typed admin input. Reuses {@link ProgressionAdmin#parse} so the menu and the command agree. */
public final class AdminInput {
    private AdminInput() { }

    /** Error text for a level, or null when it is a valid 1-100 whole number. */
    public static String levelError(String text) {
        return ProgressionAdmin.parse(new String[]{"level", "x", "set", text}).error();
    }

    /** Error text for an XP amount, or null when it is valid. */
    public static String xpError(String text) {
        return ProgressionAdmin.parse(new String[]{"xp", "x", "add", text}).error();
    }

    private static final Set<String> DIFFICULTIES = Set.of("adaptive", "easy", "normal", "hard");

    public static boolean difficulty(String value) { return DIFFICULTIES.contains(value.toLowerCase(Locale.ROOT)); }

    /** A plausible Minecraft name (Java 3-16 letters, digits and underscores; Floodgate adds a leading dot). */
    public static String nameError(String text) {
        return text.matches("\\.?[A-Za-z0-9_ ]{3,24}") ? null : "That does not look like a player name.";
    }
}
