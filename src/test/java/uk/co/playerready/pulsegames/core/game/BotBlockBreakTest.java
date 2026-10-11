package uk.co.playerready.pulsegames.core.game;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BotBlockBreakTest {
 @Test void npcDiggingUsesNormalProtectionAndRewardsPath() {
  var plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);
  var bot=mock(Player.class);var block=mock(Block.class);when(plugin.bots().isBot(bot)).thenReturn(true);
  when(plugin.instances().byPlayer(bot)).thenReturn(game);when(game.state()).thenReturn(GameState.RUNNING);
  when(game.isAlive(bot)).thenReturn(true);var listener=new GameListener(plugin);
  var denied=new BlockBreakEvent(block,bot);listener.onBreak(denied);assertTrue(denied.isCancelled());
  when(game.logic().canBreak(bot,block)).thenReturn(true);
  var allowed=new BlockBreakEvent(block,bot);listener.onBreak(allowed);assertFalse(allowed.isCancelled());assertFalse(allowed.isDropItems());
  when(game.frozen()).thenReturn(true);var frozen=new BlockBreakEvent(block,bot);listener.onBreak(frozen);assertTrue(frozen.isCancelled());
 }
}
