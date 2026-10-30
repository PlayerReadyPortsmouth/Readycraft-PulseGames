package uk.co.playerready.pulsegames.core.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Admin verbs for testing progression: {@code xp <player> add|set <n>}, {@code level <player> set <n>|reset},
 * {@code namestyle <player> grant|revoke <id|all>} and {@code progression <player>}. Pure logic (no Bukkit);
 * the server side sits behind {@link Backend} so parsing, thresholds and the unlock path are unit tested.
 */
public final class ProgressionAdmin {
    public enum Verb { XP_ADD, XP_SET, LEVEL_SET, LEVEL_RESET, STYLE_GRANT, STYLE_REVOKE, SHOW }

    /** A parsed command, or {@code error} set (and everything else empty). */
    public record Parsed(Verb verb, String target, long number, String styleId, String error) {
        static Parsed fail(String error) { return new Parsed(null, null, 0, null, error); }
    }

    public static final long MAX_XP = 1_000_000;
    public static final List<String> VERBS = List.of("xp", "level", "namestyle", "progression");

    public interface Backend {
        long xp(UUID player);
        void setXp(UUID player, long xp);
        /** Runs the real level-up unlock path (cosmetics, name-style grants) up to {@code level}; returns what was newly unlocked. */
        List<String> unlock(UUID player, int level);
        void refresh(UUID player);
        /** Removes the level-reward cosmetics from the player's unlocks (not LuckPerms); returns how many. */
        int clearUnlocks(UUID player);
        int unlockedCount(UUID player);
        List<String> styleIds();
        boolean holds(UUID player, String styleId);
        /** False when LuckPerms is unavailable. */
        boolean grant(UUID player, String styleId);
        boolean revoke(UUID player, String styleId);
    }

    private final Backend backend;
    private final Consumer<String> audit;

    public ProgressionAdmin(Backend backend, Consumer<String> audit) {
        this.backend = backend;
        this.audit = audit;
    }

    public static final String USAGE = "Usage: /pulse xp <player> add|set <n> | level <player> set <n>|reset | "
            + "namestyle <player> grant|revoke <id|all> | progression <player>";

    /** {@code args} start at the verb (a leading "admin" is stripped by the caller). */
    public static Parsed parse(String[] args) {
        if (args.length < 2) return Parsed.fail(USAGE);
        String verb = args[0].toLowerCase(Locale.ROOT), target = args[1];
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
        switch (verb) {
            case "progression":
                return args.length == 2 ? new Parsed(Verb.SHOW, target, 0, null, null) : Parsed.fail(USAGE);
            case "xp": {
                if (args.length != 4 || !action.equals("add") && !action.equals("set")) return Parsed.fail(USAGE);
                Long n = number(args[3]);
                if (n == null || n < 0 || n > MAX_XP) return Parsed.fail("XP must be a whole number from 0 to " + MAX_XP + ".");
                return new Parsed(action.equals("add") ? Verb.XP_ADD : Verb.XP_SET, target, n, null, null);
            }
            case "level": {
                if (action.equals("reset") && args.length == 3) return new Parsed(Verb.LEVEL_RESET, target, 0, null, null);
                if (!action.equals("set") || args.length != 4) return Parsed.fail(USAGE);
                Long n = number(args[3]);
                if (n == null || n < 1 || n > XpRules.MAX_LEVEL) return Parsed.fail("Level must be a whole number from 1 to " + XpRules.MAX_LEVEL + ".");
                return new Parsed(Verb.LEVEL_SET, target, n, null, null);
            }
            case "namestyle": {
                if (args.length != 4 || !action.equals("grant") && !action.equals("revoke")) return Parsed.fail(USAGE);
                return new Parsed(action.equals("grant") ? Verb.STYLE_GRANT : Verb.STYLE_REVOKE, target, 0, args[3].toLowerCase(Locale.ROOT), null);
            }
            default:
                return Parsed.fail(USAGE);
        }
    }

    private static Long number(String text) {
        try { return Long.parseLong(text); } catch (NumberFormatException e) { return null; }
    }

