package uk.co.playerready.pulsegames.games.luckypillars;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/** Weighted Lucky Pillars drop registry. Items are built lazily so the pool is testable off-server. */
public final class LuckyDrops {

    /** How a drop's weight moves over the round (progress 0 at start, 1 at {@link #FULL_PROGRESS_SECONDS}). */
    public enum Tier {
        BASIC(1.4, 0.4), NORMAL(1.0, 1.0), STRONG(0.25, 2.0), RARE(0.05, 2.5);

        final double start, end;
        Tier(double start, double end) { this.start = start; this.end = end; }
        double factor(double progress) { return start + (end - start) * progress; }
    }

    public enum Category { WEAPON, BLOCK, UTILITY, POTION, EGG, JOKE }

    /** @param cap max per player per round, 0 = unlimited */
    public record Drop(String id, String label, Material material, int weight, Tier tier, Category category,
                       int cap, Supplier<ItemStack> item) {}

    public static final int FULL_PROGRESS_SECONDS = 300;
    public static final double WEAPON_CATCH_UP = 3.0;
    private static final Set<String> BANNED = Set.of("WITHER", "WARDEN", "DRAGON", "RAVAGER");

    public static final List<Drop> ALL = build();

    /** Materials that count as "armed" for catch-up. */
    public static final Set<Material> WEAPONS = weaponMaterials();

    private LuckyDrops() {}

    public static double progress(int gameTime) {
        return Math.max(0, Math.min(1, gameTime / (double) FULL_PROGRESS_SECONDS));
    }

    public static double weightOf(Drop drop, double progress, boolean needsWeapon) {
        double w = drop.weight() * drop.tier().factor(progress);
        if (needsWeapon && drop.category() == Category.WEAPON && drop.tier() != Tier.RARE) w *= WEAPON_CATCH_UP;
        return w;
    }

    /** Picks a drop by time-scaled weight; ids in {@code excluded} (capped out) are skipped. */
    public static Drop pick(List<Drop> pool, double progress, boolean needsWeapon, Set<String> excluded,
                            RandomGenerator random) {
        double total = 0;
        for (Drop d : pool) if (!excluded.contains(d.id())) total += weightOf(d, progress, needsWeapon);
        if (total <= 0) return null;
        double roll = random.nextDouble() * total;
        Drop last = null;
        for (Drop d : pool) {
            if (excluded.contains(d.id())) continue;
            last = d;
            roll -= weightOf(d, progress, needsWeapon);
            if (roll < 0) return d;
        }
        return last;
    }

    public static boolean banned(Material material) {
        String name = material.name();
        return BANNED.stream().anyMatch(name::contains);
    }

    // ---- builders ----------------------------------------------------------

