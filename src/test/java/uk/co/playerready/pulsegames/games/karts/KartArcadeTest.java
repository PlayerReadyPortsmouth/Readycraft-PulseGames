package uk.co.playerready.pulsegames.games.karts;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.*;
import io.papermc.paper.entity.TeleportFlag;
import uk.co.playerready.pulsegames.core.game.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KartArcadeTest {
    GameInstance game;PulseKartsGame logic;Map<UUID,PulseKartsGame.Kart> karts;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        game=mock(GameInstance.class,RETURNS_DEEP_STUBS);when(game.state()).thenReturn(GameState.RUNNING);
        when(game.world()).thenReturn(mock(World.class));logic=new PulseKartsGame(game);
        var field=PulseKartsGame.class.getDeclaredField("karts");field.setAccessible(true);karts=(Map<UUID,PulseKartsGame.Kart>)field.get(logic);
        field=PulseKartsGame.class.getDeclaredField("space");field.setAccessible(true);field.set(logic,(KartPhysics.CollisionSpace)(x,y,z,w,h)->y>=64);
    }
    KartPhysics.State state(double x,double z,double speed){return new KartPhysics.State(x,64,z,0,speed,0,true);}
    PulseKartsGame.Kart kart(double x,double z,double speed) {
        Player player=mock(Player.class);ArmorStand seat=mock(ArmorStand.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.getVehicle()).thenReturn(seat);when(game.isActive(player)).thenReturn(true);when(seat.isValid()).thenReturn(true);
        when(seat.teleport(any(Location.class),eq(TeleportFlag.EntityState.RETAIN_PASSENGERS))).thenReturn(true);
        var kart=new PulseKartsGame.Kart(player,seat,mock(AutoCloseable.class),state(x,z,speed));
        karts.put(player.getUniqueId(),kart);return kart;
    }
    void tick(long value)throws Exception {var f=PulseKartsGame.class.getDeclaredField("tick");f.setAccessible(true);f.setLong(logic,value);}
    void bump(Map<UUID,KartPhysics.State> before)throws Exception {
        var m=PulseKartsGame.class.getDeclaredMethod("applyBumps",Map.class);m.setAccessible(true);m.invoke(logic,before);
    }
    @Test void overlappingSidesRemainFreeToDrive()throws Exception {
        var a=kart(0,0,8);var b=kart(1,0,8);a.input=b.input=new KartPhysics.Input(1,0);
        var m=PulseKartsGame.class.getDeclaredMethod("drive");m.setAccessible(true);m.invoke(logic);
        assertTrue(a.motion.z()>.3);assertTrue(b.motion.z()>.3);
        assertEquals(0,a.slowUntil);assertEquals(0,b.slowUntil);
    }
    @Test void rearVictimSpinsSlowsAndCannotBeRepeatedlyHitDuringImmunity()throws Exception {
        var attacker=kart(0,0,12);var victim=kart(0,.2,4);tick(100);
        var before=Map.of(attacker.driver.getUniqueId(),state(0,-3,12),victim.driver.getUniqueId(),state(0,0,4));
        bump(before);assertEquals(120,victim.slowUntil);assertEquals(111,victim.spinUntil);assertEquals(160,victim.bumpImmuneUntil);
        assertEquals(1.8,victim.motion.speed(),1e-9);assertEquals(0,attacker.bumpImmuneUntil);tick(159);bump(before);
        assertEquals(120,victim.slowUntil);assertEquals(111,victim.spinUntil);assertEquals(160,victim.bumpImmuneUntil);
        tick(160);bump(before);assertEquals(180,victim.slowUntil);assertEquals(220,victim.bumpImmuneUntil);
    }
    @Test void unobstructedSpinCompletesFullTurnWithoutLeavingHeadingOffset() throws Exception {
        var attacker=kart(0,0,12);var victim=kart(0,.2,4);tick(100);
        bump(Map.of(attacker.driver.getUniqueId(),state(0,-3,12),victim.driver.getUniqueId(),state(0,0,4)));
        karts.remove(attacker.driver.getUniqueId());
        var drive=PulseKartsGame.class.getDeclaredMethod("drive");drive.setAccessible(true);
        for(int n=0;n<10;n++)drive.invoke(logic);
        assertEquals(0,victim.motion.yaw(),.001);
    }
    @Test void heavyHitsHarderAndHeavyVictimsRecoverBeforeLightVictims()throws Exception {
        var attacker=kart(0,0,12);var victim=kart(0,.2,4);attacker.style=KartStyle.HEAVY;
        var before=Map.of(attacker.driver.getUniqueId(),state(0,-3,12),victim.driver.getUniqueId(),state(0,0,4));
        victim.style=KartStyle.HEAVY;tick(100);bump(before);assertEquals(119,victim.slowUntil);
        victim.style=KartStyle.LIGHT;victim.bumpImmuneUntil=0;tick(200);bump(before);assertEquals(234,victim.slowUntil);
    }
    @Test void tricksterHasMoreTurboAndLightningAndFewerShells() {
        var normal=new EnumMap<PulseKartsGame.Item,Integer>(PulseKartsGame.Item.class);
        var trick=new EnumMap<PulseKartsGame.Item,Integer>(PulseKartsGame.Item.class);
        for(int roll=0;roll<100;roll++) {
            normal.merge(PulseKartsGame.pickItem(KartStyle.BALANCED,roll),1,Integer::sum);
            trick.merge(PulseKartsGame.pickItem(KartStyle.TRICKSTER,roll),1,Integer::sum);
        }
        assertTrue(trick.get(PulseKartsGame.Item.TURBO)>normal.get(PulseKartsGame.Item.TURBO));
        assertTrue(trick.get(PulseKartsGame.Item.LIGHTNING)>normal.get(PulseKartsGame.Item.LIGHTNING));
        assertTrue(trick.get(PulseKartsGame.Item.SHELL)<normal.get(PulseKartsGame.Item.SHELL));
    }
    @Test void cupUsesMapConfiguredFiveLapsWhileSprintStaysOne() {
        when(game.mode().id()).thenReturn("cup");when(game.arena().settings().getInt("laps",3)).thenReturn(5);
        assertEquals(5,logic.laps());when(game.mode().id()).thenReturn("sprint");assertEquals(1,logic.laps());
    }
    @Test void fiveLapCupRequiresFinalLapCheckpointsBeforeWinning() throws Exception {
        var racer=kart(0,0,8);var world=game.world();var grid=new Location(world,0,64,0);
        when(game.mode().id()).thenReturn("cup");when(game.arena().settings().getInt("laps",3)).thenReturn(5);
        when(game.spawnFor(racer.driver)).thenReturn(grid);
        when(racer.driver.getLocation()).thenReturn(grid);when(racer.driver.isInsideVehicle()).thenReturn(true);
        Class<?> base=uk.co.playerready.pulsegames.games.common.CheckpointRaceGame.class;
        Class<?> pc=Class.forName(base.getName()+"$Progress");var ctor=pc.getDeclaredConstructor();ctor.setAccessible(true);var progress=ctor.newInstance();
        var lap=pc.getDeclaredField("lap");lap.setAccessible(true);lap.setInt(progress,4);
        var next=pc.getDeclaredField("nextCheckpoint");next.setAccessible(true);
        var checkpoints=base.getDeclaredField("checkpoints");checkpoints.setAccessible(true);
        checkpoints.set(logic,List.of(new uk.co.playerready.pulsegames.core.util.Cuboid(-2,63,2,2,66,2)));
        var finish=base.getDeclaredField("finish");finish.setAccessible(true);
        finish.set(logic,new uk.co.playerready.pulsegames.core.util.Cuboid(-2,63,5,2,66,5));
        var move=base.getDeclaredMethod("moveSwept",Player.class,pc,Location.class,Location.class);move.setAccessible(true);
        move.invoke(logic,racer.driver,progress,new Location(world,0,64,0),new Location(world,0,64,7));
        assertEquals(5,lap.getInt(progress));assertEquals(0,next.getInt(progress));verify(game,never()).end(anyList(),eq("finish"));
        // Returning to finish without the final lap's checkpoint must not win.
        move.invoke(logic,racer.driver,progress,new Location(world,0,64,4),new Location(world,0,64,7));
        verify(game,never()).end(anyList(),eq("finish"));
        move.invoke(logic,racer.driver,progress,new Location(world,0,64,0),new Location(world,0,64,7));
        when(game.players()).thenReturn(List.of(racer.driver));
        var complete=PulseKartsGame.class.getDeclaredMethod("completeRaceTick");complete.setAccessible(true);complete.invoke(logic);
        verify(game).end(List.of(racer.driver),"race-complete");
    }
 void arcade(String key,uk.co.playerready.pulsegames.core.util.Cuboid box)throws Exception {
  var f=PulseKartsGame.class.getDeclaredField("arcade");f.setAccessible(true);
  f.set(logic,KartArcadeConfig.load(Map.of(),Map.of(key,box)));
 }
 void drive()throws Exception {var m=PulseKartsGame.class.getDeclaredMethod("drive");m.setAccessible(true);m.invoke(logic);}
 @Test void drivingOntoABoostPanelBoostsOnceAndStayingOnItDoesNotRetrigger()throws Exception {
  arcade("boost1",new uk.co.playerready.pulsegames.core.util.Cuboid(-1,64,3,1,66,5));
  var kart=kart(0,2.8,12);kart.input=new KartPhysics.Input(1,0);
  drive();assertTrue(kart.boost.active(1),"entered the panel");assertEquals(31,kart.boost.until());
  drive();assertEquals(31,kart.boost.until(),"still inside, same boost");
 }
 @Test void aKartThatNeverTouchesAPanelIsNotBoosted()throws Exception {
  arcade("boost1",new uk.co.playerready.pulsegames.core.util.Cuboid(10,64,3,12,66,5));
  var kart=kart(0,2.8,12);kart.input=new KartPhysics.Input(1,0);drive();assertFalse(kart.boost.active(1));
 }
 @Test void drivingIntoAJumpPadLaunchesOnceAndATrickLandsWithABoost()throws Exception {
  var f=PulseKartsGame.class.getDeclaredField("arcade");f.setAccessible(true);
  f.set(logic,KartArcadeConfig.load(Map.of(),Map.of("jump1",new uk.co.playerready.pulsegames.core.util.Cuboid(-1,64,3,1,66,5))));
  var kart=kart(0,2.8,12);kart.input=new KartPhysics.Input(1,0);
  drive();assertTrue(kart.air.flying());assertEquals(10,kart.motion.verticalSpeed(),1e-9);assertFalse(kart.motion.grounded());
  for(int i=0;i<6;i++)drive();
  kart.trickRequested=true;
  int guard=0;while(kart.air.flying() && guard++<40)drive();
  assertFalse(kart.air.flying(),"landed within the air-time cap");assertTrue(kart.motion.grounded());
  assertTrue(kart.boost.active(KartBoost.Source.TRICK,logicTick()),"trick landing boost");
  assertEquals(64,kart.motion.y(),.01);
 }
 long logicTick()throws Exception {var f=PulseKartsGame.class.getDeclaredField("tick");f.setAccessible(true);return f.getLong(logic);}
 @Test void noTrickWithoutPressingJumpGivesNoLandingBoost()throws Exception {
  var f=PulseKartsGame.class.getDeclaredField("arcade");f.setAccessible(true);
  f.set(logic,KartArcadeConfig.load(Map.of(),Map.of("jump1",new uk.co.playerready.pulsegames.core.util.Cuboid(-1,64,3,1,66,5))));
  var kart=kart(0,2.8,12);kart.input=new KartPhysics.Input(1,0);
  drive();int guard=0;while(kart.air.flying() && guard++<40)drive();
  assertFalse(kart.boost.active(KartBoost.Source.TRICK,logicTick()));
 }
 @Test void aSlowKartIsNotLaunched()throws Exception {
  var f=PulseKartsGame.class.getDeclaredField("arcade");f.setAccessible(true);
  f.set(logic,KartArcadeConfig.load(Map.of(),Map.of("jump1",new uk.co.playerready.pulsegames.core.util.Cuboid(-1,64,3,1,66,5))));
  var kart=kart(0,2.9,0);kart.input=new KartPhysics.Input(0,0);kart.motion=new KartPhysics.State(0,64,2.95,0,2,0,true);
  drive();assertFalse(kart.air.flying());
 }
 @Test void jumpConfigurationIsValidated() {
  var regions=Map.of("jump1",new uk.co.playerready.pulsegames.core.util.Cuboid(0,0,0,1,1,1));
  assertEquals(1.25,KartArcadeConfig.load(Map.of("jump-power","1.25"),regions).jumpPower());
  assertEquals(1,KartArcadeConfig.load(Map.of(),regions).jumpPower());
  assertNotNull(KartArcadeConfig.validationError(Map.of("jump-power","3"),regions));
  assertNotNull(KartArcadeConfig.validationError(Map.of("jump-power","fast"),regions));
  assertNotNull(KartArcadeConfig.validationError(Map.of(),Map.of("jump2",new uk.co.playerready.pulsegames.core.util.Cuboid(0,0,0,1,1,1))));
 }
 @Test void antiGravityGivesTenPercentAndOnlyTouchingKartsInsideItBumpBoost()throws Exception {
  var f=PulseKartsGame.class.getDeclaredField("arcade");f.setAccessible(true);
  f.set(logic,KartArcadeConfig.load(Map.of(),Map.of("antigrav1",new uk.co.playerready.pulsegames.core.util.Cuboid(-5,64,-5,5,66,30))));
  var a=kart(0,2,6);var b=kart(1,2,6);var solo=kart(0,20,6);var far=kart(0,200,6);
  a.input=b.input=solo.input=far.input=new KartPhysics.Input(1,0);
  drive();
  assertEquals(KartAntiGravity.SPEED_FACTOR,solo.boost.multiplier(1),1e-9,"+10% inside, alone");
  assertTrue(a.boost.active(KartBoost.Source.BUMP,1));assertTrue(b.boost.active(KartBoost.Source.BUMP,1));
  assertEquals(KartAntiGravity.BUMP_FACTOR,a.boost.multiplier(1),1e-9,"the bump boost is the larger factor, not stacked");
  assertFalse(solo.boost.active(KartBoost.Source.BUMP,1),"not touching anyone");
  assertEquals(1,far.boost.multiplier(1),"outside the section: nothing");
 }
}
