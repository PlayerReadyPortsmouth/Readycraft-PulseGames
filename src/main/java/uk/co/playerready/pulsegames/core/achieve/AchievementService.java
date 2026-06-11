package uk.co.playerready.pulsegames.core.achieve;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.util.Text;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Lifetime achievements with popup celebrations and token rewards.
 * Non-competitive milestones - everyone can reach them at their own pace.
 */
public final class AchievementService {

    public record Achievement(String id, String name, String description, String statKey,
                              int threshold, int reward) {}

    public static final List<Achievement> CATALOG = List.of(
            new Achievement("first-win", "First Victory!", "Win your first game", "wins", 1, 100),
            new Achievement("winner-10", "On a Roll", "Win 10 games", "wins", 10, 250),
            new Achievement("champion-50", "Champion", "Win 50 games", "wins", 50, 750),
            new Achievement("legend-200", "Pulse Legend", "Win 200 games", "wins", 200, 2000),
            new Achievement("getting-started", "Getting Started", "Play 10 games", "played", 10, 100),
            new Achievement("regular", "Regular", "Play 100 games", "played", 100, 500),
            new Achievement("devoted", "Devoted", "Play 500 games", "played", 500, 1500),
            new Achievement("scrapper", "Scrapper", "Get 25 kills", "kills", 25, 150),
            new Achievement("warrior", "Warrior", "Get 100 kills", "kills", 100, 400),
            new Achievement("unstoppable", "Unstoppable", "Get 500 kills", "kills", 500, 1200));

    private final PulseGamesPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private boolean dirty;

    public AchievementService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "achievements.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 20L * 90, 20L * 90);
    }

    public synchronized boolean unlocked(Player player, Achievement achievement) {
        return data.getBoolean(player.getUniqueId() + "." + achievement.id());
    }

    /** Checks all milestones against lifetime stats; call after wins/games/kills. */
    public void check(Player player) {
        for (Achievement achievement : CATALOG) {
            if (unlocked(player, achievement)) continue;
            if (plugin.stats().total(player, achievement.statKey()) < achievement.threshold()) continue;
            synchronized (this) {
                data.set(player.getUniqueId() + "." + achievement.id(), true);
                dirty = true;
            }
            celebrate(player, achievement);
        }
    }

    private void celebrate(Player player, Achievement achievement) {
        Text.title(player, "<gold><b>ACHIEVEMENT!", "<yellow>" + achievement.name());
        player.sendMessage(Text.msg("<gold><b>ACHIEVEMENT UNLOCKED:</b></gold> <yellow>" + achievement.name()
                + "</yellow> <gray>- " + achievement.description() + " <gold>(+" + achievement.reward() + " tokens)"));
        if (!Text.calm(player)) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        plugin.economy().addTokens(player, achievement.reward(), "achievement");
        plugin.levels().addXp(player, achievement.reward() / 2);
        // Let the server share the moment.
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.sendMessage(Text.msg("<yellow>" + player.getName() + "</yellow> <gray>unlocked <gold>"
                        + achievement.name() + "</gold>!"));
            }
        }
    }

    public void show(Player player) {
        long done = CATALOG.stream().filter(a -> unlocked(player, a)).count();
        player.sendMessage(Text.msg("<yellow><b>Achievements</b> <gray>(" + done + "/" + CATALOG.size() + ")"));
        for (Achievement achievement : CATALOG) {
            boolean has = unlocked(player, achievement);
            int progress = Math.min(plugin.stats().total(player, achievement.statKey()), achievement.threshold());
            player.sendMessage(Text.mm(" " + (has ? "<green>✔ <white>" : "<gray>• ") + achievement.name()
                    + " <gray>- " + achievement.description()
                    + (has ? "" : " <white>(" + progress + "/" + achievement.threshold() + ")")));
        }
    }

    public synchronized void flush() {
        if (!dirty) return;
        try {
            data.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save achievements.yml: " + ex.getMessage());
        }
    }
}
