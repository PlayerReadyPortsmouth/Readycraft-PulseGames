package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.content.PulseContentBridge;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameType;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgressionServiceTest {
    @TempDir Path directory;
    PulseGamesPlugin plugin;
    PulseContentBridge content;
    YamlConfiguration config;
    ProgressionService service;
    GameInstance game;
    Player player;
    Runnable retry;

    @BeforeEach void setup() {
        plugin=mock(PulseGamesPlugin.class);
        content=mock(PulseContentBridge.class);
        Server server=mock(Server.class);
        BukkitScheduler scheduler=mock(BukkitScheduler.class);
        config=new YamlConfiguration();
        config.set("progression.enabled",true);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.content()).thenReturn(content);
        doAnswer(call->{retry=call.getArgument(1);return null;})
                .when(scheduler).runTaskTimer(eq(plugin),any(Runnable.class),eq(40L),eq(600L));
        doAnswer(call->{call.<Runnable>getArgument(1).run();return null;})
                .when(scheduler).runTask(eq(plugin),any(Runnable.class));
        service=new ProgressionService(plugin);
        game=mock(GameInstance.class);
        GameType type=mock(GameType.class);
        player=mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(game.id()).thenReturn("test-match");
        when(game.type()).thenReturn(type);
        when(type.id()).thenReturn("duels");
        when(game.ranked()).thenReturn(true);
        when(game.isParticipant(player)).thenReturn(true);
    }
    int retained() throws Exception {
        return new ProgressionOutbox(directory.resolve("progression-outbox")).pending().size();
    }
    void complete() {service.completed(game,player,true,2,"last-team");}

    @Test void onlyRankedHumanParticipantsEnqueue() throws Exception {
        when(game.ranked()).thenReturn(false); complete();
        when(game.ranked()).thenReturn(true);
        when(game.isParticipant(player)).thenReturn(false); complete();
        when(game.isParticipant(player)).thenReturn(true);
        when(game.isBot(player)).thenReturn(true); complete();
        when(game.isBot(player)).thenReturn(false);
        for(String reason:new String[]{"forced","error","shutdown","aborted"})
            service.completed(game,player,true,2,reason);
        assertEquals(0,retained());
        complete();
        assertEquals(1,retained());
        verifyNoInteractions(content);
    }
    @Test void failureRetainsReceiptThenRetries() throws Exception {
        when(content.recordCompetitiveMatch(any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("SQL unavailable")),
                CompletableFuture.completedFuture(false));
        complete();retry.run();
        assertEquals(1,retained());
        retry.run();
        assertEquals(1,retained());
        verify(content,times(2)).recordCompetitiveMatch(any());
    }
    @Test void successfulDeliveryAcknowledgesOnceAndDoesNotDuplicateInFlight() throws Exception {
        CompletableFuture<Boolean> delivery=new CompletableFuture<>();
        when(content.recordCompetitiveMatch(any())).thenReturn(delivery);
        complete();retry.run();retry.run();
        assertEquals(1,retained());
        verify(content).recordCompetitiveMatch(any());
        delivery.complete(true);
        assertEquals(0,retained());
        retry.run();
        verify(content).recordCompetitiveMatch(any());
    }
    @Test void unavailableProviderDoesNotLoseDurableReceipt() throws Exception {
        // The real bridge reports absent/disabled PulseContent as a failed future.
        when(content.recordCompetitiveMatch(any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("PulseContent unavailable")));
        complete();retry.run();
        assertEquals(1,retained());
        when(content.recordCompetitiveMatch(any())).thenReturn(CompletableFuture.completedFuture(true));
        retry.run();
        assertEquals(0,retained());
    }
    @Test void disabledDoesNotCollectOrDeliverExistingReceipts() throws Exception {
        complete();
        config.set("progression.enabled",false);
        when(game.id()).thenReturn("second-match");
        complete();retry.run();
        assertEquals(1,retained());
        verifyNoInteractions(content);
    }
}
