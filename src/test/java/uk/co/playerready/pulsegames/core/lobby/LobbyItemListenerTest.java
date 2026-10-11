package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LobbyItemListenerTest {
    private LobbyService lobby;
    private LobbyItemListener listener;
    private Player player;
    private PlayerInventory inventory;
    private ItemStack control;
    @BeforeEach void setup() {
        lobby = mock(LobbyService.class); listener = new LobbyItemListener(lobby);
        player = mock(Player.class); inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        control = mock(ItemStack.class); when(lobby.isMenuItem(control)).thenReturn(true);
    }
    private InventoryClickEvent click() {
        var event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player); when(event.getHotbarButton()).thenReturn(-1);
        return event;
    }
    @Test void blocksPickingUpAndShiftClickingControls() {
        for (ClickType type : new ClickType[]{ClickType.LEFT, ClickType.SHIFT_LEFT, ClickType.DROP, ClickType.MIDDLE}) {
            var e=click(); when(e.getClick()).thenReturn(type); when(e.getCurrentItem()).thenReturn(control);
            listener.onClick(e); verify(e).setCancelled(true);
        }
    }
    @Test void blocksNumberKeySwapsFromTheReservedHotbarSlot() {
        var e=click(); when(e.getHotbarButton()).thenReturn(4); when(inventory.getItem(4)).thenReturn(control);
        listener.onClick(e); verify(e).setCancelled(true);
    }
    @Test void blocksOffhandSwapInsideInventory() {
        var e=click(); when(e.getClick()).thenReturn(ClickType.SWAP_OFFHAND); when(inventory.getItemInOffHand()).thenReturn(control);
        listener.onClick(e); verify(e).setCancelled(true);
    }
    @Test void blocksCreativeReplacementOfControlSlot() {
        var e=mock(InventoryCreativeEvent.class); when(e.getWhoClicked()).thenReturn(player);
        when(e.getHotbarButton()).thenReturn(-1); when(e.getCurrentItem()).thenReturn(control);
        listener.onClick(e); verify(e).setCancelled(true);
    }
    @Test void blocksMovingAControlOnTheCursor() {
        var e=click(); when(e.getCursor()).thenReturn(control); listener.onClick(e); verify(e).setCancelled(true);
    }
    @Test void blocksDraggingOntoAControlSlot() {
        var e=mock(InventoryDragEvent.class); var view=mock(InventoryView.class);
        when(e.getView()).thenReturn(view); when(e.getRawSlots()).thenReturn(Set.of(40)); when(view.getItem(40)).thenReturn(control);
        listener.onDrag(e); verify(e).setCancelled(true);
    }
    @Test void blocksDraggingControlFromCursor() {
        var e=mock(InventoryDragEvent.class); when(e.getOldCursor()).thenReturn(control);
        listener.onDrag(e); verify(e).setCancelled(true);
    }
    @Test void blocksWorldDropAndHandSwap() {
        var drop=mock(PlayerDropItemEvent.class); var entity=mock(Item.class);
        when(drop.getItemDrop()).thenReturn(entity); when(entity.getItemStack()).thenReturn(control);
        listener.onDrop(drop); verify(drop).setCancelled(true);
        var swap=mock(PlayerSwapHandItemsEvent.class); when(swap.getOffHandItem()).thenReturn(control);
        listener.onSwap(swap); verify(swap).setCancelled(true);
    }
    @Test void allowsOrdinaryBuildingItems() {
        var e=click(); when(e.getCurrentItem()).thenReturn(mock(ItemStack.class));
        listener.onClick(e); verify(e,never()).setCancelled(anyBoolean());
    }
    @Test void protectsShopAsWellAsGameMenu() {
        when(lobby.isMenuItem(control)).thenReturn(false); when(lobby.isShopItem(control)).thenReturn(true);
        var e=click(); when(e.getCurrentItem()).thenReturn(control); listener.onClick(e); verify(e).setCancelled(true);
    }
}
