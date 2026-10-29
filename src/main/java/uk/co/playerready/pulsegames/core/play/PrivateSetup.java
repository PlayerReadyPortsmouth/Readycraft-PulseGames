package uk.co.playerready.pulsegames.core.play;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a leader chose for a private match. Pure data, so a rematch can reuse it as it is.
 *
 * @param mapId       arena id, or null for "Random"
 * @param fillBots    the "fill with bots" toggle (on unless the leader switches it off)
 * @param botsTouched the leader pressed the toggle, so the choice overrides the map's {@code bots-enabled}
 * @param level       bot difficulty; {@link Level#AUTO} follows the map and server settings
 * @param members     the people admitted (leader included); bots are always admitted
 */
public record PrivateSetup(String gameId, String modeId, String mapId, boolean fillBots, boolean botsTouched,
                           Level level, UUID leader, Set<UUID> members) {

    /** Bot difficulty choice; AUTO leaves the map's {@code bot-difficulty} override and the config in charge. */
    public enum Level {
        AUTO(null, "Map default"), EASY("easy", "Easy"), NORMAL("normal", "Normal"), HARD("hard", "Hard"),
        ADAPTIVE("adaptive", "Adaptive");

        private final String setting;
        private final String label;
        Level(String setting, String label) { this.setting = setting; this.label = label; }

        /** The value for {@code bot-difficulty}, or null to follow the map. */
        public String setting() { return setting; }
        public String label() { return label; }
        public Level next() { return values()[(ordinal() + 1) % values().length]; }

        public static Level parse(String text) {
            if (text == null) return AUTO;
            for (Level level : values()) if (level.name().equals(text.trim().toUpperCase(Locale.ROOT))) return level;
            return AUTO;
        }
    }

    public PrivateSetup {
        members = Set.copyOf(new LinkedHashSet<>(members));
        level = level == null ? Level.AUTO : level;
    }

    /** True when the leader explicitly asked for bots, which beats a map that has not opted in. */
    public boolean explicitBots() { return fillBots && botsTouched; }

    public boolean admits(UUID id) { return members.contains(id); }

    /** The same game, mode, map and bot choices for a rematch with whoever the party is now. */
    public PrivateSetup withPeople(UUID newLeader, Set<UUID> people) {
        return new PrivateSetup(gameId, modeId, mapId, fillBots, botsTouched, level, newLeader, people);
    }
}
