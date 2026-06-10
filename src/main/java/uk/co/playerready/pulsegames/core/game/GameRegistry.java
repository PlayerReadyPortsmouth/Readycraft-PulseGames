package uk.co.playerready.pulsegames.core.game;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class GameRegistry {

    private final Map<String, GameType> games = new LinkedHashMap<>();

    public void register(GameType type) {
        games.put(type.id().toLowerCase(Locale.ROOT), type);
    }

    public GameType get(String id) {
        return id == null ? null : games.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<GameType> all() {
        return games.values();
    }
}
