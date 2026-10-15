package uk.co.playerready.pulsegames.core.game;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FinishParticipantTest {
 private record Fixture(PulseGamesPlugin plugin,MiniGame logic,GameInstance game,Player player) {}
 private Fixture fixture(boolean managed) throws Exception {
  var plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);var type=mock(GameType.class);var logic=mock(MiniGame.class);
  when(type.createLogic(any())).thenReturn(logic);when(logic.managesCompletion()).thenReturn(managed);
  var game=new GameInstance(plugin,type,mock(GameMode.class),mock(Arena.class));
  var f=GameInstance.class.getDeclaredField("state");f.setAccessible(true);f.set(game,GameState.RUNNING);
  var player=mock(Player.class);var id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);when(player.isOnline()).thenReturn(true);when(plugin.getServer().getPlayer(id)).thenReturn(player);
  for(String name:List.of("participants","alive")){f=GameInstance.class.getDeclaredField(name);f.setAccessible(true);((Set<UUID>)f.get(game)).add(id);}
  return new Fixture(plugin,logic,game,player);
 }
 @Test void finisherRetainsCreditButCannotRemainActiveOrTriggerAutoCompletion() throws Exception {
  var f=fixture(true);f.game.finishParticipant(f.player);f.game.finishParticipant(f.player);f.game.checkEnd();
  assertTrue(f.game.isParticipant(f.player));assertTrue(f.game.isSpectator(f.player));assertFalse(f.game.isActive(f.player));assertEquals(GameState.RUNNING,f.game.state());
  assertEquals(List.of(f.player),GameInstance.winningMembers(f.game.players(),List.of(f.player),p->null));
  verify(f.plugin.playerState()).reset(f.player);verify(f.player).setGameMode(org.bukkit.GameMode.SPECTATOR);
  verify(f.logic,never()).onEliminated(any());verify(f.logic,never()).onEnd(anyList());verify(f.plugin.economy(),never()).addMatchTokens(any(),anyInt(),anyString());
 }
 @Test void normalGamesIgnoreFinishTransitionAndStillEndAtLastStanding() throws Exception {
  var f=fixture(false);f.game.finishParticipant(f.player);assertTrue(f.game.isAlive(f.player));assertFalse(f.game.isSpectator(f.player));
  // Remove online lookup only for the existing end pipeline to avoid particle dependencies.
  when(f.plugin.getServer().getPlayer(f.player.getUniqueId())).thenReturn(null);
  try(var bukkit=mockStatic(Bukkit.class)){bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));f.game.checkEnd();}
  assertEquals(GameState.ENDING,f.game.state());verify(f.logic).onEnd(List.of());
 }
}
