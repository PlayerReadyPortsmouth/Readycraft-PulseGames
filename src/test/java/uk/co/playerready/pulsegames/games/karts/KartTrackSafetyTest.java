package uk.co.playerready.pulsegames.games.karts;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class KartTrackSafetyTest {
 @Test void overlappingFirstGateRejectedButTouchingBlockFacesAndHeightSeparationAllowed() {
  var arena=mock(Arena.class);when(arena.region("finish")).thenReturn(new Cuboid(0,64,0,2,66,2));
  when(arena.regionsByPrefix("checkpoint")).thenReturn(List.of(new Cuboid(2,64,2,4,66,4)));assertNotNull(KartTrackSafety.validationError(arena));
  when(arena.regionsByPrefix("checkpoint")).thenReturn(List.of(new Cuboid(3,64,0,4,66,2)));assertNull(KartTrackSafety.validationError(arena));
  when(arena.regionsByPrefix("checkpoint")).thenReturn(List.of(new Cuboid(0,67,0,2,69,2)));assertNull(KartTrackSafety.validationError(arena));
 }
 @Test void absentGateCannotCreateLapFarmingTrack() {
  var arena=mock(Arena.class);when(arena.regionsByPrefix("checkpoint")).thenReturn(List.of());assertNotNull(KartTrackSafety.validationError(arena));
 }
}
