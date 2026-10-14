package uk.co.playerready.pulsegames.core.practice;

import uk.co.playerready.pulsegames.core.bot.BotDiagnostics;
import java.util.*;

/** Bounded observation report. A stalled sample is evidence for investigation, not a pathfinder diagnosis. */
public final class PracticeReport {
    private int samples, stalledSamples, blockedSamples, peakBots, botSamples, longestIssueRun;
    private final Map<UUID,Integer> issueRuns = new HashMap<>();
    private final Map<uk.co.playerready.pulsegames.core.bot.BotDifficulty,Integer> difficulties = new EnumMap<>(uk.co.playerready.pulsegames.core.bot.BotDifficulty.class);
    private Integer testerEliminatedAt;
    private final Set<String> objectives = new LinkedHashSet<>();
    private final Set<String> reasons = new LinkedHashSet<>();
    public void sample(List<BotDiagnostics.Snapshot> bots) {
        samples++;
        Set<UUID> seen = new HashSet<>();
        peakBots = Math.max(peakBots, bots.size());
        for (var bot : bots) {
            botSamples++;
            difficulties.merge(bot.difficulty(), 1, Integer::sum);
            if (bot.id() != null && (issueRuns.containsKey(bot.id()) || issueRuns.size() < 64)) {
                seen.add(bot.id());
                boolean issue = "STALLED".equals(bot.state()) || "WAITING_FOR_SAFE_STEP".equals(bot.state());
                int run = issue ? issueRuns.getOrDefault(bot.id(), 0) + 1 : 0;
                issueRuns.put(bot.id(), run); longestIssueRun = Math.max(longestIssueRun, run);
            }
            if ("STALLED".equals(bot.state())) stalledSamples++;
            if ("WAITING_FOR_SAFE_STEP".equals(bot.state())) blockedSamples++;
            add(objectives, bot.objective());
            add(reasons, bot.reason());
        }
        issueRuns.keySet().retainAll(seen);
    }
    private static void add(Set<String> values, String value) {
        if (value != null && !value.isBlank() && values.size() < 16)
            values.add(value.replaceAll("[\\r\\n\\t]", " ").substring(0, Math.min(value.length(), 160)));
    }
    /** Observation only: early elimination can be legitimate and never updates ranked or adaptive history. */
    public void testerEliminated(int elapsedSeconds) {
        if (elapsedSeconds >= 0 && testerEliminatedAt == null) testerEliminatedAt = elapsedSeconds;
    }
    public List<String> findings() {
        List<String> findings = new ArrayList<>();
        if (longestIssueRun >= 3) findings.add("Repeated navigation issue across " + longestIssueRun
                + " consecutive observations for one bot: inspect route, collision and objective access before tuning combat.");
        if (blockedSamples > 0) findings.add("Safe-step waits observed: check gaps, bridge materials and reachable map objectives.");
        if (testerEliminatedAt != null && testerEliminatedAt <= 60) findings.add("Tester eliminated at " + testerEliminatedAt
                + "s: repeat on easy with the same kit/map and inspect encounter timing before concluding novice pressure is excessive.");
        if (botSamples == 0) findings.add("No bot observations; no difficulty or path conclusion is available.");
        findings.add("Observed objectives do not prove completion; verify scoring, captures and round-ending objectives in the match events.");
        return List.copyOf(findings);
    }
    public List<String> lines(String reference, String outcome) {
        List<String> lines = new ArrayList<>(List.of("Practice " + reference + " | " + outcome,
                "Samples=" + samples + "; peak bots=" + peakBots + "; stalled bot samples=" + stalledSamples
                        + "; blocked bot samples=" + blockedSamples,
                "Objectives observed: " + String.join("; ", objectives),
                "Reasons observed: " + String.join("; ", reasons),
                "Samples are every five seconds; zero observations do not prove a map is safe."));
        lines.add("Difficulty bot samples: " + difficulties + "; total bot observations=" + botSamples
                + "; longest repeated issue=" + longestIssueRun + " observations.");
        lines.addAll(findings());
        lines.add("Practice does not train adaptive difficulty. Change one scenario setting at a time and compare repeat reports.");
        return List.copyOf(lines);
    }
}
