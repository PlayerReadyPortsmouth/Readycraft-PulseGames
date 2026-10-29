package uk.co.playerready.pulsenetwork;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.*;

/** Builds the signed STATUS report: server load plus per-player afk/hidden/in-match flags. */
final class StatusReporter {
    private final List<String> afkKeys;
    private Method busy;
    private boolean busyChecked;
    StatusReporter(List<String> afkKeys) { this.afkKeys = List.copyOf(afkKeys); }

    private static boolean flag(Player p, String key) {
        for (MetadataValue v : p.getMetadata(key)) if (v.asBoolean()) return true;
        return false;
    }
    /** {@code games} may be null; PulseGames only exposes a busy boolean, so the match flag carries no game name. */
    ServerStatus collect(double tps, double mspt, Collection<? extends Player> players, Plugin games) {
        var flags = new HashMap<UUID, Integer>();
        int online = 0;
        for (Player p : players) {
            if (p.hasMetadata("NPC")) continue;
            int value = 0;
            if (flag(p, "vanished")) value |= ServerStatus.HIDDEN; else online++;
            if (afkKeys.stream().anyMatch(k -> flag(p, k))) value |= ServerStatus.AFK;
            if (matching(games, p)) value |= ServerStatus.MATCH;
            if (value != 0) flags.put(p.getUniqueId(), value);
        }
        return new ServerStatus(tps, mspt, online, flags);
    }
    private boolean matching(Plugin games, Player p) {
        if (games == null || !games.isEnabled()) return false;
        try {
            if (!busyChecked) { busyChecked = true; busy = games.getClass().getMethod("networkPlayerBusy", Player.class); }
            return busy != null && Boolean.TRUE.equals(busy.invoke(games, p));
        } catch (ReflectiveOperationException e) { busy = null; return false; }
    }
}
