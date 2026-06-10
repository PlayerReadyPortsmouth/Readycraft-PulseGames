package uk.co.playerready.pulsegames.games.duels;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import uk.co.playerready.pulsegames.core.game.GameInstance;
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
    private int round = 1;
    private boolean roundOver;

    public DuelsGame(GameInstance game) {
        super(game);
    }

    private boolean isSumo() { return game.mode().id().equals("sumo"); }

    private int roundsToWin() { return game.arena().settings().getInt("rounds-to-win", 2); }

    private String kitId() {
        return switch (game.mode().id()) {
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
        Kit kit = game.plugin().kits().get(kitId());
        int i = 0;
        for (Player player : game.alivePlayers()) {
            game.plugin().playerState().reset(player);
            player.teleport(game.arena().spawn(i++, game.world()));
            if (kit != null) kit.apply(player);
            Text.title(player, "<yellow><b>ROUND " + round, isSumo() ? "<gray>Knock them off!" : "<gray>Fight!");
        }
        game.broadcast("<yellow><b>Round " + round + "</b></yellow> <gray>- first to " + roundsToWin() + " round wins takes it!");
    }

    @Override
    public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {
        if (isSumo()) event.setDamage(0); // knockback only
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        if (roundOver) return;
        roundOver = true;
        Player winner = killer != null ? killer
                : game.alivePlayers().stream().filter(p -> !p.equals(victim)).findFirst().orElse(null);
        if (winner == null) {
            game.end(List.of(), "draw");
            return;
        }
        int wins = roundWins.merge(winner.getUniqueId(), 1, Integer::sum);
        game.broadcast("<yellow>" + winner.getName() + "</yellow> wins round <yellow>" + round + "</yellow>! <gray>("
                + wins + "/" + roundsToWin() + ")");
        if (wins >= roundsToWin()) {
            game.end(List.of(winner), "rounds");
            return;
        }
        round++;
        // Park the loser safely until the next round starts.
        victim.teleport(game.arena().spectator(game.world()));
        victim.setGameMode(org.bukkit.GameMode.SPECTATOR);
        game.runLater(60L, () -> {
            if (victim.isOnline()) victim.setGameMode(playGameMode());
            startRound();
        });
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
    public boolean pvp() { return true; }

    @Override
    public boolean fallDamage() { return !isSumo(); }

    @Override
    public int timeLimitSeconds() { return 600; }
}
