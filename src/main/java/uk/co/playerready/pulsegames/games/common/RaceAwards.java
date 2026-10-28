package uk.co.playerready.pulsegames.games.common;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Text;

/** Finish title and per-map personal best for race-pack games. */
public final class RaceAwards {
    private RaceAwards() {}

    /** Saves the time as a personal best if it is one and shows the matching title and sound. */
    public static boolean announceFinish(GameInstance game, Player player, int place, long ms) {
        String time = RaceFinish.time(ms);
        boolean pb = false;
        long old = -1;
        if (!game.isBot(player)) {
            String gameId = game.type().id(), map = game.arena().id();
            old = game.stats().bestTime(player, gameId, map);
            pb = game.stats().submitBestTime(player, gameId, map, ms);
        }
        if (pb) {
            Text.title(player, "<gold><b>New personal best!", "<yellow>" + RaceFinish.ordinal(place) + " <gray>in <white>" + time
                    + (old > 0 ? " <gray>(was " + RaceFinish.time(old) + ")" : ""));
            player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", 1f, 1f);
        } else {
            Text.title(player, "<gold><b>" + RaceFinish.ordinal(place) + " place", "<gray>Finish time <white>" + time);
            player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 1f, 1.2f);
        }
        return pb;
    }
}
