package uk.co.playerready.pulsegames.games.blockparty;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockPartyBotTest {
    void set(Object o, String name, Object value) throws Exception {
        var field=o.getClass().getDeclaredField(name);field.setAccessible(true);field.set(o,value);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    void phase(BlockPartyGame game, String value) throws Exception {
        var field=BlockPartyGame.class.getDeclaredField("phase");field.setAccessible(true);
        field.set(game,Enum.valueOf((Class)field.getType(),value));
    }
    @Test void followsPublicCallToActualUnobstructedColour() throws Exception {
        var game=mock(GameInstance.class);var world=mock(World.class);var bot=mock(Player.class);
        when(game.world()).thenReturn(world);when(bot.getLocation()).thenReturn(new Location(world,0.5,81,0.5));
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(c->{
            int x=c.getArgument(0),y=c.getArgument(1),z=c.getArgument(2);
            Block block=mock(Block.class);when(block.isPassable()).thenReturn(y>80);
            when(block.getType()).thenReturn(y==80 && x==3 && z==0?Material.RED_WOOL:Material.BLUE_WOOL);
            return block;
        });
        var logic=new BlockPartyGame(game);set(logic,"floor",new Cuboid(0,80,0,4,80,4));set(logic,"target",Material.RED_WOOL);
        assertNull(logic.botDestination(bot),"No advance knowledge during shuffle");
        phase(logic,"STAND");assertEquals(new Location(world,3.5,81,0.5),logic.botDestination(bot));
        phase(logic,"CLEARED");assertEquals(new Location(world,3.5,81,0.5),logic.botDestination(bot));
    }
    @Test void refusesBlockedOrMissingSafeTiles() throws Exception {
        var game=mock(GameInstance.class);var world=mock(World.class);var bot=mock(Player.class);
        when(game.world()).thenReturn(world);when(bot.getLocation()).thenReturn(new Location(world,0.5,81,0.5));
        Block block=mock(Block.class);when(block.getType()).thenReturn(Material.RED_WOOL);
        when(block.isPassable()).thenReturn(false);when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);
        var logic=new BlockPartyGame(game);set(logic,"floor",new Cuboid(0,80,0,4,80,4));set(logic,"target",Material.RED_WOOL);phase(logic,"STAND");
        assertNull(logic.botDestination(bot));
    }
}
