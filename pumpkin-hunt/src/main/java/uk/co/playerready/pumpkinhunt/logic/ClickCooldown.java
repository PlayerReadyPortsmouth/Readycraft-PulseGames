package uk.co.playerready.pumpkinhunt.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One click per player per lantern per cooldown window, to stop click spam. Main thread only. */
public final class ClickCooldown {
    private final Map<String, Long> last = new HashMap<>();

    /** True (and starts the window) when the click may count; false while the cooldown is running. */
    public boolean tryAcquire(UUID player, String lanternId, long nowMs, long cooldownMs) {
        String k = player + "|" + lanternId;
        Long prev = last.get(k);
        if (prev != null && nowMs - prev < cooldownMs) return false;
        last.put(k, nowMs);
        return true;
    }

    public void forget(UUID player) { last.keySet().removeIf(k -> k.startsWith(player.toString())); }
}
