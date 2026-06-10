package uk.co.playerready.pulsegames.core.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record Kit(String id, String displayName, Material icon,
                  ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots,
                  List<ItemStack> items) {

    public void apply(Player player) {
        var inv = player.getInventory();
        inv.clear();
        inv.setHelmet(helmet);
        inv.setChestplate(chestplate);
        inv.setLeggings(leggings);
        inv.setBoots(boots);
        for (ItemStack item : items) {
            inv.addItem(item.clone());
        }
    }
}
