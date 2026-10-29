package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotServiceTest {
    PulseGamesPlugin plugin;
    GameInstance game;
    BotProvider provider;
    BotService bots;
    Player human;
    World world;
    YamlConfiguration config;
    List<Player> roster;
    @BeforeEach void setup() {
        plugin = mock(PulseGamesPlugin.class);
        provider = mock(BotProvider.class);
        game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        world = mock(World.class);
        human = player("Human");
        roster = new ArrayList<>(List.of(human));
        config = new YamlConfiguration();
        config.set("bots.enabled", true); config.set("bots.wait-seconds", 2);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.canUseNewFeatures(any())).thenReturn(true);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(game.players()).thenAnswer(c -> List.copyOf(roster));
        when(game.humanCount()).thenAnswer(c -> roster.contains(human) ? 1 : 0);
        when(game.type().id()).thenReturn("duels");
        when(game.mode().id()).thenReturn("classic");
        when(game.mode().rulesId()).thenAnswer(c -> game.mode().id().replaceFirst("-(doubles|triples|squads)$", ""));
        when(game.state()).thenReturn(GameState.WAITING);
        when(game.arena().settings().getBoolean("bots-enabled",false)).thenReturn(true);
        when(game.minPlayers()).thenReturn(2); when(game.maxPlayers()).thenReturn(2);
        when(game.world()).thenReturn(world);
        when(game.arena().lobby(world)).thenReturn(new Location(world,0,70,0));
        when(game.add(any())).thenAnswer(c -> { roster.add(c.getArgument(0)); return true; });
        bots = new BotService(plugin, provider);
        doAnswer(c -> { Player p = c.getArgument(0); roster.remove(p); bots.destroy(p.getUniqueId()); return null; })
                .when(game).remove(any(), eq(false));
        when(provider.create(anyString(), any())).thenAnswer(c -> {
            BotActor actor = mock(BotActor.class);
            Player p = player("Bot_Ready");
            when(actor.player()).thenReturn(p); when(actor.valid()).thenReturn(true);
            return actor;
        });
    }
    @Test void queueCountdownUsesActualFillTimerAndDoesNotAdvanceOnRead() {
        assertEquals("Bots join in: 2s", bots.queueStatus(game));
        assertEquals("Bots join in: 2s", bots.queueStatus(game));
        bots.reconcile(game);
        assertEquals("Bots join in: 1s", bots.queueStatus(game));
        bots.reconcile(game);
        assertEquals("Bots: 1 joined", bots.queueStatus(game));
    }
    @Test void waitingLobbyUsesReloadedBotOptInButRunningRoundKeepsSnapshot() {
        var arenas = mock(uk.co.playerready.pulsegames.core.arena.ArenaService.class);
        var latest = mock(uk.co.playerready.pulsegames.core.arena.Arena.class, RETURNS_DEEP_STUBS);
        when(plugin.arenas()).thenReturn(arenas);
        when(game.arena().id()).thenReturn("wharf");
        when(arenas.byId("duels", "wharf")).thenReturn(latest);
        when(game.arena().settings().getBoolean("bots-enabled", false)).thenReturn(false);
        when(latest.settings().getBoolean("bots-enabled", false)).thenReturn(true);
        bots.reconcile(game); bots.reconcile(game);
        assertEquals(1, bots.count(game));
        when(game.state()).thenReturn(GameState.RUNNING);
        assertFalse(bots.eligible(game));
    }
    @Test void reloadedDisabledMapStopsCountdownAndRemovesWaitingBots() {
        bots.reconcile(game); bots.reconcile(game);
        when(game.arena().settings().getBoolean("bots-enabled", false)).thenReturn(false);
        bots.reconcile(game);
        assertEquals(0, bots.count(game));
        assertEquals("Bots: disabled on this map", bots.queueStatus(game));
    }
    @Test void blockedQueuesShowReasonInsteadOfEndlessCountdown() {
        config.set("bots.max-total", 0);
        assertEquals("Bots: capacity limit", bots.queueStatus(game));
        config.set("bots.enabled", false);
        assertEquals("Bots: disabled", bots.queueStatus(game));
        config.set("bots.enabled", true);
        when(plugin.canUseNewFeatures(human)).thenReturn(false);
        assertEquals("Bots: testing access required", bots.queueStatus(game));
        when(plugin.canUseNewFeatures(human)).thenReturn(true);
        when(game.type().id()).thenReturn("dropper");
        assertEquals("Bots: unsupported mode", bots.queueStatus(game));
    }
    Player player(String name) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getName()).thenReturn(name);
        return p;
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"quake,classic", "tntrun,classic", "volcano,classic", "volcano,eruption", "avalanche,classic", "spleef,classic", "spleef,decay", "spleef,splegg"})
    void hazardModesFillNormalQueue(String type, String mode) {
        when(game.type().id()).thenReturn(type);when(game.mode().id()).thenReturn(mode);
        bots.reconcile(game);bots.reconcile(game);
        assertEquals(1,bots.count(game));verify(game).add(any());
    }
    void kartRoute() {
        when(game.type().id()).thenReturn("karts"); when(game.mode().id()).thenReturn("sprint");
        var settings = new YamlConfiguration(); settings.set("bots-enabled",true);
        settings.set("bot-waypoint1","0,64,0"); settings.set("bot-waypoint2","0,64,20");
        settings.set("bot-waypoint3","20,64,20"); settings.set("bot-waypoint4","20,64,0");
        settings.set("checkpoint-spawn1","0,64,12");
        var regions = Map.of("checkpoint1",new uk.co.playerready.pulsegames.core.util.Cuboid(-1,63,10,1,66,11),
                "finish",new uk.co.playerready.pulsegames.core.util.Cuboid(9,63,-1,10,66,1));
        when(game.arena().settings()).thenReturn(settings); when(game.arena().regions()).thenReturn(regions);
    }
    @Test void kartQueuesRequireMappedRouteAndKeepSmallLobbyException() {
        kartRoute(); assertTrue(bots.supports("karts","sprint")); assertTrue(bots.supports("karts","cup"));
        when(game.maxPlayers()).thenReturn(4);
        bots.reconcile(game); bots.reconcile(game); assertEquals(3,bots.count(game));
        bots.makeRoom(game,1); assertEquals(2,bots.count(game));
    }
    @Test void kartLargeQueueStillCapsBotsAtHalfCapacity() {
        kartRoute(); when(game.maxPlayers()).thenReturn(12);
        bots.reconcile(game); bots.reconcile(game); assertEquals(6,bots.count(game));
    }
    @Test void missingKartRouteReportsActionableReasonWithoutCreatingBots() {
        kartRoute(); game.arena().settings().set("bot-waypoint2",null);
        assertTrue(bots.queueStatus(game).contains("kart route:"));
        bots.reconcile(game); bots.reconcile(game); verify(provider,never()).create(any(),any());
    }
    @Test void waitsThenAddsActualContestantToNormalInstance() {
        bots.reconcile(game); verify(provider, never()).create(any(),any());
        bots.reconcile(game);
        assertEquals(2, roster.size()); assertEquals(1,bots.count(game));
        Player bot = roster.get(1);
        assertTrue(bots.isBot(bot)); assertSame(bot,bots.player(bot.getUniqueId()));
        verify(game).add(bot);
    }
    @Test void normalHumanCanReclaimBotSeatBeforeStart() {
        bots.reconcile(game); bots.reconcile(game);
        Player bot = roster.get(1);
        bots.makeRoom(game,1);
        verify(game).remove(bot,false); assertEquals(0,bots.count(game));
    }
    @Test void neverReplacesContestantsDuringRunningRound() {
        bots.reconcile(game); bots.reconcile(game);
        when(game.state()).thenReturn(GameState.RUNNING);
        bots.makeRoom(game,1);
        assertEquals(1,bots.count(game));
        verify(game,never()).remove(any(),anyBoolean());
    }
    @Test void lastHumanLeavingTriggersWorldCleanup() {
        bots.reconcile(game); bots.reconcile(game);
        roster.remove(human);
        bots.reconcile(game);
        verify(game).cleanup();
    }
    @Test void unsupportedGamesAndNonCanaryPlayersNeverReceiveBots() {
        when(game.type().id()).thenReturn("buildbattle");
        for(int i=0;i<5;i++) bots.reconcile(game);
        verify(provider,never()).create(any(),any());
        when(game.type().id()).thenReturn("duels");
        when(plugin.canUseNewFeatures(human)).thenReturn(false);
        for(int i=0;i<5;i++) bots.reconcile(game);
        verify(provider,never()).create(any(),any());
    }
    @Test void customPropBotsRequirePairedContentAndBlockDisguises() {
        when(game.type().id()).thenReturn("prophunt");
        config.set("content.prophunt.enabled", true);
        assertFalse(bots.eligible(game));
        config.set("content.prophunt.block-snapping", true);
        var content = mock(uk.co.playerready.pulsegames.core.content.PulseContentBridge.class);
        when(plugin.content()).thenReturn(content);
        assertFalse(bots.eligible(game));
        when(content.npcPropVisualsReady()).thenReturn(true);
        assertTrue(bots.eligible(game));
    }

    @Test void adaptiveOpponentLevelsAreFrozenAndGroupUsesLowerMedian() {
        var skills = mock(AdaptiveBotSkillStore.class);
        bots = new BotService(plugin, provider, skills);
        Player expert = player("Expert");
        roster.add(expert);
        when(game.arena().settings().getString(eq("bot-difficulty"), anyString())).thenReturn("adaptive");
        when(skills.difficulty(human.getUniqueId(), "duels:classic")).thenReturn(BotDifficulty.EASY);
        when(skills.difficulty(expert.getUniqueId(), "duels:classic")).thenReturn(BotDifficulty.HARD);
        bots.onRoundStart(game);
        assertEquals(BotDifficulty.EASY, bots.difficultyFor(game, human));
        assertEquals(BotDifficulty.HARD, bots.difficultyFor(game, expert));
        assertEquals(BotDifficulty.EASY, bots.difficultyFor(game, null));
        when(skills.difficulty(expert.getUniqueId(), "duels:classic")).thenReturn(BotDifficulty.EASY);
        assertEquals(BotDifficulty.HARD, bots.difficultyFor(game, expert));
        when(game.arena().settings().getString(eq("bot-difficulty"), anyString())).thenReturn("normal");
        assertEquals(BotDifficulty.NORMAL, bots.difficultyFor(game, expert));
    }

    @Test void onlyCompletedPresentPlayerRoundsTrainPracticeHistoryOnce() {
        var skills = mock(AdaptiveBotSkillStore.class);
        bots = new BotService(plugin, provider, skills);
        when(skills.difficulty(any(), anyString())).thenReturn(BotDifficulty.NORMAL);
        when(plugin.getServer()).thenReturn(mock(org.bukkit.Server.class, RETURNS_DEEP_STUBS));
        when(game.state()).thenReturn(GameState.RUNNING);
        when(game.gameTime()).thenReturn(3); // A legitimate fast duel still trains the profile.
        bots.onRoundStart(game);
        bots.onRoundEnd(game, List.of(human), "last-standing");
        bots.onRoundEnd(game, List.of(human), "last-standing");
        verify(skills, times(1)).record(human.getUniqueId(), "duels:classic", true, .5);
        bots.onRoundStart(game);
        bots.onRoundEnd(game, List.of(human), "forced");
        bots.onRoundStart(game);
        roster.clear();
        bots.onRoundEnd(game, List.of(human), "last-standing");
        verify(skills, times(1)).record(any(), anyString(), anyBoolean(), anyDouble());
    }

    @Test void authoredPatrolRouteAdvancesInOrderWithoutTeleporting() {
        bots.reconcile(game); bots.reconcile(game);
        Player bot = roster.get(1);
        when(game.arena().settings().getKeys(false)).thenReturn(Set.of("bot-route-courtyard"));
        Location first = new Location(world, 0.5, 80, 0.5), second = new Location(world, 6.5, 80, 0.5);
        try (var hints = mockStatic(BotNavigationHints.class)) {
            hints.when(() -> BotNavigationHints.points(game, "bot-route-courtyard")).thenReturn(List.of(first, second));
            when(bot.getLocation()).thenReturn(new Location(world, -3, 80, .5));
            assertEquals(first, bots.patrolDestination(game, bot));
            when(bot.getLocation()).thenReturn(first);
            assertEquals(second, bots.patrolDestination(game, bot));
            when(bot.getLocation()).thenReturn(second);
            assertEquals(first, bots.patrolDestination(game, bot));
            verify(bot, never()).teleport(any(Location.class));
        }
    }

    @Test void allocationUsesHumanCalibrationAndConfiguredBotStrength() {
        var skills = mock(AdaptiveBotSkillStore.class);
        bots = new BotService(plugin, provider, skills);
        when(skills.difficulty(human.getUniqueId(), "duels:classic")).thenReturn(BotDifficulty.HARD);
        bots.reconcile(game); bots.reconcile(game);
        Player bot = roster.get(1);
        when(game.arena().settings().getString(eq("bot-difficulty"), anyString())).thenReturn("adaptive");
        assertEquals(3, bots.teamStrength(game, human));
        assertEquals(3, bots.teamStrength(game, bot));
        when(game.arena().settings().getString(eq("bot-difficulty"), anyString())).thenReturn("easy");
        assertEquals(1, bots.teamStrength(game, bot));
        assertEquals(3, bots.teamStrength(game, human));
        assertEquals(bot.getUniqueId(), bots.diagnostics().getFirst().id());
        assertEquals("WAITING", bots.diagnostics().getFirst().state());
    }

    @Test void flagOffRemovesWaitingBots() {
        bots.reconcile(game); bots.reconcile(game);
        config.set("bots.enabled",false);
        bots.reconcile(game);
        assertEquals(0,bots.count(game));
    }
    @Test void cleanupRemovesTransportAndDoesNotRecreateBotsWithoutHumans() {
        bots.reconcile(game); bots.reconcile(game);
        bots.forget(game);
        assertEquals(0,bots.count(game));
        roster.clear(); bots.reconcile(game);
        verify(provider,times(1)).create(any(),any());
        bots.close(); verify(provider).close();
    }
    @Test void missingMapOptInDoesNotSpawnBots() {
        when(game.arena().settings().getBoolean("bots-enabled",false)).thenReturn(false);
        for(int i=0;i<5;i++) bots.reconcile(game);
        verifyNoInteractions(provider);
    }

    BotActor activeCoop(org.bukkit.entity.Zombie zombie, Location guard) {
        bots = new BotService(plugin, provider, bound -> 0);
        when(game.type().id()).thenReturn("villagerdefense");
        when(game.mode().id()).thenReturn("easy");
        BotActor actor = mock(BotActor.class);
        Player bot = player("Defender");
        when(actor.player()).thenReturn(bot);
        when(actor.valid()).thenReturn(true);
        when(provider.create(anyString(), any())).thenReturn(actor);
        when(bot.getLocation()).thenReturn(new Location(world, 0, 70, 0));
        when(bot.getWorld()).thenReturn(world);
        when(bot.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
        when(bot.hasLineOfSight(any(org.bukkit.entity.Entity.class))).thenReturn(true);
        MiniGame logic = mock(MiniGame.class, withSettings().extraInterfaces(BotObjectives.class));
        when(logic.acceptsDeath(bot)).thenReturn(true);
        when(logic.voidY()).thenReturn(0.0);
        when(game.logic()).thenReturn(logic);
        when(((BotObjectives) logic).botEnemies()).thenAnswer(c -> zombie == null ? List.of() : List.of(zombie));
        when(((BotObjectives) logic).botGuardPoint()).thenReturn(guard);
        when(((BotObjectives) logic).botGuardPoint(any())).thenReturn(guard);
        if (zombie != null) {
            when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
            when(zombie.isValid()).thenReturn(true);
            when(zombie.getWorld()).thenReturn(world);
            when(zombie.getLocation()).thenReturn(new Location(world, 2, 70, 0));
            when(zombie.getEyeLocation()).thenReturn(new Location(world, 2, 71, 0));
        }
        bots.reconcile(game); bots.reconcile(game);
        when(game.state()).thenReturn(GameState.RUNNING);
        when(game.isActive(bot)).thenReturn(true);
        when(game.alivePlayers()).thenReturn(List.of(bot, human));
        return actor;
    }

    @Test void mountedKartBotsNeverUseWalkingNavigationOrMelee() {
        BotActor actor = activeCoop(null,null);
        var logic = mock(uk.co.playerready.pulsegames.games.karts.PulseKartsGame.class);
        when(logic.acceptsDeath(actor.player())).thenReturn(true);
        when(logic.botStatus(actor.player())).thenReturn("Waypoint 2/8");
        when(game.logic()).thenReturn(logic);
        bots.think();
        verify(actor).stopNavigation(); verify(actor,never()).navigate(any());
        verify(actor.player(),never()).attack(any());
        assertEquals("DRIVING",bots.diagnostics().getFirst().state());
    }

    @Test void coopAttacksWaveEnemyThroughNormalPlayerCombatNotAllies() {
        var zombie = mock(org.bukkit.entity.Zombie.class);
        BotActor actor = activeCoop(zombie, null);
        bots.think();
        verify(actor.player(), never()).attack(any());
        for (int i = 0; i < 4; i++) bots.think();
        verify(actor.player()).attack(zombie);
        verify(actor.player(), never()).attack(human);
    }

    @Test void coopReturnsToMayorWhenNoWaveEnemyIsVisible() {
        Location guard = new Location(world, 10, 70, 0);
        BotActor actor = activeCoop(null, guard);
        bots.think();
        verify(actor).navigate(guard);
        verify(actor.player(), never()).attack(any());
    }

    @Test void coopNeverAttacksThroughWallsOrAcrossWorlds() {
        var zombie = mock(org.bukkit.entity.Zombie.class);
        Location guard = new Location(world, 10, 70, 0);
        BotActor actor = activeCoop(zombie, guard);
        when(actor.player().hasLineOfSight(zombie)).thenReturn(false);
        bots.think();
        verify(actor.player(), never()).attack(any());
        when(actor.player().hasLineOfSight(zombie)).thenReturn(true);
        when(zombie.getWorld()).thenReturn(mock(World.class));
        bots.think();
        verify(actor.player(), never()).attack(any());
    }

    @Test void deadAndRespawningDefendersStopActing() {
        var zombie = mock(org.bukkit.entity.Zombie.class);
        BotActor actor = activeCoop(zombie, null);
        when(game.logic().acceptsDeath(actor.player())).thenReturn(false);
        bots.think();
        verify(actor).stopNavigation();
        verify(actor.player(), never()).attack(any());
    }

    @Test void easyAttacksLessFrequentlyThanHardWithoutChangingDamage() {
        var zombie = mock(org.bukkit.entity.Zombie.class);
        BotActor actor = activeCoop(zombie, null);
        when(game.arena().settings().getString(eq("bot-difficulty"), anyString())).thenReturn("easy");
        for (int i = 0; i < 6; i++) bots.think();
        verify(actor.player(), times(1)).attack(zombie);
        when(game.arena().settings().getString(eq("bot-difficulty"), anyString())).thenReturn("hard");
        for (int i = 0; i < 5; i++) bots.think();
        verify(actor.player(), times(2)).attack(zombie);
    }

    @Test void defenderEquipsEarnedUpgradeFromHotbar() {
        Player bot = mock(Player.class);
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
        when(bot.getInventory()).thenReturn(inventory);
        var stone = mock(org.bukkit.inventory.ItemStack.class);
        var diamond = mock(org.bukkit.inventory.ItemStack.class);
        when(stone.getType()).thenReturn(org.bukkit.Material.STONE_SWORD);
        when(diamond.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);
        when(inventory.getItem(0)).thenReturn(stone);
        when(inventory.getItem(4)).thenReturn(diamond);
        BotService.selectSword(bot);
        verify(inventory).setHeldItemSlot(4);
    }
    @Test void privatePracticeUsesFixedDifficultyAndRequestedCount() {
        when(game.practice()).thenReturn(true);
        when(game.practiceDifficulty()).thenReturn(BotDifficulty.EASY);
        when(game.practiceBotTarget()).thenReturn(1);
        when(game.maxPlayers()).thenReturn(4);
        bots.reconcile(game); bots.reconcile(game);
        assertEquals(1,bots.count(game));
        assertEquals(BotDifficulty.EASY,bots.difficultyFor(game,human));
        assertEquals(1,bots.teamStrength(game,human));
    }
    @Test void privatePracticeDoesNotCalibratePlayerHistory() {
        var store=mock(AdaptiveBotSkillStore.class);
        bots=new BotService(plugin,provider,store);
        when(game.practice()).thenReturn(true);
        bots.onRoundStart(game);
        when(game.state()).thenReturn(GameState.RUNNING);
        bots.onRoundEnd(game,List.of(human),"last-standing");
        verifyNoInteractions(store);
    }

    // ---- PvP combat core (duels / kitpvp / koth) ----
    org.bukkit.inventory.PlayerInventory pvpInventory;
    BotActor pvp(double targetZ, String difficulty, java.util.function.IntUnaryOperator rnd) {
        bots = new BotService(plugin, provider, rnd);
        bots.walkable = feet -> true; // no server registries in unit tests; terrain checks are covered in GroundNavigationTest
        bots.eatEffects = bot -> bot.addPotionEffect(null);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
            org.bukkit.Material type = (int) call.getArgument(1) == 79 ? org.bukkit.Material.STONE : org.bukkit.Material.AIR;
            when(block.getType()).thenReturn(type); when(block.isPassable()).thenReturn(type == org.bukkit.Material.AIR);
            return block;
        });
        BotActor actor = mock(BotActor.class);
        Player bot = player("Fighter");
        when(actor.player()).thenReturn(bot); when(actor.valid()).thenReturn(true);
        when(provider.create(anyString(), any())).thenReturn(actor);
        pvpInventory = mock(org.bukkit.inventory.PlayerInventory.class);
        when(pvpInventory.first(any(org.bukkit.Material.class))).thenReturn(-1);
        when(bot.getInventory()).thenReturn(pvpInventory);
        when(bot.getWorld()).thenReturn(world);
        when(bot.getLocation()).thenReturn(new Location(world, 0.5, 80, 0.5));
        when(bot.getEyeLocation()).thenReturn(new Location(world, 0.5, 81.6, 0.5));
        when(bot.getHealth()).thenReturn(20.0); when(bot.getAttackCooldown()).thenReturn(1f);
        when(bot.getMaximumNoDamageTicks()).thenReturn(20); when(bot.isOnGround()).thenReturn(true);
        when(bot.canSee(human)).thenReturn(true); when(bot.hasLineOfSight(human)).thenReturn(true);
        when(human.getWorld()).thenReturn(world);
        when(human.getLocation()).thenReturn(new Location(world, 0.5, 80, targetZ));
        when(human.getEyeLocation()).thenReturn(new Location(world, 0.5, 81.6, targetZ));
        MiniGame logic = mock(MiniGame.class);
        when(logic.acceptsDeath(bot)).thenReturn(true); when(logic.voidY()).thenReturn(0.0);
        when(game.logic()).thenReturn(logic);
        when(game.arena().settings().getString(eq("bot-difficulty"), anyString())).thenReturn(difficulty);
        bots.reconcile(game); bots.reconcile(game);
        when(game.state()).thenReturn(GameState.RUNNING);
        when(game.isActive(bot)).thenReturn(true); when(game.isActive(human)).thenReturn(true);
        when(game.alivePlayers()).thenReturn(List.of(bot, human));
        return actor;
    }

    @Test void aMissStillSwingsAtAnOffsetAndNeverLandsADamagingHit() {
        BotActor actor = pvp(2.5, "hard", bound -> bound - 1); // every roll is the worst: always a miss
        for (int i = 0; i < 12; i++) bots.think();
        verify(actor.player(), atLeastOnce()).swingMainHand();
        verify(actor.player(), never()).attack(any());
        verify(actor, atLeastOnce()).face(argThat(at -> Math.abs(at.getX() - 0.5) >= 0.9));
    }

    @Test void meleeRangeBotsStrafeInsteadOfStandingStill() {
        BotActor actor = pvp(2.5, "normal", bound -> 0);
        for (int i = 0; i < 6; i++) bots.think();
        verify(actor, atLeastOnce()).navigate(argThat(goal -> Math.abs(goal.getX() - 0.5) >= 1.5));
        verify(actor.player(), atLeastOnce()).attack(human);
    }

    @Test void strafeCanBeSwitchedOffAndThenTheBotJustTradesBlows() {
        config.set("bots.combat.strafe", false);
        BotActor actor = pvp(2.0, "normal", bound -> 0);
        for (int i = 0; i < 6; i++) bots.think();
        verify(actor, never()).navigate(any());
    }

    @Test void sprintsOnTheHitAndTheSwitchTurnsItOff() {
        BotActor actor = pvp(2.0, "hard", bound -> 0);
        for (int i = 0; i < 6; i++) bots.think();
        verify(actor.player(), atLeastOnce()).setSprinting(true);
        config.set("bots.combat.sprint", false);
        BotActor off = pvp(2.0, "hard", bound -> 0);
        for (int i = 0; i < 6; i++) bots.think();
        verify(off.player(), never()).setSprinting(true);
    }

    @Test void picksTheSwordWhenSlotZeroIsNotOne() {
        BotActor actor = pvp(2.0, "normal", bound -> 0);
        var sword = mock(org.bukkit.inventory.ItemStack.class);
        when(sword.getType()).thenReturn(org.bukkit.Material.IRON_SWORD);
        when(pvpInventory.getItem(3)).thenReturn(sword);
        for (int i = 0; i < 4; i++) bots.think();
        verify(pvpInventory, atLeastOnce()).setHeldItemSlot(3);
    }

    @Test void hurtBotEatsAGoldenAppleBeforeReEngaging() {
        BotActor actor = pvp(3.0, "normal", bound -> 0);
        Player bot = actor.player();
        var server = mock(org.bukkit.Server.class);
        when(bot.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        var apple = mock(org.bukkit.inventory.ItemStack.class);
        when(apple.getType()).thenReturn(org.bukkit.Material.GOLDEN_APPLE); when(apple.getAmount()).thenReturn(3);
        when(pvpInventory.first(org.bukkit.Material.GOLDEN_APPLE)).thenReturn(2);
        when(pvpInventory.getItem(2)).thenReturn(apple);
        when(bot.getHealth()).thenReturn(5.0);
        for (int i = 0; i < 4; i++) bots.think();
        verify(apple).setAmount(2);
        verify(bot).addPotionEffect(null); // the (stubbed) golden apple effects ran once
        verify(bot, never()).attack(any());
    }

    @Test void archerShootsFromRangeAndCountsTheArrow() {
        BotActor actor = pvp(11.0, "normal", bound -> 0);
        Player bot = actor.player();
        var server = mock(org.bukkit.Server.class);
        when(bot.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        var bow = mock(org.bukkit.inventory.ItemStack.class); var ammo = mock(org.bukkit.inventory.ItemStack.class);
        when(bow.getType()).thenReturn(org.bukkit.Material.BOW); when(ammo.getType()).thenReturn(org.bukkit.Material.ARROW);
        when(ammo.getAmount()).thenReturn(16);
        when(pvpInventory.first(org.bukkit.Material.BOW)).thenReturn(1); when(pvpInventory.first(org.bukkit.Material.ARROW)).thenReturn(9);
        when(pvpInventory.getItem(1)).thenReturn(bow); when(pvpInventory.getItem(9)).thenReturn(ammo);
        var arrow = mock(org.bukkit.entity.Arrow.class); when(arrow.isValid()).thenReturn(true);
        when(bot.launchProjectile(eq(org.bukkit.entity.Arrow.class), any())).thenReturn(arrow);
        for (int i = 0; i < 3; i++) bots.think();
        verify(bot, never()).launchProjectile(eq(org.bukkit.entity.Arrow.class), any());
        for (int i = 0; i < 8; i++) bots.think();
        verify(bot, atLeastOnce()).launchProjectile(eq(org.bukkit.entity.Arrow.class), any());
        verify(ammo, atLeastOnce()).setAmount(15);
        verify(bot, never()).attack(any());
    }

    BotActor kothScene(double enemyX, double enemyZ) {
        BotActor actor = pvp(5.0, "normal", bound -> 0);
        when(game.type().id()).thenReturn("koth"); when(game.mode().id()).thenReturn("solo");
        var koth = mock(uk.co.playerready.pulsegames.games.koth.KothGame.class);
        when(koth.acceptsDeath(actor.player())).thenReturn(true); when(koth.voidY()).thenReturn(0.0);
        when(koth.botHill()).thenReturn(new uk.co.playerready.pulsegames.core.util.Cuboid(10, 80, 10, 15, 85, 15));
        when(game.logic()).thenReturn(koth);
        when(human.getLocation()).thenReturn(new Location(world, enemyX, 80, enemyZ));
        when(human.getEyeLocation()).thenReturn(new Location(world, enemyX, 81.6, enemyZ));
        return actor;
    }

    @Test void kothBotHeadsForTheActiveHillRatherThanChasingADistantRival() {
        BotActor actor = kothScene(0.5, 7.0);
        for (int i = 0; i < 4; i++) bots.think();
        verify(actor, atLeastOnce()).navigate(argThat(goal -> goal.getX() >= 10 && goal.getX() <= 16 && goal.getZ() >= 10 && goal.getZ() <= 16));
    }

    @Test void kothBotFightsARivalWhoStandsOnTheHill() {
        BotActor actor = kothScene(12.5, 12.5);
        for (int i = 0; i < 4; i++) bots.think();
        verify(actor, atLeastOnce()).navigate(argThat(goal -> Math.abs(goal.getX() - 12.5) < 3.1 && Math.abs(goal.getZ() - 12.5) < 3.1));
    }
}
