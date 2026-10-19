package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;

import java.lang.reflect.Field;
import java.util.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A tiny mocked world for real-blocks tests: air everywhere, a solid floor at y=63, blocks that remember their data. */
final class RealBlockFixture {
    final BlockData air = data(), cube = data(), half = data();
    final World world = mock(World.class);
    final GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
    final PropHuntGame logic = new PropHuntGame(game);
    final Map<List<Integer>, Block> grid = new HashMap<>();
    final Map<List<Integer>, BlockData> state = new HashMap<>();
    final Set<List<Integer>> solid = new HashSet<>();

    RealBlockFixture() throws Exception {
        VoxelShape full = shape(new BoundingBox(0, 0, 0, 1, 1, 1)), small = shape(new BoundingBox(0, 0, 0, 1, .5, 1));
        when(cube.getCollisionShape(any(Location.class))).thenReturn(full);
        when(half.getCollisionShape(any(Location.class))).thenReturn(small);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
        set(logic, "blocks", new PropBlocks(m -> m == Material.AIR ? air : m == Material.PLAYER_HEAD ? half : cube));
        set(logic, "blockProps", true); set(logic, "realBlocks", true); set(logic, "powers", true);
        set(logic, "decoyLimit", 3); set(logic, "decoyLifetime", 60); set(logic, "decoyCooldown", 1);
        when(game.state()).thenReturn(GameState.RUNNING); when(game.mode().id()).thenReturn("classic");
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) solid.add(List.of(x, 63, z));
    }
    static BlockData data() { BlockData d = mock(BlockData.class); when(d.clone()).thenReturn(d); return d; }
    private static VoxelShape shape(BoundingBox box) {
        VoxelShape shape = mock(VoxelShape.class); when(shape.getBoundingBoxes()).thenReturn(List.of(box)); return shape;
    }
    Block block(int x, int y, int z) {
        return grid.computeIfAbsent(List.of(x, y, z), key -> {
            Block b = mock(Block.class);
            when(b.getX()).thenReturn(x); when(b.getY()).thenReturn(y); when(b.getZ()).thenReturn(z);
            when(b.getWorld()).thenReturn(world);
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            when(b.isPassable()).thenAnswer(c -> !solid.contains(key));
            when(b.isLiquid()).thenReturn(false);
            when(b.getBoundingBox()).thenReturn(new BoundingBox(x, y, z, x + 1, y + 1, z + 1));
            when(b.getBlockData()).thenAnswer(c -> state.getOrDefault(key, air));
            doAnswer(c -> { state.put(key, c.getArgument(0)); return null; }).when(b).setBlockData(any(BlockData.class), eq(false));
            when(b.getRelative(any(org.bukkit.block.BlockFace.class))).thenAnswer(c -> {
                org.bukkit.block.BlockFace f = c.getArgument(0);
                return block(x + f.getModX(), y + f.getModY(), z + f.getModZ());
            });
            return b;
        });
    }
    BlockData at(int x, int y, int z) { return state.getOrDefault(List.of(x, y, z), air); }
    Player player() {
        Player p = mock(Player.class); UUID id = UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(id); when(p.getWorld()).thenReturn(world);
        when(p.teleport(any(Location.class))).thenReturn(true);
        when(game.player(id)).thenReturn(p); when(game.isActive(p)).thenReturn(true);
        return p;
    }
    /** A hider choosing Stone (a full cube), standing at the given point. */
    Player hider(double x, double y, double z, boolean onGround) throws Exception {
        Player p = player();
        when(p.getLocation()).thenReturn(new Location(world, x, y, z)); when(p.isOnGround()).thenReturn(onGround);
        this.<Set<UUID>>field("hiders").add(p.getUniqueId());
        this.<Map<UUID, PropPalette.Option>>field("choices").put(p.getUniqueId(), STONE);
        return p;
    }
    static final PropPalette.Option STONE = new PropPalette.Option("Stone", Material.STONE, "ready:stone");
    static final PropPalette.Option HEAD = new PropPalette.Option("Head", Material.PLAYER_HEAD, "ready:head");
    void unlockDebounce() throws Exception { this.<Map<UUID, Long>>field("nextLock").clear(); }
    @SuppressWarnings("unchecked") <T> T field(String name) throws Exception { return (T) get(logic, name); }
    static Object get(Object o, String n) throws Exception { Field f = o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o); }
    static void set(Object o, String n, Object v) throws Exception { Field f = o.getClass().getDeclaredField(n); f.setAccessible(true); f.set(o, v); }
}
