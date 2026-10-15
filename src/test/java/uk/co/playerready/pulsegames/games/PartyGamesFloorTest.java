package uk.co.playerready.pulsegames.games;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.games.partygames.PartyGamesGame;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartyGamesFloorTest {
    private record Fixture(GameInstance game, PartyGamesGame logic, Block block, BlockData original) {}
    private Fixture fixture() {
        var game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.arena().region("floor")).thenReturn(new Cuboid(0, 64, 0, 0, 64, 0));
        when(game.alivePlayers()).thenReturn(List.of());
        var block = mock(Block.class);
        var data = mock(BlockData.class);
        when(data.clone()).thenReturn(data);
        when(block.getBlockData()).thenReturn(data);
        when(game.world().getBlockAt(0, 64, 0)).thenReturn(block);
        return new Fixture(game, new PartyGamesGame(game), block, data);
    }
    private void field(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    private Object get(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private void tick(PartyGamesGame logic) throws Exception {
        var method = PartyGamesGame.class.getDeclaredMethod("tickColorRush");
        method.setAccessible(true); method.invoke(logic);
    }
    @Test void tinyFloorCallsOnlyPresentColour() throws Exception {
        var f = fixture(); field(f.logic, "microStep", 1);
        when(f.block.getType()).thenReturn(Material.CYAN_WOOL); // Not in this game's colour pool.
        tick(f.logic); assertNull(get(f.logic, "targetColor"));
        f = fixture(); field(f.logic, "microStep", 1);
        when(f.block.getType()).thenReturn(Material.BLUE_WOOL);
        tick(f.logic); assertEquals(Material.BLUE_WOOL, get(f.logic, "targetColor"));
    }
    @Test void finalColourRoundRestoresAuthoredFloor() throws Exception {
        var f = fixture(); tick(f.logic); // Capture before shuffle.
        field(f.logic, "microStep", 25); tick(f.logic);
        verify(f.block).setBlockData(f.original, false);
    }
    @Test void forcedEndAndCleanupRestoreOnce() throws Exception {
        var f = fixture(); tick(f.logic);
        f.logic.onEnd(List.of()); f.logic.onCleanup();
        verify(f.block, times(1)).setBlockData(f.original, false);
    }
    @Test void cleanupDuringAnActiveColourRoundRestoresFloor() throws Exception {
        var f = fixture(); tick(f.logic); f.logic.onCleanup();
        verify(f.block).setBlockData(f.original, false);
    }
}
