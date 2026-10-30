package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoleMenuModelTest {
    @Test void composesTheSameCommandsTheProxyAccepts() {
        assertEquals(Optional.of("/role set Kit venue:ports"), RoleMenuCommands.line("set", "Kit", "venue:ports"));
        assertEquals(Optional.of("/role remove .BedrockKid staff"), RoleMenuCommands.line("remove", ".BedrockKid", "staff"));
        assertEquals(Optional.of("/role who Kit"), RoleMenuCommands.line("who", "Kit", null));
        assertEquals(Optional.of("/role confirm"), RoleMenuCommands.line("confirm", null, null));
    }
    @Test void refusesUnlistedRolesBadNamesAndOtherCommands() {
        assertTrue(RoleMenuCommands.line("set", "Kit", "owner").isEmpty());
        assertTrue(RoleMenuCommands.line("set", "Kit Two", "dev").isEmpty());
        assertTrue(RoleMenuCommands.line("ban", "Kit", null).isEmpty());
        assertEquals(Optional.of("/role hide Kit"), RoleMenuCommands.line("hide", "Kit", null));
        assertEquals(Optional.of("identity Kit"), RoleMenuCommands.fromFields(List.of("identity", "Kit")));
        assertTrue(RoleMenuCommands.fromFields(List.of("set", "Kit", "dev", "extra")).isEmpty());
        assertEquals(Optional.of("role set Kit dev"), RoleMenuCommands.fromFields(List.of("set", "Kit", "dev")));
        assertEquals(Optional.of("role confirm"), RoleMenuCommands.fromFields(List.of("confirm")));
        assertTrue(RoleMenuCommands.fromFields(List.of("confirm", "x")).isEmpty());
    }
    @Test void everyRoleHasGiveAndTakeAwayInDistinctSlotsPlusViewRoles() {
        var all = RoleMenuModel.actions();
        assertEquals(RoleMenuCommands.ROLES.size() * 2 + 1, all.size());
        assertEquals(all.size(), all.stream().map(RoleMenuModel.Action::slot).distinct().count());
        assertTrue(all.stream().allMatch(a -> a.slot() < RoleMenuModel.MENU_SIZE));
        var venue = all.stream().filter(a -> "venue:ports".equals(a.role()) && a.kind() == RoleMenuModel.Kind.SET).findFirst().orElseThrow();
        assertEquals(Optional.of(List.of("set", "Kit", "venue:ports")), RoleMenuModel.fields(venue, "Kit"));
        assertEquals("Give Venue: Portsmouth", venue.label());
        assertEquals(Optional.of(List.of("who", "Kit")), RoleMenuModel.fields(all.getLast(), "Kit"));
    }
    @Test void playerListExcludesSelfAndBadNamesAndIsSorted() {
        assertEquals(List.of("alice", "Bob"), RoleMenuModel.pickable(List.of("Bob", "me", "alice", "bad name"), "ME"));
    }
}
