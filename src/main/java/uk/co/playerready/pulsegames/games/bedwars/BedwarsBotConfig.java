package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.configuration.ConfigurationSection;

/** Tunables under {@code bedwars.bots.*} in config.yml; every value has the default documented there. */
public record BedwarsBotConfig(
        int lookAheadCells, int bridgePlaceTicks, boolean bridgeSneak, int bridgeMaxGap,
        int attackStartSeconds, int midGameSeconds, int diamondTripSeconds, int diamondsWanted,
        int gatherIntervalSeconds, int gatherHoldSeconds, int spendRadius,
        int guardRadius, int alertRadius, int defenceLayers, int defencePlaceTicks, int defenceBlockCap, int soloGuardSeconds,
        boolean fireball, int fireballMinRange, int fireballMaxRange, int fireballCooldownTicks,
        double pearlEscapeHealth, double appleHealth, int targetReassessSeconds) {

    public static BedwarsBotConfig defaults() { return from(null); }

    public static BedwarsBotConfig from(ConfigurationSection s) {
        return new BedwarsBotConfig(
                clamp(s, "pace.look-ahead-cells", 3, 1, 5),
                clamp(s, "bridge.place-ticks", 5, 2, 20), bool(s, "bridge.sneak", true), clamp(s, "bridge.max-gap", 24, 1, 64),
                clamp(s, "roles.attack-start-seconds", 150, 0, 1200), clamp(s, "roles.mid-game-seconds", 300, 0, 1800),
                clamp(s, "resources.diamond-trip-seconds", 40, 10, 180), clamp(s, "resources.diamonds-wanted", 4, 1, 16),
                clamp(s, "resources.gather-interval-seconds", 60, 15, 600), clamp(s, "resources.gather-hold-seconds", 12, 3, 60),
                clamp(s, "resources.spend-radius", 14, 4, 40),
                clamp(s, "defence.guard-radius", 6, 2, 20), clamp(s, "defence.alert-radius", 16, 6, 40),
                clamp(s, "defence.layers", 2, 1, 3), clamp(s, "defence.place-ticks", 10, 3, 40),
                clamp(s, "defence.block-cap", 192, 16, 512), clamp(s, "roles.solo-guard-seconds", 90, 0, 600),
                bool(s, "fireball.enabled", true), clamp(s, "fireball.min-range", 3, 2, 10), clamp(s, "fireball.max-range", 9, 4, 20),
                clamp(s, "fireball.cooldown-ticks", 60, 20, 400),
                dbl(s, "pearl.escape-health", 6.0), dbl(s, "apple-health", 10.0),
                clamp(s, "attack.reassess-seconds", 15, 3, 120));
    }

    private static int clamp(ConfigurationSection s, String key, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, s == null ? fallback : s.getInt(key, fallback)));
    }
    private static boolean bool(ConfigurationSection s, String key, boolean fallback) { return s == null ? fallback : s.getBoolean(key, fallback); }
    private static double dbl(ConfigurationSection s, String key, double fallback) { return s == null ? fallback : s.getDouble(key, fallback); }
}
