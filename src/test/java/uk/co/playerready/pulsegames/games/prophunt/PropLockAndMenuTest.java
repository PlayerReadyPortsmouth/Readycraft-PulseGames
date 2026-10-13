package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PropLockAndMenuTest {
    @Test void twoPlayersCanStartButSoloForceStartIsRejected() {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        PropHuntGame logic = new PropHuntGame(game);
        assertNotNull(logic.startProblem(java.util.List.of(mock(Player.class))));
        assertNull(logic.startProblem(java.util.List.of(mock(Player.class), mock(Player.class))));
    }

    @Test void snapUsesBlockFloorIncludingNegativeCoordinates() {
        World world = mock(World.class);
        Location cell = PropHuntGame.snapCell(new Location(world, -0.01, 64.99, -1.01, 95, -30));
        assertEquals(new Location(world, -1, 64, -2), cell);
        assertEquals(new Location(world, -1, -2, 0), PropHuntGame.snapCell(new Location(world, -1, -1.5, 0)));
    }
    @Test void lockedSameCellMovementKeepsAnchorButAllowsCameraRotation() throws Exception {
        GameInstance game = mock(GameInstance.class);
        PropHuntGame logic = new PropHuntGame(game);
        Player player = player();
        World world = mock(World.class);
        Location anchor = new Location(world, -0.5, 65, -0.5, 0, 0);
        Class<?> lockedClass = Class.forName(PropHuntGame.class.getName() + "$Locked");
        var constructor = lockedClass.getDeclaredConstructor(Location.class, Location.class);
        constructor.setAccessible(true);
        Map<UUID, Object> locked = field(logic, "locked");
        locked.put(player.getUniqueId(), constructor.newInstance(new Location(world, -1, 64, -1), anchor));
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getTo()).thenReturn(new Location(world, -0.4, 65, -0.3, 123, -45));
        logic.onPlayerMove(event);
        ArgumentCaptor<Location> result = ArgumentCaptor.forClass(Location.class);
        verify(event).setTo(result.capture());
        assertEquals(-0.5, result.getValue().getX());
        assertEquals(65, result.getValue().getY());
        assertEquals(-0.5, result.getValue().getZ());
        assertEquals(123, result.getValue().getYaw());
        assertEquals(-45, result.getValue().getPitch());
        assertEquals(0, anchor.getYaw()); // Camera changes cannot mutate the saved anchor.
    }
    @Test void unlockedMovementIsUnchanged() {
        PropHuntGame logic = new PropHuntGame(mock(GameInstance.class));
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        Player player = player();
        when(event.getPlayer()).thenReturn(player);
        logic.onPlayerMove(event);
        verify(event, never()).setTo(any());
    }
    @Test void paletteDragAndExpiredSelectionAreCancelledWithoutReplacingVisual() throws Exception {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        PropHuntGame logic = new PropHuntGame(game);
        Player player = player();
        when(game.state()).thenReturn(GameState.RUNNING);
        when(game.isActive(player)).thenReturn(true);
        set(logic, "blockProps", true);
        set(logic, "hidePhase", 0);
        set(logic, "palette", java.util.List.of(new PropPalette.Option("Stone", org.bukkit.Material.STONE, "ready:stone")));
        AutoCloseable original = mock(AutoCloseable.class);
        Map<UUID, AutoCloseable> visuals = field(logic, "visuals");
        visuals.put(player.getUniqueId(), original);
        Set<UUID> hiders = field(logic, "hiders");
        hiders.add(player.getUniqueId());
        Class<?> menuClass = Class.forName(PropHuntGame.class.getName() + "$PaletteMenu");
        var constructor = menuClass.getDeclaredConstructor(PropHuntGame.class, UUID.class);
        constructor.setAccessible(true);
        InventoryHolder menu = (InventoryHolder) constructor.newInstance(logic, player.getUniqueId());
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(menu);
        InventoryClickEvent click = mock(InventoryClickEvent.class, RETURNS_DEEP_STUBS);
        when(click.getView().getTopInventory()).thenReturn(inventory);
        when(click.getClickedInventory()).thenReturn(inventory);
        when(click.getSlot()).thenReturn(0);
        logic.onInventoryClick(player, click);
        verify(click).setCancelled(true);
        verify(original, never()).close();
        verify(game.plugin().content(), never()).attach(anyString(), any(), anySet());
        InventoryDragEvent drag = mock(InventoryDragEvent.class, RETURNS_DEEP_STUBS);
        when(drag.getView().getTopInventory()).thenReturn(inventory);
        logic.onInventoryDrag(player, drag);
        verify(drag).setCancelled(true);
    }
    @Test void adjacentWallLockDoesNotRaisePlayerAndDuplicateUseDoesNotUnlock() throws Exception {
        GameInstance game=mock(GameInstance.class, RETURNS_DEEP_STUBS); PropHuntGame logic=new PropHuntGame(game);
        Player player=player(); World world=mock(World.class);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(call -> {
            int x=call.getArgument(0), y=call.getArgument(1), z=call.getArgument(2);
            var block=mock(org.bukkit.block.Block.class);
            when(block.isPassable()).thenReturn(x==0 && y==64 && z==0);
            when(block.getBoundingBox()).thenReturn(new org.bukkit.util.BoundingBox(x,y,z,x+1,y+1,z+1)); return block;
        });
        when(player.getLocation()).thenReturn(new Location(world,.48,64,.5)); when(player.isOnGround()).thenReturn(true);
        when(player.teleport(any(Location.class))).thenReturn(true);
        when(game.state()).thenReturn(GameState.RUNNING); when(game.isActive(player)).thenReturn(true);
        Set<UUID> hiders=field(logic,"hiders"); hiders.add(player.getUniqueId());
        Map<UUID,PropPalette.Option> choices=field(logic,"choices"); choices.put(player.getUniqueId(),new PropPalette.Option("Stone",org.bukkit.Material.STONE,"ready:stone"));
        logic.toggleLock(player); logic.toggleLock(player);
        Map<UUID,Object> locks=field(logic,"locked"); assertTrue(locks.containsKey(player.getUniqueId()));
        ArgumentCaptor<Location> destination=ArgumentCaptor.forClass(Location.class); verify(player,times(1)).teleport(destination.capture());
        assertEquals(64,destination.getValue().getY()); assertEquals(.5,destination.getValue().getX());
        verify(game.plugin().content(),never()).attachBlockProp(any(),any(),any(),anyCollection());
    }
    @Test void failedSnappingDoesNotRemoveMovingDisguiseOrTeleport() throws Exception {
        GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS); PropHuntGame logic=new PropHuntGame(game);
        Player player=player(); World world=mock(World.class);
        when(player.getLocation()).thenReturn(new Location(world,.5,64,.5)); when(player.isOnGround()).thenReturn(true);
        when(game.state()).thenReturn(GameState.RUNNING); when(game.isActive(player)).thenReturn(true);
        Set<UUID> hiders=field(logic,"hiders"); hiders.add(player.getUniqueId());
        Map<UUID,PropPalette.Option> choices=field(logic,"choices"); choices.put(player.getUniqueId(),new PropPalette.Option("Stone",org.bukkit.Material.STONE,"ready:stone"));
        AutoCloseable visual=mock(AutoCloseable.class); Map<UUID,AutoCloseable> visuals=field(logic,"visuals"); visuals.put(player.getUniqueId(),visual);
        logic.toggleLock(player);
        verify(player,never()).teleport(any(Location.class)); verify(visual,never()).close();
        Map<UUID,Object> locks=field(logic,"locked"); assertTrue(locks.isEmpty());
    }
    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
    @SuppressWarnings("unchecked") private <T> T field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true);
        return (T) field.get(object);
    }
    private void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
}
