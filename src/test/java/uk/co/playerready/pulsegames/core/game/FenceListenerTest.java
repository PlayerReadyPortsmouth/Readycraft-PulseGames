package uk.co.playerready.pulsegames.core.game;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;

import static org.mockito.Mockito.*;

class FenceListenerTest {
    private final PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
    private final World world = mock(World.class);
    private final FenceListener listener = new FenceListener(plugin);

    private void world(boolean fenced) {
        GameInstance instance = mock(GameInstance.class);
        MiniGame logic = mock(MiniGame.class);
        when(logic.fenced()).thenReturn(fenced);
        when(instance.logic()).thenReturn(logic);
        when(plugin.instances().byWorld(world)).thenReturn(instance);
    }

    @Test void signsBooksBucketsFireAndSpawnsAreRefusedInAFencedWorld() {
        world(true);
        SignChangeEvent sign = mock(SignChangeEvent.class, RETURNS_DEEP_STUBS);
        when(sign.getBlock().getWorld()).thenReturn(world);
        listener.onSign(sign);
        verify(sign).setCancelled(true);

        PlayerEditBookEvent book = mock(PlayerEditBookEvent.class, RETURNS_DEEP_STUBS);
        when(book.getPlayer().getWorld()).thenReturn(world);
        listener.onBook(book);
        verify(book).setCancelled(true);

        PlayerBucketEmptyEvent bucket = mock(PlayerBucketEmptyEvent.class, RETURNS_DEEP_STUBS);
        when(bucket.getPlayer().getWorld()).thenReturn(world);
        listener.onBucketEmpty(bucket);
        verify(bucket).setCancelled(true);

        BlockIgniteEvent fire = mock(BlockIgniteEvent.class, RETURNS_DEEP_STUBS);
        when(fire.getBlock().getWorld()).thenReturn(world);
        listener.onIgnite(fire);
        verify(fire).setCancelled(true);

        EntitySpawnEvent egg = mock(EntitySpawnEvent.class, RETURNS_DEEP_STUBS);
        when(egg.getEntity().getWorld()).thenReturn(world);
        when(egg.getEntityType()).thenReturn(EntityType.ZOMBIE);
        listener.onSpawn(egg);
        verify(egg).setCancelled(true);
    }

    @Test void otherGamesKeepTheirBucketsAndDrops() {
        world(false);
        PlayerBucketEmptyEvent bucket = mock(PlayerBucketEmptyEvent.class, RETURNS_DEEP_STUBS);
        when(bucket.getPlayer().getWorld()).thenReturn(world);
        listener.onBucketEmpty(bucket);
        verify(bucket, never()).setCancelled(true);

        world(true);
        EntitySpawnEvent drop = mock(EntitySpawnEvent.class, RETURNS_DEEP_STUBS);
        when(drop.getEntity().getWorld()).thenReturn(world);
        when(drop.getEntityType()).thenReturn(EntityType.ITEM);
        listener.onSpawn(drop);
        verify(drop, never()).setCancelled(true);
    }
}
