package uk.co.playerready.pulsegames.core.party;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NetworkPartyImportTest {
    JavaPlugin plugin; PartyManager parties; Player leader,member;
    @BeforeEach void setup() {
        plugin=mock(JavaPlugin.class,RETURNS_DEEP_STUBS);when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        parties=new PartyManager(plugin);leader=player();member=player();
    }
    Player player() {var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.isOnline()).thenReturn(true);when(plugin.getServer().getPlayer(p.getUniqueId())).thenReturn(p);return p;}
    List<UUID> ids(){return List.of(leader.getUniqueId(),member.getUniqueId());}
    @Test void importsWholeGroupOnlyWhenEveryonePresent() {
        when(member.isOnline()).thenReturn(false);assertFalse(parties.importNetworkParty(leader,ids()));assertNull(parties.partyOf(leader));
        when(member.isOnline()).thenReturn(true);assertTrue(parties.importNetworkParty(leader,ids()));assertSame(parties.partyOf(leader),parties.partyOf(member));
    }
    @Test void refusesToReplaceUnrelatedParty() {
        Party original=parties.createOrGet(member);assertFalse(parties.importNetworkParty(leader,ids()));assertSame(original,parties.partyOf(member));assertNull(parties.partyOf(leader));
    }
    @Test void repeatedHeartbeatPreservesPartyObjectAndQueuedTicketIdentity() {
        parties.networkState(leader,leader.getUniqueId(),ids());Party imported=parties.partyOf(leader);
        parties.networkState(member,leader.getUniqueId(),ids());parties.networkState(leader,leader.getUniqueId(),ids());
        assertSame(imported,parties.partyOf(leader));
    }
    @Test void disbandSnapshotClearsImportedPartyNotUnrelatedLocalParty() {
        parties.networkState(leader,leader.getUniqueId(),ids());parties.networkState(leader,null,List.of());assertNull(parties.partyOf(leader));
        Party local=parties.createOrGet(leader);parties.networkState(leader,null,List.of());assertSame(local,parties.partyOf(leader));
    }
    @Test void rejectsDuplicatesAndAbsentLeader() {
        assertFalse(parties.importNetworkParty(leader,List.of(member.getUniqueId())));
        assertFalse(parties.importNetworkParty(leader,List.of(leader.getUniqueId(),leader.getUniqueId())));
    }
    @Test void backendTransferInvalidatesImportedCacheWithoutMutatingAuthoritativeRoster() {
        assertTrue(parties.importNetworkParty(leader,ids()));
        parties.handleQuit(member);
        assertNull(parties.partyOf(leader));
        assertTrue(parties.localQueueReady(leader));
        assertEquals(new HashSet<>(ids()),parties.partyOf(leader).members());
        assertTrue(parties.partyOf(leader).isLeader(leader));
    }
}
