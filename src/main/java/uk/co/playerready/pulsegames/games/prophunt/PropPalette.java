package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

/** Explicit native-block/model pairs: never infer a model's appearance from its ID. */
public final class PropPalette {
    public static final Set<Material> ALLOWED = Set.of(Material.HAY_BLOCK, Material.CRAFTING_TABLE,
            Material.BOOKSHELF, Material.MELON, Material.PUMPKIN, Material.OAK_LOG,
            Material.STONE, Material.BRICKS, Material.MOSS_BLOCK, Material.PLAYER_HEAD,
            Material.BARREL, Material.COBBLESTONE, Material.STONE_BRICKS, Material.DARK_OAK_LOG, Material.DARK_OAK_PLANKS, Material.SPRUCE_LOG, Material.SPRUCE_PLANKS, Material.OAK_PLANKS, Material.SMOOTH_SANDSTONE, Material.POLISHED_ANDESITE, Material.LOOM, Material.JUKEBOX, Material.NOTE_BLOCK, Material.SMOKER, Material.SMITHING_TABLE, Material.CARVED_PUMPKIN, Material.MOSSY_COBBLESTONE);
    public record Option(String name, Material material, String model) {
        public double height() { return material == Material.PLAYER_HEAD ? 0.5 : 1.0; }
    }
    private PropPalette() {}
    /** Absent override preserves old maps. An explicit invalid/empty override never widens to global. */
    public static List<Option> read(ConfigurationSection config, ConfigurationSection settings) {
        List<Option> global = read(config);
        if (settings == null || !settings.contains("prop-palette")) return global;
        if (!settings.isList("prop-palette")) throw new IllegalArgumentException("Arena prop-palette must be a list of materials.");
        if (settings.getList("prop-palette").stream().anyMatch(value -> !(value instanceof String)))
            throw new IllegalArgumentException("Arena prop-palette must contain only material names.");
        List<String> names = settings.getStringList("prop-palette");
        if (names.isEmpty() || names.size() > 27) throw new IllegalArgumentException("Arena prop-palette needs 1–27 supported materials.");
        Set<Material> selected = new LinkedHashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material == null || global.stream().noneMatch(p -> p.material() == material) || !selected.add(material))
                throw new IllegalArgumentException("Unsupported or duplicate arena prop: " + name);
        }
        return global.stream().filter(option -> selected.contains(option.material())).toList();
    }

    public static List<Option> read(ConfigurationSection config) {
        ConfigurationSection section = config.getConfigurationSection("content.prophunt.block-palette");
        if (section == null) throw new IllegalArgumentException("No block palette configured.");
        List<Option> result = new ArrayList<>();
        Set<Material> used = new HashSet<>();
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(section.getString(key + ".material", ""));
            String model = section.getString(key + ".model", "");
            String name = section.getString(key + ".name", key.replace('_', ' '));
            if (material == null || !ALLOWED.contains(material) || !used.add(material)
                    || !model.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || name.isBlank()
                    || name.length() > 40 || name.contains("<") || name.contains(">"))
                throw new IllegalArgumentException("Invalid block palette entry: " + key);
            result.add(new Option(name, material, model));
        }
        if (result.isEmpty() || result.size() > 27) throw new IllegalArgumentException("Choose between 1 and 27 props.");
        return List.copyOf(result);
    }
}
