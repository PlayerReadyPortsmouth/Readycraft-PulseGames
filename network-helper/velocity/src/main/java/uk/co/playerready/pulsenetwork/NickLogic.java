package uk.co.playerready.pulsenetwork;

import uk.co.playerready.pulsenetwork.RoleLogic.Level;

/** Pure gating for {@code /nick}. Levels come from direct group membership, like {@code /role}. */
final class NickLogic {
    private NickLogic() {}

    /**
     * Dev, admin and staff may set their own nickname and other people's, except: staff cannot change a dev or an admin,
     * and only a dev can change another admin. Everyone else cannot use /nick at all. The console may do anything.
     */
    static boolean mayChange(Level actor, boolean self, Level target) {
        if (actor == Level.CONSOLE || actor == Level.DEV) return true;
        if (actor == Level.NONE) return false;
        if (self) return true;
        return switch (actor) {
            case ADMIN -> target != Level.ADMIN;
            case STAFF -> target == Level.NONE || target == Level.STAFF;
            default -> false;
        };
    }
}
