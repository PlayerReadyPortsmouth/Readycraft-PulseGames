package uk.co.playerready.pulsegames.core.game;

/**
 * A variant of a game (e.g. Skywars Solo / Doubles / Insane).
 *
 * @param teamSize maximum players on a team; solo base games use teams of one.
 * @param teamBased distinguishes solo teams from free-for-all scoring.
 */
public record GameMode(String id, String displayName, int minPlayers, int maxPlayers, int teamSize, boolean teamBased) {

    public GameMode(String id, String displayName, int min, int max, int size) {
        this(id, displayName, min, max, size, size > 1);
    }

    public String rulesId() { return id.replaceFirst("-(doubles|triples|squads)$", ""); }

    public static GameMode ffa(String id, String displayName, int min, int max) {
        return new GameMode(id, displayName, min, max, 1, false);
    }

    public static GameMode teams(String id, String displayName, int min, int max, int teamSize) {
        return new GameMode(id, displayName, min, max, teamSize, true);
    }

    public boolean isTeams() {
        return teamBased;
    }
}
