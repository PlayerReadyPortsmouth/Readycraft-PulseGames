package uk.co.playerready.pulsegames.games.readyplayerz;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZShopCatalogueTest {
    @Test void everyShopLineIsOnExactlyOneShelf() {
        List<Integer> seen = new ArrayList<>();
        for (ZShelf shelf : ZShelf.values()) seen.addAll(shelf.slots);
        assertEquals(seen.size(), new HashSet<>(seen).size(), "a line is on two shelves");
        for (int i = 0; i < ReadyPlayerZGame.UPGRADES.size(); i++) assertTrue(seen.contains(i), "line " + i + " has no shelf");
        assertTrue(seen.contains(ReadyPlayerZGame.BOX_SLOT));
        assertTrue(seen.contains(ReadyPlayerZGame.PAP_SLOT));
        assertEquals(ReadyPlayerZGame.UPGRADES.size() + 2, seen.size());
        assertTrue(ZShelf.values().length <= 9, "tabs must fit the tab row");
    }

    @Test void pricesAndWordsAreKeptAndChildFriendly() {
        var ups = ReadyPlayerZGame.UPGRADES;
        assertEquals(11, ups.size());
        assertEquals(75, ups.get(6).cost());           // Heal Up: bots and tests buy slot 6
        assertEquals("Heal Up", ups.get(6).name());
        for (var u : ups) {
            assertTrue(u.cost() > 0, u.name());
            assertFalse(u.blurb().isBlank(), u.name());
            assertTrue(u.blurb().length() <= 60, u.name() + " blurb will wrap too far");
        }
        assertEquals(5000, ZMysteryBox.PACK_A_PUNCH_PRICE);
    }

    @Test void onlyPerksCanBeActiveAndEachHasAnEffect() {
        for (int slot : ZShelf.PERKS.slots) assertNotNull(ReadyPlayerZGame.UPGRADES.get(slot).perk(), "perk " + slot);
        for (int slot : ZShelf.WEAPONS.slots) assertNull(ReadyPlayerZGame.UPGRADES.get(slot).perk());
        for (int slot : ZShelf.GEAR.slots) assertNull(ReadyPlayerZGame.UPGRADES.get(slot).perk());
    }

    @Test void bagSpaceIsOnlyCheckedForTheItemsPurchaseChecks() {
        assertTrue(ZShelf.needsBagSpace(0));
        assertTrue(ZShelf.needsBagSpace(4));
        assertFalse(ZShelf.needsBagSpace(5));
        assertFalse(ZShelf.needsBagSpace(ReadyPlayerZGame.BOX_SLOT));
    }
}
