package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A daily challenge from config.yml {@code challenges.daily}. */
public record ChallengeDef(String id, String name, Type type, String game, int target, int reward, int xp) {
    /** A challenge that pays tokens only. */
    public ChallengeDef(String id, String name, Type type, String game, int target, int reward) {
        this(id, name, type, game, target, reward, 0);
    }
    public enum Type { DISTINCT_GAMES, WINS, SURVIVE_SECONDS }

    public static List<ChallengeDef> load(ConfigurationSection daily) {
        List<ChallengeDef> result = new ArrayList<>();
        if (daily == null) return result;
        for (String id : daily.getKeys(false)) {
            ConfigurationSection s = daily.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Type type = Type.valueOf(s.getString("type", "").toUpperCase(Locale.ROOT));
                int target = s.getInt("target", 1);
                if (target < 1) continue;
                String game = s.getString("game", "");
                result.add(new ChallengeDef(id, s.getString("name", id), type, game == null ? "" : game,
                        target, Math.max(0, s.getInt("reward", 0)), Math.max(0, s.getInt("xp", 0))));
            } catch (IllegalArgumentException unknownType) {
                // Skip a malformed entry rather than break the others.
            }
        }
        return result;
    }
}
