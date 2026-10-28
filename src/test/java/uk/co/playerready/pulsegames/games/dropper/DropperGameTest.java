package uk.co.playerready.pulsegames.games.dropper;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DropperGameTest {
    final World world = mock(World.class);
    final GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
    final Player p1 = player(), p2 = player();
    long clock = 5_000_000;

    Player player() {
        Player p = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(id);
        when(p.getLocation()).thenReturn(new Location(world, 0, 80, 0));
        when(game.isActive(p)).thenReturn(true);
        return p;
    }

    DropperGame start() {
        Block water = mock(Block.class);
        when(water.isLiquid()).thenReturn(true);
        when(water.getType()).thenReturn(Material.WATER);
        when(world.getBlockAt(any(Location.class))).thenReturn(water);
        when(game.world()).thenReturn(world);
        when(game.alivePlayers()).thenReturn(List.of(p1, p2));
        when(game.player(p1.getUniqueId())).thenReturn(p1);
        when(game.player(p2.getUniqueId())).thenReturn(p2);
        when(game.spawnFor(any())).thenReturn(new Location(world, 0, 100, 0));
        when(game.arena().regionsByPrefix("checkpoint")).thenReturn(List.of(new Cuboid(2, 0, 0, 2, 90, 0)));
        when(game.arena().region("finish")).thenReturn(new Cuboid(8, 0, 0, 8, 90, 0));
        when(game.arena().settingLocation("checkpoint-spawn1", world)).thenReturn(new Location(world, 5, 100, 0));
        when(game.arena().settings().getInt("finish-grace", 30)).thenReturn(30);
        DropperGame dropper = new DropperGame(game) {
            @Override protected long nowMs() { return clock; }
            @Override public int startFreezeSeconds() { return 0; }
        };
        dropper.onStart();
        return dropper;
    }

    Location pool(double x) { return new Location(world, x, 50, 0.5); }

    @Test void firstFinisherDoesNotMakeEveryoneElseLose() {
        DropperGame dropper = start();
        dropper.onMove(p1, pool(0), pool(2.5));
        dropper.onMove(p1, pool(0), pool(8.5));
        verify(game).finishParticipant(p1);
        verify(game, never()).end(any(), anyString());
        clock += 20_000;
        dropper.onMove(p2, pool(0), pool(2.5));
        dropper.onMove(p2, pool(0), pool(8.5));
        verify(game).end(List.of(p1), "finished");
    }

    @Test void stageClearShowsTitleAndMissesAreCountedWithoutPenalty() {
        DropperGame dropper = start();
        dropper.onDeath(p1, null);
        dropper.onDeath(p1, null);
        clock += 12_000;
        dropper.onMove(p1, pool(0), pool(2.5));
        verify(p1).showTitle(any(net.kyori.adventure.title.Title.class));
        verify(p1).playSound(any(Location.class), eq("minecraft:entity.player.levelup"), anyFloat(), anyFloat());
        assertTrue(dropper.sidebar(p1).get(1).contains("2"), "two misses shown");
    }

    @Test void practiceIsATimeTrialThatSavesThePersonalBest() {
        when(game.practice()).thenReturn(true);
        when(game.alivePlayers()).thenReturn(List.of(p1));
        when(game.stats().submitBestTime(eq(p1), any(), any(), anyLong())).thenReturn(true);
        DropperGame dropper = start();
        when(game.alivePlayers()).thenReturn(List.of(p1));
        clock += 30_000;
        dropper.onMove(p1, pool(0), pool(2.5));
        dropper.onMove(p1, pool(0), pool(8.5));
        verify(game.stats()).submitBestTime(eq(p1), any(), any(), eq(30_000L));
    }
}
