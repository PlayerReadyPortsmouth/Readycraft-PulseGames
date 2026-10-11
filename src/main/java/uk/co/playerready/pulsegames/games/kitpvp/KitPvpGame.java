package uk.co.playerready.pulsegames.games.kitpvp;

import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.kit.Kit;

import java.util.ArrayList;
import java.util.List;

/**
 * Kit PvP: free-for-all arena fighting with respawns.
 * Modes: ffa (first to kill target), oitc (One in the Chamber: bow one-shots, arrow back per kill).
 */
public final class KitPvpGame extends MiniGame {

    public KitPvpGame(GameInstance game) {
        super(game);
    }

    private boolean isOitc() { return game.mode().rulesId().equals("oitc"); }

    private int targetKills() {
        return game.arena().settings().getInt("target-kills", isOitc() ? 20 : 15);
    }

    @Override
    public void onStart() {
        game.alivePlayers().forEach(this::equip);
        if (!isOitc()) game.broadcast("<aqua>Choose a loadout with /pulsekits. Changes apply on your next spawn.");
        game.broadcast("First to <yellow>" + targetKills() + "</yellow> kills wins!");
    }

    private void equip(Player player) {
        if (isOitc()) {
            player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
            player.getInventory().setItem(1, new ItemStack(Material.BOW));
            player.getInventory().setItem(9, new ItemStack(Material.ARROW, 1));
        } else {
            // Token-shop kit unlocks override the arena default.
            String unlocked = game.plugin().cosmetics().equippedKitId(player);
            Kit kit = unlocked != null ? game.plugin().kits().get(unlocked) : null;
            if (kit == null) {
                kit = game.plugin().kits().get(game.arena().settings().getString("kit", "warrior"));
            }
            if (kit != null) kit.apply(player);
        }
    }

    @Override
    public void onRespawn(Player player) {
        equip(player);
    }

    @Override
    public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {
        if (isOitc() && event.getDamager() instanceof Arrow) {
            event.setDamage(1000); // one in the chamber
        }
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        if (killer != null) {
            game.addScore(killer, 1);
            game.stats().addKill(killer, game.type().id());
            game.broadcast("<red>" + victim.getName() + "</red> was slain by <yellow>" + killer.getName()
                    + "</yellow> <gray>(" + game.score(killer) + "/" + targetKills() + ")");
            if (isOitc()) {
                killer.getInventory().addItem(new ItemStack(Material.ARROW, 1));
            }
            if (sideScore(killer) >= targetKills()) {
                game.end(sideMembers(killer), "kills");
                return;
            }
        } else {
            game.broadcast("<red>" + victim.getName() + "</red> died");
        }
        game.respawn(victim, respawnDelaySeconds());
    }

    @Override
    public void onTimeUp() {
        int best = game.players().stream().mapToInt(this::sideScore).max().orElse(0);
        List<Player> leaders = game.players().stream().filter(p -> sideScore(p) == best).toList();
        boolean oneSide = game.mode().isTeams() ? leaders.stream().map(game::teamOf).distinct().count() == 1 : leaders.size() == 1;
        game.end(best > 0 && oneSide ? leaders : List.of(), "time");
    }

    private List<Player> sideMembers(Player player) {
        var team = game.teamOf(player);
        return team == null ? List.of(player) : game.players().stream().filter(team::contains).toList();
    }

    private int sideScore(Player player) { return sideMembers(player).stream().mapToInt(game::score).sum(); }

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Your kills: <white>" + game.score(player));
        if (game.mode().isTeams()) lines.add("<gray>Team kills: <white>" + sideScore(player));
        lines.add("<gray>Target: <yellow>" + targetKills());
        game.topScores().stream().limit(3).forEach(e -> {
            Player p = game.player(e.getKey());
            if (p != null) lines.add("<gray>" + p.getName() + ": <white>" + e.getValue());
        });
        return lines;
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public boolean respawnable() { return true; }

    @Override
    public int timeLimitSeconds() { return 600; }
}
