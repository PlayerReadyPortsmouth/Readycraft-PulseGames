package uk.co.playerready.pulsegames.games.tntrun;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.GroundNavigation;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TntRunBotTest {
 @Test void runsContinuousCorridorButReplansWhenAnIntermediateTileDisappears() {
  var game=mock(GameInstance.class);var bot=mock(Player.class);var world=mock(World.class);
  when(bot.getUniqueId()).thenReturn(UUID.randomUUID());when(bot.getLocation()).thenReturn(new Location(world,.5,80,.5));
  var logic=new TntRunGame(game);
  try(var ground=mockStatic(GroundNavigation.class)) {
   ground.when(()->GroundNavigation.walkable(any())).thenReturn(true);
   assertEquals(new Location(world,3.5,80,.5),logic.botDestination(bot));
   ground.when(()->GroundNavigation.walkable(any())).thenAnswer(call->{Location p=call.getArgument(0);return p.getBlockX()!=1;});
   var redirected=logic.botDestination(bot);assertNotNull(redirected);assertTrue(redirected.getX()<1,"Cannot route across missing first tile");
  }
 }
 @Test void pendingCrumbleBlocksCannotBeUsedAsCorridors() throws Exception {
  var game=mock(GameInstance.class);var bot=mock(Player.class);var world=mock(World.class);
  when(bot.getUniqueId()).thenReturn(UUID.randomUUID());when(bot.getLocation()).thenReturn(new Location(world,.5,80,.5));
  var logic=new TntRunGame(game);var field=TntRunGame.class.getDeclaredField("crumbling");field.setAccessible(true);
  @SuppressWarnings("unchecked") var crumbling=(Set<Location>)field.get(logic);crumbling.add(new Location(world,1,79,0));
  try(var ground=mockStatic(GroundNavigation.class)) {
   ground.when(()->GroundNavigation.walkable(any())).thenReturn(true);
   assertTrue(logic.botDestination(bot).getX()<1);
  }
 }
}
