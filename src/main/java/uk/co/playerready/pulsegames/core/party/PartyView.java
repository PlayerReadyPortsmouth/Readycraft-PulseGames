package uk.co.playerready.pulsegames.core.party;

import uk.co.playerready.pulsegames.core.game.GameState;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Pure ordering and wording for the party menu. */
public final class PartyView {
    private PartyView() { }

    public record Person(UUID id, String name) { }

    /** Leader first, then everyone else A to Z. */
    public static List<Person> orderMembers(UUID leader, List<Person> members) {
        return members.stream()
                .sorted(Comparator.<Person, Boolean>comparing(m -> !m.id().equals(leader))
                        .thenComparing(m -> m.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** One friendly line about what the party is doing; {@code gameName} null means not in a game. */
    public static String queueLine(String gameName, GameState state) {
        if (gameName == null || state == null) return "Not queued yet. The leader picks a game.";
        return switch (state) {
            case LOADING, WAITING, COUNTDOWN -> "Waiting for " + gameName + " to start";
            case RUNNING -> "Playing " + gameName;
            case ENDING, RESETTING -> "Finishing " + gameName;
        };
    }
}
