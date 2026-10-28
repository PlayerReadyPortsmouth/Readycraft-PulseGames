package uk.co.playerready.pulsegames.games.common;

import java.util.*;

/**
 * Pure finish ledger for races: places and times, a grace window that opens at the first
 * finish, split times against the leader, and display formatting. No Bukkit types, so it is
 * unit tested directly. Times are milliseconds since the race clock started.
 */
public final class RaceFinish {
    public static final int DEFAULT_GRACE_SECONDS = 30;
    public record Result(UUID player, int place, long timeMs) {}

    private final Set<UUID> entrants = new LinkedHashSet<>();
    private final Set<UUID> departed = new HashSet<>();
    private final Map<UUID, Long> finishes = new HashMap<>();
    private final Map<Integer, Map<UUID, Long>> splits = new HashMap<>();
    private final long graceMs;
    private long firstFinishMs = -1;

    public RaceFinish(Collection<UUID> players, int graceSeconds) {
        entrants.addAll(players);
        graceMs = Math.clamp(graceSeconds, 5, 120) * 1000L;
    }

    /** Records a finish; false if unknown, repeated, departed, or the grace window has closed. */
    public boolean finish(UUID player, long timeMs) {
        if (!entrants.contains(player) || departed.contains(player) || finishes.containsKey(player)) return false;
        if (hasFinishers() && timeMs - firstFinishMs > graceMs) return false;
        finishes.put(player, timeMs);
        firstFinishMs = firstFinishMs < 0 ? timeMs : Math.min(firstFinishMs, timeMs);
        return true;
    }

    /** Leavers and ghost runners stop being waited for; a recorded finish stands. */
    public void depart(UUID player) { if (entrants.contains(player)) departed.add(player); }
    public boolean finished(UUID player) { return finishes.containsKey(player); }
    public boolean hasFinishers() { return !finishes.isEmpty(); }
    public long timeOf(UUID player) { return finishes.getOrDefault(player, -1L); }

    public int remainingRacers() {
        return (int) entrants.stream().filter(p -> !departed.contains(p) && !finishes.containsKey(p)).count();
    }

    /** True once someone has finished and either nobody is left to wait for or the grace has run out. */
    public boolean shouldEnd(long nowMs) {
        return hasFinishers() && (remainingRacers() == 0 || graceRemainingMs(nowMs) == 0);
    }

    public long graceRemainingMs(long nowMs) {
        if (!hasFinishers()) return graceMs;
        return Math.max(0, graceMs - Math.max(0, nowMs - firstFinishMs));
    }

    public int graceRemainingSeconds(long nowMs) { return (int) ((graceRemainingMs(nowMs) + 999) / 1000); }

    /** Finishers in place order (fastest first; equal times share the earlier entrant's place order). */
    public List<Result> results() {
        List<UUID> order = new ArrayList<>(finishes.keySet());
        List<UUID> entry = new ArrayList<>(entrants);
        order.sort(Comparator.<UUID>comparingLong(finishes::get).thenComparingInt(entry::indexOf));
        List<Result> out = new ArrayList<>();
        int place = 0;
        for (int i = 0; i < order.size(); i++) {
            long t = finishes.get(order.get(i));
            if (i == 0 || t != out.get(i - 1).timeMs()) place = i + 1;
            out.add(new Result(order.get(i), place, t));
        }
        return out;
    }

    /** Place of a finisher, or 0 if they have not finished. */
    public int placeOf(UUID player) {
        for (Result r : results()) if (r.player().equals(player)) return r.place();
        return 0;
    }

    /** Everyone placed: finishers first, then {@code unfinishedBestFirst} (ranked by checkpoint/stage). */
    public List<UUID> fullOrder(List<UUID> unfinishedBestFirst) {
        List<UUID> out = new ArrayList<>();
        results().forEach(r -> out.add(r.player()));
        for (UUID id : unfinishedBestFirst) if (!out.contains(id)) out.add(id);
        return out;
    }

    /**
     * Records a split at a gate (keyed by a monotonic progress count) and returns the gap to the
     * fastest earlier arrival at that gate in ms: 0 means this player is the leader there.
     */
    public long split(int gateKey, UUID player, long timeMs) {
        Map<UUID, Long> at = splits.computeIfAbsent(gateKey, k -> new HashMap<>());
        long leader = at.values().stream().mapToLong(Long::longValue).min().orElse(timeMs);
        at.putIfAbsent(player, timeMs);
        return Math.max(0, timeMs - leader);
    }

    // ---- display helpers ----

    public static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) return n + "th";
        return n + switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
    }

    /** m:ss.t */
    public static String time(long ms) {
        long tenths = Math.max(0, ms) / 100;
        return "%d:%02d.%d".formatted(tenths / 600, (tenths / 10) % 60, tenths % 10);
    }

    public static String gap(long ms) { return ms <= 0 ? "leader" : "+" + "%.1fs".formatted(ms / 1000.0); }
}
