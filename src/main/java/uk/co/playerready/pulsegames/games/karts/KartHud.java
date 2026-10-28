package uk.co.playerready.pulsegames.games.karts;

/** Short HUD strings (Bedrock action bars truncate). Pure. */
final class KartHud {
    static final int INTERVAL_TICKS = 10, NOTICE_HOLD_TICKS = 30;
    private KartHud() {}

    static String line(int lap, int laps, int place, int field, String item, boolean boosted, int driftTier) {
        StringBuilder out = new StringBuilder();
        if (laps > 1) out.append("<yellow>L").append(Math.min(lap, laps)).append('/').append(laps).append("  ");
        out.append("<white>P").append(place).append('/').append(field);
        if (item != null) out.append("  <aqua>").append(item);
        if (driftTier > 0) out.append("  ").append(TIER_COLOUR[driftTier - 1]).append(KartDrift.TIER_NAME[driftTier - 1]);
        else if (boosted) out.append("  <gold>BOOST");
        return out.toString();
    }
    private static final String[] TIER_COLOUR = {"<blue>", "<gold>", "<light_purple>"};

    static boolean enteredFinalLap(int previousLap, int lap, int laps) { return laps > 1 && lap == laps && previousLap < lap; }
}
