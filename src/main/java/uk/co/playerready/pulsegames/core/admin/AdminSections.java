package uk.co.playerready.pulsegames.core.admin;

import java.util.ArrayList;
import java.util.List;

/** Which sections the admin menu shows. Pure, so the rules are tested without a server. */
public enum AdminSections {
    GAMES("Games and arenas"),
    MATCHES("Matches running"),
    PLAYERS("Players"),
    EVENTS("Events"),
    BOTS("Bots"),
    SERVER("Server");

    private final String label;

    AdminSections(String label) { this.label = label; }

    public String label() { return label; }

    /** Nobody without the admin permission sees anything; Players needs levels running and Events needs events switched on. */
    public static List<AdminSections> visible(boolean admin, boolean levelsRunning, boolean eventsEnabled) {
        List<AdminSections> out = new ArrayList<>();
        if (!admin) return out;
        for (AdminSections s : values()) {
            if (s == PLAYERS && !levelsRunning) continue;
            if (s == EVENTS && !eventsEnabled) continue;
            out.add(s);
        }
        return out;
    }
}
