package uk.co.playerready.pulsegames.core.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsFormatTest {
    @Test void winRateRoundsAndNeverDivides_ByZero() {
        assertEquals("0%", StatsFormat.winRate(0, 0));
        assertEquals("33%", StatsFormat.winRate(3, 1));
        assertEquals("100%", StatsFormat.winRate(2, 5));
    }

    @Test void onlyPlayedGamesMostPlayedFirst() {
        var a = new StatsFormat.Row("a", "Beta", 2, 1);
        var b = new StatsFormat.Row("b", "Alpha", 2, 0);
        var c = new StatsFormat.Row("c", "Zero", 0, 0);
        var d = new StatsFormat.Row("d", "Busy", 9, 4);
        assertEquals(List.of(d, b, a), StatsFormat.playedGames(List.of(a, b, c, d)));
    }

    @Test void pluralsAreRight() {
        assertEquals("1 win", StatsFormat.wins(1));
        assertEquals("0 wins", StatsFormat.wins(0));
        assertEquals("1 game", StatsFormat.games(1));
    }
}
