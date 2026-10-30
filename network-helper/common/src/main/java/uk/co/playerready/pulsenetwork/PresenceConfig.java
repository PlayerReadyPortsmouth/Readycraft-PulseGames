package uk.co.playerready.pulsenetwork;

import java.util.*;

/** {@code presence.*} keys from the proxy's network.properties. */
public record PresenceConfig(boolean enabled, String join, String hop, String leave, String welcome, boolean serversOnly,
                             PresenceTracker.Rules rules, Set<String> hiddenRoles) {
    public static final String JOIN = "<gray>→ <name> joined the network", SWITCH = "<gray>⇄ <name> hopped to <server>",
            LEAVE = "<gray>← <name> left", WELCOME = "<gray>Welcome back, <name>!";

    public static PresenceConfig parse(Properties p) {
        String scope = p.getProperty("presence.scope", "network").trim().toLowerCase(Locale.ROOT);
        if (!scope.equals("network") && !scope.equals("servers")) throw new IllegalArgumentException("presence.scope must be network or servers");
        var roles = new HashSet<String>();
        for (String r : p.getProperty("presence.hidden-roles", "").split(",")) if (!r.isBlank()) roles.add(r.trim().toLowerCase(Locale.ROOT));
        var config = new PresenceConfig(Boolean.parseBoolean(p.getProperty("presence.enabled", "true")),
                p.getProperty("presence.join", JOIN), p.getProperty("presence.switch", SWITCH), p.getProperty("presence.leave", LEAVE),
                p.getProperty("presence.welcome-back", WELCOME), scope.equals("servers"),
                new PresenceTracker.Rules(seconds(p, "presence.switch-cooldown-seconds", 60), seconds(p, "presence.leave-min-stay-seconds", 20),
                        seconds(p, "presence.join-quiet-rejoin-seconds", 120)), Set.copyOf(roles));
        for (String t : List.of(config.join, config.hop, config.leave, config.welcome))
            if (t.length() > 300) throw new IllegalArgumentException("A presence template is too long");
        return config;
    }
    private static long seconds(Properties p, String key, long fallback) {
        long value = Long.parseLong(p.getProperty(key, Long.toString(fallback)).trim());
        if (value < 0 || value > 3600) throw new IllegalArgumentException(key + " must be 0 to 3600");
        return value * 1000;
    }
}
