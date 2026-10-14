package uk.co.playerready.pulsegames.core.maps;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.mockito.Mockito.*;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import org.bukkit.command.CommandSender;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapReviewServiceTest {
    @TempDir Path directory;
    @Test void visualEvidenceNeedsSeparatePermissionAndDoesNotWriteWhenDenied() throws Exception {
        PulseGamesPlugin plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var type=mock(GameType.class);when(plugin.registry().get("prophunt")).thenReturn(type);
        CommandSender sender=mock(CommandSender.class);when(sender.hasPermission("pulsegames.admin")).thenReturn(true);
        var service=new MapReviewService(plugin);
        service.handle(sender,new String[]{"mark","prophunt","laboratory","visual","pass","Inspected all sides"});
        assertTrue(new MapReviewStore(directory.resolve("map-reviews.yml")).all().isEmpty());
        when(sender.hasPermission("pulsegames.maps.approve")).thenReturn(true);when(sender.getName()).thenReturn("Reviewer");
        when(plugin.arenas().byId("prophunt","laboratory")).thenReturn(null);
        service.handle(sender,new String[]{"mark","prophunt","laboratory","visual","pass","Inspected all sides"});
        assertEquals(MapReviewStore.Outcome.PASS,new MapReviewStore(directory.resolve("map-reviews.yml"))
                .get("prophunt","laboratory").marks().get(MapReviewStore.Check.VISUAL).outcome());
    }
    @Test void unconfiguredReportDoesNotLoadWorldsOrInferApprovals() {
        PulseGamesPlugin plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.registry().get("prophunt")).thenReturn(mock(GameType.class));
        when(plugin.arenas().byId("prophunt","laboratory")).thenReturn(null);
        var lines=new MapReviewService(plugin).report("prophunt","laboratory");
        assertTrue(lines.stream().anyMatch(line->line.startsWith("CONFIGURED: NO")));
        for(var check:MapReviewStore.Check.values()) assertTrue(lines.contains(check+": NOT RECORDED"));
        verify(plugin,never()).worlds();verify(plugin,never()).getServer();
    }
    @Test void captureMarkerChangesFingerprintAndUnreadableMarkerNeverReusesEvidence() throws Exception {
        PulseGamesPlugin plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.worlds().templateDir("lab-template")).thenReturn(directory.toFile());
        var service=new MapReviewService(plugin);
        var method=MapReviewService.class.getDeclaredMethod("currentFingerprint",Arena.class);method.setAccessible(true);
        Arena arena=arena(new MemoryConfiguration(),List.of("0,64,0"),Map.of());
        String legacy=(String)method.invoke(service,arena);
        Path marker=directory.resolve(".pulse-capture-revision");java.nio.file.Files.writeString(marker,"11111111-1111-1111-1111-111111111111");
        String first=(String)method.invoke(service,arena);assertNotEquals(legacy,first);assertEquals(first,method.invoke(service,arena));
        java.nio.file.Files.writeString(marker,"22222222-2222-2222-2222-222222222222");assertNotEquals(first,method.invoke(service,arena));
        java.nio.file.Files.writeString(marker,"x".repeat(101));
        assertNotEquals(method.invoke(service,arena),method.invoke(service,arena));
        java.nio.file.Files.delete(marker);
        java.nio.file.Files.createSymbolicLink(marker,directory.resolve("missing"));
        assertNotEquals(method.invoke(service,arena),method.invoke(service,arena));
        java.nio.file.Files.delete(marker);
        when(plugin.worlds().templateDir("lab-template")).thenReturn(directory.resolve("gone").toFile());
        assertNotEquals(legacy,method.invoke(service,arena));
        verify(plugin,never()).getServer();
    }
    private Arena arena(MemoryConfiguration settings,List<String> spawns,Map<String,Cuboid> regions) {
        return new Arena("laboratory","prophunt","Laboratory","lab-template",List.of("classic"),2,12,
                "0,64,0","0,80,0",spawns,regions,settings);
    }
    @Test void exportedHtmlEscapesTitlesAndUntrustedEvidence() {
        String html=MapReviewService.html("<img src=x onerror=alert(1)> & \"lab\"",List.of("</li><script>alert(1)</script>","A & B"));
        assertFalse(html.contains("<script>"));assertFalse(html.contains("<img"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt; &amp; &quot;lab&quot;"));
        assertTrue(html.contains("&lt;/li&gt;&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("A &amp; B"));assertTrue(html.contains("@media print"));
    }
    @Test void fingerprintsIgnoreConfigurationInsertionOrderButTrackNestedValues() {
        var a=new MemoryConfiguration();a.set("modes.classic.max-players",12);a.set("bot-hide-points",List.of("0,64,0","2,64,0"));
        var b=new MemoryConfiguration();b.set("bot-hide-points",List.of("0,64,0","2,64,0"));b.set("modes.classic.max-players",12);
        var spawns=List.of("0,64,0","4,64,0");String original=MapReviewService.fingerprint(arena(a,spawns,Map.of()));
        assertEquals(original,MapReviewService.fingerprint(arena(b,spawns,Map.of())));
        b.set("modes.classic.max-players",8);assertNotEquals(original,MapReviewService.fingerprint(arena(b,spawns,Map.of())));
        assertEquals(24,original.length());assertEquals("unconfigured",MapReviewService.fingerprint(null));
    }
    @Test void spawnOrderRegionsAndRoutesChangeFingerprint() {
        var settings=new MemoryConfiguration();var spawns=List.of("0,64,0","4,64,0");
        String original=MapReviewService.fingerprint(arena(settings,spawns,Map.of()));
        assertNotEquals(original,MapReviewService.fingerprint(arena(settings,List.of("4,64,0","0,64,0"),Map.of())));
        assertNotEquals(original,MapReviewService.fingerprint(arena(settings,spawns,Map.of("boundary",new Cuboid(0,60,0,20,90,20)))));
        settings.set("bot-route-main",List.of("0,64,0","3,64,0"));
        assertNotEquals(original,MapReviewService.fingerprint(arena(settings,spawns,Map.of())));
    }
    @Test void structuralFingerprintDoesNotConfuseDelimitedTextWithMultipleValues() {
        var first=new MemoryConfiguration();var second=new MemoryConfiguration();
        first.set("notes",List.of("a,b","c"));second.set("notes",List.of("a","b,c"));
        assertNotEquals(MapReviewService.fingerprint(arena(first,List.of("0,64,0"),Map.of())),
                MapReviewService.fingerprint(arena(second,List.of("0,64,0"),Map.of())));
    }
}
