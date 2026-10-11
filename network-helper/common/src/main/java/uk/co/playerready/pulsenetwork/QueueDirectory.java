package uk.co.playerready.pulsenetwork;

import java.time.Clock;
import java.util.*;

/** Proxy-owned identifiers prevent a chat click from choosing an arbitrary backend. */
public final class QueueDirectory {
    public record Queue(String token, String server, String instance, String title, int players, int maximum, long expires) {}
    private final Map<String, Queue> entries = new LinkedHashMap<>();
    private final Clock clock;
    public QueueDirectory() { this(Clock.systemUTC()); }
    public QueueDirectory(Clock clock) { this.clock = clock; }
    public synchronized Queue update(String server, String instance, String title, int players, int maximum) {
        prune();
        if (!Wire.id(server) || !Wire.id(instance) || maximum < 1 || maximum > 64 || players < 1 || players > maximum)
            throw new IllegalArgumentException("Invalid queue");
        String key = server + "/" + instance;
        Queue old = entries.get(key);
        if (old == null && entries.size() >= 256) throw new IllegalArgumentException("Queue directory full");
        Queue next = new Queue(old == null ? UUID.randomUUID().toString() : old.token(), server, instance,
                Wire.plain(title), players, maximum, clock.millis() + 20_000);
        entries.put(key, next); return next;
    }
    public synchronized void remove(String server, String instance) { entries.remove(server + "/" + instance); }
    public synchronized List<Queue> list() { prune(); return List.copyOf(entries.values()); }
    public synchronized Optional<Queue> find(String token) { return list().stream().filter(q -> q.token().equals(token)).findFirst(); }
    private void prune() { entries.values().removeIf(q -> q.expires() <= clock.millis()); }
}
