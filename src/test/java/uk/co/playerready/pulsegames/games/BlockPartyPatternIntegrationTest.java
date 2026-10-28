package uk.co.playerready.pulsegames.games;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.games.blockparty.BlockPartyGame;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockPartyPatternIntegrationTest {
    private void field(Object target, String name, Object value) throws Exception {
        var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    private Player player(World world,double x) {
        var player=mock(Player.class,RETURNS_DEEP_STUBS);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(world,x,65,.5));return player;
    }
    @Test void fallingContestantCannotAbortRemainingColourCall() throws Exception {
        var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);var world=mock(World.class);
        when(game.world()).thenReturn(world);when(game.state()).thenReturn(GameState.RUNNING);
        when(game.mode().id()).thenReturn("hardcore");
        var tile=mock(Block.class);when(tile.getType()).thenReturn(Material.BLUE_WOOL);
        var air=mock(Block.class);when(air.isPassable()).thenReturn(true);
        when(world.getBlockAt(0,64,0)).thenReturn(tile);
        when(world.getBlockAt(0,65,0)).thenReturn(air);when(world.getBlockAt(0,66,0)).thenReturn(air);
        var falling=player(world,-1);var survivor=player(world,.5);
        var alive=new ArrayList<>(List.of(falling,survivor));when(game.alivePlayers()).thenAnswer(i->List.copyOf(alive));
        doAnswer(i->{alive.remove(i.getArgument(0));return null;}).when(game).eliminate(any(Player.class));
        var logic=new BlockPartyGame(game);field(logic,"floor",new Cuboid(0,64,0,0,64,0));field(logic,"round",6);field(logic,"hintDelivery",(java.util.function.Consumer<Player>)p->{});
        var call=BlockPartyGame.class.getDeclaredMethod("pickTarget");call.setAccessible(true);
        assertEquals(true,call.invoke(logic));verify(game).eliminate(falling);
        verify(game,never()).end(anyList(),eq("invalid-floor"));verify(game,never()).eliminate(survivor);
    }
    @Test void botFindsSafeCalledPatchBeyondTwelveBlocks() throws Exception {
        var game=mock(GameInstance.class);var world=mock(World.class);when(game.world()).thenReturn(world);
        var other=mock(Block.class);when(other.getType()).thenReturn(Material.RED_WOOL);
        var target=mock(Block.class);when(target.getType()).thenReturn(Material.BLUE_WOOL);
        var air=mock(Block.class);when(air.isPassable()).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->{
            int x=i.getArgument(0),y=i.getArgument(1);return y==64?(x==16?target:other):air;
        });
        var logic=new BlockPartyGame(game);field(logic,"floor",new Cuboid(0,64,0,16,64,0));field(logic,"target",Material.BLUE_WOOL);
        var phase=BlockPartyGame.class.getDeclaredField("phase");phase.setAccessible(true);
        for(Object value:phase.getType().getEnumConstants())if(value.toString().equals("STAND"))phase.set(logic,value);
        var destination=logic.botDestination(player(world,.5));assertNotNull(destination);assertEquals(16,destination.getBlockX());
    }
}
