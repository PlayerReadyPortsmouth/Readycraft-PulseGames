package uk.co.playerready.pulsenetwork;

import java.util.*;

/** Pure grouping and ordering of the network roster. Hidden players and non-participating servers are omitted. */
final class TabRoster {
    record Member(UUID id, String name, String server, int ping, String prefix, String suffix, boolean bedrock,
                  boolean hidden, boolean afk, boolean match, String role, String styleId, Integer level, String nickname) {
        Member(UUID id, String name, String server, int ping, String prefix, String suffix, boolean bedrock, boolean hidden, boolean afk, boolean match, String role, String styleId, Integer level) {
            this(id, name, server, ping, prefix, suffix, bedrock, hidden, afk, match, role, styleId, level, null);
        }
        Member(UUID id, String name, String server, int ping, String prefix, String suffix, boolean bedrock, boolean hidden, boolean afk, boolean match) {
            this(id, name, server, ping, prefix, suffix, bedrock, hidden, afk, match, null, null, null, null);
        }
    }
    record Group(String server, List<Member> members) {}

    private TabRoster() {}

    /** Viewer's own server first, then {@code order}, then any other participating server alphabetically; members by name. */
    static List<Group> groups(Collection<Member> all, String viewerServer, List<String> order, Set<String> participating) {
        var by = new TreeMap<String, List<Member>>();
        for (Member m : all) if (!m.hidden() && participating.contains(m.server())) by.computeIfAbsent(m.server(), k -> new ArrayList<>()).add(m);
        var names = new ArrayList<String>();
        if (by.containsKey(viewerServer)) names.add(viewerServer);
        for (String s : order) if (by.containsKey(s) && !names.contains(s)) names.add(s);
        for (String s : by.keySet()) if (!names.contains(s)) names.add(s);
        var groups = new ArrayList<Group>();
        for (String s : names) {
            var members = by.get(s);
            members.sort(Comparator.comparing((Member m) -> m.name().toLowerCase(Locale.ROOT)).thenComparing(Member::id));
            groups.add(new Group(s, List.copyOf(members)));
        }
        return groups;
    }
}
