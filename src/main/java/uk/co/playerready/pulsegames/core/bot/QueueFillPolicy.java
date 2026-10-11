package uk.co.playerready.pulsegames.core.bot;

/** Pure capacity decision; never create a partial set that cannot start a match. */
public final class QueueFillPolicy {
    private QueueFillPolicy() {}
    public static int desiredBots(int humans, int minimum, int maximum,
                                  int perMatchCap, int availableIncludingCurrent) {
        if (humans < 1 || maximum < minimum || humans >= maximum) return 0;
        int target = maximum <= 4 ? maximum : maximum / 2 + maximum % 2;
        int desired = Math.max(minimum, target) - humans;
        int capacity = Math.max(0, Math.min(perMatchCap, availableIncludingCurrent));
        if (capacity < minimum - humans) return 0;
        return Math.max(0, Math.min(desired, capacity));
    }
}
