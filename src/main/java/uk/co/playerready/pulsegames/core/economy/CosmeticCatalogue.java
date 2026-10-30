package uk.co.playerready.pulsegames.core.economy;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads the extra cosmetics from cosmetics.yml. A bad entry is skipped and reported, never fatal. */
public final class CosmeticCatalogue {
    private CosmeticCatalogue() { }

    public record Parsed(List<Cosmetic> items, List<String> problems) { }

    /**
     * @param section the {@code cosmetics} section: id -> {slot, name, icon, price | level, description, effect}
     * @param takenIds ids already in the catalogue (the built-in items), so a clash is refused
     */
    public static Parsed parse(ConfigurationSection section, Set<String> takenIds) {
        List<Cosmetic> items = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (section == null) return new Parsed(items, problems);
        Set<String> seen = new HashSet<>(takenIds);
        Set<Integer> levels = new HashSet<>(), styleLevels = new HashSet<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            try {
                if (s == null) throw new IllegalArgumentException("not a section");
                if (!seen.add(id)) throw new IllegalArgumentException("duplicate id");
                Cosmetic.Category slot = slot(s.getString("slot", ""));
                int price = s.getInt("price", 0), level = s.getInt("level", 0);
                if (price > 0 == level > 0) throw new IllegalArgumentException("needs exactly one of price or level");
                boolean style = slot == Cosmetic.Category.NAME_STYLE;
                if (style && (level <= 0 || level > uk.co.playerready.pulsegames.core.progression.XpRules.MAX_LEVEL
                        || !id.startsWith(Cosmetic.NAME_STYLE_PREFIX)))
                    throw new IllegalArgumentException("a name style needs an ns- id and a level from 1 to 100");
                if (level > 0 && !style && !uk.co.playerready.pulsegames.core.progression.XpRules.isUnlockLevel(level))
                    throw new IllegalArgumentException("level must be a multiple of 5 up to 100");
                if (level > 0 && !(style ? styleLevels : levels).add(level))
                    throw new IllegalArgumentException("level " + level + " already unlocks another item");
                String name = s.getString("name", "");
                if (name.isBlank()) throw new IllegalArgumentException("missing name");
                Material icon = Material.matchMaterial(s.getString("icon", ""));
                if (icon == null) throw new IllegalArgumentException("unknown icon");
                EffectSpec effect = slot == Cosmetic.Category.KIT || style ? null : EffectSpec.parse(s.getConfigurationSection("effect"));
                items.add(new Cosmetic(id, slot, name, icon, price, s.getString("description", ""), level, effect));
            } catch (IllegalArgumentException bad) {
                problems.add(id + ": " + bad.getMessage());
            }
        }
        return new Parsed(items, problems);
    }

    private static Cosmetic.Category slot(String name) {
        try { return Cosmetic.Category.valueOf(name.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException unknown) { throw new IllegalArgumentException("missing or unknown slot '" + name + "'"); }
    }
}
