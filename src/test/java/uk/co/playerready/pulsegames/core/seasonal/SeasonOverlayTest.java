package uk.co.playerready.pulsegames.core.seasonal;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SeasonOverlayTest {
    @Test void disabledOverlayHasNoOffer() {assertNull(SeasonOverlay.Pack.parse(null));assertNull(SeasonOverlay.Pack.parse(new MemoryConfiguration()));}
    @Test void identityBindsToContentHash() {
        var a=new MemoryConfiguration();a.set("url","https://example.test/christmas.zip");a.set("sha1","a".repeat(40));
        var original=SeasonOverlay.Pack.parse(a);assertEquals(original,SeasonOverlay.Pack.parse(a));
        a.set("sha1","b".repeat(40));assertNotEquals(original.id(),SeasonOverlay.Pack.parse(a).id());
    }
    @Test void badOrUnboundOverlayFailsClosed() {
        var c=new MemoryConfiguration();c.set("url","https://example.test/pack.zip");
        assertThrows(IllegalArgumentException.class,()->SeasonOverlay.Pack.parse(c));
        c.set("sha1","a".repeat(40));c.set("url","http://example.test/pack.zip");
        assertThrows(IllegalArgumentException.class,()->SeasonOverlay.Pack.parse(c));
    }
}
