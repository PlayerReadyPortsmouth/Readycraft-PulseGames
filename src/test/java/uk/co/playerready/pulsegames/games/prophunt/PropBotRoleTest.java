package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PropBotRoleTest {
    @SuppressWarnings("unchecked") private static <T> T field(Object owner, String name) throws Exception {
        var field = PropHuntGame.class.getDeclaredField(name); field.setAccessible(true); return (T) field.get(owner);
    }
    @Test void infectionSkillDoesNotRewardHidersForBeingConvertedIntoWinningSeekers() throws Exception {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.mode().id()).thenReturn("infection");
        var logic = new PropHuntGame(game);
        Player caught = mock(Player.class), originalSeeker = mock(Player.class);
        UUID caughtId = UUID.randomUUID(), seekerId = UUID.randomUUID();
        when(caught.getUniqueId()).thenReturn(caughtId); when(originalSeeker.getUniqueId()).thenReturn(seekerId);
        PropBotRoleTest.<Set<UUID>>field(logic, "originalHiders").add(caughtId);
        PropBotRoleTest.<Set<UUID>>field(logic, "seekers").addAll(Set.of(caughtId, seekerId));
        assertFalse(logic.botSkillWin(caught, List.of(caught, originalSeeker)));
        assertTrue(logic.botSkillWin(originalSeeker, List.of(caught, originalSeeker)));
    }
    @Test void survivingOriginalHiderWinsSkillOnlyWhenIncludedAmongWinners() throws Exception {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.mode().id()).thenReturn("infection");
        var logic = new PropHuntGame(game); Player survivor = mock(Player.class);
        UUID id = UUID.randomUUID(); when(survivor.getUniqueId()).thenReturn(id);
        PropBotRoleTest.<Set<UUID>>field(logic, "originalHiders").add(id);
        PropBotRoleTest.<Set<UUID>>field(logic, "hiders").add(id);
        assertTrue(logic.botSkillWin(survivor, List.of(survivor)));
        assertFalse(logic.botSkillWin(survivor, List.of()));
    }
    @Test void classicSkillKeepsNormalWinnerOutcome() {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.mode().id()).thenReturn("classic");
        Player winner = mock(Player.class); var logic = new PropHuntGame(game);
        assertTrue(logic.botSkillWin(winner, List.of(winner)));
        assertFalse(logic.botSkillWin(winner, List.of()));
    }
    @Test void seekerCannotNavigateOrAttackBeforeRelease() throws Exception {
        GameInstance game = mock(GameInstance.class); Player bot = mock(Player.class);
        UUID id = UUID.randomUUID(); when(bot.getUniqueId()).thenReturn(id);
        when(game.state()).thenReturn(GameState.RUNNING); when(game.isActive(bot)).thenReturn(true);
        var logic = new PropHuntGame(game);
        PropBotRoleTest.<Set<UUID>>field(logic, "seekers").add(id);
        PropBotRoleTest.<Map<UUID,Location>>field(logic, "botGoals").put(id, new Location(null, 5, 64, 8));
        var phase = PropHuntGame.class.getDeclaredField("hidePhase"); phase.setAccessible(true); phase.setInt(logic, 20);
        assertNull(logic.botDestination(bot));
        logic.botTick(bot, 100, BotDifficulty.HARD);
        assertTrue(PropBotRoleTest.<Map<UUID,Location>>field(logic, "botGoals").isEmpty());
        verify(bot, never()).attack(any()); verify(bot, never()).swingMainHand();
    }
    @Test void inactiveBotDoesNotObserveOrAttack() {
        GameInstance game = mock(GameInstance.class); Player bot = mock(Player.class);
        when(game.state()).thenReturn(GameState.RUNNING);
        new PropHuntGame(game).botTick(bot, 100, BotDifficulty.HARD);
        verifyNoInteractions(bot);
    }
}
