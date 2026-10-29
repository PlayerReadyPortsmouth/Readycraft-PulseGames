package uk.co.playerready.pulsegames.core.ranked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Waiting players per kit and the pairing rule. Pure: the caller supplies the clock. */
public final class RankedQueue {
    public record Ticket(UUID player, String kit, int rating, long joinedMs) { }
    public record Pair(Ticket a, Ticket b) { }

    private final Map<UUID, Ticket> tickets = new LinkedHashMap<>();
    private final QueueRange range;

    public RankedQueue(QueueRange range) { this.range = range; }

    public void add(Ticket ticket) { tickets.put(ticket.player(), ticket); }
    public Ticket remove(UUID player) { return tickets.remove(player); }
    public Ticket get(UUID player) { return tickets.get(player); }
    public List<Ticket> all() { return List.copyOf(tickets.values()); }

    public int rangeFor(Ticket ticket, long nowMs) { return range.at(nowMs - ticket.joinedMs()); }

    /**
     * Pairs the longest waiters first, each with the closest rating they and the other would both accept
     * (the narrower of the two ranges applies). Paired tickets leave the queue.
     */
    public List<Pair> match(long nowMs) {
        List<Ticket> waiting = new ArrayList<>(tickets.values());
        waiting.sort(Comparator.comparingLong(Ticket::joinedMs));
        List<Pair> pairs = new ArrayList<>();
        List<Ticket> used = new ArrayList<>();
        for (Ticket t : waiting) {
            if (used.contains(t)) continue;
            Ticket best = null;
            for (Ticket c : waiting) {
                if (c == t || used.contains(c) || !c.kit().equals(t.kit())) continue;
                int gap = Math.abs(c.rating() - t.rating());
                if (gap > Math.min(rangeFor(t, nowMs), rangeFor(c, nowMs))) continue;
                if (best == null || gap < Math.abs(best.rating() - t.rating())) best = c;
            }
            if (best == null) continue;
            used.add(t);
            used.add(best);
            pairs.add(new Pair(t, best));
        }
        for (Pair p : pairs) { tickets.remove(p.a().player()); tickets.remove(p.b().player()); }
        return pairs;
    }
}
