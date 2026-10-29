package uk.co.playerready.pulsegames.core.ranked;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** When a season turns over and what a soft reset does. */
public final class SeasonRules {
    private SeasonRules() { }

    public enum Action {
        /** Nothing stored yet: start tracking the configured season. */
        ADOPT,
        /** The configured id changed: archive the old season, soft reset, start the new one. */
        ROLLOVER,
        /** The end date has passed: archive and soft reset, then pause ranked until the config moves on. */
        END,
        NONE
    }

    /** 1000 + (old - 1000) / 2, rounded to the nearest point. */
    public static int softReset(int rating) { return Elo.START + (int) Math.round((rating - Elo.START) / 2.0); }

    public static LocalDate parseEnds(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try { return LocalDate.parse(iso.trim()); } catch (DateTimeParseException bad) { return null; }
    }

    public static Action decide(String storedId, boolean storedClosed, String configId, LocalDate ends, LocalDate today) {
        if (storedId == null || storedId.isBlank()) return Action.ADOPT;
        if (!storedId.equals(configId)) return storedClosed ? Action.ADOPT : Action.ROLLOVER;
        if (!storedClosed && ends != null && today.isAfter(ends)) return Action.END;
        return Action.NONE;
    }
}
