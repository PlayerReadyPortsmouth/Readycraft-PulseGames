package uk.co.playerready.pumpkinhunt.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Who has found which lantern, and who has finished. Pure logic: no server types, so it is unit-tested.
 * Completion is recorded here, once per player, and is what the reward and broadcast hang off.
 */
public final class PumpkinProgress {
    public enum Outcome { NEW, ALREADY, COMPLETED, ALREADY_COMPLETE }

    public record Result(Outcome outcome, int found) {}

    public record Finisher(UUID id, String name, long completedAt) {}

    static final class Entry {
        Set<String> found = new LinkedHashSet<>();
        long completedAt;
        String name = "";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    /**
     * Records a click on {@code lanternId}. {@code allIds} is the current set of registered lanterns; the
     * hunt is complete when every one of them is found. Returns COMPLETED exactly once per player.
     */
    public synchronized Result record(UUID player, String name, String lanternId, Set<String> allIds, long now) {
        Entry e = entries.computeIfAbsent(player, k -> new Entry());
        if (e.completedAt > 0) return new Result(Outcome.ALREADY_COMPLETE, countIn(e, allIds));
        if (!e.found.add(lanternId)) return new Result(Outcome.ALREADY, countIn(e, allIds));
        int n = countIn(e, allIds);
        if (!allIds.isEmpty() && n >= allIds.size()) {
            e.completedAt = now;
            e.name = name == null ? "" : name;
            return new Result(Outcome.COMPLETED, n);
        }
        return new Result(Outcome.NEW, n);
    }

    /** Found lanterns that are still registered (a removed lantern stops counting). */
    public synchronized int foundCount(UUID player, Set<String> allIds) {
        Entry e = entries.get(player);
        return e == null ? 0 : countIn(e, allIds);
    }

    public synchronized Set<String> found(UUID player) {
        Entry e = entries.get(player);
        return e == null ? Set.of() : new LinkedHashSet<>(e.found);
    }

    public synchronized boolean completed(UUID player) {
        Entry e = entries.get(player);
        return e != null && e.completedAt > 0;
    }

    /** The first {@code limit} finishers, earliest first. */
    public synchronized List<Finisher> top(int limit) {
        List<Finisher> all = new ArrayList<>();
        for (Map.Entry<UUID, Entry> m : entries.entrySet()) {
            if (m.getValue().completedAt > 0) all.add(new Finisher(m.getKey(), m.getValue().name, m.getValue().completedAt));
        }
        all.sort(Comparator.comparingLong(Finisher::completedAt).thenComparing(f -> f.id().toString()));
        return all.size() <= limit ? all : new ArrayList<>(all.subList(0, limit));
    }

    /** How many players found each lantern, including lanterns nobody has found (count 0). */
    public synchronized Map<String, Integer> stats(Set<String> allIds) {
        Map<String, Integer> out = new TreeMap<>();
        for (String id : allIds) out.put(id, 0);
        for (Entry e : entries.values()) for (String id : e.found) out.computeIfPresent(id, (k, v) -> v + 1);
        return out;
    }

    public synchronized boolean reset(UUID player) { return entries.remove(player) != null; }

    public synchronized int resetAll() {
        int n = entries.size();
        entries.clear();
        return n;
    }

    public synchronized int players() { return entries.size(); }

    public synchronized String toJson() {
        Map<String, Entry> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, Entry> m : entries.entrySet()) out.put(m.getKey().toString(), m.getValue());
        return GSON.toJson(out);
    }

    public synchronized void loadJson(String json) {
        entries.clear();
        if (json == null || json.isBlank()) return;
        Map<String, Entry> raw = GSON.fromJson(json, new TypeToken<Map<String, Entry>>() {}.getType());
        if (raw == null) return;
        for (Map.Entry<String, Entry> m : raw.entrySet()) {
            UUID id;
            try { id = UUID.fromString(m.getKey()); } catch (IllegalArgumentException bad) { continue; }
            Entry e = m.getValue();
            if (e == null) continue;
            if (e.found == null) e.found = new LinkedHashSet<>();
            if (e.name == null) e.name = "";
            entries.put(id, e);
        }
    }

    private static int countIn(Entry e, Set<String> allIds) {
        int n = 0;
        for (String id : e.found) if (allIds.contains(id)) n++;
        return n;
    }
}
