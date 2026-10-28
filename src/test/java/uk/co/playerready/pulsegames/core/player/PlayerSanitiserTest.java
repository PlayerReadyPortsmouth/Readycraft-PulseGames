package uk.co.playerready.pulsegames.core.player;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class PlayerSanitiserTest {
    @Test void loginClearsGhostFlagsAttributesEffectsAndKitItems() {
        Player p = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(p.getInventory()).thenReturn(inventory);
        Attribute scale = uk.co.playerready.pulsegames.AttributeStub.named("scale");
        AttributeInstance instance = mock(AttributeInstance.class);
        when(p.getAttribute(scale)).thenReturn(instance);
        AttributeModifier ours = new AttributeModifier(new NamespacedKey("pulsegames", "prophunt_body"), -0.5,
                AttributeModifier.Operation.ADD_NUMBER);
        AttributeModifier theirs = new AttributeModifier(new NamespacedKey("other", "x"), 1,
                AttributeModifier.Operation.ADD_NUMBER);
        when(instance.getModifiers()).thenReturn(List.of(ours, theirs));
        when(p.getActivePotionEffects()).thenReturn(List.of());

        PlayerSanitiser.apply(p, true, Map.of(scale, 1.0));

        verify(p).setInvisible(false);
        verify(p).setInvulnerable(false);
        verify(p).setCollidable(true);
        verify(p).setAllowFlight(false);
        verify(p).setGameMode(GameMode.ADVENTURE);
        verify(p).getActivePotionEffects();
        verify(instance).setBaseValue(1.0);
        verify(instance).removeModifier(ours);
        verify(instance, never()).removeModifier(theirs);
        verify(inventory).clear();
    }

    @Test void worldChangeKeepsTheInventoryAGameJustRestored() {
        Player p = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(p.getInventory()).thenReturn(inventory);
        PlayerSanitiser.apply(p, false, Map.of());
        verify(p).setInvulnerable(false);
        verify(inventory, never()).clear();
    }
}
