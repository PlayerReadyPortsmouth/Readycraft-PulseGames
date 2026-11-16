package uk.co.playerready.pulsegames.games.christmas;

/** Bounded trail and tagging rules. Lifetimes are server ticks, not wall clock time. */
public final class GrinchRules {
    private GrinchRules() {}
    public enum Ground { FRESH, CLEARED, INDOORS }
    public enum VillagerRole { TRACKER, LAMPLIGHTER, SNOWBALLER, BELL_RINGER, ELF_INVENTOR, NIGHT_WATCH }
    public static int printLifetime(Ground ground, boolean sneaking) {
        int ticks = switch (ground) { case FRESH -> 1800; case CLEARED -> 600; case INDOORS -> 200; };
        return sneaking ? ticks / 3 : ticks;
    }
    public static float printScale(Ground ground, boolean sneaking) {
        float size = switch (ground) { case FRESH -> 0.6f; case CLEARED -> 0.4f; case INDOORS -> 0.3f; };
        return sneaking ? size * 0.55f : size;
    }
    public static int grinchCount(int players) { return players >= 12 ? 2 : 1; }
    public static boolean tagged(int hits, int heldTicks) { return hits >= 3 || heldTicks >= 60; }
    public static boolean grinchWins(int banked, int carried, int heldMinimum) {
        return banked >= 15 || carried >= Math.max(1, heldMinimum);
    }
}
