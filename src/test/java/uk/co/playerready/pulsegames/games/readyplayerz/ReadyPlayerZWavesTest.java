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
  settings=new YamlConfiguration();when(game.arena().settings()).thenReturn(settings);when(game.mode().id()).thenReturn("classic");
  logic=spy(new ReadyPlayerZGame(game));doNothing().when(logic).darken(any());doNothing().when(logic).clearDarkness(any());
  var f=ReadyPlayerZGame.class.getDeclaredField("mobSpawns");f.setAccessible(true);f.set(logic,List.of());
  spawns=new ArrayList<>();doAnswer(i->{spawns.add(i.getArgument(1));return null;}).when(game).runLater(anyLong(),any(Runnable.class));
 }
 void start() throws Exception {var m=ReadyPlayerZGame.class.getDeclaredMethod("startRound");m.setAccessible(true);m.invoke(logic);}
 @Test void roundSoundDoesNotPreventTenZombiesBeingScheduledAndSpawned() throws Exception {
  start();org.junit.jupiter.api.Assertions.assertEquals(10,spawns.size());
  var zombie=mock(Zombie.class);when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
  when(game.world().spawnEntity(any(),eq(EntityType.ZOMBIE))).thenReturn(zombie);
  spawns.forEach(Runnable::run);verify(game.world(),times(10)).spawnEntity(any(),eq(EntityType.ZOMBIE));
  verify(human).playSound(nullable(Location.class),eq("minecraft:entity.zombie.ambient"),eq(1f),eq(.7f));
 }
 @Test void darknessOnlyAffectsHumansDuringWavesAndCanBeDisabled() throws Exception {
  logic.refreshDarkness();verify(logic,never()).darken(any());start();verify(logic).darken(human);verify(logic,never()).darken(bot);
  settings.set("darkness",false);logic.refreshDarkness();verify(logic,times(1)).darken(human);
 }
 @Test void completedWaveAndDepartureClearDarkness() throws Exception {
  start();var f=ReadyPlayerZGame.class.getDeclaredField("pendingSpawns");f.setAccessible(true);f.set(logic,0);
  logic.onSecond(1);verify(logic).clearDarkness(human);logic.onQuit(human);verify(logic,times(2)).clearDarkness(human);
  logic.onCleanup();verify(logic,times(3)).clearDarkness(human);logic.refreshDarkness();verify(logic,times(2)).darken(human);
 }
}
