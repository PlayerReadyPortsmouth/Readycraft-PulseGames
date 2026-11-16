package uk.co.playerready.pulsegames.games.christmas;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChristmasOutcomeTest {
    private GameInstance game() {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.arena().settings()).thenReturn(new MemoryConfiguration());
        return game;
    }
    private Player player() {
        Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); return player;
    }
    @SuppressWarnings("unchecked")
    private <T> T state(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return (T)field.get(owner);
    }
    @Test void calendarAndCanaryGatesRunBeforeAnyWorldStateIsCreated() {
        GameInstance game = game(); Player player = player();
        assertNotNull(new PresentPanicGame(game).startProblem(List.of(player)));
        assertNotNull(new GrinchHuntGame(game).startProblem(List.of(player, player())));
        when(game.plugin().seasons().gameEnabled(anyString())).thenReturn(true);
        assertTrue(new PresentPanicGame(game).startProblem(List.of(player)).contains("trial"));
        assertTrue(new GrinchHuntGame(game).startProblem(List.of(player, player())).contains("trial"));
        verify(game, never()).world();
    }
    @Test void presentTimeoutSharesEqualLeadingTeamsAndRejectsIdleZeroPointWin() throws Exception {
        GameInstance game = game(); Player red = player(), blue = player(), green = player();
        when(game.alivePlayers()).thenReturn(List.of(red, blue, green));
        GameTeam redTeam = new GameTeam(0), blueTeam = new GameTeam(1), greenTeam = new GameTeam(2);
        when(game.teamOf(red)).thenReturn(redTeam); when(game.teamOf(blue)).thenReturn(blueTeam); when(game.teamOf(green)).thenReturn(greenTeam);
        PresentPanicGame logic = new PresentPanicGame(game);
        logic.onTimeUp(); verify(game).end(List.of(), "sleigh-launch");
        Map<Integer, Integer> points = state(logic, "points"); points.put(0, 12); points.put(1, 12); points.put(2, 6);
        logic.onTimeUp(); verify(game).end(List.of(red, blue), "sleigh-launch");
    }
    @Test void grinchTimeoutRequiresCargoOnUncaughtActiveThief() throws Exception {
        GameInstance game = game(); Player thief = player(), caughtThief = player(), villager = player();
        when(game.alivePlayers()).thenReturn(List.of(thief, caughtThief, villager));
        when(game.isActive(any())).thenReturn(true);
        GrinchHuntGame logic = new GrinchHuntGame(game);
        Set<UUID> grinches = state(logic, "grinches"); grinches.add(thief.getUniqueId()); grinches.add(caughtThief.getUniqueId());
        Set<UUID> caught = state(logic, "caught"); caught.add(caughtThief.getUniqueId());
        Map<UUID, List<String>> cargo = state(logic, "carrying"); cargo.put(caughtThief.getUniqueId(), List.of("gh.tree.1"));
        logic.onTimeUp(); verify(game).end(List.of(villager), "christmas-morning");
        cargo.put(thief.getUniqueId(), List.of("gh.tree.2"));
        logic.onTimeUp(); verify(game).end(List.of(thief), "christmas-morning");
    }
    @Test void configurableHeldThresholdCannotTurnNoCargoIntoAFreeWin() throws Exception {
        GameInstance game = game(); Player thief = player(), villager = player();
        when(game.alivePlayers()).thenReturn(List.of(thief, villager)); when(game.isActive(any())).thenReturn(true);
        game.arena().settings().set("timeout-held-min", 0);
        GrinchHuntGame logic = new GrinchHuntGame(game);
        Set<UUID> grinches = state(logic, "grinches"); grinches.add(thief.getUniqueId());
        logic.onTimeUp(); verify(game).end(List.of(villager), "christmas-morning");
    }
    @Test void endedOrInactivePlayersCannotThrowAndFillTheEntityBudget() {
        GameInstance game = game(); Player player = player();
        assertFalse(new PresentPanicGame(game).throwSnowball(player));
        verify(player, never()).launchProjectile(any(), any());
    }
}
