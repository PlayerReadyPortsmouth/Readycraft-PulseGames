package uk.co.playerready.pulsegames.games.readyplayerz;

import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.bukkit.Material;

/** Mystery Box pricing and weighted prize table, plus Pack-a-Punch eligibility. */
final class ZMysteryBox {
    static final int BASE_PRICE = 950, PACK_A_PUNCH_PRICE = 5000, SPIN_STEPS = 6, SPIN_STEP_TICKS = 10;

    enum Prize {
        STONE_SHARP("Sharp Stone Sword", Material.STONE_SWORD, 15),
        IRON_SWORD("Iron Sword", Material.IRON_SWORD, 18),
        DIAMOND_SWORD("Diamond Sword", Material.DIAMOND_SWORD, 8),
        BOW("Bow + 24 arrows", Material.BOW, 18),
        CROSSBOW("Crossbow + 24 arrows", Material.CROSSBOW, 14),
        SPEED("Speed Perk", Material.SUGAR, 10),
        TOUGH("Tough Skin Perk", Material.GOLDEN_APPLE, 9),
        REGEN("Regen Perk", Material.GLISTERING_MELON_SLICE, 8);

        final String label;
        final Material icon;
        final int weight;
        Prize(String label, Material icon, int weight) { this.label = label; this.icon = icon; this.weight = weight; }
        boolean weapon() { return ordinal() <= CROSSBOW.ordinal(); }
    }

    private static final Set<Material> PUNCHABLE = Set.of(Material.WOODEN_SWORD, Material.STONE_SWORD,
        Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD, Material.BOW,
        Material.CROSSBOW);

    private ZMysteryBox() {}

    /** 950 points, +10% per use this round (compounding), rounded to the nearest point. */
    static int price(int usesThisRound) {
        return (int) Math.round(BASE_PRICE * Math.pow(1.1, Math.max(0, Math.min(usesThisRound, 60))));
    }

    static Prize roll(RandomGenerator random) {
        int total = 0;
        for (Prize p : Prize.values()) total += p.weight;
        int pick = random.nextInt(total);
        for (Prize p : Prize.values()) {
            if (pick < p.weight) return p;
            pick -= p.weight;
        }
        return Prize.IRON_SWORD;
    }

    static List<Prize> prizes() { return List.of(Prize.values()); }

    static boolean punchable(Material material) { return PUNCHABLE.contains(material); }

    /** Bows and crossbows get Power/Quick Charge; melee weapons get Sharpness. */
    static boolean ranged(Material material) { return material == Material.BOW || material == Material.CROSSBOW; }
}
