package uk.co.playerready.pulsegames.games.karts;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import uk.co.playerready.pulsegames.core.util.Cuboid;

/** Explicit per-region profiles. Absent profiles retain the original arena-wide pad behaviour. */
final class KartPadConfig {
    private static final Pattern KEY = Pattern.compile("(jump|boost)([1-9][0-9]{0,2})-(.+)");
    private static final Set<String> JUMP_FIELDS = Set.of("launch-speed", "gravity", "air-ticks", "min-speed", "cooldown", "yaw", "air-steering");
    private static final Set<String> BOOST_FIELDS = Set.of("factor", "ticks", "cooldown", "yaw");
    record JumpProfile(double launchSpeed, double gravity, int maxAirTicks, double minSpeed,
                       int cooldown, Double yaw, double airSteering) {}
    record BoostProfile(double factor, int ticks, int cooldown, Double yaw) {}
    private final Map<Integer, JumpProfile> jumps;
    private final Map<Integer, BoostProfile> boosts;

    private KartPadConfig(Map<Integer, JumpProfile> jumps, Map<Integer, BoostProfile> boosts) {
        this.jumps = Map.copyOf(jumps);
        this.boosts = Map.copyOf(boosts);
    }
    JumpProfile jump(int index) { return jumps.get(index); }
    BoostProfile boost(int index) { return boosts.get(index); }
    boolean configured() { return !jumps.isEmpty() || !boosts.isEmpty(); }

    static KartPadConfig load(Map<String, Object> settings, Map<String, Cuboid> regions) {
        Map<Integer, JumpProfile> jumps = new HashMap<>();
        Map<Integer, BoostProfile> boosts = new HashMap<>();
        for (String key : settings.keySet()) {
            var match = KEY.matcher(key);
            if (!match.matches()) {
                if (key.matches("(jump|boost)[0-9]+-.+"))
                    throw new IllegalArgumentException("Number pad settings from 1 to 999: " + key + ".");
                continue;
            }
            String kind = match.group(1), id = kind + match.group(2);
            if (!regions.containsKey(id)) throw new IllegalArgumentException(key + " requires region " + id + ".");
            if (!(kind.equals("jump") ? JUMP_FIELDS : BOOST_FIELDS).contains(match.group(3)))
                throw new IllegalArgumentException("Unknown pad setting " + key + ".");
            int index = Integer.parseInt(match.group(2)) - 1;
            if (kind.equals("jump")) {
                jumps.put(index, new JumpProfile(number(settings, id + "-launch-speed", 9, 1, 30),
                        number(settings, id + "-gravity", 8, 2, 48),
                        integer(settings, id + "-air-ticks", 100, 10, 200),
                        number(settings, id + "-min-speed", 3, 0, 24),
                        integer(settings, id + "-cooldown", 40, 1, 400), yaw(settings, id),
                        number(settings, id + "-air-steering", .25, 0, .5)));
            } else {
                boosts.put(index, new BoostProfile(number(settings, id + "-factor", 1.45, 1.01, 2),
                        integer(settings, id + "-ticks", 24, 1, 200),
                        integer(settings, id + "-cooldown", 20, 1, 400), yaw(settings, id)));
            }
        }
        return new KartPadConfig(jumps, boosts);
    }
    static String validationError(Map<String, Object> settings, Map<String, Cuboid> regions) {
        try { load(settings, regions); return null; }
        catch (IllegalArgumentException error) { return error.getMessage(); }
    }
    private static Double yaw(Map<String, Object> settings, String id) {
        return settings.containsKey(id + "-yaw") ? number(settings, id + "-yaw", 0, -180, 180) : null;
    }
    private static int integer(Map<String, Object> settings, String key, int fallback, int min, int max) {
        double value = number(settings, key, fallback, min, max);
        if (value != Math.rint(value)) throw new IllegalArgumentException(key + " must be a whole number.");
        return (int) value;
    }
    private static double number(Map<String, Object> settings, String key, double fallback, double min, double max) {
        if (!settings.containsKey(key)) return fallback;
        Object raw = settings.get(key);
        try {
            double value = raw instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(raw).trim());
            if (Double.isFinite(value) && value >= min && value <= max) return value;
        } catch (NumberFormatException ignored) { /* builder-facing error below */ }
        throw new IllegalArgumentException(key + " must be a finite number from " + min + " to " + max + ".");
    }
}
