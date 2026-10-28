package uk.co.playerready.pulsegames.games.buildbattle;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

/**
 * Creative-palette filter for Build Battle. Everything here either spawns something, moves liquid or fire
 * across plots, or carries text a child typed that every voter would then read.
 */
final class BuildBattleRules {

    private BuildBattleRules() {}

    static boolean banned(Material type) {
        if (type == null) return false;
        String name = type.name();
        if (name.endsWith("_SPAWN_EGG") || name.endsWith("_BUCKET") || name.endsWith("_SIGN")
                || name.endsWith("MINECART") || name.endsWith("_BOAT") || name.endsWith("_RAFT")
                || name.endsWith("COMMAND_BLOCK") || name.endsWith("_SPAWNER")) return true;
        return switch (type) {
            case WRITABLE_BOOK, WRITTEN_BOOK, KNOWLEDGE_BOOK, NAME_TAG, FLINT_AND_STEEL, FIRE_CHARGE, TNT,
                 END_CRYSTAL, ARMOR_STAND, ITEM_FRAME, GLOW_ITEM_FRAME, PAINTING, SPAWNER, STRUCTURE_BLOCK,
                 JIGSAW, DEBUG_STICK, FIRE, SOUL_FIRE, LAVA, WATER -> true;
            default -> false;
        };
    }

    /** True for a banned item, or a shulker box or bundle with one tucked inside (saved creative toolbars). */
    static boolean banned(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (banned(item.getType())) return true;
        if (!item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta state && state.hasBlockState()
                && state.getBlockState() instanceof org.bukkit.block.Container container) {
            return anyBanned(container.getInventory());
        }
        return meta instanceof BundleMeta bundle && bundle.getItems().stream().anyMatch(BuildBattleRules::banned);
    }

    private static boolean anyBanned(Inventory inventory) {
        for (ItemStack stack : inventory.getContents()) if (banned(stack)) return true;
        return false;
    }

    /** Removes every banned stack, armour and off-hand included. Returns how many were removed. */
    static int strip(org.bukkit.inventory.PlayerInventory inventory) {
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (banned(contents[slot])) {
                inventory.setItem(slot, null);
                removed++;
            }
        }
        return removed;
    }
}
