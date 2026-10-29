package uk.co.playerready.pulsegames.core.play;

import java.util.Set;
import java.util.UUID;

/** What the leader has picked so far in the private-match menu (pure state, shared by chest menu and Bedrock form). */
public final class PrivateDraft {
    private String gameId, modeId, mapId;
    private boolean fillBots = true, botsTouched;
    private PrivateSetup.Level level = PrivateSetup.Level.AUTO;

    public String gameId() { return gameId; }
    public String modeId() { return modeId; }
    /** Arena id, or null for Random. */
    public String mapId() { return mapId; }
    public boolean fillBots() { return fillBots; }
    public PrivateSetup.Level level() { return level; }

    /** A new game clears the mode and map, because neither carries over. */
    public void setGame(String id, String onlyMode) {
        gameId = id;
        modeId = onlyMode;
        mapId = null;
    }

    public void setMode(String id) { modeId = id; mapId = null; }
    public void setMap(String arenaId) { mapId = arenaId; }

    public void toggleBots() { fillBots = !fillBots; botsTouched = true; }
    public void nextLevel() { level = level.next(); }

    public boolean ready() { return gameId != null && modeId != null; }

    public PrivateSetup toSetup(UUID leader, Set<UUID> members) {
        return new PrivateSetup(gameId, modeId, mapId, fillBots, botsTouched, level, leader, members);
    }
}
