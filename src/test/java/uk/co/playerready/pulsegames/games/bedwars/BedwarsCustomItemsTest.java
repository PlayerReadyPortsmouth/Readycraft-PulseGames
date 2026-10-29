package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsCustomItems.Custom;
import uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BedwarsCustomItemsTest {
    private static BedwarsCustomItems items(GameInstance game) {
        return new BedwarsCustomItems(game, ShopCatalogue::loadBundled, b -> false, b -> { }, b -> { }, t -> null);
    }

    @Test void everyBehaviourIsSoldAndEveryCustomKeyHasAHandler() {
        ShopCatalogue shop = ShopCatalogue.loadBundled();
        Set<String> used = shop.items().stream().map(ShopCatalogue.Item::custom).filter(c -> c != null).collect(Collectors.toSet());
        assertEquals(BedwarsCustomItems.IDS, used, "each behaviour has a shop entry and each shop key a behaviour");
        for (String id : used) assertNotNull(Custom.byId(id), id);
        assertEquals(Custom.values().length, BedwarsCustomItems.IDS.size());
    }

    @Test void cleanupRemovesSummonsAndForgetsTheirState() {
        GameInstance game = mock(GameInstance.class);
        BedwarsCustomItems items = items(game);
        ArmorStand decoy = mock(ArmorStand.class);
        World world = mock(World.class);
        when(decoy.isValid()).thenReturn(true);
        when(decoy.getWorld()).thenReturn(world);
        when(decoy.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        items.adopt(decoy, Custom.DECOY_DUMMY, 0, 100);
        assertEquals(1, items.liveSummons());
        items.clear();
        verify(decoy).remove();
        assertEquals(0, items.liveSummons());
        assertEquals(0, items.tempBlockCount());
    }

    @Test void summonsExpireOnTheirOwnAtTheirTime() {
        GameInstance game = mock(GameInstance.class);
        BedwarsCustomItems items = items(game);
        ArmorStand decoy = mock(ArmorStand.class);
        World world = mock(World.class);
        when(decoy.isValid()).thenReturn(true);
        when(decoy.getWorld()).thenReturn(world);
        when(decoy.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        items.adopt(decoy, Custom.DECOY_DUMMY, 0, 30);
        items.tick(29);
        verify(decoy, never()).remove();
        items.tick(30);
        verify(decoy).remove();
        assertEquals(0, items.liveSummons());
    }

    @Test void milkImmunityEndsWithTheMatch() {
        GameInstance game = mock(GameInstance.class);
        BedwarsCustomItems items = items(game);
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        assertFalse(items.trapImmune(p));
        items.clear();
        assertFalse(items.trapImmune(p));
    }
}
