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
    void movementFloor() {
        when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        var stone=mock(org.bukkit.block.Block.class,RETURNS_DEEP_STUBS);var air=mock(org.bukkit.block.Block.class,RETURNS_DEEP_STUBS);
        when(stone.getType()).thenReturn(Material.STONE);when(stone.getBlockData().isOccluding()).thenReturn(true);when(air.getType()).thenReturn(Material.AIR);when(air.isEmpty()).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(call -> (int)call.getArgument(1)==63?stone:air);
        when(game.arena().region("spawn-area")).thenReturn(new uk.co.playerready.pulsegames.core.util.Cuboid(-30,64,-30,30,65,30));
    }
    @Test void closeBotsRetreatAndStrafeRatherThanCharge() {
        movementFloor();when(victim.getLocation()).thenReturn(new Location(world,0,64,4));
        Location goal=logic.combatDestination(shooter,victim,20);
        assertTrue(goal.getZ()<0);assertTrue(Math.abs(goal.getX())>0);
    }
    @Test void woundedBotsRetreatEvenFromDistantTarget() {
        movementFloor();when(victim.getLocation()).thenReturn(new Location(world,0,64,20));
        logic.hit(victim,shooter,60);
        assertTrue(logic.combatDestination(shooter,victim,20).getZ()<0);
    }
    @Test void healthyBotsStrafeAtWeaponRangeAndAdvanceFromTooFarAway() {
        movementFloor();when(victim.getLocation()).thenReturn(new Location(world,0,64,14));
        Location goal=logic.combatDestination(shooter,victim,20);assertEquals(0,goal.getZ());assertTrue(Math.abs(goal.getX())>0);
        when(victim.getLocation()).thenReturn(new Location(world,0,64,22));assertTrue(logic.combatDestination(shooter,victim,20).getZ()>0);
    }
    @Test void retreatDoesNotRequestMovementAcrossVoid() {
        movementFloor();var air=mock(org.bukkit.block.Block.class,RETURNS_DEEP_STUBS);when(air.getType()).thenReturn(Material.AIR);when(air.isEmpty()).thenReturn(true);
        when(world.getBlockAt(anyInt(),eq(63),anyInt())).thenReturn(air);
        when(victim.getLocation()).thenReturn(new Location(world,0,64,4));
        assertEquals(shooter.getLocation(),logic.combatDestination(shooter,victim,20));
    }
    @Test void teamRespawnsAlsoUseArenaInteriorAndFallbackWhenNoSafeFloor() {
        movementFloor();when(game.mode().isTeams()).thenReturn(true);
        when(game.arena().region("spawn-area")).thenReturn(new uk.co.playerready.pulsegames.core.util.Cuboid(10,64,10,10,65,10));
        Location point=logic.respawnLocation(shooter);assertEquals(10.5,point.getX());assertEquals(10.5,point.getZ());
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        Location fallback=new Location(world,0,64,0);when(game.arena().spawnCount()).thenReturn(1);when(game.arena().spawn(0,world)).thenReturn(fallback);
        assertEquals(fallback,logic.respawnLocation(shooter));
    }

    @Test void selectionRequiresTwoDistinctAbilitiesAndDoesNotGrantUnselectedActions(){
        assertFalse(logic.selectAbilities(shooter,QuakeGame.Ability.SHIELD,QuakeGame.Ability.SHIELD));
        assertEquals(List.of(QuakeGame.Ability.DASH,QuakeGame.Ability.JUMP),logic.abilities(shooter));
        assertTrue(logic.selectAbilities(shooter,QuakeGame.Ability.SHIELD,QuakeGame.Ability.SPRINT));
        assertFalse(logic.ability(shooter,QuakeGame.Ability.DASH));
        assertTrue(logic.ability(shooter,QuakeGame.Ability.SHIELD));
        assertFalse(logic.ability(shooter,QuakeGame.Ability.SHIELD));
        logic.selectAbilities(shooter,QuakeGame.Ability.DASH,QuakeGame.Ability.JUMP);
        logic.selectAbilities(shooter,QuakeGame.Ability.SHIELD,QuakeGame.Ability.SPRINT);
        assertFalse(logic.ability(shooter,QuakeGame.Ability.SHIELD));
    }
    @Test void shieldHalvesDamageThenExpiresWithoutGrantingInvulnerability() throws Exception {
        var field=QuakeGame.class.getDeclaredField("protectedUntil");field.setAccessible(true);
        @SuppressWarnings("unchecked") var protectedTimes=(Map<UUID,Long>)field.get(logic);
        protectedTimes.put(victim.getUniqueId(),clock.get()+2_000_000_000L);
        logic.selectAbilities(victim,QuakeGame.Ability.SHIELD,QuakeGame.Ability.JUMP);
        assertTrue(logic.ability(victim,QuakeGame.Ability.SHIELD));
        logic.hit(shooter,victim,100);verify(game,never()).handleDeath(any(),any());
        clock.addAndGet(2_000_000_000L);logic.hit(shooter,victim,50);verify(game).handleDeath(victim,shooter);
    }
    @Test void sprintRestoresOriginalSpeedOnExpiryAndLeaving(){
        when(shooter.getWalkSpeed()).thenReturn(.18f);when(game.everyone()).thenReturn(List.of(shooter));
        logic.selectAbilities(shooter,QuakeGame.Ability.SPRINT,QuakeGame.Ability.SHIELD);
        assertTrue(logic.ability(shooter,QuakeGame.Ability.SPRINT));verify(shooter).setWalkSpeed(.27f);
        clock.addAndGet(3_000_000_000L);logic.onSecond(3);verify(shooter).setWalkSpeed(.18f);
        clock.addAndGet(8_000_000_000L);assertTrue(logic.ability(shooter,QuakeGame.Ability.SPRINT));
        logic.onQuit(shooter);verify(shooter,times(2)).setWalkSpeed(.18f);
        assertEquals(List.of(QuakeGame.Ability.SPRINT,QuakeGame.Ability.SHIELD),logic.abilities(shooter));
    }
    @Test void pulseBlasterNeedsThreeHitsAndUsesItsOwnReloadWithoutMultipleRays() throws Exception {
        var field=QuakeGame.class.getDeclaredField("weapons");field.setAccessible(true);
        @SuppressWarnings("unchecked") var weapons=(Map<UUID,QuakeGame.Weapon>)field.get(logic);
        weapons.put(shooter.getUniqueId(),QuakeGame.Weapon.BLASTER);
        assertTrue(logic.fire(shooter));assertFalse(logic.fire(shooter));
        verify(world,times(1)).rayTraceEntities(any(),any(),eq(24.0),eq(.08),any());
        clock.addAndGet(350_000_000L);assertTrue(logic.fire(shooter));
        logic.hit(shooter,victim,34);logic.hit(shooter,victim,34);verify(game,never()).handleDeath(any(),any());
        logic.hit(shooter,victim,34);verify(game).handleDeath(victim,shooter);
    }

}
