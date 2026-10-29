package uk.co.playerready.pulsegames.core.progression;

/** Pure XP and level maths (no Bukkit), so the whole table is unit tested. */
public final class XpRules {
    private XpRules() { }

    public static final int MAX_LEVEL = 100;
    /** A cosmetic unlocks every this many levels. */
    public static final int UNLOCK_EVERY = 5;
    public static final int FIRST_OF_DAY_BONUS = 25;
    public static final int KILL_XP = 2;
    public static final int KILL_XP_CAP = 20;
    /** Lifetime wins from before levels existed are worth this much each. */
    public static final int XP_PER_MIGRATED_WIN = 10;

    /** 1st 100, 2nd 60, 3rd 40, everyone else (and unplaced) 20. */
    public static int placementXp(int place) {
        return switch (place) { case 1 -> 100; case 2 -> 60; case 3 -> 40; default -> 20; };
    }

    public static int killXp(int kills) { return Math.min(KILL_XP_CAP, Math.max(0, kills) * KILL_XP); }

    public static int matchXp(int place, int kills, boolean firstOfDayInGame) {
        return placementXp(place) + killXp(kills) + (firstOfDayInGame ? FIRST_OF_DAY_BONUS : 0);
    }

    /** XP needed to finish level {@code n} (go from n to n + 1): 100 + 50 * (n - 1). */
    public static int xpToFinish(int n) { return 100 + 50 * (Math.max(1, n) - 1); }

    /** Total XP at which {@code level} is reached; level 1 starts at 0. */
    public static long xpForLevel(int level) {
        long steps = Math.max(1, Math.min(MAX_LEVEL, level)) - 1;
        return 100 * steps + 25 * steps * (steps - 1);
    }

    /** Where a total sits: the level, XP into it, XP the level needs (0 once capped). */
    public record Progress(int level, int into, int needed, boolean maxed) { }

    public static Progress progress(long totalXp) {
        long xp = Math.max(0, totalXp);
        int level = 1;
        while (level < MAX_LEVEL && xp >= xpForLevel(level + 1)) level++;
        if (level >= MAX_LEVEL) return new Progress(MAX_LEVEL, 0, 0, true);
        return new Progress(level, (int) (xp - xpForLevel(level)), xpToFinish(level), false);
    }

    public static int levelFor(long totalXp) { return progress(totalXp).level(); }

    /** Levels 5, 10, ... 100 each unlock one cosmetic. */
    public static boolean isUnlockLevel(int level) { return level >= UNLOCK_EVERY && level <= MAX_LEVEL && level % UNLOCK_EVERY == 0; }

    /** The next unlock level above {@code level}, or 0 when none is left. */
    public static int nextUnlockLevel(int level) {
        int next = (Math.max(0, level) / UNLOCK_EVERY + 1) * UNLOCK_EVERY;
        return next > MAX_LEVEL ? 0 : next;
    }

    /** Starting XP for a player who already has lifetime wins. */
    public static long migratedXp(int lifetimeWins) { return (long) Math.max(0, lifetimeWins) * XP_PER_MIGRATED_WIN; }

    /** "[12]" for the nametag, results and stats. */
    public static String tag(int level) { return "[" + level + "]"; }

    /** The same tag in MiniMessage, coloured by tier so a higher level stands out. */
    public static String colouredTag(int level) {
        String colour = level >= 100 ? "<red>" : level >= 75 ? "<gold>" : level >= 50 ? "<light_purple>"
                : level >= 25 ? "<aqua>" : level >= 10 ? "<green>" : "<gray>";
        return colour + tag(level) + "</" + colour.substring(1);
    }

    /** Text XP bar, for lore and Bedrock forms: {@code [######----] 60%}. */
    public static String bar(Progress p, int width) {
        int filled = p.maxed() ? width : p.needed() <= 0 ? 0 : Math.min(width, p.into() * width / p.needed());
        int percent = p.maxed() ? 100 : p.needed() <= 0 ? 0 : p.into() * 100 / p.needed();
        return "[" + "#".repeat(filled) + "-".repeat(width - filled) + "] " + percent + "%";
    }
}
