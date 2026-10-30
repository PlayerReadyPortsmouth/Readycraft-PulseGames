package uk.co.playerready.pulsenetwork;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pure state machine deciding which network presence line (if any) a player event earns. No I/O and no player text;
 * time comes from the injected clock. Rules: a switch line at most once per cooldown window per player (suppressed
 * hops are only counted), no leave line after a very short stay, and a rejoin soon after leaving is silent.
 * Hidden players never produce a line.
 */
public final class PresenceTracker {
    public enum Line { NONE, JOIN, SWITCH, LEAVE }
    public record Rules(long switchCooldownMs, long leaveMinStayMs, long quietRejoinMs) {}
    private static final ZoneId UK = ZoneId.of("Europe/London");
    private static final class State { long joinedAt = -1, leftAt = -1, lastSwitchLine = -1; int suppressed; }
    private final Clock clock;
    private final Rules rules;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, LocalDate> welcomed = new HashMap<>();

    public PresenceTracker(Rules rules, Clock clock) { this.rules = rules; this.clock = clock; }

    /** First connection to the network. */
    public synchronized Line join(UUID id, boolean hidden) {
        long now = clock.millis(); prune(now);
        State s = states.computeIfAbsent(id, k -> new State());
        boolean quiet = s.leftAt >= 0 && now - s.leftAt < rules.quietRejoinMs();
        s.joinedAt = now; s.leftAt = -1;
        return hidden || quiet ? Line.NONE : Line.JOIN;
    }
    /** Moved from one server to another. */
    public synchronized Line hop(UUID id, boolean hidden) {
        long now = clock.millis(); prune(now);
        if (hidden) return Line.NONE;
        State s = states.computeIfAbsent(id, k -> new State());
        if (s.joinedAt < 0) s.joinedAt = now;
        if (s.lastSwitchLine >= 0 && now - s.lastSwitchLine < rules.switchCooldownMs()) { s.suppressed++; return Line.NONE; }
        s.lastSwitchLine = now; s.suppressed = 0;
        return Line.SWITCH;
    }
    /** Left the network. */
    public synchronized Line leave(UUID id, boolean hidden) {
        long now = clock.millis(); prune(now);
        State s = states.get(id);
        if (s == null || s.joinedAt < 0) return Line.NONE;
        long stay = now - s.joinedAt;
        s.joinedAt = -1; s.leftAt = now;
        return hidden || stay < rules.leaveMinStayMs() ? Line.NONE : Line.LEAVE;
    }
    /** Hops swallowed since the last allowed switch line. Never shown to players. */
    public synchronized int suppressedSwitches(UUID id) { State s = states.get(id); return s == null ? 0 : s.suppressed; }

    /** True once per UK calendar day per player (until the proxy restarts). */
    public synchronized boolean firstJoinToday(UUID id) {
        LocalDate today = LocalDate.now(clock.withZone(UK));
        welcomed.values().removeIf(d -> !d.equals(today));
        return !today.equals(welcomed.put(id, today));
    }
    private void prune(long now) {
        long keep = Math.max(rules.quietRejoinMs(), rules.switchCooldownMs());
        states.values().removeIf(s -> s.joinedAt < 0 && s.leftAt >= 0 && now - s.leftAt >= keep);
    }
}
