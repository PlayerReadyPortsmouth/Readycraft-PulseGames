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
public final class VolcanoGame extends MiniGame implements uk.co.playerready.pulsegames.core.bot.BotMovement {

    /** Block writes per tick while a layer floods; a whole plane in one tick stalls the server. */
    private static final int BLOCKS_PER_TICK = 1024;

    private final uk.co.playerready.pulsegames.core.bot.SurfaceMemory memory = new uk.co.playerready.pulsegames.core.bot.SurfaceMemory();
    private Cuboid area;
    private int lavaY;
    private boolean flooding;
    private int nextRiseAt;
    private boolean closed;
    private long floodGeneration;

    public VolcanoGame(GameInstance game) {
        super(game);
    }

    private boolean isEruption() { return game.mode().id().equals("eruption"); }

    static int riseInterval(int initial, int accelerationSeconds, int elapsed) {
        return Math.max(1, Math.max(1, initial) - Math.max(0, elapsed) / Math.max(1, accelerationSeconds));
    }

    private int lavaInterval(int elapsed) {
        return riseInterval(game.arena().settings().getInt("lava-interval", isEruption() ? 3 : 4),
                game.arena().settings().getInt("lava-acceleration-seconds", 30), elapsed);
    }

    @Override
    public void onStart() {
        closed = false;
        floodGeneration++;
        area = game.arena().region("arena");
        flooding = false;
        lavaY = area != null ? Math.max(area.minY(), Math.min(area.maxY(),
                game.arena().settings().getInt("lava-start-y", area.minY()))) : 0;
        nextRiseAt = Math.max(1, game.arena().settings().getInt("lava-grace-seconds", isEruption() ? 3 : 5));
        game.broadcast("<red>The volcano is erupting - climb above the lava!");
    }

    @Override
    public void onSecond(int gameTime) {
        if (closed || area == null) return;
        if (!flooding && gameTime >= nextRiseAt && lavaY <= area.maxY()) {
            nextRiseAt = gameTime + lavaInterval(gameTime);
            flooding = true;
            floodSlice(lavaY, 0, floodGeneration);
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

    /** Budget scanned cells, not rows: even a very long thin region cannot exceed this tick's limit. */
    private void floodSlice(int y, long cursor, long generation) {
        if (closed || generation != floodGeneration || game.world() == null) return;
        long depth = (long) area.maxZ() - area.minZ() + 1;
        long cells = ((long) area.maxX() - area.minX() + 1) * depth;
        long end = Math.min(cells, cursor + BLOCKS_PER_TICK);
        for (long cell = cursor; cell < end; cell++) {
            int x = (int) (area.minX() + cell / depth);
            int z = (int) (area.minZ() + cell % depth);
            Block block = game.world().getBlockAt(x, y, z);
            if (block.getType().isAir() || block.getType() == Material.WATER) block.setType(Material.LAVA);
        }
        if (end >= cells) { flooding = false; return; }
        game.runLater(1L, () -> floodSlice(y, end, generation));
    }

    @Override
    public Location botDestination(Player bot) {
        if (area == null || !bot.getWorld().equals(game.world())) return null;
        memory.remember(bot);
        Location here = bot.getLocation();
        var rocks = game.world().getNearbyEntities(here, 12, 24, 12).stream()
                .filter(e -> e instanceof org.bukkit.entity.FallingBlock && e.isValid())
                .filter(e -> e.getLocation().getY() > here.getY() - 1 && bot.hasLineOfSight(e))
                .limit(128).map(e -> e.getLocation()).toList();
        return memory.nextStep(bot, area::contains,
                p -> p.getY() * 12 - memory.penalty(bot, p) - rocks.stream().mapToDouble(rock -> {
                    double dx = p.getX() - rock.getX(), dz = p.getZ() - rock.getZ();
                    return 100.0 / Math.max(0.25, dx * dx + dz * dz);
                }).sum());
    }
    @Override public void onCleanup() { closed = true; floodGeneration++; flooding = false; memory.clear(); }

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
