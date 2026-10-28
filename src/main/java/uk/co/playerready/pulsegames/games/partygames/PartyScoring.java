package uk.co.playerready.pulsegames.games.partygames;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Placement points for a micro-game: 5/3/1 for the podium, plus 1 for taking part. */
public final class PartyScoring {
    public static final int[] PLACE_POINTS = {5, 3, 1};
    public static final int PARTICIPATION = 1;

    private PartyScoring() {}

    /**
     * @param groups placement groups, best first; players in one group tied. A group's rank is the
     *               number of players placed above it (competition ranking), so a 3-way tie for
     *               first leaves nobody on 3 or 1.
     * @param participants everyone who took part (gets the participation point)
     * @param multiplier 2 in a chaos round
     */
    public static Map<UUID, Integer> award(List<? extends Collection<UUID>> groups,
                                           Collection<UUID> participants, int multiplier) {
        Map<UUID, Integer> points = new LinkedHashMap<>();
        for (UUID id : participants) points.put(id, PARTICIPATION);
        int position = 0;
        for (Collection<UUID> group : groups) {
            int place = position < PLACE_POINTS.length ? PLACE_POINTS[position] : 0;
            for (UUID id : group) if (points.containsKey(id)) points.merge(id, place, Integer::sum);
            position += group.size();
        }
        points.replaceAll((id, value) -> value * multiplier);
        return points;
    }
}
