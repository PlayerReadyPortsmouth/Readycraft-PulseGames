package uk.co.playerready.pulsegames.core.setup;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.GameRegistry;
import uk.co.playerready.pulsegames.core.seasonal.ChristmasTestMaps;
import uk.co.playerready.pulsegames.games.GameCatalog;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChristmasSetupTest {
    @Test void bothCataloguesValidateImportedMarkersAndRejectGenericSpawnOnlyMaps() {
        var registry = new GameRegistry(); GameCatalog.registerAll(registry);
        for (String id : List.of("presentpanic", "grinchhunt")) {
            var type = registry.get(id);
            var settings = new YamlConfiguration(); settings.set("team-count",4);
            var arena = new Arena("test",id,"Test","test",List.of(type.defaultMode().id()),2,24,
                    "0,65,0","0,70,0",Collections.nCopies(12,"0,65,0"),Map.of(),settings);
            var missing = SavedArenaValidation.check(type,arena,null);
            assertFalse(missing.configurationValid());
            assertEquals(SavedArenaValidation.Status.MISSING, missing.checklist().stream().filter(c -> c.key().equals("markers")).findFirst().orElseThrow().status());
            settings.set("markers",ChristmasTestMaps.markers(id).stream()
                    .map(m -> Map.of("key",m.key(),"location",m.x()+","+m.y()+","+m.z())).toList());
            var valid = SavedArenaValidation.check(type,arena,null);
            assertTrue(valid.configurationValid(), valid.validation().issues().toString());
            assertEquals(SavedArenaValidation.Status.RECORDED, valid.checklist().stream().filter(c -> c.key().equals("markers")).findFirst().orElseThrow().status());
        }
    }
}
