package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import java.util.Collection;

/** A co-op game exposes only its own enemies and the position its defenders protect. */
public interface BotObjectives {
    Collection<? extends LivingEntity> botEnemies();
    Location botGuardPoint();
}
