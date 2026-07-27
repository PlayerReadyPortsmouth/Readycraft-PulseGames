package uk.co.playerready.pulsegames.games.volcano;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Volcano: the lava rises - climb for your life!
 * Modes: classic, eruption (faster lava + flaming rocks).
 */
public final class VolcanoGame extends MiniGame {

    /** Block writes per tick while a layer floods; a whole plane in one tick stalls the server. */
    private static final int BLOCKS_PER_TICK = 1024;

    private Cuboid area;
    private int lavaY;
    private boolean flooding;

    public VolcanoGame(GameInstance game) {
        super(game);
    }

    private boolean isEruption() { return game.mode().id().equals("eruption"); }

    private int lavaInterval() {
        return game.arena().settings().getInt("lava-interval", isEruption() ? 5 : 8);
    }

    @Override
    public void onStart() {
        area = game.arena().region("arena");
        lavaY = area != null ? area.minY() : 0;
        game.broadcast("<red>The volcano is erupting - climb above the lava!");
    }

    @Override
    public void onSecond(int gameTime) {
        if (area == null) return;
        if (!flooding && gameTime > 10 && gameTime % lavaInterval() == 0 && lavaY <= area.maxY()) {
            flooding = true;
            floodSlice(lavaY, area.minX());
            lavaY++;
            game.broadcast("<red>The lava rises! <gray>(Y=" + lavaY + ")");
        }
        if (isEruption() && gameTime > 15 && gameTime % 3 == 0) {
            var random = ThreadLocalRandom.current();
            double x = random.nextInt(area.minX(), area.maxX() + 1) + 0.5;
            double z = random.nextInt(area.minZ(), area.maxZ() + 1) + 0.5;
            var rock = game.world().spawnFallingBlock(
                    new Location(game.world(), x, area.maxY(), z), Material.MAGMA_BLOCK.createBlockData());
            rock.setDropItem(false);
            rock.setHurtEntities(true);
        }
    }

    /** Writes one budgeted strip of the layer, then queues the next strip a tick later. */
    private void floodSlice(int y, int fromX) {
        int depth = area.maxZ() - area.minZ() + 1;
        int columns = Math.max(1, BLOCKS_PER_TICK / depth);
        int toX = Math.min(area.maxX(), fromX + columns - 1);
        for (int x = fromX; x <= toX; x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                Block block = game.world().getBlockAt(x, y, z);
                if (block.getType().isAir() || block.getType() == Material.WATER) {
                    block.setType(Material.LAVA);
                }
            }
        }
        if (toX >= area.maxX()) {
            flooding = false;
            return;
        }
        game.runLater(1L, () -> {
            if (game.world() == null) return;
            floodSlice(y, toX + 1);
        });
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent event) {
        switch (event.getCause()) {
            case LAVA, FIRE, FIRE_TICK -> {
                event.setCancelled(true);
                game.broadcast("<red>" + victim.getName() + "</red> was swallowed by the lava!");
                game.eliminate(victim);
            }
            case FALLING_BLOCK -> {
                event.setCancelled(true);
                game.broadcast("<red>" + victim.getName() + "</red> was crushed by a volcanic rock!");
                game.eliminate(victim);
            }
            default -> { }
        }
    }

    @Override
    public boolean fallDamage() { return false; }

    @Override
    public List<String> sidebar(Player player) {
        return List.of(
                "<gray>Lava level: <red>Y=" + lavaY,
                "<gray>Alive: <white>" + game.alivePlayers().size());
    }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("survive-seconds", 300);
    }
}
