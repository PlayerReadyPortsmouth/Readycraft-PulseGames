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
    Player player(String name) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getName()).thenReturn(name);
        return p;
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
        when(game.type().id()).thenReturn("prophunt");
        for(int i=0;i<5;i++) bots.reconcile(game);
        verify(provider,never()).create(any(),any());
        when(game.type().id()).thenReturn("duels");
        when(plugin.canUseNewFeatures(human)).thenReturn(false);
        for(int i=0;i<5;i++) bots.reconcile(game);
        verify(provider,never()).create(any(),any());
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
}
