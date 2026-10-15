package uk.co.playerready.pulsegames.games.karts;

import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.util.Cuboid;

/** Reject lap gates which can all be crossed repeatedly without leaving the finish. */
final class KartTrackSafety {
    static String validationError(Arena arena) {
        var checkpoints=arena.regionsByPrefix("checkpoint");
        Cuboid finish=arena.region("finish");
        if (checkpoints.isEmpty() || finish==null) return "PulseKarts needs an ordered checkpoint and finish line.";
        Cuboid first=checkpoints.get(0);
        boolean overlap=first.minX()<=finish.maxX() && first.maxX()>=finish.minX()
                && first.minY()<=finish.maxY() && first.maxY()>=finish.minY()
                && first.minZ()<=finish.maxZ() && first.maxZ()>=finish.minZ();
        return overlap ? "The first kart checkpoint must not overlap the finish line." : null;
    }
}
