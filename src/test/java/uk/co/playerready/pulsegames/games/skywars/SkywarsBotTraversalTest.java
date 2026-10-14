package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkywarsBotTraversalTest {
    static final class Fixture {
        final SkywarsBotTerrainTest.Fixture terrain=new SkywarsBotTerrainTest.Fixture();
        final Player bot=mock(Player.class); final GameInstance game=mock(GameInstance.class);
        final SkywarsBotTraversal traversal=new SkywarsBotTraversal();
        final AtomicReference<Location> position=new AtomicReference<>(terrain.at(.5,.5));
        final Location landing;
        Fixture() {
            when(bot.getUniqueId()).thenReturn(java.util.UUID.randomUUID()); when(bot.getWorld()).thenReturn(terrain.world);
            when(bot.getLocation()).thenAnswer(i->position.get().clone());
            when(terrain.world.getBlockAt(1,64,0)).thenAnswer(i->terrain.block(Material.STONE,1,64,0));
            landing=traversal.start(game,bot,terrain.at(1.5,.5),100);
            assertNotNull(landing);
        }
    }
    @Test void retainsCheckedLandingDuringOriginalFlightAndReturnsDefensiveCopy() {
        var f=new Fixture(); f.position.set(new Location(f.terrain.world,.8,64.7,.5));
        Location first=f.traversal.airborne(f.bot,105);
        assertEquals(f.landing,first); first.add(100,0,0);
        assertEquals(f.landing,f.traversal.airborne(f.bot,110));
        verify(f.bot).setJumping(true); verify(f.bot,never()).setJumping(false);
    }
    @Test void sidewaysKnockbackAbortsInsteadOfSteeringAcrossUncheckedVoid() {
        var f=new Fixture(); f.position.set(new Location(f.terrain.world,.8,64.7,1.2));
        assertNull(f.traversal.airborne(f.bot,105)); verify(f.bot).setJumping(false);
        f.position.set(new Location(f.terrain.world,.8,64.7,.5));
        assertNull(f.traversal.airborne(f.bot,110));
    }
    @Test void removedLandingSupportAbortsFlight() {
        var f=new Fixture(); f.position.set(new Location(f.terrain.world,.8,64.7,.5));
        when(f.terrain.world.getBlockAt(1,64,0)).thenAnswer(i->f.terrain.block(Material.AIR,1,64,0));
        assertNull(f.traversal.airborne(f.bot,105)); verify(f.bot).setJumping(false);
    }
}
