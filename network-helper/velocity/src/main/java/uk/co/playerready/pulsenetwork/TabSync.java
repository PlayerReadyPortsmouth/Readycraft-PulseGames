package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import java.util.*;

/** Applies desired rows to a viewer's tab list as a diff against its actual entries; unchanged entries send nothing. */
final class TabSync {
    private TabSync() {}

    /**
     * Local entries (same backend) are only adjusted when the backend has already sent them. Remote and header entries are added
     * by the proxy and tracked in {@code owned}; only owned entries are ever removed. Returns the number of changes made.
     */
    static int apply(TabList tab, List<TabRender.Row> rows, Set<UUID> owned) {
        int changes = 0;
        owned.removeIf(id -> !tab.containsEntry(id)); // The proxy clears the list on every server switch.
        var wanted = new HashSet<UUID>();
        for (var row : rows) {
            wanted.add(row.id());
            var existing = tab.getEntry(row.id());
            if (existing.isPresent()) {
                var entry = existing.get(); boolean changed = false;
                if (!entry.getDisplayNameComponent().equals(Optional.of(row.display()))) { entry.setDisplayName(row.display()); changed = true; }
                if (entry.getLatency() != row.latency()) { entry.setLatency(row.latency()); changed = true; }
                if (entry.getListOrder() != row.order()) { entry.setListOrder(row.order()); changed = true; }
                if (changed) changes++;
                if (row.local()) owned.remove(row.id());
            } else if (!row.local()) {
                tab.addEntry(TabListEntry.builder().tabList(tab).profile(row.profile()).displayName(row.display())
                        .latency(row.latency()).gameMode(0).listed(true).listOrder(row.order()).build());
                owned.add(row.id()); changes++;
            }
        }
        for (UUID id : List.copyOf(owned)) if (!wanted.contains(id)) { tab.removeEntry(id); owned.remove(id); changes++; }
        return changes;
    }
}
