package uk.co.playerready.pulsegames.games.karts;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KartShowcaseIntegrationTest {
    KartArcadeTest fixture;
    YamlConfiguration config,settings;
    @BeforeEach void setup() throws Exception {
        fixture=new KartArcadeTest();fixture.setup();
        config=new YamlConfiguration();settings=new YamlConfiguration();
        when(fixture.game.plugin().getConfig()).thenReturn(config);
        when(fixture.game.arena().settings()).thenReturn(settings);
        var checkpoint=new Cuboid(-1,64,10,1,66,10);
        var finish=new Cuboid(-1,64,20,1,66,20);
        when(fixture.game.arena().regions()).thenReturn(Map.of("checkpoint1",checkpoint,"finish",finish));
        when(fixture.game.arena().regionsByPrefix("checkpoint")).thenReturn(List.of(checkpoint));
        when(fixture.game.arena().region("finish")).thenReturn(finish);
    }
    @Test void excludedLobbyIgnoresOptionalPropsButEligibleLobbyValidatesThem() {
        var canary=fixture.kart(0,0,12).driver;var other=fixture.kart(3,0,12).driver;
        settings.set("kart-prop1","invalid props");
        assertNull(fixture.logic.startProblem(List.of(canary)));
        config.set("karts.showcase.enabled",true);
        assertNull(fixture.logic.startProblem(List.of(canary)),"empty allowlist keeps legacy rules");
        config.set("karts.showcase.canary-players",List.of(canary.getUniqueId().toString()));
        assertNotNull(fixture.logic.startProblem(List.of(canary)));
        assertNull(fixture.logic.startProblem(List.of(canary,other)),"partial canary lobby keeps legacy rules");
        settings.set("kart-prop1","checkpoint_pillar,0,64,10,0,checkpoint1");
        assertNull(fixture.logic.startProblem(List.of(canary)));
    }
    @Test void revokedShowcaseCleansBeforeForcedCancellationAndDoesNotMoveKart() throws Exception {
        var kart=fixture.kart(0,0,12);var props=mock(KartTrackProps.class);
        fixture.field("showcaseEnabled",true);fixture.field("trackHumanRacers",List.of(kart.driver.getUniqueId()));
        fixture.field("trackProps",props);var before=kart.motion;
        fixture.drive();
        assertEquals(before,kart.motion);assertTrue(fixture.karts.isEmpty());
        var order=inOrder(props,kart.seat,fixture.game);
        order.verify(props).close();order.verify(kart.seat).remove();order.verify(fixture.game).end(List.of(),"forced");
        verify(props,never()).update(anyLong(),anyCollection());
    }
    @Test void originalHumanRosterKeepsShowcaseActiveAfterFinisherLeavesRace() throws Exception {
        var canary=java.util.UUID.randomUUID();config.set("karts.showcase.enabled",true);
        config.set("karts.showcase.canary-players",List.of(canary.toString()));
        fixture.field("showcaseEnabled",true);fixture.field("trackHumanRacers",List.of(canary));
        when(fixture.game.alivePlayers()).thenReturn(List.of());fixture.drive();
        verify(fixture.game,never()).end(anyList(),anyString());
    }
    @Test void actualBoostEntryPulsesButStayingInsideDoesNot() throws Exception {
        fixture.arcade("boost1",new Cuboid(-1,64,3,1,66,5));
        var kart=fixture.kart(0,2.8,12);kart.input=new KartPhysics.Input(1,0);
        var props=mock(KartTrackProps.class);fixture.field("trackProps",props);
        fixture.drive();fixture.drive();verify(props,times(1)).pulse(eq("boost1"),anyLong());
    }
    @Test void propCloseFailureStillRemovesChassisAndOtherVisuals() throws Exception {
        var kart=fixture.kart(0,0,12);var props=mock(KartTrackProps.class);fixture.field("trackProps",props);
        doThrow(new IllegalStateException("provider failure")).when(props).close();
        assertThrows(IllegalStateException.class,fixture.logic::onCleanup);
        verify(kart.visual).close();verify(kart.seat).remove();assertTrue(fixture.karts.isEmpty());
    }
}
