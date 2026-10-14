package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotNavigationHintsTest {
    private static final class Fixture {
        final World world = mock(World.class);
        final WorldBorder border = mock(WorldBorder.class);
        final GameInstance game = mock(GameInstance.class);
        final Arena arena = mock(Arena.class);
        final MemoryConfiguration config = new MemoryConfiguration();
        Fixture() {
            when(game.world()).thenReturn(world); when(game.arena()).thenReturn(arena); when(arena.settings()).thenReturn(config);
            when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
            when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
                int x = call.getArgument(0), y = call.getArgument(1), z = call.getArgument(2);
                Block block = mock(Block.class); boolean ground = y == 63;
                when(block.isPassable()).thenReturn(!ground); when(block.getType()).thenReturn(ground ? Material.STONE : Material.AIR);
                when(block.getBoundingBox()).thenReturn(new BoundingBox(x,y,z,x+1,y+1,z+1)); return block;
            });
        }
        void points(String... entries) { config.set("bot-patrol-points", List.of(entries)); }
    }
    @Test void keepsValidOrderAndSkipsMalformedNonFiniteAndUnsupportedEntries() {
        var f = new Fixture(); f.points("1.5,64,2.5", "broken", "NaN,64,0", "2.5,65,2.5", "3.5,64,2.5,90,0");
        var result = BotNavigationHints.points(f.game, "bot-patrol-points");
        assertEquals(2, result.size()); assertEquals(1.5, result.getFirst().getX()); assertEquals(90, result.getLast().getYaw());
        assertSame(f.world, result.getFirst().getWorld());
    }
    @Test void doesNotAccessBlocksForUnloadedFootprintEvenAcrossChunkBoundary() {
        var f = new Fixture(); f.points("15.9,64,2.5"); when(f.world.isChunkLoaded(1,0)).thenReturn(false);
        assertTrue(BotNavigationHints.points(f.game, "bot-patrol-points").isEmpty());
        verify(f.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(f.world, never()).getChunkAt(anyInt(), anyInt());
    }
    @Test void rejectsWorldBorderAndHeightWithoutBlockAccess() {
        var f = new Fixture(); f.points("1.5,320,2.5", "1.5,-64,2.5", "1.5,64,2.5");
        when(f.border.isInside(any())).thenReturn(false);
        assertTrue(BotNavigationHints.points(f.game, "bot-patrol-points").isEmpty());
        verify(f.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }
    @Test void blockedHeadLiquidAndHazardousGroundAreRejected() {
        var f = new Fixture(); f.points("1.5,64,2.5");
        Block obstacle = mock(Block.class); when(obstacle.getType()).thenReturn(Material.STONE);
        when(obstacle.getBoundingBox()).thenReturn(new BoundingBox(1,65,2,2,66,3));
        when(f.world.getBlockAt(1,65,2)).thenReturn(obstacle);
        assertTrue(BotNavigationHints.points(f.game, "bot-patrol-points").isEmpty());
        when(obstacle.isPassable()).thenReturn(true); when(obstacle.isLiquid()).thenReturn(true);
        assertTrue(BotNavigationHints.points(f.game, "bot-patrol-points").isEmpty());
        when(obstacle.isLiquid()).thenReturn(false); when(obstacle.getType()).thenReturn(Material.FIRE);
        assertTrue(BotNavigationHints.points(f.game, "bot-patrol-points").isEmpty());
        when(obstacle.getType()).thenReturn(Material.AIR);
        Block magma = mock(Block.class); when(magma.getType()).thenReturn(Material.MAGMA_BLOCK);
        when(magma.getBoundingBox()).thenReturn(new BoundingBox(1,63,2,2,64,3));
        when(f.world.getBlockAt(1,63,2)).thenReturn(magma);
        assertTrue(BotNavigationHints.points(f.game, "bot-patrol-points").isEmpty());
    }
    @Test void limitsWorkAndOnlyReadsDocumentedHintKeys() {
        var f = new Fixture(); f.config.set("bot-patrol-points", Collections.nCopies(150,"1.5,64,2.5"));
        assertEquals(128, BotNavigationHints.points(f.game,"bot-patrol-points").size());
        f.config.set("spawns", List.of("1.5,64,2.5"));
        assertTrue(BotNavigationHints.points(f.game,"spawns").isEmpty());
        f.config.set("bot-route-courtyard", List.of("1.5,64,2.5"));
        assertEquals(1, BotNavigationHints.points(f.game,"bot-route-courtyard").size());
    }
    @Test void thinOrHollowSupportDoesNotQualifyAsSafeHint() {
        var f = new Fixture(); f.points("1.5,64,2.5");
        Block floor = mock(Block.class);
        when(floor.getType()).thenReturn(Material.OAK_TRAPDOOR);
        when(floor.getBoundingBox()).thenReturn(new BoundingBox(1,63,2,2,64,3));
        when(f.world.getBlockAt(1,63,2)).thenReturn(floor);
        assertTrue(BotNavigationHints.points(f.game,"bot-patrol-points").isEmpty());
        when(floor.getType()).thenReturn(Material.OAK_STAIRS);
        assertTrue(BotNavigationHints.points(f.game,"bot-patrol-points").isEmpty());
    }

    @Test void nearestUsesInstanceWorldRejectsWrongWorldAndRevalidatesChangedTerrain() {
        var f = new Fixture(); f.points("1.5,64,2.5", "9.5,64,2.5");
        assertEquals(9.5, BotNavigationHints.nearestSafe(f.game,new Location(f.world,8,64,2),"bot-patrol-points").getX());
        assertNull(BotNavigationHints.nearestSafe(f.game,new Location(mock(World.class),8,64,2),"bot-patrol-points"));
        when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertNull(BotNavigationHints.nearestSafe(f.game,new Location(f.world,8,64,2),"bot-patrol-points"));
    }
}
