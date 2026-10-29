package uk.co.playerready.pulsegames.core.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionVisibilityTest {
    @Test
    void othersSeeReactionsByDefault() {
        assertTrue(SpectatorTools.showsReaction(false, false));
    }

    @Test
    void hideSettingSkipsOtherPlayersReactions() {
        assertFalse(SpectatorTools.showsReaction(false, true));
    }

    @Test
    void senderAlwaysSeesTheirOwnReaction() {
        assertTrue(SpectatorTools.showsReaction(true, true));
        assertTrue(SpectatorTools.showsReaction(true, false));
    }
}
