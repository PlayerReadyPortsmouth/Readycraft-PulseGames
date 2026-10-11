package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;

/** Optional NPC transport. Creation failures must not leave registered NPCs behind. */
public interface BotProvider extends AutoCloseable {
    BotActor create(String name, Location location);
    @Override void close();
}
