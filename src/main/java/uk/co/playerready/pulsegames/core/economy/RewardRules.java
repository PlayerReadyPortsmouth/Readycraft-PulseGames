package uk.co.playerready.pulsegames.core.economy;

/** Pure token rules: kill cap and win-streak bonus. */
public final class RewardRules {
    private RewardRules() { }

    /** Tokens to pay for the next kill so that kills in one match never total more than {@code cap}. */
    public static int killPayout(int paidSoFar, int reward, int cap) {
        if (reward <= 0 || cap <= 0) return 0;
        return Math.max(0, Math.min(reward, cap - paidSoFar));
    }

    /** Bonus for reaching {@code streak} consecutive wins: nothing for 1, then {@code step} per extra win up to {@code max}. */
    public static int streakBonus(int streak, int step, int max) {
        if (streak < 2 || step <= 0) return 0;
        return Math.min(Math.max(max, 0), step * (streak - 1));
    }

    /** Streak lengths that get an announcement. */
    public static boolean announceStreak(int streak) {
        return streak == 3 || streak == 5 || streak == 10;
    }
}
