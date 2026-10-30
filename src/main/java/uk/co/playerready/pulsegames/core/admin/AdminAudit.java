package uk.co.playerready.pulsegames.core.admin;

import java.util.function.Consumer;

/** Console audit line for every action taken from the admin menu: {@code PGADMIN <actor> <action> <target>}. */
public final class AdminAudit {
    private final Consumer<String> sink;

    public AdminAudit(Consumer<String> sink) { this.sink = sink; }

    public static String line(String actor, String action, String target) {
        return "PGADMIN " + token(actor) + " " + token(action) + " " + token(target);
    }

    /** One word each, so the line stays easy to grep: whitespace becomes an underscore, empty becomes a dash. */
    static String token(String value) {
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", "_");
        return clean.isEmpty() ? "-" : clean;
    }

    public void log(String actor, String action, String target) { sink.accept(line(actor, action, target)); }
}
