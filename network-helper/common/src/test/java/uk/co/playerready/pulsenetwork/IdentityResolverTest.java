package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IdentityResolverTest {
    final UUID me = UUID.randomUUID();
    static IdentityResolver.Meta meta(String... kv) {
        var m = new HashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return (id, key) -> m.get(key);
    }
    NameStyle.Identity resolve(IdentityResolver.Meta meta, Integer level) { return IdentityResolver.resolve(me, "Aurora", meta, level, NameStyle.Tags.DEFAULT); }

    @Test void readsRoleStyleAndLevel() {
        var i = resolve(meta("pulse.role", "Dev", "pulse.namestyle", " rainbow "), 12);
        assertEquals(new NameStyle.Identity("Aurora", "dev", "rainbow", 12), i);
    }
    @Test void unknownOrMissingValuesAreDropped() {
        assertEquals(new NameStyle.Identity("Aurora", null, null, null), resolve(meta(), null));
        assertEquals(new NameStyle.Identity("Aurora", null, null, null), resolve(meta("pulse.role", "owner", "pulse.namestyle", "<red>x"), 0));
        assertNull(resolve(meta(), 10_000).level());
    }
    @Test void lookupFailureIsSafeWhenLuckPermsIsAbsent() {
        assertNull(new LuckPermsMeta().value(me, "pulse.role"));
    }
    @Test void adminWithWildcardStillShowsAdminAndVenueShowsVenueOnly() {
        // Permissions never decide the tag: only the role meta does.
        var admin = NameStyle.render(resolve(meta("pulse.role", "admin"), null), false, 0);
        assertEquals("[ADMIN] Aurora", NameStyleTest.text(admin));
        var venue = NameStyle.render(resolve(meta("pulse.role", "venue", "pulse.venue", "ports"), null), false, 0);
        assertEquals("[VENUE] Aurora", NameStyleTest.text(venue));
    }
}
