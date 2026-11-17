package uk.co.playerready.pulsenetwork;

import java.util.*;

/** Explicit test-environment gate: missing, misspelled and production environments are inert. */
public record SafeguardingConfig(boolean testEnvironment, boolean filter, boolean logging, int retentionDays,
                                 Set<String> backends, String extraWords, String webhookEnv, String readyAppEndpoint, String readyAppTokenEnv) {
    public static SafeguardingConfig parse(Properties p) {
        boolean test = "test".equals(p.getProperty("safeguarding.environment", "").trim());
        // Flag-off must not validate new settings or retain/resolve any secret references.
        if (!test) return new SafeguardingConfig(false, false, false, 90, Set.of(), "", "", "", "");
        var backends = new HashSet<String>();
        for (String name : p.getProperty("safeguarding.test-backends", "minigamestest,survivaltest").split(",")) {
            name = name.trim(); if (!name.isEmpty()) { if (!Wire.id(name)) throw new IllegalArgumentException("Invalid safeguarding backend"); backends.add(name); }
        }
        int days = Integer.parseInt(p.getProperty("safeguarding.retention-days", "90"));
        if (days < 1 || days > 90) throw new IllegalArgumentException("Safeguarding retention must be 1..90 days; approved retention is 90 days");
        String env = p.getProperty("safeguarding.discord-webhook-env", "").trim();
        if (!env.isEmpty() && !env.matches("[A-Z][A-Z0-9_]{0,79}")) throw new IllegalArgumentException("Use an environment variable name for the webhook");
        return new SafeguardingConfig(test, test && Boolean.parseBoolean(p.getProperty("safeguarding.filter", "true")),
                test && Boolean.parseBoolean(p.getProperty("safeguarding.logging", "true")), days, Set.copyOf(backends),
                p.getProperty("safeguarding.extra-words", ""), env, p.getProperty("safeguarding.readyapp-endpoint", "").trim(),
                p.getProperty("safeguarding.readyapp-token-env", "").trim());
    }
    public boolean applies(String backend) { return testEnvironment && backends.contains(backend) && (filter || logging); }
}
