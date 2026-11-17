package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.configuration.ConfigurationSection;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Item rules apply to an entire race, never only the eligible drivers within it. */
final class KartShowcaseAccess {
    static boolean allowed(ConfigurationSection config, Collection<UUID> humans) {
        String path = "karts.showcase";
        if (config == null || !config.getBoolean(path + ".enabled", false)) return false;
        if (config.getBoolean(path + ".broad", false)) return true;
        Set<String> allowed = config.getStringList(path + ".canary-players").stream()
                .map(String::trim).map(s -> s.toLowerCase(java.util.Locale.ROOT)).collect(Collectors.toSet());
        return !humans.isEmpty() && humans.stream().allMatch(id -> allowed.contains(id.toString()));
    }
}
