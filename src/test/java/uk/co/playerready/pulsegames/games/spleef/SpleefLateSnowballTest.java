package uk.co.playerready.pulsegames.games.spleef;

import org.bukkit.block.Block;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.games.common.RoundController;

import static org.mockito.Mockito.*;

class SpleefLateSnowballTest {
    @Test void aSnowballLandingBetweenRoundsLeavesTheFloorAlone() throws Exception {
        SpleefGame game = new SpleefGame(mock(GameInstance.class));
        RoundController rounds = mock(RoundController.class);
        when(rounds.active()).thenReturn(false);
        var field = SpleefGame.class.getDeclaredField("rounds");
        field.setAccessible(true);
        field.set(game, rounds);
        ProjectileHitEvent event = mock(ProjectileHitEvent.class);
        Block block = mock(Block.class);
        when(event.getEntity()).thenReturn(mock(Snowball.class));
        when(event.getHitBlock()).thenReturn(block);
        game.onProjectileHit(event);
        verify(block, never()).setType(any());
    }
}
