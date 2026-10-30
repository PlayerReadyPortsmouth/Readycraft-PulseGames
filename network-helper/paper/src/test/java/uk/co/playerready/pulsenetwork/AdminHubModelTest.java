package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsenetwork.AdminHubModel.*;

class AdminHubModelTest {
    private static Env env(Set<String> plugins, boolean bridge) {
        return new Env() {
            public boolean pluginEnabled(String n) { return plugins.contains(n); }
            public boolean bridgeEnabled() { return bridge; }
        };
    }

    @Test void lobbyShowsTheWhitelistAndOnlyPresentPluginSections() {
        assertEquals(List.of(Section.PLAYERS, Section.WHITELIST), visible(env(Set.of("UltimateWhitelist"), false), true));
        assertEquals(List.of(Section.PLAYERS, Section.NETWORK, Section.GAMES, Section.ADDON, Section.PUMPKINS),
                visible(env(Set.of("PulseGames", "PulseAddon", "PumpkinHunt"), true), true));
    }
    @Test void aBareServerStillHasPlayersAndNothingElse() {
        assertEquals(List.of(Section.PLAYERS), visible(env(Set.of(), false), true));
    }
    @Test void announceShowsForDevAndAdminWheneverTheBridgeIsEnabled() {
        assertEquals(List.of(Section.PLAYERS, Section.NETWORK), visible(env(Set.of(), true), true));
        assertEquals(List.of(Section.PLAYERS), visible(env(Set.of(), true), false), "staff do not see Announce");
        assertEquals(List.of(Section.PLAYERS), visible(env(Set.of(), false), true), "no bridge, no Announce");
        assertEquals(List.of("chat", "title", "bar"), ANNOUNCE_STYLES.stream().map(AnnounceStyle::style).toList());
        assertEquals(List.of("Chat", "Title", "Action bar"), ANNOUNCE_STYLES.stream().map(AnnounceStyle::label).toList());
        ANNOUNCE_STYLES.forEach(st -> assertEquals(st.style(), Announce.style(st.style())));
    }
    @Test void pluginButtonsRunTheirOwnAdminCommands() {
        assertEquals("pulse adminmenu", plugin(Section.GAMES).orElseThrow().command());
        assertEquals("pulseadmin menu", plugin(Section.ADDON).orElseThrow().command());
        assertEquals("pumpkins admin", plugin(Section.PUMPKINS).orElseThrow().command());
        assertTrue(plugin(Section.PLAYERS).isEmpty());
    }
    @Test void whitelistCommandsAreComposedFromValidNamesOnly() {
        assertEquals(Optional.of("uw add .BedrockKid"), whitelistCommand(WhitelistOp.ADD, ".BedrockKid"));
        assertEquals(Optional.of("uw remove Kit_1"), whitelistCommand(WhitelistOp.REMOVE, "Kit_1"));
        assertEquals(Optional.of("uw list"), whitelistCommand(WhitelistOp.LIST, null));
        assertEquals(Optional.of("uw on"), whitelistCommand(WhitelistOp.ON, null));
        assertEquals(Optional.of("uw off"), whitelistCommand(WhitelistOp.OFF, "ignored"));
        assertTrue(whitelistCommand(WhitelistOp.ADD, "Kit; op Kit").isEmpty());
        assertTrue(whitelistCommand(WhitelistOp.REMOVE, "").isEmpty());
    }
    @Test void gatingUsesDirectGroupsAndKeepsRiskyActionsForDevAndAdmin() {
        assertTrue(allowed(Gate.HUB, Set.of("staff")));
        assertFalse(allowed(Gate.ADMIN, Set.of("staff")));
        assertTrue(allowed(Gate.ADMIN, Set.of("admin")));
        assertTrue(allowed(Gate.ADMIN, Set.of("dev", "staff")));
        assertFalse(allowed(Gate.HUB, Set.of()));
        assertFalse(allowed(Gate.HUB, Set.of("default", "venue:ports")));
        assertEquals(Gate.HUB, gate(WhitelistOp.ADD));
        assertEquals(Gate.ADMIN, gate(WhitelistOp.REMOVE));
        assertEquals(Gate.ADMIN, gate(WhitelistOp.ON));
        assertEquals(Gate.ADMIN, gate(WhitelistOp.OFF));
    }
    @Test void styleGridHasEveryStyleOnceInDistinctSlotsAndMarksHeldOnes() {
        var held = Set.of("pulse.namestyle.red", "pulse.namestyle.rainbow");
        var cells = grid(held::contains);
        assertEquals(NameStyle.styleIds().size(), cells.size());
        assertEquals(16, cells.size());
        assertEquals(cells.size(), cells.stream().map(Cell::slot).distinct().count());
        assertTrue(cells.stream().allMatch(c -> c.slot() < BACK_SLOT));
        assertEquals(Set.of("red", "rainbow"), new HashSet<>(cells.stream().filter(Cell::held).map(Cell::id).toList()));
        assertEquals("pulse.namestyle.sunset", permission("sunset"));
    }
    @Test void auditLineHasActorActionTarget() {
        assertEquals("ADMIN Aurora grant style red Kit", audit("Aurora", "grant style red", "Kit"));
        assertEquals("ADMIN Aurora announce -", audit("Aurora", "announce", null));
        assertEquals(9, menuSize(1));
        assertEquals(54, menuSize(80));
    }
}
