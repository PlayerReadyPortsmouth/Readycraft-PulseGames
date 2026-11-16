package uk.co.playerready.pulsegames.core.seasonal;
import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MarkerContractTest {
    @Test void flatMapsMeetTheExactBuilderContract() {
        for(String game:List.of("presentpanic","grinchhunt")) {
            var m=ChristmasTestMaps.markers(game);
            assertEquals(List.of(),MarkerContract.problems(game,m.stream().map(ChristmasTestMaps.Marker::key).toList()));
            assertEquals(m.size(),m.stream().map(p->p.x()+","+p.y()+","+p.z()).distinct().count(),"overlapping signs");
        }
    }
    @Test void duplicateSingletonAndMissingPairAreRejected() {
        var keys=new ArrayList<>(ChristmasTestMaps.markers("grinchhunt").stream().map(ChristmasTestMaps.Marker::key).toList());
        keys.add("gh.tree.1");keys.remove("gh.path.cleared.1.max");
        var problems=MarkerContract.problems("grinchhunt",keys);
        assertTrue(problems.stream().anyMatch(p->p.contains("Duplicate marker: gh.tree.1")));
        assertTrue(problems.stream().anyMatch(p->p.contains("gh.path.cleared.1.max")));
    }
    @Test void nodeGapsTyposAndFacingAreRejected() {
        var keys=new ArrayList<>(ChristmasTestMaps.markers("presentpanic").stream().map(ChristmasTestMaps.Marker::key).toList());
        keys.remove("pp.belt.1.2");keys.add("pp.belt.1.90");
        assertTrue(MarkerContract.problems("presentpanic",keys).stream().anyMatch(p->p.contains("pp.belt.1.2")));
        assertFalse(MarkerContract.validKey("presentpanic","pp.spawn.yellow"));
        assertFalse(MarkerContract.validKey("grinchhunt","GH.tree.1"));
        assertThrows(IllegalArgumentException.class,()->MarkerContract.yaw("north"));
        assertEquals(-90,MarkerContract.yaw("e"));
    }
    @Test void yamlRoundTripRetainsDotsAndRepeatedSpawns() throws Exception {
        List<Map<String,Object>> rows=ChristmasTestMaps.markers("presentpanic").stream()
                .map(m->Map.<String,Object>of("key",m.key(),"location",m.x()+","+m.y()+","+m.z())).toList();
        var y=MarkerImporter.definition("presentpanic","xm_pp",rows);
        YamlConfiguration read=new YamlConfiguration();read.loadFromString(y.saveToString());
        assertEquals(6,read.getMapList("settings.markers").stream().filter(m->m.get("key").equals("pp.spawn.red")).count());
        assertEquals(4,read.getStringList("spawns").size());
    }
    @Test void numericPathOrderIsNotLexical() {
        var keys=new ArrayList<>(List.of("pp.belt.1.10","pp.belt.1.2","pp.belt.1.1"));keys.sort(ChristmasMarkers::naturalCompare);
        assertEquals(List.of("pp.belt.1.1","pp.belt.1.2","pp.belt.1.10"),keys);
    }
}
