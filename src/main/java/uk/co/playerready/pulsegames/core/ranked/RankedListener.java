package uk.co.playerready.pulsegames.core.ranked;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Keeps the division metadata fresh, and tells the service when a disconnected player is back. */
public final class RankedListener implements Listener {
    private final RankedService ranked;
    public RankedListener(RankedService ranked) { this.ranked = ranked; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { ranked.reconnected(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ranked.leaveQueue(event.getPlayer());
        ranked.clearMetadata(event.getPlayer());
    }
}
