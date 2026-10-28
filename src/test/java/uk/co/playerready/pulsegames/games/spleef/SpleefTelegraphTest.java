package uk.co.playerready.pulsegames.games.spleef;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameMode;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpleefTelegraphTest {
    @Test void decayCracksABlockOneSecondBeforeItVanishes() throws Exception {
        var game = mock(GameInstance.class);
        when(game.mode()).thenReturn(GameMode.ffa("decay", "Decay", 2, 8));
        World world = mock(World.class);
        List<Block> floor = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Block b = mock(Block.class);
            when(b.isEmpty()).thenReturn(false);
            when(b.getWorld()).thenReturn(world);
            when(b.getLocation()).thenAnswer(x -> new Location(world, 1, 64, 1));
            when(b.getBlockData()).thenReturn(mock(BlockData.class));
            floor.add(b);
        }
        var marked = List.copyOf(floor);
        var logic = new SpleefGame(game, bound -> 0);
        var f = SpleefGame.class.getDeclaredField("floorBlocks");
        f.setAccessible(true);
        f.set(logic, floor);

        logic.onSecond(30);                                  // 5 blocks picked: warned, still solid
        for (Block b : marked) verify(b, never()).setType(Material.AIR);
        verify(world, times(5)).playSound(any(Location.class), eq("minecraft:block.snow.step"), anyFloat(), anyFloat());

        logic.onSecond(31);                                  // a second later they go
        for (Block b : marked) verify(b, times(1)).setType(Material.AIR);
    }
}
