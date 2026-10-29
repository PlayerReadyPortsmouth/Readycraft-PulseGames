package uk.co.playerready.pulsegames.games.bedwars.shop;

import org.bukkit.Material;
import uk.co.playerready.pulsegames.core.ui.shop.ShopRules.Status;
import uk.co.playerready.pulsegames.core.ui.shop.ShopRules.Verdict;

import java.util.Collection;
import java.util.Locale;

/** Pure shop rules: can this child buy it, what do we say, which armour pieces go on. */
public final class ShopRules {
    private ShopRules() {}


    public static Verdict verdict(ShopCatalogue catalogue, ShopCatalogue.Item item, int have, Collection<Material> held) {
        if (item.group() != null) return uk.co.playerready.pulsegames.core.ui.shop.ShopRules.tiered(catalogue.ownedTier(item, held), item.tier(), have, item.cost());
        if (item.unique() && held.contains(item.material())) return new Verdict(Status.OWNED_THIS, 0);
        return uk.co.playerready.pulsegames.core.ui.shop.ShopRules.afford(have, item.cost());
    }

    /** Team upgrade at its current level; maxed upgrades report OWNED_THIS. */
    public static Verdict upgradeVerdict(ShopCatalogue.Upgrade upgrade, int level, int diamonds) {
        if (level >= upgrade.maxLevel()) return new Verdict(Status.OWNED_THIS, 0);
        return uk.co.playerready.pulsegames.core.ui.shop.ShopRules.afford(diamonds, upgrade.costs().get(level));
    }

    public static Verdict afford(int have, int cost) { return uk.co.playerready.pulsegames.core.ui.shop.ShopRules.afford(have, cost); }

    public static String currencyName(Material currency, int n) {
        return switch (currency) {
            case IRON_INGOT -> "iron";
            case GOLD_INGOT -> "gold";
            case DIAMOND -> n == 1 ? "diamond" : "diamonds";
            default -> currency.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    public static String needMore(int shortfall, Material currency) {
        return uk.co.playerready.pulsegames.core.ui.shop.ShopRules.needMore(shortfall, currencyName(currency, shortfall));
    }

    /** Leggings and boots that match a chestplate, e.g. IRON_CHESTPLATE gives IRON_LEGGINGS and IRON_BOOTS. */
    public static Material[] armourSet(Material chestplate) {
        String stem = chestplate.name().replace("_CHESTPLATE", "");
        return new Material[]{chestplate, Material.valueOf(stem + "_LEGGINGS"), Material.valueOf(stem + "_BOOTS")};
    }

    public static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; default -> String.valueOf(n); };
    }
}
