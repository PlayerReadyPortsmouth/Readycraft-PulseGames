package uk.co.playerready.pulsegames.games.dodgeball;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DodgeballGameTest {
    GameInstance game;
    DodgeballGame logic;
    Player shooter, victim;
    World world;
    Snowball ball;
    AtomicLong time;
    @BeforeEach void setup() {
        game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        world = mock(World.class); shooter = player("Thrower"); victim = player("Target");
        when(game.world()).thenReturn(world); when(game.state()).thenReturn(GameState.RUNNING);
        when(game.isActive(any())).thenReturn(true);
        when(game.teamOf(any())).thenReturn(null);
        when(game.arena().settings().getInt("throw-cooldown-ticks", 7)).thenReturn(7);
        when(game.everyone()).thenReturn(List.of(shooter, victim));
        ball = mock(Snowball.class); when(ball.getUniqueId()).thenReturn(UUID.randomUUID());
        when(ball.getShooter()).thenReturn(shooter);
        when(shooter.launchProjectile(eq(Snowball.class), any())).thenReturn(ball);
        time = new AtomicLong(); logic = new DodgeballGame(game, time::get);
    }
    Player player(String name) {
        Player p = mock(Player.class); when(p.getName()).thenReturn(name);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID()); when(p.getWorld()).thenReturn(world);
        when(p.getEyeLocation()).thenReturn(new Location(world, 0, 82, 0));
        when(p.getLocation()).thenAnswer(x -> new Location(world, 0, 81, 0)); return p;
    }
    ProjectileHitEvent hit() {
        var event = mock(ProjectileHitEvent.class); when(event.getEntity()).thenReturn(ball);
        when(event.getHitEntity()).thenReturn(victim); return event;
    }
    @Test void cooldownIs350msAndVisibleWithoutAllowingSpam() {
        assertTrue(logic.throwBall(shooter)); verify(shooter).setCooldown(Material.BLAZE_ROD, 7);
        time.set(349_999_999L); assertFalse(logic.throwBall(shooter));
        time.set(350_000_000L); assertTrue(logic.throwBall(shooter));
    }
    @Test void acceptedHitNotifiesBothPlayersAndOnlyCountsOnce() {
        logic.throwBall(shooter); var event = hit(); logic.onProjectileHit(event); logic.onProjectileHit(event);
        verify(game).addScore(shooter, 1);
        verify(shooter).sendActionBar(any(net.kyori.adventure.text.Component.class));
        verify(victim).sendActionBar(any(net.kyori.adventure.text.Component.class));
        verify(victim).playHurtAnimation(anyFloat()); verify(game, never()).eliminate(any());
        assertTrue(logic.sidebar(victim).getFirst().contains("2"));
    }
    @Test void thirdHitEliminatesAndDisplaysOutTitle() {
        for (int i=0;i<3;i++) {time.addAndGet(350_000_000L);logic.throwBall(shooter);logic.onProjectileHit(hit());}
        verify(game).eliminate(victim); verify(victim).showTitle(any(net.kyori.adventure.title.Title.class));
    }
    @Test void teammateHitsDoNotScoreOrNotify() {
        var team = mock(GameTeam.class); when(game.teamOf(shooter)).thenReturn(team); when(game.teamOf(victim)).thenReturn(team);
        logic.throwBall(shooter);logic.onProjectileHit(hit());
        verify(game, never()).addScore(any(), anyInt()); verify(victim, never()).sendActionBar(any(net.kyori.adventure.text.Component.class));
    }
    @Test void ignoresUntrackedProjectiles() {
        logic.onProjectileHit(hit());verify(game, never()).addScore(any(), anyInt());
    }
    @Test void clearsVisibleCooldownOnCleanup() {
        logic.throwBall(shooter);logic.onCleanup();verify(shooter).setCooldown(Material.BLAZE_ROD, 0);
    }
    @Test void clampsArenaCooldownToUsableRange() {
        when(game.arena().settings().getInt("throw-cooldown-ticks", 7)).thenReturn(0);assertEquals(1,logic.throwCooldownTicks());
        when(game.arena().settings().getInt("throw-cooldown-ticks", 7)).thenReturn(999);assertEquals(40,logic.throwCooldownTicks());
    }
}
