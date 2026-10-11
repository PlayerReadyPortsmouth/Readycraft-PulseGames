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
public final class AvalancheGame extends MiniGame implements uk.co.playerready.pulsegames.core.bot.BotMovement {

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
    public Location botDestination(Player bot) {
        if (area == null || !bot.getWorld().equals(game.world())) return null;
        Location here = bot.getLocation();
        // Only currently visible falling entities, never the random next-wave positions.
        var threats = bot.getWorld().getNearbyEntities(here, 12, 24, 12).stream()
                .filter(entity -> entity instanceof org.bukkit.entity.FallingBlock && entity.isValid())
                .filter(entity -> entity.getLocation().getY() > here.getY() - 1 && bot.hasLineOfSight(entity))
                .limit(128).map(entity -> entity.getLocation()).toList();
        if (threats.isEmpty()) return null;
        return uk.co.playerready.pulsegames.core.bot.GroundNavigation.nextStep(here, area::containsColumn,
                point -> threats.stream().mapToDouble(threat -> {
                    double dx = point.getX() - threat.getX(), dz = point.getZ() - threat.getZ();
                    return -100.0 / Math.max(0.25, dx * dx + dz * dz);
                }).sum());
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
