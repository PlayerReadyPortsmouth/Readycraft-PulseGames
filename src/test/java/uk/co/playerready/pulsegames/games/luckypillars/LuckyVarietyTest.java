package uk.co.playerready.pulsegames.games.luckypillars;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class LuckyVarietyTest {
    @Test void poolIsWellFormedAndSafe() {
        Set<String> ids = new HashSet<>();
        assertTrue(LuckyDrops.ALL.size() >= 55);
        for (var d : LuckyDrops.ALL) {
            assertTrue(d.weight() > 0, d.id());
            assertTrue(ids.add(d.id()), "duplicate " + d.id());
            assertFalse(d.material().name().startsWith("LEGACY_") || d.material().name().endsWith("AIR"), d.id());
            assertFalse(LuckyDrops.banned(d.material()), d.id());
        }
        assertTrue(LuckyDrops.banned(Material.WITHER_SPAWN_EGG));
        assertTrue(LuckyDrops.banned(Material.WARDEN_SPAWN_EGG));
    }

    @Test void seededSelectionIsDeterministicAndHonoursExclusions() {
        var a = LuckyDrops.pick(LuckyDrops.ALL, 0.5, false, Set.of(), new Random(42));
        var b = LuckyDrops.pick(LuckyDrops.ALL, 0.5, false, Set.of(), new Random(42));
        assertEquals(a.id(), b.id());
        Set<String> all = new HashSet<>();
        LuckyDrops.ALL.forEach(d -> all.add(d.id()));
        assertNull(LuckyDrops.pick(LuckyDrops.ALL, 0, false, all, new Random(1)));
        all.remove("cake");
        assertEquals("cake", LuckyDrops.pick(LuckyDrops.ALL, 0, false, all, new Random(1)).id());
    }

    @Test void raresGrowOverTimeAndUnarmedPlayersGetMoreWeapons() {
        var mace = LuckyDrops.ALL.stream().filter(d -> d.id().equals("mace")).findFirst().orElseThrow();
        var planks = LuckyDrops.ALL.stream().filter(d -> d.id().equals("planks")).findFirst().orElseThrow();
        var sword = LuckyDrops.ALL.stream().filter(d -> d.id().equals("iron_sword")).findFirst().orElseThrow();
        assertTrue(LuckyDrops.weightOf(mace, 1, false) > LuckyDrops.weightOf(mace, 0, false) * 10);
        assertTrue(LuckyDrops.weightOf(planks, 1, false) < LuckyDrops.weightOf(planks, 0, false));
        assertEquals(LuckyDrops.weightOf(sword, 0.5, false) * LuckyDrops.WEAPON_CATCH_UP, LuckyDrops.weightOf(sword, 0.5, true), 1e-9);
        assertEquals(LuckyDrops.weightOf(planks, 0.5, false), LuckyDrops.weightOf(planks, 0.5, true), 1e-9);
        assertEquals(0, LuckyDrops.progress(-5));
        assertEquals(1, LuckyDrops.progress(9999));
    }

    @Test void armedDetectionUsesWeaponMaterials() {
        Player p = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        when(p.getInventory()).thenReturn(inv);
        ItemStack planks = stack(Material.OAK_PLANKS), axe = stack(Material.STONE_AXE);
        when(inv.getContents()).thenReturn(new ItemStack[]{null, planks});
        assertFalse(LuckyPillarsGame.armed(p));
        when(inv.getContents()).thenReturn(new ItemStack[]{axe});
        assertTrue(LuckyPillarsGame.armed(p));
    }

    private static ItemStack stack(Material m) {
        ItemStack i = mock(ItemStack.class);
        when(i.getType()).thenReturn(m);
        return i;
    }

    @Test void dropIntervalDefaultsToFiveAndHasFloor() {
        assertEquals(5, LuckyPillarsGame.DEFAULT_DROP_INTERVAL);
        assertEquals(3, LuckyPillarsGame.dropInterval(1));
        assertEquals(8, LuckyPillarsGame.dropInterval(8));
    }

    @Test void swapPicksTwoDistinctPlayersAndNoOpsBelowTwo() {
        Player a = mock(Player.class), b = mock(Player.class), c = mock(Player.class);
        for (int seed = 0; seed < 200; seed++) {
            Player[] pair = LuckyChaos.pickPair(List.of(a, b, c), new Random(seed));
            assertNotSame(pair[0], pair[1]);
        }
        assertNull(LuckyChaos.pickPair(List.of(a), new Random(1)));
        assertNull(LuckyChaos.pickPair(List.of(), new Random(1)));
        for (int seed = 0; seed < 50; seed++) assertNotEquals(LuckyChaos.SWAP, LuckyChaos.pickEvent(1, new Random(seed)));
    }

    private static final class ChaosFixture {
        final GameInstance game = mock(GameInstance.class);
        final BukkitTask task = mock(BukkitTask.class);
        final Attribute scaleAttr = uk.co.playerready.pulsegames.AttributeStub.named("scale");
        final Player p = mock(Player.class);
        final AttributeInstance scale = mock(AttributeInstance.class);
        final LuckyChaos.Ctx ctx;
        ChaosFixture() {
            when(game.runRepeating(anyLong(), anyLong(), any())).thenReturn(task);
            var plugin = mock(uk.co.playerready.pulsegames.PulseGamesPlugin.class);
            when(plugin.getName()).thenReturn("PulseGames");
            when(game.plugin()).thenReturn(plugin);
            when(p.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
            when(p.getAttribute(any())).thenReturn(scale);
            when(scale.getBaseValue()).thenReturn(1.0);
            ctx = new LuckyChaos.Ctx(game, new Random(1), () -> null, (x, y) -> {}, LuckyChaos.TINY, new java.util.ArrayList<>());
        }
    }

    @Test void chaosUsesTransientModifierNeverTheBaseValue() {
        var f = new ChaosFixture();
        f.ctx.setAttribute(f.p, f.scaleAttr, 0.4);
        f.ctx.repeat(10, () -> {});
        var modifier = org.mockito.ArgumentCaptor.forClass(org.bukkit.attribute.AttributeModifier.class);
        verify(f.scale).addTransientModifier(modifier.capture());
        assertEquals(-0.6, modifier.getValue().getAmount(), 1e-9);
        verify(f.scale, never()).setBaseValue(anyDouble());
        verify(f.scale, never()).addModifier(any());
        f.ctx.restoreAll();
        f.ctx.restoreAll();
        verify(f.scale, atLeastOnce()).removeModifier(modifier.getValue().getKey());
        verify(f.task).cancel();
    }

    @Test void quittingPlayerIsRestoredWithoutWaitingForTheEventToEnd() {
        var f = new ChaosFixture();
        f.ctx.setAttribute(f.p, f.scaleAttr, 2.0);
        clearInvocations(f.scale);
        f.ctx.restoreFor(f.p);
        verify(f.scale).removeModifier(any(org.bukkit.NamespacedKey.class));
        clearInvocations(f.scale);
        f.ctx.restoreAll();
        verify(f.scale, never()).removeModifier(any(org.bukkit.NamespacedKey.class));
    }

    @Test void modifierAmountReachesTheTarget() {
        assertEquals(0.4, 1.0 + LuckyChaos.modifierAmount(1.0, 0.4), 1e-9);
        assertEquals(0.02, 0.08 + LuckyChaos.modifierAmount(0.08, 0.02), 1e-9);
    }
}
