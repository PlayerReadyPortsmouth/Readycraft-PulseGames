package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import java.util.Set;

/** Conservative level walking only; no jump, fall, teleport or unloaded-chunk shortcut. */
final class SkywarsBotTerrain {
    private static final Set<Material> DANGER = Set.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE);
    private SkywarsBotTerrain() {}
    static Location step(Location here, Location goal) {
        if (here == null || goal == null || here.getWorld() == null || here.getWorld() != goal.getWorld()) return null;
        double dx = goal.getX() - here.getX(), dz = goal.getZ() - here.getZ();
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || dx*dx+dz*dz < .04) return null;
        Location centre = new Location(here.getWorld(), here.getBlockX()+.5, here.getY(), here.getBlockZ()+.5);
        // Centre on the current block before stepping out, so bridging never requires a diagonal leap.
        if (Math.abs(here.getX()-centre.getX()) > .2 || Math.abs(here.getZ()-centre.getZ()) > .2) return centre;
        if (Math.abs(dx) >= Math.abs(dz)) centre.add(Math.signum(dx),0,0);
        else centre.add(0,0,Math.signum(dz));
        return centre;
    }
    static boolean corridor(Location from, Location to) {
        if (from == null || to == null || from.getWorld() != to.getWorld() || from.distanceSquared(to) > 4
                || Math.abs(from.getY()-to.getY()) > .04) return false;
        var delta = to.toVector().subtract(from.toVector());
        int samples = Math.max(1, (int)Math.ceil(delta.length()/.2));
        for (int i = 0; i <= samples; i++) if (!safe(from.clone().add(delta.clone().multiply((double)i/samples)), true)) return false;
        return true;
    }
    static boolean safe(Location feet, boolean support) {
        var world = feet.getWorld();
        if (world == null || !Double.isFinite(feet.getX()) || !Double.isFinite(feet.getZ()) || !Double.isFinite(feet.getY()) || Math.abs(feet.getX()) > 29_999_983
                || Math.abs(feet.getZ()) > 29_999_983 || feet.getY() < world.getMinHeight()+1
                || feet.getY()+1.8 >= world.getMaxHeight()) return false;
        var box = new BoundingBox(feet.getX()-.29, feet.getY()+.001, feet.getZ()-.29,
                feet.getX()+.29, feet.getY()+1.8, feet.getZ()+.29);
        var border = world.getWorldBorder();
        if (border == null || !border.isInside(new Location(world,box.getMinX(),feet.getY(),box.getMinZ()))
                || !border.isInside(new Location(world,box.getMaxX(),feet.getY(),box.getMaxZ()))) return false;
        for(int x=floor(box.getMinX());x<=floor(box.getMaxX());x++) for(int z=floor(box.getMinZ());z<=floor(box.getMaxZ());z++)
            if(!world.isChunkLoaded(x>>4,z>>4)) return false;
        for(int x=floor(box.getMinX());x<=floor(box.getMaxX());x++) for(int z=floor(box.getMinZ());z<=floor(box.getMaxZ());z++) {
            for(int y=floor(box.getMinY());y<=floor(box.getMaxY());y++) {
                var block=world.getBlockAt(x,y,z);
                if(block.isLiquid() || DANGER.contains(block.getType()) || (!block.isPassable() && block.getBoundingBox().overlaps(box))) return false;
            }
            if(support) {
                var floor=world.getBlockAt(x,floor(feet.getY()-.02),z);
                var top=floor.getBoundingBox();
                // Conservative full-cube floors avoid treating open trapdoors, fences or stair bounding envelopes as support.
                if(floor.isPassable() || floor.isLiquid() || !fullFloor(floor.getType()) || DANGER.contains(floor.getType())
                        || Math.abs(top.getMaxY()-feet.getY())>.04
                        || top.getMinX()>Math.max(x,box.getMinX())+.001 || top.getMaxX()<Math.min(x+1,box.getMaxX())-.001
                        || top.getMinZ()>Math.max(z,box.getMinZ())+.001 || top.getMaxZ()<Math.min(z+1,box.getMaxZ())-.001) return false;
            }
        }
        return true;
    }
    static boolean air(Material material) { return material==Material.AIR || material==Material.CAVE_AIR || material==Material.VOID_AIR; }
    private static boolean fullFloor(Material material) {
        return uk.co.playerready.pulsegames.core.bot.BotFloorSafety.fullCube(material);
    }
    private static int floor(double number) { return (int)Math.floor(number); }
}
