package uk.co.playerready.pulsegames.games.quake;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class QuakeGameTest {
    GameInstance game; QuakeGame logic; World world; Player shooter,victim;AtomicLong clock;
    @BeforeEach void setup(){
        game=mock(GameInstance.class,RETURNS_DEEP_STUBS);world=mock(World.class);clock=new AtomicLong(1_000_000_000L);
        when(game.world()).thenReturn(world);when(game.state()).thenReturn(GameState.RUNNING);
        when(game.arena().settings()).thenReturn(new YamlConfiguration());
        shooter=player("Shooter");victim=player("Victim");when(game.players()).thenReturn(List.of(shooter,victim));
        when(game.teamOf(any())).thenReturn(null);logic=new QuakeGame(game,clock::get);
    }
    Player player(String name){
        Player p=mock(Player.class,RETURNS_DEEP_STUBS);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getName()).thenReturn(name);
        when(p.getWorld()).thenReturn(world);when(p.getLocation()).thenAnswer(i->new Location(world,0,64,0));
        when(p.getEyeLocation()).thenAnswer(i->new Location(world,0,65,0));when(game.isActive(p)).thenReturn(true);return p;
    }
    @Test void fireIsRateLimitedAndResettingOrFrozenCannotShoot(){
        assertTrue(logic.fire(shooter));assertFalse(logic.fire(shooter));clock.addAndGet(1_200_000_000L);assertTrue(logic.fire(shooter));
        when(game.frozen()).thenReturn(true);clock.addAndGet(2_000_000_000L);assertFalse(logic.fire(shooter));
        when(game.frozen()).thenReturn(false);when(game.state()).thenReturn(GameState.RESETTING);assertFalse(logic.fire(shooter));
    }
    @Test void wallLimitsRayDistance(){
        when(world.rayTraceBlocks(any(),any(),eq(32.0),eq(FluidCollisionMode.NEVER),eq(true)))
                .thenReturn(new RayTraceResult(new Vector(0,65,3)));
        logic.fire(shooter);verify(world).rayTraceEntities(any(),any(),eq(3.0),eq(.08),any());
    }
    @Test void railHitKillsAndFriendlyFireIsRejected(){
        logic.hit(shooter,victim,100);verify(game).handleDeath(victim,shooter);
        var team=mock(GameTeam.class);when(game.teamOf(shooter)).thenReturn(team);when(game.teamOf(victim)).thenReturn(team);
        logic.hit(shooter,victim,100);verify(game,times(1)).handleDeath(victim,shooter);
    }
    @Test void respawnProtectionBlocksDamageUntilTimeout(){
        // Equip contains item metadata that requires a real server; set protection via the clock contract.
        try { var f=QuakeGame.class.getDeclaredField("protectedUntil");f.setAccessible(true);
            @SuppressWarnings("unchecked") var protection=(Map<UUID,Long>)f.get(logic);protection.put(victim.getUniqueId(),clock.get()+2_000_000_000L);
        }catch(Exception e){throw new AssertionError(e);}
        logic.hit(shooter,victim,100);verify(game,never()).handleDeath(any(),any());
        clock.addAndGet(2_000_000_000L);logic.hit(shooter,victim,100);verify(game).handleDeath(victim,shooter);
    }
    @Test void abilityCooldownsAreIndependentAndBounded(){
        assertTrue(logic.ability(shooter,false));assertFalse(logic.ability(shooter,false));assertTrue(logic.ability(shooter,true));
        clock.addAndGet(6_000_000_000L);assertTrue(logic.ability(shooter,false));assertFalse(logic.ability(shooter,true));
    }
    @Test void scoreTargetEndsRoundAndOtherwiseRespawns(){
        when(game.score(shooter)).thenReturn(20);logic.onDeath(victim,shooter);verify(game).end(List.of(shooter),"quake-target");
        verify(game,never()).respawn(any(),anyInt());
    }
    @Test void voidDeathDoesNotAwardKill(){logic.onDeath(victim,null);verify(game).respawn(victim,2);verify(game,never()).addScore(any(),anyInt());}
    @Test void tiedTimeoutIsDraw(){when(game.score(any())).thenReturn(4);logic.onTimeUp();verify(game).end(List.of(),"quake-time");}
    @Test void botsWaitForReactionAndOnlyAimAtVisibleEnemies(){
        QuakeGame spy=spy(logic);doReturn(true).when(spy).fire(shooter);
        when(game.alivePlayers()).thenReturn(List.of(shooter,victim));when(shooter.hasLineOfSight(victim)).thenReturn(true);
        when(game.plugin().bots().difficultyFor(game,victim)).thenReturn(uk.co.playerready.pulsegames.core.bot.BotDifficulty.NORMAL);
        spy.botTick(shooter,1,uk.co.playerready.pulsegames.core.bot.BotDifficulty.NORMAL);
        verify(spy,never()).fire(any());
        spy.botTick(shooter,21,uk.co.playerready.pulsegames.core.bot.BotDifficulty.NORMAL);verify(spy).fire(shooter);
        when(shooter.hasLineOfSight(victim)).thenReturn(false);
        spy.botTick(shooter,80,uk.co.playerready.pulsegames.core.bot.BotDifficulty.NORMAL);verify(spy,times(1)).fire(shooter);
        verify(game.plugin().bots()).patrolDestination(game,shooter);
    }
    @Test void teamTargetUsesCombinedScore(){
        var team=mock(GameTeam.class);when(game.teamOf(shooter)).thenReturn(team);when(team.contains(shooter)).thenReturn(true);
        Player teammate=player("Friend");when(game.teamOf(teammate)).thenReturn(team);when(team.contains(teammate)).thenReturn(true);
        when(game.players()).thenReturn(List.of(shooter,teammate,victim));when(game.score(shooter)).thenReturn(9);when(game.score(teammate)).thenReturn(11);
        logic.onDeath(victim,shooter);verify(game).end(List.of(shooter,teammate),"quake-target");
    }
    @Test void catalogueExposesSoloDoublesTriplesAndSquads(){
        GameRegistry registry=new GameRegistry();uk.co.playerready.pulsegames.games.GameCatalog.registerAll(registry);
        GameType quake=registry.get("quake");assertNotNull(quake);assertTrue(quake.wip());
        assertNotNull(quake.mode("classic"));assertEquals(2,quake.mode("classic-doubles").teamSize());
        assertEquals(3,quake.mode("classic-triples").teamSize());assertEquals(4,quake.mode("classic-squads").teamSize());
    }
}
