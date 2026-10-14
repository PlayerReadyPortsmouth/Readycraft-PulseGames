package uk.co.playerready.pulsegames.games.skywars;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
final class SkywarsTestItems {
    static ItemStack stack(Material material) { return stack(material,1); }
    static ItemStack stack(Material material,int amount) {
        ItemStack item=mock(ItemStack.class); AtomicInteger count=new AtomicInteger(amount);
        when(item.getMaxStackSize()).thenReturn(64); when(item.getType()).thenReturn(material); when(item.getAmount()).thenAnswer(i->count.get());
        doAnswer(i->{count.set(i.getArgument(0));return null;}).when(item).setAmount(anyInt());
        when(item.clone()).thenAnswer(i->stack(material,count.get()));
        when(item.isSimilar(any())).thenAnswer(i->i.getArgument(0) instanceof ItemStack other && other.getType()==material);
        return item;
    }
}
