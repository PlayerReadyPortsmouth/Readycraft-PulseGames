package uk.co.playerready.pulsegames.core.seasonal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class ChristmasTestMapExportTest {
    @TempDir Path directory;
    @Test void exportsBothMapsWithAllMarkerTextsAndDeterministicNbt() throws Exception {
        ChristmasTestMapExport.export(directory);
        for(String name:new String[]{"pulse_present_panic_test","pulse_grinch_hunt_test"}) {
            byte[] before=Files.readAllBytes(directory.resolve(name+".schem"));
            try(var input=new GZIPInputStream(new ByteArrayInputStream(before))) {
                String nbt=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1);
                assertTrue(nbt.contains("Schematic"));assertTrue(nbt.contains("[pulse]"));
                assertTrue(nbt.contains(name.contains("present")?"pp.sleigh.gold.launch":"gh.cave.back"));
                assertTrue(nbt.contains("AurorasChaos (on behalf of Player Ready)"));
            }
            ChristmasTestMapExport.export(directory);assertArrayEquals(before,Files.readAllBytes(directory.resolve(name+".schem")));
        }
        assertEquals(89,Files.readAllLines(directory.resolve("pulse_present_panic_test-markers.csv")).size());
        assertEquals(64,Files.readAllLines(directory.resolve("pulse_grinch_hunt_test-markers.csv")).size());
    }
}
