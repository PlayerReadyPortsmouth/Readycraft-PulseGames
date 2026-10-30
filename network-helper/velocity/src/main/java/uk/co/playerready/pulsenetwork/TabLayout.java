package uk.co.playerready.pulsenetwork;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Pure planning for {@code tab.layout=columns}: one 20-slot column per server (header, members, blank fillers), so a
 * 1.21.2+ client that fills column by column shows each server in its own column. {@link #plan} returns empty when the
 * grouped list must be used instead.
 */
final class TabLayout {
    static final int ROWS = 20, MAX_COLUMNS = 4, MAX_ORDER = ROWS * MAX_COLUMNS;

    enum Kind { HEADER, MEMBER, FILLER, MORE }
    /** {@code index} is the position in the plan (column = index / 20); {@code member} only for MEMBER; {@code n} is the server's player count for HEADER, the hidden count for MORE and the block position for FILLER. */
    record Slot(int index, String server, Kind kind, TabRoster.Member member, int n) {}
    record Plan(List<Slot> slots, int columns) {}

    private TabLayout() {}

    /** Stable id for the filler at block position {@code i} of {@code server}; independent of which column the block sits in. */
    static UUID fillerId(String server, int i) { return UUID.nameUUIDFromBytes(("pulsenetwork-pad:" + server + ":" + i).getBytes(StandardCharsets.UTF_8)); }
    /** Stable id for the "+n more" row of {@code server}. */
    static UUID moreId(String server) { return UUID.nameUUIDFromBytes(("pulsenetwork-more:" + server).getBytes(StandardCharsets.UTF_8)); }
    /** Profile name sorts after real names (starts with a tilde) and is at most 16 characters. */
    static String fillerName(int i) { return "~p" + i; }

    /** Only Java 1.21.2+ honours list order; Bedrock and older Java keep the grouped list. */
    static boolean eligible(boolean bedrock, boolean modernJava) { return !bedrock && modernJava; }

    /** @return the plan, or empty to fall back to the grouped list. */
    static Optional<Plan> plan(List<TabRoster.Group> groups, String viewerServer, TabConfig config, Set<String> participating) {
        var blocks = new ArrayList<TabRoster.Group>(groups);
        if (config.showEmpty()) {
            var present = new HashSet<String>(); for (var g : groups) present.add(g.server());
            var empty = new TreeSet<>(participating); empty.removeAll(present);
            var ordered = new ArrayList<String>(); for (String s : config.order()) if (empty.remove(s)) ordered.add(s);
            ordered.addAll(empty);
            for (String s : ordered) blocks.add(new TabRoster.Group(s, List.of()));
        }
        if (blocks.isEmpty() || blocks.size() > MAX_COLUMNS) return Optional.empty();
        // One column each; spare columns go to servers that overflow, viewer's server first (groups are already in that order).
        var cols = new int[blocks.size()];
        Arrays.fill(cols, 1);
        int spare = MAX_COLUMNS - blocks.size();
        for (int b = 0; b < blocks.size() && spare > 0; b++) {
            int need = (int) Math.ceil((1 + blocks.get(b).members().size()) / (double) ROWS);
            int extra = Math.min(spare, need - 1);
            cols[b] += extra; spare -= extra;
        }
        var slots = new ArrayList<Slot>();
        for (int b = 0; b < blocks.size(); b++) {
            var g = blocks.get(b);
            int capacity = cols[b] * ROWS - 1, n = g.members().size();
            boolean overflow = n > capacity;
            if (overflow && g.server().equals(viewerServer)) return Optional.empty(); // The backend owns those entries: they cannot be hidden.
            int shown = overflow ? capacity - 1 : n;
            int blockStart = slots.size();
            slots.add(new Slot(slots.size(), g.server(), Kind.HEADER, null, n));
            for (int i = 0; i < shown; i++) slots.add(new Slot(slots.size(), g.server(), Kind.MEMBER, g.members().get(i), 0));
            if (overflow) slots.add(new Slot(slots.size(), g.server(), Kind.MORE, null, n - shown));
            for (int i = slots.size() - blockStart; i < cols[b] * ROWS; i++) slots.add(new Slot(slots.size(), g.server(), Kind.FILLER, null, i));
        }
        return Optional.of(new Plan(List.copyOf(slots), slots.size() / ROWS));
    }
    /** List order for a slot: same direction convention as the grouped list, but fixed to 80 so a plan's size never changes it. */
    static int order(int index, boolean higherFirst) { return higherFirst ? MAX_ORDER - index : index; }
}
