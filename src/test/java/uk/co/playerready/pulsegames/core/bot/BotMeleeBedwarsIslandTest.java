package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsGame;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Bedwars strafing through the shared BotMelee never picks a lateral tile off the island. */
class BotMeleeBedwarsIslandTest {
    /** A 6-wide strip island along z: stone floor at y 63 for x in [-2, 3], void everywhere else. */
    private static World island() {
        World world = mock(World.class); WorldBorder border = mock(WorldBorder.class);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(i -> {
            int x = i.getArgument(0), y = i.getArgument(1), z = i.getArgument(2);
            Material type = y == 63 && x >= -2 && x <= 3 ? Material.STONE : Material.AIR;
            Block b = mock(Block.class); when(b.getType()).thenReturn(type);
            when(b.isPassable()).thenReturn(type == Material.AIR);
            when(b.getBoundingBox()).thenReturn(new BoundingBox(x, y, z, x + 1, y + 1, z + 1));
            when(b.getBlockData()).thenReturn(null);
            return b;
        });
        return world;
    }

    private static boolean onIsland(Location at) { return at.getX() - .29 >= -2 && at.getX() + .29 <= 4; }

    @Test void predicateRejectsVoidAndOverhangingTiles() {
        World world = island();
        Predicate<Location> safe = BedwarsGame.botStrafeSafe(new Location(world, .5, 64, .5));
        assertTrue(safe.test(new Location(world, .5, 64, 3.5)));
        assertTrue(safe.test(new Location(world, -.5, 64, 2.5)));
        assertFalse(safe.test(new Location(world, 4.5, 64, .5)), "void to the east");
        assertFalse(safe.test(new Location(world, -3.5, 64, .5)), "void to the west");
        assertFalse(safe.test(new Location(world, 3.9, 64, .5)), "footprint overhangs the edge");
    }

    @Test void strafeOnAnIslandNeverChoosesAnUnsafeLateralTile() {
        World world = island();
        // The enemy is straight along the strip, so every strafe is lateral, towards the void on either side.
        Location bot = new Location(world, .5, 64, .5), enemy = new Location(world, .5, 64, 3.5);
        Predicate<Location> safe = BedwarsGame.botStrafeSafe(bot);
        int chosen = 0, offered = 0;
        for (long seed = 0; seed < 200; seed++) {
            AtomicLong clock = new AtomicLong();
            var melee = new BotMelee(new UUID(seed, seed * 31), new Random(seed), clock::get);
            for (int t = 0; t < 120; t += 7) {
                clock.set(t);
                for (BotDifficulty d : BotDifficulty.values()) for (boolean ranged : new boolean[]{false, true}) {
                    Location goal = melee.strafeGoal(bot, enemy, d.profile(), safe, ranged);
                    if (goal == null) continue;
                    if (Math.abs(goal.getX() - bot.getX()) >= 1.5) offered++;
                    chosen++;
                    assertTrue(onIsland(goal), "strafe left the island: " + goal);
                    assertTrue(safe.test(goal));
                }
            }
        }
        // Lateral strafes 2-3 blocks out land at x 2.5..3.5 (some on, some off the island) or x -1.5..-2.5.
        assertTrue(offered > 0, "the bot still strafes onto the safe side of the island");
    }
}
