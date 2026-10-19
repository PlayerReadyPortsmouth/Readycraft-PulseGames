package uk.co.playerready.pulsegames.games.readyplayerz;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyAttackSpeedTest {
    final Map<Object, Object> stored = new HashMap<>();
    final double[] base = {4.0};
    final Player player = mock(Player.class);
    AttributeInstance attribute;
    boolean heal(Player p) { return LegacyAttackSpeed.heal(p, x -> x == player ? attribute : null); }

    LegacyAttackSpeedTest() {
        var pdc = mock(PersistentDataContainer.class);
        doAnswer(i -> { stored.put(i.getArgument(0), i.getArgument(2)); return null; }).when(pdc).set(any(), any(), any());
        doAnswer(i -> { stored.remove(i.getArgument(0)); return null; }).when(pdc).remove(any());
        when(pdc.has(any(), eq(PersistentDataType.DOUBLE))).thenAnswer(i -> stored.containsKey(i.getArgument(0)));
        when(pdc.get(any(), eq(PersistentDataType.DOUBLE))).thenAnswer(i -> stored.get(i.getArgument(0)));
        when(player.getPersistentDataContainer()).thenReturn(pdc);
        attribute = mock(AttributeInstance.class);
        when(attribute.getBaseValue()).thenAnswer(i -> base[0]);
        doAnswer(i -> { base[0] = i.getArgument(0); return null; }).when(attribute).setBaseValue(anyDouble());
    }

    @Test void leftoverLegacyValueAfterCrashIsRestoredToTheOriginal() {
        LegacyAttackSpeed.mark(player, 3.5); base[0] = 1024;
        assertTrue(heal(player));
        assertEquals(3.5, base[0]); assertTrue(stored.isEmpty());
        assertFalse(heal(player)); assertEquals(3.5, base[0]);
    }
    @Test void badStoredOriginalFallsBackToVanillaDefault() {
        LegacyAttackSpeed.mark(player, 1024); base[0] = 1024;
        assertTrue(heal(player)); assertEquals(4.0, base[0]);
    }
    @Test void unmarkedPlayersAndOtherPluginsValuesAreLeftAlone() {
        base[0] = 1024; assertFalse(heal(player)); assertEquals(1024, base[0]);
        LegacyAttackSpeed.mark(player, 4.0); base[0] = 7.0;
        assertFalse(heal(player)); assertEquals(7.0, base[0]); assertTrue(stored.isEmpty());
    }
    @Test void clearRemovesTheMarker() {
        LegacyAttackSpeed.mark(player, 4.0); LegacyAttackSpeed.clear(player);
        base[0] = 1024; assertFalse(heal(player)); assertEquals(1024, base[0]);
    }
    @Test void mockPlayersWithoutDataContainerAreSafe() {
        Player bare = mock(Player.class);
        assertDoesNotThrow(() -> { LegacyAttackSpeed.mark(bare, 4.0); LegacyAttackSpeed.clear(bare); heal(bare); });
    }
}
