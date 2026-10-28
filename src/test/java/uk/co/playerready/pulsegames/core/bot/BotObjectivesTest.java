package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class BotObjectivesTest {
    @Test void perBotGuardPointDefaultsToTheSharedOneSoVillagerDefenseIsUnchanged() {
        Location shared = mock(Location.class);
        BotObjectives objectives = new BotObjectives() {
            @Override public Collection<? extends LivingEntity> botEnemies() { return List.of(); }
            @Override public Location botGuardPoint() { return shared; }
        };
        assertSame(shared, objectives.botGuardPoint(mock(Player.class)));
    }
}
