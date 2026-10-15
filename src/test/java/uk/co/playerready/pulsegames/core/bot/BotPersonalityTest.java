package uk.co.playerready.pulsegames.core.bot;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class BotPersonalityTest {
    @Test void namesAreDistinctClearlyMarkedAndSafeForPlayerProfiles() {
        var names = new HashSet<String>();
        for (int i = 0; i < 4096; i++) {
            String name = BotNames.at(i);
            assertTrue(name.matches("[A-Za-z0-9_]{1,16}"));
            assertTrue(name.endsWith("_NPC"));
            assertTrue(names.add(name), name);
            assertEquals(name, CitizensBotProvider.botName(name));
        }
    }
    @Test void retainsAttentionUntilAThreatIsSubstantiallyCloser() {
        assertTrue(BotPersonality.targetScore(100, true) < BotPersonality.targetScore(81, false));
        assertTrue(BotPersonality.targetScore(100, true) > BotPersonality.targetScore(25, false));
    }
    @Test void hesitationIsBoundedAndDifficultyDependent() {
        assertEquals(15, BotPersonality.reactionTicks(BotDifficulty.EASY, -9));
        assertEquals(20, BotPersonality.reactionTicks(BotDifficulty.EASY, 99));
        assertEquals(10, BotPersonality.reactionTicks(BotDifficulty.NORMAL, 0));
        assertEquals(5, BotPersonality.reactionTicks(BotDifficulty.HARD, 0));
        assertEquals(10, BotPersonality.reactionTicks(BotDifficulty.HARD, 5));
    }
}
