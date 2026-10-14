package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PropBotAbilitiesTest {
    @SuppressWarnings("unchecked") static <T> T field(Object o,String name) throws Exception {
        var f=PropHuntGame.class.getDeclaredField(name);f.setAccessible(true);return (T)f.get(o);
    }
    static void set(Object o,String name,Object value) throws Exception {
        var f=PropHuntGame.class.getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
    static class Fixture {
        final GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);
        final World world=mock(World.class);
        final Player bot=mock(Player.class);
        final UUID id=UUID.randomUUID();
        final PropHuntGame logic=new PropHuntGame(game);
        Fixture() throws Exception {
            when(bot.getUniqueId()).thenReturn(id);when(bot.getWorld()).thenReturn(world);
            when(bot.getLocation()).thenAnswer(i->new Location(world,.5,64,.5));
            when(bot.getEyeLocation()).thenAnswer(i->new Location(world,.5,65.6,.5));
            when(game.state()).thenReturn(GameState.RUNNING);when(game.isActive(bot)).thenReturn(true);
            when(game.isBot(bot)).thenReturn(true);set(logic,"powers",true);set(logic,"pulseCooldown",20);set(logic,"pulseRadius",12);
        }
    }
    @Test void pulseUsesCoarseSignalAndOrdinaryCooldownWithoutAddingTargetMemory() throws Exception {
        Fixture f=new Fixture();PropBotAbilitiesTest.<Set<UUID>>field(f.logic,"seekers").add(f.id);
        Player hider=mock(Player.class);UUID hiderId=UUID.randomUUID();
        when(f.game.player(hiderId)).thenReturn(hider);when(hider.getWorld()).thenReturn(f.world);
        when(hider.getLocation()).thenReturn(new Location(f.world,2,64,.5));
        PropBotAbilitiesTest.<Set<UUID>>field(f.logic,"hiders").add(hiderId);
        f.logic.botAbilities(f.bot,100);
        assertEquals(20,PropBotAbilitiesTest.<Map<String,Integer>>field(f.logic,"cooldowns").get(f.id+":Pulse"));
        assertTrue(PropBotAbilitiesTest.<Map<?,?>>field(f.logic,"botMemories").isEmpty());
        assertEquals(200L,PropBotAbilitiesTest.<Map<UUID,Long>>field(f.logic,"botLocalSearchUntil").get(f.id));
        f.logic.botAbilities(f.bot,140);
        verify(hider,times(1)).getLocation(); // Cooldown prevents repeated hidden-distance queries.
        verify(f.bot,never()).setRotation(anyFloat(),anyFloat());
    }
    @Test void pulseCanBeFooledByDecoysWithoutCreatingEnemyKnowledge() throws Exception {
        Fixture f=new Fixture();PropBotAbilitiesTest.<Set<UUID>>field(f.logic,"seekers").add(f.id);
        Class<?> type=Class.forName(PropHuntGame.class.getName()+"$Decoy");
        var constructor=type.getDeclaredConstructor(UUID.class,PropPalette.Option.class,Location.class,AutoCloseable.class,int.class);
        constructor.setAccessible(true);
        Object decoy=constructor.newInstance(UUID.randomUUID(),new PropPalette.Option("Stone",Material.STONE,"ready:stone"),
                new Location(f.world,2,64,.5),mock(AutoCloseable.class),60);
        PropBotAbilitiesTest.<Map<UUID,Object>>field(f.logic,"decoys").put(UUID.randomUUID(),decoy);
        f.logic.botAbilities(f.bot,100);
        assertEquals(200L,PropBotAbilitiesTest.<Map<UUID,Long>>field(f.logic,"botLocalSearchUntil").get(f.id));
        assertTrue(PropBotAbilitiesTest.<Map<?,?>>field(f.logic,"botMemories").isEmpty());
        assertTrue(PropBotAbilitiesTest.<Map<?,?>>field(f.logic,"botGoals").isEmpty());
    }
    @Test void heldFrozenAndInactiveSeekersCannotUsePowers() throws Exception {
        Fixture f=new Fixture();PropBotAbilitiesTest.<Set<UUID>>field(f.logic,"seekers").add(f.id);
        set(f.logic,"hidePhase",10);f.logic.botAbilities(f.bot,100);
        set(f.logic,"hidePhase",0);when(f.game.frozen()).thenReturn(true);f.logic.botAbilities(f.bot,200);
        when(f.game.frozen()).thenReturn(false);when(f.game.isActive(f.bot)).thenReturn(false);f.logic.botAbilities(f.bot,300);
        assertTrue(PropBotAbilitiesTest.<Map<?,?>>field(f.logic,"cooldowns").isEmpty());
    }
    @Test void botDecoyUsesOwnedNpcApiAndSameLimitCooldownAndCleanup() throws Exception {
        Fixture f=new Fixture();PropBotAbilitiesTest.<Set<UUID>>field(f.logic,"hiders").add(f.id);
        set(f.logic,"blockProps",true);set(f.logic,"decoyLimit",1);set(f.logic,"decoyCooldown",12);set(f.logic,"decoyLifetime",20);
        set(f.logic,"palette",List.of(new PropPalette.Option("Stone",Material.STONE,"ready:stone")));
        Block floor=mock(Block.class),air=mock(Block.class);
        when(floor.getLocation()).thenReturn(new Location(f.world,2,63,0));
        when(floor.getBoundingBox()).thenReturn(new BoundingBox(2,63,0,3,64,1));
        when(air.isPassable()).thenReturn(true);when(f.world.getMinHeight()).thenReturn(-64);when(f.world.getMaxHeight()).thenReturn(320);
        when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(f.world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->(int)i.getArgument(1)==63?floor:air);
        when(f.bot.rayTraceBlocks(4,FluidCollisionMode.NEVER)).thenReturn(new RayTraceResult(new org.bukkit.util.Vector(2.5,64,.5),floor,BlockFace.UP));
        var visual=mock(uk.co.playerready.pulsegames.core.content.PulseContentBridge.PropVisual.class);
        when(f.game.plugin().content().createNpcProp(eq("ready:stone"),eq(f.bot),any(),anySet(),eq(1f))).thenReturn(visual);
        f.logic.botAbilities(f.bot,100);f.logic.botAbilities(f.bot,140);
        assertEquals(1,PropBotAbilitiesTest.<Map<?,?>>field(f.logic,"decoys").size());
        assertEquals(12,PropBotAbilitiesTest.<Map<String,Integer>>field(f.logic,"cooldowns").get(f.id+":Decoy"));
        verify(f.game.plugin().content(),never()).createProp(anyString(),any(),any(),anySet(),anyFloat());
        f.logic.onCleanup();verify(visual).close();
        assertTrue(PropBotAbilitiesTest.<Map<?,?>>field(f.logic,"botNextPower").isEmpty());
    }
}
