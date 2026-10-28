package uk.co.playerready.pulsegames.games.karts;

/** Announces a place change only once it has been stable for 10 ticks and at most once per 3 s. */
final class KartPlaceTracker {
    enum Change { NONE, GAINED, LOST }
    static final int STABLE_TICKS = 10, MIN_GAP_TICKS = 60;
    private int shown, candidate;
    private long since, lastAnnounced = Long.MIN_VALUE / 2;

    Change update(int place, long tick) {
        if (place != candidate) { candidate = place; since = tick; }
        if (shown == 0) { if (tick - since >= STABLE_TICKS) shown = candidate; return Change.NONE; }
        if (candidate == shown || tick - since < STABLE_TICKS || tick - lastAnnounced < MIN_GAP_TICKS) return Change.NONE;
        Change change = candidate < shown ? Change.GAINED : Change.LOST;
        shown = candidate; lastAnnounced = tick;
        return change;
    }
}
