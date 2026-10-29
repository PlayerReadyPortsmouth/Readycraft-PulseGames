package uk.co.playerready.pulsegames.games.blockparty;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import static org.junit.jupiter.api.Assertions.*;

class BlockPartyTacticsTest {
    @Test void reactionGetsFasterWithTierAndNeverEatsTheWalkingAllowance() {
        var easy = BlockPartyTactics.plan(0, 1, BotDifficulty.EASY, false, 140, bound -> 3);
        var hard = BlockPartyTactics.plan(0, 1, BotDifficulty.HARD, false, 140, bound -> 3);
        assertTrue(easy.reactTicks() > hard.reactTicks());
        assertTrue(easy.reactTicks() <= BlockPartyTactics.MAX_REACT_TICKS);
        var short1 = BlockPartyTactics.plan(0, 9, BotDifficulty.EASY, true, 20, bound -> 5);
        assertTrue(short1.reactTicks() <= 5, "a one second hardcore call leaves little time to dither");
    }

    @Test void easyBotsSometimesMisreadAndHardBotsNever() {
        assertTrue(BlockPartyTactics.plan(0, 1, BotDifficulty.EASY, false, 140, bound -> 0).wrongGuess());
        assertFalse(BlockPartyTactics.plan(0, 1, BotDifficulty.EASY, false, 140, bound -> 50).wrongGuess());
        assertFalse(BlockPartyTactics.plan(0, 1, BotDifficulty.HARD, true, 40, bound -> 0).wrongGuess());
    }

    @Test void aMisreadIsCorrectedHalfWayThroughWhenTheBotNoticesOtherwiseItCommits() {
        var notices = BlockPartyTactics.plan(0, 1, BotDifficulty.EASY, false, 140, bound -> 0); // wrong, and rolls "notice"
        assertEquals(BlockPartyTactics.Step.WAIT, BlockPartyTactics.step(notices, 0));
        assertEquals(BlockPartyTactics.Step.WRONG, BlockPartyTactics.step(notices, notices.reactTicks() + 1));
        assertEquals(BlockPartyTactics.Step.RIGHT, BlockPartyTactics.step(notices, 80));
        var commits = new BlockPartyTactics.Plan(0, 1, 5, true, Integer.MAX_VALUE);
        assertEquals(BlockPartyTactics.Step.WRONG, BlockPartyTactics.step(commits, 139));
    }

    @Test void aNormalPlanGoesStraightToTheRightColourAfterTheDelay() {
        var plan = BlockPartyTactics.plan(0, 1, BotDifficulty.NORMAL, false, 140, bound -> 50);
        assertEquals(BlockPartyTactics.Step.RIGHT, BlockPartyTactics.step(plan, plan.reactTicks()));
    }
}
