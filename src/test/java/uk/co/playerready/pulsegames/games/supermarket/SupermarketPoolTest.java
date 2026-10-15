package uk.co.playerready.pulsegames.games.supermarket;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupermarketPoolTest {
    private MockedStatic<Material> materials;
    @BeforeEach void itemRegistryBoundary() {
        // Paper owns item/block registries; exercise list logic with material capabilities supplied at that boundary.
        var apple=spy(Material.APPLE);doReturn(true).when(apple).isItem();doReturn(false).when(apple).isAir();
        var bread=spy(Material.BREAD);doReturn(true).when(bread).isItem();doReturn(false).when(bread).isAir();
        var air=spy(Material.AIR);doReturn(false).when(air).isItem();
        var water=spy(Material.WATER);doReturn(false).when(water).isItem();
        materials=mockStatic(Material.class,CALLS_REAL_METHODS);
        for(String name:List.of("APPLE","apple","minecraft:apple"))materials.when(()->Material.matchMaterial(name)).thenReturn(apple);
        materials.when(()->Material.matchMaterial("BREAD")).thenReturn(bread);
        materials.when(()->Material.matchMaterial("AIR")).thenReturn(air);
        materials.when(()->Material.matchMaterial("CAVE_AIR")).thenReturn(air);
        materials.when(()->Material.matchMaterial("WATER")).thenReturn(water);
    }
    @AfterEach void closeMaterialBoundary() { materials.close(); }

    private SupermarketSweepGame game(List<String> pool,int size) {
        var settings=new MemoryConfiguration();settings.set("item-pool",pool);settings.set("list-size",size);
        var game=mock(GameInstance.class);when(game.mode()).thenReturn(GameMode.ffa("classic","Classic",2,12));
        when(game.arena()).thenReturn(new Arena("shop","supermarket","Shop","world",List.of(),2,12,"0,64,0","0,64,0",List.of("0,64,0"),Map.of(),settings));
        when(game.alivePlayers()).thenReturn(List.of());return new SupermarketSweepGame(game);
    }
    @Test void repeatedConfiguredMaterialsCannotCreateAnUnfinishableList() {
        var logic=game(List.of("APPLE","apple","minecraft:apple","BREAD"),12);
        assertNull(logic.startProblem(List.of()));logic.onStart();var lines=logic.sidebar(mock(Player.class));
        assertEquals(2,lines.size());assertTrue(lines.stream().anyMatch(s->s.contains("apple")));assertTrue(lines.stream().anyMatch(s->s.contains("bread")));
    }
    @Test void nonPositiveListSizesStillProduceOneRequiredItem() {
        for(int size:new int[]{0,-1,Integer.MIN_VALUE}){var logic=game(List.of("APPLE","BREAD"),size);logic.onStart();assertEquals(1,logic.sidebar(mock(Player.class)).size());}
    }
    @Test void invalidConfiguredPoolFailsBeforeStartRatherThanAddingUnstockedDefaults() {
        var logic=game(List.of("AIR","CAVE_AIR","WATER","not_a_material"),6);
        assertNotNull(logic.startProblem(List.of()));assertThrows(IllegalStateException.class,logic::onStart);
    }
    @Test void absentPoolKeepsDefaultItemsAndOversizedListStaysBounded() {
        var logic=game(List.of(),Integer.MAX_VALUE);assertNull(logic.startProblem(List.of()));logic.onStart();assertEquals(15,logic.sidebar(mock(Player.class)).size());
    }
}
