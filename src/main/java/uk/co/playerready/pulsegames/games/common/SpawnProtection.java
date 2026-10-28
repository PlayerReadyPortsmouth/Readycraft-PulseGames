package uk.co.playerready.pulsegames.games.common;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Short invulnerability after (re)spawning. Clock is injectable for tests. */
public final class SpawnProtection {
    private final LongSupplier nanoClock;
    private final Map<UUID, Long> until = new HashMap<>();

    public SpawnProtection() { this(System::nanoTime); }
    public SpawnProtection(LongSupplier nanoClock) { this.nanoClock = nanoClock; }

    public void protect(UUID player, double seconds) {
        until.put(player, nanoClock.getAsLong() + (long) (seconds * 1_000_000_000L));
    }

    public boolean isProtected(UUID player) {
        Long deadline = until.get(player);
        return deadline != null && nanoClock.getAsLong() < deadline;
    }

    public void clear(UUID player) { until.remove(player); }
    public void clear() { until.clear(); }
}
