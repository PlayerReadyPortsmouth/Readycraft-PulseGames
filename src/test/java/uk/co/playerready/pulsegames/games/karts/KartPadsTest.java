package uk.co.playerready.pulsegames.games.karts;
import java.util.*;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import static org.junit.jupiter.api.Assertions.*;
class KartPadsTest {
 final UUID a=new UUID(0,1),b=new UUID(0,2);
 KartPhysics.State at(double x,double y,double z){return new KartPhysics.State(x,y,z,0,12,0,true);}
 @Test void cooldownIsPerKartAndPerPadAndWigglingCannotFarmIt() {
  var pads=new KartPads();
  assertTrue(pads.trigger(a,"boost1",100,20));assertFalse(pads.trigger(a,"boost1",119,20));
  assertTrue(pads.trigger(b,"boost1",101,20));assertTrue(pads.trigger(a,"boost2",101,20));
  assertTrue(pads.trigger(a,"boost1",120,20));
  pads.forget(a);assertTrue(pads.trigger(a,"boost2",102,20));
 }
 @Test void sweptEntryCatchesAThinPadAtFullSpeedButNotAKartAlreadyOnIt() {
  var pad=new Cuboid(0,64,10,3,66,10);
  assertTrue(KartPads.enters(pad,at(1,64,9.8),at(1,64,10.4)));
  assertTrue(KartPads.enters(pad,at(1,64,9.6),at(1,64,11.2)),"stepped clean over a one-block pad");
  assertFalse(KartPads.enters(pad,at(1,64,10.2),at(1,64,10.8)),"already inside");
  assertFalse(KartPads.enters(pad,at(5,64,9.6),at(5,64,11.2)),"beside the pad");
  assertFalse(KartPads.enters(pad,at(1,70,9.6),at(1,70,11.2)),"above the pad");
  assertFalse(KartPads.enters(pad,at(1,64,Double.NaN),at(1,64,11.2)));
 }
 @Test void configReadsPanelsAndBlocksAndIgnoresOtherBoostNames() {
  var regions=Map.of("boost1",new Cuboid(0,0,0,1,1,1),"boost2",new Cuboid(5,0,5,6,1,6),"boost-foo",new Cuboid(9,0,9,9,0,9),"checkpoint1",new Cuboid(0,0,0,0,0,0));
  var config=KartArcadeConfig.load(Map.of("boost-block","BLUE_GLAZED_TERRACOTTA, light_blue_glazed_terracotta"),regions);
  assertEquals(2,config.boostPanels().size());
  assertEquals(Set.of(Material.BLUE_GLAZED_TERRACOTTA,Material.LIGHT_BLUE_GLAZED_TERRACOTTA),config.boostBlocks());
  var none=KartArcadeConfig.load(Map.of(),Map.of("checkpoint1",new Cuboid(0,0,0,0,0,0)));
  assertTrue(none.boostPanels().isEmpty());assertTrue(none.boostBlocks().isEmpty());
 }
 @Test void malformedOptionalEntriesAreRefusedWithAMessage() {
  assertNotNull(KartArcadeConfig.validationError(Map.of(),Map.of("boost1",new Cuboid(0,0,0,0,0,0),"boost3",new Cuboid(0,0,0,0,0,0))));
  assertNotNull(KartArcadeConfig.validationError(Map.of("boost-block","NOT_A_BLOCK"),Map.of()));
  assertNull(KartArcadeConfig.validationError(Map.of("boost-block",42),Map.of()),"non-text values are ignored");
  assertNull(KartArcadeConfig.validationError(Map.of(),Map.of()));
 }
 @Test void railPointsJoinTheSectionThatContainsThemAndBadRailsAreRefused() {
  var sections=Map.of("antigrav1",new Cuboid(0,0,0,10,10,10),"antigrav2",new Cuboid(20,0,0,30,10,10));
  var settings=Map.<String,Object>of("antigrav-path1","1,2,3","antigrav-path2","5.5,2,3.5,90,0","antigrav-path3","25,2,5");
  var config=KartArcadeConfig.load(settings,sections);
  assertEquals(2,config.antiGravity().size());
  assertEquals(2,config.antiGravityRails().get(0).size());assertEquals(1,config.antiGravityRails().get(1).size());
  assertTrue(config.inAntiGravity(5,3,5));assertFalse(config.inAntiGravity(15,3,5));
  assertNotNull(KartArcadeConfig.validationError(Map.of("antigrav-path1","99,2,99"),sections),"outside every section");
  assertNotNull(KartArcadeConfig.validationError(Map.of("antigrav-path2","1,2,3"),sections),"must start at 1");
  assertNotNull(KartArcadeConfig.validationError(Map.of("antigrav-path1","one,two"),sections));
  assertNotNull(KartArcadeConfig.validationError(Map.of("antigrav-pathX","1,2,3"),sections));
  assertNotNull(KartArcadeConfig.validationError(Map.of(),Map.of("antigrav2",new Cuboid(0,0,0,1,1,1))));
 }
 @Test void contactCooldownNeedsBothKartsReadyAndArmsBoth() {
  var pads=new KartPads();
  assertTrue(pads.triggerBoth(a,b,"bump",100,40));assertFalse(pads.triggerBoth(a,b,"bump",139,40));
  var c=new UUID(0,3);assertFalse(pads.triggerBoth(c,a,"bump",120,40),"a is still cooling down");
  assertTrue(pads.triggerBoth(a,b,"bump",140,40));
 }
}
