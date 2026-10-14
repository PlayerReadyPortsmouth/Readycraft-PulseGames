package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Material;
import org.bukkit.World;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;

/** Main-thread bounded scan. Results are published only after every selected cell is read. */
public final class PropPaletteScan {
    private final World world;
    private final Cuboid region;
    private final List<PropPalette.Option> supported;
    private final Set<Material> found = new HashSet<>();
    private int index;
    public PropPaletteScan(World world,Cuboid region,List<PropPalette.Option> supported) {
        this.world=world;this.region=region;this.supported=List.copyOf(supported);
        if(region.minY()<world.getMinHeight()||region.maxY()>=world.getMaxHeight())
            throw new IllegalArgumentException("Palette region extends beyond the world's height.");
    }
    public boolean step() {
        int sy=region.maxY()-region.minY()+1,sz=region.maxZ()-region.minZ()+1;
        int stop=(int)Math.min(region.volume(),index+4096L);
        for(;index<stop;index++){
            int x=region.minX()+index/(sy*sz),y=region.minY()+index/sz%sy,z=region.minZ()+index%sz;
            if(!world.isChunkLoaded(x>>4,z>>4))throw new IllegalArgumentException("Palette area contains unloaded chunks; load the built area and scan again.");
            found.add(world.getBlockAt(x,y,z).getType());
        }
        return index==region.volume();
    }
    public List<String> result() {
        if(index!=region.volume())throw new IllegalStateException("Palette scan is incomplete.");
        List<String> result=supported.stream().filter(o->found.contains(o.material())).map(o->o.material().name()).toList();
        if(result.isEmpty())throw new IllegalArgumentException("No supported disguise blocks found; previous palette retained.");
        return result;
    }
}
