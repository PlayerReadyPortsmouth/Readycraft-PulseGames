package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lifetime XP, the last few XP sources and the "first match of the day in this game" marker, kept in the
 * {@code levels} section of engagement.yml next to challenges and streaks. A stored {@code xp} key means the
 * player has been set up (migrated from lifetime wins, or started fresh).
 */
public final class LevelStore {
    public static final int RECENT = 6;
    private final ConfigurationSection root;

    public LevelStore(ConfigurationSection root) { this.root = root; }

    public synchronized boolean has(UUID player) { return root.contains(player + ".xp"); }

    public synchronized long xp(UUID player) { return root.getLong(player + ".xp"); }

    public synchronized void set(UUID player, long xp) { root.set(player + ".xp", Math.max(0, xp)); }

    /** Adds XP and remembers where it came from; returns the new total. */
    public synchronized long add(UUID player, int xp, String source) {
        long total = xp(player) + Math.max(0, xp);
        root.set(player + ".xp", total);
        List<String> recent = new ArrayList<>(root.getStringList(player + ".recent"));
        recent.add(0, "+" + xp + " XP: " + source);
        root.set(player + ".recent", new ArrayList<>(recent.subList(0, Math.min(RECENT, recent.size()))));
        return total;
    }

    public synchronized List<String> recent(UUID player) { return List.copyOf(root.getStringList(player + ".recent")); }

    /** True once per player, game and day. */
    public synchronized boolean claimFirstOfDay(UUID player, String game, LocalDate day) {
        String path = player + ".first." + game;
        if (day.toString().equals(root.getString(path))) return false;
        root.set(path, day.toString());
        return true;
    }
}
