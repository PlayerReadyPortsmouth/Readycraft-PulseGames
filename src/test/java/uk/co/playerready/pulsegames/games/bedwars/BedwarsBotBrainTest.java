package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsBotTeams.*;
import uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedwarsBotBrainTest {
    private static final BedwarsBotConfig CFG = BedwarsBotConfig.defaults();
    private static final ShopCatalogue SHOP = ShopCatalogue.loadBundled();

    // ---- shop priority ----------------------------------------------------------
    private static BedwarsShopPlan.Loadout kit(Role role, int blocks, Map<Material, Integer> counts, Material... held) {
        return new BedwarsShopPlan.Loadout(counts, Set.of(held), blocks, role, 200, true, Map.of());
    }
    private static String id(BedwarsShopPlan.Pick pick) { return pick == null ? null : pick.item() != null ? pick.item().id() : "upgrade:" + pick.upgrade().id(); }

    @Test void shopOrderIsNeedFirstArmourBeforeBowAndCostAware() {
        assertEquals("wool", id(BedwarsShopPlan.next(SHOP, kit(Role.GATHER, 0, Map.of(Material.IRON_INGOT, 4)))));
        assertEquals("sword_stone", id(BedwarsShopPlan.next(SHOP, kit(Role.GATHER, 16, Map.of(Material.IRON_INGOT, 12)))));
        // 12 gold buys iron armour or a bow: armour first.
        assertEquals("armour_iron", id(BedwarsShopPlan.next(SHOP, kit(Role.GATHER, 16, Map.of(Material.GOLD_INGOT, 12), Material.IRON_SWORD, Material.CHAINMAIL_CHESTPLATE))));
        // 24 iron cannot buy chainmail (30), so it saves rather than spending on shears (20)...
        var attack = kit(Role.ATTACK, 16, Map.of(Material.IRON_INGOT, 24), Material.STONE_SWORD);
        assertNull(BedwarsShopPlan.next(SHOP, attack));
        // ...but running out of blocks still buys wool.
        assertEquals("wool", id(BedwarsShopPlan.next(SHOP, kit(Role.ATTACK, 2, Map.of(Material.IRON_INGOT, 24), Material.STONE_SWORD))));
    }

    @Test void shopPricesComeFromTheYamlAndRoleItemsAreRoleGated() {
        Set<Material> geared = Set.of(Material.DIAMOND_SWORD, Material.DIAMOND_CHESTPLATE, Material.IRON_PICKAXE, Material.SHEARS);
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        counts.put(Material.IRON_INGOT, 40); counts.put(Material.GOLDEN_APPLE, 2); counts.put(Material.BOW, 1); counts.put(Material.ARROW, 12);
        var attacker = new BedwarsShopPlan.Loadout(counts, geared, 30, Role.ATTACK, 200, true, Map.of());
        assertEquals("fireball", id(BedwarsShopPlan.next(SHOP, attacker)));
        counts.put(Material.IRON_INGOT, 39);
        assertNull(BedwarsShopPlan.next(SHOP, attacker), "fireball costs 40 iron in bedwars-shop.yml");
        counts.put(Material.FIRE_CHARGE, 1); counts.put(Material.DIAMOND, 4);
        assertEquals("ender_pearl", id(BedwarsShopPlan.next(SHOP, attacker)));
        counts.put(Material.ENDER_PEARL, 1); counts.put(Material.DIAMOND, 8); counts.put(Material.CHEST, 1); // tower already owned
        assertEquals("upgrade:sharpness", id(BedwarsShopPlan.next(SHOP, attacker)));
    }

    @Test void guardBuysStrongerBlocksAsTheyBecomeAffordable() {
        Set<Material> geared = Set.of(Material.DIAMOND_SWORD, Material.DIAMOND_CHESTPLATE);
        var guard = new BedwarsShopPlan.Loadout(Map.of(Material.IRON_INGOT, 24), geared, 10, Role.GUARD, 200, true, Map.of());
        assertEquals("end_stone", id(BedwarsShopPlan.next(SHOP, guard)));
    }

    // ---- roles ------------------------------------------------------------------
    private static List<UUID> bots(int n) { List<UUID> l = new ArrayList<>(); for (int i = 0; i < n; i++) l.add(new UUID(0, i + 1)); return l; }
    private static long count(Map<UUID, Role> m, Role r) { return m.values().stream().filter(x -> x == r).count(); }

    @Test void oneGuardPerTeamThenTheRestGatherAndLaterAttack() {
        var early = BedwarsBotTeams.assign(bots(3), true, 10, CFG);
        assertEquals(1, count(early, Role.GUARD)); assertEquals(2, count(early, Role.GATHER));
        var late = BedwarsBotTeams.assign(bots(3), true, 200, CFG);
        assertEquals(1, count(late, Role.GUARD)); assertEquals(2, count(late, Role.ATTACK));
        assertEquals(early.keySet().stream().filter(k -> early.get(k) == Role.GUARD).toList(),
                late.keySet().stream().filter(k -> late.get(k) == Role.GUARD).toList(), "same guard all match");
    }

    @Test void noGuardWithoutABedAndALoneBotOnlyGuardsEarly() {
        assertEquals(0, count(BedwarsBotTeams.assign(bots(2), false, 10, CFG), Role.GUARD));
        assertEquals(Role.GUARD, BedwarsBotTeams.assign(bots(1), true, 10, CFG).get(bots(1).get(0)));
        assertEquals(Role.ATTACK, BedwarsBotTeams.assign(bots(1), true, 200, CFG).get(bots(1).get(0)));
    }

    // ---- bed target ---------------------------------------------------------------
    @Test void attackersPickTheLeastDefendedBedAndSpreadAcrossBeds() {
        var weak = new BedCandidate(1, 40, 0, 6, 0); var strong = new BedCandidate(2, 20, 12, 6, 0);
        assertEquals(1, BedwarsBotTeams.chooseBed(List.of(strong, weak)));
        var twinA = new BedCandidate(1, 30, 3, 5, 1); var twinB = new BedCandidate(2, 30, 3, 5, 0);
        assertEquals(2, BedwarsBotTeams.chooseBed(List.of(twinA, twinB)), "a teammate already went for bed 1");
        assertEquals(1, BedwarsBotTeams.chooseBed(List.of(new BedCandidate(1, 30, 3, 2, 0), new BedCandidate(2, 30, 3, 20, 0))), "shorter bridge wins");
        assertNull(BedwarsBotTeams.chooseBed(List.of()));
    }

    // ---- plan, items, tools -------------------------------------------------------------
    private static BedwarsBotPlan.Facts facts(Role role, int time, boolean enemy, boolean threat, boolean near, boolean gap, boolean atBase,
                                              boolean afford, boolean trip) {
        return new BedwarsBotPlan.Facts(role, time, enemy, threat, near, true, true, true, gap, atBase, afford, trip, false);
    }

    @Test void planPrioritisesFightThenBedThreatThenRebuildThenWork() {
        assertEquals(BedwarsBotPlan.Goal.FIGHT, BedwarsBotPlan.decide(facts(Role.GUARD, 10, true, true, true, true, true, false, false), CFG));
        assertEquals(BedwarsBotPlan.Goal.RESPOND_BED, BedwarsBotPlan.decide(facts(Role.GUARD, 10, false, true, false, true, true, false, false), CFG));
        assertEquals(BedwarsBotPlan.Goal.GATHER, BedwarsBotPlan.decide(facts(Role.GATHER, 200, false, true, false, false, true, false, false), CFG), "far gatherer ignores it");
        assertEquals(BedwarsBotPlan.Goal.RESPOND_BED, BedwarsBotPlan.decide(facts(Role.GATHER, 200, false, true, true, false, true, false, false), CFG));
        assertEquals(BedwarsBotPlan.Goal.REBUILD, BedwarsBotPlan.decide(facts(Role.GUARD, 200, false, false, true, true, true, false, false), CFG));
        assertEquals(BedwarsBotPlan.Goal.HOLD_BED, BedwarsBotPlan.decide(facts(Role.GUARD, 200, false, false, true, false, true, false, false), CFG));
        assertEquals(BedwarsBotPlan.Goal.SPEND, BedwarsBotPlan.decide(facts(Role.ATTACK, 200, false, false, false, false, false, true, false), CFG));
        assertEquals(BedwarsBotPlan.Goal.ATTACK, BedwarsBotPlan.decide(facts(Role.ATTACK, 200, false, false, false, false, true, false, true), CFG), "no diamonds before mid-game");
        assertEquals(BedwarsBotPlan.Goal.DIAMONDS, BedwarsBotPlan.decide(facts(Role.ATTACK, 400, false, false, false, false, true, false, true), CFG));
    }

    @Test void combatItemsEscapeThenHealThenFireballNeverNearOwnBed() {
        var low = new BedwarsBotItems.Facts(4, 5, true, true, true, 100, 0, true);
        assertEquals(BedwarsBotItems.Use.PEARL_ESCAPE, BedwarsBotItems.choose(low, CFG));
        assertEquals(BedwarsBotItems.Use.GOLDEN_APPLE, BedwarsBotItems.choose(new BedwarsBotItems.Facts(8, 5, true, false, true, 100, 0, true), CFG));
        var fine = new BedwarsBotItems.Facts(20, 6, false, false, true, 100, 0, true);
        assertEquals(BedwarsBotItems.Use.FIREBALL, BedwarsBotItems.choose(fine, CFG));
        assertEquals(BedwarsBotItems.Use.NONE, BedwarsBotItems.choose(new BedwarsBotItems.Facts(20, 6, false, false, true, 100, 0, false), CFG), "own bed in the blast");
        assertEquals(BedwarsBotItems.Use.NONE, BedwarsBotItems.choose(new BedwarsBotItems.Facts(20, 6, false, false, true, 100, 160, true), CFG), "cooling down");
        assertEquals(BedwarsBotItems.Use.NONE, BedwarsBotItems.choose(new BedwarsBotItems.Facts(20, 2, false, false, true, 100, 0, true), CFG), "too close to knock back safely");
    }

    @Test void rightToolForEachBlock() {
        Set<Material> held = Set.of(Material.SHEARS, Material.IRON_PICKAXE, Material.WOODEN_PICKAXE, Material.STONE_AXE);
        assertEquals(Material.SHEARS, BedwarsBotTools.preferred(Material.RED_WOOL, held));
        assertEquals(Material.STONE_AXE, BedwarsBotTools.preferred(Material.OAK_PLANKS, held));
        assertEquals(Material.IRON_PICKAXE, BedwarsBotTools.preferred(Material.END_STONE, held));
        assertNull(BedwarsBotTools.preferred(Material.RED_BED, held));
        assertNull(BedwarsBotTools.preferred(Material.RED_WOOL, Set.of(Material.IRON_PICKAXE)));
    }

    @Test void configDefaultsAreTheDocumentedOnes() {
        assertEquals(3, CFG.lookAheadCells()); assertEquals(5, CFG.bridgePlaceTicks()); assertEquals(2, CFG.defenceLayers());
        assertEquals(150, CFG.attackStartSeconds()); assertEquals(6.0, CFG.pearlEscapeHealth());
    }
}
