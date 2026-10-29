package uk.co.playerready.pulsegames.core.progression;

import java.util.Locale;

/** Wording for the one-off action-bar line shown when a player joins. Pure, so it can be tested. */
final class JoinHint {
    private JoinHint() { }

    /** MiniMessage line, or null when there is nothing worth saying. {@code featured} null = no bonus game today. */
    static String line(String featured, double multiplier, boolean challengeWaiting, boolean firstWinWaiting) {
        String bonus = featured == null || multiplier <= 1 ? null
                : "Today: " + featured + " pays x" + trim(multiplier);
        if (bonus != null && (challengeWaiting || firstWinWaiting)) {
            return "<gold>" + bonus + ", plus a bonus waiting. Quick Play?";
        }
        if (bonus != null) return "<gold>" + bonus + ". Quick Play?";
        if (firstWinWaiting && challengeWaiting) return "<gold>Today: a first-win bonus and daily challenges are waiting. Quick Play?";
        if (firstWinWaiting) return "<gold>Today: your first win earns a bonus. Quick Play?";
        if (challengeWaiting) return "<gold>Today: daily challenges are waiting. Quick Play?";
        return null;
    }

    private static String trim(double x) {
        return x == Math.rint(x) ? String.valueOf((long) x) : String.format(Locale.ROOT, "%.1f", x);
    }
}
