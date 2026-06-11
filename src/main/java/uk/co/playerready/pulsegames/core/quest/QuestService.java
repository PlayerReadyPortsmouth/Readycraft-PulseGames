package uk.co.playerready.pulsegames.core.quest;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.util.Text;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Daily quests: the same three quests for everyone each day (community feel),
 * progress per player, token + XP rewards on completion. View with /quests.
 */
public final class QuestService {

    public enum Type { WIN, PLAY, KILL }

    public record Quest(String id, Type type, String gameId, int target, int reward, String description) {}

    /** Template pool; three are drawn per day, seeded by the date. */
    private static final List<Quest> TEMPLATES = List.of(
            new Quest("win-any", Type.WIN, null, 1, 150, "Win any game"),
            new Quest("win-3", Type.WIN, null, 3, 400, "Win 3 games"),
            new Quest("play-3", Type.PLAY, null, 3, 150, "Play 3 games"),
            new Quest("play-6", Type.PLAY, null, 6, 300, "Play 6 games"),
            new Quest("kills-5", Type.KILL, null, 5, 200, "Get 5 kills"),
            new Quest("kills-15", Type.KILL, null, 15, 450, "Get 15 kills"),
            new Quest("win-spleef", Type.WIN, "spleef", 1, 250, "Win a game of Spleef"),
            new Quest("win-bedwars", Type.WIN, "bedwars", 1, 300, "Win a game of Bedwars"),
            new Quest("win-skywars", Type.WIN, "skywars", 1, 300, "Win a game of Skywars"),
            new Quest("play-blockparty", Type.PLAY, "blockparty", 2, 200, "Play 2 rounds of Block Party"),
            new Quest("play-tntrun", Type.PLAY, "tntrun", 2, 200, "Play 2 rounds of TNT Run"),
            new Quest("play-karts", Type.PLAY, "karts", 2, 250, "Race twice in PulseKarts"));

    private final PulseGamesPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private boolean dirty;

    public QuestService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "quests.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 20L * 90, 20L * 90);
    }

    /** Today's three quests - same for everyone, rotating daily. */
    public List<Quest> today() {
        Random random = new Random(LocalDate.now().toEpochDay());
        List<Quest> pool = new ArrayList<>(TEMPLATES);
        List<Quest> picked = new ArrayList<>(3);
        for (int i = 0; i < 3 && !pool.isEmpty(); i++) {
            picked.add(pool.remove(random.nextInt(pool.size())));
        }
        return picked;
    }

    private String path(Player player, Quest quest) {
        return player.getUniqueId() + "." + LocalDate.now() + "." + quest.id();
    }

    public synchronized int progress(Player player, Quest quest) {
        return data.getInt(path(player, quest) + ".progress");
    }

    public synchronized boolean isDone(Player player, Quest quest) {
        return data.getBoolean(path(player, quest) + ".done");
    }

    /** Called by the game engine on wins, plays and kills. */
    public void record(Player player, String gameId, Type type) {
        for (Quest quest : today()) {
            if (quest.type() != type) continue;
            if (quest.gameId() != null && !quest.gameId().equalsIgnoreCase(gameId)) continue;
            if (isDone(player, quest)) continue;
            int progress;
            synchronized (this) {
                progress = progress(player, quest) + 1;
                data.set(path(player, quest) + ".progress", progress);
                dirty = true;
            }
            if (progress >= quest.target()) {
                synchronized (this) {
                    data.set(path(player, quest) + ".done", true);
                }
                plugin.economy().addTokens(player, quest.reward(), "quest complete");
                plugin.levels().addXp(player, quest.reward() / 2);
                player.sendMessage(Text.msg("<green><b>QUEST COMPLETE!</b></green> <gray>"
                        + quest.description() + " <gold>(+" + quest.reward() + " tokens)"));
                if (!Text.calm(player)) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.3f);
                }
            } else {
                player.sendMessage(Text.msg("<gray>Quest progress: <yellow>" + quest.description()
                        + "</yellow> <white>" + progress + "/" + quest.target()));
            }
        }
    }

    public void show(Player player) {
        player.sendMessage(Text.msg("<yellow><b>Today's quests</b> <gray>(reset at midnight)"));
        for (Quest quest : today()) {
            boolean done = isDone(player, quest);
            int progress = Math.min(progress(player, quest), quest.target());
            String bar = done ? "<green>✔ COMPLETE"
                    : "<white>" + progress + "/" + quest.target();
            player.sendMessage(Text.mm(" " + (done ? "<green>✔" : "<gray>•") + " <white>"
                    + quest.description() + " <gray>- " + bar + " <gold>(+" + quest.reward() + " ⛀)"));
        }
    }

    public synchronized void flush() {
        if (!dirty) return;
        try {
            data.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save quests.yml: " + ex.getMessage());
        }
    }
}
