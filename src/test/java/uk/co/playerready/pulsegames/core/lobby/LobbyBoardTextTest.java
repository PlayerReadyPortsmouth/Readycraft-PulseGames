package uk.co.playerready.pulsegames.core.lobby;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LobbyBoardTextTest {
    @Test void titleThenTopThreeAndNoMarkupFromNames() {
        var lines = LobbyBoardText.lines("T", List.of(new LobbyBoardText.Line("A<b>", "9"), new LobbyBoardText.Line("B", "5"),
                new LobbyBoardText.Line("C", "2"), new LobbyBoardText.Line("D", "1")), 3);
        assertEquals(4, lines.size());
        assertTrue(lines.get(1).contains("1. <white>Ab "));
        assertFalse(lines.get(1).contains("<b>"));
    }

    @Test void emptyBoardAndNpcCounts() {
        assertEquals(2, LobbyBoardText.lines("T", List.of(), 3).size());
        assertEquals("<gray>3 in queue · 7 playing", LobbyBoardText.npcCounts(3, 7));
    }
}
