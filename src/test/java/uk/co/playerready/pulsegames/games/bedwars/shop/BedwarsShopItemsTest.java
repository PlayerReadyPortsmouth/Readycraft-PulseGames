package uk.co.playerready.pulsegames.games.bedwars.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The shipped catalogue: every new entry parses, ids are unique, tabs fit, currencies exist. */
class BedwarsShopItemsTest {
    private static final ShopCatalogue SHOP = ShopCatalogue.loadBundled();

    @Test void idsAreUniqueAndCurrenciesAreOnlyTheThreeWeHave() {
        Set<String> ids = new HashSet<>();
        for (var item : SHOP.items()) {
            assertTrue(ids.add(item.id()), "duplicate " + item.id());
            assertTrue(Set.of(Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND).contains(item.currency()), item.id());
            assertFalse(item.name().isBlank());
            assertFalse(item.blurb().isBlank(), item.id() + " needs lore");
        }
        for (var u : SHOP.upgrades()) assertTrue(ids.add("upgrade:" + u.id()), u.id());
    }

    @Test void theStandardKitAndTheFunkyItemsAreAllThere() {
        for (String id : new String[]{"tnt", "water_bucket", "ladder", "obsidian", "end_stone", "bridge_egg", "magic_milk", "potion_invis",
                "potion_jump", "potion_speed", "knockback_stick", "sponge", "dream_defender", "bed_bug", "popup_tower"})
            assertNotNull(SHOP.byId(id), id);
        for (String id : new String[]{"grappling_hook", "bounce_pad", "ice_road", "smoke_bomb", "freeze_ball", "rocket_boots",
                "recall_totem", "shield_wall", "honey_trap", "decoy_dummy"}) {
            var item = SHOP.byId(id);
            assertNotNull(item, id);
            assertEquals(ShopCatalogue.Category.FUNKY, item.category());
            assertTrue(item.isNew(), id + " is marked new");
            assertTrue(item.cooldown() > 0, id + " has a cooldown");
        }
        assertEquals(30, SHOP.byId("potion_invis").potion().seconds());
        assertEquals(5, SHOP.byId("potion_jump").potion().level());
        assertEquals(45, SHOP.byId("potion_speed").potion().seconds());
        assertEquals(2, SHOP.byId("potion_speed").potion().level());
        var effects = SHOP.upgrades().stream().map(ShopCatalogue.Upgrade::effect).collect(java.util.stream.Collectors.toSet());
        assertTrue(effects.containsAll(Set.of(ShopCatalogue.Effect.SHARPNESS, ShopCatalogue.Effect.PROTECTION, ShopCatalogue.Effect.HASTE,
                ShopCatalogue.Effect.FORGE, ShopCatalogue.Effect.HEAL_POOL, ShopCatalogue.Effect.TRAP_ALARM,
                ShopCatalogue.Effect.TRAP_SLOW, ShopCatalogue.Effect.TRAP_FATIGUE)));
    }

    @Test void everyShelfFitsAndTheTabRowHoldsNineTabs() {
        for (var c : ShopCatalogue.Category.values())
            assertTrue(SHOP.inCategory(c).size() <= ShopCatalogue.PAGE_CAPACITY, c.name());
        long shelves = java.util.Arrays.stream(ShopCatalogue.Category.values()).filter(c -> c != ShopCatalogue.Category.QUICK_BUY).count();
        assertEquals(8, shelves);
        assertTrue(shelves + 1 <= uk.co.playerready.pulsegames.core.ui.shop.ShopLayout.TABS, "eight shelves plus Team Upgrades");
        assertTrue(SHOP.upgrades().size() <= uk.co.playerready.pulsegames.core.ui.shop.ShopLayout.SPACED_CAPACITY);
        assertTrue(SHOP.quickBuy().size() <= ShopCatalogue.QUICK_CAPACITY);
    }

    @Test void anUnknownCustomBehaviourIsRejected() {
        String yaml = "items:\n  - {id: a, category: funky, material: DIRT, currency: iron, cost: 1, name: A, custom: nope}\n";
        assertThrows(IllegalArgumentException.class, () -> ShopCatalogue.parse(new StringReader(yaml)));
        assertDoesNotThrow(() -> ShopCatalogue.parse(new StringReader(yaml.replace("nope", "ice_road"))));
    }

    @Test void confirmUsesThePriceThreshold() {
        assertTrue(BedwarsShopMenu.needsConfirm(500, 500));
        assertFalse(BedwarsShopMenu.needsConfirm(120, 500));
        assertTrue(BedwarsShopMenu.needsConfirm(120, 100));
    }
}
