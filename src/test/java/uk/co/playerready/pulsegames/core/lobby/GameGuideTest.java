package uk.co.playerready.pulsegames.core.lobby;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameRegistry;
import uk.co.playerready.pulsegames.games.GameCatalog;
import static org.junit.jupiter.api.Assertions.*;

class GameGuideTest {
    @Test void everyGameHasAnAuthoredGoalAndTheSeasonalGamesExplainTheirControls() {
        var registry = new GameRegistry(); GameCatalog.registerAll(registry);
        for (var type : registry.all()) {
            assertNotEquals(type.description(), type.card().subtitle(), type.id() + " needs an authored card");
            assertFalse(GameGuide.steps(type).isEmpty());
        }
        assertTrue(String.join(" ",GameGuide.steps(registry.get("presentpanic"))).contains("toggle"));
        assertTrue(String.join(" ",GameGuide.steps(registry.get("grinchhunt"))).contains("escape"));
        assertTrue(GameGuide.rewards(73,19).contains("73 tokens"));
        assertTrue(GameGuide.rewards(73,19).contains("Practice rounds give no"));
    }
}
