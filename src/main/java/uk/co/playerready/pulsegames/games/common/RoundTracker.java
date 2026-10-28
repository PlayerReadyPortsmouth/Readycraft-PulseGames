package uk.co.playerready.pulsegames.games.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure bookkeeping for the {@code rounds} mode: who is still in the current round, the order
 * people went out, and the placement-based points carried between rounds. No Bukkit types, so
 * the scoring rules are unit-tested directly.
 */
public final class RoundTracker {

    private final int totalRounds;
    private int round;
    private final Set<UUID> remaining = new LinkedHashSet<>();
    private final List<UUID> outOrder = new ArrayList<>();
    private final Map<UUID, Integer> totals = new LinkedHashMap<>();
    private final Map<UUID, Integer> roundWins = new LinkedHashMap<>();

    public RoundTracker(int totalRounds) {
        this.totalRounds = Math.max(1, totalRounds);
    }

    public int round() { return round; }
    public int totalRounds() { return totalRounds; }
    public boolean lastRound() { return round >= totalRounds; }
    public int remainingCount() { return remaining.size(); }
    public boolean isRemaining(UUID id) { return remaining.contains(id); }
    public Set<UUID> remaining() { return Set.copyOf(remaining); }
    public int total(UUID id) { return totals.getOrDefault(id, 0); }

    /** Begin the next round with the given field; everyone starts in. */
    public void startRound(Collection<UUID> field) {
        round++;
        remaining.clear();
        remaining.addAll(field);
        outOrder.clear();
        field.forEach(id -> totals.putIfAbsent(id, 0));
    }

    /** Record an out; false when they were not in the round. */
    public boolean knockOut(UUID id) {
        if (!remaining.remove(id)) return false;
        outOrder.add(id);
        return true;
    }

    /** A leaver forfeits their place; points already banked stay on the books but they cannot win. */
    public void leave(UUID id) {
        remaining.remove(id);
        outOrder.remove(id);
    }

    /** Round over for last-man-standing games: at most one left. */
    public boolean lastStanding() { return remaining.size() <= 1; }

    /** Best to worst: everyone still in (tied), then the last out, back to the first out. */
    public List<List<UUID>> eliminationTiers() {
        List<List<UUID>> tiers = new ArrayList<>();
        if (!remaining.isEmpty()) tiers.add(new ArrayList<>(remaining));
        for (int i = outOrder.size() - 1; i >= 0; i--) tiers.add(List.of(outOrder.get(i)));
        return tiers;
    }

    /**
     * Points for each tier (best first). Standard competition ranking: a player scores the field
     * size minus the number of players ranked strictly above them, so a lone winner of four scores
     * 4, a tied pair at the top both score 4 and the next player scores 2.
     */
    public static Map<UUID, Integer> pointsFor(List<List<UUID>> tiers) {
        int field = tiers.stream().mapToInt(List::size).sum();
        Map<UUID, Integer> points = new LinkedHashMap<>();
        int above = 0;
        for (List<UUID> tier : tiers) {
            for (UUID id : tier) points.put(id, field - above);
            above += tier.size();
        }
        return points;
    }

    /** 1-based competition placement of each player for the given tiers. */
    public static Map<UUID, Integer> placementsFor(List<List<UUID>> tiers) {
        Map<UUID, Integer> places = new LinkedHashMap<>();
        int above = 0;
        for (List<UUID> tier : tiers) {
            for (UUID id : tier) places.put(id, above + 1);
            above += tier.size();
        }
        return places;
    }

    /** Bank the round's points; returns what each player earned this round. */
    public Map<UUID, Integer> award(List<List<UUID>> tiers) {
        Map<UUID, Integer> earned = pointsFor(tiers);
        earned.forEach((id, pts) -> totals.merge(id, pts, Integer::sum));
        if (!tiers.isEmpty()) tiers.get(0).forEach(id -> roundWins.merge(id, 1, Integer::sum));
        return earned;
    }

    /**
     * Highest points total among {@code present}; equal totals are broken by round wins, and
     * anyone still level shares the win.
     */
    public List<UUID> matchWinners(Collection<UUID> present) {
        int best = Integer.MIN_VALUE;
        for (UUID id : present) best = Math.max(best, total(id));
        int top = best;
        List<UUID> leaders = present.stream().filter(id -> total(id) == top).toList();
        int bestWins = leaders.stream().mapToInt(id -> roundWins.getOrDefault(id, 0)).max().orElse(0);
        return leaders.stream().filter(id -> roundWins.getOrDefault(id, 0) == bestWins).toList();
    }
}
