package uk.co.playerready.pulsegames.core.scoreboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure sidebar bookkeeping: unique entry text per line, and the minimal set of changes between two renders. */
final class SidebarDiff {
    private SidebarDiff() { }

    /** Entries to remove from the board, and entries to (re)score. */
    record Changes(List<String> remove, Map<String, Integer> set) {
        int total() { return remove.size() + set.size(); }
    }

    /** Pads duplicate (e.g. blank) lines with invisible resets so each line is its own entry. The pad budget is per line. */
    static List<String> uniqueEntries(List<String> rendered) {
        List<String> out = new ArrayList<>(rendered.size());
        Set<String> seen = new HashSet<>();
        for (String line : rendered) {
            String entry = line;
            int pad = 0;
            while (seen.contains(entry)) {
                entry += "§r";
                if (++pad > 32) break;
            }
            seen.add(entry);
            out.add(entry);
        }
        return out;
    }

    /** Score of line i is size - i, as the sidebar sorts descending. {@code previous} may be null (nothing drawn). */
    static Changes diff(List<String> previous, List<String> next) {
        Map<String, Integer> old = scores(previous);
        Map<String, Integer> now = scores(next);
        List<String> remove = new ArrayList<>();
        for (String entry : old.keySet()) if (!now.containsKey(entry)) remove.add(entry);
        Map<String, Integer> set = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : now.entrySet()) if (!e.getValue().equals(old.get(e.getKey()))) set.put(e.getKey(), e.getValue());
        return new Changes(remove, set);
    }

    private static Map<String, Integer> scores(List<String> entries) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (entries == null) return map;
        int score = entries.size();
        for (String entry : entries) map.put(entry, score--);
        return map;
    }
}
