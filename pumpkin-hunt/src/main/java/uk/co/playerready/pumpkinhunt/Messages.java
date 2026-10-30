package uk.co.playerready.pumpkinhunt;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Player-facing wording from messages.yml; missing keys fall back to the defaults shipped in the jar. */
public final class Messages {
    private final JavaPlugin plugin;
    private YamlConfiguration cfg = new YamlConfiguration();

    public Messages(JavaPlugin plugin) { this.plugin = plugin; }

    public void load() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        var in = plugin.getResource("messages.yml");
        if (in != null) c.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        cfg = c;
    }

    /** {@code get("found", "n", 3, "total", 13)} replaces {n} and {total}. */
    public String get(String key, Object... pairs) {
        String s = cfg.getString(key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) s = s.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        return s;
    }
}
