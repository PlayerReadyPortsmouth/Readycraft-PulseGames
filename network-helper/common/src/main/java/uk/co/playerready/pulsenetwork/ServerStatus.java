package uk.co.playerready.pulsenetwork;

import java.util.*;

/**
 * Bounded backend status carried in a signed STATUS message: TPS (1m), MSPT (5s average),
 * online count, and flags only for players that have one (AFK, hidden, in a match).
 * Fields: tps, mspt, online, then up to {@link #MAX_CHUNKS} chunks of {@code hex32+flag} entries.
 */
public record ServerStatus(double tps, double mspt, int online, Map<UUID, Integer> flags, Map<UUID, Integer> levels) {
    public ServerStatus(double tps, double mspt, int online, Map<UUID, Integer> flags) { this(tps, mspt, online, flags, Map.of()); }
    /** Level chunks follow the flag chunks, each starting with {@code L}: {@code hex32:level} entries. Missing levels simply show no badge. */
    public static final int MAX_LEVEL_CHUNKS = 2, LEVEL_CHUNK_CHARS = 700, MAX_LEVEL = 9999;
    private static final java.util.regex.Pattern LEVEL_ENTRY = java.util.regex.Pattern.compile("[0-9a-f]{32}:[0-9]{1,4}");
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
        chunk.setLength(0);
        int levelChunks = 0;
        for (var e : new TreeMap<>(levels).entrySet()) {
            if (e.getValue() < 1 || e.getValue() > MAX_LEVEL) continue;
            String entry = e.getKey().toString().replace("-", "") + ":" + e.getValue();
            if (chunk.length() + entry.length() + 2 > LEVEL_CHUNK_CHARS) {
                fields.add("L" + chunk); chunk.setLength(0);
                if (++levelChunks == MAX_LEVEL_CHUNKS) break;
            }
            if (chunk.length() > 0) chunk.append(',');
            chunk.append(entry);
        }
        if (chunk.length() > 0 && levelChunks < MAX_LEVEL_CHUNKS) fields.add("L" + chunk);
        return fields.toArray(String[]::new);
    }

    /** Strict parse: any malformed value rejects the whole report. */
    public static Optional<ServerStatus> parse(List<String> fields) {
        if (fields.size() < 3 || fields.size() > 3 + MAX_CHUNKS + MAX_LEVEL_CHUNKS) return Optional.empty();
        try {
            double tps = Double.parseDouble(fields.get(0)), mspt = Double.parseDouble(fields.get(1));
            int online = Integer.parseInt(fields.get(2));
            if (!(tps >= 0 && tps <= 25) || !(mspt >= 0 && mspt <= 100_000) || online < 0 || online > 10_000) return Optional.empty();
            var flags = new HashMap<UUID, Integer>();
            var levels = new HashMap<UUID, Integer>();
            int levelChunks = 0;
            for (String chunk : fields.subList(3, fields.size())) {
                if (chunk.startsWith("L")) {
                    if (chunk.length() < 2 || chunk.length() > LEVEL_CHUNK_CHARS + 1 || ++levelChunks > MAX_LEVEL_CHUNKS) return Optional.empty();
                    for (String entry : chunk.substring(1).split(",", -1)) {
                        if (!LEVEL_ENTRY.matcher(entry).matches()) return Optional.empty();
                        int level = Integer.parseInt(entry.substring(33));
                        if (level < 1 || levels.put(uuid(entry.substring(0, 32)), level) != null) return Optional.empty();
                    }
                    continue;
                }
                if (levelChunks > 0) return Optional.empty(); // flag chunks come first
                if (chunk.isEmpty() || chunk.length() > CHUNK_CHARS) return Optional.empty();
                for (String entry : chunk.split(",", -1)) {
                    if (entry.length() != 33 || !entry.matches("[0-9a-f]{32}[1-7]")) return Optional.empty();
                    UUID id = uuid(entry.substring(0, 32));
                    if (flags.put(id, entry.charAt(32) - '0') != null || flags.size() > MAX_ENTRIES) return Optional.empty();
                }
            }
            return Optional.of(new ServerStatus(tps, mspt, online, Map.copyOf(flags), Map.copyOf(levels)));
        } catch (IllegalArgumentException e) { return Optional.empty(); }
    }
    private static UUID uuid(String h) {
        return UUID.fromString(h.substring(0, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16) + "-" + h.substring(16, 20) + "-" + h.substring(20, 32));
    }
}
