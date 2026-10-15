package uk.co.playerready.pulsegames.core.setup;
import org.bukkit.*;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ArenaValidationTest {
    ArenaSetupManager.Session session(List<SetupRequirement> requirements) {
        World w=mock(World.class);when(w.getMinHeight()).thenReturn(-64);when(w.getMaxHeight()).thenReturn(320);
        var border=mock(WorldBorder.class);when(w.getWorldBorder()).thenReturn(border);when(border.isInside(any())).thenReturn(true);
        GameType game=mock(GameType.class);when(game.id()).thenReturn("test");when(game.setup()).thenReturn(requirements);when(game.modes()).thenReturn(List.of(uk.co.playerready.pulsegames.core.game.GameMode.ffa("solo","Solo",2,16)));
        var s=new ArenaSetupManager.Session(game,"test",w);s.lobby="0,81,0";s.spectator="0,90,0";s.spawns.add("0,81,0");s.spawns.add("3,81,0");s.minPlayers=2;s.maxPlayers=4;return s;
    }
    @Test void unloadedChunkProducesWarningWithoutLoadingIt(){var s=session(List.of());assertTrue(ArenaValidation.check(s).valid());verify(s.world,never()).getChunkAt(anyInt(),anyInt());}
    @Test void malformedAndNonfiniteCoordinatesBlockPublication(){var s=session(List.of());s.lobby="NaN,81,0";assertFalse(ArenaValidation.check(s).valid());s.lobby="1,2";assertFalse(ArenaValidation.check(s).valid());}
    @Test void missingNumberCannotBeHiddenByAnotherPrefixName(){var s=session(List.of(SetupRequirement.regions("checkpoint",2,"course")));s.regions.put("checkpoint1",new Cuboid(0,80,0,2,82,2));s.regions.put("checkpoint3",new Cuboid(3,80,0,5,82,2));assertFalse(ArenaValidation.check(s).valid());}
    @Test void voidAboveSpawnBlocksPublication(){var s=session(List.of());s.settings.put("void-y",90);assertFalse(ArenaValidation.check(s).valid());}
    @Test void unknownModeAndImpossibleTeamCountBlockPublication(){var s=session(List.of());s.modes=List.of("typo");assertFalse(ArenaValidation.check(s).valid());s.modes=List.of();when(s.game.modes()).thenReturn(List.of(uk.co.playerready.pulsegames.core.game.GameMode.teams("duos","Duos",4,8,2)));s.settings.put("team-count",3);assertFalse(ArenaValidation.check(s).valid());}
    @Test void missingBedAndGeneratorAreReportedForEveryTeam(){var s=session(List.of());when(s.game.id()).thenReturn("bedwars");when(s.game.modes()).thenReturn(List.of(uk.co.playerready.pulsegames.core.game.GameMode.teams("solo","Solo",2,16,1)));var r=ArenaValidation.check(s);assertFalse(r.valid());assertTrue(r.issues().stream().anyMatch(i->i.message().contains("generator2")));}
    @Test void duelsCannotPublishThreeTeamsEvenWithThreeSpawns(){var s=session(List.of());when(s.game.id()).thenReturn("duels");s.spawns.add("6,81,0");when(s.game.modes()).thenReturn(List.of(uk.co.playerready.pulsegames.core.game.GameMode.teams("doubles","Doubles",2,4,2)));s.settings.put("team-count",3);assertFalse(ArenaValidation.check(s).valid());s.settings.remove("team-count");assertTrue(ArenaValidation.check(s).valid());}
    @Test void kartBotRouteRequiredOnlyForBotEnabledMapsOrAuthoredLines(){
        var s=session(List.of());when(s.game.id()).thenReturn("karts");
        assertTrue(ArenaValidation.checkConfiguration(s).valid());
        s.settings.put("bots-enabled",true);assertFalse(ArenaValidation.checkConfiguration(s).valid());
        s.settings.put("bots-enabled",false);s.settings.put("bot-waypoint1","0,81,0");
        assertFalse(ArenaValidation.checkConfiguration(s).valid());
    }
}