    /** Runs a parsed command against an online player; returns MiniMessage lines for the sender. */
    public List<String> run(String actor, UUID player, String name, Parsed p) {
        List<String> out = new ArrayList<>();
        switch (p.verb()) {
            case XP_ADD, XP_SET -> {
                long before = backend.xp(player);
                long after = p.verb() == Verb.XP_ADD ? Math.min(MAX_XP * 10, before + p.number()) : p.number();
                backend.setXp(player, after);
                int from = XpRules.levelFor(before), to = XpRules.levelFor(after);
                audit(actor, p.verb() == Verb.XP_ADD ? "xp-add" : "xp-set", name, String.valueOf(p.number()));
                out.add("<green>" + name + " now has " + after + " XP (level " + to + ").");
                if (to > from) unlockLines(out, backend.unlock(player, to));
                backend.refresh(player);
            }
            case LEVEL_SET -> {
                int level = (int) p.number();
                backend.setXp(player, XpRules.xpForLevel(level));
                audit(actor, "level-set", name, String.valueOf(level));
                out.add("<green>" + name + " is now level " + level + " with " + XpRules.xpForLevel(level) + " XP.");
                unlockLines(out, backend.unlock(player, level));
                backend.refresh(player);
                out.add("<gray>Rewards already earned are kept; use /pulse level " + name + " reset to clear them.");
            }
            case LEVEL_RESET -> {
                backend.setXp(player, 0);
                int cleared = backend.clearUnlocks(player);
                backend.refresh(player);
                audit(actor, "level-reset", name, "0");
                out.add("<green>" + name + " reset to level 1 with 0 XP; " + cleared + " cosmetic unlocks cleared.");
                out.add("<yellow>LuckPerms name-style permissions were not touched. Use /pulse namestyle " + name + " revoke all to remove them.");
            }
            case STYLE_GRANT, STYLE_REVOKE -> {
                boolean grant = p.verb() == Verb.STYLE_GRANT;
                List<String> ids = p.styleId().equals("all") ? backend.styleIds() : List.of(p.styleId());
                if (!p.styleId().equals("all") && !backend.styleIds().contains(p.styleId())) {
                    return List.of("<red>Unknown name style '" + p.styleId() + "'. Known: " + String.join(", ", backend.styleIds()));
                }
                int done = 0;
                for (String id : ids) if (grant ? backend.grant(player, id) : backend.revoke(player, id)) done++;
                if (done == 0) return List.of("<red>LuckPerms is not available, nothing changed.");
                audit(actor, grant ? "namestyle-grant" : "namestyle-revoke", name, p.styleId());
                out.add("<green>" + (grant ? "Granted " : "Revoked ") + done + " name style" + (done == 1 ? "" : "s") + (grant ? " to " : " from ") + name + ".");
            }
            case SHOW -> {
                XpRules.Progress progress = XpRules.progress(backend.xp(player));
                out.add("<aqua>" + name + ": level " + progress.level() + ", " + backend.xp(player) + " XP, "
                        + (progress.maxed() ? "max level" : "next level at " + XpRules.xpForLevel(progress.level() + 1) + " XP")
                        + ", " + backend.unlockedCount(player) + " cosmetics unlocked.");
                List<String> held = backend.styleIds().stream().filter(id -> backend.holds(player, id)).toList();
                out.add("<gray>Name styles held (" + held.size() + "/" + backend.styleIds().size() + "): "
                        + (held.isEmpty() ? "none" : String.join(", ", held)));
            }
        }
        return out;
    }

    private static void unlockLines(List<String> out, List<String> unlocked) {
        out.add(unlocked.isEmpty() ? "<gray>Nothing new unlocked."
                : "<green>Unlocked " + unlocked.size() + ": <yellow>" + String.join(", ", unlocked));
    }

    private void audit(String actor, String verb, String target, String value) {
        audit.accept("PROGRESSION " + actor + " " + verb + " " + target + " " + value);
    }
}
