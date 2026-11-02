package uk.co.playerready.pulsegames.games.karts;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KartTrackPropsTest {
    static final class Handle implements KartTrackProps.Visual {
        boolean active=true,failClose;int closes;
        public boolean active(){return active;}
        public void close(){closes++;if(failClose)throw new IllegalStateException("provider close failed");active=false;}
    }
    static final class Backend implements KartTrackProps.Backend {
        boolean loaded=true,available=true;int probes,markers,pulses;
        final List<Handle> created=new ArrayList<>();final List<Integer> numbers=new ArrayList<>();
        Set<UUID> lastAudience=Set.of();
        public boolean loaded(KartPresentationConfig.Prop p){return loaded;}
        public boolean available(String model,Set<UUID> audience){probes++;return available;}
        public KartTrackProps.Visual create(KartPresentationConfig.Prop p,Set<UUID> audience){var h=new Handle();created.add(h);numbers.add(p.number());lastAudience=audience;return h;}
        public void marker(KartPresentationConfig.Prop p,Set<UUID> audience,boolean pulse){markers++;if(pulse)pulses++;lastAudience=audience;}
    }
    KartPresentationConfig config(){return KartPresentationConfig.load(Map.of("kart-prop1","boost_panel,0,64,0,0,boost1"),Map.of("boost1",new Cuboid(-2,64,-2,2,66,2)));}
    KartTrackProps.Viewer viewer(UUID id,boolean ready){return new KartTrackProps.Viewer(id,0,64,2,ready);}
    @Test void readyNearbyAudienceGetsModelAndLeavingOrUnloadingCleansIt() {
        var backend=new Backend();var props=new KartTrackProps(config(),backend,ignored->{});var id=UUID.randomUUID();
        props.updateViews(0,List.of(viewer(id,true)));assertEquals(1,backend.created.size());assertEquals(Set.of(id),backend.lastAudience);
        props.updateViews(10,List.of(new KartTrackProps.Viewer(id,300,64,0,true)));assertFalse(backend.created.getFirst().active);
        props.updateViews(20,List.of(viewer(id,true)));assertEquals(2,backend.created.size());
        backend.loaded=false;props.updateViews(30,List.of(viewer(id,true)));assertFalse(backend.created.get(1).active);
    }
    @Test void missingModelsAndUnreadyPacksGetMarkersWithoutUnboundedRetry() {
        var backend=new Backend();backend.available=false;var props=new KartTrackProps(config(),backend,ignored->{});var id=UUID.randomUUID();
        for(int tick=0;tick<100;tick+=10)props.updateViews(tick,List.of(viewer(id,true)));
        assertEquals(1,backend.probes);assertEquals(5,backend.markers);assertTrue(backend.created.isEmpty());
        backend.available=true;props.updateViews(100,List.of(viewer(id,true)));assertEquals(1,backend.created.size());
        props.updateViews(110,List.of(viewer(id,false)));assertFalse(backend.created.getFirst().active);
        props.updateViews(120,List.of(viewer(id,false)));assertEquals(2,backend.probes);assertTrue(backend.markers>5);
    }
    @Test void mixedPackAudienceRetainsFallbackForUnreadyViewer() {
        var backend=new Backend();var props=new KartTrackProps(config(),backend,ignored->{});
        var ready=UUID.randomUUID();var unready=UUID.randomUUID();
        props.updateViews(0,List.of(viewer(ready,true),viewer(unready,false)));
        assertEquals(1,backend.created.size());assertEquals(1,backend.markers);assertEquals(Set.of(unready),backend.lastAudience);
    }
    @Test void gatePulseIsBoundedAndDecorationsDoNotInventCheckpointState() {
        var backend=new Backend();var props=new KartTrackProps(config(),backend,ignored->{});var id=UUID.randomUUID();
        props.updateViews(0,List.of(viewer(id,true)));assertEquals(0,backend.markers);
        props.pulse("checkpoint1",1);props.updateViews(10,List.of(viewer(id,true)));assertEquals(0,backend.pulses);
        props.pulse("boost1",11);props.updateViews(20,List.of(viewer(id,true)));assertEquals(1,backend.pulses);
        props.updateViews(30,List.of(viewer(id,true)));assertEquals(1,backend.pulses);
    }
    @Test void failedCloseRetainsHandleNeverCreatesOrphanReplacementAndRetriesOnCleanup() {
        var backend=new Backend();var props=new KartTrackProps(config(),backend,ignored->{});var id=UUID.randomUUID();
        props.updateViews(0,List.of(viewer(id,true)));var h=backend.created.getFirst();h.failClose=true;
        props.updateViews(10,List.of(viewer(UUID.randomUUID(),true)));assertEquals(1,backend.created.size());
        assertThrows(IllegalStateException.class,props::close);assertTrue(h.active);
        props.updateViews(20,List.of(viewer(id,true)));assertEquals(1,backend.created.size());
        h.failClose=false;assertDoesNotThrow(props::close);assertFalse(h.active);
    }
    @Test void inactiveHandleWithFailedCleanupRetainsOwnershipAndRestoresMarkers() {
        var backend=new Backend();var props=new KartTrackProps(config(),backend,ignored->{});var id=UUID.randomUUID();
        props.updateViews(0,List.of(viewer(id,true)));
        var handle=backend.created.getFirst();handle.active=false;handle.failClose=true;
        props.updateViews(20,List.of(viewer(id,true)));
        assertEquals(1,backend.created.size());assertEquals(1,handle.closes);
        assertEquals(1,backend.markers);assertEquals(Set.of(id),backend.lastAudience);
        handle.failClose=false;props.close();
    }
    @Test void legacyPickupsReserveCapacityEvenWhenTheirChunksAreNotLoadedYet() {
        assertEquals(12,KartTrackProps.capacityAfterPickups(0));
        assertEquals(12,KartTrackProps.capacityAfterPickups(20));
        assertEquals(7,KartTrackProps.capacityAfterPickups(25));
        assertEquals(0,KartTrackProps.capacityAfterPickups(32));
        assertEquals(0,KartTrackProps.capacityAfterPickups(33));
        Map<String,Object> settings=new HashMap<>();
        for(int i=1;i<=12;i++)settings.put("kart-prop"+i,"boost_panel,0,64,"+i+",0");
        var config=KartPresentationConfig.load(settings,Map.of());
        var backend=new Backend();var props=new KartTrackProps(config,backend,ignored->{},25);
        props.updateViews(0,List.of(viewer(UUID.randomUUID(),true)));
        assertEquals(7,backend.created.size());assertEquals(5,backend.markers);
        props.close();
        var full=new Backend();var noSlots=new KartTrackProps(config,full,ignored->{},32);
        noSlots.updateViews(0,List.of(viewer(UUID.randomUUID(),true)));
        assertEquals(0,full.probes);assertEquals(0,full.created.size());assertEquals(12,full.markers);
    }
    @Test void gatePropsHavePriorityAndActiveModelsAndAudienceAreBounded() {
        Map<String,Object> settings=new HashMap<>();for(int i=1;i<=20;i++)settings.put("kart-prop"+i,"marshal,0,64,"+i+",0");
        settings.put("kart-prop21","checkpoint_pillar,0,64,22,0");
        var backend=new Backend();var props=new KartTrackProps(KartPresentationConfig.load(settings,Map.of()),backend,ignored->{});
        List<KartTrackProps.Viewer> viewers=new ArrayList<>();for(int i=0;i<80;i++)viewers.add(viewer(UUID.randomUUID(),true));
        props.updateViews(0,viewers);assertEquals(12,backend.created.size());assertEquals(21,backend.numbers.getFirst());assertEquals(32,backend.lastAudience.size());
        props.close();assertTrue(backend.created.stream().noneMatch(h->h.active));
    }
}
