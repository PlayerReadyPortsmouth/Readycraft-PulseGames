package uk.co.playerready.pulsegames.core.npc;

import org.bukkit.Location;
import org.bukkit.World;
import uk.co.playerready.pulsegames.core.util.LocUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * A configured NPC. The world is the name of the world the NPC was placed in:
 * for lobby NPCs that's a live world; for map NPCs it's the arena's template
 * world, and a copy is spawned into every game instance cloned from it.
 */
public final class NpcDefinition {

    public enum Kind { GAME, GREETER }

    private final String id;
    private final Kind kind;
    private final String world;
    private final String location; // "x,y,z,yaw,pitch"
    private String name;
    private String skin;           // player name to copy the skin from, or null
    private String gameId;         // GAME only; null = open the full game menu
    private double radius = 4;     // GREETER chat trigger distance
    private final List<String> lines = new ArrayList<>();

    public NpcDefinition(String id, Kind kind, String world, String location, String name) {
        this.id = id;
        this.kind = kind;
        this.world = world;
        this.location = location;
        this.name = name;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public String world() { return world; }
    public String name() { return name; }
    public String skin() { return skin; }
    public String gameId() { return gameId; }
    public double radius() { return radius; }
    public List<String> lines() { return lines; }

    public void setName(String name) { this.name = name; }
    public void setSkin(String skin) { this.skin = skin; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public void setRadius(double radius) { this.radius = radius; }

    /** The stored coordinates resolved against {@code target} (a live or cloned world). */
    public Location locationIn(World target) {
        return LocUtil.parse(location, target);
    }

    public String rawLocation() { return location; }
}
