package uk.co.playerready.pulsegames.core.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Bounded, current operational snapshots. No persisted player skill or outcome history. */
public final class BotDiagnostics {
    private static final int LIST_LIMIT = 8;
    private BotDiagnostics() {}

    public record Snapshot(UUID id, String name, String gameId, String objective, BotDifficulty difficulty,
                           String target, String route, String state, String reason, long lastProgressAgeTicks) {
        public Snapshot {
            name = safe(name, "Unnamed bot");
            gameId = safe(gameId, "Unknown match");
            objective = safe(objective, "No objective");
            difficulty = difficulty == null ? BotDifficulty.NORMAL : difficulty;
            target = safe(target, "None");
            route = safe(route, "No route");
            state = safe(state, "Unknown");
            reason = safe(reason, "No issue reported");
            lastProgressAgeTicks = Math.max(0, lastProgressAgeTicks);
        }
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String clean = value.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").strip();
        if (clean.isBlank()) return fallback;
        return clean.length() > 160 ? clean.substring(0, 157) + "..." : clean;
    }

    /** A triage hint, never proof that navigation or an objective failed. */
    public static String assessment(Snapshot bot) {
        if ("WAITING_FOR_SAFE_STEP".equals(bot.state())) return "Inspect the next safe step, bridge materials and map route before increasing difficulty.";
        if ("STALLED".equals(bot.state()) && bot.lastProgressAgeTicks() >= 200)
            return "Inspect collision, route reachability and objective access; slow movement is not evidence of low combat skill.";
        return "No navigation fault inferred from this snapshot; intentional defence and hiding may be stationary.";
    }

    public static List<String> report(List<Snapshot> snapshots, String selector) {
        List<Snapshot> current = snapshots == null ? List.of() : snapshots.stream().filter(Objects::nonNull).toList();
        if (current.isEmpty()) return List.of("No active bot snapshots. Bots appear after their lobby fill delay.");
        if (selector == null || selector.isBlank()) {
            List<String> lines = new ArrayList<>();
            lines.add("Current bots: " + current.size() + ". Inspect one: /pulse bots inspect <name or UUID>");
            current.stream().limit(LIST_LIMIT).forEach(bot -> lines.add(bot.name() + " | " + bot.gameId()
                    + " | " + bot.state() + " | " + bot.difficulty().name().toLowerCase(Locale.ROOT)));
            if (current.size() > LIST_LIMIT) lines.add("Showing the first " + LIST_LIMIT + "; use a bot's name or UUID to inspect another.");
            return List.copyOf(lines);
        }
        String key = selector.strip();
        var matches = current.stream().filter(bot -> bot.name().equalsIgnoreCase(key)
                || (bot.id() != null && bot.id().toString().equalsIgnoreCase(key))).limit(2).toList();
        if (matches.isEmpty()) return List.of("No active bot matches that name or UUID. Use /pulse bots inspect to list current bots.");
        if (matches.size() > 1) return List.of("More than one bot has that name. Inspect using its UUID instead.");
        Snapshot bot = matches.getFirst();
        return List.of(
                "Bot: " + bot.name() + " (" + (bot.id() == null ? "unknown UUID" : bot.id()) + ")",
                "Match: " + bot.gameId() + " | Difficulty: " + bot.difficulty().name().toLowerCase(Locale.ROOT),
                "Objective: " + bot.objective() + " | Target: " + bot.target(),
                "Route: " + bot.route(),
                "State: " + bot.state() + " | Reason: " + bot.reason(),
                "Last movement progress: " + String.format(Locale.ROOT, "%.1f", bot.lastProgressAgeTicks() / 20.0) + " seconds ago",
                "Review: " + assessment(bot));
    }
}
