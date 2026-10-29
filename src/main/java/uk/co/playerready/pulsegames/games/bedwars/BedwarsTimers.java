package uk.co.playerready.pulsegames.games.bedwars;

import java.util.List;

/** Generator timings and the sidebar text for them. Pure so it can be tested without a server. */
final class BedwarsTimers {
    private BedwarsTimers() {}
    static final int TIER2_AT = 300, TIER3_AT = 600;

    static int tierAt(int gameTime) { return gameTime >= TIER3_AT ? 3 : gameTime >= TIER2_AT ? 2 : 1; }
    static int diamondEvery(int tier) { return tier >= 3 ? 20 : tier == 2 ? 25 : 30; }

    /** Seconds until the next generator upgrade, or -1 once the top tier is reached. */
    static int untilUpgrade(int gameTime) {
        return gameTime < TIER2_AT ? TIER2_AT - gameTime : gameTime < TIER3_AT ? TIER3_AT - gameTime : -1;
    }

    /** Seconds until the next diamond drop (a full interval right after a drop). */
    static int untilDiamonds(int gameTime) {
        int every = diamondEvery(tierAt(gameTime));
        return every - gameTime % every;
    }

    static String clock(int seconds) { return "%d:%02d".formatted(seconds / 60, seconds % 60); }

    /**
     * Two lines normally; one combined line on big maps so team lines keep their room
     * (the engine caps the whole sidebar at 15 lines).
     */
    static List<String> lines(int gameTime, int teamCount) {
        int up = untilUpgrade(gameTime), dia = untilDiamonds(gameTime);
        String upText = up < 0 ? "<green>Max tier" : "<yellow>" + clock(up);
        if (teamCount > 6) return List.of("<gray>Up " + upText + " <gray>| Dia <aqua>" + clock(dia));
        return List.of("<gray>Next upgrade: " + upText, "<gray>Diamonds in <aqua>" + clock(dia));
    }
}
