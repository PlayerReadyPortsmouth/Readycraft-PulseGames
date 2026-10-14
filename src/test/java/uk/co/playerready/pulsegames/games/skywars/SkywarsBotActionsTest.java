package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkywarsBotActionsTest {
    @Test void deniedChestOpenDoesNotTransferAndRedirectIsClosed() {
        Player bot=mock(Player.class); Chest chest=mock(Chest.class); Inventory inventory=mock(Inventory.class);
        when(chest.getBlockInventory()).thenReturn(inventory);
        assertEquals(-1,SkywarsGame.lootChest(bot,chest));
        verify(bot,never()).getInventory(); verify(inventory,never()).getItem(anyInt());
        InventoryView redirected=mock(InventoryView.class); when(redirected.getTopInventory()).thenReturn(mock(Inventory.class));
        when(bot.openInventory(inventory)).thenReturn(redirected);
        assertEquals(-1,SkywarsGame.lootChest(bot,chest)); verify(bot).closeInventory();
        verify(inventory,never()).getItem(anyInt());
    }
    @Test void successfulChestOpenTransfersActualItemsAndAlwaysCloses() {
        Player bot=mock(Player.class); PlayerInventory bag=mock(PlayerInventory.class);
        Chest chest=mock(Chest.class); Inventory inventory=mock(Inventory.class); InventoryView view=mock(InventoryView.class);
        when(bot.getServer()).thenReturn(mock(Server.class,RETURNS_DEEP_STUBS));
        when(chest.getBlockInventory()).thenReturn(inventory); when(view.getTopInventory()).thenReturn(inventory);
        when(bot.openInventory(inventory)).thenReturn(view); when(bot.getInventory()).thenReturn(bag);
        when(inventory.getSize()).thenReturn(1); when(inventory.getItem(0)).thenAnswer(i->SkywarsTestItems.stack(Material.ARROW,8));
        when(bag.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        assertEquals(8,SkywarsGame.lootChest(bot,chest)); verify(inventory).setItem(0,null); verify(bot).closeInventory();
    }
    @Test void cancelledExtractionOrEventRemovingSourceDoesNotTransferItems() {
        Player bot=mock(Player.class); PlayerInventory bag=mock(PlayerInventory.class);
        Chest chest=mock(Chest.class); Inventory inventory=mock(Inventory.class); InventoryView view=mock(InventoryView.class);
        Server server=mock(Server.class,RETURNS_DEEP_STUBS); when(bot.getServer()).thenReturn(server);
        when(chest.getBlockInventory()).thenReturn(inventory); when(view.getTopInventory()).thenReturn(inventory);
        when(bot.openInventory(inventory)).thenReturn(view); when(bot.getInventory()).thenReturn(bag);
        when(inventory.getSize()).thenReturn(1);
        AtomicReference<ItemStack> source=new AtomicReference<>(SkywarsTestItems.stack(Material.ARROW,8));
        when(inventory.getItem(0)).thenAnswer(i->source.get());
        var manager=server.getPluginManager();
        doAnswer(i->{((org.bukkit.event.inventory.InventoryClickEvent)i.getArgument(0)).setCancelled(true);return null;})
                .when(manager).callEvent(any(org.bukkit.event.inventory.InventoryClickEvent.class));
        assertEquals(0,SkywarsGame.lootChest(bot,chest)); assertEquals(8,source.get().getAmount());
        doAnswer(i->{source.set(null);return null;}).when(manager).callEvent(any(org.bukkit.event.inventory.InventoryClickEvent.class));
        assertEquals(0,SkywarsGame.lootChest(bot,chest));
        verify(bag,never()).addItem(any(ItemStack.class)); verify(inventory,never()).setItem(anyInt(),any());
        verify(bot,times(2)).closeInventory();
    }
    @Test void acceptedBridgeConsumesExactlyOneExistingBlock() {
        var f=new BridgeFixture();
        assertTrue(f.logic.placeBridge(f.bot,f.here,f.step));
        assertEquals(1,f.contents.get(0).getAmount()); assertEquals(Material.COBBLESTONE,f.placed.get());
        verify(f.before,never()).update(anyBoolean(),anyBoolean()); verify(f.bot).swingMainHand();
        assertFalse(f.logic.placeBridge(f.bot,f.here,f.step)); assertEquals(1,f.contents.get(0).getAmount());
    }
    private static final class BridgeFixture {
        final GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);
        final World world=mock(World.class); final Player bot=mock(Player.class); final PlayerInventory bag=mock(PlayerInventory.class);
        final Block support=mock(Block.class),target=mock(Block.class); final BlockState before=mock(BlockState.class);
        final AtomicReference<Material> placed=new AtomicReference<>(Material.AIR);
        final Map<Integer,ItemStack> contents=new HashMap<>();
        final Location here=new Location(world,.5,64,.5),step=new Location(world,1.5,64,.5);
        final SkywarsGame logic=new SkywarsGame(game);
        BridgeFixture() {
            when(game.world()).thenReturn(world); when(bot.getWorld()).thenReturn(world); when(bot.getUniqueId()).thenReturn(UUID.randomUUID());
            when(game.state()).thenReturn(GameState.RUNNING); when(game.isActive(bot)).thenReturn(true);
            when(game.arena().settings()).thenReturn(new MemoryConfiguration());
            when(bot.getInventory()).thenReturn(bag); when(bot.getEyeLocation()).thenReturn(here.clone().add(0,1.62,0));
            when(world.getBlockAt(0,63,0)).thenReturn(support); when(world.getBlockAt(1,63,0)).thenReturn(target);
            when(support.getY()).thenReturn(63); when(support.getLocation()).thenReturn(new Location(world,0,63,0));
            when(target.getX()).thenReturn(1); when(target.getY()).thenReturn(63); when(target.getLocation()).thenReturn(new Location(world,1,63,0));
            when(target.getType()).thenAnswer(i->placed.get()); when(target.getState()).thenReturn(before);
            doAnswer(i->{placed.set(i.getArgument(0));return null;}).when(target).setType(any(Material.class),eq(false));
            when(bag.getItem(anyInt())).thenAnswer(i->contents.get(i.getArgument(0)));
            doAnswer(i->{contents.put(i.getArgument(0),i.getArgument(1));return null;}).when(bag).setItem(anyInt(),nullable(ItemStack.class));
            contents.put(0,stack(2));
        }
        ItemStack stack(int amount) { return SkywarsTestItems.stack(Material.COBBLESTONE,amount); }
    }
    @Test void cancelledBridgeRollsBackAndDoesNotConsumeInventory() {
        var f=new BridgeFixture();
        var manager=f.game.plugin().getServer().getPluginManager();
        doAnswer(i->{((BlockPlaceEvent)i.getArgument(0)).setCancelled(true);return null;})
                .when(manager).callEvent(any(BlockPlaceEvent.class));
        assertFalse(f.logic.placeBridge(f.bot,f.here,f.step)); verify(f.before).update(true,false);
        assertEquals(2,f.contents.get(0).getAmount()); verify(f.bot,never()).swingMainHand();
    }
    @Test void eventChangingStackQuantityCannotCauseExtraConsumptionOrDuplicatePlacement() {
        var f=new BridgeFixture();
        var manager=f.game.plugin().getServer().getPluginManager();
        doAnswer(i->{f.contents.put(0,f.stack(1));return null;})
                .when(manager).callEvent(any(BlockPlaceEvent.class));
        assertFalse(f.logic.placeBridge(f.bot,f.here,f.step)); verify(f.before).update(true,false);
        assertEquals(1,f.contents.get(0).getAmount()); verify(f.bot,never()).swingMainHand();
    }
    @Test void noMaterialsNeverPlacesBlockOrRaisesPlacementEvent() {
        var f=new BridgeFixture(); f.contents.clear();
        assertFalse(f.logic.placeBridge(f.bot,f.here,f.step)); verify(f.target,never()).setType(any(Material.class),anyBoolean());
        verify(f.game.plugin().getServer().getPluginManager(),never()).callEvent(any(BlockPlaceEvent.class));
    }
}
