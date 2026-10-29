package uk.co.playerready.pulsegames.core.events;

import uk.co.playerready.pulsegames.core.game.CompetitiveEligibility;

/** Pure token maths for events: the normal competitive-eligibility rule, then the multiplier. */
public final class EventRewards {
    private EventRewards() { }

    /** Humans only (events never use bots), so this is the engine's own rule with zero bots. */
    public static boolean eligible(int humans, int capacity) {
        return CompetitiveEligibility.eligible(humans, 0, Math.max(capacity, humans), false);
    }

    /** Zero when not eligible or nothing is due; otherwise at least one token. */
    public static int tokens(int base, double multiplier, boolean eligible) {
        if (!eligible || base <= 0) return 0;
        return (int) Math.max(1, Math.round(base * Math.max(1.0, multiplier)));
    }

    /** Podium prize for a place (joint places pay the same). */
    public static int prize(int place, int first, int second, int third) {
        return switch (place) { case 1 -> first; case 2 -> second; case 3 -> third; default -> 0; };
    }
}
