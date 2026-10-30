package uk.co.playerready.pulsegames.core.progression;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.economy.Cosmetic;
import uk.co.playerready.pulsegames.core.economy.CosmeticsService;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Text;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * XP and levels across every game. Awards XP for eligible rounds and challenges, shows the {@code [12]} prefix
 * in the lobby, announces milestones to the lobby only, and unlocks one cosmetic every five levels.
 * Storage is the {@code levels} section of engagement.yml.
 */
public final class LevelService implements Listener {
    private final PulseGamesPlugin plugin;

    private NameStyleGrants nameStyles;

    public LevelService(PulseGamesPlugin plugin) { this.plugin = plugin; }

    /** Created lazily so LuckPerms is looked up once, after every plugin has loaded. */
    private NameStyleGrants nameStyles() {
        if (nameStyles == null) {
            NameStyleGrants.Granter granter = null;
            try { granter = LuckPermsGranter.create(plugin.getServer().getPluginManager(), plugin.getLogger()); }
            catch (Throwable missing) { plugin.getLogger().info("LuckPerms not found: name styles will not be granted."); }
            nameStyles = new NameStyleGrants(granter,
                    () -> plugin.getConfig().getBoolean("progression.namestyle-grants", true));
        }
        return nameStyles;
    }

    private boolean enabled() { return plugin.getConfig().getBoolean("progression.levels-enabled", true); }

    private LevelStore store() { return plugin.engagement().levelStore(); }

    private static boolean real(Player player) { return !player.hasMetadata("NPC"); }

    // ---- reading -----------------------------------------------------------------------

    /** Sets a player up on first sight: lifetime wins become starting XP, then owed unlocks are granted. */
    public void ensure(Player player) {
        if (!enabled() || !real(player)) return;
        UUID id = player.getUniqueId();
        if (!store().has(id)) {
            int wins = 0;
            for (var type : plugin.registry().all()) wins += plugin.stats().get(player, type.id(), "wins");
            store().set(id, XpRules.migratedXp(wins));
            plugin.engagement().markDirty();
        }
        // Read the store directly: level() -> progress() -> xp() -> ensure() would recurse forever.
        grantUnlocks(player, XpRules.levelFor(store().xp(id)), false);
    }

    public long xp(Player player) { ensure(player); return enabled() ? store().xp(player.getUniqueId()) : 0; }

    public XpRules.Progress progress(Player player) { return XpRules.progress(xp(player)); }

    public int level(Player player) { return progress(player).level(); }

    /** "[12]" or an empty string when levels are off. */
    public String tag(Player player) { return enabled() ? XpRules.tag(level(player)) : ""; }

    public List<String> recent(Player player) { return store().recent(player.getUniqueId()); }

    // ---- awarding ----------------------------------------------------------------------

    /** Called for an eligible round only (the caller has already applied the competitive gate). */
    public void matchEnded(GameInstance game, Player player, boolean won) {
        if (!enabled() || !real(player)) return;
        int place = won ? 1 : game.placement(player);
        boolean first = store().claimFirstOfDay(player.getUniqueId(), game.type().id(), LocalDate.now());
        int kills = game.roundKillCount(player);
        int xp = XpRules.matchXp(place, kills, first);
        String source = (place > 0 ? ordinal(place) + " in " : "Played ") + game.type().displayName()
                + (kills > 0 ? ", " + kills + (kills == 1 ? " kill" : " kills") : "") + (first ? ", first of the day" : "");
        award(player, xp, source);
        player.sendMessage(Text.msg("<aqua>+" + xp + " XP</aqua> <gray>" + XpRules.colouredTag(level(player))
                + (first ? " (includes the first game of the day bonus)" : "")));
    }

    private static String ordinal(int place) {
        return switch (place) { case 1 -> "1st"; case 2 -> "2nd"; case 3 -> "3rd"; default -> place + "th"; };
    }

    public void award(Player player, int xp, String source) {
        if (!enabled() || !real(player) || xp <= 0) return;
        ensure(player);
        int before = XpRules.levelFor(store().xp(player.getUniqueId()));
        long total = store().add(player.getUniqueId(), xp, source);
        plugin.engagement().markDirty();
        int after = XpRules.levelFor(total);
        if (after > before) levelUp(player, before, after);
    }

