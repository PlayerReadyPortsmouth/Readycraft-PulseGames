package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The plugin's own daily-challenge progress (engagement.yml). This is what makes challenges work
 * without the shared PulseContent SQL; progress resets whenever the stored day differs from today.
 */
public final class ChallengeStore {
    private final ConfigurationSection root;

    public ChallengeStore(ConfigurationSection root) { this.root = root; }

    private ConfigurationSection today(UUID player, LocalDate day) {
        String base = player.toString();
        if (!day.toString().equals(root.getString(base + ".day"))) {
            root.set(base, null);
            root.set(base + ".day", day.toString());
        }
        return root.getConfigurationSection(base);
    }

    public synchronized int progress(UUID player, LocalDate day, ChallengeDef def) {
        ConfigurationSection s = today(player, day);
        return switch (def.type()) {
            case DISTINCT_GAMES -> s.getStringList("games").size();
            case WINS, SURVIVE_SECONDS -> s.getInt("p." + def.id());
        };
    }

    public synchronized boolean done(UUID player, LocalDate day, ChallengeDef def) {
        return today(player, day).getBoolean("done." + def.id());
    }

    /** Records one finished round and returns the challenges it newly completed. */
    public synchronized List<ChallengeDef> record(UUID player, LocalDate day, String game, boolean won,
                                                  int survivedSeconds, List<ChallengeDef> defs) {
        ConfigurationSection s = today(player, day);
        List<String> games = new ArrayList<>(s.getStringList("games"));
        if (!games.contains(game)) { games.add(game); s.set("games", games); }
        List<ChallengeDef> completed = new ArrayList<>();
        for (ChallengeDef def : defs) {
            if (s.getBoolean("done." + def.id())) continue;
            boolean gameOk = def.game().isEmpty() || def.game().equals(game);
            int now = switch (def.type()) {
                case DISTINCT_GAMES -> games.size();
                case WINS -> {
                    int n = s.getInt("p." + def.id()) + (won && gameOk ? 1 : 0);
                    s.set("p." + def.id(), n);
                    yield n;
                }
                case SURVIVE_SECONDS -> {
                    int n = gameOk ? Math.max(s.getInt("p." + def.id()), survivedSeconds) : s.getInt("p." + def.id());
                    s.set("p." + def.id(), n);
                    yield n;
                }
            };
            if (now >= def.target()) {
                s.set("done." + def.id(), true);
                completed.add(def);
            }
        }
        return completed;
    }
}
