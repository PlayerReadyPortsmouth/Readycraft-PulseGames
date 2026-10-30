package uk.co.playerready.pumpkinhunt.logic;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.OptionalLong;

/**
 * The hunt's open season. {@code start} and {@code end} are optional ISO values, either a date
 * ({@code 2026-10-24}) or a date and time ({@code 2026-10-24T09:00}), read in {@code zone}. A bare end date
 * includes that whole day. A blank or unreadable value means "no limit" on that side.
 */
public final class SeasonWindow {
    public enum State { OPEN, NOT_STARTED, OVER, DISABLED }

    private final boolean enabled;
    private final OptionalLong startMs;
    private final OptionalLong endMs;

    public SeasonWindow(boolean enabled, String start, String end, ZoneId zone) {
        this.enabled = enabled;
        this.startMs = parse(start, zone, false);
        this.endMs = parse(end, zone, true);
    }

    public State state(long nowMs) {
        if (!enabled) return State.DISABLED;
        if (startMs.isPresent() && nowMs < startMs.getAsLong()) return State.NOT_STARTED;
        if (endMs.isPresent() && nowMs >= endMs.getAsLong()) return State.OVER;
        return State.OPEN;
    }

    public boolean isOpen(long nowMs) { return state(nowMs) == State.OPEN; }

    /** True when a non-blank value could not be read (so the caller can warn). */
    public static boolean unreadable(String value, ZoneId zone) {
        return value != null && !value.isBlank() && parse(value, zone, false).isEmpty();
    }

    private static OptionalLong parse(String value, ZoneId zone, boolean endOfDay) {
        if (value == null || value.isBlank()) return OptionalLong.empty();
        String v = value.trim();
        try {
            if (v.length() <= 10) {
                LocalDate d = LocalDate.parse(v);
                return OptionalLong.of((endOfDay ? d.plusDays(1) : d).atStartOfDay(zone).toInstant().toEpochMilli());
            }
            return OptionalLong.of(LocalDateTime.parse(v).atZone(zone).toInstant().toEpochMilli());
        } catch (DateTimeParseException bad) {
            return OptionalLong.empty();
        }
    }
}
