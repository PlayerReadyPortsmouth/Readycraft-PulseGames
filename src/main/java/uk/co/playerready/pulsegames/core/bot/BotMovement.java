package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** A movement objective follows the same public cues as players; navigation never teleports. */
public interface BotMovement {
    Location botDestination(Player bot);
    default void botTick(Player bot, long tick, BotDifficulty difficulty) {}
}
