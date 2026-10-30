package uk.co.playerready.pulsegames.core.progression;

import org.junit.jupiter.api.Test;
import org.bukkit.Material;
import uk.co.playerready.pulsegames.core.economy.Cosmetic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NameStyleGrantsTest {
    private static final UUID ID = UUID.randomUUID();
    private static final List<Cosmetic> REWARDS = List.of(style("red", 3), style("orange", 6), style("sunset", 30),
            new Cosmetic("tr-x", Cosmetic.Category.TRAIL, "Trail", Material.STICK, 0, "d", 5, null));

    private static Cosmetic style(String id, int level) {
        return new Cosmetic("ns-" + id, Cosmetic.Category.NAME_STYLE, id, Material.NAME_TAG, 0, "d", level, null);
    }

    private final List<String> calls = new ArrayList<>();
    private final Set<String> held = new HashSet<>();

    private NameStyleGrants grants(boolean on) {
        return new NameStyleGrants((player, perm) -> { assertEquals(ID, player); calls.add(perm); held.add(perm); }, () -> on);
    }

    @Test void levelUpGrantsOnlyTheStylesReached() {
        assertEquals(List.of("red"), grants(true).grant(ID, 3, held::contains, REWARDS));
        assertEquals(List.of("pulse.namestyle.red"), calls);
        assertEquals(List.of("orange"), grants(true).grant(ID, 7, held::contains, REWARDS));
        assertEquals(List.of("pulse.namestyle.red", "pulse.namestyle.orange"), calls);
    }

    @Test void catchUpGrantsEverythingOwedOnceAndIsIdempotent() {
        var g = grants(true);
        assertEquals(List.of("red", "orange", "sunset"), g.grant(ID, 40, held::contains, REWARDS));
        assertEquals(List.of(), g.grant(ID, 40, held::contains, REWARDS));
        assertEquals(3, calls.size());
    }

    @Test void nothingIsGrantedWhenTheKeyIsOffOrLuckPermsIsMissing() {
        assertEquals(List.of(), grants(false).grant(ID, 100, held::contains, REWARDS));
        assertEquals(List.of(), new NameStyleGrants(null, () -> true).grant(ID, 100, held::contains, REWARDS));
        assertTrue(calls.isEmpty());
    }
}
