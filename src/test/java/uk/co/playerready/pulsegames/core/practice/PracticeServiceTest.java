package uk.co.playerready.pulsegames.core.practice;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;
import static org.mockito.Mockito.*;

class PracticeServiceTest {
    PulseGamesPlugin plugin;
    Player owner;
    PracticeService service;
    GameInstance game;
    Arena arena;
    GameType type;
    GameMode mode;
    PracticeService.Scenario scenario = new PracticeService.Scenario("duels","classic","test",BotDifficulty.EASY,1);
    @BeforeEach void setup() {
        plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);
        owner=mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.hasPermission("pulsegames.admin")).thenReturn(true);
        when(owner.isOnline()).thenReturn(true);
        when(plugin.getServer().getPlayer(owner.getUniqueId())).thenReturn(owner);
        when(plugin.canUseNewFeatures(owner)).thenReturn(true);
        when(plugin.instances().byPlayer(owner)).thenReturn(null);
        YamlConfiguration config=new YamlConfiguration();config.set("bots.enabled",true);
        when(plugin.getConfig()).thenReturn(config);
        type=mock(GameType.class);mode=GameMode.ffa("classic","Classic",2,2);
        when(type.id()).thenReturn("duels");when(type.mode("classic")).thenReturn(mode);
        when(plugin.registry().get("duels")).thenReturn(type);
        when(plugin.gameAvailable(type)).thenReturn(true);
        when(plugin.bots().supports("duels","classic")).thenReturn(true);
        arena=mock(Arena.class,RETURNS_DEEP_STUBS);
        when(arena.supportsMode("classic")).thenReturn(true);
        when(arena.settings().getBoolean("bots-enabled",false)).thenReturn(true);
        when(plugin.arenas().byId("duels","test")).thenReturn(arena);
        game=mock(GameInstance.class);
        when(game.minPlayers()).thenReturn(2);when(game.maxPlayers()).thenReturn(2);
        when(game.state()).thenReturn(GameState.WAITING);when(game.id()).thenReturn("test-instance");
        when(game.add(owner)).thenReturn(true);when(game.isParticipant(owner)).thenReturn(true);
        when(plugin.instances().createPractice(type,mode,arena,BotDifficulty.EASY,1)).thenReturn(game);
        service=new PracticeService(plugin);
    }
    @Test void permissionRequiredEvenWhenCalledOutsideCommand() {
        when(owner.hasPermission("pulsegames.admin")).thenReturn(false);
        service.begin(owner,scenario);
        verify(plugin.instances(),never()).createPractice(any(),any(),any(),any(),anyInt());
    }
    @Test void privateInstanceWaitsForLoadThenAdmitsOwnerOnce() {
        when(game.state()).thenReturn(GameState.LOADING);
        service.begin(owner,scenario);service.tick();verify(game,never()).add(any());
        when(game.state()).thenReturn(GameState.WAITING);
        service.tick();service.tick();verify(game,times(1)).add(owner);
    }
    @Test void anotherQueueDuringLoadCancelsPracticeWithoutMovingPlayer() {
        service.begin(owner,scenario);
        when(plugin.playService().hasPending(owner)).thenReturn(true);
        service.tick();verify(game).cleanup();verify(game,never()).add(owner);
    }
    @Test void ownerDisconnectCleansClone() {
        service.begin(owner,scenario);when(owner.isOnline()).thenReturn(false);
        service.tick();verify(game).cleanup();verify(game,never()).add(owner);
    }
    @Test void rejectedAdmissionCleansClone() {
        when(game.add(owner)).thenReturn(false);service.begin(owner,scenario);service.tick();
        verify(game).cleanup();
    }
    @Test void tooFewTestersForMapMinimumDoesNotLeavePrivateClone() {
        when(game.minPlayers()).thenReturn(4);service.begin(owner,scenario);
        verify(game).cleanup();service.tick();verify(game,never()).add(owner);
    }
    @Test void repeatReusesScenarioButRefusesConcurrentSession() {
        service.begin(owner,scenario);service.handle(owner,new String[]{"repeat"});
        verify(plugin.instances(),times(1)).createPractice(type,mode,arena,BotDifficulty.EASY,1);
        service.handle(owner,new String[]{"end"});service.handle(owner,new String[]{"repeat"});
        verify(plugin.instances(),times(2)).createPractice(type,mode,arena,BotDifficulty.EASY,1);
    }
}
