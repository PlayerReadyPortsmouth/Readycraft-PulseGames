package uk.co.playerready.pulsegames.core.bot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SurfaceMemoryTest {
 @Test void finishesAStairEdgeStepButDropsDestinationWhenSupportDisappears() {
  var bot=mock(Player.class);var world=mock(org.bukkit.World.class);when(bot.getUniqueId()).thenReturn(UUID.randomUUID());
  when(bot.getLocation()).thenReturn(new Location(world,.5,80,.5));
  var goal=new Location(world,1.5,81,.5);var memory=new SurfaceMemory();
  try(var terrain=mockStatic(GroundNavigation.class)) {
   terrain.when(()->GroundNavigation.nextStep(any(),any(),any())).thenReturn(goal);
   assertEquals(goal,memory.nextStep(bot,p->true,p->p.getY()));
   when(bot.getLocation()).thenReturn(new Location(world,.9,81,.5));
   terrain.when(()->GroundNavigation.nextStep(any(),any(),any())).thenReturn(null);
   terrain.when(()->GroundNavigation.walkable(goal)).thenReturn(true);
   assertEquals(goal,memory.nextStep(bot,p->true,p->p.getY()));
   terrain.when(()->GroundNavigation.walkable(goal)).thenReturn(false);
   assertNull(memory.nextStep(bot,p->true,p->p.getY()));
  }
 }
 @Test void repeatedTicksDoNotGrowTrailAndOldVisitsExpire() {
  var bot=mock(Player.class);when(bot.getUniqueId()).thenReturn(UUID.randomUUID());
  var memory=new SurfaceMemory();when(bot.getLocation()).thenReturn(new Location(null,.5,80,.5));
  for(int n=0;n<100;n++)memory.remember(bot);
  assertEquals(3,memory.penalty(bot,new Location(null,0,80,0)));
  for(int n=1;n<=24;n++){when(bot.getLocation()).thenReturn(new Location(null,n,80,0));memory.remember(bot);}
  assertEquals(0,memory.penalty(bot,new Location(null,0,80,0)));
  memory.clear();assertEquals(0,memory.penalty(bot,new Location(null,24,80,0)));
 }
}
