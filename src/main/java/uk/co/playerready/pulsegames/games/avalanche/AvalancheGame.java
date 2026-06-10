package uk.co.playerready.pulsegames.games.avalanche;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Avalanche: blocks rain from the sky and pile up - dodge or be buried.
 * One hit and you're out. Survive to the end (or be the last standing).
 */
public final class AvalancheGame extends MiniGame {

    private Cuboid area;

    public AvalancheGame(GameInstance game) {
        super(game);
    }

    @Override
    public void onStart() {
        area = game.arena().region("arena");
        game.broadcast("<yellow>Incoming avalanche! Dodge the falling blocks!");
    }

    @Override
    public void onSecond(int gameTime) {
        if (area == null || gameTime < 5) return;
        int intensity = Math.min(2 + gameTime / 10, 14);
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < intensity; i++) {
            double x = random.nextInt(area.minX(), area.maxX() + 1) + 0.5;
            double z = random.nextInt(area.minZ(), area.maxZ() + 1) + 0.5;
            Location spawn = new Location(game.world(), x, area.maxY(), z);
            Material material = random.nextInt(5) == 0 ? Material.ANVIL : Material.SNOW_BLOCK;
            var falling = game.world().spawnFallingBlock(spawn, material.createBlockData());
            falling.setDropItem(false);
            falling.setHurtEntities(true);
        }
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALLING_BLOCK
                || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            event.setCancelled(true);
            game.broadcast("<red>" + victim.getName() + "</red> was buried by the avalanche!");
            game.eliminate(victim);
        }
    }

    /** Everyone still alive at the end survives together. */
    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("survive-seconds", 180);
    }
}
