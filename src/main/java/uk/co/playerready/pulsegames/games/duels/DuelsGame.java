package uk.co.playerready.pulsegames.games.duels;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.kit.Kit;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Duels: 1v1, best of 3 rounds.
 * Modes: classic (iron kit), op (diamond kit), sumo (knock them off, no damage).
 */
public final class DuelsGame extends MiniGame {

    private final Map<UUID, Integer> roundWins = new HashMap<>();
    private final java.util.Set<UUID> roundOut = new java.util.HashSet<>();
    private int round = 1;
    private boolean roundOver;

    public DuelsGame(GameInstance game) {
        super(game);
    }

    @Override
    public boolean acceptsDeath(Player player) { return !roundOver && !roundOut.contains(player.getUniqueId()); }

    private boolean isSumo() { return game.mode().rulesId().equals("sumo"); }

    private int roundsToWin() { return game.arena().settings().getInt("rounds-to-win", 2); }

    private String kitId() {
        return switch (game.mode().rulesId()) {
            case "op" -> "op";
            case "sumo" -> "sumo";
            default -> game.arena().settings().getString("kit", "warrior");
        };
    }

    @Override
    public void onStart() {
        startRound();
    }

    private void startRound() {
        roundOver = false;
        roundOut.clear();
        Kit kit = game.plugin().kits().get(kitId());
        int i = 0;
        for (Player player : game.alivePlayers()) {
            game.plugin().playerState().reset(player);
            player.setGameMode(playGameMode());
            var team = game.teamOf(player);
            player.teleport(game.arena().spawn(team != null ? team.index() : i++, game.world()));
            if (game.plugin().combat() != null) game.plugin().combat().refresh(player);
            if (kit != null) kit.apply(player);
            Text.title(player, "<yellow><b>ROUND " + round, isSumo() ? "<gray>Knock them off!" : "<gray>Fight!");
        }
        game.broadcast("<yellow><b>Round " + round + "</b></yellow> <gray>- first to " + roundsToWin() + " round wins takes it!");
    }

    @Override
    public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {
        if (roundOver) event.setCancelled(true);
        else if (isSumo()) event.setDamage(0); // knockback only
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        if (roundOver) return;
        roundOut.add(victim.getUniqueId());
        victim.teleport(game.arena().spectator(game.world()));
        victim.setGameMode(org.bukkit.GameMode.SPECTATOR);
        if (game.plugin().combat() != null) game.plugin().combat().leave(victim);
        List<Player> remaining = game.alivePlayers().stream()
                .filter(p -> !roundOut.contains(p.getUniqueId())).toList();
        if (game.mode().isTeams()) {
            if (remaining.stream().map(game::teamOf).distinct().count() > 1) return;
        } else if (remaining.size() > 1) return;
        roundOver = true;
        if (remaining.isEmpty()) { game.end(List.of(), "draw"); return; }
        Player winner = remaining.getFirst();
        var winningTeam = game.teamOf(winner);
        List<Player> winners = winningTeam == null ? List.of(winner)
                : game.players().stream().filter(winningTeam::contains).toList();
        int wins = winners.stream().mapToInt(p -> roundWins.merge(p.getUniqueId(), 1, Integer::sum)).max().orElse(0);
        game.broadcast("<yellow>" + (winningTeam == null ? winner.getName() : winningTeam.coloredName())
                + "</yellow> wins round <yellow>" + round + "</yellow>! <gray>(" + wins + "/" + roundsToWin() + ")");
        if (wins >= roundsToWin()) { game.end(winners, "rounds"); return; }
        round++;
        game.runLater(60L, () -> {
            // The match can be decided in this window (a disconnect ends it), and
            // restarting a round mid-ENDING wipes the winner's inventory.
            if (game.state() != GameState.RUNNING) return;
            startRound();
        });
    }

    @Override
    public void onTimeUp() {
        List<Player> players = game.alivePlayers();
        int best = players.stream().mapToInt(p -> roundWins.getOrDefault(p.getUniqueId(), 0)).max().orElse(0);
        List<Player> leaders = players.stream()
                .filter(p -> roundWins.getOrDefault(p.getUniqueId(), 0) == best).toList();
        boolean oneSide = game.mode().isTeams() ? leaders.stream().map(game::teamOf).distinct().count() == 1 : leaders.size() == 1;
        game.end(best > 0 && oneSide ? leaders : List.of(), "time");
    }

    @Override
    public void onQuit(Player player) {
        roundWins.remove(player.getUniqueId());
    }

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("<gray>Round: <white>" + round);
        for (Player p : game.players()) {
            lines.add("<gray>" + p.getName() + ": <white>" + roundWins.getOrDefault(p.getUniqueId(), 0));
        }
        return lines;
    }

    @Override
    public int maxTeams() { return 2; }

    @Override
    public boolean pvp() { return true; }

    @Override
    public boolean fallDamage() { return !isSumo(); }

    @Override
    public int timeLimitSeconds() { return 600; }
}
