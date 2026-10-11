package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

/** Explicit native-block/model pairs: never infer a model's appearance from its ID. */
public final class PropPalette {
    public static final Set<Material> ALLOWED = Set.of(Material.HAY_BLOCK, Material.CRAFTING_TABLE,
            Material.BOOKSHELF, Material.MELON, Material.PUMPKIN, Material.OAK_LOG,
            Material.STONE, Material.BRICKS, Material.MOSS_BLOCK, Material.PLAYER_HEAD);
    public record Option(String name, Material material, String model) {
        public double height() { return material == Material.PLAYER_HEAD ? 0.5 : 1.0; }
    }
    private PropPalette() {}
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
