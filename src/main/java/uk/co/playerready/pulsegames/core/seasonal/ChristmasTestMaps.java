package uk.co.playerready.pulsegames.core.seasonal;

import org.bukkit.*;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.world.WorldService;
import java.util.*;

/** Small flat engineering maps, separate from the builders' production worlds. */
public final class ChristmasTestMaps {
    private ChristmasTestMaps() {}
    public record Marker(String key,int x,int y,int z,String facing) {}
    public static List<Marker> markers(String game) {
        List<Marker> m=new ArrayList<>();boolean pp=game.equals("presentpanic");String p=pp?"pp":"gh";
        add(m,p+".bounds.min",-44,65,-44);add(m,p+".bounds.max",44,110,44);add(m,p+".return",0,65,38);
        for(int i=0;i<4;i++)add(m,p+".spectator",-6+i*4,70,38);
        if(pp) {
            for(int t=0;t<4;t++) {
                String c=MarkerContract.COLOURS.get(t);int x=t%2==0?-28:28,z=t<2?-22:22;
                for(int i=0;i<6;i++)add(m,"pp.spawn."+c,x+(i%3)*2,65,z+(i/3)*2);
                add(m,"pp.bow."+c,x,65,z/2);add(m,"pp.sleigh."+c,x,65,t<2?-36:36);
                add(m,"pp.sleigh."+c+".launch",x,105,t<2?-40:40);
                for(int i=0;i<4;i++)add(m,"pp.wrap."+MarkerContract.COLOURS.get(i)+"."+t,x/2+i*3-4,65,z/2);
            }
            for(int belt=1;belt<=2;belt++) {
                int x=belt==1?-3:3;add(m,"pp.belt."+belt+".feed",x,65,-21);
                for(int i=1;i<=13;i++)add(m,"pp.belt."+belt+"."+i,x,65,-21+i*3);
            }
            add(m,"pp.event.naughty",0,65,28);
        } else {
            for(int i=0;i<3;i++)add(m,"gh.grinch.spawn",-35+i*35,65,-35);
            for(int i=0;i<12;i++)add(m,"gh.villager.spawn",-8+(i%6)*3,65,30+(i/6)*3);
            for(int i=0;i<20;i++)add(m,"gh.tree."+(i+1),-30+(i%5)*15,65,-24+(i/5)*16);
            add(m,"gh.cave.pile",38,65,-36);add(m,"gh.cave.door",38,65,-30);add(m,"gh.cave.back",-38,65,20);
            for(int i=0;i<10;i++)add(m,"gh.chimney."+(i+1),-36+(i%5)*16,65,-16+(i/5)*32);
            add(m,"gh.snow.fresh.1.min",-40,65,-40);add(m,"gh.snow.fresh.1.max",32,70,26);
            add(m,"gh.path.cleared.1.min",-40,65,26);add(m,"gh.path.cleared.1.max",32,70,28);
            for(int i=0;i<3;i++)add(m,"gh.bell."+(i+1),-24+i*24,65,27);
            add(m,"gh.kennel",20,65,36);
        }
        return List.copyOf(m);
    }
    private static void add(List<Marker> m,String key,int x,int y,int z) {m.add(new Marker(key,x,y,z,"s"));}
    public static void build(PulseGamesPlugin plugin,CommandSender sender,String game) {
        if(!Set.of("presentpanic","grinchhunt").contains(game))throw new IllegalArgumentException("Use presentpanic or grinchhunt.");
        String name="rc_build_xm_"+(game.equals("presentpanic")?"pp":"gh");
        WorldCreator creator=new WorldCreator(name).generator(new WorldService.VoidGenerator()).generateStructures(false);
        if(Bukkit.getWorld(name)!=null||WorldService.worldDirectory(creator).exists())throw new IllegalArgumentException("Test world already exists; it is preserved: "+name);
        World world=creator.createWorld();if(world==null)throw new IllegalArgumentException("Could not create test world.");
        plugin.buildWorlds().manage(world);plugin.buildWorlds().retain(world);
        world.setGameRule(GameRule.DO_MOB_SPAWNING,false);world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);world.setTime(game.equals("grinchhunt")?18000:6000);
        new BukkitRunnable() {
            int x=-46;
            public void run() {
                try {
                    for(int n=0;n<3&&x<=46;n++,x++)for(int z=-46;z<=46;z++) {
                        world.getBlockAt(x,64,z).setType(Material.SNOW_BLOCK,false);
                        if(Math.abs(x)==46||Math.abs(z)==46)world.getBlockAt(x,65,z).setType(Material.SPRUCE_FENCE,false);
                    }
                    if(x<=46)return;
                    for(Marker marker:markers(game)) {
                        // Supporting blocks make raised spectator/bounds/launch signs persistent.
                        world.getBlockAt(marker.x,marker.y-1,marker.z).setType(Material.SNOW_BLOCK,false);
                        var block=world.getBlockAt(marker.x,marker.y,marker.z);block.setType(Material.OAK_SIGN,false);
                        Sign sign=(Sign)block.getState();var side=sign.getSide(Side.FRONT);
                        side.line(0,Component.text("[pulse]"));side.line(1,Component.text(marker.key));side.line(2,Component.text(marker.facing));
                        side.line(3,Component.text("XM flat test"));sign.update(true,false);
                        if(marker.key.startsWith("gh.tree.")) {
                            world.getBlockAt(marker.x+2,65,marker.z).setType(Material.SPRUCE_LOG,false);
                            world.getBlockAt(marker.x+2,66,marker.z).setType(Material.SPRUCE_LEAVES,false);
                            world.getBlockAt(marker.x+2,67,marker.z).setType(Material.GLOWSTONE,false);
                        }
                    }
                    // Clearly distinguish cleared paths without hiding prints under clutter.
                    if(game.equals("grinchhunt"))for(int xx=-40;xx<=32;xx++)for(int z=26;z<=28;z++)world.getBlockAt(xx,64,z).setType(Material.COBBLESTONE,false);
                    world.setSpawnLocation(0,65,38);world.save(true);
                    sender.sendMessage(Component.text("Built "+name+" ("+markers(game).size()+" signs). Teleport there, then /admin games setup markers "+game+" xm_"+(game.equals("presentpanic")?"pp":"gh")+" -46 64 -46 46 112 46 check"));
                    sender.sendMessage(Component.text("Repeat without check to store/remove signs and capture. Templates are test layouts; timing/fairness need production maps."));
                    cancel();plugin.buildWorlds().release(world);
                } catch(RuntimeException failure) {cancel();plugin.buildWorlds().release(world);sender.sendMessage(Component.text("Build failed; preserved partial world "+name+": "+failure.getMessage()));}
            }
        }.runTaskTimer(plugin,1,1);
    }
}
