package uk.co.playerready.pulsegames.games.bedwars;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BedwarsBotResourcesTest {
    static final class Fixture {
        final GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS); final Player bot=mock(Player.class);
        final Item drop=mock(Item.class); final World world=mock(World.class); final PlayerInventory bag=mock(PlayerInventory.class);
        Fixture() {
            when(game.state()).thenReturn(GameState.RUNNING); when(game.world()).thenReturn(world); when(game.isActive(bot)).thenReturn(true); when(game.isBot(bot)).thenReturn(true);
            when(bot.getWorld()).thenReturn(world); when(drop.getWorld()).thenReturn(world); when(bot.getUniqueId()).thenReturn(UUID.randomUUID());
            when(bot.getLocation()).thenReturn(new Location(world,.5,64,.5)); when(drop.getLocation()).thenReturn(new Location(world,.5,64.5,.5));
            when(drop.isValid()).thenReturn(true); when(drop.canPlayerPickup()).thenReturn(true); when(bot.hasLineOfSight(drop)).thenReturn(true);
            when(drop.getItemStack()).thenAnswer(i->BedwarsPurchaseTest.item(Material.IRON_INGOT,4)); when(bot.getInventory()).thenReturn(bag);
            when(bag.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        }
    }
    @Test void collectsExistingDropAndRemovesOnlyAfterSuccessfulTransfer() {
        var f=new Fixture(); assertEquals(4,BedwarsBotResources.pickup(f.game,f.bot,f.drop)); verify(f.drop).remove();
        verify(f.game.plugin().getServer().getPluginManager()).callEvent(any(EntityPickupItemEvent.class));
    }
    @Test void cancelledPrivateOrRemoteDropIsNeverTransferred() {
        var f=new Fixture(); var manager=f.game.plugin().getServer().getPluginManager();
        doAnswer(i->{((EntityPickupItemEvent)i.getArgument(0)).setCancelled(true);return null;}).when(manager).callEvent(any(EntityPickupItemEvent.class));
        assertEquals(0,BedwarsBotResources.pickup(f.game,f.bot,f.drop)); verify(f.bag,never()).addItem(any(ItemStack.class));
        when(f.drop.getOwner()).thenReturn(UUID.randomUUID()); assertEquals(0,BedwarsBotResources.pickup(f.game,f.bot,f.drop));
        when(f.drop.getOwner()).thenReturn(null); when(f.drop.getLocation()).thenReturn(new Location(f.world,10,64,.5));
        assertEquals(0,BedwarsBotResources.pickup(f.game,f.bot,f.drop)); verify(f.drop,never()).remove();
    }
    @Test void inventoryOverflowStaysInTheOriginalEntity() {
        var f=new Fixture(); when(f.bag.addItem(any(ItemStack.class))).thenAnswer(i->new HashMap<>(Map.of(0,BedwarsPurchaseTest.item(Material.IRON_INGOT,2))));
        assertEquals(2,BedwarsBotResources.pickup(f.game,f.bot,f.drop));
        verify(f.drop).setItemStack(argThat(item->item.getType()==Material.IRON_INGOT && item.getAmount()==2)); verify(f.drop,never()).remove();
    }
}
