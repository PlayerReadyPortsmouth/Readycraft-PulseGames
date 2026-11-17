package uk.co.playerready.pulsegames.games.karts;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class KartPresentationConfigTest {
    final Map<String,Cuboid> regions=Map.of("boost1",new Cuboid(0,64,0,4,66,4),"checkpoint1",new Cuboid(0,64,10,4,68,11));
    @Test void explicitPropsCarryExactModelYawAndOptionalBinding() {
        var config=KartPresentationConfig.load(Map.of("kart-prop1","boost_panel,1,64,2,90,boost1","kart-prop2","marshal,8,64,3,-90","kart-prop-range",32),regions);
        assertEquals(2,config.props().size());assertEquals(32,config.range());
        var p=config.props().getFirst();assertEquals("readycraft:kart_boost_panel",p.model());assertEquals(90,p.yaw());assertEquals("boost1",p.binding());
        assertEquals("",config.props().get(1).binding());
        assertFalse(KartPresentationConfig.load(Map.of(),regions).configured());
    }
    @Test void bakedWallAndCeilingVariantsRemainBoundToRealBoostRegions() {
        var config=KartPresentationConfig.load(Map.of("kart-prop1","boost-wall,1,64,2,90,boost1","kart-prop2","boost-ceiling,1,70,2,0,boost1"),regions);
        assertEquals("readycraft:kart_boost_panel_wall",config.props().get(0).model());
        assertEquals("readycraft:kart_boost_panel_ceiling",config.props().get(1).model());
    }
    @Test void malformedUnsupportedOrUnboundedAuthoringIsRejected() {
        for(var settings:java.util.List.<Map<String,Object>>of(
                Map.of("kart-prop2","marshal,0,64,0,0"),Map.of("kart-prop0","marshal,0,64,0,0"),
                Map.of("kart-prop49","marshal,0,64,0,0"),Map.of("kart-prop1","villager,0,64,0,0"),
                Map.of("kart-prop1","marshal,NaN,64,0,0"),Map.of("kart-prop1","marshal,1e20,64,0,0"),
                Map.of("kart-prop1","marshal,0,64,0,0,boost1"),Map.of("kart-prop1","boost_panel,0,64,0,0,jump1"),
                Map.of("kart-prop1","boost_panel,0,64,0,0,boost2"),Map.of("kart-prop1","boost_panel,0,64,0,0,boost1,extra"),
                Map.of("kart-prop1",42),Map.of("kart-prop-range",100)))
            assertThrows(IllegalArgumentException.class,()->KartPresentationConfig.load(settings,regions),settings.toString());
    }
}
