package uk.co.playerready.pulsegames.core.game;

/**
 * Plain-language "how to win" card shown as a title when a player joins a queue and
 * again at GO, with a short tip for the waiting sidebar. Keep each part to one line:
 * Bedrock and mobile players often have chat hidden, so this is the main onboarding.
 *
 * @param title short goal, e.g. "Last one standing wins!"
 * @param subtitle how to do it, e.g. "Dig the floor out from under everyone else"
 * @param tip waiting-sidebar line, kept short enough for a sidebar (about 36 characters)
 */
public record ObjectiveCard(String title, String subtitle, String tip) {

    /** Used when a game has no authored card: the catalogue name and description. */
    public static ObjectiveCard fallback(GameType type) {
        return new ObjectiveCard(type.displayName(), type.description(), "Play fair and have fun!");
    }
}
