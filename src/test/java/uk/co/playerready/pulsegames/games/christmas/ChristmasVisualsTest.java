package uk.co.playerready.pulsegames.games.christmas;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.PlatformDetector;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChristmasVisualsTest {
    @Test @SuppressWarnings("unchecked")
    void lateBedrockSpectatorDowngradesExistingDisplaysAndSoundsForTheRestOfTheRound() throws Exception {
        GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);
        Player java=mock(Player.class), bedrock=mock(Player.class);
        var viewers=new ArrayList<>(List.of(java)); when(game.everyone()).thenAnswer(i -> List.copyOf(viewers));
        when(game.plugin().seasons().customVisualsReady(java)).thenReturn(true);
        World world=mock(World.class); Location at=new Location(world,0,65,0); Sound fallback=null; // Avoid server-backed Sound registry initialisation in a plain JVM.
        try(var platform=mockStatic(PlatformDetector.class)) {
            platform.when(() -> PlatformDetector.isBedrock(bedrock)).thenReturn(true);
            ChristmasVisuals visuals=new ChristmasVisuals(game); assertTrue(visuals.custom());
            ItemDisplay display=mock(ItemDisplay.class); ItemStack item=mock(ItemStack.class); ItemMeta meta=mock(ItemMeta.class);
            when(display.getItemStack()).thenReturn(item);
            doAnswer(i -> { ((Consumer<ItemMeta>)i.getArgument(0)).accept(meta);return true; }).when(item).editMeta(any(Consumer.class));
            var owned=ChristmasVisuals.class.getDeclaredField("owned");owned.setAccessible(true);
            ((Set<ItemDisplay>)owned.get(visuals)).add(display);
            visuals.sound(at,"wrap",fallback,.5f,1);
            verify(world).playSound(at,"pulse:christmas.wrap",.5f,1);
            viewers.add(bedrock); visuals.sound(at,"wrap",fallback,.5f,1);
            assertFalse(visuals.custom()); verify(meta).setItemModel(null); verify(display).setItemStack(item);
            verify(world).playSound(at,fallback,.5f,1);
            viewers.remove(bedrock); visuals.refreshViewers(); assertFalse(visuals.custom());
        }
    }

    @Test void packLossUsesVanillaSoundEvenBeforeTheNextGameTick() {
        GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS); Player player=mock(Player.class);
        when(game.everyone()).thenReturn(List.of(player)); when(game.plugin().seasons().customVisualsReady(player)).thenReturn(true);
        World world=mock(World.class); Location at=new Location(world,0,65,0); Sound fallback=null; // Avoid server-backed Sound registry initialisation in a plain JVM.
        try(var platform=mockStatic(PlatformDetector.class)) {
            ChristmasVisuals visuals=new ChristmasVisuals(game); assertTrue(visuals.custom());
            when(game.plugin().seasons().customVisualsReady(player)).thenReturn(false);
            visuals.sound(at,"alarm",fallback,.5f,1);
            verify(world).playSound(at,fallback,.5f,1);
            verify(world,never()).playSound(eq(at),anyString(),anyFloat(),anyFloat());
        }
    }
}
