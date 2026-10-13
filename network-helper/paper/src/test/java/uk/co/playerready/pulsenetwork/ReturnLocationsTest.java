package uk.co.playerready.pulsenetwork;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReturnLocationsTest {
    @TempDir Path dir;
    @Test void legacyLocationLoadsWithoutLoadingItsWorld() throws Exception {
        UUID player=UUID.randomUUID();Path file=dir.resolve("returns.yml");
        Files.writeString(file,player+":\n  ==: org.bukkit.Location\n  world: unloaded_build\n  x: 1.5\n  y: 81\n  z: -4.5\n  yaw: 90\n  pitch: 0\n");
        var store=ReturnLocations.load(file);assertEquals("unloaded_build",store.get(player).world());assertEquals(81,store.get(player).y());
        store.save(file);assertFalse(Files.readString(file).contains("==:"));assertEquals(store.get(player),ReturnLocations.load(file).get(player));
    }
    @Test void invalidRowDoesNotDiscardOtherPlayers() throws Exception {
        UUID player=UUID.randomUUID();Path file=dir.resolve("returns.yml");Files.writeString(file,"bad: {world: nope}\n"+player+": {world: survival, x: 2, y: 80, z: 3, yaw: 0, pitch: 0}\n");
        assertNotNull(ReturnLocations.load(file).get(player));
    }
    @Test void missingOrReplacedWorldCannotResolve() {
        var p=new ReturnLocations.Position("survival",UUID.randomUUID(),0,80,0,0,0);assertNull(p.in(null));
        World w=mock(World.class);when(w.getName()).thenReturn("survival");when(w.getUID()).thenReturn(UUID.randomUUID());assertNull(p.in(w));
    }
    @Test void nonfiniteCoordinatesRejected(){assertThrows(IllegalArgumentException.class,()->new ReturnLocations.Position("world",null,Double.NaN,80,0,0,0));}
    @Test void parseFailureDoesNotOverwriteSource()throws Exception{
        Path file=dir.resolve("returns.yml");Files.writeString(file,"broken: [oops");assertThrows(java.io.IOException.class,()->ReturnLocations.load(file));assertEquals("broken: [oops",Files.readString(file));
    }
}
