package uk.co.playerready.pulsegames.core.level;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.playerready.pulsegames.core.util.Text;

import java.io.File;
import java.io.IOException;

/**
 * Network XP and levels. XP is earned alongside tokens (wins, games, kills);
 * levels carry a title shown in chat and the tab list - visible progression
 * that isn't tied to beating other players.
 */
public final class LevelService implements Listener {

    private record Rank(int minLevel, String title, String color) {}

    private static final Rank[] RANKS = {
            new Rank(50, "Legend", "<gold>"),
            new Rank(30, "Master", "<dark_purple>"),
            new Rank(20, "Expert", "<red>"),
            new Rank(10, "Pro", "<aqua>"),
            new Rank(5, "Player", "<green>"),
            new Rank(1, "Rookie", "<gray>"),
    };

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private boolean dirty;

    public LevelService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "levels.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 20L * 90, 20L * 90);
    }

    public synchronized int xp(Player player) {
        return data.getInt(player.getUniqueId() + ".xp");
    }

    /** Level curve: level n starts at 100*(n-1)^2 XP (2@100, 3@400, 10@8100...). */
    public int level(Player player) {
        return 1 + (int) Math.sqrt(xp(player) / 100.0);
    }

    public int xpForLevel(int level) {
        return 100 * (level - 1) * (level - 1);
    }

    public Rank rank(int level) {
        for (Rank rank : RANKS) {
            if (level >= rank.minLevel()) return rank;
        }
        return RANKS[RANKS.length - 1];
    }

    public String prefix(Player player) {
        int level = level(player);
        Rank rank = rank(level);
        return rank.color() + "[" + level + " " + rank.title() + "]</" + colorTag(rank.color()) + "> ";
    }

    private String colorTag(String open) {
        return open.substring(1, open.length() - 1);
    }

    public void addXp(Player player, int amount) {
        if (amount <= 0) return;
        int before = level(player);
        synchronized (this) {
            data.set(player.getUniqueId() + ".xp", xp(player) + amount);
            dirty = true;
        }
        int after = level(player);
        if (after > before) {
            Rank rank = rank(after);
            player.sendMessage(Text.msg("<gold><b>LEVEL UP!</b></gold> <gray>You are now level "
                    + rank.color() + after + " " + rank.title()));
            Text.title(player, rank.color() + "<b>LEVEL " + after, "<gray>" + rank.title());
            if (!Text.calm(player)) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            }
            updateTabName(player);
        }
    }

    public void updateTabName(Player player) {
        player.playerListName(Text.mm(prefix(player) + "<white>" + player.getName()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateTabName(event.getPlayer());
    }

    /** Chat format: [Lv Title] Name: message. */
    @EventHandler
    public void onChat(AsyncChatEvent event) {
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) ->
                Text.mm(prefix(source) + "<white>" + source.getName() + "</white><gray>: <white>")
                        .append(message.color(net.kyori.adventure.text.format.NamedTextColor.WHITE))));
    }

    public synchronized void flush() {
        if (!dirty) return;
        try {
            data.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save levels.yml: " + ex.getMessage());
        }
    }
}
