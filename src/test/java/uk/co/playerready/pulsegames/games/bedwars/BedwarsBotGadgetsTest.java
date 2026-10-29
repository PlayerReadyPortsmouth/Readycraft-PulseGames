package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsBotFunky.Facts;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsBotTeams.Role;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsCustomItems.Custom;
import uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue;

import java.util.*;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BedwarsBotGadgetsTest {
    private static final ShopCatalogue SHOP = ShopCatalogue.loadBundled();
    private static final Set<Material> GEARED = Set.of(Material.DIAMOND_SWORD, Material.DIAMOND_CHESTPLATE, Material.IRON_PICKAXE,
            Material.SHEARS, Material.BOW, Material.WATER_BUCKET);
    private static final IntUnaryOperator ALWAYS = n -> 0, NEVER = n -> n - 1;

    // ---- purchase plan: role x phase x budget x tier ----------------------------------------------------
    private static BedwarsShopPlan.Loadout kit(Role role, int time, int tier, Map<Material, Integer> money, Material... extra) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        counts.putAll(money); counts.put(Material.GOLDEN_APPLE, 2); counts.put(Material.BOW, 1); counts.put(Material.ARROW, 12);
        counts.put(Material.FIRE_CHARGE, 1); counts.put(Material.ENDER_PEARL, 1); counts.put(Material.EGG, 1);
        counts.put(Material.CHEST, 1); counts.put(Material.POTION, 1);
        Set<Material> held = new HashSet<>(GEARED); held.addAll(List.of(extra));
        for (Material m : extra) counts.merge(m, 1, Integer::sum);
        return new BedwarsShopPlan.Loadout(counts, held, 30, role, time, true, Map.of(), tier, Set.of());
    }
    private static String id(BedwarsShopPlan.Pick p) { return p == null ? null : p.item() != null ? p.item().id() : "upgrade:" + p.upgrade().id(); }
    private static Map<Material, Integer> gold(int n) { return Map.of(Material.GOLD_INGOT, n); }
    private static Map<Material, Integer> iron(int n) { return Map.of(Material.IRON_INGOT, n); }

    @Test void defendersBuyShieldHoneyThenPadAndTheDefenderOnlyWhenIronIsPlentiful() {
        assertEquals("knockback_stick", id(BedwarsShopPlan.next(SHOP, kit(Role.GUARD, 200, 1, gold(20)))));
        assertEquals("shield_wall", id(BedwarsShopPlan.next(SHOP, kit(Role.GUARD, 200, 1, gold(20), Material.STICK))));
        var held = kit(Role.GUARD, 200, 1, gold(20), Material.STICK, Material.SHIELD);
        assertEquals("honey_trap", id(BedwarsShopPlan.next(SHOP, held)));
        assertEquals("bounce_pad", id(BedwarsShopPlan.next(SHOP, kit(Role.GUARD, 200, 1, iron(60), Material.STICK, Material.SHIELD, Material.HONEYCOMB))));
        // 120 iron is the price; with only 130 the 30 reserve stops it, with 150 it goes through.
        var padOwned = new Material[]{Material.STICK, Material.SHIELD, Material.HONEYCOMB, Material.SLIME_BLOCK};
        assertNotEquals("dream_defender", id(BedwarsShopPlan.next(SHOP, kit(Role.GUARD, 300, 2, iron(130), padOwned))));
        assertEquals("dream_defender", id(BedwarsShopPlan.next(SHOP, kit(Role.GUARD, 300, 2, iron(150), padOwned))));
        assertNotEquals("dream_defender", id(BedwarsShopPlan.next(SHOP, kit(Role.GUARD, 100, 2, iron(150), padOwned))), "too early in the match");
    }

    @Test void attackersBuyTheirKitInOrderAndGatherersPrepareForTheRaid() {
        assertEquals("knockback_stick", id(BedwarsShopPlan.next(SHOP, kit(Role.ATTACK, 200, 1, gold(20)))));
        var stick = Material.STICK;
        assertEquals("shield_wall", id(BedwarsShopPlan.next(SHOP, kit(Role.ATTACK, 200, 1, gold(20), stick))));
        assertEquals("grappling_hook", id(BedwarsShopPlan.next(SHOP, kit(Role.ATTACK, 200, 1, gold(20), stick, Material.SHIELD))));
        assertEquals("ice_road", id(BedwarsShopPlan.next(SHOP, kit(Role.ATTACK, 200, 1, gold(20), stick, Material.SHIELD, Material.FISHING_ROD))));
        assertEquals("magic_milk", id(BedwarsShopPlan.next(SHOP, kit(Role.GATHER, 200, 1, gold(20)))));
        var milk = kit(Role.GATHER, 250, 1, Map.of(Material.DIAMOND, 6), Material.MILK_BUCKET);
        assertEquals("recall_totem", id(BedwarsShopPlan.next(SHOP, milk)), "diamonds: totem leaves 4 behind");
        assertNotEquals("recall_totem", id(BedwarsShopPlan.next(SHOP, kit(Role.GATHER, 250, 1, Map.of(Material.DIAMOND, 5), Material.MILK_BUCKET))), "a totem would leave 3");
    }

    @Test void phaseMattersAndEasyBotsSkipTheFancierGadgets() {
        assertNotEquals("shield_wall", id(BedwarsShopPlan.next(SHOP, kit(Role.ATTACK, 100, 1, gold(20), Material.STICK))), "too early");
        assertEquals("knockback_stick", id(BedwarsShopPlan.next(SHOP, kit(Role.ATTACK, 100, 0, gold(20)))));
        var easy = kit(Role.ATTACK, 300, 0, gold(60), Material.STICK, Material.SHIELD);
        String pick = id(BedwarsShopPlan.next(SHOP, easy));
        assertNotEquals("grappling_hook", pick); assertNotEquals("ice_road", pick); assertNotEquals("bed_bug", pick);
        assertEquals("smoke_bomb", pick, "Easy still gets the simple throwables");
    }

    @Test void aGimmickNeverSpendsTheTeamIntoZeroAndArmourStaysFirst() {
        // 8 gold could buy a shield wall (8), honey (6) or a freeze ball (3) but would leave under the 6 reserve.
        assertNull(BedwarsShopPlan.next(SHOP, kit(Role.GUARD, 200, 2, gold(8), Material.STICK)));
        assertEquals("honey_trap", id(BedwarsShopPlan.next(SHOP, kit(Role.GUARD, 200, 2, gold(12), Material.STICK))), "leaves exactly the 6 reserve");
        assertEquals("shield_wall", id(BedwarsShopPlan.next(SHOP, kit(Role.GUARD, 200, 2, gold(14), Material.STICK))));
        // Missing armour is bought before any gadget, even when gadgets are affordable too.
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        counts.put(Material.GOLD_INGOT, 40); counts.put(Material.GOLDEN_APPLE, 2);
        var noArmour = new BedwarsShopPlan.Loadout(counts, Set.of(Material.IRON_SWORD, Material.CHAINMAIL_CHESTPLATE), 30, Role.ATTACK, 300, true, Map.of(), 2, Set.of());
        assertEquals("armour_iron", id(BedwarsShopPlan.next(SHOP, noArmour)));
    }

    @Test void upgradesFollowTheRoleAndTrapsNeedNormalTierAndTime() {
        var d = Map.of(Material.DIAMOND, 8);
        var guard = kit(Role.GUARD, 200, 1, d, Material.SHIELD, Material.HONEYCOMB, Material.SLIME_BLOCK, Material.CARVED_PUMPKIN,
                Material.ARMOR_STAND, Material.SNOWBALL, Material.SNOWBALL, Material.STICK);
        assertEquals("upgrade:protection", id(BedwarsShopPlan.next(SHOP, guard)));
        var protectedAlready = new BedwarsShopPlan.Loadout(guard.counts(), guard.held(), 30, Role.GUARD, 200, true,
                Map.of(ShopCatalogue.Effect.PROTECTION, 4, ShopCatalogue.Effect.FORGE, 4), 1, Set.of());
        assertEquals("upgrade:trap_alarm", id(BedwarsShopPlan.next(SHOP, protectedAlready)));
        var easy = new BedwarsShopPlan.Loadout(guard.counts(), guard.held(), 30, Role.GUARD, 200, true,
                Map.of(ShopCatalogue.Effect.PROTECTION, 4, ShopCatalogue.Effect.FORGE, 4), 0, Set.of());
        assertEquals("upgrade:heal_pool", id(BedwarsShopPlan.next(SHOP, easy)), "Easy skips the traps and goes on to the heal pool");
        assertEquals("upgrade:sharpness", id(BedwarsShopPlan.next(SHOP, kit(Role.ATTACK, 200, 1, d, Material.STICK, Material.SHIELD, Material.FISHING_ROD,
                Material.PACKED_ICE, Material.GUNPOWDER, Material.SILVERFISH_SPAWN_EGG, Material.SNOWBALL, Material.SNOWBALL, Material.FIREWORK_ROCKET,
                Material.ARMOR_STAND, Material.MILK_BUCKET, Material.TOTEM_OF_UNDYING))));
    }

    // ---- use policy per tier -------------------------------------------------------------------------------
    private static Facts fight(double distance, double health, boolean edge, boolean theirBed, boolean cornered) {
        return new Facts(health, 20, distance, edge, theirBed, cornered, distance, 10, true, 0, false, false, false, false, false, false);
    }
    private static Facts travel(boolean nearBed, boolean threatened, boolean approach, boolean entering, double gap, double health, double away, double enemy) {
        return new Facts(health, 20, -1, false, false, false, enemy, away, true, gap, nearBed, threatened, approach, false, entering, false);
    }
    private static Set<String> all() { return new HashSet<>(BedwarsBotFunky.POLICY.keySet()); }

    @Test void theUseTableFollowsTheRulesAtHardWithTheDiceFixedToYes() {
        assertEquals("freeze_ball", BedwarsBotFunky.combat(fight(9, 20, false, false, false), all(), 2, ALWAYS));
        var onlySmoke = Set.of("smoke_bomb");
        assertEquals("smoke_bomb", BedwarsBotFunky.combat(fight(7, 20, false, false, false), onlySmoke, 2, ALWAYS));
        assertNull(BedwarsBotFunky.combat(fight(4, 20, false, false, false), onlySmoke, 2, ALWAYS), "closer than 6");
        assertNull(BedwarsBotFunky.combat(fight(13, 20, false, false, false), onlySmoke, 2, ALWAYS), "farther than 12");
        assertEquals("knockback_stick", BedwarsBotFunky.combat(fight(3, 20, true, false, false), all(), 2, ALWAYS));
        assertNull(BedwarsBotFunky.combat(fight(3, 20, false, false, false), Set.of("knockback_stick"), 2, ALWAYS), "not by an edge");
        assertEquals("shield_wall", BedwarsBotFunky.combat(fight(5, 8, false, false, false), all(), 2, ALWAYS));
        assertEquals("rocket_boots", BedwarsBotFunky.combat(fight(3, 5, false, false, true), all(), 2, ALWAYS));
        assertEquals("bed_bug", BedwarsBotFunky.combat(fight(10, 20, false, true, false), Set.of("bed_bug"), 2, ALWAYS));
        assertNull(BedwarsBotFunky.combat(fight(10, 20, false, false, false), Set.of("bed_bug"), 2, ALWAYS), "not near their bed");
        assertEquals("grappling_hook", BedwarsBotFunky.combat(fight(15, 20, false, false, false), Set.of("grappling_hook"), 2, ALWAYS));

        assertEquals("recall_totem", BedwarsBotFunky.travel(travel(false, false, false, false, 0, 6, 60, 30), all(), 2, ALWAYS));
        assertNull(BedwarsBotFunky.travel(travel(false, false, false, false, 0, 6, 60, 5), Set.of("recall_totem"), 2, ALWAYS), "enemy within 8");
        assertNull(BedwarsBotFunky.travel(travel(false, false, false, false, 0, 6, 20, 30), Set.of("recall_totem"), 2, ALWAYS), "close to base");
        assertEquals("dream_defender", BedwarsBotFunky.travel(travel(true, true, false, false, 0, 20, 5, 12), all(), 2, ALWAYS));
        assertEquals("honey_trap", BedwarsBotFunky.travel(travel(true, false, true, false, 0, 20, 5, 30), all(), 2, ALWAYS));
        assertEquals("magic_milk", BedwarsBotFunky.travel(travel(false, false, false, true, 0, 20, 50, 30), all(), 2, ALWAYS));
        assertEquals("ice_road", BedwarsBotFunky.travel(travel(false, false, false, false, 5, 20, 50, 30), all(), 2, ALWAYS));
        assertEquals("grappling_hook", BedwarsBotFunky.travel(travel(false, false, false, false, 12, 20, 50, 30), all(), 2, ALWAYS));
    }

    @Test void easyUsesAFewNormalMoreHardMostAndNeverWhatItsTierLacks() {
        for (int tier = 0; tier <= 2; tier++) {
            int t = tier;
            long usable = BedwarsBotFunky.POLICY.entrySet().stream().filter(e -> e.getValue().minTier() <= t && e.getValue().chance(t) > 0).count();
            if (tier == 0) assertTrue(usable <= 6, "Easy uses few: " + usable);
            if (tier == 2) assertEquals(BedwarsBotFunky.POLICY.size(), usable, "Hard uses all of them");
        }
        assertNull(BedwarsBotFunky.combat(fight(15, 20, false, false, false), Set.of("grappling_hook"), 0, ALWAYS), "Easy never grapples");
        assertNull(BedwarsBotFunky.travel(travel(true, true, false, false, 0, 20, 5, 12), Set.of("dream_defender"), 1, ALWAYS), "Normal never summons a defender");
        assertEquals("shield_wall", BedwarsBotFunky.combat(fight(5, 8, false, false, false), Set.of("shield_wall"), 0, ALWAYS));
        // The dice: freeze ball is 25 / 50 / 80 percent. A roll of 40 fails Easy, passes Normal and Hard.
        IntUnaryOperator forty = n -> 40;
        var f = fight(9, 20, false, false, false);
        assertNull(BedwarsBotFunky.combat(f, Set.of("freeze_ball"), 0, forty));
        assertEquals("freeze_ball", BedwarsBotFunky.combat(f, Set.of("freeze_ball"), 1, forty));
        assertEquals("freeze_ball", BedwarsBotFunky.combat(f, Set.of("freeze_ball"), 2, forty));
        assertNull(BedwarsBotFunky.combat(f, Set.of("freeze_ball"), 2, NEVER));
    }

    @Test void anItemOnCooldownIsNeverChosenOrUsed() {
        // Cooldown items drop out of the ready set, so the policy has nothing to roll for.
        assertNull(BedwarsBotFunky.combat(fight(9, 20, false, false, false), Set.of(), 2, ALWAYS));
        assertNull(BedwarsBotFunky.travel(travel(true, true, false, false, 0, 20, 5, 12), Set.of(), 2, ALWAYS));
        // And the use itself refuses before touching the inventory.
        GameInstance game = mock(GameInstance.class);
        var items = new BedwarsCustomItems(game, ShopCatalogue::loadBundled, b -> false, b -> { }, b -> { }, t -> null);
        Player bot = mock(Player.class);
        when(bot.getUniqueId()).thenReturn(UUID.randomUUID());
        items.startCooldown(bot.getUniqueId(), Custom.FREEZE_BALL);
        assertFalse(items.botUse(bot, Custom.FREEZE_BALL));
        verify(bot, never()).getInventory();
    }

    // ---- decoys -------------------------------------------------------------------------------------------
    @Test void aBotDecidesOnceAboutADecoyChasesItBrieflyThenIgnoresIt() {
        var decoys = new BedwarsBotDecoys();
        UUID bot = UUID.randomUUID(), decoy = UUID.randomUUID();
        assertTrue(decoys.attracts(bot, decoy, 100, 0, ALWAYS), "Easy falls for it");
        assertTrue(decoys.attracts(bot, decoy, 100 + 119, 0, NEVER), "the first roll stands; still chasing at 6 s");
        assertFalse(decoys.attracts(bot, decoy, 100 + 120, 0, ALWAYS), "after 6 s it ignores the decoy for good");
        assertFalse(decoys.attracts(bot, decoy, 100 + 1000, 0, ALWAYS));
        UUID other = UUID.randomUUID();
        assertFalse(decoys.attracts(bot, other, 100, 2, n -> 50), "Hard: 10 percent, a roll of 50 declines");
        assertFalse(decoys.attracts(bot, other, 101, 2, ALWAYS), "and it does not reconsider");
        assertTrue(decoys.attracts(bot, UUID.randomUUID(), 100, 2, ALWAYS));
        assertTrue(new BedwarsBotDecoys().attracts(bot, decoy, 100 + 100, 0, ALWAYS) == true, "a new service starts clean");
    }

    @Test void foesAreOtherTeamsDecoysAndMobsHuntingTheBotAndNothingElse() {
        GameInstance game = mock(GameInstance.class);
        GameTeam mine = mock(GameTeam.class);
        when(mine.index()).thenReturn(0);
        Player bot = mock(Player.class);
        when(game.teamOf(bot)).thenReturn(mine);
        var items = new BedwarsCustomItems(game, ShopCatalogue::loadBundled, b -> false, b -> { }, b -> { }, t -> null);
        var enemyDecoy = mock(org.bukkit.entity.ArmorStand.class); when(enemyDecoy.isValid()).thenReturn(true);
        var ownDecoy = mock(org.bukkit.entity.ArmorStand.class); when(ownDecoy.isValid()).thenReturn(true);
        var hunting = mock(Silverfish.class); when(hunting.isValid()).thenReturn(true); when(((Mob) hunting).getTarget()).thenReturn(bot);
        var idle = mock(Silverfish.class); when(idle.isValid()).thenReturn(true);
        items.adopt(enemyDecoy, Custom.DECOY_DUMMY, 1, 100);
        items.adopt(ownDecoy, Custom.DECOY_DUMMY, 0, 100);
        items.adopt(hunting, Custom.BED_BUG, 1, 100);
        items.adopt(idle, Custom.BED_BUG, 1, 100);
        var foes = items.foesOf(bot);
        assertEquals(2, foes.size());
        assertTrue(foes.stream().anyMatch(f -> f.entity() == enemyDecoy && f.decoy()));
        assertTrue(foes.stream().anyMatch(f -> f.entity() == hunting && !f.decoy()));
    }

    // ---- alarm -------------------------------------------------------------------------------------------
    @Test void anAlarmSendsEveryBotHomeWhateverItsRoleOrDistance() {
        var cfg = BedwarsBotConfig.defaults();
        var far = new BedwarsBotPlan.Facts(Role.ATTACK, 200, false, false, false, true, true, true, false, true, false, false, false, true);
        assertEquals(BedwarsBotPlan.Goal.RESPOND_BED, BedwarsBotPlan.decide(far, cfg));
        var fighting = new BedwarsBotPlan.Facts(Role.ATTACK, 200, true, false, false, true, true, true, false, true, false, false, false, true);
        assertEquals(BedwarsBotPlan.Goal.FIGHT, BedwarsBotPlan.decide(fighting, cfg), "a fight in hand comes first");
        var bedGone = new BedwarsBotPlan.Facts(Role.ATTACK, 200, false, false, false, false, true, true, false, true, false, false, false, true);
        assertNotEquals(BedwarsBotPlan.Goal.RESPOND_BED, BedwarsBotPlan.decide(bedGone, cfg));
        var state = new BedwarsBotTeams.TeamState();
        state.alarm(100, 1, 2, 3);
        assertTrue(state.alarmActive(119)); assertFalse(state.alarmActive(120));
    }
}
