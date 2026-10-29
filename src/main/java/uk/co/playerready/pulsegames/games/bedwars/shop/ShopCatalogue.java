package uk.co.playerready.pulsegames.games.bedwars.shop;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Bedwars shop as data: items, categories, tier chains, auto-equip flags and team upgrades.
 * Parsed from {@code bedwars-shop.yml} so prices can be tuned without a release.
 * Author: AurorasChaos (on behalf of Player Ready).
 */
public final class ShopCatalogue {
    public static final int PAGE_CAPACITY = 18;
    public static final int QUICK_CAPACITY = 8;
    public static final int MAX_UPGRADES = 9;

    public enum Category {
        QUICK_BUY("Quick Buy", Material.NETHER_STAR),
        BLOCKS("Blocks", Material.BRICKS),
        MELEE("Melee", Material.IRON_SWORD),
        ARMOUR("Armour", Material.CHAINMAIL_CHESTPLATE),
        TOOLS("Tools", Material.IRON_PICKAXE),
        RANGED("Ranged", Material.BOW),
        POTIONS("Potions", Material.BREWING_STAND),
        UTILITY("Utility", Material.GOLDEN_APPLE),
        FUNKY("Funky", Material.FIREWORK_STAR),
        /** Cheap essentials for a team fighting back without a bed; shown only while the comeback is active. */
        COMEBACK("Comeback", Material.TOTEM_OF_UNDYING);

        public final String title;
        public final Material icon;
        Category(String title, Material icon) { this.title = title; this.icon = icon; }
    }

    public record PotionSpec(String effect, int seconds, int level) {}

    public record Item(String id, Category category, Material material, int amount, Material currency, int cost,
                       String name, String blurb, String group, int tier, boolean equip, boolean teamColour,
                       boolean unique, boolean quickBuy, PotionSpec potion, String custom, boolean isNew,
                       int cooldown, int seconds) {}

    public enum Effect {
        SHARPNESS, PROTECTION, HASTE, FORGE, HEAL_POOL, TRAP_ALARM, TRAP_SLOW, TRAP_FATIGUE;
        public boolean isTrap() { return name().startsWith("TRAP_"); }
    }

    /** Each entry of {@code costs} is the diamond price of that level. */
    public record Upgrade(String id, Effect effect, Material icon, String name, String blurb, List<Integer> costs) {
        public int maxLevel() { return costs.size(); }
    }

    private final List<Item> items;
    private final List<Upgrade> upgrades;
    private final Map<String, List<Material>> groups;

    private ShopCatalogue(List<Item> items, List<Upgrade> upgrades, Map<String, List<Material>> groups) {
        this.items = List.copyOf(items); this.upgrades = List.copyOf(upgrades); this.groups = groups;
    }

    public List<Item> items() { return items; }
    public List<Upgrade> upgrades() { return upgrades; }
    public Map<String, List<Material>> groups() { return groups; }

    public List<Item> inCategory(Category category) {
        if (category == Category.QUICK_BUY) return quickBuy();
        return items.stream().filter(i -> i.category() == category).toList();
    }

    public List<Item> quickBuy() { return items.stream().filter(Item::quickBuy).toList(); }

    public Item byId(String id) {
        for (Item item : items) if (item.id().equals(id)) return item;
        return null;
    }

    public Item byMaterial(Material material) {
        for (Item item : items) if (item.material() == material) return item;
        return null;
    }

    /** Materials in the same tier chain that sit below this item and are replaced when it is bought. */
    public Set<Material> lowerTiers(Item item) {
        if (item.group() == null || item.equip()) return Set.of();
        return uk.co.playerready.pulsegames.core.ui.shop.ShopRules.lowerTiers(groups.get(item.group()), item.tier());
    }

    /** Highest tier of the item's chain among the given materials, or -1 for none. */
    public int ownedTier(Item item, java.util.Collection<Material> held) {
        if (item.group() == null) return -1;
        return uk.co.playerready.pulsegames.core.ui.shop.ShopRules.ownedTier(groups.get(item.group()), held);
    }

    // ---- loading ---------------------------------------------------------------------

