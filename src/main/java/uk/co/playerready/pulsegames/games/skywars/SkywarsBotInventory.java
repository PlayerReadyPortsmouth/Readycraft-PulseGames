package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Finite chest transfers and inventory swaps; no generated equipment or ammunition. */
public final class SkywarsBotInventory {
    private SkywarsBotInventory() {}
    static int transfer(Inventory chest, PlayerInventory target) { return transfer(chest,target,slot->true); }
    static int transfer(Inventory chest, PlayerInventory target, java.util.function.IntPredicate mayMoveSlot) {
        int moved = 0;
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int slot = 0; slot < chest.getSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            names.add(stack == null || SkywarsBotTerrain.air(stack.getType()) ? null : stack.getType().name());
        }
        for (int slot : SkywarsTactics.lootOrder(names)) {
            if (!mayMoveSlot.test(slot)) continue;
            ItemStack stack = chest.getItem(slot);
            if (stack == null || SkywarsBotTerrain.air(stack.getType())) continue;
            int before = stack.getAmount();
            var left = target.addItem(stack.clone());
            int remaining = left.values().stream().mapToInt(ItemStack::getAmount).sum();
            if (remaining >= before) continue;
            moved += before - remaining;
            if (remaining == 0) chest.setItem(slot, null);
            else { ItemStack remainder = stack.clone(); remainder.setAmount(remaining); chest.setItem(slot, remainder); }
        }
        return moved;
    }
    public static void equip(PlayerInventory inventory) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate == null) continue;
            String name = candidate.getType().name();
            ItemStack equipped;
            if (name.endsWith("_HELMET")) equipped = inventory.getHelmet();
            else if (name.endsWith("_CHESTPLATE")) equipped = inventory.getChestplate();
            else if (name.endsWith("_LEGGINGS")) equipped = inventory.getLeggings();
            else if (name.endsWith("_BOOTS")) equipped = inventory.getBoots();
            else continue;
            if (rank(candidate) <= rank(equipped)) continue;
            if (name.endsWith("_HELMET")) inventory.setHelmet(candidate.clone());
            else if (name.endsWith("_CHESTPLATE")) inventory.setChestplate(candidate.clone());
            else if (name.endsWith("_LEGGINGS")) inventory.setLeggings(candidate.clone());
            else inventory.setBoots(candidate.clone());
            inventory.setItem(slot, equipped == null ? null : equipped.clone());
        }
        int best = -1, rank = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType().name().endsWith("_SWORD") && rank(item) > rank) { best = slot; rank = rank(item); }
        }
        if (best >= 9) {
            ItemStack displaced = inventory.getItem(0);
            inventory.setItem(0, inventory.getItem(best)); inventory.setItem(best, displaced); best = 0;
        }
        if (best >= 0) inventory.setHeldItemSlot(best);
    }
    public static int rank(ItemStack item) {
        if (item == null || SkywarsBotTerrain.air(item.getType())) return 0;
        String name = item.getType().name();
        if (name.startsWith("NETHERITE_")) return 6;
        if (name.startsWith("DIAMOND_")) return 5;
        if (name.startsWith("IRON_")) return 4;
        if (name.startsWith("CHAINMAIL_") || name.startsWith("STONE_")) return 3;
        if (name.startsWith("GOLDEN_")) return 2;
        return 1;
    }
    public static int blockSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getAmount() > 0
                    && (item.getType() == Material.OAK_PLANKS || item.getType() == Material.COBBLESTONE
                        || item.getType() == Material.END_STONE || item.getType().name().endsWith("_WOOL"))) return slot;
        }
        // No planks or stone from the chests: any other solid full block in the bag will do for a bridge.
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getAmount() > 0 && stackableBridgeBlock(item.getType())) return slot;
        }
        return -1;
    }
    /** A full solid block that stays where it is put: not sand or gravel, not a hazard, not a chest or a stair. */
    static boolean stackableBridgeBlock(Material material) {
        String name = material.name();
        return uk.co.playerready.pulsegames.core.bot.BotFloorSafety.fullCube(material)
                && !name.endsWith("SAND") && !name.equals("GRAVEL") && !name.endsWith("_POWDER") && !name.endsWith("_LEAVES")
                && !name.endsWith("_BUCKET") && !name.endsWith("_ORE") && !name.contains("SHULKER") && !name.contains("COMMAND") && !name.contains("SPAWNER");
    }
}
