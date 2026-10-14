package uk.co.playerready.pulsegames.core.bot;

/** Pure capacity decision; never create a partial set that cannot start a match. */
public final class QueueFillPolicy {
    private QueueFillPolicy() {}
    public static int desiredBots(int humans, int minimum, int maximum,
                                  int perMatchCap, int availableIncludingCurrent) {
        if (humans < 1 || maximum < minimum || humans >= maximum) return 0;
        // Small maps fill completely; larger maps allow half their capacity as bot seats.
        // Human arrivals take remaining seats first, replacing bots when necessary.
        int botSeatLimit = maximum <= 4 ? maximum : maximum / 2;
        int capacity = Math.max(0, Math.min(Math.min(botSeatLimit, maximum - humans),
                Math.min(perMatchCap, availableIncludingCurrent)));
        if (capacity < minimum - humans) return 0;
        return capacity;
    }
}
