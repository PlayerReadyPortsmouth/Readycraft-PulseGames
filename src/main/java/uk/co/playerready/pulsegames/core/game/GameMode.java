package uk.co.playerready.pulsegames.core.game;

/**
 * A variant of a game (e.g. Skywars Solo / Doubles / Insane).
 *
 * @param teamSize 1 for free-for-all; >1 groups players into teams of this size.
 */
public record GameMode(String id, String displayName, int minPlayers, int maxPlayers, int teamSize) {

    public static GameMode ffa(String id, String displayName, int min, int max) {
        return new GameMode(id, displayName, min, max, 1);
    }

    public static GameMode teams(String id, String displayName, int min, int max, int teamSize) {
        return new GameMode(id, displayName, min, max, teamSize);
    }

    public boolean isTeams() {
        return teamSize > 1;
    }
}
