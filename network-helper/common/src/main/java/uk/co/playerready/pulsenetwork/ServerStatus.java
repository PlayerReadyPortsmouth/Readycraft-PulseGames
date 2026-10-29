package uk.co.playerready.pulsenetwork;

import java.util.*;

/**
 * Bounded backend status carried in a signed STATUS message: TPS (1m), MSPT (5s average),
 * online count, and flags only for players that have one (AFK, hidden, in a match).
 * Fields: tps, mspt, online, then up to {@link #MAX_CHUNKS} chunks of {@code hex32+flag} entries.
 */
public record ServerStatus(double tps, double mspt, int online, Map<UUID, Integer> flags) {
    public static final int AFK = 1, HIDDEN = 2, MATCH = 4;
    public static final int MAX_CHUNKS = 6, CHUNK_CHARS = 980, MAX_ENTRIES = MAX_CHUNKS * 28;

    public String[] toFields() {
        // Hidden players first so a truncated report never reveals a vanished player.
        var entries = flags.entrySet().stream().filter(e -> e.getValue() > 0 && e.getValue() <= 7)
                .sorted(Comparator.comparing((Map.Entry<UUID, Integer> e) -> (e.getValue() & HIDDEN) == 0).thenComparing(Map.Entry::getKey))
                .limit(MAX_ENTRIES).map(e -> e.getKey().toString().replace("-", "") + e.getValue()).toList();
        var fields = new ArrayList<String>(List.of(String.format(Locale.ROOT, "%.2f", Math.max(0, Math.min(tps, 25))),
                String.format(Locale.ROOT, "%.1f", Math.max(0, Math.min(mspt, 100_000))), Integer.toString(Math.max(0, Math.min(online, 10_000)))));
        var chunk = new StringBuilder();
        for (String entry : entries) {
            if (chunk.length() + entry.length() + 1 > CHUNK_CHARS) { fields.add(chunk.toString()); chunk.setLength(0); }
            if (chunk.length() > 0) chunk.append(',');
            chunk.append(entry);
        }
        if (chunk.length() > 0) fields.add(chunk.toString());
        return fields.toArray(String[]::new);
    }

    /** Strict parse: any malformed value rejects the whole report. */
    public static Optional<ServerStatus> parse(List<String> fields) {
        if (fields.size() < 3 || fields.size() > 3 + MAX_CHUNKS) return Optional.empty();
        try {
            double tps = Double.parseDouble(fields.get(0)), mspt = Double.parseDouble(fields.get(1));
            int online = Integer.parseInt(fields.get(2));
            if (!(tps >= 0 && tps <= 25) || !(mspt >= 0 && mspt <= 100_000) || online < 0 || online > 10_000) return Optional.empty();
            var flags = new HashMap<UUID, Integer>();
            for (String chunk : fields.subList(3, fields.size())) {
                if (chunk.isEmpty() || chunk.length() > CHUNK_CHARS) return Optional.empty();
                for (String entry : chunk.split(",", -1)) {
                    if (entry.length() != 33 || !entry.matches("[0-9a-f]{32}[1-7]")) return Optional.empty();
                    UUID id = UUID.fromString(entry.substring(0, 8) + "-" + entry.substring(8, 12) + "-" + entry.substring(12, 16)
                            + "-" + entry.substring(16, 20) + "-" + entry.substring(20, 32));
                    if (flags.put(id, entry.charAt(32) - '0') != null || flags.size() > MAX_ENTRIES) return Optional.empty();
                }
            }
            return Optional.of(new ServerStatus(tps, mspt, online, Map.copyOf(flags)));
        } catch (IllegalArgumentException e) { return Optional.empty(); }
    }
}
