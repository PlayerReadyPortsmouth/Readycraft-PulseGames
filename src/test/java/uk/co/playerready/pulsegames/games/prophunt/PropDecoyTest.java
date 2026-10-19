package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.*;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import org.bukkit.util.*;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class PropDecoyTest {
    private static final PropPalette.Option STONE=new PropPalette.Option("Stone",Material.STONE,"ready:stone");
    private static final PropPalette.Option HEAD=new PropPalette.Option("Head",Material.PLAYER_HEAD,"ready:head");
    private Object decoy(UUID owner,PropPalette.Option option,Location feet,AutoCloseable visual,int expires) throws Exception {
        Class<?> type=Class.forName(PropHuntGame.class.getName()+"$Decoy");
        var constructor=type.getDeclaredConstructor(UUID.class,PropPalette.Option.class,Location.class,AutoCloseable.class,int.class);
        constructor.setAccessible(true);return constructor.newInstance(owner,option,feet,visual,expires);
    }
    @Test void independentlyDisguisedDecoysUseTheirOwnBoundsAndWallsOccludeThem() throws Exception {
        GameInstance game=mock(GameInstance.class);PropHuntGame logic=new PropHuntGame(game);World world=mock(World.class);
        Player seeker=mock(Player.class);when(seeker.getEyeLocation()).thenReturn(new Location(world,0,64.75,0));
        Map<UUID,Object> decoys=field(logic,"decoys");UUID small=UUID.randomUUID(),full=UUID.randomUUID();
        decoys.put(small,decoy(UUID.randomUUID(),HEAD,new Location(world,0,64,1),mock(AutoCloseable.class),60));
        decoys.put(full,decoy(UUID.randomUUID(),STONE,new Location(world,0,64,2),mock(AutoCloseable.class),60));
        assertEquals(full,logic.aimedProp(seeker)); // Ray passes above the head but hits the full cube.
        when(seeker.getEyeLocation()).thenReturn(new Location(world,0,64.25,0));assertEquals(small,logic.aimedProp(seeker));
        when(seeker.rayTraceBlocks(3,FluidCollisionMode.NEVER)).thenReturn(new RayTraceResult(new org.bukkit.util.Vector(0,64.25,.5)));
        assertNull(logic.aimedProp(seeker));
    }
    @Test void expiryAndEliminationCloseOnlyRelevantDecoysAndDoNotLeakHandles() throws Exception {
        GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);PropHuntGame logic=new PropHuntGame(game);
        UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),expired=UUID.randomUUID(),retained=UUID.randomUUID();
        AutoCloseable oldVisual=mock(AutoCloseable.class),otherVisual=mock(AutoCloseable.class);
        Map<UUID,Object> decoys=field(logic,"decoys");
        decoys.put(expired,decoy(owner,STONE,new Location(null,0,64,0),oldVisual,5));
        decoys.put(retained,decoy(other,HEAD,new Location(null,1,64,0),otherVisual,60));
        logic.onSecond(5);verify(oldVisual).close();verify(otherVisual,never()).close();assertEquals(Set.of(retained),decoys.keySet());
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(other);logic.onEliminated(player);
        assertTrue(decoys.isEmpty());verify(otherVisual).close();logic.onCleanup();verify(otherVisual,times(1)).close();
    }
    @Test void seekersCannotUsePowersBeforeReleaseOrWhileFrozenAndSpectatorsCannotUseThem() throws Exception {
        GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);PropHuntGame logic=new PropHuntGame(game);Player player=mock(Player.class);
        UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);when(game.state()).thenReturn(GameState.RUNNING);when(game.isActive(player)).thenReturn(true);
        set(logic,"powers",true);Set<UUID> seekers=field(logic,"seekers");seekers.add(id);
        Method can=PropHuntGame.class.getDeclaredMethod("canUsePower",Player.class,boolean.class);can.setAccessible(true);
        set(logic,"hidePhase",10);assertFalse((boolean)can.invoke(logic,player,false));
        set(logic,"hidePhase",0);assertTrue((boolean)can.invoke(logic,player,false));
        when(game.frozen()).thenReturn(true);assertFalse((boolean)can.invoke(logic,player,false));
        when(game.frozen()).thenReturn(false);when(game.isActive(player)).thenReturn(false);assertFalse((boolean)can.invoke(logic,player,false));
    }
    @Test void realBlockDecoysPlaceAgainstAnyFaceStackWithoutSupportAndRestoreOnExpiryAndHit() throws Exception {
        RealBlockFixture f = new RealBlockFixture();
        Player owner = f.hider(2.5, 64, 2.5, true);
        var floor = f.block(0, 63, 0); var lower = f.block(0, 64, 0); var upper = f.block(0, 65, 0);
        when(owner.rayTraceBlocks(4, FluidCollisionMode.NEVER)).thenReturn(new RayTraceResult(new org.bukkit.util.Vector(.5, 64, .5), floor, org.bukkit.block.BlockFace.UP));
        Method place = PropHuntGame.class.getDeclaredMethod("placeDecoy", Player.class, PropPalette.Option.class); place.setAccessible(true);
        place.invoke(f.logic, owner, RealBlockFixture.STONE);
        assertSame(f.cube, f.at(0, 64, 0));
        // Aim at the side of that decoy: the new one goes in the neighbouring cell, in mid-air, with no support underneath.
        ((Map<String, Integer>) f.field("cooldowns")).clear();
        var sideCell = f.block(1, 64, 0);
        when(owner.rayTraceBlocks(4, FluidCollisionMode.NEVER)).thenReturn(new RayTraceResult(new org.bukkit.util.Vector(1, 64.5, .5), lower, org.bukkit.block.BlockFace.EAST));
        place.invoke(f.logic, owner, RealBlockFixture.STONE);
        ((Map<String, Integer>) f.field("cooldowns")).clear();
        when(owner.rayTraceBlocks(4, FluidCollisionMode.NEVER)).thenReturn(new RayTraceResult(new org.bukkit.util.Vector(.5, 65, .5), lower, org.bukkit.block.BlockFace.UP));
        place.invoke(f.logic, owner, RealBlockFixture.STONE);
        assertSame(f.cube, f.at(1, 64, 0)); assertSame(f.cube, f.at(0, 65, 0));
        Map<UUID, Object> decoys = f.field("decoys"); assertEquals(3, decoys.size());
        // Hitting the lower decoy restores only its own cell; the stacked one stays until it expires.
        Player seeker = f.player(); when(seeker.getEyeLocation()).thenReturn(new Location(f.world, .5, 64.5, -1.5, 0, 0));
        ((Set<UUID>) f.field("seekers")).add(seeker.getUniqueId());
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class); var sword = mock(org.bukkit.inventory.ItemStack.class);
        when(sword.getType()).thenReturn(Material.IRON_SWORD); when(inventory.getItemInMainHand()).thenReturn(sword); when(seeker.getInventory()).thenReturn(inventory);
        when(seeker.rayTraceBlocks(3.0, FluidCollisionMode.NEVER)).thenReturn(new RayTraceResult(new org.bukkit.util.Vector(.5, 64.5, 0), lower, org.bukkit.block.BlockFace.NORTH));
        f.logic.onSwing(seeker);
        assertSame(f.air, f.at(0, 64, 0)); assertSame(f.cube, f.at(0, 65, 0)); assertEquals(2, decoys.size());
        verify(owner, never()).damage(anyDouble(), any(org.bukkit.entity.Entity.class));
        f.logic.onSecond(61); // lifetime 60 seconds from game time 0
        assertTrue(decoys.isEmpty()); assertSame(f.air, f.at(1, 64, 0)); assertSame(f.air, f.at(0, 65, 0));
    }
    @SuppressWarnings("unchecked") private static <T>T field(Object object,String name)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return(T)f.get(object);}
    private static void set(Object object,String name,Object value)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
}
