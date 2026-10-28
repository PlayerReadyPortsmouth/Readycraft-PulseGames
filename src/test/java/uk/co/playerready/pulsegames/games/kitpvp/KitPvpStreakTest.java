package uk.co.playerready.pulsegames.games.kitpvp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KitPvpStreakTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    @Test void goldenAppleAtThreeSpeedAtFiveNothingBetween() {
        KillStreaks streaks = new KillStreaks();
        KillStreaks.Reward[] seen = new KillStreaks.Reward[6];
        for (int i = 1; i <= 6; i++) seen[i - 1] = KillStreaks.reward(streaks.kill(a));
        assertArrayEquals(new KillStreaks.Reward[]{KillStreaks.Reward.NONE, KillStreaks.Reward.NONE,
                KillStreaks.Reward.GOLDEN_APPLE, KillStreaks.Reward.NONE, KillStreaks.Reward.SPEED,
                KillStreaks.Reward.NONE}, seen);
    }

    @Test void dyingResetsOnlyThatPlayersStreak() {
        KillStreaks streaks = new KillStreaks();
        streaks.kill(a); streaks.kill(a); streaks.kill(b);
        streaks.died(a);
        assertEquals(0, streaks.streak(a));
        assertEquals(1, streaks.streak(b));
        assertEquals(1, streaks.kill(a));
    }

    @Test void bountyNeedsThreeKillsAStrictLeadAndIsCapped() {
        assertTrue(BountyBoard.leader(List.of()).isEmpty());
        assertTrue(BountyBoard.leader(List.of(Map.entry(a, 2))).isEmpty());
        assertEquals(a, BountyBoard.leader(List.of(Map.entry(a, 3), Map.entry(b, 2))).orElseThrow());
        assertTrue(BountyBoard.leader(List.of(Map.entry(a, 4), Map.entry(b, 4))).isEmpty());
        BountyBoard board = new BountyBoard();
        for (int i = 0; i < BountyBoard.MAX_CLAIMS; i++) assertTrue(board.claim(b));
        assertFalse(board.claim(b));
        assertTrue(board.claim(a));
    }
}
