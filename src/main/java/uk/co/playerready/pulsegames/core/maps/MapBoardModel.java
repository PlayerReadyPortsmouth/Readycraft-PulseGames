package uk.co.playerready.pulsegames.core.maps;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure view model for the map readiness board: status per check, summaries, age text and ordering. */
public final class MapBoardModel {
    private MapBoardModel() { }

    /** Shape + word per state so the board never relies on colour alone. */
    public enum Status {
        PASS("✔", "Pass"), FAIL("✘", "Fail"), PENDING("○", "Pending"),
        STALE("↻", "Stale"), MISSING("–", "Missing");
        public final String symbol, word;
        Status(String symbol, String word) { this.symbol = symbol; this.word = word; }
        /** Counts as evidence that still stands for the current build. */
        public boolean evidenced() { return this == PASS || this == FAIL || this == PENDING; }
    }

    public record Cell(Status status, String age, String note) { }

    public record Row(String game, String map, Map<MapReviewStore.Check, Cell> cells) {
        public Row { cells = Map.copyOf(cells); }
        public boolean fullyChecked() {
            return cells.size() == MapReviewStore.Check.values().length
                    && cells.values().stream().allMatch(c -> c.status() == Status.PASS);
        }
        /** Any check without current evidence (never recorded, or stale after a revision/config change). */
        public boolean missingEvidence() {
            return cells.values().stream().anyMatch(c -> c.status() == Status.MISSING || c.status() == Status.STALE);
        }
    }

    public static Status status(MapReviewStore.Mark mark, String revision, String fingerprint) {
        if (mark == null) return Status.MISSING;
        if (!mark.current(revision, fingerprint)) return Status.STALE;
        return switch (mark.outcome()) {
            case PASS -> Status.PASS;
            case FAIL -> Status.FAIL;
            case PENDING -> Status.PENDING;
        };
    }

    public static Row row(String game, String map, MapReviewStore.Entry entry, String fingerprint, Instant now) {
        Map<MapReviewStore.Check, Cell> cells = new EnumMap<>(MapReviewStore.Check.class);
        String revision = entry == null ? "" : entry.revision();
        for (var check : MapReviewStore.Check.values()) {
            var mark = entry == null ? null : entry.marks().get(check);
            cells.put(check, new Cell(status(mark, revision, fingerprint),
                    mark == null ? "never" : age(mark.at(), now), mark == null ? "" : mark.note()));
        }
        return new Row(game, map, cells);
    }

    /** "3 of 5 maps fully checked"; "No maps registered" when empty. */
    public static String summary(List<Row> rows) {
        if (rows.isEmpty()) return "No maps registered";
        long done = rows.stream().filter(Row::fullyChecked).count();
        return done + " of " + rows.size() + (rows.size() == 1 ? " map" : " maps") + " fully checked";
    }

    /** Short age such as "just now", "5 min ago", "3 h ago", "12 d ago"; "unknown" for a bad or future timestamp. */
    public static String age(String at, Instant now) {
        if (at == null || at.isBlank()) return "unknown";
        Instant then;
        try { then = Instant.parse(at); } catch (DateTimeParseException bad) { return "unknown"; }
        Duration d = Duration.between(then, now);
        if (d.isNegative()) return "unknown";
        if (d.toMinutes() < 1) return "just now";
        if (d.toHours() < 1) return d.toMinutes() + " min ago";
        if (d.toDays() < 1) return d.toHours() + " h ago";
        return d.toDays() + " d ago";
    }

    /** Maps needing evidence first (most unevidenced checks first), then by map id. */
    public static List<Row> order(List<Row> rows) {
        return rows.stream().sorted(Comparator
                .comparing(Row::fullyChecked)
                .thenComparing(Comparator.comparingLong(MapBoardModel::gaps).reversed())
                .thenComparing(Row::map, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    private static long gaps(Row row) {
        return row.cells().values().stream().filter(c -> !c.status().evidenced() || c.status() == Status.FAIL).count();
    }

    /** The exact command the prompt shows; the admin replaces the placeholder with their evidence. */
    public static String markCommand(String game, String map, MapReviewStore.Check check) {
        return "/pulse maps mark " + Objects.requireNonNull(game) + " " + map + " "
                + check.name().toLowerCase(java.util.Locale.ROOT) + " pass <evidence note>";
    }
}
