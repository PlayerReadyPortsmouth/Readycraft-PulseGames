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

    /**
     * Parses into a local map and only swaps it in on success: a single malformed
     * line used to throw out of here, wiping every kit and - because this runs from
     * onEnable - skipping game registration, listeners and commands entirely.
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) plugin.saveResource("kits.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("kits");
        if (root == null) {
            plugin.getLogger().severe("kits.yml has no 'kits' section; keeping the "
                    + kits.size() + " kits already loaded.");
            return;
        }
        Map<String, Kit> loaded = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            try {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec == null) throw new IllegalArgumentException("not a kit section");
                List<ItemStack> items = new ArrayList<>();
                for (String raw : sec.getStringList("items")) {
                    ItemStack item = parseItem(raw);
                    if (item != null) items.add(item);
                }
                loaded.put(id.toLowerCase(Locale.ROOT), new Kit(
                        id,
                        sec.getString("display-name", id),
                        material(sec.getString("icon"), Material.CHEST),
                        armor(sec.getString("helmet")), armor(sec.getString("chestplate")),
                        armor(sec.getString("leggings")), armor(sec.getString("boots")),
                        items));
            } catch (Exception ex) {
                plugin.getLogger().severe("Skipping kit '" + id + "' in kits.yml: " + ex.getMessage());
            }
        }
        kits.clear();
        kits.putAll(loaded);
        plugin.getLogger().info("Loaded " + kits.size() + " kits.");
    }

    private ItemStack parseItem(String raw) {
        String[] parts = raw.split(":");
        Material material = Material.matchMaterial(parts[0].trim());
        if (material == null) {
            plugin.getLogger().warning("Unknown material in kits.yml: " + raw);
            return null;
        }
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("Bad amount in kits.yml entry '" + raw + "'; using 1.");
            }
        }
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
