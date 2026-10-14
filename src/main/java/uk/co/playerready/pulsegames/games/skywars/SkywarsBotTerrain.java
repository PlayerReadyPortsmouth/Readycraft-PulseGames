package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import java.util.Set;

/** Conservative level walking only; no jump, fall, teleport or unloaded-chunk shortcut. */
public final class SkywarsBotTerrain {
    private static final Set<Material> DANGER = Set.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE);
    private SkywarsBotTerrain() {}
    public static Location step(Location here, Location goal) {
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
    public static boolean corridor(Location from, Location to) {
        if (from == null || to == null || from.getWorld() != to.getWorld() || from.distanceSquared(to) > 4
                || Math.abs(from.getY()-to.getY()) > .04) return false;
        var delta = to.toVector().subtract(from.toVector());
        int samples = Math.max(1, (int)Math.ceil(delta.length()/.2));
        for (int i = 0; i <= samples; i++) if (!safe(from.clone().add(delta.clone().multiply((double)i/samples)), true)) return false;
        return true;
    }
    public static boolean safe(Location feet, boolean support) {
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
                if(floor.isPassable() || floor.isLiquid() || DANGER.contains(floor.getType())
                        || !supportedTop(floor,feet,box,x,z,top)) return false;
            }
        }
        return true;
    }
    private static boolean supportedTop(org.bukkit.block.Block block,Location feet,BoundingBox body,int x,int z,BoundingBox top) {
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Stairs stair) {
            if(stair.isWaterlogged() || Math.abs(feet.getY()-(block.getY()+1))>.04) return false;
            if(stair.getHalf()==org.bukkit.block.data.Bisected.Half.TOP) return covers(top,body,x,z);
            if(stair.getShape()!=org.bukkit.block.data.type.Stairs.Shape.STRAIGHT) return false;
            double minX=x,maxX=x+1,minZ=z,maxZ=z+1;
            switch(stair.getFacing()) {
                case EAST -> minX+=.5; case WEST -> maxX-=.5;
                case SOUTH -> minZ+=.5; case NORTH -> maxZ-=.5; default -> { return false; }
            }
            // Centre and a conservative 0.4-wide footing must sit on the upper step; no corner-stair guesses.
            return feet.getX()-.2>=minX-.001 && feet.getX()+.2<=maxX+.001 && feet.getZ()-.2>=minZ-.001 && feet.getZ()+.2<=maxZ+.001;
        }
        boolean slab=block.getBlockData() instanceof org.bukkit.block.data.type.Slab data && !data.isWaterlogged();
        return (fullFloor(block.getType()) || slab) && Math.abs(top.getMaxY()-feet.getY())<.04 && covers(top,body,x,z);
    }
    private static boolean covers(BoundingBox top,BoundingBox body,int x,int z) {
        return top.getMinX()<=Math.max(x,body.getMinX())+.001 && top.getMaxX()>=Math.min(x+1,body.getMaxX())-.001
                && top.getMinZ()<=Math.max(z,body.getMinZ())+.001 && top.getMaxZ()>=Math.min(z+1,body.getMaxZ())-.001;
    }
    /** Adjacent height changes only; proves landing and overhead clearance, never a long void jump. */
    public static Location heightStep(Location from,Location flat) {
        if(from==null || flat==null || !safe(from,true) || from.distanceSquared(flat)>2.6) return null;
        var world=from.getWorld();
        if(!world.isChunkLoaded(flat.getBlockX()>>4,flat.getBlockZ()>>4)) return null;
        for(double height:new double[]{.5,1,-.5,-1}) {
            Location landing=flat.clone().add(0,height,0);
            if(landing.getY()<world.getMinHeight()+1 || landing.getY()+1.8>=world.getMaxHeight()) continue;
            var floor=world.getBlockAt(landing.getBlockX(),(int)Math.floor(landing.getY()-.02),landing.getBlockZ());
            if(floor.getBlockData() instanceof org.bukkit.block.data.type.Stairs stair
                    && stair.getHalf()==org.bukkit.block.data.Bisected.Half.BOTTOM && stair.getShape()==org.bukkit.block.data.type.Stairs.Shape.STRAIGHT) {
                landing.add(stair.getFacing().getModX()*.2,0,stair.getFacing().getModZ()*.2);
            }
            if(!safe(landing,true)) continue;
            double clearance=Math.max(from.getY(),landing.getY())+.25;
            Location raised=from.clone(); raised.setY(clearance);
            var delta=landing.toVector().subtract(from.toVector()); delta.setY(0);
            boolean clear=true;
            for(int i=0;i<=8;i++) if(!safe(raised.clone().add(delta.clone().multiply(i/8.0)),false)) { clear=false; break; }
            if(clear) return landing;
        }
        return null;
    }
    public static boolean loadedSegment(Location from,Location to) {
        if(from==null || to==null || from.getWorld()==null || from.getWorld()!=to.getWorld()
                || !Double.isFinite(from.getX()) || !Double.isFinite(from.getZ())
                || !Double.isFinite(to.getX()) || !Double.isFinite(to.getZ()) || from.distanceSquared(to)>64*64) return false;
        for(int x=Math.min(from.getBlockX(),to.getBlockX())>>4;x<=(Math.max(from.getBlockX(),to.getBlockX())>>4);x++)
            for(int z=Math.min(from.getBlockZ(),to.getBlockZ())>>4;z<=(Math.max(from.getBlockZ(),to.getBlockZ())>>4);z++)
                if(!from.getWorld().isChunkLoaded(x,z)) return false;
        return true;
    }
    public static boolean air(Material material) { return material==Material.AIR || material==Material.CAVE_AIR || material==Material.VOID_AIR; }
    private static boolean fullFloor(Material material) {
        return uk.co.playerready.pulsegames.core.bot.BotFloorSafety.fullCube(material);
    }
    private static int floor(double number) { return (int)Math.floor(number); }
}
