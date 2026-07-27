package uk.co.playerready.pulsegames.games.koth;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.kit.Kit;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * King of the Hill: hold the hill to earn points; first to the target wins.
 * Modes: solo, teams.
 */
public final class KothGame extends MiniGame {

    private Cuboid hill;
    private final Map<Integer, Integer> teamScores = new HashMap<>();

    public KothGame(GameInstance game) {
        super(game);
    }

    private int target() {
        return game.arena().settings().getInt("target-score", game.mode().isTeams() ? 250 : 120);
    }

    @Override
    public void onStart() {
        hill = game.arena().region("hill");
        Kit kit = game.plugin().kits().get(game.arena().settings().getString("kit", "koth"));
        if (kit != null) game.alivePlayers().forEach(kit::apply);
        game.broadcast("Hold the hill! First to <yellow>" + target() + "</yellow> points wins.");
    }

    @Override
    public void onRespawn(Player player) {
        Kit kit = game.plugin().kits().get(game.arena().settings().getString("kit", "koth"));
        if (kit != null) kit.apply(player);
    }

    @Override
    public void onSecond(int gameTime) {
        if (hill == null) return;
        List<Player> onHill = game.alivePlayers().stream()
                .filter(p -> hill.contains(p.getLocation())).toList();
        // One point per team per second, not per player: stacking the hill would otherwise
        // scale a team's capture rate with its size and win in a fraction of the time.
        onHill.stream()
                .map(game::teamOf)
                .filter(java.util.Objects::nonNull)
                .map(GameTeam::index)
                .distinct()
                .forEach(index -> teamScores.merge(index, 1, Integer::sum));
        for (Player player : onHill) {
            GameTeam team = game.teamOf(player);
            if (team == null) game.addScore(player, 1);
            int shown = team != null ? teamScores.getOrDefault(team.index(), 0) : game.score(player);
            player.sendActionBar(uk.co.playerready.pulsegames.core.util.Text.mm(
                    "<gold>⚑ Capturing! <yellow>+1</yellow> <gray>(" + shown + "/" + target() + ")"));
            player.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                    player.getLocation().add(0, 0.2, 0), 5, 0.3, 0.1, 0.3, 0.01);
        }
        if (game.mode().isTeams()) {
            teamScores.entrySet().stream()
                    .filter(e -> e.getValue() >= target())
                    .findFirst()
                    .ifPresent(e -> game.end(membersOfTeam(e.getKey()), "score"));
        } else {
            game.topScores().stream()
                    .filter(e -> e.getValue() >= target())
                    .findFirst()
                    .ifPresent(e -> game.end(playerList(e.getKey()), "score"));
        }
    }

    private List<Player> membersOfTeam(int index) {
        return game.teams().stream()
                .filter(t -> t.index() == index)
                .findFirst()
                .map(t -> game.alivePlayers().stream().filter(t::contains).toList())
                .orElse(List.of());
    }

    private List<Player> playerList(UUID uuid) {
        return game.players().stream().filter(p -> p.getUniqueId().equals(uuid)).toList();
    }

    @Override
    public void onTimeUp() {
        if (game.mode().isTeams()) {
            int best = teamScores.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(-1);
            game.end(best < 0 ? List.of() : membersOfTeam(best), "time");
        } else {
            var top = game.topScores();
            game.end(top.isEmpty() ? List.of() : playerList(top.get(0).getKey()), "time");
        }
    }

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Target: <yellow>" + target());
        if (game.mode().isTeams()) {
            game.teams().stream()
                    .sorted(Comparator.comparingInt((GameTeam t) -> teamScores.getOrDefault(t.index(), 0)).reversed())
                    .limit(4)
                    .forEach(t -> lines.add(t.coloredName() + "<gray>: <white>" + teamScores.getOrDefault(t.index(), 0)));
        } else {
            lines.add("<gray>You: <white>" + game.score(player));
            game.topScores().stream().limit(3).forEach(e -> {
                Player p = game.plugin().getServer().getPlayer(e.getKey());
                if (p != null) lines.add("<gray>" + p.getName() + ": <white>" + e.getValue());
            });
        }
        return lines;
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public boolean respawnable() { return true; }

    @Override
    public int respawnDelaySeconds() { return 5; }

    @Override
    public boolean fallDamage() { return true; }

    @Override
    public int timeLimitSeconds() { return 600; }
}
