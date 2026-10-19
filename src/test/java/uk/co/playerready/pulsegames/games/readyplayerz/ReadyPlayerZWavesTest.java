package uk.co.playerready.pulsegames.games.readyplayerz;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;
import static org.mockito.Mockito.*;
class ReadyPlayerZWavesTest {
 GameInstance game; ReadyPlayerZGame logic; Player human,bot; YamlConfiguration settings; List<Runnable> spawns;
 @BeforeEach void setup() throws Exception {
  game=mock(GameInstance.class,RETURNS_DEEP_STUBS);human=mock(Player.class);bot=mock(Player.class);
  when(human.getUniqueId()).thenReturn(UUID.randomUUID());when(bot.getUniqueId()).thenReturn(UUID.randomUUID());
  when(game.everyone()).thenReturn(List.of(human,bot));when(game.alivePlayers()).thenReturn(List.of(human,bot));when(game.isBot(bot)).thenReturn(true);
  settings=new YamlConfiguration();
  var region=new uk.co.playerready.pulsegames.core.util.Cuboid(10,64,0,10,64,0);
  var arena=new uk.co.playerready.pulsegames.core.arena.Arena("z","readyplayerz","Z","world",List.of(),1,4,"0,64,0","0,64,0",List.of("0,64,0"),Map.of("mobspawn1",region),settings);
  when(game.arena()).thenReturn(arena);when(game.mode().id()).thenReturn("classic");
  when(game.world().getName()).thenReturn("pulse_test");
  logic=spy(new ReadyPlayerZGame(game));doNothing().when(logic).darken(any(),anyInt());doNothing().when(logic).clearDarkness(any());
  var f=ReadyPlayerZGame.class.getDeclaredField("mobSpawns");f.setAccessible(true);f.set(logic,game.arena().regionsByPrefix("mobspawn"));
  spawns=new ArrayList<>();doAnswer(i->{spawns.add(i.getArgument(1));return null;}).when(game).runLater(anyLong(),any(Runnable.class));
 }
 void start() throws Exception {var m=ReadyPlayerZGame.class.getDeclaredMethod("startRound");m.setAccessible(true);m.invoke(logic);}
 @Test void roundSoundSchedulesAndSpawnsTenZombiesOnSafeAuthoredFloor() throws Exception {
  when(game.state()).thenReturn(GameState.RUNNING);World world=game.world();
  when(world.getMaxHeight()).thenReturn(320);when(world.getWorldBorder()).thenReturn(null);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
  var air=mock(org.bukkit.block.Block.class);when(air.isPassable()).thenReturn(true);
  var floor=mock(org.bukkit.block.Block.class);when(floor.getBoundingBox()).thenReturn(new org.bukkit.util.BoundingBox(10,63,0,11,64,1));
  when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(air);when(world.getBlockAt(10,63,0)).thenReturn(floor);
  for(Player p:List.of(human,bot)){when(game.isActive(p)).thenReturn(true);when(p.getWorld()).thenReturn(world);when(p.getLocation()).thenReturn(new Location(world,.5,64,.5));}
  doNothing().when(logic).configureEnemy(any(),any());
  List<Zombie> spawned=new ArrayList<>();
  when(world.spawnEntity(any(),eq(EntityType.ZOMBIE))).thenAnswer(invocation->{Zombie zombie=mock(Zombie.class);when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());spawned.add(zombie);return zombie;});
  start();org.junit.jupiter.api.Assertions.assertEquals(10,spawns.size());
  List.copyOf(spawns).forEach(Runnable::run);
  verify(world,times(10)).spawnEntity(any(),eq(EntityType.ZOMBIE));verify(game,never()).forceEnd();
  for(Zombie zombie:spawned)verify(zombie).setTarget(human);
  var tracked=ReadyPlayerZGame.class.getDeclaredField("zombies");tracked.setAccessible(true);org.junit.jupiter.api.Assertions.assertEquals(10,((Set<?>)tracked.get(logic)).size());
  var pending=ReadyPlayerZGame.class.getDeclaredField("pendingSpawns");pending.setAccessible(true);org.junit.jupiter.api.Assertions.assertEquals(0,pending.get(logic));
  verify(human).playSound(any(Location.class),eq("minecraft:entity.zombie.ambient"),eq(1f),eq(.7f));
 }
 @Test void unloadedSpawnNeverFallsBackOntoPlayers()throws Exception{
  when(game.state()).thenReturn(GameState.RUNNING);World world=game.world();when(world.getMaxHeight()).thenReturn(320);when(world.getWorldBorder()).thenReturn(null);
  for(Player p:List.of(human,bot)){when(game.isActive(p)).thenReturn(true);when(p.getWorld()).thenReturn(world);when(p.getLocation()).thenReturn(new Location(world,.5,64,.5));}
  start();List.copyOf(spawns).forEach(Runnable::run);verify(world,never()).spawnEntity(any(),any());
  org.junit.jupiter.api.Assertions.assertEquals(20,spawns.size());
 }
 void pulses(int times) throws Exception {
  for(int i=0;i<times;i++)logic.onSecond(i);
 }
 @Test void darknessPulsesAreShortRandomisedHumanOnlyAndCanBeDisabled() throws Exception {
  logic.pulseDarkness();verify(logic,never()).darken(any(),anyInt());
  logic.pulseRoll=()->0;start();
  pulses(29);verify(logic,never()).darken(any(),anyInt());          // first pulse only after one full interval (30 s)
  pulses(2);verify(logic).darken(human,50);verify(logic,never()).darken(eq(bot),anyInt()); // 2.5 s default
  pulses(10);verify(logic,times(1)).darken(eq(human),anyInt());     // not constantly refreshed
  pulses(25);verify(logic,times(2)).darken(eq(human),anyInt());
  settings.set("darkness",false);pulses(60);verify(logic,times(2)).darken(eq(human),anyInt());
 }
 @Test void pulseIntervalAndDurationSettingsApplyAndRollStretchesGap() throws Exception {
  settings.set("darkness-interval",10);settings.set("darkness-duration",4);logic.pulseRoll=()->1.0;start();
  pulses(14);verify(logic,never()).darken(any(),anyInt());pulses(2);verify(logic).darken(human,80); // 10 s * 1.5 = 15 s
 }
 @Test void noPulseWhileAtADoorPrompt() throws Exception {
  var prog=mock(ZMapProgression.class);logic.progression=prog;when(prog.nearDoorPrompt(human)).thenReturn(true);
  logic.pulseRoll=()->0;start();pulses(80);verify(logic,never()).darken(any(),anyInt());verify(logic,atLeastOnce()).clearDarkness(human);
 }
 @Test void completedWaveAndDepartureClearDarkness() throws Exception {
  start();var f=ReadyPlayerZGame.class.getDeclaredField("pendingSpawns");f.setAccessible(true);f.set(logic,0);
  logic.onSecond(1);verify(logic).clearDarkness(human);logic.onQuit(human);verify(logic,times(2)).clearDarkness(human);
  logic.onCleanup();verify(logic,times(3)).clearDarkness(human);
 }
}
