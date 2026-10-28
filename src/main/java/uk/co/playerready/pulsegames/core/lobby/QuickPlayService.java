package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.util.Text;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Quick Play button, the daily featured game (token bonus) and live queue counts. */
public final class QuickPlayService {
    private final PulseGamesPlugin plugin;
    private final Random random = new Random();

    public QuickPlayService(PulseGamesPlugin plugin) { this.plugin = plugin; }

    /** Duels needs a kit choice, so it stays on its own menu. */
    private boolean playable(GameType type) {
        return !type.id().equals("duels") && plugin.gameAvailable(type) && modeFor(type) != null;
    }

    /** First mode that can start with real players and has at least one arena. */
    GameMode modeFor(GameType type) {
        for (GameMode mode : type.modes()) {
            if (mode.minPlayers() >= 2 && !plugin.arenas().forGame(type.id(), mode.id()).isEmpty()) return mode;
        }
        return null;
    }

    private List<String> pool() {
        List<String> configured = plugin.getConfig().getStringList("quickplay.featured.games");
        return plugin.registry().all().stream()
                .filter(this::playable)
                .map(GameType::id)
                .filter(id -> configured.isEmpty() || configured.contains(id))
                .toList();
    }

    public String featuredId() {
        return QuickPlay.featured(LocalDate.now(), pool(), plugin.getConfig().getString("quickplay.featured.override", ""));
    }

    public boolean isFeatured(GameType type) {
        return plugin.getConfig().getBoolean("quickplay.enabled", true) && type.id().equals(featuredId());
    }

    public double multiplier() {
        return Math.max(1.0, plugin.getConfig().getDouble("quickplay.featured.token-multiplier", 1.5));
    }

    public Map<String, QueueCounts.Count> counts() {
        return QueueCounts.tally(plugin.instances().all().stream()
                .map(g -> new QueueCounts.Row(g.type().id(),
                        g.state() == GameState.WAITING || g.state() == GameState.COUNTDOWN,
                        g.state() == GameState.RUNNING, g.practice(), g.humanCount()))
                .toList());
    }

    public void quickPlay(Player player) {
        if (!plugin.getConfig().getBoolean("quickplay.enabled", true)) {
            player.sendMessage(Text.msg("<red>Quick Play is not enabled.")); return;
        }
        if (plugin.instances().byPlayer(player) != null || plugin.playService().hasPending(player)) {
            player.sendMessage(Text.msg("<yellow>You are already in a queue. Use /lobby to leave it first.")); return;
        }
        String featured = featuredId();
        var opens = plugin.instances().all().stream()
                .filter(g -> (g.state() == GameState.WAITING || g.state() == GameState.COUNTDOWN)
                        && !g.practice() && g.humanCount() > 0 && g.joinable(1) && playable(g.type())
                        && (!g.type().wip() || plugin.gamePublic(g.type()) || plugin.canUseNewFeatures(player)))
                .map(g -> new QuickPlay.Open(g.type().id(), g.id(), g.humanCount()))
                .toList();
        var open = QuickPlay.pickQueue(opens, featured, random);
        if (open != null) {
            plugin.playService().joinNetworkQueue(player, open.instanceId());
            return;
        }
        List<String> eligible = plugin.registry().all().stream().filter(this::playable)
                .filter(t -> !t.wip() || plugin.gamePublic(t) || plugin.canUseNewFeatures(player))
                .map(GameType::id).toList();
        String chosen = QuickPlay.pickGame(eligible, featured, random);
        if (chosen == null) {
            player.sendMessage(Text.msg("<red>No game is ready to play right now. Try the game menu.")); return;
        }
        GameType type = plugin.registry().get(chosen);
        player.sendMessage(Text.msg("<green>Quick Play: <yellow>" + type.displayName()
                + (chosen.equals(featured) ? " <gold>(today's featured game)" : "")));
        plugin.playService().join(player, type, modeFor(type));
    }
}
