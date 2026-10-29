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

    /** @param tier 0 Easy, 1 Normal, 2 Hard: lower tiers skip the fancier gadgets. @param potions effect names of held potions. */
    record Loadout(Map<Material, Integer> counts, Set<Material> held, int blocks, Role role, int gameTime,
                   boolean teamUpgrades, Map<ShopCatalogue.Effect, Integer> upgradeLevels, int tier, Set<String> potions) {
        Loadout(Map<Material, Integer> counts, Set<Material> held, int blocks, Role role, int gameTime,
                boolean teamUpgrades, Map<ShopCatalogue.Effect, Integer> upgradeLevels) {
            this(counts, held, blocks, role, gameTime, teamUpgrades, upgradeLevels, 0, Set.of());
        }
        int count(Material m) { return counts.getOrDefault(m, 0); }
    }
    record Pick(ShopCatalogue.Item item, ShopCatalogue.Upgrade upgrade) {}
    /** reserve: currency that must be left after buying (0 none, -1 the gadget default for that currency). */
    private record Rule(String id, boolean essential, int reserve, Predicate<Loadout> when) {
        Rule(String id, boolean essential, Predicate<Loadout> when) { this(id, essential, 0, when); }
    }
    private static Rule gadget(String id, Predicate<Loadout> when) { return new Rule(id, false, -1, when); }
    private static Rule gadget(String id, int reserve, Predicate<Loadout> when) { return new Rule(id, false, reserve, when); }
    /** What a gadget purchase must leave behind so a gimmick never empties the purse: armour and tools come first. */
    private static int defaultReserve(Material currency) {
        return currency == Material.IRON_INGOT ? 24 : currency == Material.GOLD_INGOT ? 6 : 4;
    }
    private static boolean guard(Loadout l) { return l.role() == Role.GUARD; }
    private static boolean attack(Loadout l) { return l.role() == Role.ATTACK; }

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
            new Rule("bridge_egg", false, l -> l.role() == Role.ATTACK && l.gameTime() >= 60 && l.count(Material.EGG) == 0),
            new Rule("popup_tower", false, l -> l.role() != Role.GATHER && l.gameTime() >= 120 && l.count(Material.CHEST) == 0),
            new Rule("potion_speed", false, l -> l.role() == Role.ATTACK && l.gameTime() >= 45 && l.count(Material.POTION) == 0),
            new Rule("water_bucket", false, l -> l.role() == Role.GUARD),
            // Gadgets: after armour, weapons and tools, each leaving a reserve. Defenders protect the bed, attackers
            // cross and disrupt, gatherers prepare for the raid. Lower tiers skip the fancier ones (BedwarsBotFunky).
            gadget("knockback_stick", 8, l -> l.role() != Role.GATHER && l.gameTime() >= 60),
            gadget("shield_wall", l -> (guard(l) || attack(l)) && l.gameTime() >= 150 && l.count(Material.SHIELD) == 0),
            gadget("honey_trap", l -> guard(l) && l.gameTime() >= 120 && l.count(Material.HONEYCOMB) == 0),
            gadget("bounce_pad", l -> guard(l) && l.gameTime() >= 180 && l.count(Material.SLIME_BLOCK) == 0),
            gadget("dream_defender", 30, l -> guard(l) && l.gameTime() >= 240 && l.count(Material.CARVED_PUMPKIN) == 0),
            gadget("decoy_dummy", l -> (guard(l) || attack(l)) && l.gameTime() >= 300 && l.count(Material.ARMOR_STAND) == 0),
            gadget("grappling_hook", l -> attack(l) && l.gameTime() >= 90),
            gadget("ice_road", l -> attack(l) && l.gameTime() >= 90 && l.count(Material.PACKED_ICE) == 0),
            gadget("smoke_bomb", l -> attack(l) && l.gameTime() >= 120 && l.count(Material.GUNPOWDER) == 0),
            gadget("bed_bug", l -> attack(l) && l.gameTime() >= 150 && l.count(Material.SILVERFISH_SPAWN_EGG) == 0),
            gadget("freeze_ball", l -> (guard(l) || attack(l)) && l.gameTime() >= 90 && l.count(Material.SNOWBALL) < 2),
            gadget("rocket_boots", l -> attack(l) && l.gameTime() >= 180 && l.count(Material.FIREWORK_ROCKET) == 0),
            gadget("potion_invis", l -> attack(l) && l.gameTime() >= 240 && !l.potions().contains("invisibility")),
            gadget("magic_milk", l -> l.role() != Role.GUARD && l.gameTime() >= 120 && l.count(Material.MILK_BUCKET) == 0),
            new Rule("bow", false, l -> true),
            new Rule("arrows", false, l -> l.count(Material.BOW) > 0 && l.count(Material.ARROW) < 12),
            new Rule("sword_diamond", false, l -> true),
            new Rule("armour_diamond", false, l -> true),
            gadget("recall_totem", l -> l.role() != Role.GUARD && l.gameTime() >= 200 && l.count(Material.TOTEM_OF_UNDYING) == 0));

    private static final List<ShopCatalogue.Effect> ATTACK_UPGRADES = List.of(
            ShopCatalogue.Effect.SHARPNESS, ShopCatalogue.Effect.PROTECTION, ShopCatalogue.Effect.HASTE, ShopCatalogue.Effect.FORGE);
    private static final List<ShopCatalogue.Effect> GUARD_UPGRADES = List.of(
            ShopCatalogue.Effect.PROTECTION, ShopCatalogue.Effect.FORGE, ShopCatalogue.Effect.TRAP_ALARM,
            ShopCatalogue.Effect.TRAP_SLOW, ShopCatalogue.Effect.HEAL_POOL, ShopCatalogue.Effect.SHARPNESS, ShopCatalogue.Effect.HASTE);
    private static final List<ShopCatalogue.Effect> GATHER_UPGRADES = List.of(
            ShopCatalogue.Effect.PROTECTION, ShopCatalogue.Effect.SHARPNESS, ShopCatalogue.Effect.FORGE, ShopCatalogue.Effect.HASTE);

    /** Team upgrades in the order this role wants them. Traps are a Normal-and-up habit and need the game to be under way. */
    static List<ShopCatalogue.Effect> upgradeOrder(Loadout l) {
        List<ShopCatalogue.Effect> order = switch (l.role()) {
            case GUARD -> GUARD_UPGRADES;
            case ATTACK -> ATTACK_UPGRADES;
            default -> GATHER_UPGRADES;
        };
        return order.stream().filter(e -> !e.isTrap() || (l.tier() >= 1 && l.gameTime() >= 90)).toList();
    }

    static Pick next(ShopCatalogue catalogue, Loadout l) {
        Set<Material> saving = new HashSet<>();
        for (Rule rule : RULES) {
            if (l.tier() < BedwarsBotFunky.minTier(rule.id()) || !rule.when().test(l)) continue;
            ShopCatalogue.Item item = catalogue.items().stream().filter(i -> i.id().equals(rule.id())).findFirst().orElse(null);
            if (item == null) continue;
            int have = l.count(item.currency());
            int reserve = rule.reserve() < 0 ? defaultReserve(item.currency()) : rule.reserve();
            var verdict = ShopRules.verdict(catalogue, item, have, l.held());
            if (verdict.status() == Status.CAN_BUY && have - item.cost() < reserve) continue; // a gadget never empties the purse
            if (verdict.status() == Status.NEED_MORE) { if (rule.reserve() == 0) saving.add(item.currency()); } // a gadget never makes the bot save
            else if (verdict.status() == Status.CAN_BUY && (rule.essential() || !saving.contains(item.currency()))) return new Pick(item, null);
        }
        if (l.teamUpgrades() && !saving.contains(Material.DIAMOND)) {
            for (ShopCatalogue.Effect effect : upgradeOrder(l)) {
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
