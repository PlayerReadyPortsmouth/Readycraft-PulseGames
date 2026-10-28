package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.util.UUID;

/** Win streaks and the first-win-of-the-day marker (engagement.yml). */
public final class StreakStore {
    private final ConfigurationSection root;

    public StreakStore(ConfigurationSection root) { this.root = root; }

    public synchronized int win(UUID player) {
        int now = root.getInt(player + ".current") + 1;
        root.set(player + ".current", now);
        if (now > root.getInt(player + ".best")) root.set(player + ".best", now);
        return now;
    }

    public synchronized void loss(UUID player) { root.set(player + ".current", 0); }

    public synchronized int current(UUID player) { return root.getInt(player + ".current"); }

    public synchronized int best(UUID player) { return root.getInt(player + ".best"); }

    /** True exactly once per calendar day per player. */
    public synchronized boolean claimFirstWin(UUID player, LocalDate day) {
        if (day.toString().equals(root.getString(player + ".first-win"))) return false;
        root.set(player + ".first-win", day.toString());
        return true;
    }

    public synchronized boolean firstWinClaimed(UUID player, LocalDate day) {
        return day.toString().equals(root.getString(player + ".first-win"));
    }
}
