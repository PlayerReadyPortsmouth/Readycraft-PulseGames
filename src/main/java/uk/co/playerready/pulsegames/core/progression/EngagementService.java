package uk.co.playerready.pulsegames.core.progression;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.economy.RewardRules;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.lobby.InfoMenu;
import uk.co.playerready.pulsegames.core.stats.WeeklyStats;
import uk.co.playerready.pulsegames.core.util.Text;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Daily challenges, win streaks, first win of the day and the weekly top three.
 * Works entirely from the plugin's own engagement.yml; the shared PulseContent SQL
 * path ({@link ProgressionService}) stays optional and separate.
 */
public final class EngagementService {
    private final PulseGamesPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final ChallengeStore challenges;
    private final StreakStore streaks;
    private boolean dirty;

    public EngagementService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "engagement.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.challenges = new ChallengeStore(section("challenges"));
        this.streaks = new StreakStore(section("streaks"));
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 20L * 60, 20L * 60);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::weeklyRollover, 100L, 20L * 600);
    }

    private org.bukkit.configuration.ConfigurationSection section(String name) {
        var s = data.getConfigurationSection(name);
        return s != null ? s : data.createSection(name);
    }

    private List<ChallengeDef> defs() {
        if (!plugin.getConfig().getBoolean("challenges.enabled", true)) return List.of();
        return ChallengeDef.load(plugin.getConfig().getConfigurationSection("challenges.daily"));
    }

    // ---- match hook -------------------------------------------------------------

    /** Called once per human at the end of a round (win or not). Practice and aborted rounds are ignored. */
    public void matchEnded(GameInstance game, Player player, boolean won, boolean anyWinner, String reason) {
        if (!ProgressionService.eligible(game.ranked(), game.isParticipant(player), game.isBot(player), reason)) return;
        LocalDate today = LocalDate.now();
        var done = new ArrayList<ChallengeDef>();
        synchronized (this) {
            done.addAll(challenges.record(player.getUniqueId(), today, game.type().id(), won,
                    game.survivedSeconds(player), defs()));
            dirty = true;
        }
        for (ChallengeDef def : done) {
            plugin.economy().addTokens(player, def.reward(), "challenge: " + def.name());
            player.sendMessage(Text.msg("<green><b>Challenge complete!</b> <gray>" + def.name() + " <gold>+" + def.reward() + " tokens"));
        }
        if (won) {
            int streak;
            boolean first;
            synchronized (this) {
                streak = streaks.win(player.getUniqueId());
                first = streaks.claimFirstWin(player.getUniqueId(), today);
                dirty = true;
            }
            int bonus = RewardRules.streakBonus(streak, plugin.getConfig().getInt("economy.streak-bonus-step", 5),
                    plugin.getConfig().getInt("economy.streak-bonus-max", 25));
            if (bonus > 0) plugin.economy().addTokens(player, bonus, streak + " win streak");
            if (first) {
                int firstBonus = plugin.getConfig().getInt("economy.first-win-bonus", 30);
                plugin.economy().addTokens(player, firstBonus, "first win of the day");
                player.sendMessage(Text.msg("<gold><b>First win of the day!</b> <gray>+" + firstBonus + " tokens"));
            }
            if (RewardRules.announceStreak(streak)) {
                Text.title(player, "<gold><b>" + streak + " WIN STREAK!", "<gray>Keep it going");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                Bukkit.getServer().sendMessage(Text.msg("<gold>" + player.getName() + "</gold> is on a <yellow>" + streak + "-win streak<gray>!"));
            }
        } else if (anyWinner) {
            synchronized (this) { streaks.loss(player.getUniqueId()); dirty = true; }
        }
    }

    // ---- /challenges ------------------------------------------------------------

    public void openChallenges(Player player) {
        LocalDate today = LocalDate.now();
        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        List<ChallengeDef> list = defs();
        int slot = 10;
        for (ChallengeDef def : list) {
            if (slot > 14) break;
            int progress; boolean done;
            synchronized (this) {
                progress = challenges.progress(player.getUniqueId(), today, def);
                done = challenges.done(player.getUniqueId(), today, def);
            }
            items.put(slot++, icon(done ? Material.LIME_DYE : Material.PAPER,
                    (done ? "<green><b>Done: " : "<yellow><b>") + def.name(),
                    "<gray>Progress: <white>" + Math.min(progress, def.target()) + "/" + def.target(),
                    "<gold>Reward: " + def.reward() + " tokens", done ? "<green>Come back tomorrow" : "<gray>Resets daily"));
        }
        if (list.isEmpty()) items.put(13, icon(Material.BARRIER, "<red>No challenges set", "<gray>Check back soon"));
        boolean claimed;
        int current, best;
        synchronized (this) {
            claimed = streaks.firstWinClaimed(player.getUniqueId(), today);
            current = streaks.current(player.getUniqueId());
            best = streaks.best(player.getUniqueId());
        }
        items.put(16, icon(claimed ? Material.GOLD_NUGGET : Material.GOLD_INGOT, "<gold><b>First win of the day",
                claimed ? "<green>Claimed today" : "<yellow>+" + plugin.getConfig().getInt("economy.first-win-bonus", 30) + " tokens on your next win"));
        items.put(22, icon(Material.BLAZE_POWDER, "<red><b>Win streak: " + current,
                "<gray>Best: <white>" + best, "<gray>Bonus tokens from 2 wins, announced at 3, 5 and 10"));
        InfoMenu.open(player, Text.mm("<dark_gray>Daily challenges"), 3, items);
        // Optional shared-SQL challenges print below when that path is switched on.
        if (plugin.getConfig().getBoolean("progression.enabled", false)) plugin.progression().show(player);
    }

    // ---- /top and the weekly board ----------------------------------------------

    public void top(Player player, String[] args) {
        if (args.length > 0) {
            GameType type = plugin.registry().get(args[0].toLowerCase(Locale.ROOT));
            if (type == null) { player.sendMessage(Text.msg("<red>Unknown game.")); return; }
            var rows = plugin.stats().topWeekly(type.id(), 3);
            player.sendMessage(Text.msg("<yellow><b>This week: " + type.displayName()));
            if (rows.isEmpty()) player.sendMessage(Text.msg("<gray>No wins yet this week."));
            for (int i = 0; i < rows.size(); i++)
                player.sendMessage(Text.mm("<gray>" + (i + 1) + ". <white>" + rows.get(i).name() + " <gray>- " + rows.get(i).wins() + " wins"));
            return;
        }
        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        var rows = plugin.stats().topWeekly(null, 3);
        Material[] medals = {Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT};
        int[] slots = {1, 3, 5};
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            GameType best = row.topGame() == null ? null : plugin.registry().get(row.topGame());
            items.put(slots[i], icon(medals[i], "<gold><b>#" + (i + 1) + " " + row.name(), "<gray>Wins this week: <white>" + row.wins(),
                    best == null ? "" : "<gray>Best game: <white>" + best.displayName() + " (" + row.topGameWins() + ")"));
        }
        if (rows.isEmpty()) items.put(3, icon(Material.BARRIER, "<gray>No wins yet this week", "<gray>Be the first!"));
        items.put(7, icon(Material.PLAYER_HEAD, "<aqua><b>You", "<gray>Your wins this week: <white>" + plugin.stats().weeklyWins(player)));
        List<String> ids = plugin.registry().all().stream().map(GameType::id).toList();
        int slot = 9;
        for (var leader : plugin.stats().weeklyLeaders(ids).entrySet()) {
            if (slot >= 45) break;
            GameType type = plugin.registry().get(leader.getKey());
            items.put(slot++, icon(type.icon(), "<yellow><b>" + type.displayName(),
                    "<gray>Leader: <white>" + leader.getValue().name(), "<gray>Wins: <white>" + leader.getValue().wins()));
        }
        InfoMenu.open(player, Text.mm("<dark_gray>Weekly top 3 (resets Monday)"), 5, items);
    }

    private void weeklyRollover() {
        String closing = plugin.stats().weeklyWeek();
        List<WeeklyStats.Entry> winners = plugin.stats().weeklyRollover(LocalDate.now());
        if (winners == null || winners.isEmpty()) return;
        // A crash after the payout reached disk but before the board was cleared must not pay the week twice.
        if (closing != null && closing.equals(plugin.economy().weeklyPaid())) { flushWeekly(); return; }
        plugin.economy().markWeeklyPaid(closing);
        List<Integer> rewards = plugin.getConfig().getIntegerList("leaderboard.weekly-rewards");
        for (int i = 0; i < winners.size() && i < rewards.size(); i++) {
            var w = winners.get(i);
            int amount = rewards.get(i);
            plugin.economy().grant(w.player(), amount);
            String note = "Last week you finished #" + (i + 1) + " with " + w.wins() + " wins: +" + amount + " tokens!";
            synchronized (this) {
                List<String> notices = new ArrayList<>(data.getStringList("notices." + w.player()));
                notices.add(note);
                data.set("notices." + w.player(), notices);
                dirty = true;
            }
            Player online = plugin.getServer().getPlayer(w.player());
            if (online != null) onJoin(online);
        }
        flushWeekly();
    }

    /** Payout, notices and the cleared board go to disk together, payout first, instead of on three timers. */
    private void flushWeekly() {
        plugin.economy().flush();
        flush();
        plugin.stats().flush();
    }

    /** Delivers weekly-reward notices to a player who was offline at the reset. */
    public void onJoin(Player player) {
        List<String> notices;
        synchronized (this) {
            notices = data.getStringList("notices." + player.getUniqueId());
            if (notices.isEmpty()) return;
            data.set("notices." + player.getUniqueId(), null);
            dirty = true;
        }
        notices.forEach(n -> player.sendMessage(Text.msg("<gold>" + n)));
    }

    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Text.mm(name));
            List<Component> lines = new ArrayList<>();
            for (String l : lore) if (!l.isEmpty()) lines.add(Text.mm(l));
            meta.lore(lines);
        });
        return item;
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
                plugin.getLogger().warning("Could not save engagement.yml: " + ex.getMessage());
            }
        }
    }
}
