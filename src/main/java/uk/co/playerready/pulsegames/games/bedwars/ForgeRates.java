package uk.co.playerready.pulsegames.games.bedwars;

/** Forge team upgrade maths: each level adds half as much again to the team generator's iron and gold. */
final class ForgeRates {
    private ForgeRates() {}

    static final int MAX_LEVEL = 4;

    /** 1.0, 1.5, 2.0, 2.5, 3.0 for levels 0 to 4. */
    static double multiplier(int level) { return 1 + 0.5 * Math.max(0, Math.min(level, MAX_LEVEL)); }

    /** Extra items on the nth drop (0-based) so the long-run rate equals {@link #multiplier}. */
    static int extraDrops(int level, int eventIndex) {
        int l = Math.max(0, Math.min(level, MAX_LEVEL));
        return (eventIndex + 1) * l / 2 - eventIndex * l / 2;
    }

    /** One free forge tier for a team on a comeback, never beyond the top tier. */
    static int withComeback(int level, boolean comeback) {
        return Math.min(MAX_LEVEL, Math.max(0, level) + (comeback ? 1 : 0));
    }
}
