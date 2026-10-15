package uk.co.playerready.pulsegames.games.bedwars;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import java.util.*;
import static org.mockito.Mockito.*;

class BedwarsGeneratorCapTest {
    @Test void stackedIronAtCapPausesOnlyIronAndResumesAfterPickup() {
        var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);World world=mock(World.class);when(game.world()).thenReturn(world);
        GameTeam team=mock(GameTeam.class);when(game.aliveTeams()).thenReturn(List.of(team));Location loc=new Location(world,0,64,0);when(game.arena().settingLocation("generator1",world)).thenReturn(loc);
        Item pile=mock(Item.class),spawned=mock(Item.class);when(pile.isValid()).thenReturn(true);ItemStack stack=mock(ItemStack.class);when(stack.getType()).thenReturn(Material.IRON_INGOT);when(stack.getAmount()).thenReturn(64);when(pile.getItemStack()).thenReturn(stack);
        when(world.getNearbyEntities(loc,2.5,2,2.5)).thenReturn(List.of(pile));when(world.dropItem(any(Location.class),any(ItemStack.class))).thenReturn(spawned);
        try(var items=mockConstruction(ItemStack.class,(item,context)->{
            when(item.getType()).thenReturn((Material)context.arguments().getFirst());when(item.getAmount()).thenReturn(1);
        })) {
        var logic=new BedwarsGame(game);logic.onSecond(8);
        verify(world,never()).dropItem(any(Location.class),argThat(i->i.getType()==Material.IRON_INGOT));verify(world).dropItem(any(Location.class),argThat(i->i.getType()==Material.GOLD_INGOT));
        when(stack.getAmount()).thenReturn(63);logic.onSecond(10);verify(world).dropItem(any(Location.class),argThat(i->i.getType()==Material.IRON_INGOT));verify(pile,never()).remove();
        }
    }
    @Test void deadTeamsDoNotGenerateResources() {
        var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);when(game.aliveTeams()).thenReturn(List.of());new BedwarsGame(game).onSecond(8);verify(game.world(),never()).dropItem(any(Location.class),any(ItemStack.class));
    }
}
