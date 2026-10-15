package uk.co.playerready.pulsegames.games.avalanche;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import static org.junit.jupiter.api.Assertions.*;
class AvalancheShrinkTest {
 @Test void shrinksBothAxesTowardsOriginalCentreAndPreservesHeight() {
  var a=new Cuboid(-20,60,10,10,90,30);var b=AvalancheGame.inset(a,7);
  assertEquals(-19,b.minX());assertEquals(9,b.maxX());assertEquals(11,b.minZ());assertEquals(29,b.maxZ());
  assertEquals(a.minY(),b.minY());assertEquals(a.maxY(),b.maxY());
  assertEquals(a.minX()+a.maxX(),b.minX()+b.maxX());assertEquals(a.minZ()+a.maxZ(),b.minZ()+b.maxZ());
 }
 @Test void stopsBeforeEitherDimensionWouldBecomeTooSmall() {
  var a=new Cuboid(0,60,0,7,90,20);assertSame(a,AvalancheGame.inset(a,7));
  var b=new Cuboid(0,60,0,8,90,8);b=AvalancheGame.inset(b,7);
  assertEquals(7,b.maxX()-b.minX()+1);assertSame(b,AvalancheGame.inset(b,7));
 }
 @Test void collapseWarnsThenRemovesOnlyOuterColumns() throws Exception {
  var game=org.mockito.Mockito.mock(uk.co.playerready.pulsegames.core.game.GameInstance.class,org.mockito.Mockito.RETURNS_DEEP_STUBS);
  var settings=new org.bukkit.configuration.file.YamlConfiguration();
  org.mockito.Mockito.when(game.arena().settings()).thenReturn(settings);
  org.mockito.Mockito.when(game.arena().region("arena")).thenReturn(new Cuboid(0,60,0,8,62,8));
  var world=org.mockito.Mockito.mock(org.bukkit.World.class);org.mockito.Mockito.when(game.world()).thenReturn(world);
  var changed=new java.util.ArrayList<org.bukkit.Location>();
  org.mockito.Mockito.when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i->{
   var block=org.mockito.Mockito.mock(org.bukkit.block.Block.class);
   var loc=new org.bukkit.Location(world,i.getArgument(0,Integer.class),i.getArgument(1,Integer.class),i.getArgument(2,Integer.class));
   org.mockito.Mockito.when(block.getLocation()).thenReturn(loc);
   org.mockito.Mockito.doAnswer(call->{changed.add(loc);return null;}).when(block).setType(org.bukkit.Material.AIR,false);return block;
  });
  var logic=new AvalancheGame(game);logic.onStart();
  var method=AvalancheGame.class.getDeclaredMethod("shrink",int.class);method.setAccessible(true);
  method.invoke(logic,25);assertTrue(changed.isEmpty());
  org.mockito.Mockito.verify(game).broadcast(org.mockito.ArgumentMatchers.contains("5 seconds"));
  method.invoke(logic,30);assertEquals(128,changed.size());
  assertTrue(changed.stream().allMatch(l->l.getBlockX()==0||l.getBlockX()==8||l.getBlockZ()==0||l.getBlockZ()==8));
  method.invoke(logic,45);assertEquals(128,changed.size());
 }
}
