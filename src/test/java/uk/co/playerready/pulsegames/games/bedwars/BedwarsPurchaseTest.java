package uk.co.playerready.pulsegames.games.bedwars;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BedwarsPurchaseTest {
    static ItemStack item(Material material,int amount) {
        ItemStack item=mock(ItemStack.class); AtomicInteger count=new AtomicInteger(amount);
        when(item.getType()).thenReturn(material); when(item.getAmount()).thenAnswer(i->count.get()); when(item.getMaxStackSize()).thenReturn(64);
        doAnswer(i->{count.set(i.getArgument(0));return null;}).when(item).setAmount(anyInt());
        when(item.clone()).thenAnswer(i->item(material,count.get()));
        when(item.isSimilar(any())).thenAnswer(i->i.getArgument(0) instanceof ItemStack other && other.getType()==material);
        return item;
    }
    static final class Bag {
        final PlayerInventory inventory=mock(PlayerInventory.class); final Map<Integer,ItemStack> slots=new HashMap<>();
        Bag() {
            when(inventory.getItem(anyInt())).thenAnswer(i->slots.get(i.getArgument(0)));
            doAnswer(i->{slots.put(i.getArgument(0),i.getArgument(1));return null;}).when(inventory).setItem(anyInt(),nullable(ItemStack.class));
        }
    }
    @Test void purchaseChargesExactFiniteCurrencyAndMergesProduct() {
        var bag=new Bag(); bag.slots.put(0,item(Material.IRON_INGOT,10)); bag.slots.put(1,item(Material.WHITE_WOOL,60));
        assertTrue(BedwarsPurchase.buy(bag.inventory,Material.IRON_INGOT,4,item(Material.WHITE_WOOL,16)));
        assertEquals(6,BedwarsPurchase.count(bag.inventory,Material.IRON_INGOT)); assertEquals(76,BedwarsPurchase.count(bag.inventory,Material.WHITE_WOOL));
    }
    @Test void insufficientCurrencyOrFullInventoryNeverPartiallyCharges() {
        var bag=new Bag(); bag.slots.put(0,item(Material.IRON_INGOT,3));
        assertFalse(BedwarsPurchase.buy(bag.inventory,Material.IRON_INGOT,4,item(Material.WHITE_WOOL,16)));
        verify(bag.inventory,never()).setItem(anyInt(),any());
        for(int i=0;i<36;i++) bag.slots.put(i,item(Material.STONE,64)); bag.slots.put(0,item(Material.IRON_INGOT,5));
        assertFalse(BedwarsPurchase.buy(bag.inventory,Material.IRON_INGOT,4,item(Material.WHITE_WOOL,16)));
        assertEquals(5,bag.slots.get(0).getAmount()); verify(bag.inventory,never()).setItem(anyInt(),any());
    }
    @Test void spendingLastCurrencyStackCanFreeSpaceAndNamedCurrencyIsNotAccepted() {
        var bag=new Bag(); for(int i=0;i<36;i++) bag.slots.put(i,item(Material.STONE,64)); bag.slots.put(0,item(Material.IRON_INGOT,4));
        assertTrue(BedwarsPurchase.buy(bag.inventory,Material.IRON_INGOT,4,item(Material.WHITE_WOOL,16)));
        assertEquals(Material.WHITE_WOOL,bag.slots.get(0).getType());
        var named=new Bag(); var iron=item(Material.IRON_INGOT,64); when(iron.hasItemMeta()).thenReturn(true); named.slots.put(0,iron);
        assertFalse(BedwarsPurchase.buy(named.inventory,Material.IRON_INGOT,4,item(Material.WHITE_WOOL,16)));
    }
}
