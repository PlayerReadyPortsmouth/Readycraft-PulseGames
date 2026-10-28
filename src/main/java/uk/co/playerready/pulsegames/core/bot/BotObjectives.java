package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import java.util.Collection;

/** A co-op game exposes only its own enemies and the position its defenders protect. */
public interface BotObjectives {
    Collection<? extends LivingEntity> botEnemies();
    Location botGuardPoint();
    /** Where this particular bot should stand; games that want per-bot behaviour override it. */
    default Location botGuardPoint(org.bukkit.entity.Player bot) { return botGuardPoint(); }
    default void botTick(org.bukkit.entity.Player bot) {}
}
