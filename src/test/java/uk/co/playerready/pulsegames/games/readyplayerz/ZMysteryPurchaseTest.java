package uk.co.playerready.pulsegames.games.readyplayerz;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import uk.co.playerready.pulsegames.core.game.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ZMysteryPurchaseTest {
    GameInstance game; ReadyPlayerZGame logic; Player p; World world; int[] score = {0};
    @BeforeEach void setup() {
        game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.arena()).thenReturn(ReadyPlayerZBotTest.arena());
        when(game.state()).thenReturn(GameState.RUNNING);
        world = game.world();
        p = mock(Player.class, RETURNS_DEEP_STUBS);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getWorld()).thenReturn(world);
        when(game.isActive(p)).thenReturn(true);
        when(game.score(p)).thenAnswer(i -> score[0]);
        doAnswer(i -> { score[0] += i.getArgument(1, Integer.class); return null; }).when(game).addScore(eq(p), anyInt());
        logic = new ReadyPlayerZGame(game);
    }
    @Test void boxChargesAndPriceRisesThenResetsNextRound() throws Exception {
        score[0] = 950;
        assertTrue(logic.mysteryBox(p));
        assertEquals(0, score[0]);
        score[0] = 1000;
        assertFalse(logic.mysteryBox(p), "second spin is still running and costs 1045");
        var m = ReadyPlayerZGame.class.getDeclaredField("spinning"); m.setAccessible(true);
        ((Set<?>) m.get(logic)).clear();
        assertFalse(logic.mysteryBox(p), "1000 < 1045");
        score[0] = 1045;
        assertTrue(logic.mysteryBox(p));
        var f = ReadyPlayerZGame.class.getDeclaredField("boxUses"); f.setAccessible(true);
        ((Map<?, ?>) f.get(logic)).clear(); // what startRound does
        ((Set<?>) m.get(logic)).clear();
        score[0] = 950;
        assertTrue(logic.mysteryBox(p));
    }
    @Test void packAPunchNeedsAWeaponAndFivethousandPoints() {
        var inv = p.getInventory();
        var gem = mock(ItemStack.class);
        when(gem.getType()).thenReturn(Material.EMERALD);
        when(inv.getItemInMainHand()).thenReturn(gem);
        score[0] = 6000;
        assertFalse(logic.packAPunch(p));
        assertEquals(6000, score[0]);
        var sword = mock(ItemStack.class);
        when(sword.getType()).thenReturn(Material.IRON_SWORD);
        when(inv.getItemInMainHand()).thenReturn(sword);
        score[0] = 4999;
        assertFalse(logic.packAPunch(p));
        verify(game, never()).addScore(eq(p), anyInt());
    }
    @Test void shopFallbackSlotsRouteToTheSameActions() {
        score[0] = 5000;
        var gem = mock(ItemStack.class);
        when(gem.getType()).thenReturn(Material.EMERALD);
        when(p.getInventory().getItemInMainHand()).thenReturn(gem);
        assertFalse(logic.purchase(p, ReadyPlayerZGame.PAP_SLOT));
        score[0] = 950;
        assertTrue(logic.purchase(p, ReadyPlayerZGame.BOX_SLOT));
    }
}
