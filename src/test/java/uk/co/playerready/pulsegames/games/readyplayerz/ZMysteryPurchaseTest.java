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
    @Test void punchPlansMatchTheWeaponFamilies() {
        assertEquals(ZMysteryBox.PunchKind.SHARPNESS, ZMysteryBox.punch(Material.IRON_SWORD).kind());
        assertEquals(3, ZMysteryBox.punch(Material.IRON_SWORD).level());
        assertEquals(ZMysteryBox.PunchKind.POWER, ZMysteryBox.punch(Material.BOW).kind());
        assertEquals(3, ZMysteryBox.punch(Material.BOW).level());
        assertEquals(ZMysteryBox.PunchKind.QUICK_CHARGE, ZMysteryBox.punch(Material.CROSSBOW).kind());
        assertEquals(2, ZMysteryBox.punch(Material.CROSSBOW).level());
        assertEquals("<aqua><b>Punched golden sword", ZMysteryBox.punch(Material.GOLDEN_SWORD).displayName());
        assertNull(ZMysteryBox.punch(Material.EMERALD));
    }
    @Test void packAPunchSuccessChargesEnchantsRenamesAndAddsGlint() {
        // Enchantment cannot even be mocked (its registry fails to initialise), so the lookup is stubbed to null.
        logic = spy(logic);
        doReturn(null).when(logic).enchantmentFor(ZMysteryBox.PunchKind.SHARPNESS);
        var inv = p.getInventory();
        var sword = mock(ItemStack.class);
        when(sword.getType()).thenReturn(Material.DIAMOND_SWORD);
        var meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        when(sword.editMeta(any(java.util.function.Consumer.class))).thenAnswer(i -> {
            i.<java.util.function.Consumer<org.bukkit.inventory.meta.ItemMeta>>getArgument(0).accept(meta); return true; });
        when(inv.getItemInMainHand()).thenReturn(sword);
        score[0] = 5200;
        assertTrue(logic.packAPunch(p));
        assertEquals(200, score[0]);
        verify(sword).addUnsafeEnchantment(isNull(), eq(3));
        verify(meta).displayName(any(net.kyori.adventure.text.Component.class));
        verify(meta).lore(argThat(lore -> lore.size() == 1));
        verify(meta).setEnchantmentGlintOverride(true);
        verify(inv).setItemInMainHand(sword);
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
