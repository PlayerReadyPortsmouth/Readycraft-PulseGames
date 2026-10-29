package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Material;
import uk.co.playerready.pulsegames.core.ui.shop.ShopRules.Status;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsBotTeams.Role;
import uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue;
import uk.co.playerready.pulsegames.games.bedwars.shop.ShopRules;

import java.util.*;
import java.util.function.Predicate;

/**
 * What a bot buys next, in order of need, using the prices in bedwars-shop.yml (ids below must exist there;
 * an id missing from a server's older copy is simply skipped). Cost-aware: when the first wanted item in a
 * currency is not affordable yet the bot saves that currency instead of spending it on something lower down.
 */
final class BedwarsShopPlan {
    private BedwarsShopPlan() {}

    record Loadout(Map<Material, Integer> counts, Set<Material> held, int blocks, Role role, int gameTime,
                   boolean teamUpgrades, Map<ShopCatalogue.Effect, Integer> upgradeLevels) {
        int count(Material m) { return counts.getOrDefault(m, 0); }
    }
    record Pick(ShopCatalogue.Item item, ShopCatalogue.Upgrade upgrade) {}
    private record Rule(String id, boolean essential, Predicate<Loadout> when) {}

    /** The priority table. Armour before bow; blocks first only when nearly out. */
    static final List<Rule> RULES = List.of(
            new Rule("wool", true, l -> l.blocks() < 8),
            new Rule("sword_stone", false, l -> true),
            new Rule("armour_chain", false, l -> true),
            new Rule("sword_iron", false, l -> true),
            new Rule("armour_iron", false, l -> true),
            new Rule("golden_apple", false, l -> l.gameTime() >= 90 && l.count(Material.GOLDEN_APPLE) < 2),
            new Rule("end_stone", false, l -> l.role() == Role.GUARD && l.blocks() < 24 && l.gameTime() >= 120),
            new Rule("planks", false, l -> l.role() == Role.GUARD && l.blocks() < 24),
            new Rule("wool", false, l -> l.blocks() < 24),
            new Rule("pickaxe_iron", false, l -> l.role() == Role.ATTACK),
            new Rule("shears", false, l -> l.role() == Role.ATTACK),
            new Rule("fireball", false, l -> l.role() != Role.GATHER && l.count(Material.FIRE_CHARGE) == 0),
            new Rule("ender_pearl", false, l -> l.role() == Role.ATTACK && l.count(Material.ENDER_PEARL) == 0),
            new Rule("water_bucket", false, l -> l.role() == Role.GUARD),
            new Rule("bow", false, l -> true),
            new Rule("arrows", false, l -> l.count(Material.BOW) > 0 && l.count(Material.ARROW) < 12),
            new Rule("sword_diamond", false, l -> true),
            new Rule("armour_diamond", false, l -> true));

    private static final List<ShopCatalogue.Effect> UPGRADE_ORDER = List.of(
            ShopCatalogue.Effect.SHARPNESS, ShopCatalogue.Effect.PROTECTION, ShopCatalogue.Effect.HASTE);

    static Pick next(ShopCatalogue catalogue, Loadout l) {
        Set<Material> saving = new HashSet<>();
        for (Rule rule : RULES) {
            if (!rule.when().test(l)) continue;
            ShopCatalogue.Item item = catalogue.items().stream().filter(i -> i.id().equals(rule.id())).findFirst().orElse(null);
            if (item == null) continue;
            var verdict = ShopRules.verdict(catalogue, item, l.count(item.currency()), l.held());
            if (verdict.status() == Status.NEED_MORE) saving.add(item.currency());
            else if (verdict.status() == Status.CAN_BUY && (rule.essential() || !saving.contains(item.currency()))) return new Pick(item, null);
        }
        if (l.teamUpgrades() && !saving.contains(Material.DIAMOND)) {
            for (ShopCatalogue.Effect effect : UPGRADE_ORDER) {
                for (ShopCatalogue.Upgrade upgrade : catalogue.upgrades()) {
                    if (upgrade.effect() != effect) continue;
                    int level = l.upgradeLevels().getOrDefault(effect, 0);
                    if (ShopRules.upgradeVerdict(upgrade, level, l.count(Material.DIAMOND)).status() == Status.CAN_BUY) return new Pick(null, upgrade);
                }
            }
        }
        return null;
    }

    /** True when something is worth buying but the bot is short: it should keep gathering, not travel to spend. */
    static boolean affordable(ShopCatalogue catalogue, Loadout l) { return next(catalogue, l) != null; }
}
