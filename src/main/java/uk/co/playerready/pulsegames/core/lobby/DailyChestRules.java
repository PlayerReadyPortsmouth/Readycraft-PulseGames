package uk.co.playerready.pulsegames.core.lobby;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

/** The daily reward chest: one claim per player per calendar day, resetting at local midnight. */
public final class DailyChestRules {
    private DailyChestRules() { }

    public static boolean canClaim(LocalDate lastClaim, LocalDate today) {
        return lastClaim == null || lastClaim.isBefore(today);
    }

    public static Duration untilNext(LocalDateTime now) {
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay());
    }

    /** "5h 12m" or "12m"; never less than a minute. */
    public static String describe(Duration wait) {
        long minutes = Math.max(1, (wait.getSeconds() + 59) / 60);
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    /** Small random extra, min to max inclusive. */
    public static int extra(Random random, int min, int max) {
        int lo = Math.max(0, Math.min(min, max)), hi = Math.max(lo, Math.max(min, max));
        return lo + random.nextInt(hi - lo + 1);
    }
}
