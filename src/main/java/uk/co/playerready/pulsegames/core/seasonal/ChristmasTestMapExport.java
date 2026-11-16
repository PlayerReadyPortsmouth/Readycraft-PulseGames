package uk.co.playerready.pulsegames.core.seasonal;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Standalone engineering map artifacts, generated from the same markers as the in-server builder. */
public final class ChristmasTestMapExport {
    private ChristmasTestMapExport() {}
    // Version taken from Mojang 1.21.1 version.json; 1.21.4 can upgrade these vanilla blocks/signs.
    static final int DATA_VERSION=3955,WIDTH=93,HEIGHT=49,LENGTH=93;
    private static final String[] PALETTE={"minecraft:air","minecraft:snow_block","minecraft:spruce_fence","minecraft:oak_sign[rotation=0,waterlogged=false]","minecraft:spruce_log","minecraft:spruce_leaves[persistent=true]","minecraft:glowstone","minecraft:cobblestone"};
    public static void main(String[] args) throws IOException {export(Path.of(args.length==0?"build/christmas-test-maps":args[0]));}
    public static void export(Path directory) throws IOException {
        Files.createDirectories(directory);
        for(String game:List.of("presentpanic","grinchhunt")) {
            String name=game.equals("presentpanic")?"pulse_present_panic_test":"pulse_grinch_hunt_test";
            List<ChristmasTestMaps.Marker> markers=ChristmasTestMaps.markers(game);byte[] blocks=new byte[WIDTH*HEIGHT*LENGTH];
            for(int x=-46;x<=46;x++)for(int z=-46;z<=46;z++) {
                put(blocks,x,64,z,1);if(Math.abs(x)==46||Math.abs(z)==46)put(blocks,x,65,z,2);
            }
            for(var m:markers) {
                put(blocks,m.x(),m.y()-1,m.z(),1);put(blocks,m.x(),m.y(),m.z(),3);
                if(m.key().startsWith("gh.tree.")) {put(blocks,m.x()+2,65,m.z(),4);put(blocks,m.x()+2,66,m.z(),5);put(blocks,m.x()+2,67,m.z(),6);}
            }
            if(game.equals("grinchhunt"))for(int x=-40;x<=32;x++)for(int z=26;z<=28;z++)put(blocks,x,64,z,7);
            try(DataOutputStream o=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(directory.resolve(name+".schem"))))) {
                head(o,10,"");head(o,10,"Schematic");integer(o,"Version",3);integer(o,"DataVersion",DATA_VERSION);
                head(o,10,"Metadata");string(o,"Name",name);string(o,"Author","AurorasChaos (on behalf of Player Ready)");o.writeByte(0);
                small(o,"Width",WIDTH);small(o,"Height",HEIGHT);small(o,"Length",LENGTH);ints(o,"Offset",-46,64,-46);
                head(o,10,"Blocks");head(o,10,"Palette");for(int i=0;i<PALETTE.length;i++)integer(o,PALETTE[i],i);o.writeByte(0);
                head(o,7,"Data");o.writeInt(blocks.length);o.write(blocks);
                head(o,9,"BlockEntities");o.writeByte(10);o.writeInt(markers.size());
                for(var m:markers) {
                    ints(o,"Pos",m.x()+46,m.y()-64,m.z()+46);string(o,"Id","minecraft:sign");head(o,10,"Data");
                    head(o,10,"front_text");messages(o,List.of("[pulse]",m.key(),m.facing(),"XM flat test"));string(o,"color","black");head(o,1,"has_glowing_text");o.writeByte(0);o.writeByte(0);
                    head(o,10,"back_text");messages(o,List.of("","","",""));string(o,"color","black");o.writeByte(0);
                    head(o,1,"is_waxed");o.writeByte(0);o.writeByte(0);o.writeByte(0);
                }
                o.writeByte(0);o.writeByte(0);o.writeByte(0);
            }
            StringBuilder csv=new StringBuilder("key,x,y,z,facing\n");markers.forEach(m->csv.append(m.key()).append(',').append(m.x()).append(',').append(m.y()).append(',').append(m.z()).append(',').append(m.facing()).append('\n'));
            Files.writeString(directory.resolve(name+"-markers.csv"),csv);
        }
        Files.writeString(directory.resolve("README.txt"),"XM flat engineering maps. Author: AurorasChaos (on behalf of Player Ready).\nSponge v3 .schem, DataVersion3955. Paste at origin0,0,0 to preserve coordinates. Offset -46,64,-46; footprint93x93, top112.\nPresent Panic:88 signs. Grinch Hunt:63 signs. No entities or redstone.\n/admin games setup markers presentpanic xm_pp -46 64 -46 46 112 46 check\n/admin games setup markers grinchhunt xm_gh -46 64 -46 46 112 46 check\nRepeat without check to store/remove signs and capture. The in-server alternative is /admin games setup christmas-test <game>.\nThese are small flat engineering test maps. Production route timing, rooftop/chimney travel and Java/Bedrock screenshots remain human playtest checks.\n");
    }
    private static void put(byte[] blocks,int x,int y,int z,int value) {blocks[(x+46)+(z+46)*WIDTH+(y-64)*WIDTH*LENGTH]=(byte)value;}
    private static void head(DataOutputStream o,int tag,String name) throws IOException {o.writeByte(tag);o.writeUTF(name);}
    private static void integer(DataOutputStream o,String name,int value) throws IOException {head(o,3,name);o.writeInt(value);}
    private static void small(DataOutputStream o,String name,int value) throws IOException {head(o,2,name);o.writeShort(value);}
    private static void string(DataOutputStream o,String name,String value) throws IOException {head(o,8,name);o.writeUTF(value);}
    private static void ints(DataOutputStream o,String name,int... values) throws IOException {head(o,11,name);o.writeInt(values.length);for(int value:values)o.writeInt(value);}
    private static void messages(DataOutputStream o,List<String> lines) throws IOException {
        head(o,9,"messages");o.writeByte(8);o.writeInt(lines.size());for(String line:lines)o.writeUTF("{\"text\":\""+line+"\"}");
    }
}
