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
  when(game.everyone()).thenReturn(List.of());
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
 void bot() throws Exception {
  kart.bot=new KartBotDriver(player.getUniqueId());
  when(game.plugin().bots().player(player.getUniqueId())).thenReturn(player);
  when(game.plugin().bots().player(player.getUniqueId())).thenReturn(player);
  var f=PulseKartsGame.class.getDeclaredField("botPoints");f.setAccessible(true);
  f.set(logic,List.of(new Location(game.world(),0,64,30),new Location(game.world(),0,64,60),new Location(game.world(),30,64,60)));
  when(game.plugin().bots().difficultyFor(game,null)).thenReturn(uk.co.playerready.pulsegames.core.bot.BotDifficulty.NORMAL);
 }
 @Test void frozenBotCannotSteerAdvanceOrSpendPowerups() throws Exception {
  bot();when(game.frozen()).thenReturn(true);kart.item=PulseKartsGame.Item.TURBO;
  drive();assertEquals(0,kart.waypoint);assertEquals(0,kart.motion.speed());assertEquals(PulseKartsGame.Item.TURBO,kart.item);
  verify(seat,never()).teleport(any(Location.class),eq(TeleportFlag.EntityState.RETAIN_PASSENGERS));
 }
 @Test void botMovesThroughExactlyTheOrdinaryChassisIntegrator() throws Exception {
  bot();var initial=kart.motion;drive();
  assertEquals(KartPhysics.tick(initial,kart.input,1,(x,y,z,w,h)->y>=64),kart.motion);
  assertTrue(kart.motion.z()>0);verify(player,never()).teleport(any(Location.class));
 }
 @Test void externalInputCannotOverrideBotAndRouteAdvancesOnlyInOrder() throws Exception {
  bot();var input=mock(Input.class);when(input.isBackward()).thenReturn(true);logic.onInput(player,input);
  assertEquals(0,kart.input.throttle());
  kart.motion=new KartPhysics.State(0,64,30,0,0,0,true);drive();assertEquals(1,kart.waypoint);
  kart.motion=new KartPhysics.State(30,64,60,0,0,0,true);drive();assertEquals(1,kart.waypoint);
 }
 @Test void botUsesCollectedTurboOnlyOnClearStraightAndCleansUpOnce() throws Exception {
  bot();kart.motion=new KartPhysics.State(0,64,0,0,6,0,true);kart.item=PulseKartsGame.Item.TURBO;
  drive();assertNull(kart.item);assertEquals(41,kart.boostUntil);
  logic.onEliminated(player);logic.onCleanup();verify(visual,times(1)).close();verify(seat,times(1)).remove();
 }
 @Test void obstructedCheckpointFallbackRestartsBotRouteAtGrid() throws Exception {
  bot();kart.waypoint=2;var grid=new Location(game.world(),0,64,0);when(game.spawnFor(player)).thenReturn(grid);
  var pc=Class.forName("uk.co.playerready.pulsegames.games.common.CheckpointRaceGame$Progress");
  var constructor=pc.getDeclaredConstructor();constructor.setAccessible(true);var progress=constructor.newInstance();
  var safe=pc.getDeclaredField("lastSafe");safe.setAccessible(true);safe.set(progress,new Location(game.world(),100,64,0));
  var next=pc.getDeclaredField("nextCheckpoint");next.setAccessible(true);next.setInt(progress,1);
  var field=uk.co.playerready.pulsegames.games.common.CheckpointRaceGame.class.getDeclaredField("progress");field.setAccessible(true);
  ((Map)field.get(logic)).put(player.getUniqueId(),progress);
  field=PulseKartsGame.class.getDeclaredField("space");field.setAccessible(true);
  field.set(logic,(KartPhysics.CollisionSpace)(x,y,z,w,h)->y>=64 && x<50);
  logic.onDeath(player,null);assertEquals(0,kart.waypoint);assertEquals(0,kart.motion.x());
  assertEquals(0,kart.motion.speed());assertEquals(0,kart.input.throttle());
 }
 void field(Class<?> owner,String name,Object value) throws Exception {
  var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(logic,value);
 }
 Object routeProgress(int nextCheckpoint) throws Exception {
  bot();var settings=new HashMap<String,Object>();
  settings.put("bot-waypoint1","0,64,0");settings.put("bot-waypoint2","0,64,20");
  settings.put("bot-waypoint3","20,64,20");settings.put("bot-waypoint4","20,64,0");
  settings.put("checkpoint-spawn1","0,64,12");settings.put("checkpoint-spawn2","12,64,20");
  var cp1=new uk.co.playerready.pulsegames.core.util.Cuboid(-1,63,10,1,66,11);
  var cp2=new uk.co.playerready.pulsegames.core.util.Cuboid(10,63,19,11,66,21);
  var finish=new uk.co.playerready.pulsegames.core.util.Cuboid(9,63,-1,10,66,1);
  var route=KartBotRoute.load(settings,Map.of("checkpoint1",cp1,"checkpoint2",cp2,"finish",finish),game.world());
  field(PulseKartsGame.class,"botRoute",route);field(PulseKartsGame.class,"botPoints",route.points());
  var base=uk.co.playerready.pulsegames.games.common.CheckpointRaceGame.class;
  field(base,"checkpoints",List.of(cp1,cp2));field(base,"finish",finish);
  var pc=Class.forName(base.getName()+"$Progress");var constructor=pc.getDeclaredConstructor();constructor.setAccessible(true);
  var progress=constructor.newInstance();var next=pc.getDeclaredField("nextCheckpoint");next.setAccessible(true);next.setInt(progress,nextCheckpoint);
  var safe=pc.getDeclaredField("lastSafe");safe.setAccessible(true);safe.set(progress,new Location(game.world(),0,64,0));
  var f=base.getDeclaredField("progress");f.setAccessible(true);((Map)f.get(logic)).put(player.getUniqueId(),progress);
  Location gridSpawn=new Location(game.world(),0,64,0);when(game.spawnFor(player)).thenReturn(gridSpawn);
  when(game.mode().id()).thenReturn("cup");when(game.arena().settings().getInt("laps",3)).thenReturn(3);
  when(game.plugin().content().kartActive(seat)).thenReturn(true);
  return progress;
 }
 @Test void missedActualCheckpointResetsInsteadOfAdvancingDrivingLine() throws Exception {
  routeProgress(0);kart.waypoint=1;kart.motion=new KartPhysics.State(0,64,20,0,0,0,true);
  drive();assertEquals(0,kart.waypoint);assertEquals(0,kart.motion.z());assertEquals(0,kart.motion.speed());
  verify(game,never()).end(anyList(),anyString());
 }
 @Test void stylesCanOnlyBeChosenByActiveHumanDuringGridCountdown() {
  when(game.frozen()).thenReturn(true);
  assertTrue(logic.chooseStyle(player,KartStyle.LIGHT));assertEquals(KartStyle.LIGHT,kart.style);
  when(game.frozen()).thenReturn(false);
  assertFalse(logic.chooseStyle(player,KartStyle.HEAVY));assertEquals(KartStyle.LIGHT,kart.style);
  when(game.frozen()).thenReturn(true);when(game.isActive(player)).thenReturn(false);
  assertFalse(logic.chooseStyle(player,KartStyle.TRICKSTER));
  when(game.isActive(player)).thenReturn(true);kart.bot=new KartBotDriver(player.getUniqueId());
  assertFalse(logic.chooseStyle(player,KartStyle.HEAVY));
 }
 @Test void citizensSkinReplacementKeepsOwnedSeatAndRefreshesVisual() throws Exception {
  routeProgress(0);
  UUID racerId=player.getUniqueId();Player replacement=mock(Player.class);when(replacement.getUniqueId()).thenReturn(racerId);
  when(replacement.getVehicle()).thenReturn(seat);when(game.isActive(replacement)).thenReturn(true);
  Location grid=new Location(game.world(),0,64,0);when(game.spawnFor(replacement)).thenReturn(grid);
  when(game.plugin().bots().player(player.getUniqueId())).thenReturn(replacement);
  var nextVisual=mock(AutoCloseable.class);
  when(game.plugin().content().attachKart(eq(PulseKartsGame.MODEL),eq(seat),eq(replacement),any())).thenReturn(nextVisual);
  drive();assertSame(replacement,kart.driver);verify(visual).close();verify(seat,never()).remove();
  logic.onQuit(replacement);verify(nextVisual).close();verify(seat).remove();
 }
 @Test void actualFinishCrossingAdvancesCupLapAndContinuesRoute() throws Exception {
  Object progress=routeProgress(2);kart.waypoint=0;kart.motion=new KartPhysics.State(11,64,0,90,5,0,true);
  for(int tick=0;tick<100 && kart.waypoint==0;tick++)drive();
  var lap=progress.getClass().getDeclaredField("lap");lap.setAccessible(true);
  var next=progress.getClass().getDeclaredField("nextCheckpoint");next.setAccessible(true);
  assertEquals(2,lap.getInt(progress));assertEquals(0,next.getInt(progress));assertEquals(1,kart.waypoint);
  verify(game,never()).end(anyList(),anyString());
 }
 @Test void jumpInputChargesAndReleaseBoostsActualKart() throws Exception {
  kart.motion=new KartPhysics.State(0,64,0,0,8,0,true);
  var input=mock(Input.class);when(input.isForward()).thenReturn(true);when(input.isRight()).thenReturn(true);when(input.isJump()).thenReturn(true);
  logic.onInput(player,input);for(int i=0;i<30;i++)drive();
  assertEquals(0,kart.boostUntil);
  when(input.isJump()).thenReturn(false);logic.onInput(player,input);drive();
  assertTrue(kart.boostUntil>31);
 }
 @Test void itemBoxesSurviveStableAudienceAndCloseExactlyOnce() throws Exception {
  when(game.plugin().content().worldVisualViewerReady(player)).thenReturn(true);var matchWorld=game.world();when(matchWorld.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(player.isOnline()).thenReturn(true);when(player.getWorld()).thenReturn(matchWorld);when(game.everyone()).thenReturn(List.of(player));
  var box=mock(uk.co.playerready.pulsegames.core.content.PulseContentBridge.WorldVisual.class);when(box.active()).thenReturn(true);
  when(game.plugin().content().createWorldVisual(eq(PulseKartsGame.PICKUP_MODEL),any(Location.class),anyCollection())).thenReturn(box);
  var f=PulseKartsGame.class.getDeclaredField("itemBoxes");f.setAccessible(true);f.set(logic,List.of(new uk.co.playerready.pulsegames.core.util.Cuboid(0,64,0,1,65,1)));
  var m=PulseKartsGame.class.getDeclaredMethod("refreshBoxVisuals");m.setAccessible(true);m.invoke(logic);m.invoke(logic);
  verify(game.plugin().content(),times(1)).createWorldVisual(anyString(),any(Location.class),anyCollection());
  logic.onCleanup();logic.onCleanup();verify(box,times(1)).close();
 }
 @Test void pickupRefreshFailureClosesEarlierCreatedBoxes() throws Exception {
  when(game.plugin().content().worldVisualViewerReady(player)).thenReturn(true);var matchWorld=game.world();when(matchWorld.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(player.isOnline()).thenReturn(true);when(player.getWorld()).thenReturn(matchWorld);when(game.everyone()).thenReturn(List.of(player));
  var box=mock(uk.co.playerready.pulsegames.core.content.PulseContentBridge.WorldVisual.class);
  when(game.plugin().content().createWorldVisual(anyString(),any(Location.class),anyCollection())).thenReturn(box).thenThrow(new IllegalStateException("capacity"));
  var f=PulseKartsGame.class.getDeclaredField("itemBoxes");f.setAccessible(true);f.set(logic,List.of(new uk.co.playerready.pulsegames.core.util.Cuboid(0,64,0,1,65,1),new uk.co.playerready.pulsegames.core.util.Cuboid(4,64,0,5,65,1)));
  var m=PulseKartsGame.class.getDeclaredMethod("refreshBoxVisuals");m.setAccessible(true);assertThrows(java.lang.reflect.InvocationTargetException.class,()->m.invoke(logic));
  verify(box).close();
 }
 @Test void failedPickupCloseIsRetainedAndCannotBlockSeatCleanup() throws Exception {
  var box=mock(uk.co.playerready.pulsegames.core.content.PulseContentBridge.WorldVisual.class);
  doThrow(new IllegalStateException("temporary close failure")).doNothing().when(box).close();
  var f=PulseKartsGame.class.getDeclaredField("boxVisuals");f.setAccessible(true);
  ((List<uk.co.playerready.pulsegames.core.content.PulseContentBridge.WorldVisual>)f.get(logic)).add(box);
  assertThrows(IllegalStateException.class,logic::onCleanup);verify(seat).remove();
  assertEquals(1,((List<?>)f.get(logic)).size());
  logic.onCleanup();assertTrue(((List<?>)f.get(logic)).isEmpty());verify(box,times(2)).close();
 }
 @Test void pickupWaitsForLoadedChunkAndReadySpectator() throws Exception {
  var matchWorld=game.world();when(player.isOnline()).thenReturn(true);when(player.getWorld()).thenReturn(matchWorld);when(game.everyone()).thenReturn(List.of(player));
  var box=mock(uk.co.playerready.pulsegames.core.content.PulseContentBridge.WorldVisual.class);when(box.active()).thenReturn(true);
  when(game.plugin().content().createWorldVisual(anyString(),any(Location.class),anyCollection())).thenReturn(box);
  var f=PulseKartsGame.class.getDeclaredField("itemBoxes");f.setAccessible(true);f.set(logic,List.of(new uk.co.playerready.pulsegames.core.util.Cuboid(0,64,0,1,65,1)));
  var m=PulseKartsGame.class.getDeclaredMethod("refreshBoxVisuals");m.setAccessible(true);
  m.invoke(logic);verify(game.plugin().content(),never()).createWorldVisual(anyString(),any(Location.class),anyCollection());
  when(game.plugin().content().worldVisualViewerReady(player)).thenReturn(true);
  m.invoke(logic);verify(game.plugin().content(),never()).createWorldVisual(anyString(),any(Location.class),anyCollection());
  when(matchWorld.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);m.invoke(logic);
  verify(game.plugin().content(),times(1)).createWorldVisual(anyString(),any(Location.class),anyCollection());
 }
 @Test void itemHitsCannotChainStunAndShareResetBumpProtection() throws Exception {
  var slow=PulseKartsGame.class.getDeclaredMethod("slow",PulseKartsGame.Kart.class,int.class);slow.setAccessible(true);
  var time=PulseKartsGame.class.getDeclaredField("tick");time.setAccessible(true);time.set(logic,100L);
  assertEquals(true,slow.invoke(logic,kart,50));assertEquals(150,kart.slowUntil);assertEquals(190,kart.bumpImmuneUntil);
  time.set(logic,149L);assertEquals(false,slow.invoke(logic,kart,60));assertEquals(150,kart.slowUntil);
  time.set(logic,189L);assertEquals(false,slow.invoke(logic,kart,60));
  time.set(logic,190L);assertEquals(true,slow.invoke(logic,kart,60));assertEquals(250,kart.slowUntil);
  kart.slowUntil=0;kart.bumpImmuneUntil=300;time.set(logic,260L);
  assertEquals(false,slow.invoke(logic,kart,50));assertEquals(0,kart.slowUntil);
 }
 @Test void lightningOnlyTargetsStrictlyAheadUnprotectedRacers() throws Exception {
  var otherPlayer=mock(Player.class);when(otherPlayer.getUniqueId()).thenReturn(UUID.randomUUID());when(game.isActive(otherPlayer)).thenReturn(true);
  when(game.alivePlayers()).thenReturn(List.of(player,otherPlayer));
  var other=new PulseKartsGame.Kart(otherPlayer,mock(ArmorStand.class),()->{},new KartPhysics.State(0,64,10,0,8,0,true));
  var k=PulseKartsGame.class.getDeclaredField("karts");k.setAccessible(true);((Map<UUID,PulseKartsGame.Kart>)k.get(logic)).put(otherPlayer.getUniqueId(),other);
  var behind=PulseKartsGame.class.getDeclaredMethod("clearlyBehind",PulseKartsGame.Kart.class);behind.setAccessible(true);
  var target=PulseKartsGame.class.getDeclaredMethod("lightningTarget",PulseKartsGame.Kart.class);target.setAccessible(true);
  assertEquals(false,behind.invoke(logic,kart));assertNull(target.invoke(logic,kart));
  var base=uk.co.playerready.pulsegames.games.common.CheckpointRaceGame.class;
  var pc=Class.forName(base.getName()+"$Progress");var ctor=pc.getDeclaredConstructor();ctor.setAccessible(true);var progress=ctor.newInstance();
  var lap=pc.getDeclaredField("lap");lap.setAccessible(true);lap.setInt(progress,2);
  var field=base.getDeclaredField("progress");field.setAccessible(true);((Map<UUID,Object>)field.get(logic)).put(otherPlayer.getUniqueId(),progress);
  assertEquals(true,behind.invoke(logic,kart));assertSame(other,target.invoke(logic,kart));
  other.bumpImmuneUntil=100;assertNull(target.invoke(logic,kart));
  kart.item=PulseKartsGame.Item.LIGHTNING;var use=PulseKartsGame.class.getDeclaredMethod("useItem",PulseKartsGame.Kart.class);use.setAccessible(true);use.invoke(logic,kart);
  assertEquals(PulseKartsGame.Item.LIGHTNING,kart.item);
 }
}