    private static List<Drop> build() {
        List<Drop> d = new ArrayList<>();
        var B = Tier.BASIC; var N = Tier.NORMAL; var S = Tier.STRONG; var R = Tier.RARE;
        var W = Category.WEAPON; var K = Category.BLOCK; var U = Category.UTILITY;
        var P = Category.POTION; var E = Category.EGG; var J = Category.JOKE;

        // weapons
        d.add(new Drop("yeet_stick", "a Yeet Stick", Material.STICK, 10, N, W, 0,
                () -> named(enchant(new ItemStack(Material.STICK), Enchantment.KNOCKBACK, 2), "<gold>Yeet Stick")));
        d.add(new Drop("sharp_sword", "a Sharp Wooden Sword", Material.WOODEN_SWORD, 8, B, W, 0,
                () -> enchant(new ItemStack(Material.WOODEN_SWORD), Enchantment.SHARPNESS, 2)));
        d.add(new Drop("stone_axe", "a stone axe", Material.STONE_AXE, 8, N, W, 0, () -> new ItemStack(Material.STONE_AXE)));
        d.add(new Drop("iron_sword", "an iron sword", Material.IRON_SWORD, 5, S, W, 0, () -> new ItemStack(Material.IRON_SWORD)));
        d.add(new Drop("mace", "a MACE", Material.MACE, 2, R, W, 1, () -> new ItemStack(Material.MACE)));
        d.add(new Drop("bow", "a bow", Material.BOW, 6, N, W, 0, () -> new ItemStack(Material.BOW)));
        d.add(new Drop("arrows", "12 arrows", Material.ARROW, 7, N, K, 0, () -> new ItemStack(Material.ARROW, 12)));
        d.add(new Drop("crossbow", "a crossbow", Material.CROSSBOW, 4, S, W, 0, () -> new ItemStack(Material.CROSSBOW)));
        d.add(new Drop("trident", "a Loyalty trident", Material.TRIDENT, 2, R, W, 1,
                () -> enchant(new ItemStack(Material.TRIDENT), Enchantment.LOYALTY, 2)));
        d.add(new Drop("wind_charges", "wind charges", Material.WIND_CHARGE, 7, N, W, 0, () -> new ItemStack(Material.WIND_CHARGE, 6)));
        d.add(new Drop("fire_charges", "fire charges", Material.FIRE_CHARGE, 4, N, W, 0, () -> new ItemStack(Material.FIRE_CHARGE, 3)));
        d.add(new Drop("stone_pickaxe", "a stone pickaxe", Material.STONE_PICKAXE, 4, B, W, 0, () -> new ItemStack(Material.STONE_PICKAXE)));

        // blocks
        d.add(new Drop("planks", "building blocks", Material.OAK_PLANKS, 14, B, K, 0, () -> new ItemStack(Material.OAK_PLANKS, 12)));
        d.add(new Drop("wool", "wool", Material.WHITE_WOOL, 10, B, K, 0, () -> new ItemStack(Material.WHITE_WOOL, 16)));
        d.add(new Drop("cobble", "cobblestone", Material.COBBLESTONE, 8, B, K, 0, () -> new ItemStack(Material.COBBLESTONE, 16)));
        d.add(new Drop("stone_bricks", "stone bricks", Material.STONE_BRICKS, 5, N, K, 0, () -> new ItemStack(Material.STONE_BRICKS, 12)));
        d.add(new Drop("slime", "slime blocks", Material.SLIME_BLOCK, 6, N, K, 0, () -> new ItemStack(Material.SLIME_BLOCK, 3)));
        d.add(new Drop("cobweb", "cobwebs", Material.COBWEB, 6, N, K, 0, () -> new ItemStack(Material.COBWEB, 2)));
        d.add(new Drop("tnt", "TNT (primes when placed!)", Material.TNT, 6, N, K, 4, () -> new ItemStack(Material.TNT, 2)));
        d.add(new Drop("honey", "honey blocks", Material.HONEY_BLOCK, 4, N, K, 0, () -> new ItemStack(Material.HONEY_BLOCK, 2)));
        d.add(new Drop("water", "a water bucket", Material.WATER_BUCKET, 4, N, K, 0, () -> new ItemStack(Material.WATER_BUCKET)));
        d.add(new Drop("powder_snow", "a powder snow bucket", Material.POWDER_SNOW_BUCKET, 3, N, K, 0, () -> new ItemStack(Material.POWDER_SNOW_BUCKET)));
        d.add(new Drop("scaffolding", "scaffolding", Material.SCAFFOLDING, 6, B, K, 0, () -> new ItemStack(Material.SCAFFOLDING, 8)));
        d.add(new Drop("ladders", "ladders", Material.LADDER, 5, B, K, 0, () -> new ItemStack(Material.LADDER, 8)));
        d.add(new Drop("snowballs", "8 snowballs", Material.SNOWBALL, 8, B, K, 0, () -> new ItemStack(Material.SNOWBALL, 8)));
        d.add(new Drop("obsidian", "obsidian", Material.OBSIDIAN, 3, S, K, 0, () -> new ItemStack(Material.OBSIDIAN, 2)));

        // utility
        d.add(new Drop("pearl", "an ender pearl", Material.ENDER_PEARL, 6, N, U, 0, () -> new ItemStack(Material.ENDER_PEARL)));
        d.add(new Drop("shield", "a shield", Material.SHIELD, 6, N, U, 0, () -> new ItemStack(Material.SHIELD)));
        d.add(new Drop("rod", "a fishing rod", Material.FISHING_ROD, 6, N, U, 0, () -> new ItemStack(Material.FISHING_ROD)));
        d.add(new Drop("gapple", "a golden apple", Material.GOLDEN_APPLE, 6, N, U, 0, () -> new ItemStack(Material.GOLDEN_APPLE)));
        d.add(new Drop("totem", "a Totem of Undying", Material.TOTEM_OF_UNDYING, 2, R, U, 1, () -> new ItemStack(Material.TOTEM_OF_UNDYING)));
        d.add(new Drop("elytra", "ELYTRA", Material.ELYTRA, 2, R, U, 1, () -> new ItemStack(Material.ELYTRA)));
        d.add(new Drop("rockets", "firework rockets", Material.FIREWORK_ROCKET, 3, S, U, 0, () -> new ItemStack(Material.FIREWORK_ROCKET, 3)));
        d.add(new Drop("chorus", "a chorus fruit", Material.CHORUS_FRUIT, 4, N, U, 0, () -> new ItemStack(Material.CHORUS_FRUIT, 2)));
        d.add(new Drop("flint", "flint and steel", Material.FLINT_AND_STEEL, 4, N, U, 0, () -> new ItemStack(Material.FLINT_AND_STEEL)));
        d.add(new Drop("lava", "a LAVA bucket", Material.LAVA_BUCKET, 2, R, U, 2, () -> new ItemStack(Material.LAVA_BUCKET)));
        d.add(new Drop("chain_chest", "a chainmail chestplate", Material.CHAINMAIL_CHESTPLATE, 3, B, U, 0, () -> new ItemStack(Material.CHAINMAIL_CHESTPLATE)));
        d.add(new Drop("iron_helmet", "an iron helmet", Material.IRON_HELMET, 3, N, U, 0, () -> new ItemStack(Material.IRON_HELMET)));

        // potions (1.21.4 PotionType names)
        d.add(potion("pot_harming", "a splash potion of harming", Material.SPLASH_POTION, () -> PotionType.HARMING, 4, S));
        d.add(potion("pot_slowness", "a splash potion of slowness", Material.SPLASH_POTION, () -> PotionType.SLOWNESS, 5, N));
        d.add(potion("pot_weakness", "a splash potion of weakness", Material.SPLASH_POTION, () -> PotionType.WEAKNESS, 4, N));
        d.add(potion("pot_poison", "a lingering potion of poison", Material.LINGERING_POTION, () -> PotionType.POISON, 3, S));
        d.add(potion("pot_leaping", "a potion of leaping", Material.POTION, () -> PotionType.LEAPING, 4, N));
        d.add(potion("pot_invis", "a potion of invisibility", Material.POTION, () -> PotionType.INVISIBILITY, 3, N));
        d.add(potion("pot_slowfall", "a potion of slow falling", Material.POTION, () -> PotionType.SLOW_FALLING, 4, N));

        // spawn eggs (never wither / warden / dragon / ravager)
        d.add(new Drop("egg_creeper", "a creeper egg", Material.CREEPER_SPAWN_EGG, 3, S, E, 2, () -> new ItemStack(Material.CREEPER_SPAWN_EGG)));
        d.add(new Drop("egg_silverfish", "a silverfish egg", Material.SILVERFISH_SPAWN_EGG, 4, N, E, 0, () -> new ItemStack(Material.SILVERFISH_SPAWN_EGG)));
        d.add(new Drop("egg_bee", "a bee egg", Material.BEE_SPAWN_EGG, 4, N, E, 0, () -> new ItemStack(Material.BEE_SPAWN_EGG)));
        d.add(new Drop("egg_cave_spider", "a cave spider egg", Material.CAVE_SPIDER_SPAWN_EGG, 3, N, E, 0, () -> new ItemStack(Material.CAVE_SPIDER_SPAWN_EGG)));
        d.add(new Drop("egg_chicken", "a chicken egg", Material.CHICKEN_SPAWN_EGG, 3, B, E, 0, () -> new ItemStack(Material.CHICKEN_SPAWN_EGG)));
        d.add(new Drop("egg_phantom", "a PHANTOM egg", Material.PHANTOM_SPAWN_EGG, 1, R, E, 1, () -> new ItemStack(Material.PHANTOM_SPAWN_EGG)));

        // jokes
        d.add(new Drop("wet_fish", "a Wet Fish", Material.COD, 5, N, J, 0,
                () -> named(enchant(new ItemStack(Material.COD), Enchantment.KNOCKBACK, 3), "<aqua>Wet Fish")));
        d.add(new Drop("bone_meal", "bone meal", Material.BONE_MEAL, 3, B, J, 0, () -> named(new ItemStack(Material.BONE_MEAL, 3), "<white>Fertiliser (useless)")));
        d.add(new Drop("poison_potato", "a poisonous potato", Material.POISONOUS_POTATO, 3, B, J, 0, () -> new ItemStack(Material.POISONOUS_POTATO)));
        d.add(new Drop("pufferfish", "a pufferfish", Material.PUFFERFISH, 3, B, J, 0, () -> new ItemStack(Material.PUFFERFISH)));
        d.add(new Drop("dead_bush", "a dead bush", Material.DEAD_BUSH, 3, B, J, 0, () -> named(new ItemStack(Material.DEAD_BUSH), "<gray>Tumbleweed")));
        d.add(new Drop("cake", "a cake", Material.CAKE, 3, B, J, 0, () -> named(new ItemStack(Material.CAKE), "<light_purple>The Cake Is A Lie")));
        d.add(new Drop("free_win", "a Free Win", Material.NAME_TAG, 3, B, J, 0, () -> named(new ItemStack(Material.NAME_TAG), "<gold>Free Win")));
        d.add(new Drop("trophy", "a Participation Trophy", Material.DIRT, 3, B, J, 0, () -> named(new ItemStack(Material.DIRT), "<yellow>Participation Trophy")));
        d.add(new Drop("carrot_stick", "a carrot on a stick", Material.CARROT_ON_A_STICK, 3, B, J, 0, () -> new ItemStack(Material.CARROT_ON_A_STICK)));
        return List.copyOf(d);
    }

    private static Drop potion(String id, String label, Material container, Supplier<PotionType> type, int weight, Tier tier) {
        return new Drop(id, label, container, weight, tier, Category.POTION, 0, () -> {
            ItemStack item = new ItemStack(container);
            item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type.get()));
            return item;
        });
    }

    private static Set<Material> weaponMaterials() {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (Drop d : ALL) if (d.category() == Category.WEAPON) set.add(d.material());
        return Set.copyOf(set);
    }

    static ItemStack enchant(ItemStack item, Enchantment enchantment, int level) {
        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }

    static ItemStack named(ItemStack item, String name) {
        item.editMeta(meta -> meta.displayName(Text.mm(name)));
        return item;
    }
}
