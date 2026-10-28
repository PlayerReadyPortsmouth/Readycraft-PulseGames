package uk.co.playerready.pulsegames.games.common;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RacePackTest {
    final World world = mock(World.class);
    final GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
    final Player p1 = player(), p2 = player();
    long clock = 1_000_000;

    Player player() {
        Player p = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(id);
        when(p.getName()).thenReturn("P" + id.toString().substring(0, 4));
        when(p.getLocation()).thenReturn(at(0));
        return p;
    }

    class Race extends CheckpointRaceGame {
        Race() { super(RacePackTest.this.game); }
        protected int laps() { return 1; }
        @Override protected boolean racePack() { return true; }
        @Override protected long nowMs() { return clock; }
        @Override public int startFreezeSeconds() { return 0; }
    }

    Location at(double x) { return new Location(world, x, 80, 0.5); }

    Race race(List<Cuboid> checkpoints) {
        when(game.world()).thenReturn(world);
        when(game.alivePlayers()).thenReturn(List.of(p1, p2));
        when(game.player(p1.getUniqueId())).thenReturn(p1);
        when(game.player(p2.getUniqueId())).thenReturn(p2);
        when(game.arena().regionsByPrefix("checkpoint")).thenReturn(checkpoints);
        when(game.arena().region("finish")).thenReturn(new Cuboid(8, 79, 0, 8, 82, 0));
        when(game.arena().settings().getInt("finish-grace", 30)).thenReturn(30);
        when(game.arena().settingLocation(anyString(), eq(world))).thenReturn(null);
        Race race = new Race();
        race.onStart();
        return race;
    }

    @Test void firstFinisherDoesNotEndTheRaceAndOthersFinishInsideGrace() {
        Race race = race(List.of());
        clock += 60_000;
        race.onMove(p1, at(0), at(8.5));
        verify(game, never()).end(any(), anyString());
        verify(game).finishParticipant(p1);
        clock += 12_000;
        race.onMove(p2, at(0), at(8.5));
        assertEquals(2, race.finishes.placeOf(p2.getUniqueId()));
        assertEquals(72_000, race.finishes.timeOf(p2.getUniqueId()));
        verify(game).end(List.of(p1), "race-complete");
    }

    @Test void engineHearsEveryFinishInRaceOrderThenTheUnfinished() {
        Race race = race(List.of());
        clock += 60_000;
        race.onMove(p1, at(0), at(8.5));
        var order = inOrder(game);
        order.verify(game).recordFinish(p1);
        order.verify(game).finishParticipant(p1);
        clock += 31_000;
        race.onSecond(91);
        order.verify(game).recordFinish(p2); // did not finish: ranked after the finisher
        order.verify(game).end(List.of(p1), "race-complete");
    }

    @Test void graceExpiryEndsWithFirstFinisherAsWinner() {
        Race race = race(List.of());
        clock += 60_000;
        race.onMove(p1, at(0), at(8.5));
        clock += 29_000;
        race.onSecond(89);
        verify(game, never()).end(any(), anyString());
        clock += 2_000;
        race.onSecond(91);
        verify(game).end(List.of(p1), "race-complete");
    }

    @Test void splitAgainstLeaderShownAtCheckpoint() {
        Race race = race(List.of(new Cuboid(2, 79, 0, 2, 82, 0)));
        clock += 10_000;
        race.onMove(p1, at(0), at(2.5));
        clock += 1_400;
        race.onMove(p2, at(0), at(2.5));
        ArgumentCaptor<Component> bar = ArgumentCaptor.forClass(Component.class);
        verify(p2, atLeastOnce()).sendActionBar(bar.capture());
        String text = Text.legacy(bar.getValue());
        assertTrue(text.contains("+1.4s"), text);
        assertTrue(text.contains("2nd"), text);
    }

    @Test void personalBestShowsTitleAndSound() {
        Race race = race(List.of());
        when(game.stats().submitBestTime(eq(p1), any(), any(), anyLong())).thenReturn(true);
        clock += 50_000;
        race.onMove(p1, at(0), at(8.5));
        verify(p1).showTitle(any(net.kyori.adventure.title.Title.class));
        verify(p1).playSound(any(Location.class), eq("minecraft:ui.toast.challenge_complete"), anyFloat(), anyFloat());
    }

    @Test void sidebarListsEveryRacerWithPlaces() {
        Race race = race(List.of());
        List<String> lines = race.sidebar(p2);
        assertEquals(2, lines.stream().filter(l -> l.contains("#")).count());
    }
}
