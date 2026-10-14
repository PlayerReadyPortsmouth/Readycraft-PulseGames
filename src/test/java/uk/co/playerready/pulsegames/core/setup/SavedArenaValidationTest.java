package uk.co.playerready.pulsegames.core.setup;

import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SavedArenaValidationTest {
    private GameType type(String id, List<SetupRequirement> requirements, GameMode mode) {
        var type = mock(GameType.class);
        when(type.id()).thenReturn(id); when(type.setup()).thenReturn(requirements); when(type.modes()).thenReturn(List.of(mode));
        return type;
    }
    private Arena arena(String game, MemoryConfiguration settings, Map<String,Cuboid> regions) {
        return new Arena("courtyard", game, "Courtyard", "template", List.of("solo"), 2, 4,
                "0,80,0", "0,90,0", List.of("0,80,0", "5,80,0", "10,80,0", "15,80,0"), regions, settings);
    }
    @Test void unloadedSavedConfigurationCanPassWithoutClaimingHumanOrRuntimeApproval() {
        var report = SavedArenaValidation.check(type("test", List.of(SetupRequirement.spawns(2,"Player spawns")),
                GameMode.ffa("solo","Solo",2,4)), arena("test", new MemoryConfiguration(), Map.of()), null);
        assertTrue(report.configurationValid());
        assertTrue(report.validation().issues().stream().anyMatch(i -> !i.error() && i.message().contains("not loaded")));
        for (String key : List.of("physical", "runtime-paths", "visual", "java", "bedrock"))
            assertEquals(SavedArenaValidation.Status.UNVERIFIED, report.checklist().stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow().status());
        assertThrows(UnsupportedOperationException.class, () -> report.checklist().clear());
    }
    @Test void canonicalMissingObjectivesAndMalformedRouteRemainBlockingWithoutWorld() {
        var settings = new MemoryConfiguration(); settings.set("bot-route-main", List.of("0,80,0", "NaN,80,0"));
        var report = SavedArenaValidation.check(type("test", List.of(SetupRequirement.region("finish","Finish line")),
                GameMode.ffa("solo","Solo",2,4)), arena("test", settings, Map.of()), null);
        assertFalse(report.configurationValid());
        assertTrue(report.validation().issues().stream().anyMatch(i -> i.error() && i.message().contains("Missing region: finish")));
        assertTrue(report.validation().issues().stream().anyMatch(i -> i.error() && i.message().contains("invalid coordinates")));
        assertEquals(SavedArenaValidation.Status.MISSING, report.checklist().stream().filter(c -> c.key().equals("finish")).findFirst().orElseThrow().status());
    }
    @Test void bedwarsUsesCanonicalPerTeamBaseAndGeneratorRequirements() {
        var settings = new MemoryConfiguration(); settings.set("team-count", 4);
        settings.set("generator1", "0,80,0"); settings.set("generator2", "5,80,0");
        var report = SavedArenaValidation.check(type("bedwars", List.of(), GameMode.teams("solo","Solo",2,4,1)),
                arena("bedwars", settings, Map.of("bed1",new Cuboid(0,79,0,1,80,1), "bed2",new Cuboid(5,79,0,6,80,1))), null);
        assertFalse(report.configurationValid());
        assertTrue(report.validation().issues().stream().anyMatch(i -> i.message().contains("bed region for team 4")));
        assertTrue(report.validation().issues().stream().anyMatch(i -> i.message().contains("generator4")));
    }
    @Test void explicitLoadedWorldOnlyInspectsAlreadyLoadedChunks() {
        World world = mock(World.class); WorldBorder border = mock(WorldBorder.class);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        var report = SavedArenaValidation.check(type("test", List.of(), GameMode.ffa("solo","Solo",2,4)),
                arena("test",new MemoryConfiguration(),Map.of()),world);
        assertTrue(report.configurationValid());
        assertTrue(report.validation().issues().stream().anyMatch(i -> i.message().contains("visit this chunk")));
        verify(world,never()).getChunkAt(anyInt(),anyInt());
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    @Test void existingSetupStillRequiresLoadedWorld() {
        var type = type("test",List.of(),GameMode.ffa("solo","Solo",2,4));
        var session = new ArenaSetupManager.Session(type,"test",null);
        session.lobby="0,80,0"; session.spectator="0,90,0"; session.spawns.addAll(List.of("0,80,0","5,80,0"));
        assertFalse(ArenaValidation.check(session).valid());
        assertTrue(ArenaValidation.checkConfiguration(session).valid());
    }
    @Test void explicitCombatProfilesDoNotValidateModesTheMapDoesNotOffer() {
        var type = type("test",List.of(),GameMode.ffa("classic","Classic",2,4));
        when(type.modes()).thenReturn(List.of(GameMode.ffa("classic","Classic",2,4), GameMode.ffa("modern-classic","Modern",2,4)));
        var settings = new MemoryConfiguration();
        settings.set("combat-profiles-explicit", true); settings.set("modes.modern-classic.min-players", 100);
        var arena = new Arena("test","test","Test","template",List.of("classic"),2,4,
                "0,80,0","0,90,0",List.of("0,80,0","5,80,0"),Map.of(),settings);
        assertTrue(SavedArenaValidation.check(type,arena,null).configurationValid());
        settings.set("combat-profiles-explicit", false);
        assertFalse(SavedArenaValidation.check(type,arena,null).configurationValid());
    }

    @Test void rejectsWrongGameInsteadOfReportingUnrelatedRequirements() {
        assertThrows(IllegalArgumentException.class, () -> SavedArenaValidation.check(type("other",List.of(),
                GameMode.ffa("solo","Solo",2,4)),arena("test",new MemoryConfiguration(),Map.of()),null));
    }
}
