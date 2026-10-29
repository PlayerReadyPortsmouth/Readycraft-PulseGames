package uk.co.playerready.pulsegames.core.settings;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerSettingsTest {
    private Player player() {
        Set<NamespacedKey> stored = new HashSet<>();
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(any(NamespacedKey.class), any(PersistentDataType.class))).thenAnswer(i -> stored.contains(i.<NamespacedKey>getArgument(0)));
        doAnswer(i -> stored.add(i.<NamespacedKey>getArgument(0))).when(pdc).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
        doAnswer(i -> stored.remove(i.<NamespacedKey>getArgument(0))).when(pdc).remove(any(NamespacedKey.class));
        Player p = mock(Player.class);
        when(p.getPersistentDataContainer()).thenReturn(pdc);
        return p;
    }

    @Test void everyOptionDefaultsOff() {
        Player p = player();
        for (Comfort option : Comfort.values()) assertFalse(PlayerSettings.enabled(p, option), option.name());
    }

    @Test void setAndClearAreIndependentPerOption() {
        Player p = player();
        PlayerSettings.set(p, Comfort.COLOUR_PATTERNS, true);
        assertTrue(PlayerSettings.enabled(p, Comfort.COLOUR_PATTERNS));
        assertFalse(PlayerSettings.enabled(p, Comfort.REDUCE_FLASHES));
        PlayerSettings.set(p, Comfort.COLOUR_PATTERNS, false);
        assertFalse(PlayerSettings.enabled(p, Comfort.COLOUR_PATTERNS));
    }

    @Test void oneOptedInPlayerEnablesItForTheRound() {
        Player a = player(), b = player();
        assertFalse(PlayerSettings.anyEnabled(List.of(a, b), Comfort.COLOUR_PATTERNS));
        PlayerSettings.set(b, Comfort.COLOUR_PATTERNS, true);
        assertTrue(PlayerSettings.anyEnabled(List.of(a, b), Comfort.COLOUR_PATTERNS));
    }
}
