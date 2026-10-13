package uk.co.playerready.pulsenetwork;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.LoaderOptions;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Stores coordinates as plain data; loading the file never requires a loaded world. */
final class ReturnLocations {
    record Position(String world, UUID worldId, double x, double y, double z, float yaw, float pitch) {
        Position {
            if (world == null || world.isBlank() || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                    || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) throw new IllegalArgumentException("Invalid return position");
        }
        Location in(World loaded) {
            if (loaded == null || !world.equals(loaded.getName()) || worldId != null && !worldId.equals(loaded.getUID())) return null;
            Location loc = new Location(loaded,x,y,z,yaw,pitch);
            return y >= loaded.getMinHeight() && y < loaded.getMaxHeight()-1 && loaded.getWorldBorder().isInside(loc) ? loc : null;
        }
    }
    private final Map<UUID, Position> positions = new HashMap<>();
    static ReturnLocations load(Path file) throws IOException {
        ReturnLocations result = new ReturnLocations();
        if (!Files.exists(file)) return result;
        // SafeConstructor treats Bukkit's old '==' field as data, never invokes Location.deserialize.
        try (Reader reader=Files.newBufferedReader(file)) {
            Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
            if (root == null) return result;
            if (!(root instanceof Map<?,?> rows)) throw new IOException("Invalid return-location file");
            for (var entry:rows.entrySet()) {
                try {
                    if (!(entry.getValue() instanceof Map<?,?> m)) continue;
                    String name=(String)m.get("world");
                    UUID worldId=m.get("world-id") instanceof String id ? UUID.fromString(id) : null;
                    result.positions.put(UUID.fromString(entry.getKey().toString()),new Position(name,worldId,
                            number(m,"x"),number(m,"y"),number(m,"z"),(float)number(m,"yaw"),(float)number(m,"pitch")));
                } catch (IllegalArgumentException | ClassCastException invalid) { /* Ignore one corrupt row, preserving others. */ }
            }
        } catch (org.yaml.snakeyaml.error.YAMLException ex) { throw new IOException("Invalid return-location YAML",ex); }
        return result;
    }
    private static double number(Map<?,?> m,String key) { Object value=m.get(key);return value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value)); }
    Position get(UUID player) {return positions.get(player);}
    void put(UUID player,Location loc) {
        World w=Objects.requireNonNull(loc.getWorld());
        positions.put(player,new Position(w.getName(),w.getUID(),loc.getX(),loc.getY(),loc.getZ(),loc.getYaw(),loc.getPitch()));
    }
    void save(Path file) throws IOException {
        YamlConfiguration yml=new YamlConfiguration();
        positions.forEach((id,p)-> {
            String k=id.toString()+".";yml.set(k+"world",p.world());yml.set(k+"world-id",p.worldId()==null?null:p.worldId().toString());
            yml.set(k+"x",p.x());yml.set(k+"y",p.y());yml.set(k+"z",p.z());yml.set(k+"yaw",p.yaw());yml.set(k+"pitch",p.pitch());
        });
        Files.createDirectories(file.getParent());Path tmp=Files.createTempFile(file.getParent(),"return-",".tmp");
        try { Files.writeString(tmp,yml.saveToString());
            try {Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch (AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(tmp);}
    }
}