    /** The copy shipped inside the jar. */
    public static ShopCatalogue loadBundled() {
        try (InputStream in = ShopCatalogue.class.getResourceAsStream("/bedwars-shop.yml")) {
            if (in == null) throw new IllegalStateException("bedwars-shop.yml missing from the jar");
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @throws IllegalArgumentException with a plain explanation when the data is wrong. */
    public static ShopCatalogue parse(Reader reader) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(reader);
        Map<String, List<Material>> groups = new LinkedHashMap<>();
        var groupSection = yml.getConfigurationSection("groups");
        if (groupSection != null) for (String key : groupSection.getKeys(false)) {
            List<Material> chain = new ArrayList<>();
            for (String name : groupSection.getStringList(key)) chain.add(material(name, "group " + key));
            groups.put(key, List.copyOf(chain));
        }
        List<Item> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Map<?, ?> raw : yml.getMapList("items")) {
            Row row = new Row(raw);
            String id = row.str("id");
            if (!ids.add(id)) throw new IllegalArgumentException("duplicate item id " + id);
            Category category = category(row.str("category"), id);
            Material material = material(row.str("material"), "item " + id);
            int amount = row.integer("amount", 1), cost = row.integer("cost", 0);
            if (amount < 1 || amount > 64) throw new IllegalArgumentException("item " + id + " amount must be 1-64");
            if (cost < 1) throw new IllegalArgumentException("item " + id + " needs a cost of at least 1");
            String group = row.opt("group");
            int tier = -1;
            if (group != null) {
                List<Material> chain = groups.get(group);
                if (chain == null) throw new IllegalArgumentException("item " + id + " uses unknown group " + group);
                tier = chain.indexOf(material);
                if (tier < 0) throw new IllegalArgumentException("item " + id + " is not in group " + group);
            }
            String custom = row.opt("custom");
            if (custom != null && !uk.co.playerready.pulsegames.games.bedwars.BedwarsCustomItems.IDS.contains(custom))
                throw new IllegalArgumentException("item " + id + " uses unknown custom behaviour " + custom);
            boolean equip = row.bool("equip");
            if (equip && !material.name().endsWith("_CHESTPLATE"))
                throw new IllegalArgumentException("item " + id + ": equip items must be a chestplate");
            PotionSpec potion = null;
            if (raw.get("potion") instanceof Map<?, ?> p) {
                Row pr = new Row(p);
                potion = new PotionSpec(pr.str("effect"), pr.integer("seconds", 30), pr.integer("level", 1));
            }
            items.add(new Item(id, category, material, amount, currency(row.str("currency"), id), cost,
                    row.str("name"), row.opt("blurb") == null ? "" : row.opt("blurb"), group, tier, equip,
                    row.bool("team-colour"), row.bool("unique"), row.bool("quick-buy"), potion,
                    custom, row.bool("new"), Math.max(0, row.integer("cooldown", 0)), Math.max(0, row.integer("seconds", 0))));
        }
        if (items.isEmpty()) throw new IllegalArgumentException("no items");
        Map<Category, Integer> perCategory = new EnumMap<>(Category.class);
        for (Item item : items) {
            if (item.category() == Category.QUICK_BUY) throw new IllegalArgumentException("item " + item.id() + ": pick a real category");
            if (perCategory.merge(item.category(), 1, Integer::sum) > PAGE_CAPACITY)
                throw new IllegalArgumentException("too many items in " + item.category().name().toLowerCase(Locale.ROOT));
        }
        if (items.stream().filter(Item::quickBuy).count() > QUICK_CAPACITY)
            throw new IllegalArgumentException("quick-buy holds at most " + QUICK_CAPACITY + " items");
        List<Upgrade> upgrades = new ArrayList<>();
        for (Map<?, ?> raw : yml.getMapList("upgrades")) {
            Row row = new Row(raw);
            String id = row.str("id");
            Effect effect;
            try { effect = Effect.valueOf(row.str("effect").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("upgrade " + id + " has an unknown effect"); }
            List<Integer> costs = new ArrayList<>();
            if (raw.get("costs") instanceof List<?> list) for (Object o : list) {
                if (!(o instanceof Number n) || n.intValue() < 1) throw new IllegalArgumentException("upgrade " + id + " has a bad cost");
                costs.add(n.intValue());
            }
            if (costs.isEmpty()) throw new IllegalArgumentException("upgrade " + id + " needs costs");
            upgrades.add(new Upgrade(id, effect, material(row.str("icon"), "upgrade " + id), row.str("name"),
                    row.opt("blurb") == null ? "" : row.opt("blurb"), List.copyOf(costs)));
        }
        if (upgrades.size() > MAX_UPGRADES) throw new IllegalArgumentException("at most " + MAX_UPGRADES + " team upgrades");
        return new ShopCatalogue(items, upgrades, groups);
    }

    private static Category category(String name, String id) {
        for (Category c : Category.values())
            if (c != Category.QUICK_BUY && c.name().equalsIgnoreCase(name)) return c;
        throw new IllegalArgumentException("item " + id + " has unknown category " + name);
    }

    private static Material material(String name, String where) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException(where + ": unknown material " + name); }
    }

    private static Material currency(String name, String id) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "iron" -> Material.IRON_INGOT;
            case "gold" -> Material.GOLD_INGOT;
            case "diamond" -> Material.DIAMOND;
            default -> throw new IllegalArgumentException("item " + id + " has unknown currency " + name);
        };
    }

    private record Row(Map<?, ?> map) {
        String opt(String key) { Object v = map.get(key); return v == null ? null : v.toString(); }
        String str(String key) {
            String v = opt(key);
            if (v == null || v.isBlank()) throw new IllegalArgumentException("missing " + key);
            return v;
        }
        int integer(String key, int fallback) { return map.get(key) instanceof Number n ? n.intValue() : fallback; }
        boolean bool(String key) { return Boolean.TRUE.equals(map.get(key)); }
    }
}
