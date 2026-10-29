package uk.co.playerready.pulsegames.games.koth;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KothBotHillTest {
    private GameInstance game(Map<String, Cuboid> regions) {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.mode()).thenReturn(GameMode.ffa("solo", "Solo", 2, 12));
        when(game.arena().settings()).thenReturn(new MemoryConfiguration());
        when(game.arena().regions()).thenReturn(regions);
        when(game.arena().region(anyString())).thenReturn(null);
        regions.forEach((name, region) -> when(game.arena().region(name)).thenReturn(region));
        return game;
    }

    @Test void singleHillMapUsesTheHillRegion() {
        var hill = new Cuboid(0, 60, 0, 5, 65, 5);
        var logic = new KothGame(game(Map.of("hill", hill)));
        assertSame(hill, logic.botHill(), "before the round starts");
        logic.onStart();
        assertSame(hill, logic.botHill());
    }

    @Test void rotatingMapWithoutAHillRegionFollowsTheActiveHill() {
        var one = new Cuboid(0, 60, 0, 5, 65, 5);
        var two = new Cuboid(20, 60, 20, 25, 65, 25);
        var three = new Cuboid(40, 60, 40, 45, 65, 45);
        GameInstance game = game(Map.of("hill1", one, "hill2", two, "hill3", three));
        var logic = new KothGame(game);
        logic.onStart();
        assertSame(one, logic.botHill(), "hill1 is first");
        Cuboid first = logic.botHill();
        for (int second = 1; second <= 200 && logic.botHill() == first; second++) logic.onSecond(second);
        assertSame(two, logic.botHill(), "the bot follows the rotation to hill2");
    }

    @Test void mapWithNoHillAtAllHasNoBotHill() {
        assertNull(new KothGame(game(Map.of())).botHill());
    }
}
