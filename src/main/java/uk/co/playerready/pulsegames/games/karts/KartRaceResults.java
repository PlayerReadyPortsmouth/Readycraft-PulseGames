package uk.co.playerready.pulsegames.games.karts;

import java.util.*;

/** Per-race finish ledger. Departures stop waiting but never erase a recorded finish. */
public final class KartRaceResults {
    public static final int DEFAULT_GRACE_SECONDS = 30;
    private record Crossing(UUID player, long tick, double fraction) {}
    public record Result(UUID player, int place, long tick, double fraction, boolean tied) {}

    private final Map<UUID,Integer> entrants = new LinkedHashMap<>();
    private final Set<UUID> departed = new HashSet<>();
    private final Map<UUID,Crossing> finishes = new HashMap<>();
    private final long graceTicks;
    private long firstFinishTick = -1;

    public KartRaceResults(Collection<UUID> players) { this(players, DEFAULT_GRACE_SECONDS); }
    public KartRaceResults(Collection<UUID> players, int graceSeconds) {
        for (UUID player : players) {
            Objects.requireNonNull(player,"player");
            if (entrants.putIfAbsent(player,entrants.size()) != null)
                throw new IllegalArgumentException("Duplicate race entrant");
        }
        graceTicks = Math.clamp(graceSeconds,5,60) * 20L;
    }

    /** A crossing fraction is the swept gate entry in [0,1] within this simulation tick. */
    public boolean finish(UUID player, long tick, double fraction) {
        if (!entrants.containsKey(player) || departed.contains(player) || finishes.containsKey(player)) return false;
        if (tick < 0 || !Double.isFinite(fraction) || fraction < 0 || fraction > 1)
            throw new IllegalArgumentException("Invalid finish crossing");
        if (hasFinishers() && tick >= firstFinishTick && tick-firstFinishTick >= graceTicks) return false;
        finishes.put(player,new Crossing(player,tick,fraction == 0 ? 0 : fraction));
        firstFinishTick = firstFinishTick < 0 ? tick : Math.min(firstFinishTick,tick);
        return true;
    }

    public void depart(UUID player) { if (entrants.containsKey(player)) departed.add(player); }
    public boolean finished(UUID player) { return finishes.containsKey(player); }
    public boolean hasFinishers() { return !finishes.isEmpty(); }
    public int remainingRacers() {
        return (int) entrants.keySet().stream().filter(p -> !departed.contains(p) && !finished(p)).count();
    }

    /** Evaluate only after processing every racer in the current tick, to avoid iteration-order wins. */
    public boolean shouldEnd(long tick) {
        return hasFinishers() && (remainingRacers() == 0 || remainingTicks(tick) == 0);
    }
    public long remainingTicks(long tick) {
        if (!hasFinishers()) return graceTicks;
        // Subtraction avoids overflowing a deadline near Long.MAX_VALUE.
        long elapsed = tick <= firstFinishTick ? 0 : tick - firstFinishTick;
        return Math.max(0,graceTicks-elapsed);
    }
    public int remainingSeconds(long tick) { return (int)((remainingTicks(tick)+19)/20); }

    /** All genuine first-place ties, never every racer who merely completed the course. */
    public List<UUID> winners() {
        return results().stream().filter(result -> result.place()==1).map(Result::player).toList();
    }

    /** Stable first entry for display/legacy callers; progression should use winners() for exact ties. */
    public Optional<UUID> winner() {
        List<Result> results=results();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst().player());
    }
    public List<Result> results() {
        List<Crossing> sorted=new ArrayList<>(finishes.values());
        sorted.sort(Comparator.comparingLong(Crossing::tick).thenComparingDouble(Crossing::fraction)
                .thenComparingInt(c -> entrants.get(c.player())));
        List<Result> result=new ArrayList<>();
        int place=0;
        for(int i=0;i<sorted.size();i++) {
            Crossing current=sorted.get(i);
            boolean previousTie=i>0 && sameCrossing(current,sorted.get(i-1));
            boolean nextTie=i+1<sorted.size() && sameCrossing(current,sorted.get(i+1));
            if(!previousTie)place=i+1;
            result.add(new Result(current.player(),place,current.tick(),current.fraction(),previousTie||nextTie));
        }
        return List.copyOf(result);
    }
    private static boolean sameCrossing(Crossing a,Crossing b) {
        return a.tick()==b.tick() && Double.compare(a.fraction(),b.fraction())==0;
    }
}
