package uk.co.playerready.pulsegames.core.combat;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import java.util.Set;
import static org.mockito.Mockito.*;

class ClassicCombatServiceTest {
    PulseGamesPlugin plugin;
    ClassicCombatService.Modesets api;
    ClassicCombatService service;
    Player player;
    GameInstance instance;
    World world;
    @BeforeEach void setup() throws Exception {
        plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        api = mock(ClassicCombatService.Modesets.class);
        service = new ClassicCombatService(plugin, api);
        player = mock(Player.class); world = mock(World.class);
        instance = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(player.getWorld()).thenReturn(world); when(instance.world()).thenReturn(world);
        when(instance.isParticipant(player)).thenReturn(true); when(instance.logic().pvp()).thenReturn(true);
        when(plugin.instances().byPlayer(player)).thenReturn(instance);
        when(api.allowed(world)).thenReturn(Set.of("pulse-modern", "pulse-classic"));
    }
    @Test void modernPvpNeverGetsClassicMechanicsOrNpcMetadata() throws Exception {
        when(instance.mode()).thenReturn(uk.co.playerready.pulsegames.core.game.GameMode.ffa("modern-classic", "Modern", 2, 2));
        when(instance.isBot(player)).thenReturn(true);
        service.refresh(player); verify(api).select(player, "pulse-modern");
        verify(player,never()).setMetadata(anyString(),any());
    }
    @Test void enablesClassicForPvpParticipantsIncludingNpcPlayers() throws Exception {
        when(player.isOnline()).thenReturn(false);
        when(instance.isBot(player)).thenReturn(true);
        service.refresh(player); verify(api).select(player, "pulse-classic");
        verify(player).setMetadata(eq("pulsegames-classic-combat"), any());
    }
    @Test void leavesCoopOnModern() throws Exception {
        when(instance.logic().pvp()).thenReturn(false);
        service.refresh(player); verify(api).select(player, "pulse-modern");
    }
    @Test void leavesSpectatorsOnModern() throws Exception {
        when(instance.isSpectator(player)).thenReturn(true);
        service.refresh(player); verify(api).select(player, "pulse-modern");
    }
    @Test void restoresModernOutsideMatchWorld() throws Exception {
        when(player.getWorld()).thenReturn(mock(World.class));
        when(api.allowed(player.getWorld())).thenReturn(Set.of("pulse-modern"));
        service.refresh(player); verify(api).select(player, "pulse-modern");
    }
    @Test void restoresModernBeforeLeavingArena() throws Exception {
        service.leave(player); verify(api).select(player, "pulse-modern");
    }
    @Test void doesNotRewriteUnchangedMode() throws Exception {
        when(api.current(player)).thenReturn("pulse-classic");
        service.refresh(player); verify(api, never()).select(any(), any());
    }
    @Test void refusesUnavailablePreset() throws Exception {
        when(api.allowed(world)).thenReturn(Set.of("pulse-modern"));
        service.refresh(player); verify(api, never()).select(any(), any());
    }
}
