package uk.co.playerready.pulsegames.core.player;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
class PlayerStateServiceTest {
    @Test void restoringWithoutSnapshotNeverClearsInventory() {
        Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        new PlayerStateService().restore(p);verify(p,never()).getInventory();verify(p,never()).setGameMode(any());
    }
}
