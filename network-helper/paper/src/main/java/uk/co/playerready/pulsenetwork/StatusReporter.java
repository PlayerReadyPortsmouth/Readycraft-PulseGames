package uk.co.playerready.pulsenetwork;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.*;

/** Builds the signed STATUS report: server load plus per-player afk/hidden/in-match flags. */
final class StatusReporter {
    private final List<String> afkKeys;
    private Method busy, level;
    private boolean busyChecked, levelChecked;
    StatusReporter(List<String> afkKeys) { this.afkKeys = List.copyOf(afkKeys); }

    private static boolean flag(Player p, String key) {
        for (MetadataValue v : p.getMetadata(key)) if (v.asBoolean()) return true;
        return false;
    }
    /** {@code games} may be null; PulseGames only exposes a busy boolean, so the match flag carries no game name. */
    ServerStatus collect(double tps, double mspt, Collection<? extends Player> players, Plugin games) {
        var flags = new HashMap<UUID, Integer>();
        var levels = new HashMap<UUID, Integer>();
        int online = 0;
        for (Player p : players) {
            if (p.hasMetadata("NPC")) continue;
            int value = 0;
            if (flag(p, "vanished")) value |= ServerStatus.HIDDEN; else online++;
            if (afkKeys.stream().anyMatch(k -> flag(p, k))) value |= ServerStatus.AFK;
            if (matching(games, p)) value |= ServerStatus.MATCH;
            if (value != 0) flags.put(p.getUniqueId(), value);
            Integer lv = level(games, p);
            if (lv != null) levels.put(p.getUniqueId(), lv);
        }
        return new ServerStatus(tps, mspt, online, flags, levels);
    }
    /** PulseGames level via the soft {@code networkPlayerLevel(Player)} hook, or null when absent, disabled or invalid. */
    Integer level(Plugin games, Player p) {
        if (games == null || !games.isEnabled()) return null;
        try {
            if (!levelChecked) { levelChecked = true; level = games.getClass().getMethod("networkPlayerLevel", Player.class); }
            if (level == null) return null;
            Object value = level.invoke(games, p);
            return value instanceof Integer i && i > 0 && i <= ServerStatus.MAX_LEVEL ? i : null;
        } catch (ReflectiveOperationException e) { level = null; return null; }
    }
    private boolean matching(Plugin games, Player p) {
        if (games == null || !games.isEnabled()) return false;
        try {
            if (!busyChecked) { busyChecked = true; busy = games.getClass().getMethod("networkPlayerBusy", Player.class); }
            return busy != null && Boolean.TRUE.equals(busy.invoke(games, p));
        } catch (ReflectiveOperationException e) { busy = null; return false; }
    }
}
