package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** A visibly labelled, non-session NPC. All operations run on the server thread. */
public interface BotActor {
    Player player();
    boolean valid();
    void navigate(Location destination);
    void stopNavigation();
    void face(Location destination);
    void destroy();
}
