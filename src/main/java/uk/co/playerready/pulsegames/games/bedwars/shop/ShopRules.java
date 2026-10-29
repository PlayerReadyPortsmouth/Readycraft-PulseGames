package uk.co.playerready.pulsegames.games.bedwars.shop;

import org.bukkit.Material;

import java.util.Collection;
import java.util.Locale;

/** Pure shop rules: can this child buy it, what do we say, which armour pieces go on. */
public final class ShopRules {
    private ShopRules() {}

    public enum Status { CAN_BUY, NEED_MORE, OWNED_THIS, OWNED_BETTER }

    public record Verdict(Status status, int shortfall) {
        public boolean buyable() { return status == Status.CAN_BUY; }
    }

    public static Verdict verdict(ShopCatalogue catalogue, ShopCatalogue.Item item, int have, Collection<Material> held) {
        if (item.group() != null) {
            int owned = catalogue.ownedTier(item, held);
            if (owned > item.tier()) return new Verdict(Status.OWNED_BETTER, 0);
            if (owned == item.tier()) return new Verdict(Status.OWNED_THIS, 0);
        } else if (item.unique() && held.contains(item.material())) {
            return new Verdict(Status.OWNED_THIS, 0);
        }
        return afford(have, item.cost());
    }

    /** Team upgrade at its current level; maxed upgrades report OWNED_THIS. */
    public static Verdict upgradeVerdict(ShopCatalogue.Upgrade upgrade, int level, int diamonds) {
        if (level >= upgrade.maxLevel()) return new Verdict(Status.OWNED_THIS, 0);
        return afford(diamonds, upgrade.costs().get(level));
    }

    public static Verdict afford(int have, int cost) {
        return have >= cost ? new Verdict(Status.CAN_BUY, 0) : new Verdict(Status.NEED_MORE, cost - have);
    }

    public static String currencyName(Material currency, int n) {
        return switch (currency) {
            case IRON_INGOT -> "iron";
            case GOLD_INGOT -> "gold";
            case DIAMOND -> n == 1 ? "diamond" : "diamonds";
            default -> currency.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    public static String needMore(int shortfall, Material currency) {
        return "Need " + shortfall + " more " + currencyName(currency, shortfall);
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
