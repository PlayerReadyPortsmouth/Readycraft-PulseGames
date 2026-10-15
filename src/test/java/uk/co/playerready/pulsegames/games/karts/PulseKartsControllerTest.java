package uk.co.playerready.pulsegames.games.karts;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import uk.co.playerready.pulsegames.core.game.*;
import io.papermc.paper.entity.TeleportFlag;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PulseKartsControllerTest {
 GameInstance game; PulseKartsGame logic; Player player; ArmorStand seat; AutoCloseable visual; PulseKartsGame.Kart kart;
 @BeforeEach void setup() throws Exception {
  game=mock(GameInstance.class,RETURNS_DEEP_STUBS);player=mock(Player.class);seat=mock(ArmorStand.class);visual=mock(AutoCloseable.class);
  when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
  when(player.getVehicle()).thenReturn(seat);when(seat.isValid()).thenReturn(true);when(game.isActive(player)).thenReturn(true);
  when(game.state()).thenReturn(GameState.RUNNING);when(game.world()).thenReturn(mock(World.class));
  when(seat.teleport(any(Location.class),eq(TeleportFlag.EntityState.RETAIN_PASSENGERS))).thenReturn(true);
  logic=new PulseKartsGame(game);kart=new PulseKartsGame.Kart(player,seat,visual,new KartPhysics.State(0,64,0,0,0,0,true));
  var f=PulseKartsGame.class.getDeclaredField("karts");f.setAccessible(true);((Map<UUID,PulseKartsGame.Kart>)f.get(logic)).put(player.getUniqueId(),kart);
  f=PulseKartsGame.class.getDeclaredField("space");f.setAccessible(true);f.set(logic,(KartPhysics.CollisionSpace)(x,y,z,w,h)->y>=64);
 }
 void drive() throws Exception {var m=PulseKartsGame.class.getDeclaredMethod("drive");m.setAccessible(true);m.invoke(logic);}
 @Test void inputMovesOwnedSeatWithoutPlayerTeleportOrBoatPhysics() throws Exception {
  var input=mock(Input.class);when(input.isForward()).thenReturn(true);logic.onInput(player,input);drive();
  assertTrue(kart.motion.z()>0);verify(seat).teleport(any(Location.class),eq(TeleportFlag.EntityState.RETAIN_PASSENGERS));
  verify(player,never()).teleport(any(Location.class));
 }
 @Test void gridFreezePreventsMovement() throws Exception {
  kart.input=new KartPhysics.Input(1,1);when(game.frozen()).thenReturn(true);drive();assertEquals(0,kart.motion.speed());
  verify(seat,never()).teleport(any(Location.class),eq(TeleportFlag.EntityState.RETAIN_PASSENGERS));
 }
 @Test void departureAndRepeatedCleanupRemoveOnlyOwnedSeat() throws Exception {
  logic.onQuit(player);logic.onCleanup();verify(visual,times(1)).close();verify(seat,times(1)).remove();
  verify(game.world(),never()).getEntitiesByClass(Boat.class);
 }
 @Test void turboIsSingleUseAndExpiredEffectsReturnNormalSpeed() {
  kart.item=PulseKartsGame.Item.TURBO;var event=mock(PlayerInteractEvent.class);
  when(event.getPlayer()).thenReturn(player);when(event.getHand()).thenReturn(EquipmentSlot.HAND);when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
  var item=mock(ItemStack.class);when(item.getType()).thenReturn(Material.SUGAR);when(event.getItem()).thenReturn(item);logic.onInteract(event);assertNull(kart.item);assertEquals(40,kart.boostUntil);
  assertEquals(1.55,PulseKartsGame.speedMultiplier(39,kart.boostUntil,0));assertEquals(1,PulseKartsGame.speedMultiplier(40,kart.boostUntil,0));
  assertEquals(.4,PulseKartsGame.speedMultiplier(10,0,20));
 }
}
