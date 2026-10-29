package uk.co.playerready.pulsegames.core.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuKitTest {
    // wrap + lore

    @Test void descriptionsWrapAtTheGeyserSafeWidth() {
        var lines = Lore.wrap("Dig the floor out from under everyone else and be the last one standing!", Lore.WIDTH);
        assertTrue(lines.size() >= 3);
        lines.forEach(l -> assertTrue(l.length() <= Lore.WIDTH, l));
        assertEquals("Dig the floor out from under everyone else and be the last one standing!", String.join(" ", lines));
        assertEquals(List.of("Supercalifragilisticexpialidocious"), Lore.wrap("Supercalifragilisticexpialidocious", 10));
        assertTrue(Lore.wrap("  ", 10).isEmpty());
    }

    @Test void everyNameAndLoreLineIsExplicitlyNotItalic() {
        List<Component> all = Lore.of().desc("A long description that will wrap onto more than one line here.")
                .blank().kv("Modes", "3").hint("Click to play").status("<red>Soon").line("<yellow><b>Bold").build();
        all.forEach(c -> assertEquals(TextDecoration.State.FALSE, c.decoration(TextDecoration.ITALIC), c.toString()));
        assertEquals(TextDecoration.State.FALSE, Theme.mm("<yellow><b>Spleef").decoration(TextDecoration.ITALIC));
        assertEquals("Pulse · Games", PlainTextComponentSerializer.plainText().serialize(Theme.title("Games")));
    }

    // paginate

    @Test void pagesClampAndSlice() {
        List<Integer> items = java.util.stream.IntStream.range(0, 30).boxed().toList();
        assertEquals(2, Paginator.pages(30, 28));
        assertEquals(1, Paginator.pages(0, 28));
        var last = Paginator.page(items, 28, 99);
        assertEquals(1, last.page());
        assertEquals(List.of(28, 29), last.items());
        assertFalse(last.hasNext());
        assertTrue(last.hasPrev());
        assertEquals(0, Paginator.page(items, 28, -4).page());
        assertTrue(Paginator.page(List.of(), 28, 3).items().isEmpty());
    }

    @Test void navSlotsAreFixed() {
        Menu small = new Menu(mock(Plugin.class), player(UUID.randomUUID()), Component.empty(), 3);
        assertEquals(18, small.backSlot());
        assertEquals(26, small.closeSlot());
        Menu big = new Menu(mock(Plugin.class), player(UUID.randomUUID()), Component.empty(), 6);
        assertArrayEquals(new int[]{45, 48, 49, 50, 53},
                new int[]{big.backSlot(), big.prevSlot(), big.pageSlot(), big.nextSlot(), big.closeSlot()});
    }

    // click + drag safety

    private static Player player(UUID id) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        when(p.isOnline()).thenReturn(true);
        return p;
    }

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final Plugin plugin = mock(Plugin.class, RETURNS_DEEP_STUBS);
        final Player player = player(id);
        final Menu menu = new Menu(plugin, player, Component.empty(), 3);
        final Inventory inventory = mock(Inventory.class);
        final InventoryView view = mock(InventoryView.class);
        @SuppressWarnings("unchecked") final Consumer<Player> action = mock(Consumer.class);

        Fixture() {
            menu.attach(inventory);
            when(inventory.getHolder()).thenReturn(menu);
            when(view.getTopInventory()).thenReturn(inventory);
            when(player.getOpenInventory()).thenReturn(view);
            menu.set(10, mock(ItemStack.class), action);
        }

        InventoryClickEvent click(Player who, Inventory clicked, int slot) {
            var e = mock(InventoryClickEvent.class);
            when(e.getView()).thenReturn(view);
            when(e.getWhoClicked()).thenReturn(who);
            when(e.getClickedInventory()).thenReturn(clicked);
            when(e.getRawSlot()).thenReturn(slot);
            return e;
        }
    }

    @Test void clickRunsNextTickOnlyAndCancelsTheEvent() {
        var f = new Fixture();
        var e = f.click(f.player, f.inventory, 10);
        new MenuListener().onClick(e);
        verify(e).setCancelled(true);
        verifyNoInteractions(f.action);
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(f.plugin.getServer().getScheduler()).runTask(eq(f.plugin), task.capture());
        task.getValue().run();
        verify(f.action).accept(f.player);
    }

    @Test void staleViewDoesNothing() {
        var f = new Fixture();
        new MenuListener().onClick(f.click(f.player, f.inventory, 10));
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(f.plugin.getServer().getScheduler()).runTask(eq(f.plugin), task.capture());
        when(f.view.getTopInventory()).thenReturn(mock(Inventory.class));
        task.getValue().run();
        verifyNoInteractions(f.action);
    }

    @Test void everyClickKindIsCancelledEvenWhenNothingRuns() {
        // shift-click, number key, offhand swap, double-click and collect-to-cursor all arrive as clicks.
        var f = new Fixture();
        Player stranger = player(UUID.randomUUID());
        Inventory own = mock(Inventory.class);
        for (var e : List.of(f.click(f.player, own, 5), f.click(f.player, f.inventory, 3), f.click(stranger, f.inventory, 10))) {
            new MenuListener().onClick(e);
            verify(e).setCancelled(true);
        }
        verify(f.plugin.getServer().getScheduler(), never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test void dragsAreCancelledOnlyForMenus() {
        var f = new Fixture();
        var drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(f.view);
        new MenuListener().onDrag(drag);
        verify(drag).setCancelled(true);

        var other = mock(InventoryView.class);
        var plain = mock(Inventory.class);
        when(other.getTopInventory()).thenReturn(plain);
        var untouched = mock(InventoryDragEvent.class);
        when(untouched.getView()).thenReturn(other);
        new MenuListener().onDrag(untouched);
        verify(untouched, never()).setCancelled(true);
    }

    @Test void refreshRewritesOnlyChangedSlots() {
        var f = new Fixture();
        ItemStack same = mock(ItemStack.class);
        ItemStack changed = mock(ItemStack.class);
        int[] frame = {0};
        f.menu.renderer(() -> { f.menu.set(1, same); f.menu.set(2, frame[0]++ == 0 ? same : changed); });
        f.menu.refresh();
        verify(f.inventory).setItem(1, same);
        when(f.inventory.getItem(1)).thenReturn(same);
        when(f.inventory.getItem(2)).thenReturn(same);
        clearInvocations(f.inventory);
        f.menu.refresh();
        verify(f.inventory).setItem(2, changed);
        verify(f.inventory, never()).setItem(eq(1), any());
    }
}
