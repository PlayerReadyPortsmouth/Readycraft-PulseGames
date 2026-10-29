package uk.co.playerready.pulsegames.core.economy;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.playerready.pulsegames.core.util.Text;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Pulse Tokens: earned by playing (participation, wins, kills, playtime, daily login),
 * spent in the token shop on cosmetics, kit unlocks and boosters.
 * YAML-backed; swap for a database when the network grows.
 */
public final class EconomyService {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;
    private boolean dirty;

    public EconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 20L * 60, 20L * 60);
        // Playtime bonus for everyone online.
        long interval = 20L * 60 * plugin.getConfig().getInt("economy.playtime-interval-minutes", 15);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int amount = plugin.getConfig().getInt("economy.playtime-bonus", 25);
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                addTokens(player, amount, "playtime bonus");
            }
        }, interval, interval);
    }

    private String key(Player player) {
        return player.getUniqueId().toString();
    }

    public synchronized int tokens(Player player) {
        return data.getInt(key(player) + ".tokens");
    }

    /** Week key of the last weekly leaderboard payout, so a restart between payout and board clear cannot pay twice. */
    public synchronized String weeklyPaid() { return data.getString("_meta.weekly-paid"); }

    public synchronized void markWeeklyPaid(String week) {
        data.set("_meta.weekly-paid", week);
        dirty = true;
    }

    public void addMatchTokens(Player player, int amount, String reason) {
        if (plugin instanceof uk.co.playerready.pulsegames.PulseGamesPlugin games) {
            var instance = games.instances().byPlayer(player);
            if (instance != null && !instance.ranked()) return;
        }
        if (plugin instanceof uk.co.playerready.pulsegames.PulseGamesPlugin games && games.quickPlay() != null) {
            var instance = games.instances().byPlayer(player);
            if (instance != null && games.quickPlay().isFeatured(instance.type())) {
                amount = (int) Math.max(1, Math.round(amount * games.quickPlay().multiplier()));
                reason += ", featured game";
            }
        }
        addTokens(player, amount, reason);
    }

    /** Credits tokens to a player who may be offline (weekly leaderboard rewards). */
    public synchronized void grant(java.util.UUID id, int amount) {
        if (amount <= 0) return;
        data.set(id + ".tokens", data.getInt(id + ".tokens") + amount);
        dirty = true;
    }

    public void addTokens(Player player, int amount, String reason) {
        if (amount <= 0 || player.hasMetadata("NPC")) return;
        if (boosterActive(player)) amount *= 2;
        synchronized (this) {
            data.set(key(player) + ".tokens", tokens(player) + amount);
            dirty = true;
        }
        if (plugin instanceof uk.co.playerready.pulsegames.PulseGamesPlugin games && games.stats() != null) games.stats().addTokensEarned(player, amount);
        player.sendActionBar(Text.mm("<gold>+" + amount + " ⛀ Tokens <gray>(" + reason + ")"));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
    }

    /** Returns true if the player could afford it and it was deducted. */
    public synchronized boolean spend(Player player, int amount) {
        int balance = tokens(player);
        if (balance < amount) return false;
        data.set(key(player) + ".tokens", balance - amount);
        dirty = true;
        return true;
    }

    // ---- daily login bonus -----------------------------------------------------

    public void handleDailyBonus(Player player) {
        String today = LocalDate.now().toString();
        synchronized (this) {
            if (today.equals(data.getString(key(player) + ".last-daily"))) return;
        }
        int amount = plugin.getConfig().getInt("economy.daily-bonus", 100);
        // The delay is only there to land after the join spam: mark the day claimed at
        // pay time, or a disconnect inside the window burns the bonus without paying it.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            synchronized (this) {
                if (today.equals(data.getString(key(player) + ".last-daily"))) return;
                data.set(key(player) + ".last-daily", today);
                dirty = true;
            }
            addTokens(player, amount, "daily bonus");
            player.sendMessage(Text.msg("<gold><b>Daily bonus!</b></gold> <gray>+" + amount
                    + " tokens for stopping by today."));
        }, 40L);
    }

    // ---- boosters -----------------------------------------------------------------

    public synchronized boolean boosterActive(Player player) {
        return data.getLong(key(player) + ".booster-until") > System.currentTimeMillis();
    }

    public synchronized void activateBooster(Player player, int minutes) {
        long base = Math.max(System.currentTimeMillis(), data.getLong(key(player) + ".booster-until"));
        data.set(key(player) + ".booster-until", base + minutes * 60_000L);
        dirty = true;
    }

    // ---- unlocks & equipped cosmetics ------------------------------------------------

    public synchronized boolean isUnlocked(Player player, String cosmeticId) {
        return data.getStringList(key(player) + ".unlocks").contains(cosmeticId);
    }

    public synchronized void unlock(Player player, String cosmeticId) {
        List<String> unlocks = data.getStringList(key(player) + ".unlocks");
        if (!unlocks.contains(cosmeticId)) {
            unlocks.add(cosmeticId);
            data.set(key(player) + ".unlocks", unlocks);
            dirty = true;
        }
    }

    public synchronized String equipped(Player player, Cosmetic.Category category) {
        return data.getString(key(player) + ".equipped." + category.name().toLowerCase(Locale.ROOT));
    }

    public synchronized void equip(Player player, Cosmetic.Category category, String cosmeticId) {
        data.set(key(player) + ".equipped." + category.name().toLowerCase(Locale.ROOT), cosmeticId);
        dirty = true;
    }

    public synchronized String duelKit(Player player, String profile) {
        return data.getString(key(player) + ".duel-kits." + profile);
    }
    public synchronized void duelKit(Player player, String profile, String kitId) {
        if (player.hasMetadata("NPC")) return;
        data.set(key(player) + ".duel-kits." + profile, kitId);
        dirty = true;
    }

    private final Object ioLock = new Object();

    /**
     * Snapshot under the data lock (cheap), then dump and write outside it: the main thread reads this store on
     * every token and stat lookup and must not wait for a YAML dump. The write is temp-file-then-move.
     */
    public void flush() {
        synchronized (ioLock) {
            org.bukkit.configuration.file.YamlConfiguration copy;
            synchronized (this) {
                if (!dirty) return;
                copy = uk.co.playerready.pulsegames.core.util.YamlFiles.snapshot(data);
                dirty = false;
            }
            String yaml = copy.saveToString();
            try {
                uk.co.playerready.pulsegames.core.util.YamlFiles.writeAtomically(file, yaml);
            } catch (IOException ex) {
                synchronized (this) { dirty = true; }
                plugin.getLogger().warning("Could not save economy.yml: " + ex.getMessage());
            }
        }
    }
}
