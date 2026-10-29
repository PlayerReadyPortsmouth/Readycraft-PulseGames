package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TempBlocksTest {
    private record Fix(Block block, BlockData original, BlockData placed, AtomicReference<Material> type) {}

    private static Fix block(Material placedType) {
        Block b = mock(Block.class);
        BlockData original = mock(BlockData.class), placed = mock(BlockData.class);
        when(placed.getMaterial()).thenReturn(placedType);
        when(b.getBlockData()).thenReturn(original);
        AtomicReference<Material> type = new AtomicReference<>(Material.AIR);
        when(b.getType()).thenAnswer(i -> type.get());
        doAnswer(i -> { type.set(((BlockData) i.getArgument(0)) == placed ? placedType : Material.AIR); return null; })
                .when(b).setBlockData(any(BlockData.class), eq(false));
        return new Fix(b, original, placed, type);
    }

    @Test void blocksMeltAtTheirTimeAndNotBefore() {
        List<Block> gone = new ArrayList<>();
        TempBlocks temp = new TempBlocks(gone::add);
        Fix ice = block(Material.PACKED_ICE);
        temp.place(ice.block(), ice.placed(), 45);
        temp.expire(44);
        assertEquals(1, temp.size());
        assertEquals(Material.PACKED_ICE, ice.type().get());
        temp.expire(45);
        assertEquals(0, temp.size());
        assertEquals(Material.AIR, ice.type().get());
        assertEquals(List.of(ice.block()), gone);
    }

    @Test void clearAtMatchEndRestoresEverything() {
        List<Block> gone = new ArrayList<>();
        TempBlocks temp = new TempBlocks(gone::add);
        Fix a = block(Material.WHITE_WOOL), b = block(Material.HONEY_BLOCK);
        temp.place(a.block(), a.placed(), 999);
        temp.place(b.block(), b.placed(), 999);
        temp.clear();
        assertEquals(0, temp.size());
        assertEquals(Material.AIR, a.type().get());
        assertEquals(Material.AIR, b.type().get());
        assertEquals(2, gone.size());
    }

    @Test void aBlockSomeoneElseChangedIsLeftAlone() {
        TempBlocks temp = new TempBlocks(x -> { });
        Fix wall = block(Material.WHITE_WOOL);
        temp.place(wall.block(), wall.placed(), 30);
        wall.type().set(Material.RED_WOOL);
        temp.expire(30);
        verify(wall.block(), times(1)).setBlockData(any(BlockData.class), eq(false));
    }
}
