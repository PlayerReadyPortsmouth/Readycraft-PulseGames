package uk.co.playerready.pulsegames.games.common;

import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionSnapshotTest {
    /** A block whose data can be changed, with the original state recorded. */
    static class Cell {
        final Block block = mock(Block.class);
        final BlockData original = mock(BlockData.class), now = mock(BlockData.class);
        BlockData current;
        Cell(boolean broken) {
            when(original.clone()).thenReturn(original);
            current = broken ? now : original;
            when(block.getBlockData()).thenAnswer(i -> current);
            doAnswer(i -> { current = i.getArgument(0); return null; }).when(block).setBlockData(any(BlockData.class), eq(false));
        }
    }

    @Test void restoresChangedBlocksAndLeavesUntouchedOnesAlone() {
        var intact = new Cell(false);
        var broken = new Cell(false);
        var snapshot = RegionSnapshot.empty();
        snapshot.remember(intact.block);
        snapshot.remember(broken.block);
        broken.current = broken.now;   // the game breaks it after the snapshot
        snapshot.restoreAll();
        assertSame(broken.original, broken.current);
        verify(intact.block, never()).setBlockData(any(BlockData.class), anyBoolean());
        assertEquals(2, snapshot.size());
    }

    @Test void restoreIsBudgetedPerStepAndFirstRememberedStateWins() {
        var cells = new Cell[5];
        var snapshot = RegionSnapshot.empty();
        for (int i = 0; i < cells.length; i++) { cells[i] = new Cell(false); snapshot.remember(cells[i].block); }
        for (var cell : cells) cell.current = cell.now;
        snapshot.remember(cells[0].block);   // second remember must not overwrite the original with the broken state
        snapshot.beginRestore();
        assertFalse(snapshot.restoreStep(2));
        assertSame(cells[1].original, cells[1].current);
        assertSame(cells[2].now, cells[2].current);
        assertFalse(snapshot.restoreStep(2));
        assertTrue(snapshot.restoreStep(2));
        for (var cell : cells) assertSame(cell.original, cell.current);
    }
}