    private void levelUp(Player player, int before, int after) {
        grantUnlocks(player, after, true);
        // Land after the "Victory" / "Well played" title rather than being overwritten by it.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Text.title(player, "<gold><b>Level up!", XpRules.colouredTag(after) + " <gray>You reached level " + after);
            player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 1f, 1f);
            player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.1);
            refreshName(player);
            announce(player, before, after);
        }, 60L);
    }

    /** Milestone levels only, to the lobby only; chat on other servers never sees it. */
    private void announce(Player player, int before, int after) {
        if (!plugin.getConfig().getBoolean("progression.announce-levels", true)) return;
        int milestone = after / XpRules.UNLOCK_EVERY * XpRules.UNLOCK_EVERY;
        if (milestone <= before || milestone < XpRules.UNLOCK_EVERY) return;
        Component line = Text.msg("<gold>" + player.getName() + "</gold> reached level <aqua>" + milestone + "<gray>!");
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.hasMetadata("NPC") && plugin.lobby().isLobbyWorld(other.getWorld())) other.sendMessage(line);
        }
    }

    /** Unlocks every level reward at or below {@code level}. Idempotent, so a migrated regular gets them all. */
    private void grantUnlocks(Player player, int level, boolean tell) { unlockThrough(player, level, tell); }

    /** The unlock path shared by real level-ups and admin commands; returns the names newly unlocked. */
    private List<String> unlockThrough(Player player, int level, boolean tell) {
        List<String> names = new java.util.ArrayList<>();
        for (Cosmetic reward : CosmeticsService.levelRewards()) {
            if (reward.level() > level || plugin.economy().isUnlocked(player, reward.id())) continue;
            plugin.economy().unlock(player, reward.id());
            boolean style = reward.nameStyleId() != null;
            names.add(reward.name() + (style ? " (name style)" : ""));
            if (tell) player.sendMessage(Text.msg("<green><b>New " + (style ? "name style" : "cosmetic") + " unlocked:</b> <yellow>"
                    + reward.name() + (style ? " <gray>Set it with /namestyle." : " <gray>Open /wardrobe to use it.")));
        }
        nameStyles().grant(player.getUniqueId(), level, player::hasPermission, CosmeticsService.levelRewards());
        return names;
    }

    // ---- admin (see ProgressionAdmin) --------------------------------------------------

    /** Admin unlock: same path as a level-up, without chat spam to the player (the admin gets the summary). */
    public List<String> adminUnlock(Player player, int level) { return unlockThrough(player, level, false); }

    public void adminSetXp(Player player, long xp) {
        store().set(player.getUniqueId(), xp);
        plugin.engagement().markDirty();
    }

    public long adminXp(Player player) { return store().xp(player.getUniqueId()); }

    /** Removes the level-reward cosmetics (and unequips them). LuckPerms is untouched. */
    public int adminClearUnlocks(Player player) {
        int cleared = 0;
        for (Cosmetic reward : CosmeticsService.levelRewards()) {
            if (reward.id().equals(plugin.economy().equipped(player, reward.category()))) plugin.economy().equip(player, reward.category(), null);
            if (plugin.economy().lock(player, reward.id())) cleared++;
        }
        return cleared;
    }

    public NameStyleGrants nameStyleGrants() { return nameStyles(); }

    // ---- lobby nametag -----------------------------------------------------------------

    private static String teamName(Player player) { return "pgl" + player.getUniqueId().toString().replace("-", "").substring(0, 12); }

    /** Puts "[12] " before the name on the main scoreboard (the lobby's), unless another plugin owns the entry. */
    public void refreshName(Player player) {
        if (!enabled() || !real(player)) return;
        String tag = XpRules.colouredTag(level(player));
        player.playerListName(Text.mm(tag + " <white>" + player.getName()));
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team own = board.getTeam(teamName(player));
        Team current = board.getEntryTeam(player.getName());
        if (current != null && current != own) return;
        if (own == null) own = board.registerNewTeam(teamName(player));
        own.prefix(Text.mm(tag + " "));
        if (current == null) own.addEntry(player.getName());
    }

    private void removeName(Player player) {
        Team own = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName(player));
        if (own != null) own.unregister();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!real(player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> { if (player.isOnline()) { ensure(player); refreshName(player); } });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { if (real(event.getPlayer())) removeName(event.getPlayer()); }
}
