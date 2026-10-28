package uk.co.playerready.pulsegames.core.game;

/** Competitive eligibility is decided from the starting roster, never departures. */
public final class CompetitiveEligibility {
    private CompetitiveEligibility() {}

    /**
     * A competitive game needs someone to beat: a lone human with no bot or opponent (Dropper practice,
     * {@code min-players: 1}) is unranked. Co-operative games are won against the map, so they are exempt.
     */
    public static boolean eligible(int humans, int bots, int mapCapacity, boolean cooperative) {
        if (!cooperative && humans + bots < 2) return false;
        return eligible(humans, bots, mapCapacity);
    }

    public static boolean eligible(int humans, int bots, int mapCapacity) {
        if (humans < 1 || bots < 0 || mapCapacity < 1 || (long) humans + bots > mapCapacity) return false;
        if (bots == 0) return true;
        // Small maps require a human majority; larger maps permit a human/bot tie.
        return mapCapacity <= 4 ? humans > bots : humans >= bots;
    }
}
