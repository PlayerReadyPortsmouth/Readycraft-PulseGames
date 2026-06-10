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

    private boolean isOitc() { return game.mode().id().equals("oitc"); }

    private int targetKills() {
        return game.arena().settings().getInt("target-kills", isOitc() ? 20 : 15);
    }

    @Override
    public void onStart() {
        game.alivePlayers().forEach(this::equip);
        game.broadcast("First to <yellow>" + targetKills() + "</yellow> kills wins!");
    }

    private void equip(Player player) {
        if (isOitc()) {
            player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
            player.getInventory().setItem(1, new ItemStack(Material.BOW));
            player.getInventory().setItem(9, new ItemStack(Material.ARROW, 1));
        } else {
            Kit kit = game.plugin().kits().get(game.arena().settings().getString("kit", "warrior"));
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
            if (game.score(killer) >= targetKills()) {
                game.end(List.of(killer), "kills");
                return;
            }
        } else {
            game.broadcast("<red>" + victim.getName() + "</red> died");
        }
        game.respawn(victim, respawnDelaySeconds());
    }

    @Override
    public void onTimeUp() {
        var top = game.topScores();
        if (top.isEmpty()) {
            game.end(List.of(), "time");
            return;
        }
        Player best = game.plugin().getServer().getPlayer(top.get(0).getKey());
        game.end(best == null ? List.of() : List.of(best), "time");
    }

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Your kills: <white>" + game.score(player));
        lines.add("<gray>Target: <yellow>" + targetKills());
        game.topScores().stream().limit(3).forEach(e -> {
            Player p = game.plugin().getServer().getPlayer(e.getKey());
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
