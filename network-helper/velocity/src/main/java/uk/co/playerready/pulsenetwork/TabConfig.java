package uk.co.playerready.pulsenetwork;

import java.util.*;

/** Proxy tab list settings from network.properties ({@code tab.*}). Everything defaults off or conservative. */
record TabConfig(boolean enabled, List<String> order, Set<String> excluded, Map<String, String> names, boolean headers,
                 String headerFormat, String header, String footer, String staffLine, List<String> tips, int tipSeconds,
                 String bedrockMarker, boolean higherFirst, long minIntervalMs, long animationMs, NameStyle.Tags tags,
                 boolean columns, boolean showEmpty, String filler) {
    static final String HEADER = "<gradient:aqua:blue><bold>ReadyCraft</bold></gradient>\n<gray>You are on <white><server></white> · <white><network_online></white> online</gray>";
    static final String FOOTER = "<gray><counts></gray>\n<status_line>\n<yellow><tip></yellow>";
    static final String STAFF = "<dark_gray>Client: <client> · <version></dark_gray>";
    static final String HEADER_FORMAT = "<dark_gray>— <white><server></white> (<count>) —</dark_gray>";

    static TabConfig parse(Properties p) {
        var names = new LinkedHashMap<String, String>();
        for (String pair : list(p.getProperty("tab.server-names", ""), ",")) {
            int eq = pair.indexOf('=');
            if (eq < 1 || eq == pair.length() - 1) throw new IllegalArgumentException("Invalid tab.server-names entry");
            String id = pair.substring(0, eq).trim(), name = pair.substring(eq + 1).trim();
            if (!Wire.id(id) || name.length() > 32) throw new IllegalArgumentException("Invalid tab.server-names entry");
            names.put(id, name);
        }
        var tips = list(p.getProperty("tab.tips", ""), "|");
        if (tips.size() > 20 || tips.stream().anyMatch(t -> t.length() > 300)) throw new IllegalArgumentException("Too many or too long tab.tips");
        var config = new TabConfig(Boolean.parseBoolean(p.getProperty("tab.enabled", "false")), ids(p.getProperty("tab.server-order", "")),
                Set.copyOf(ids(p.getProperty("tab.excluded-servers", ""))), Map.copyOf(names),
                Boolean.parseBoolean(p.getProperty("tab.group-headers", "false")),
                p.getProperty("tab.group-header-format", HEADER_FORMAT), p.getProperty("tab.header", HEADER), p.getProperty("tab.footer", FOOTER),
                p.getProperty("tab.staff-line", STAFF), tips, Math.max(5, Math.min(600, Integer.parseInt(p.getProperty("tab.tip-seconds", "30")))),
                p.getProperty("tab.bedrock-marker", "<aqua>[B]</aqua> "), Boolean.parseBoolean(p.getProperty("tab.list-order-higher-first", "true")),
                Math.max(250, Math.min(30_000, Long.parseLong(p.getProperty("tab.min-interval-ms", "1000")))),
                Math.max(500, Math.min(10_000, Long.parseLong(p.getProperty("tab.animation-ms", "1000")))), tags(p),
                layout(p.getProperty("tab.layout", "list")), Boolean.parseBoolean(p.getProperty("tab.columns.show-empty", "false")),
                p.getProperty("tab.columns.filler", ""));
        for (String template : List.of(config.header, config.footer, config.staffLine, config.headerFormat, config.bedrockMarker, config.filler))
            if (template.length() > 2000) throw new IllegalArgumentException("A tab template is too long");
        return config;
    }
    private static boolean layout(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "list" -> false;
            case "columns" -> true;
            default -> throw new IllegalArgumentException("tab.layout must be list or columns");
        };
    }
    /** {@code tags.<role>} keys (MiniMessage; blank removes a role's tag). Also used by the global relay. */
    static NameStyle.Tags tags(Properties p) {
        var overrides = new HashMap<String, String>();
        for (String key : p.stringPropertyNames()) if (key.startsWith("tags.")) overrides.put(key.substring(5), p.getProperty(key));
        return new NameStyle.Tags(overrides);
    }
    String name(String server) {
        return names.getOrDefault(server, server.isEmpty() ? server : Character.toUpperCase(server.charAt(0)) + server.substring(1));
    }
    private static List<String> list(String value, String separator) {
        return Arrays.stream(value.split(java.util.regex.Pattern.quote(separator))).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
    private static List<String> ids(String value) {
        var result = list(value, ",");
        if (!result.stream().allMatch(Wire::id)) throw new IllegalArgumentException("Invalid server name in tab configuration");
        return result;
    }
}
