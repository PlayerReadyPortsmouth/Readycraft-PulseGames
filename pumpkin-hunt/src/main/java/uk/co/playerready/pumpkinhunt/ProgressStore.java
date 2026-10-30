package uk.co.playerready.pumpkinhunt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Writes a JSON document off the main thread, debounced, and writes anything pending on {@link #flush()}. */
public final class ProgressStore {
    private final Path file;
    private final Logger log;
    private final Supplier<String> json;
    private final long debounceMs;
    private final ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PumpkinHunt-save");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pending;

    public ProgressStore(Path file, Logger log, long debounceMs, Supplier<String> json) {
        this.file = file;
        this.log = log;
        this.debounceMs = debounceMs;
        this.json = json;
    }

    public String read() throws IOException {
        return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    /** Marks the document changed; one write follows after the debounce, however many saves arrive. */
    public synchronized void save() {
        if (pending != null && !pending.isDone()) return;
        pending = pool.schedule(this::write, debounceMs, TimeUnit.MILLISECONDS);
    }

    /** Cancels any scheduled write, writes now if one was pending, and stops the writer thread. */
    public synchronized void flush() {
        boolean dirty = pending != null && !pending.isDone();
        if (dirty) pending.cancel(false);
        pool.shutdown();
        if (dirty) write();
    }

    private void write() {
        try {
            String text = json.get();
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            log.log(Level.SEVERE, "[PumpkinHunt] Could not write " + file.getFileName(), e);
        }
    }
}
