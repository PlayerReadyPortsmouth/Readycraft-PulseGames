package uk.co.playerready.pulsegames.core.setup;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArenaSetupMenuTest {
    PulseGamesPlugin plugin;
    ArenaSetupManager manager;
    ArenaSetupMenu menu;
    Player player;
    ArenaSetupManager.Session session;
    ArenaSetupMenu.Holder holder;
    InventoryView view;
    Runnable action;

    @BeforeEach void setup() {
        plugin = mock(PulseGamesPlugin.class);
        Server server = mock(Server.class);
        when(plugin.getName()).thenReturn("PulseGames");
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        manager = mock(ArenaSetupManager.class);
        menu = new ArenaSetupMenu(plugin, manager);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("pulsegames.admin")).thenReturn(true);
        session = new ArenaSetupManager.Session(null, "test", null);
        when(manager.session(player)).thenReturn(session);
        holder = new ArenaSetupMenu.Holder(player, session);
        holder.inventory = mock(Inventory.class);
        when(holder.inventory.getHolder()).thenReturn(holder);
        view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(holder.inventory);
        when(player.getOpenInventory()).thenReturn(view);
        action = mock(Runnable.class);
        holder.actions.put(0, action);
    }
    private void click(int slot, Runnable beforeDeferred) {
        var event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> { beforeDeferred.run(); ((Runnable) invocation.getArgument(1)).run(); return null; })
                    .when(scheduler).runTask(eq(plugin), any(Runnable.class));
            menu.onClick(event);
        }
        verify(event).setCancelled(true);
    }
    @Test void executesCurrentOwnersButton() { click(0, () -> {}); verify(action).run(); }
    @Test void ignoresBottomInventoryAndOutsideClicks() { click(54, () -> {}); click(-999, () -> {}); verifyNoInteractions(action); }
    @Test void rejectsPermissionRevokedBeforeScheduledAction() {
        click(0, () -> when(player.hasPermission("pulsegames.admin")).thenReturn(false)); verifyNoInteractions(action);
    }
    @Test void rejectsReplacedSession() {
        click(0, () -> when(manager.session(player)).thenReturn(new ArenaSetupManager.Session(null, "other", null)));
        verifyNoInteractions(action);
    }
    @Test void rejectsClosedMenu() {
        click(0, () -> when(view.getTopInventory()).thenReturn(mock(Inventory.class))); verifyNoInteractions(action);
    }
    @Test void rejectsActionsDuringWorldCapture() {
        click(0, () -> menu.setSaving(session, true)); verifyNoInteractions(action);
    }
    @Test void blocksInventoryDrags() {
        var event = mock(InventoryDragEvent.class); when(event.getView()).thenReturn(view);
        menu.onDrag(event); verify(event).setCancelled(true);
    }
    @Test void fillsNumberedRegionGapsWithoutOverwriting() {
        assertEquals("floor2", ArenaSetupMenu.nextRegionName(Set.of("floor1", "floor3", "floorDecoration"), "floor"));
        assertEquals("floor4", ArenaSetupMenu.nextRegionName(Set.of("floor1", "floor2", "floor3"), "floor"));
    }
    @Test void validatesWholePositivePlayerLimits() {
        assertEquals(2, ArenaSetupMenu.positive("2"));
        for (String value : new String[]{"0", "-1", "1.5", "NaN", "many", "9999999999999999"})
            assertThrows(IllegalArgumentException.class, () -> ArenaSetupMenu.positive(value));
    }
}
