package uk.co.playerready.pulsegames.games.luckypillars;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import java.util.*;
import static org.mockito.Mockito.*;

class LuckyOverflowTest {
    @Test void dropsOnlyUninsertedGeneratedRewardBesideActivePlayer() {
        var game=mock(GameInstance.class);var p=mock(Player.class);World world=mock(World.class);PlayerInventory bag=mock(PlayerInventory.class);ItemStack granted=mock(ItemStack.class),leftover=mock(ItemStack.class);Item dropped=mock(Item.class);
        when(game.world()).thenReturn(world);when(game.isActive(p)).thenReturn(true);when(p.getWorld()).thenReturn(world);when(p.getLocation()).thenReturn(new Location(world,4,70,9));when(p.getInventory()).thenReturn(bag);
        when(bag.addItem(granted)).thenReturn(new HashMap<>(Map.of(0,leftover)));when(world.dropItem(any(Location.class),eq(leftover))).thenReturn(dropped);
        new LuckyPillarsGame(game).give(p,granted,"blocks");verify(world).dropItem(eq(new Location(world,4,70,9)),same(leftover));verify(dropped).setPickupDelay(10);verify(bag,never()).clear();
    }
    @Test void departedOrDifferentWorldPlayerCannotReceiveOrLeakReward() {
        var game=mock(GameInstance.class);var p=mock(Player.class);World instance=mock(World.class),other=mock(World.class);when(game.world()).thenReturn(instance);when(game.isActive(p)).thenReturn(true);when(p.getWorld()).thenReturn(other);
        new LuckyPillarsGame(game).give(p,mock(ItemStack.class),"blocks");verify(p,never()).getInventory();verifyNoInteractions(instance,other);
    }
}
