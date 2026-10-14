package uk.co.playerready.pulsegames.core.party;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartyManagerTest {
    private Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getName()).thenReturn("Player");
        return p;
    }
    @Test void staleInviteCannotAddPlayerToTwoParties() {
        JavaPlugin plugin = mock(JavaPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getConfig().getInt("party.max-size", 8)).thenReturn(8);
        when(plugin.getConfig().getInt("party.invite-expiry-seconds", 60)).thenReturn(60);
        PartyManager manager = new PartyManager(plugin);
        Player leader = player(), target = player();
        assertTrue(manager.invite(leader, target));
        Party own = manager.createOrGet(target);
        manager.accept(target);
        assertSame(own, manager.partyOf(target));
        assertFalse(manager.partyOf(leader).contains(target.getUniqueId()));
    }
}
