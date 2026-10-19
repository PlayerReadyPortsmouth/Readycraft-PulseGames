package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class PropAimTest {
    private static final PropPalette.Option BLOCK = new PropPalette.Option("Stone", Material.STONE, "ready:stone");
    private static final PropPalette.Option HEAD = new PropPalette.Option("Head", Material.PLAYER_HEAD, "ready:head");

    @Test void fullBlockAndHeadBoundsStartAtFeetWithCorrectWidthAndHeight() {
        Location feet = new Location(null, -1.5, 64, 2.5);
        var block = PropHuntGame.movingBounds(feet, BLOCK);
        assertEquals(-2, block.getMinX()); assertEquals(-1, block.getMaxX());
        assertEquals(64, block.getMinY()); assertEquals(65, block.getMaxY());
        assertEquals(2, block.getMinZ()); assertEquals(3, block.getMaxZ());
        var head = PropHuntGame.movingBounds(feet, HEAD);
        assertEquals(-1.75, head.getMinX()); assertEquals(-1.25, head.getMaxX());
        assertEquals(64, head.getMinY()); assertEquals(64.5, head.getMaxY());
        assertEquals(2.25, head.getMinZ()); assertEquals(2.75, head.getMaxZ());
    }

    @Test void aimAboveHeadMissesButFullBlockAtSameFeetHits() throws Exception {
        Fixture f = new Fixture();
        Player prop = f.prop(2, HEAD);
        f.eye(64.75);
        assertNull(f.logic.aimedProp(f.seeker));
        f.choices.put(prop.getUniqueId(), BLOCK);
        assertEquals(prop.getUniqueId(), f.logic.aimedProp(f.seeker));
    }

    @Test void solidWallBeforePropPreventsSelection() throws Exception {
        Fixture f = new Fixture();
        f.prop(2, BLOCK);
        when(f.seeker.rayTraceBlocks(3.0, FluidCollisionMode.NEVER))
                .thenReturn(new RayTraceResult(new Vector(0,64.25,1)));
        assertNull(f.logic.aimedProp(f.seeker));
    }

    @Test void selectsNearestOnlyAndDoesNotReachBeyondThreeBlocks() throws Exception {
        Fixture f = new Fixture();
        f.prop(2.8, BLOCK);
        Player nearest = f.prop(1.5, HEAD);
        assertEquals(nearest.getUniqueId(), f.logic.aimedProp(f.seeker));
        f.hiders.clear();
        f.prop(4, BLOCK);
        assertNull(f.logic.aimedProp(f.seeker));
    }

    @Test void skipsInactiveAndOtherWorldProps() throws Exception {
        Fixture f = new Fixture();
        Player inactive = f.prop(1, BLOCK);
        when(f.game.isActive(inactive)).thenReturn(false);
        Player elsewhere = f.prop(1.5, BLOCK);
        World otherWorld = mock(World.class);
        when(elsewhere.getWorld()).thenReturn(otherWorld);
        Player valid = f.prop(2.5, BLOCK);
        assertEquals(valid.getUniqueId(), f.logic.aimedProp(f.seeker));
    }

    @Test void swingAndPlayerDamagePacketShareOneCooldown() throws Exception {
        Fixture f = new Fixture();
        Player victim = f.prop(2, HEAD);
        f.armSeeker();
        f.visual(victim, true);
        f.logic.onSwing(f.seeker);
        EntityDamageByEntityEvent vanilla = mock(EntityDamageByEntityEvent.class);
        f.logic.onDamageByPlayer(victim, f.seeker, vanilla);
        f.logic.onSwing(f.seeker);
        verify(vanilla).setCancelled(true);
        verify(victim, times(1)).damage(6.0, f.seeker);
        Set<UUID> routed = field(f.logic, "routedBlockHits");
        assertTrue(routed.isEmpty());
    }

    @Test void missingContentDoesNotDamageOrConsumeCooldown() throws Exception {
        Fixture f = new Fixture();
        Player victim = f.prop(2, HEAD);
        f.armSeeker();
        f.logic.onSwing(f.seeker);
        verify(victim, never()).damage(anyDouble(), any(org.bukkit.entity.Entity.class));
        f.visual(victim, true);
        f.logic.onSwing(f.seeker);
        verify(victim).damage(6.0, f.seeker);
    }

    @Test void blockLockedHiderIsResolvedThroughTheCellMapEvenOnAnExactTie() throws Exception {
        RealBlockFixture f = new RealBlockFixture();
        Player seeker = f.player(); when(seeker.getEyeLocation()).thenReturn(new Location(f.world, .5, 64.5, -1.5, 0, 0));
        Player hider = f.hider(.5, 64, .5, false);
        f.logic.toggleLock(hider);
        var cell = f.block(0, 64, 0);
        // The ray meets the real block's face at z=0, exactly where the prop's own box starts: a strict tie.
        when(seeker.rayTraceBlocks(3.0, FluidCollisionMode.NEVER)).thenReturn(new RayTraceResult(
                new Vector(.5, 64.5, 0), cell, org.bukkit.block.BlockFace.NORTH));
        assertEquals(hider.getUniqueId(), f.logic.aimedProp(seeker));
        // A different block in front is just a wall.
        var wall = f.block(0, 64, -1);
        when(seeker.rayTraceBlocks(3.0, FluidCollisionMode.NEVER)).thenReturn(new RayTraceResult(
                new Vector(.5, 64.5, -.5), wall, org.bukkit.block.BlockFace.NORTH));
        assertNull(f.logic.aimedProp(seeker));
    }
    @Test void hittingABlockHiderDamagesRevealsRestoresAndLocksOutRelock() throws Exception {
        RealBlockFixture f = new RealBlockFixture();
        Player seeker = f.player(); when(seeker.getEyeLocation()).thenReturn(new Location(f.world, .5, 64.5, -1.5, 0, 0));
        Player hider = f.hider(.5, 64, .5, false);
        f.logic.toggleLock(hider);
        assertSame(f.cube, f.at(0, 64, 0));
        var cell = f.block(0, 64, 0);
        ((Set<UUID>) field(f.logic, "seekers")).add(seeker.getUniqueId());
        PlayerInventory inventory = mock(PlayerInventory.class); ItemStack sword = mock(ItemStack.class);
        when(sword.getType()).thenReturn(Material.IRON_SWORD); when(inventory.getItemInMainHand()).thenReturn(sword);
        when(seeker.getInventory()).thenReturn(inventory);
        when(seeker.rayTraceBlocks(3.0, FluidCollisionMode.NEVER)).thenReturn(new RayTraceResult(
                new Vector(.5, 64.5, 0), cell, org.bukkit.block.BlockFace.NORTH));
        f.logic.onSwing(seeker);
        verify(hider).damage(6.0, seeker);
        assertSame(f.air, f.at(0, 64, 0));
        Map<UUID, Object> locks = field(f.logic, "locked"); assertFalse(locks.containsKey(hider.getUniqueId()));
        assertTrue(((Map<UUID, AutoCloseable>) field(f.logic, "visuals")).containsKey(hider.getUniqueId())); // model shown again
        f.logic.toggleLock(hider); // within the lockout
        assertSame(f.air, f.at(0, 64, 0));
        verify(hider, times(1)).teleport(any(Location.class));
    }
    private static final class Fixture {
        final GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        final PropHuntGame logic = new PropHuntGame(game);
        final World world = mock(World.class);
        final Player seeker = mock(Player.class);
        final Set<UUID> hiders;
        final Map<UUID, PropPalette.Option> choices;
        Fixture() throws Exception {
            hiders = field(logic, "hiders"); choices = field(logic, "choices");
            when(seeker.getUniqueId()).thenReturn(UUID.randomUUID());
            eye(64.25);
        }
        void eye(double height) { when(seeker.getEyeLocation()).thenReturn(new Location(world,0,height,0,0,0)); }
        Player prop(double z, PropPalette.Option option) {
            Player player = mock(Player.class);
            UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world,0,64,z));
            when(game.player(id)).thenReturn(player);
            when(game.isActive(player)).thenReturn(true);
            hiders.add(id); choices.put(id, option);
            return player;
        }
        void visual(Player player, boolean active) throws Exception {
            var part = mock(uk.co.playerready.pulsegames.core.content.PulseContentBridge.PropVisual.class);
            when(part.active()).thenReturn(active);
            when(game.plugin().content().createProp(anyString(), any(), any(), anyCollection(), anyFloat())).thenReturn(part);
            Class<?> type = Class.forName(PropHuntGame.class.getName() + "$PropRig");
            var constructor = type.getDeclaredConstructor(PropHuntGame.class, Player.class, PropPalette.Option.class);
            constructor.setAccessible(true);
            Map<UUID, AutoCloseable> visuals = field(logic, "visuals");
            visuals.put(player.getUniqueId(), (AutoCloseable)constructor.newInstance(logic, player, choices.get(player.getUniqueId())));
        }
        void armSeeker() throws Exception {
            set(logic, "blockProps", true);
            Set<UUID> seekers = field(logic, "seekers"); seekers.add(seeker.getUniqueId());
            when(game.isActive(seeker)).thenReturn(true);
            PlayerInventory inventory = mock(PlayerInventory.class);
            ItemStack sword = mock(ItemStack.class);
            when(sword.getType()).thenReturn(Material.IRON_SWORD);
            when(inventory.getItemInMainHand()).thenReturn(sword);
            when(seeker.getInventory()).thenReturn(inventory);
        }
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object,value);
    }
}
