package uk.co.playerready.pulsegames.games.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Who recently damaged whom, for assist credit. Times are caller-supplied milliseconds. */
public final class AssistTracker {
    private final long windowMillis;
    private final Map<UUID, Map<UUID, Long>> hits = new HashMap<>();

    public AssistTracker(long windowMillis) { this.windowMillis = windowMillis; }

    public void hit(UUID victim, UUID damager, long now) {
        if (!victim.equals(damager)) hits.computeIfAbsent(victim, k -> new HashMap<>()).put(damager, now);
    }

    /** Recent damagers other than the killer; forgets the victim's history. */
    public List<UUID> assisters(UUID victim, UUID killer, long now) {
        Map<UUID, Long> recent = hits.remove(victim);
        List<UUID> result = new ArrayList<>();
        if (recent != null) recent.forEach((id, at) -> {
            if (!id.equals(killer) && now - at <= windowMillis) result.add(id);
        });
        return result;
    }

    public void clear() { hits.clear(); }
}
