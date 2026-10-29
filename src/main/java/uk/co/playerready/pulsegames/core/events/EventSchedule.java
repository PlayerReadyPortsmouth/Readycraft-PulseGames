package uk.co.playerready.pulsegames.core.events;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure schedule maths for scheduled events (no Bukkit). A schedule is a list of entries, each either
 * {@code at: "HH:mm"} (every day, London wall clock) or {@code every: 60m} (counted from local midnight,
 * so hourly events stay on the hour across a clock change). An entry without a {@code game} takes the
 * next game from the rotation list.
 */
public final class EventSchedule {

    public enum Format { BRACKET, POINTS }

    /** What to run: game, mode (null = the game's default), format, rounds (points) and token multiplier. */
    public record Pick(String game, String mode, Format format, int rounds, double multiplier) { }

    /** One schedule line; {@code fixed} is null when the game comes from the rotation. */
    public record Entry(LocalTime at, Duration every, Pick fixed) { }

    /** A dated event. {@code choices} lists the pick first, then the rest of the rotation as fallbacks. */
    public record Occurrence(Instant start, List<Pick> choices) {
        public Pick pick() { return choices.getFirst(); }
    }

    public static final Duration MIN_EVERY = Duration.ofMinutes(15);
    private static final int HORIZON_DAYS = 3;

    private final ZoneId zone;
    private final List<Entry> entries;
    private final List<Pick> rotation;

    public EventSchedule(ZoneId zone, List<Entry> entries, List<Pick> rotation) {
        this.zone = zone;
        this.entries = List.copyOf(entries);
        this.rotation = List.copyOf(rotation);
    }

    public ZoneId zone() { return zone; }

    public boolean isEmpty() { return entries.isEmpty() || entries.stream().allMatch(e -> e.fixed() == null && rotation.isEmpty()); }

    /** "90m", "2h", "60" (minutes). Null when unusable or under {@link #MIN_EVERY}. */
    public static Duration parseEvery(String text) {
        if (text == null) return null;
        String t = text.trim().toLowerCase(Locale.ROOT);
        try {
            Duration d;
            if (t.endsWith("h")) d = Duration.ofMinutes(Long.parseLong(t.substring(0, t.length() - 1).trim()) * 60);
            else if (t.endsWith("m")) d = Duration.ofMinutes(Long.parseLong(t.substring(0, t.length() - 1).trim()));
            else d = Duration.ofMinutes(Long.parseLong(t));
            return d.compareTo(MIN_EVERY) < 0 || d.compareTo(Duration.ofHours(24)) > 0 ? null : d;
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    public static LocalTime parseAt(String text) {
        try {
            return text == null ? null : LocalTime.parse(text.trim().length() == 4 ? "0" + text.trim() : text.trim());
        } catch (DateTimeParseException bad) {
            return null;
        }
    }

    /** Builds a pick from a config map; null when the format is not recognised. */
    public static Pick parsePick(Map<?, ?> raw, double defaultMultiplier) {
        Object game = raw.get("game");
        if (game == null) return null;
        Object mode = raw.get("mode");
        String format = String.valueOf(raw.get("format") == null ? "points" : raw.get("format")).toLowerCase(Locale.ROOT);
        Format f = format.equals("bracket") ? Format.BRACKET : format.equals("points") ? Format.POINTS : null;
        if (f == null) return null;
        int rounds = number(raw.get("rounds"), 3);
        double multiplier = raw.get("tokens-multiplier") instanceof Number n ? n.doubleValue() : defaultMultiplier;
        return new Pick(String.valueOf(game).toLowerCase(Locale.ROOT), mode == null ? null : String.valueOf(mode),
                f, Math.max(1, Math.min(5, rounds)), Math.max(1.0, Math.min(5.0, multiplier)));
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    /** The next {@code count} events at or after {@code from}, soonest first; clashes keep the earlier line. */
    public List<Occurrence> next(Instant from, int count) {
        List<Occurrence> found = new ArrayList<>();
        LocalDate today = from.atZone(zone).toLocalDate();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            for (int day = -1; day <= HORIZON_DAYS; day++) {
                LocalDate date = today.plusDays(day);
                for (Instant start : startsOn(entry, date)) {
                    if (start.isBefore(from)) continue;
                    List<Pick> choices = choicesFor(entry, i, start);
                    if (!choices.isEmpty()) found.add(new Occurrence(start, choices));
                }
            }
        }
        found.sort(Comparator.comparing(Occurrence::start));
        List<Occurrence> out = new ArrayList<>();
        for (Occurrence o : found) {
            if (!out.isEmpty() && out.getLast().start().equals(o.start())) continue;
            out.add(o);
            if (out.size() >= count) break;
        }
        return out;
    }

    /** Start instants of one entry on one local date. A time inside a spring gap moves forward; an autumn overlap uses the first. */
    List<Instant> startsOn(Entry entry, LocalDate date) {
        List<Instant> starts = new ArrayList<>();
        if (entry.at() != null) {
            starts.add(ZonedDateTime.of(date, entry.at(), zone).toInstant());
        } else {
            Instant dayStart = date.atStartOfDay(zone).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
            for (Instant t = dayStart; t.isBefore(dayEnd); t = t.plus(entry.every())) starts.add(t);
        }
        return starts;
    }

    private List<Pick> choicesFor(Entry entry, int entryIndex, Instant start) {
        if (entry.fixed() != null) return List.of(entry.fixed());
        if (rotation.isEmpty()) return List.of();
        long step = entry.every() != null ? entry.every().getSeconds() : 86_400L;
        int first = (int) Math.floorMod(Math.floorDiv(start.getEpochSecond(), step) + entryIndex, (long) rotation.size());
        List<Pick> ordered = new ArrayList<>();
        for (int k = 0; k < rotation.size(); k++) ordered.add(rotation.get((first + k) % rotation.size()));
        return ordered;
    }
}
