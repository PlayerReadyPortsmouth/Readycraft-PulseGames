package uk.co.playerready.pulsegames.games.skywars;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import java.util.*;

/** Retains a checked adjacent landing during ordinary jump physics. */
public final class SkywarsBotTraversal {
    private record Flight(Player bot,Location origin,Location landing,long deadline) {}
    private final Map<UUID,Flight> flights=new HashMap<>();
    public Location airborne(Player bot,long tick) {
        Flight flight=flights.get(bot.getUniqueId());
        if(flight==null) return null;
        Location here=bot.getLocation();
        if(tick>flight.deadline() || bot.getWorld()!=flight.landing().getWorld()
                || here.getX()<Math.min(flight.origin().getX(),flight.landing().getX())-.3
                || here.getX()>Math.max(flight.origin().getX(),flight.landing().getX())+.3
                || here.getZ()<Math.min(flight.origin().getZ(),flight.landing().getZ())-.3
                || here.getZ()>Math.max(flight.origin().getZ(),flight.landing().getZ())+.3
                || !SkywarsBotTerrain.safe(flight.landing(),true)) {
            bot.setJumping(false); flights.remove(bot.getUniqueId()); return null;
        }
        if(bot.isOnGround() && bot.getLocation().distanceSquared(flight.landing())<.16) {
            bot.setJumping(false); flights.remove(bot.getUniqueId()); return null;
        }
        return flight.landing().clone();
    }
    public Location start(GameInstance game,Player bot,Location flat,long tick) {
        Location landing=SkywarsBotTerrain.heightStep(bot.getLocation(),flat);
        if(landing==null) return null;
        flights.put(bot.getUniqueId(),new Flight(bot,bot.getLocation().clone(),landing,tick+40));
        if(landing.getY()>bot.getLocation().getY()+.04) {
            bot.setJumping(true);
            game.runLater(2,()->bot.setJumping(false));
        }
        return landing;
    }
    public void clear() { for(Flight flight:flights.values()) flight.bot().setJumping(false); flights.clear(); }
    public void remove(Player bot) { flights.remove(bot.getUniqueId()); bot.setJumping(false); }
}
