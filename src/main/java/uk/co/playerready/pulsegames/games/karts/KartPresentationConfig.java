package uk.co.playerready.pulsegames.games.karts;

import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;

/** Authored visual anchors; never used as race gates or collision geometry. */
final class KartPresentationConfig {
    static final int MAX_PROPS = 48;
    enum Kind {
        BOOST_PANEL(1), BOOST_PANEL_WALL(1), BOOST_PANEL_CEILING(1), JUMP_MARKER(1), CHECKPOINT_PILLAR(0), CHECKPOINT_BEAM(0),
        FINISH_PILLAR(0), FINISH_BEAM(0), PODIUM(2), TROPHY(2), MARSHAL(2), SPECTATOR_DRONE(2);
        final int priority;
        Kind(int priority) { this.priority = priority; }
        String model() { return "readycraft:kart_" + name().toLowerCase(Locale.ROOT); }
    }
    record Prop(int number, Kind kind, double x, double y, double z, float yaw, String binding) {
        String model() { return kind.model(); }
    }
    private final List<Prop> props;
    private final double range;
    private KartPresentationConfig(List<Prop> props, double range) { this.props=List.copyOf(props);this.range=range; }
    List<Prop> props() { return props; }
    double range() { return range; }
    boolean configured() { return !props.isEmpty(); }
    static KartPresentationConfig load(Map<String,Object> settings, Map<String,Cuboid> regions) {
        var found = new TreeMap<Integer,Prop>();
        for (var entry : settings.entrySet()) {
            String key=entry.getKey();
            if (!key.startsWith("kart-prop") || key.equals("kart-prop-range")) continue;
            if (!key.matches("kart-prop[1-9][0-9]?")) throw new IllegalArgumentException("Use kart-prop1, kart-prop2, ...");
            int number=Integer.parseInt(key.substring("kart-prop".length()));
            if (number>MAX_PROPS) throw new IllegalArgumentException("At most 48 kart props per map.");
            if (!(entry.getValue() instanceof String value)) throw new IllegalArgumentException(key+" must be kind,x,y,z,yaw[,binding].");
            String[] fields=value.split(",",-1);
            if(fields.length!=5&&fields.length!=6) throw new IllegalArgumentException(key+" must be kind,x,y,z,yaw[,binding].");
            Kind kind;
            try {kind=parseKind(fields[0]);}
            catch(IllegalArgumentException failure){throw new IllegalArgumentException(key+" has an unknown prop kind.");}
            double x=number(fields[1],key,-30_000_000,30_000_000),y=number(fields[2],key,-30_000_000,30_000_000),z=number(fields[3],key,-30_000_000,30_000_000);
            float yaw=(float)number(fields[4],key,-180,180);
            String binding=fields.length==6?fields[5].trim().toLowerCase(Locale.ROOT):"";
            if(!binding.isEmpty()) {
                boolean compatible=switch(kind) {
                    case BOOST_PANEL,BOOST_PANEL_WALL,BOOST_PANEL_CEILING -> binding.matches("boost[1-9][0-9]{0,2}");
                    case JUMP_MARKER -> binding.matches("jump[1-9][0-9]{0,2}");
                    case CHECKPOINT_PILLAR,CHECKPOINT_BEAM -> binding.matches("checkpoint[1-9][0-9]{0,2}");
                    case FINISH_PILLAR,FINISH_BEAM -> binding.equals("finish");
                    default -> false;
                };
                if(!compatible||!regions.containsKey(binding)) throw new IllegalArgumentException(key+" binding must name an existing matching gate/pad region.");
            }
            found.put(number,new Prop(number,kind,x,y,z,yaw,binding));
        }
        for(int number=1;number<=found.size();number++) if(!found.containsKey(number))throw new IllegalArgumentException("Number kart-props consecutively from 1.");
        double range=settings.containsKey("kart-prop-range")?number(settings.get("kart-prop-range"),"kart-prop-range",16,96):48;
        return new KartPresentationConfig(new ArrayList<>(found.values()),range);
    }
    private static Kind parseKind(String raw) {
        String name=raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch(name) {
            case "BOOST_WALL" -> Kind.BOOST_PANEL_WALL;
            case "BOOST_CEILING" -> Kind.BOOST_PANEL_CEILING;
            default -> Kind.valueOf(name);
        };
    }
    private static double number(Object raw,String key,double min,double max) {
        try {double value=Double.parseDouble(String.valueOf(raw).trim());if(Double.isFinite(value)&&value>=min&&value<=max)return value;}
        catch(NumberFormatException ignored){/* builder-facing validation below */}
        throw new IllegalArgumentException(key+" needs finite numbers in the supported range.");
    }
}
