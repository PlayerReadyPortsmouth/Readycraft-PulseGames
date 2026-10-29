package uk.co.playerready.pulsegames.games.bedwars;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Per-player, per-item cooldowns on an injectable clock (milliseconds). */
final class Cooldowns {
    private final LongSupplier clock;
    private final Map<String, Long> readyAt = new HashMap<>();

    Cooldowns(LongSupplier clock) { this.clock = clock; }

    private static String key(UUID player, String item) { return player + "/" + item; }

    /** Milliseconds left, 0 when ready. */
    long remaining(UUID player, String item) {
        return Math.max(0, readyAt.getOrDefault(key(player, item), 0L) - clock.getAsLong());
    }

    boolean ready(UUID player, String item) { return remaining(player, item) == 0; }

    /** Starts the cooldown. Zero seconds means none. */
    void start(UUID player, String item, int seconds) {
        if (seconds > 0) readyAt.put(key(player, item), clock.getAsLong() + seconds * 1000L);
    }

    void clear() { readyAt.clear(); }
}
