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
    @org.junit.jupiter.params.provider.CsvSource({"tntrun,classic", "volcano,classic", "volcano,eruption", "avalanche,classic", "spleef,classic", "spleef,decay", "spleef,splegg"})
    void hazardModesFillNormalQueue(String type, String mode) {
        when(game.type().id()).thenReturn(type);when(game.mode().id()).thenReturn(mode);
        bots.reconcile(game);bots.reconcile(game);
        assertEquals(1,bots.count(game));verify(game).add(any());
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
        if (zombie != null) {
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

    @Test void coopAttacksWaveEnemyThroughNormalPlayerCombatNotAllies() {
        var zombie = mock(org.bukkit.entity.Zombie.class);
        BotActor actor = activeCoop(zombie, null);
        bots.think();
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
        verify(actor.player(), times(4)).attack(zombie);
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
}
