package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.content.PulseContentBridge;
import java.util.*;
import java.util.function.Consumer;

/** Optional, disposable decoration. Missing models never change a race's rules or physics. */
final class KartTrackProps implements AutoCloseable {
    static final int UPDATE_TICKS=10, RETRY_TICKS=100, MAX_ACTIVE=12, MAX_VIEWERS=32;
    record Viewer(UUID id,double x,double y,double z,boolean ready) {}
    interface Visual extends AutoCloseable { boolean active(); }
    interface Backend {
        boolean loaded(KartPresentationConfig.Prop prop);
        boolean available(String model,Set<UUID> viewers);
        Visual create(KartPresentationConfig.Prop prop,Set<UUID> viewers);
        void marker(KartPresentationConfig.Prop prop,Set<UUID> viewers,boolean pulse);
    }
    private static final class Live {
        Visual visual;
        Set<UUID> audience=Set.of();
        long retryAt;
    }
    private final KartPresentationConfig config;
    private final Backend backend;
    private final int activeBudget;
    private final Consumer<String> warning;
    private final Map<Integer,Live> live=new HashMap<>();
    private final Map<String,Long> pulses=new HashMap<>();
    private final Map<UUID,Player> players=new HashMap<>();
    private final PulseContentBridge content;
    private final World world;
    private boolean closed;
    private long nextWarning;

