package uk.co.playerready.pulsegames.core.events;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Pure wording helpers for events. */
public final class EventText {
    private EventText() { }

    public static String countdown(long seconds) {
        long s = Math.max(0, seconds);
        return "%d:%02d".formatted(s / 60, s % 60);
    }

    /** "18:00" today, otherwise "Sat 18:00", in the schedule's zone. */
    public static String when(Instant now, Instant start, ZoneId zone) {
        var local = start.atZone(zone);
        boolean today = local.toLocalDate().equals(now.atZone(zone).toLocalDate());
        return local.format(DateTimeFormatter.ofPattern(today ? "HH:mm" : "EEE HH:mm", Locale.UK));
    }

    /** "in 42 min", "in 1 h 5 min", "now". */
    public static String until(Instant now, Instant start) {
        long minutes = Math.max(0, Duration.between(now, start).toMinutes());
        if (minutes == 0) return Duration.between(now, start).isPositive() ? "in under a minute" : "now";
        if (minutes < 60) return "in " + minutes + " min";
        return "in " + minutes / 60 + " h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + " min");
    }

    public static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) return n + "th";
        return n + switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
    }

    /** One line per medal place, joint places joined: "1st: Ana, Bo". */
    public static List<String> podium(Map<String, Integer> places, Function<String, String> label) {
        List<String> lines = new ArrayList<>();
        for (int p = 1; p <= 3; p++) {
            final int wanted = p;
            List<String> names = places.entrySet().stream().filter(e -> e.getValue() == wanted)
                    .map(e -> label.apply(e.getKey())).toList();
            if (!names.isEmpty()) lines.add(ordinal(p) + ": " + String.join(", ", names));
        }
        return lines;
    }
}
