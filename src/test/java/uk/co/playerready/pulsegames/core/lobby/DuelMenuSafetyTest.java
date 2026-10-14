package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DuelMenuSafetyTest {
    @Test void everyLoadoutHasReachablePageAndOutdatedPageClamps() {
        assertEquals(0, DuelMenu.loadoutPage(0, 0));
        assertEquals(1, DuelMenu.loadoutPage(1, 19));
        assertEquals(2, DuelMenu.loadoutPage(99, 37));
        assertEquals(0, DuelMenu.loadoutPage(2, 4));
    }
    @Test void deferredClickCannotActAfterTheMenuIsReplaced() {
        var plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        var menu = new DuelMenu(plugin);
        Player player = mock(Player.class); UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true);
        var holder = new DuelMenu.Screen(id); holder.inventory = mock(Inventory.class);
        when(holder.inventory.getHolder()).thenReturn(holder);
        var view = mock(InventoryView.class); when(view.getTopInventory()).thenReturn(holder.inventory);
        when(player.getOpenInventory()).thenReturn(view);
        var event = mock(InventoryClickEvent.class); when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player); when(event.getClickedInventory()).thenReturn(holder.inventory);
        @SuppressWarnings("unchecked") Consumer<Player> action = mock(Consumer.class); holder.actions.put(0, action);
        menu.onClick(event);
        verify(event).setCancelled(true);
        var scheduled = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(plugin.getServer().getScheduler()).runTask(eq(plugin), scheduled.capture());
        when(view.getTopInventory()).thenReturn(mock(Inventory.class));
        scheduled.getValue().run();
        verifyNoInteractions(action);
    }
}
