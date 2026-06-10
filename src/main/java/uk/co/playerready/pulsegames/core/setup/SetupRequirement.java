package uk.co.playerready.pulsegames.core.setup;

/**
 * One element a map needs before a game can run on it. Drives the in-game
 * setup checklist and validation in {@link ArenaSetupManager}.
 */
public record SetupRequirement(Kind kind, String key, int min, String description, boolean required) {

    public enum Kind {
        /** At least {@code min} spawn points (/pulse setup addspawn). */
        SPAWNS,
        /** A single named region (/pulse setup region <key>). */
        REGION,
        /** At least {@code min} regions named key1, key2... */
        REGION_PREFIX,
        /** A location setting set where you stand (/pulse setup setloc <key>). */
        LOCATION_SETTING,
        /** A plain value setting (/pulse setup set <key> <value>). */
        VALUE_SETTING
    }

    public static SetupRequirement spawns(int min, String description) {
        return new SetupRequirement(Kind.SPAWNS, "spawns", min, description, true);
    }

    public static SetupRequirement region(String key, String description) {
        return new SetupRequirement(Kind.REGION, key, 1, description, true);
    }

    public static SetupRequirement regions(String prefix, int min, String description) {
        return new SetupRequirement(Kind.REGION_PREFIX, prefix, min, description, true);
    }

    public static SetupRequirement location(String key, String description) {
        return new SetupRequirement(Kind.LOCATION_SETTING, key, 1, description, true);
    }

    public static SetupRequirement setting(String key, String description) {
        return new SetupRequirement(Kind.VALUE_SETTING, key, 1, description, true);
    }

    public static SetupRequirement optional(SetupRequirement requirement) {
        return new SetupRequirement(requirement.kind(), requirement.key(), requirement.min(),
                requirement.description(), false);
    }
}
