package uk.co.playerready.pulsegames.games.elytra;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ElytraRacePackTest {
    final World world = mock(World.class);
    final GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
    final Player player = mock(Player.class);

    @Test void speedReadoutConvertsBlocksPerSecondToKmh() {
        assertEquals(72, ElytraRacingGame.kmh(20));
    }

    @Test void ringPassBurstsParticlesAndChimes() {
        when(player.getLocation()).thenReturn(new Location(world, 0, 80, 0));
        when(game.world()).thenReturn(world);
        Cuboid ring = new Cuboid(5, 79, 0, 5, 82, 0);
        new ElytraRacingGame(game).onGatePassed(player, null, ring);
        verify(world).spawnParticle(eq(Particle.END_ROD), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(player).playSound(any(Location.class), eq("minecraft:block.amethyst_block.chime"), anyFloat(), anyFloat());
    }
}
