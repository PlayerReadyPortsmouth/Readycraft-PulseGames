package uk.co.playerready.pulsenetwork;

import java.time.Clock;
import java.util.*;

/** Proxy-owned social groups; membership survives backend transfers, not proxy restarts. */
public final class NetworkParties {
    private record Invite(UUID leader, long expiry) {}
    private final Map<UUID, LinkedHashSet<UUID>> groups = new HashMap<>();
    private final Map<UUID, Invite> invitations = new HashMap<>();
    private final Clock clock;
    public NetworkParties() { this(Clock.systemUTC()); }
    public NetworkParties(Clock clock) { this.clock = clock; }
    public synchronized UUID leader(UUID member) {
        return groups.entrySet().stream().filter(e -> e.getValue().contains(member)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
    public synchronized List<UUID> members(UUID member) {
        UUID leader = leader(member);
        return leader == null ? List.of(member) : List.copyOf(groups.get(leader));
    }
    public synchronized void invite(UUID leader, UUID target) {
        if (leader.equals(target)) throw new IllegalArgumentException("Choose another player.");
        UUID current = leader(leader);
        if (current != null && !current.equals(leader)) throw new IllegalArgumentException("Only your party leader can invite.");
        if (leader(target) != null) throw new IllegalArgumentException("That player already has a party.");
        var members = groups.computeIfAbsent(leader, id -> new LinkedHashSet<>(List.of(id)));
        if (members.size() >= 8) throw new IllegalArgumentException("Parties hold at most eight players.");
        invitations.put(target, new Invite(leader, clock.millis() + 60_000));
    }
    public synchronized UUID inviter(UUID target) {
        Invite invite = invitations.get(target);
        return invite == null || invite.expiry < clock.millis() ? null : invite.leader;
    }
    public synchronized void accept(UUID target) {
        if (leader(target) != null) throw new IllegalArgumentException("Leave your current party first.");
        Invite invite = invitations.remove(target);
        if (invite == null || invite.expiry < clock.millis() || !groups.containsKey(invite.leader))
            throw new IllegalArgumentException("No current invitation. Ask for another invite.");
        var members = groups.get(invite.leader);
        if (members.size() >= 8) throw new IllegalArgumentException("That party is full.");
        members.add(target);
    }
    public synchronized void deny(UUID member) { invitations.remove(member); }
    public synchronized void leave(UUID member) {
        UUID leader = leader(member);
        invitations.remove(member);
        if (leader == null) return;
        var members = groups.get(leader);
        members.remove(member);
        if (members.isEmpty() || member.equals(leader)) {
            groups.remove(leader);
            invitations.values().removeIf(invite -> invite.leader.equals(leader));
            if (!members.isEmpty()) groups.put(members.iterator().next(), members);
        }
    }
    public synchronized void disband(UUID member) {
        if (!member.equals(leader(member))) throw new IllegalArgumentException("Only the party leader can disband.");
        groups.remove(member);
        invitations.values().removeIf(invite -> invite.leader.equals(member));
    }
}
