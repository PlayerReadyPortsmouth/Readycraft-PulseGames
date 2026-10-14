package uk.co.playerready.pulsegames.core.practice;

import uk.co.playerready.pulsegames.core.bot.BotDiagnostics;
import java.util.*;

/** Bounded observation report. A stalled sample is evidence for investigation, not a pathfinder diagnosis. */
public final class PracticeReport {
    private int samples, stalledSamples, blockedSamples, peakBots;
    private final Set<String> objectives = new LinkedHashSet<>();
    private final Set<String> reasons = new LinkedHashSet<>();
    public void sample(List<BotDiagnostics.Snapshot> bots) {
        samples++;
        peakBots = Math.max(peakBots, bots.size());
        for (var bot : bots) {
            if ("STALLED".equals(bot.state())) stalledSamples++;
            if ("WAITING_FOR_SAFE_STEP".equals(bot.state())) blockedSamples++;
            add(objectives, bot.objective());
            add(reasons, bot.reason());
        }
    }
    private static void add(Set<String> values, String value) {
        if (value != null && !value.isBlank() && values.size() < 16)
            values.add(value.replaceAll("[\\r\\n\\t]", " ").substring(0, Math.min(value.length(), 160)));
    }
    public List<String> lines(String reference, String outcome) {
        return List.of("Practice " + reference + " | " + outcome,
                "Samples=" + samples + "; peak bots=" + peakBots + "; stalled bot samples=" + stalledSamples
                        + "; blocked bot samples=" + blockedSamples,
                "Objectives observed: " + String.join("; ", objectives),
                "Reasons observed: " + String.join("; ", reasons),
                "Samples are every five seconds; zero observations do not prove a map is safe.");
    }
}
