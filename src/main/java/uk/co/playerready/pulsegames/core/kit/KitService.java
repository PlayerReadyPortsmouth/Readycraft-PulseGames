package uk.co.playerready.pulsegames.core.kit;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KitService {

    private final JavaPlugin plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        kits.clear();
        File file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) plugin.saveResource("kits.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("kits");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            List<ItemStack> items = new ArrayList<>();
            for (String raw : sec.getStringList("items")) {
                ItemStack item = parseItem(raw);
                if (item != null) items.add(item);
            }
            kits.put(id.toLowerCase(Locale.ROOT), new Kit(
                    id,
                    sec.getString("display-name", id),
                    material(sec.getString("icon"), Material.CHEST),
                    armor(sec.getString("helmet")), armor(sec.getString("chestplate")),
                    armor(sec.getString("leggings")), armor(sec.getString("boots")),
                    items));
        }
        plugin.getLogger().info("Loaded " + kits.size() + " kits.");
    }

    private ItemStack parseItem(String raw) {
        String[] parts = raw.split(":");
        Material material = Material.matchMaterial(parts[0]);
        if (material == null) {
            plugin.getLogger().warning("Unknown material in kits.yml: " + raw);
            return null;
        }
        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        return new ItemStack(material, amount);
    }

    private ItemStack armor(String name) {
        if (name == null) return null;
        Material material = Material.matchMaterial(name);
        return material == null ? null : new ItemStack(material);
    }

    private Material material(String name, Material fallback) {
        Material material = name == null ? null : Material.matchMaterial(name);
        return material == null ? fallback : material;
    }

    public Kit get(String id) {
        return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Kit> all() { return List.copyOf(kits.values()); }
}
