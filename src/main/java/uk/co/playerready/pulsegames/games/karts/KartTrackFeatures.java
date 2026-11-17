package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.configuration.ConfigurationSection;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** A race uses one physics ruleset: every human participant must be eligible. */
final class KartTrackFeatures {
    static final String PATH = "karts.track-physics";

    static boolean allowed(ConfigurationSection config, Collection<UUID> humans) {
        if (config == null || !config.getBoolean(PATH + ".enabled", false)) return false;
        if (config.getBoolean(PATH + ".broad", false)) return true;
        Set<String> allowed = config.getStringList(PATH + ".canary-players").stream()
                .map(String::trim).map(s -> s.toLowerCase(java.util.Locale.ROOT)).collect(Collectors.toSet());
        return !humans.isEmpty() && humans.stream().allMatch(id -> allowed.contains(id.toString()));
    }
}
