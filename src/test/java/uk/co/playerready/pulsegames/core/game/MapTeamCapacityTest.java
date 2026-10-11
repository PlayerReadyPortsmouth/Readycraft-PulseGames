package uk.co.playerready.pulsegames.core.game;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapTeamCapacityTest {
    GameInstance game(int spawns, int beds, GameMode mode, MemoryConfiguration settings) {
        var arena = new Arena("map", "bedwars", "Map", "map", List.of(), 2, 128,
                "0,80,0", null, Collections.nCopies(spawns, "0,80,0"), Map.of(), settings);
        var type = mock(GameType.class);
        var logic = mock(MiniGame.class);
        when(logic.maxTeams()).thenReturn(beds);
        when(logic.arenaPlayerCap()).thenReturn(Integer.MAX_VALUE);
        when(type.createLogic(any())).thenReturn(logic);
        return new GameInstance(mock(PulseGamesPlugin.class), type, mode, arena);
    }
    @Test void soloBedwarsUsesSeparateOnePlayerTeams() {
        var mode = GameMode.teams("solo", "Solo", 2, 32, 1);
        assertTrue(mode.isTeams());
        var game = game(6, 6, mode, new MemoryConfiguration());
        assertEquals(6, game.teamCount()); assertEquals(6, game.maxPlayers());
    }
    @Test void basesAndSpawnsBoundCustomTeamCount() {
        var settings = new MemoryConfiguration(); settings.set("team-count", 8);
        var game = game(6, 4, GameMode.teams("triples", "Triples", 6, 96, 3), settings);
        assertEquals(4, game.teamCount()); assertEquals(12, game.maxPlayers());
    }
    @Test void sameMapHasIndependentModeLimits() {
        var settings = new MemoryConfiguration(); settings.set("team-count", 6);
        settings.set("modes.duos.team-count", 3); settings.set("modes.duos.max-players", 6);
        settings.set("modes.duos.min-players", 6);
        var duos = game(6, 6, GameMode.teams("duos", "Duos", 4, 64, 2), settings);
        var quads = game(6, 6, GameMode.teams("quads", "Quads", 8, 128, 4), settings);
        assertEquals(6, duos.minPlayers()); assertEquals(6, duos.maxPlayers());
        assertEquals(24, quads.maxPlayers());
    }
    @Test void missingBasesDoNotInventTwoTeams() {
        var game = game(4, 1, GameMode.teams("duos", "Duos", 4, 64, 2), new MemoryConfiguration());
        assertEquals(1, game.teamCount()); assertEquals(2, game.maxPlayers());
    }
    @Test void combatVariantsKeepBaseRulesAndSupportAllTeamSizes() {
        var type = new GameType("duels", "Duels", "", org.bukkit.Material.IRON_SWORD,
                List.of(GameMode.ffa("classic", "Classic", 2, 2), GameMode.ffa("op", "OP", 2, 2), GameMode.ffa("sumo", "Sumo", 2, 2)),
                g -> mock(MiniGame.class), List.of(), false);
        assertEquals(12, type.modes().size());
        assertEquals(2, type.mode("classic-doubles").teamSize());
        assertEquals(3, type.mode("op-triples").teamSize());
        assertEquals(4, type.mode("sumo-squads").teamSize());
        assertEquals("sumo", type.mode("sumo-squads").rulesId());
        assertEquals(8, type.mode("sumo-squads").maxPlayers());
    }
}
