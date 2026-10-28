package uk.co.playerready.pulsegames.core.game;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import uk.co.playerready.pulsegames.PulseGamesPlugin;

import java.util.List;

/**
 * The doors a creative-mode game leaves open besides block place and break. Only worlds of a running
 * {@link MiniGame#fenced()} instance are touched, so every other game keeps its current behaviour.
 */
public final class FenceListener implements Listener {

    private final PulseGamesPlugin plugin;

    public FenceListener(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    private MiniGame fence(World world) {
        GameInstance instance = plugin.instances().byWorld(world);
        return instance != null && instance.logic().fenced() ? instance.logic() : null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (fence(event.getPlayer().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (fence(event.getPlayer().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (fence(event.getPlayer().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (fence(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (fence(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (fence(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        MiniGame logic = fence(event.getBlock().getWorld());
        if (logic != null && !logic.fenceAllows(event.getBlock().getLocation(), event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        MiniGame logic = fence(event.getBlock().getWorld());
        if (logic != null && !pistonStays(logic, event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        MiniGame logic = fence(event.getBlock().getWorld());
        if (logic != null && !pistonStays(logic, event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private static boolean pistonStays(MiniGame logic, Block piston, List<Block> moved, org.bukkit.block.BlockFace face) {
        // The head square is where a pushed block lands, or the piston's own reach when nothing moves.
        if (!logic.fenceAllows(piston.getLocation(), piston.getRelative(face).getLocation())) return false;
        for (Block block : moved) {
            if (!logic.fenceAllows(piston.getLocation(), block.getLocation())
                    || !logic.fenceAllows(piston.getLocation(), block.getRelative(face).getLocation())) return false;
        }
        return true;
    }

    /** Spawn eggs, armor stands, vehicles, crystals, lit TNT: nothing a player carries may create an entity. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSpawn(EntitySpawnEvent event) {
        if (fence(event.getEntity().getWorld()) == null) return;
        EntityType type = event.getEntityType();
        if (type != EntityType.PLAYER && type != EntityType.ITEM && type != EntityType.FALLING_BLOCK
                && type != EntityType.EXPERIENCE_ORB) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (fence(event.getEntity().getWorld()) != null) event.setCancelled(true);
    }

    /** Text a child writes is shown to every voter; no signs or books in a fenced build. */
    @EventHandler(ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        if (fence(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent event) {
        if (fence(event.getPlayer().getWorld()) != null) event.setCancelled(true);
    }
}
