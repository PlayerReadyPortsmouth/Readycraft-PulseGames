package uk.co.playerready.pulsegames.core.leaderboard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.util.LocUtil;
import uk.co.playerready.pulsegames.core.util.Text;

import java.io.File;
import java.util.Map;
import java.util.UUID;

/**
 * Floating lobby leaderboards (weekly wins) built from invisible armor stands -
 * no hologram plugin needed. Weekly reset keeps the board winnable for everyone.
 *
 * Admin: stand where you want it and run /pulse leaderboard add <game|overall>.
 */
public final class LeaderboardService {

    private static final String TAG = "pulse_leaderboard";

    private final PulseGamesPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public LeaderboardService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "leaderboards.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        // Initial render shortly after boot, then refresh every 5 minutes.
        Bukkit.getScheduler().runTaskLater(plugin, this::renderAll, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::renderAll, 20L * 300, 20L * 300);
    }

    public void add(Player admin, String game) {
        if (!game.equals("overall") && plugin.registry().get(game) == null) {
            admin.sendMessage(Text.msg("<red>Unknown game. Use a game id or <yellow>overall</yellow>."));
            return;
        }
        String id = "board" + (data.getKeys(false).size() + 1) + "_" + System.currentTimeMillis() % 10000;
        data.set(id + ".game", game.toLowerCase());
        data.set(id + ".world", admin.getWorld().getName());
        data.set(id + ".location", LocUtil.serialize(admin.getLocation().add(0, 2.2, 0)));
        save();
        renderAll();
        admin.sendMessage(Text.msg("<green>Leaderboard placed!</green> <gray>It refreshes every 5 minutes. "
                + "Remove all with <yellow>/pulse leaderboard clear"));
    }

    public void clear(Player admin) {
        for (String key : data.getKeys(false)) {
            data.set(key, null);
        }
        save();
        for (World world : Bukkit.getWorlds()) {
            removeStands(world);
        }
        admin.sendMessage(Text.msg("All leaderboards removed."));
    }

    private void removeStands(World world) {
        for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
            if (entity.getScoreboardTags().contains(TAG)) entity.remove();
        }
    }

    public void renderAll() {
        for (World world : Bukkit.getWorlds()) {
            removeStands(world);
        }
        for (String key : data.getKeys(false)) {
            ConfigurationSection board = data.getConfigurationSection(key);
            if (board == null) continue;
            World world = Bukkit.getWorld(board.getString("world", ""));
            if (world == null) continue;
            render(world, LocUtil.parse(board.getString("location"), world), board.getString("game", "overall"));
        }
    }

    private void render(World world, Location top, String game) {
        GameType type = plugin.registry().get(game);
        String title = type != null ? type.displayName() : "All Games";
        spawnLine(world, top, "<gradient:#ff5f6d:#ffc371><b>★ TOP WINNERS ★</b></gradient>");
        spawnLine(world, top.clone().subtract(0, 0.3, 0), "<yellow>" + title + " <gray>- this week");
        var entries = plugin.stats().topWeekly(game, 5);
        double y = 0.7;
        if (entries.isEmpty()) {
            spawnLine(world, top.clone().subtract(0, y, 0), "<gray>No wins yet - be the first!");
            return;
        }
        String[] medals = {"<gold>", "<white>", "<#cd7f32>", "<gray>", "<gray>"};
        int place = 0;
        for (Map.Entry<UUID, Integer> entry : entries) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "?";
            spawnLine(world, top.clone().subtract(0, y, 0), medals[place] + "#" + (place + 1)
                    + " <white>" + name + " <gray>- <yellow>" + entry.getValue() + " wins");
            y += 0.3;
            place++;
        }
    }

    private void spawnLine(World world, Location location, String miniMessage) {
        world.spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.customName(Text.mm(miniMessage));
            stand.setCustomNameVisible(true);
            stand.addScoreboardTag(TAG);
            stand.setPersistent(false);
        });
    }

    private void save() {
        try {
            data.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save leaderboards.yml: " + ex.getMessage());
        }
    }
}
