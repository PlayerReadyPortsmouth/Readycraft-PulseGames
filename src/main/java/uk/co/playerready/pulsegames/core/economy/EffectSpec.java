package uk.co.playerready.pulsegames.core.economy;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.regex.Pattern;

/**
 * A data-driven, vanilla-only cosmetic effect: one particle burst and/or one sound, read from cosmetics.yml.
 * Particles that need extra data (dust colours, blocks, items) are refused so a typo cannot throw mid-match.
 */
public record EffectSpec(Particle particle, int count, double spread, double speed, double height,
                         String sound, float volume, float pitch) {

    private static final Pattern SOUND_KEY = Pattern.compile("[a-z0-9_./:-]+");
    private static final int MAX_COUNT = 60;

    /** @throws IllegalArgumentException with a short reason a server owner can act on. */
    public static EffectSpec parse(ConfigurationSection s) {
        if (s == null) throw new IllegalArgumentException("missing effect");
        Particle particle = null;
        String name = s.getString("particle", "");
        if (!name.isBlank()) {
            try { particle = Particle.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException unknown) { throw new IllegalArgumentException("unknown particle " + name); }
            if (particle.getDataType() != Void.class) throw new IllegalArgumentException("particle " + name + " needs extra data");
        }
        String sound = s.getString("sound", "");
        sound = sound == null || sound.isBlank() ? null : sound.trim();
        if (sound != null && !SOUND_KEY.matcher(sound).matches()) throw new IllegalArgumentException("bad sound key " + sound);
        if (particle == null && sound == null) throw new IllegalArgumentException("effect needs a particle or a sound");
        int count = Math.max(1, Math.min(MAX_COUNT, s.getInt("count", 8)));
        return new EffectSpec(particle, count, clamp(s.getDouble("spread", 0.4)), clamp(s.getDouble("speed", 0.02)),
                s.getDouble("height", 1.0), sound, (float) Math.max(0, Math.min(1, s.getDouble("volume", 0.6))),
                (float) Math.max(0.5, Math.min(2, s.getDouble("pitch", 1.0))));
    }

    private static double clamp(double v) { return Math.max(0, Math.min(3, v)); }

    /** Plays the effect for everyone near {@code at} (the base point; {@code height} is added). */
    public void play(World world, Location at) {
        try {
            Location where = at.clone().add(0, height, 0);
            if (particle != null) world.spawnParticle(particle, where, count, spread, spread, spread, speed);
            if (sound != null) world.playSound(where, sound, volume, pitch);
        } catch (RuntimeException ignored) {
            // A particle this server version dislikes must never break a round.
        }
    }
}
