package uk.co.playerready.pulsegames.games.readyplayerz;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ZDifficultyBotSummaryTest {
    @Test void difficultyDefaultsRelaxedForSmallSquadsAndScalesTuning() {
        assertEquals(ZCombatBalance.Difficulty.RELAXED, ZCombatBalance.Difficulty.parse(null, 2));
        assertEquals(ZCombatBalance.Difficulty.NORMAL, ZCombatBalance.Difficulty.parse(null, 3));
        assertEquals(ZCombatBalance.Difficulty.HARD, ZCombatBalance.Difficulty.parse(" Hard ", 1));
        assertEquals(ZCombatBalance.Difficulty.RELAXED, ZCombatBalance.Difficulty.parse("nonsense", 1));
        var base = ZCombatBalance.Tuning.DEFAULT;
        double hard = ZCombatBalance.stats(1, ZCombatBalance.Variant.WALKER, base.scaled(ZCombatBalance.Difficulty.HARD.health)).health();
        double easy = ZCombatBalance.stats(1, ZCombatBalance.Variant.WALKER, base.scaled(ZCombatBalance.Difficulty.RELAXED.health)).health();
        assertEquals(12.5, hard, .001);
        assertEquals(7.5, easy, .001);
    }
    @Test void botCrouchesBesideDownedHumanAndFollowsThem() {
        var game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.arena()).thenReturn(ReadyPlayerZBotTest.arena());
        World world = game.world();
        Player bot = mock(Player.class, RETURNS_DEEP_STUBS), human = mock(Player.class);
        for (Player p : List.of(bot, human)) {
            when(p.getUniqueId()).thenReturn(UUID.randomUUID());
            when(p.getWorld()).thenReturn(world);
            when(game.isActive(p)).thenReturn(true);
        }
        when(game.isBot(bot)).thenReturn(true);
        when(bot.getHealth()).thenReturn(20.0);
        when(bot.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        Location down = new Location(world, 1, 64, 0);
        when(human.getLocation()).thenReturn(down);
        when(game.alivePlayers()).thenReturn(List.of(bot, human));
        when(game.state()).thenReturn(GameState.RUNNING);
        var logic = spy(new ReadyPlayerZGame(game));
        doNothing().when(logic).applyDownState(any());
        doNothing().when(logic).clearDarkness(any());
        doNothing().when(logic).clearDownState(any());
        doNothing().when(logic).restoreCombat(any());
        logic.progression = mock(ZMapProgression.class);
        logic.onDeath(human, null);
        logic.botTick(bot);
        verify(bot).setSneaking(true);
        assertSame(down, logic.botGuardPoint());
    }
    @Test void overrunSummaryIsWellPlayedNotGameOver() {
        var game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.arena()).thenReturn(ReadyPlayerZBotTest.arena());
        Player p = mock(Player.class);
        when(p.getName()).thenReturn("Sam"); when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(game.players()).thenReturn(List.of(p)); when(game.everyone()).thenReturn(List.of(p));
        when(game.score(p)).thenReturn(120);
        new ReadyPlayerZGame(game).showSummary(List.of());
        var title = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.title.Title.class);
        verify(p).showTitle(title.capture());
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(title.getValue().title());
        assertTrue(text.contains("Well played"));
        assertFalse(text.toLowerCase().contains("game over"));
    }
}
