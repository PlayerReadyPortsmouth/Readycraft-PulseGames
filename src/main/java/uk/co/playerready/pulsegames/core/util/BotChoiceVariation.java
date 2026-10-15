package uk.co.playerready.pulsegames.core.util;

import java.util.Objects;
import java.util.UUID;

/** Stable tie-breaks, not random changes of mind on every navigation tick. */
public final class BotChoiceVariation {
    private BotChoiceVariation() {}
    public static double preference(UUID bot, int x, int z, int round) {
        return Math.floorMod(Objects.hash(bot, x, z, round), 1000) / 4000.0;
    }
}
