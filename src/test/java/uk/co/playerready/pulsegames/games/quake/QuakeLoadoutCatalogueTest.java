package uk.co.playerready.pulsegames.games.quake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuakeLoadoutCatalogueTest {
    @Test void loadoutTilesFitTheirTabsAndHaveWords() {
        assertTrue(QuakeGame.Weapon.values().length <= 18);
        assertTrue(QuakeGame.Ability.values().length >= 3, "two slots need a real choice");
        assertTrue(QuakeGame.Ability.values().length <= 18);
        for (var w : QuakeGame.Weapon.values()) assertFalse(w.label.isBlank());
        for (var a : QuakeGame.Ability.values()) {
            assertFalse(a.label.isBlank());
            assertTrue(a.cooldown >= 20 && a.cooldown % 20 == 0, a.name() + " cooldown shows as whole seconds");
        }
    }
}
