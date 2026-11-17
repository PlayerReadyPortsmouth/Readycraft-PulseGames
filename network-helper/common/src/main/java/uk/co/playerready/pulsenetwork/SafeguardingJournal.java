package uk.co.playerready.pulsenetwork;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;

/** Private daily records. Retention applies equally to chat, flags and copied context. No names/IPs/ages. */
public final class SafeguardingJournal {
    public record Entry(String reference, long time, UUID player, String backend, String channel, String text,
                        SafeguardingFilter.Severity severity, List<String> reasons, String outcome) {}
    private final Path directory;
    private final Clock clock;
    private final int days;
    private LocalDate pruned;
    public SafeguardingJournal(Path directory, Clock clock, int days) throws IOException {
        this.directory = directory; this.clock = clock; this.days = days;
        if (Files.isSymbolicLink(directory)) throw new IOException("Journal directory must not be a symbolic link");
        Files.createDirectories(directory); restrict(directory, true); prune();
    }
    private static void restrict(Path path, boolean directory) throws IOException {
        try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------")); }
        catch (UnsupportedOperationException e) { throw new IOException("Safeguarding journal requires private POSIX permissions", e); }
    }
    public synchronized void record(Entry entry) throws IOException {
        prune();
        Path path = directory.resolve(LocalDate.now(clock) + ".tsv");
        if (!Files.exists(path)) { Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); }
        if (Files.isSymbolicLink(path)) throw new IOException("Journal must not be a symbolic link");
        restrict(path, false);
        String line = String.join("\t", entry.reference(), Long.toString(entry.time()), entry.player().toString(),
                clean(entry.backend()), clean(entry.channel()), entry.severity().name(), clean(String.join(",", entry.reasons())), clean(entry.outcome()), clean(entry.text())) + "\n";
        Files.writeString(path, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }
    private static String clean(String text) { return text.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n"); }
    public synchronized void prune() throws IOException {
        LocalDate today = LocalDate.now(clock); if (today.equals(pruned)) return;
        LocalDate oldest = today.minusDays(days - 1L);
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.matches("\\d{4}-\\d{2}-\\d{2}\\.tsv")) continue;
                try { if (LocalDate.parse(name.substring(0, 10)).isBefore(oldest)) Files.delete(file); }
                catch (DateTimeException ignored) { /* Not one of our files. */ }
            }
        }
        pruned = today;
    }
    /** Adjacent messages in the same backend/channel, across players. A staff-only caller must enforce permission. */
    public synchronized List<String> context(String reference) throws IOException {
        prune(); var before = new ArrayDeque<String>(); var result = new ArrayList<String>();
        // First locate only metadata. Never expose arbitrary file paths to callers.
        String scope = null;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(f -> f.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}\\.tsv")).sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Journal must not be a symbolic link");
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] fields = line.split("\t", 9);
                        if (fields.length == 9 && fields[0].equals(reference)) scope = fields[3] + "\t" + fields[4];
                    }
                }
            }
        }
        if (scope == null) return List.of();
        boolean found = false; int following = 0;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(f -> f.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}\\.tsv")).sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Journal must not be a symbolic link");
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] fields = line.split("\t", 9);
                        if (fields.length != 9 || !scope.equals(fields[3] + "\t" + fields[4])) continue;
                        if (!found && fields[0].equals(reference)) { result.addAll(before); result.add(line); found = true; }
                        else if (found) { result.add(line); if (++following == 5) return List.copyOf(result); }
                        else { if (before.size() == 5) before.removeFirst(); before.addLast(line); }
                    }
                }
            }
        }
        return List.copyOf(result);
    }
    public synchronized List<String> read(UUID player, boolean flagged, int maximum) throws IOException {
        prune(); var result = new ArrayDeque<String>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(f -> f.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}\\.tsv")).sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Journal must not be a symbolic link");
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] fields = line.split("\t", 9);
                        if (fields.length != 9 || player != null && !fields[2].equals(player.toString()) || flagged && fields[5].equals("NONE")) continue;
                        if (result.size() == maximum) result.removeFirst(); result.addLast(line);
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}
