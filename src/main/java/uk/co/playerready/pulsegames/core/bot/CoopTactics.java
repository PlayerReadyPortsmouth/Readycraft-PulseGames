package uk.co.playerready.pulsegames.core.bot;

/**
 * Pure co-op reasoning for Villager Defense and ReadyPlayerZ bots: which mob to take first, where to hold
 * a lane round the objective without crowding humans, and when to back off. Plain doubles, no Bukkit.
 */
public final class CoopTactics {
    private CoopTactics() {}

    /** Keep hitting the mob already chosen unless another is clearly a bigger problem for the objective. */
    private static final double STICKY_BONUS = 3.0;
    /** Mobs further than this from the objective are left for the lane that is actually near them. */
    public static final double HOLD_RADIUS = 14;

    /**
     * Index of the mob to fight, or -1. Score is distance of the mob from the objective plus 0.6 of its distance
     * from the bot, so the mob nearest the villagers or the objective goes first and a mob on the bot's heels
     * still counts. {@code guard} may be null (no objective): then only the bot's own distance matters.
     */
    public static int pickTarget(double[] guard, double botX, double botZ, double[][] mobs, int current) {
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < mobs.length; i++) {
            double own = Math.hypot(mobs[i][0] - botX, mobs[i][1] - botZ);
            double objective = guard == null ? own : Math.hypot(mobs[i][0] - guard[0], mobs[i][1] - guard[1]);
            double score = objective + own * 0.6 - (i == current ? STICKY_BONUS : 0);
            if (score < bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    /**
     * A spot on an arc round {@code centre} for lane {@code slot} of {@code slots}. The arc is centred on the
     * direction the threat comes from (when known) so the squad spreads across the front instead of stacking on
     * one block, and any spot within 2.5 blocks of a human is pushed clear of them.
     */
    public static double[] lanePoint(double[] centre, int slot, int slots, double radius, double[] threat, double[][] humans) {
        double base = threat == null ? 0 : Math.atan2(threat[1] - centre[1], threat[0] - centre[0]);
        int count = Math.max(1, slots);
        double step = count == 1 ? 0 : Math.min(Math.toRadians(50), Math.toRadians(220) / (count - 1));
        double angle = base + (Math.floorMod(slot, count) - (count - 1) / 2.0) * step;
        double x = centre[0] + Math.cos(angle) * radius, z = centre[1] + Math.sin(angle) * radius;
        for (int pass = 0; pass < 3; pass++) {
            boolean moved = false;
            for (double[] human : humans) {
                double dx = x - human[0], dz = z - human[1], d = Math.hypot(dx, dz);
                if (d >= 2.5) continue;
                if (d < 0.01) { dx = 1; dz = 0; d = 1; }
                x = human[0] + dx / d * 2.6; z = human[1] + dz / d * 2.6; moved = true;
            }
            if (!moved) break;
        }
        return new double[]{x, z};
    }

    /** Back off (and let the lane cover) when low on health with a mob in reach. */
    public static boolean shouldKite(double healthFraction, double retreatHealth, double enemyDistance) {
        return enemyDistance <= 4.5 && healthFraction <= Math.max(0.35, retreatHealth + 0.15);
    }

    /** Chasing a mob far from the objective drags the bot out of its lane; hold instead. */
    public static boolean shouldHold(double[] guard, double enemyX, double enemyZ) {
        return guard != null && Math.hypot(enemyX - guard[0], enemyZ - guard[1]) > HOLD_RADIUS;
    }
}
