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
}