    KartTrackProps(KartPresentationConfig config,Backend backend,Consumer<String> warning) {
        this(config,backend,warning,0);
    }
    KartTrackProps(KartPresentationConfig config,Backend backend,Consumer<String> warning,int reservedLegacyBoxes) {
        this.config=config;this.backend=backend;this.warning=warning;this.content=null;this.world=null;
        this.activeBudget=capacityAfterPickups(reservedLegacyBoxes);
    }
    /** Reserve every authored legacy pickup, including ones whose chunks/viewers arrive later. */
    static int capacityAfterPickups(int count) { return Math.min(MAX_ACTIVE,32-Math.min(32,Math.max(0,count))); }
    KartTrackProps(World world,PulseContentBridge content,KartPresentationConfig config,Consumer<String> warning,int reservedLegacyBoxes) {
        this.world=world;this.content=content;this.config=config;this.warning=warning;
        this.activeBudget=capacityAfterPickups(reservedLegacyBoxes);
        this.backend=new Backend() {
            public boolean loaded(KartPresentationConfig.Prop p) {
                return p.y()>=world.getMinHeight()&&p.y()<world.getMaxHeight()
                        &&world.isChunkLoaded((int)Math.floor(p.x())>>4,(int)Math.floor(p.z())>>4);
            }
            public boolean available(String model,Set<UUID> audience) {
                return content!=null&&content.worldVisualStartProblem(model,audience.stream().map(players::get).filter(Objects::nonNull).toList())==null;
            }
            public Visual create(KartPresentationConfig.Prop p,Set<UUID> audience) {
                var handle=content.createWorldVisual(p.model(),new Location(world,p.x(),p.y(),p.z(),p.yaw(),0),audience);
                return new Visual() {
                    public boolean active(){return handle.active();}
                    public void close()throws Exception{handle.close();}
                };
            }
            public void marker(KartPresentationConfig.Prop p,Set<UUID> audience,boolean pulse) {
                if(p.kind().priority==2&&!pulse)return;
                Location at=new Location(world,p.x(),p.y()+.3,p.z());
                for(UUID id:audience) {
                    Player player=players.get(id);
                    if(player!=null&&player.isOnline()&&player.getWorld()==world)
                        player.spawnParticle(pulse?Particle.END_ROD:Particle.ELECTRIC_SPARK,at,pulse?6:2,.15,pulse?.3:.08,.15,0);
                }
            }
        };
    }
    /** Root supplies only match participants/spectators, excluding NPC drivers. */
    void update(long tick,Collection<Player> audience) {
        if(closed||tick%UPDATE_TICKS!=0)return;
        players.clear();
        List<Viewer> views=new ArrayList<>();
        for(Player p:audience) if(p.isOnline()&&p.getWorld()==world&&!p.hasMetadata("NPC")) {
            players.put(p.getUniqueId(),p);var at=p.getLocation();
            views.add(new Viewer(p.getUniqueId(),at.getX(),at.getY(),at.getZ(),content!=null&&content.worldVisualViewerReady(p)));
        }
        updateViews(tick,views);
    }
    void pulse(String binding,long tick) {
        if(!closed&&binding!=null&&!binding.isBlank()&&config.props().stream().anyMatch(p->p.binding().equals(binding)))pulses.put(binding,tick+12);
    }
    void updateViews(long tick,Collection<Viewer> audience) {
        if(closed||tick%UPDATE_TICKS!=0)return;
        pulses.entrySet().removeIf(e->e.getValue()<tick);
        var ordered=new ArrayList<>(config.props());
        ordered.sort(Comparator.comparingInt((KartPresentationConfig.Prop p)->p.kind().priority)
                .thenComparingDouble(p->audience.stream().mapToDouble(v->distance(p,v)).min().orElse(Double.MAX_VALUE))
                .thenComparingInt(KartPresentationConfig.Prop::number));
        int selected=0;
        for(var prop:ordered) {
            Live state=live.computeIfAbsent(prop.number(),ignored->new Live());
            List<Viewer> nearby=backend.loaded(prop)?audience.stream().filter(v->distance(prop,v)<=config.range()*config.range())
                    .sorted(Comparator.comparingDouble((Viewer v)->distance(prop,v)).thenComparing(v->v.id().toString())).limit(MAX_VIEWERS).toList():List.of();
            Set<UUID> visible=new LinkedHashSet<>(),ready=new LinkedHashSet<>();
            nearby.forEach(v->{visible.add(v.id());if(v.ready())ready.add(v.id());});
            boolean wanted=!ready.isEmpty()&&selected<activeBudget;
            if(wanted)selected++;
            if(state.visual!=null&&(!wanted||!state.audience.equals(ready)||!active(state.visual,tick)))close(state,tick);
            if(wanted&&state.visual==null&&tick>=state.retryAt&&live.values().stream().filter(s->s.visual!=null).count()<activeBudget) {
                state.retryAt=tick+RETRY_TICKS;
                try {
                    if(backend.available(prop.model(),ready)) {
                        state.visual=backend.create(prop,Set.copyOf(ready));state.audience=Set.copyOf(ready);
                    }
                } catch(RuntimeException|LinkageError failure){warn(tick,"Kart prop model unavailable; keeping track markers: "+prop.model());}
            }
            boolean pulse=pulses.getOrDefault(prop.binding(),Long.MIN_VALUE)>=tick;
            Set<UUID> markers=new LinkedHashSet<>(visible);
            if(!pulse&&state.visual!=null&&active(state.visual,tick))markers.removeAll(state.audience);
            if(!markers.isEmpty()&&(pulse||tick%20==0))backend.marker(prop,Set.copyOf(markers),pulse);
        }
    }
    private static double distance(KartPresentationConfig.Prop p,Viewer v) {
        double x=p.x()-v.x(),y=p.y()-v.y(),z=p.z()-v.z();return x*x+y*y+z*z;
    }
    private boolean active(Visual visual,long tick) {
        try {return visual.active();}
        catch(RuntimeException|LinkageError failure){warn(tick,"Kart prop provider unavailable; closing visual.");return false;}
    }
    private boolean close(Live state,long tick) {
        try {state.visual.close();state.visual=null;state.audience=Set.of();state.retryAt=0;return true;}
        catch(Exception|LinkageError failure){warn(tick,"Kart prop cleanup will retry; no replacement model created.");return false;}
    }
    private void warn(long tick,String message){if(tick>=nextWarning){warning.accept(message);nextWarning=tick>Long.MAX_VALUE-RETRY_TICKS?Long.MAX_VALUE:tick+RETRY_TICKS;}}
    @Override public void close() {
        closed=true;boolean clean=true;
        for(Live state:live.values())if(state.visual!=null)clean&=close(state,Long.MAX_VALUE);
        pulses.clear();players.clear();
        if(!clean)throw new IllegalStateException("Kart prop cleanup failed; handles retained for retry");
        live.clear();
    }
}
