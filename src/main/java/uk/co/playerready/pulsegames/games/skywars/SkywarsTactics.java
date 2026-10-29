package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.Location;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Pure Skywars bot reasoning: what to loot first, which chest and which player to go for, when to pearl out. */
public final class SkywarsTactics {
    private SkywarsTactics() {}

    /** Chests further than this are not worth a trip (the next island is the next job). */
    public static final double CHEST_RANGE = 40;
    /** Players further than this are not bridged towards: a bot only heads for what it could plausibly see. */
    public static final double PLAYER_RANGE = 64;

    /** Higher is looted first: sword, armour, blocks, bow and arrows, pearls, apples, food, the rest. */
    public static int lootValue(String material) {
        if (material.endsWith("_SWORD")) return 100;
        if (material.endsWith("_CHESTPLATE") || material.endsWith("_LEGGINGS")) return 90;
        if (material.endsWith("_HELMET") || material.endsWith("_BOOTS")) return 85;
        if (material.equals("OAK_PLANKS") || material.equals("COBBLESTONE") || material.equals("END_STONE")
                || material.endsWith("_WOOL")) return 70;
        if (material.equals("BOW")) return 60;
        if (material.equals("ARROW")) return 55;
        if (material.equals("ENDER_PEARL")) return 50;
        if (material.equals("GOLDEN_APPLE")) return 45;
        if (material.startsWith("COOKED_")) return 20;
        return 5;
    }

    /** Chest slot indexes, best first; stable for equal values so the order is predictable. */
    public static List<Integer> lootOrder(List<String> materialBySlot) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < materialBySlot.size(); i++) if (materialBySlot.get(i) != null) slots.add(i);
        slots.sort(Comparator.comparingInt((Integer i) -> -lootValue(materialBySlot.get(i))));
        return slots;
    }

    /** Index of the nearest unlooted chest worth walking to (x, y, z each), or -1. Height gaps count double. */
    public static int nearestChest(double x, double y, double z, double[][] chests, boolean[] looted) {
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < chests.length; i++) {
            if (looted[i]) continue;
            double flat = Math.hypot(chests[i][0] - x, chests[i][2] - z), gap = Math.abs(chests[i][1] - y);
            if (flat > CHEST_RANGE || gap > 12) continue;
            double score = flat + gap * 2;
            if (score < bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    /**
     * Index of the player to bridge towards, or -1. Each entry is x, z, gear rank (armour and weapon, 0 to about 30).
     * Score is distance minus 3 per gear point so a nearby kitted-out player beats a nearer naked one, but a far
     * one never wins by gear alone.
     */
    public static int richestTarget(double x, double z, double[][] players) {
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < players.length; i++) {
            double distance = Math.hypot(players[i][0] - x, players[i][1] - z);
            if (distance > PLAYER_RANGE) continue;
            double score = distance - players[i][2] * 3;
            if (score < bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    /** What a bot wants to do when nothing is in view. */
    public enum Plan { LOOT, BRIDGE, WANDER }

    public static Plan plan(boolean chestKnown, boolean playerKnown) {
        return chestKnown ? Plan.LOOT : playerKnown ? Plan.BRIDGE : Plan.WANDER;
    }

    /** Pearl out of a losing fight: Normal and Hard only, hurt and with the enemy on top of them. */
    public static boolean shouldPearlEscape(double healthFraction, double enemyDistance, boolean hasPearl, BotDifficulty tier) {
        return hasPearl && tier != BotDifficulty.EASY && healthFraction <= 0.35 && enemyDistance <= 5;
    }

    /** A goal a few blocks from {@code here} so an idle bot keeps pacing its island rather than standing on spawn. */
    public static double[] wanderOffset(java.util.function.IntUnaryOperator random) {
        double angle = random.applyAsInt(8) * Math.PI / 4;
        return new double[]{Math.round(Math.cos(angle) * 4), Math.round(Math.sin(angle) * 4)};
    }

    /** Island-edge-safe tiles for the shared melee strafe (same footprint rule as bridging and Bedwars). */
    public static Predicate<Location> strafeSafe(Location level) {
        double y = level.getY();
        return at -> { Location feet = at.clone(); feet.setY(y); return SkywarsBotTerrain.safe(feet, true); };
    }
}
