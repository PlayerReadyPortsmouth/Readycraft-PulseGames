package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Locale;

/** The {@code bots.combat.*} knobs. Missing or out-of-range values fall back to the tier defaults. */
record BotCombatSettings(boolean sprint, boolean strafe, boolean kitUse, boolean retreat,
                         EnumMap<BotDifficulty, BotDifficulty.Profile> tiers) {
    static BotCombatSettings defaults() { return read(null); }

    BotDifficulty.Profile profile(BotDifficulty difficulty) { return tiers.get(difficulty); }

    static BotCombatSettings read(ConfigurationSection config) {
        ConfigurationSection root = config == null ? null : config.getConfigurationSection("bots.combat");
        var tiers = new EnumMap<BotDifficulty, BotDifficulty.Profile>(BotDifficulty.class);
        for (BotDifficulty tier : BotDifficulty.values()) {
            var d = tier.profile();
            ConfigurationSection s = root == null ? null : root.getConfigurationSection("tiers." + tier.name().toLowerCase(Locale.ROOT));
            tiers.put(tier, s == null ? d : new BotDifficulty.Profile(
                    clamp(s.getInt("melee-delay-ticks", d.meleeDelay()), 4, 80),
                    clamp(s.getInt("hit-chance", d.hitChance()), 0, 100),
                    clamp(s.getInt("projectile-delay-ticks", d.projectileDelay()), 10, 120),
                    clamp(s.getInt("path-interval-ticks", d.pathInterval()), 5, 80),
                    clamp(s.getDouble("aim-error-degrees", d.aimErrorDegrees()), 0, 20),
                    clamp(s.getDouble("strafe-bias", d.strafeBias()), 0, 1),
                    clamp(s.getDouble("sprint-chance", d.sprintChance()), 0, 1),
                    clamp(s.getDouble("jump-crit-chance", d.jumpCritChance()), 0, 1),
                    clamp(s.getInt("reaction-jitter-ticks", d.reactionJitter()), 0, 5),
                    clamp(s.getDouble("retreat-health", d.retreatHealth()), 0, 0.6)));
        }
        return new BotCombatSettings(root == null || root.getBoolean("sprint", true),
                root == null || root.getBoolean("strafe", true), root == null || root.getBoolean("kit-use", true),
                root == null || root.getBoolean("retreat", true), tiers);
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double clamp(double v, double lo, double hi) { return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v)); }
}
