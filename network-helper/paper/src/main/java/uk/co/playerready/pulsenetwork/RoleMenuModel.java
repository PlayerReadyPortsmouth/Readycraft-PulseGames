package uk.co.playerready.pulsenetwork;

import org.bukkit.Material;
import java.util.*;

/** Pure model of the /rolemenu: the actions on offer for a chosen player, their slots and labels. */
final class RoleMenuModel {
    enum Kind { SET, REMOVE, WHO }
    record Action(Kind kind, String role, String label, int slot) {}
    static final Set<String> GROUPS = Set.of("dev", "admin", "staff");
    static final int MENU_SIZE = 27, WHO_SLOT = 22;
    private RoleMenuModel() {}

    static String roleLabel(String role) {
        return switch (role) {
            case "dev" -> "Dev"; case "admin" -> "Admin"; case "staff" -> "Staff";
            case "venue:ports" -> "Venue: Portsmouth"; case "venue:plym" -> "Venue: Plymouth"; case "venue:tru" -> "Venue: Truro";
            case "venue:tau" -> "Venue: Taunton"; case "venue:exet" -> "Venue: Exeter"; default -> role;
        };
    }
    static Material material(String role) {
        return switch (role) { case "dev" -> Material.DIAMOND; case "admin" -> Material.REDSTONE; case "staff" -> Material.EMERALD; default -> Material.BLUE_BANNER; };
    }

    /** Row one: give each role (slots 0-7). Row two: take each role away (slots 9-16). Slot 22: view roles. */
    static List<Action> actions() {
        var out = new ArrayList<Action>();
        for (int i = 0; i < RoleMenuCommands.ROLES.size(); i++) {
            String r = RoleMenuCommands.ROLES.get(i);
            out.add(new Action(Kind.SET, r, "Give " + roleLabel(r), i));
            out.add(new Action(Kind.REMOVE, r, "Take away " + roleLabel(r), 9 + i));
        }
        out.add(new Action(Kind.WHO, null, "View roles", WHO_SLOT));
        return out;
    }

    /** Command fields for an action against {@code name}, or empty if the name or role is not valid. */
    static Optional<List<String>> fields(Action a, String name) {
        return RoleMenuCommands.fields(switch (a.kind()) { case SET -> "set"; case REMOVE -> "remove"; case WHO -> "who"; }, name, a.role());
    }

    /** Player-head slots: online players other than the viewer, sorted by name, at most 45. */
    static List<String> pickable(Collection<String> online, String self) {
        return online.stream().filter(n -> !n.equalsIgnoreCase(self) && RoleMenuCommands.validName(n))
                .sorted(String.CASE_INSENSITIVE_ORDER).limit(45).toList();
    }

    static String confirmText(String name, String role) { return "Take the " + roleLabel(role) + " role away from " + name + "?"; }
    static String playerFormContent() { return "Pick a player, or close this and type /rolemenu <name> for someone who is offline."; }
}
