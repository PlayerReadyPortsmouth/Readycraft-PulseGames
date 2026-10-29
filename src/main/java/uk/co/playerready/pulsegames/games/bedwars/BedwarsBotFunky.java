package uk.co.playerready.pulsegames.games.bedwars;

import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * When a bot reaches for the shop's gadgets. Pure: the game feeds it facts and the set of items that are held and off
 * cooldown, and performs the answer through the same item code humans use ({@link BedwarsCustomItems#botUse}).
 * Ids are the ids in bedwars-shop.yml. Tier 0 is Easy, 1 Normal, 2 Hard: Easy uses a handful, Hard uses nearly all.
 */
final class BedwarsBotFunky {
    private BedwarsBotFunky() {}

    static final String INVISIBILITY = "potion_invis";

    /** @param minTier lowest tier that buys and uses it; chance is percent per roll for tiers 0, 1, 2. */
    record Policy(int minTier, int easy, int normal, int hard) {
        int chance(int tier) { return tier <= 0 ? easy : tier == 1 ? normal : hard; }
    }

    static final Map<String, Policy> POLICY = Map.ofEntries(
            Map.entry("freeze_ball", new Policy(0, 25, 50, 80)),
            Map.entry("smoke_bomb", new Policy(0, 15, 40, 75)),
            Map.entry("knockback_stick", new Policy(0, 40, 70, 100)),
            Map.entry("shield_wall", new Policy(0, 30, 60, 90)),
            Map.entry("honey_trap", new Policy(0, 30, 60, 90)),
            Map.entry("magic_milk", new Policy(0, 30, 70, 100)),
            Map.entry("bounce_pad", new Policy(1, 0, 50, 80)),
            Map.entry("grappling_hook", new Policy(1, 0, 35, 70)),
            Map.entry("ice_road", new Policy(1, 0, 40, 70)),
            Map.entry("bed_bug", new Policy(1, 0, 40, 80)),
            Map.entry("rocket_boots", new Policy(1, 0, 50, 85)),
            Map.entry("recall_totem", new Policy(1, 0, 60, 90)),
            Map.entry(INVISIBILITY, new Policy(1, 0, 40, 80)),
            Map.entry("dream_defender", new Policy(2, 0, 0, 70)),
            Map.entry("decoy_dummy", new Policy(2, 0, 0, 60)));

    /** Lowest tier that buys this item; 0 for anything not in the table. */
    static int minTier(String id) { Policy p = POLICY.get(id); return p == null ? 0 : p.minTier(); }

    /** Ticks between rolls for a bot of this tier, so a high chance is not rolled twenty times a second. */
    static int rollEveryTicks(int tier) { return tier <= 0 ? 60 : tier == 1 ? 30 : 15; }

    /**
     * @param enemyDistance distance to the visible fight target, or a negative number when there is none
     * @param nearestEnemy distance to the nearest living enemy of any kind, visible or not
     * @param gapAhead blocks of missing floor straight ahead on the way to the target, with floor again beyond (0 = none)
     */
    record Facts(double health, double maxHealth, double enemyDistance, boolean enemyNearEdge, boolean enemyNearTheirBed,
                 boolean cornered, double nearestEnemy, double distanceFromBase, boolean bedAlive, double gapAhead,
                 boolean nearOwnBed, boolean bedThreatened, boolean atApproach, boolean defenderActive,
                 boolean enteringEnemyBase, boolean milkActive) {
        boolean fighting() { return enemyDistance >= 0; }
        double fraction() { return maxHealth <= 0 ? 1 : health / maxHealth; }
    }

    private static boolean roll(String id, Set<String> ready, int tier, IntUnaryOperator rng) {
        Policy p = POLICY.get(id);
        if (p == null || !ready.contains(id) || tier < p.minTier()) return false;
        return rng.applyAsInt(100) < p.chance(tier);
    }

    /** With an enemy in view: escape, shield, shove, throw, then close the distance. */
    static String combat(Facts f, Set<String> ready, int tier, IntUnaryOperator rng) {
        if (!f.fighting() || ready.isEmpty()) return null;
        double d = f.enemyDistance();
        if (f.cornered() && f.health() <= 6 && roll("rocket_boots", ready, tier, rng)) return "rocket_boots";
        if (d <= 8 && f.fraction() <= 0.5 && roll("shield_wall", ready, tier, rng)) return "shield_wall";
        if (d <= 3.5 && f.enemyNearEdge() && roll("knockback_stick", ready, tier, rng)) return "knockback_stick";
        if (d >= 6 && d <= 12) {
            if (roll("freeze_ball", ready, tier, rng)) return "freeze_ball";
            if (roll("smoke_bomb", ready, tier, rng)) return "smoke_bomb";
        }
        if (f.enemyNearTheirBed() && d >= 5 && d <= 16 && roll("bed_bug", ready, tier, rng)) return "bed_bug";
        if (d >= 10 && d <= 24 && roll("grappling_hook", ready, tier, rng)) return "grappling_hook";
        return null;
    }

    /** Between fights: get home, guard the bed approach, prepare for a raid, cross gaps. */
    static String travel(Facts f, Set<String> ready, int tier, IntUnaryOperator rng) {
        if (ready.isEmpty()) return null;
        if (f.bedAlive() && f.health() <= 8 && f.distanceFromBase() >= 40 && f.nearestEnemy() > 8
                && roll("recall_totem", ready, tier, rng)) return "recall_totem";
        if (f.bedAlive() && f.nearOwnBed() && f.bedThreatened() && !f.defenderActive()
                && roll("dream_defender", ready, tier, rng)) return "dream_defender";
        if (f.bedAlive() && f.atApproach()) {
            for (String id : new String[]{"honey_trap", "bounce_pad", "decoy_dummy"}) if (roll(id, ready, tier, rng)) return id;
        }
        if (f.enteringEnemyBase()) {
            if (!f.milkActive() && roll("magic_milk", ready, tier, rng)) return "magic_milk";
            if (roll(INVISIBILITY, ready, tier, rng)) return INVISIBILITY;
        }
        if (f.gapAhead() >= 2 && f.gapAhead() <= 8 && roll("ice_road", ready, tier, rng)) return "ice_road";
        if (f.gapAhead() >= 3 && f.gapAhead() <= 18 && roll("grappling_hook", ready, tier, rng)) return "grappling_hook";
        return null;
    }
}
