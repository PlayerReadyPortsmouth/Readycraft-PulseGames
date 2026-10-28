package uk.co.playerready.pulsegames.games.common;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;

/** One small, non-flashing celebration burst. */
public final class Fireworks {
    private Fireworks() {}

    public static void burst(Location at) {
        Firework firework = at.getWorld().spawn(at, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL)
                .withColor(Color.YELLOW, Color.AQUA, Color.LIME).withFade(Color.WHITE).build());
        firework.setFireworkMeta(meta);
        firework.detonate();
    }
}
