package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.Material;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkywarsBotInventoryTest {
    @Test void partialTransferLeavesExactRemainderInChestAndNeverMutatesOriginalStack() {
        Inventory chest=mock(Inventory.class); PlayerInventory bag=mock(PlayerInventory.class);
        ItemStack original=SkywarsTestItems.stack(Material.OAK_PLANKS,32);
        when(chest.getSize()).thenReturn(1); when(chest.getItem(0)).thenReturn(original);
        when(bag.addItem(any(ItemStack.class))).thenAnswer(i->new HashMap<>(Map.of(0,SkywarsTestItems.stack(Material.OAK_PLANKS,12))));
        assertEquals(20,SkywarsBotInventory.transfer(chest,bag));
        var remaining=org.mockito.ArgumentCaptor.forClass(ItemStack.class);
        verify(chest).setItem(eq(0),remaining.capture()); assertEquals(12,remaining.getValue().getAmount());
        assertEquals(32,original.getAmount());
    }
    @Test void fullInventoryDoesNotRemoveLootAndSuccessfulTransferClearsSource() {
        Inventory chest=mock(Inventory.class); PlayerInventory bag=mock(PlayerInventory.class);
        ItemStack arrows=SkywarsTestItems.stack(Material.ARROW,8);
        when(chest.getSize()).thenReturn(1); when(chest.getItem(0)).thenReturn(arrows);
        when(bag.addItem(any(ItemStack.class))).thenAnswer(i->new HashMap<>(Map.of(0,arrows.clone())));
        assertEquals(0,SkywarsBotInventory.transfer(chest,bag)); verify(chest,never()).setItem(anyInt(),any());
        when(bag.addItem(any(ItemStack.class))).thenAnswer(i->new HashMap<>());
        assertEquals(8,SkywarsBotInventory.transfer(chest,bag)); verify(chest).setItem(0,null);
    }
    @Test void armourUpgradePreservesOldArmourAndMovesBestSwordIntoHotbar() {
        PlayerInventory bag=mock(PlayerInventory.class); Map<Integer,ItemStack> slots=new HashMap<>();
        ItemStack oldHelmet=SkywarsTestItems.stack(Material.LEATHER_HELMET), diamond=SkywarsTestItems.stack(Material.DIAMOND_HELMET);
        ItemStack sword=SkywarsTestItems.stack(Material.IRON_SWORD), displaced=SkywarsTestItems.stack(Material.COOKED_BEEF,6);
        slots.put(12,diamond); slots.put(14,sword); slots.put(0,displaced);
        when(bag.getItem(anyInt())).thenAnswer(i->slots.get(i.getArgument(0)));
        doAnswer(i->{slots.put(i.getArgument(0),i.getArgument(1));return null;}).when(bag).setItem(anyInt(),nullable(ItemStack.class));
        when(bag.getHelmet()).thenReturn(oldHelmet);
        SkywarsBotInventory.equip(bag);
        verify(bag).setHelmet(argThat(item->item.getType()==Material.DIAMOND_HELMET)); assertEquals(oldHelmet.getType(),slots.get(12).getType());
        assertSame(sword,slots.get(0)); assertSame(displaced,slots.get(14)); verify(bag).setHeldItemSlot(0);
    }
    @Test void bridgeSelectionUsesOnlyActualSafeBuildingStacks() {
        PlayerInventory bag=mock(PlayerInventory.class);
        when(bag.getItem(0)).thenAnswer(i->SkywarsTestItems.stack(Material.TNT,4));
        when(bag.getItem(1)).thenAnswer(i->SkywarsTestItems.stack(Material.LAVA_BUCKET));
        assertEquals(-1,SkywarsBotInventory.blockSlot(bag));
        when(bag.getItem(23)).thenAnswer(i->SkywarsTestItems.stack(Material.COBBLESTONE,2));
        assertEquals(23,SkywarsBotInventory.blockSlot(bag));
    }
}
