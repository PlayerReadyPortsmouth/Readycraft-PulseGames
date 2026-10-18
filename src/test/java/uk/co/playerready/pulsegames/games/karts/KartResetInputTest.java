package uk.co.playerready.pulsegames.games.karts;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import io.papermc.paper.entity.TeleportFlag;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class KartResetInputTest {
 @Test void resetKeepsHeldDrivingButRequiresJumpReleaseBeforeDrifting() throws Exception {
  var game=mock(GameInstance.class);var world=mock(World.class);when(game.world()).thenReturn(world);
  var player=mock(Player.class);var seat=mock(ArmorStand.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getVehicle()).thenReturn(seat);
  when(game.spawnFor(player)).thenReturn(new Location(world,0,64,0));
  when(seat.teleport(any(Location.class),eq(TeleportFlag.EntityState.RETAIN_PASSENGERS))).thenReturn(true);
  var held=mock(Input.class);when(held.isForward()).thenReturn(true);when(held.isRight()).thenReturn(true);when(held.isJump()).thenReturn(true);when(player.getCurrentInput()).thenReturn(held);
  var logic=new PulseKartsGame(game);var kart=new PulseKartsGame.Kart(player,seat,()->{},new KartPhysics.State(0,64,0,0,8,0,true));
  var f=PulseKartsGame.class.getDeclaredField("karts");f.setAccessible(true);((Map<UUID,PulseKartsGame.Kart>)f.get(logic)).put(player.getUniqueId(),kart);
  f=PulseKartsGame.class.getDeclaredField("space");f.setAccessible(true);f.set(logic,(KartPhysics.CollisionSpace)(x,y,z,w,h)->y>=64);
  logic.onDeath(player,null);assertEquals(1,kart.input.throttle());assertEquals(1,kart.input.steering());assertFalse(kart.driftPressed);
  logic.onInput(player,held);assertFalse(kart.driftPressed);
  when(held.isJump()).thenReturn(false);logic.onInput(player,held);
  when(held.isJump()).thenReturn(true);logic.onInput(player,held);assertTrue(kart.driftPressed);
 }
}
