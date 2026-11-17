package uk.co.playerready.pulsegames.games.christmas;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static uk.co.playerready.pulsegames.games.christmas.PresentRules.*;

class ChristmasActionsTest {
    final World world = mock(World.class);
    GameInstance game() {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.state()).thenReturn(GameState.RUNNING); when(game.isActive(any())).thenReturn(true);
        when(game.world()).thenReturn(world); when(game.arena().settings()).thenReturn(new MemoryConfiguration());
        return game;
    }
    Player player(int team) {
        Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenAnswer(i -> new Location(world, 0, 65, 0));
        when(player.getWorld()).thenReturn(world); when(player.getInventory()).thenReturn(mock(PlayerInventory.class)); return player;
    }
    @SuppressWarnings("unchecked") <T> T field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return (T)field.get(owner);
    }
    void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    void invoke(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(owner, args);
    }
    @Test void sleighKeepsUnfinishedCargoAndPaysOnlyForWrappedPresents() throws Exception {
        GameInstance game = game(); Player player = player(0);
        PresentPanicGame logic = new PresentPanicGame(game);
        set(logic, "visuals", mock(ChristmasVisuals.class));
        Map<UUID, Deque<Present>> carrying = field(logic, "carrying");
        Present unfinished = new Present(Colour.RED, null, false, 0, false);
        carrying.put(player.getUniqueId(), new ArrayDeque<>(List.of(unfinished)));
        invoke(logic, "deposit", new Class<?>[]{Player.class}, player);
        assertEquals(List.of(unfinished), new ArrayList<>(carrying.get(player.getUniqueId())));
        verify(game, never()).addScore(any(), anyInt());
        carrying.get(player.getUniqueId()).add(new Present(Colour.RED, Colour.RED, true, 0, false));
        try (var ignored = mockConstruction(ItemStack.class)) {
            invoke(logic, "deposit", new Class<?>[]{Player.class}, player);
        }
        verify(game).addScore(player, 6);
        assertEquals(List.of(unfinished), new ArrayList<>(carrying.get(player.getUniqueId())));
        Map<UUID, Integer> delivered = field(logic, "delivered"); assertEquals(1, delivered.get(player.getUniqueId()));
    }

    @Test void villagerBotsDoNotChaseInvisibleGrinchPositions() throws Exception {
        GameInstance game = game(); Player thief = player(0), villager = player(1);
        when(game.alivePlayers()).thenReturn(List.of(thief, villager));
        when(villager.canSee(thief)).thenReturn(true); when(villager.hasLineOfSight(thief)).thenReturn(true);
        GrinchHuntGame logic = new GrinchHuntGame(game);
        Set<UUID> grinches = field(logic, "grinches"); grinches.add(thief.getUniqueId());
        when(thief.isInvisible()).thenReturn(true);
        assertNull(logic.botDestination(villager));
        when(thief.isInvisible()).thenReturn(false);
        assertEquals(thief.getLocation(), logic.botDestination(villager));
        when(villager.hasLineOfSight(thief)).thenReturn(false);
        assertNull(logic.botDestination(villager));
    }
    @Test void trackedSnowballDropsTopOnlyAndRivalCanStealBeforeOriginalTeamReclaims() throws Exception {
        GameInstance game = game(); Player victim = player(0), rival = player(1);
        when(rival.getLocation()).thenAnswer(i -> new Location(world, 1, 65, 0));
        when(game.alivePlayers()).thenReturn(List.of(victim, rival));
        when(game.teamOf(victim)).thenReturn(new GameTeam(0)); when(game.teamOf(rival)).thenReturn(new GameTeam(1));
        PresentPanicGame logic = new PresentPanicGame(game);
        ChristmasVisuals visuals = mock(ChristmasVisuals.class); ItemDisplay drop = mock(ItemDisplay.class);
        when(drop.isValid()).thenReturn(true); when(drop.getLocation()).thenAnswer(i -> new Location(world, 0, 65.3, 0));
        when(visuals.spawn(any(), anyString(), any(), anyFloat())).thenReturn(drop); set(logic, "visuals", visuals);
        Map<UUID, Deque<Present>> carrying = field(logic, "carrying");
        Present present = new Present(Colour.RED, Colour.RED, true, 0, false);
        carrying.put(victim.getUniqueId(), new ArrayDeque<>(List.of(present)));
        Snowball snowball = mock(Snowball.class); when(snowball.getUniqueId()).thenReturn(UUID.randomUUID()); when(snowball.getShooter()).thenReturn(rival);
        Map<UUID, Snowball> balls = field(logic, "snowballs"); balls.put(snowball.getUniqueId(), snowball);
        ProjectileHitEvent hit = mock(ProjectileHitEvent.class); when(hit.getEntity()).thenReturn(snowball); when(hit.getHitEntity()).thenReturn(victim);
        try (var ignored = mockConstruction(ItemStack.class)) {
            logic.onProjectileHit(hit); logic.onProjectileHit(hit);
            assertTrue(carrying.get(victim.getUniqueId()).isEmpty());
            List<?> drops = field(logic, "toys"); assertEquals(1, drops.size());
            invoke(logic, "step", new Class<?>[0]);
            assertTrue(carrying.get(victim.getUniqueId()).isEmpty());
            assertEquals(1, carrying.get(rival.getUniqueId()).size());
            assertTrue(carrying.get(rival.getUniqueId()).peekLast().stolen());
            assertEquals(7, score(carrying.get(rival.getUniqueId()).peekLast(), false));
        }
        verify(victim, times(1)).setVelocity(any());
    }
    @Test void wrappingHoldCancelsWhenReleasedOrWhenLeavingTheBench() throws Exception {
        GameInstance game = game(); Player player = player(0);
        game.arena().settings().set("markers", List.of(Map.of("key", "pp.wrap.red.1", "location", "0,65,0")));
        PresentPanicGame logic = new PresentPanicGame(game);
        Map<UUID, Deque<Present>> carrying = field(logic, "carrying");
        carrying.put(player.getUniqueId(), new ArrayDeque<>(List.of(new Present(Colour.RED, null, false, 0, false))));
        for (int i=0; i<10; i++) invoke(logic, "processWrap", new Class<?>[]{Player.class, boolean.class}, player, true);
        Map<?, ?> progress = field(logic, "wrapping"); assertEquals(1, progress.size());
        invoke(logic, "processWrap", new Class<?>[]{Player.class, boolean.class}, player, false);
        assertTrue(progress.isEmpty());
        invoke(logic, "processWrap", new Class<?>[]{Player.class, boolean.class}, player, true); assertEquals(1, progress.size());
        when(player.getLocation()).thenReturn(new Location(world, 10, 65, 0));
        invoke(logic, "processWrap", new Class<?>[]{Player.class, boolean.class}, player, true);
        assertTrue(progress.isEmpty()); assertNull(carrying.get(player.getUniqueId()).peekLast().paper());
    }
    @Test void villagersMustKeepOneTargetInRangeAndKeepHoldingToTag() throws Exception {
        GameInstance game = game(); Player thief = player(0), villager = player(1);
        when(game.alivePlayers()).thenReturn(List.of(thief, villager)); when(villager.isSneaking()).thenReturn(true);
        when(villager.hasLineOfSight(thief)).thenReturn(true);
        GrinchHuntGame logic = new GrinchHuntGame(game);
        Set<UUID> grinches = field(logic, "grinches"); grinches.add(thief.getUniqueId());
        invoke(logic, "stepVillager", new Class<?>[]{Player.class}, villager);
        Map<?, ?> progress = field(logic, "holds"); assertEquals(1, progress.size());
        when(villager.isSneaking()).thenReturn(false);
        invoke(logic, "stepVillager", new Class<?>[]{Player.class}, villager); assertTrue(progress.isEmpty());
        when(villager.isSneaking()).thenReturn(true); invoke(logic, "stepVillager", new Class<?>[]{Player.class}, villager);
        assertEquals(1, progress.size()); when(thief.getLocation()).thenReturn(new Location(world, 10, 65, 0));
        invoke(logic, "stepVillager", new Class<?>[]{Player.class}, villager); assertTrue(progress.isEmpty());
        verify(game, never()).end(anyList(), anyString());
    }
}
