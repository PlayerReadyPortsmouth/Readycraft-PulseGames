package uk.co.playerready.pulsegames.games.common;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import uk.co.playerready.pulsegames.core.settings.Comfort;
import uk.co.playerready.pulsegames.core.settings.PlayerSettings;
import org.bukkit.inventory.meta.FireworkMeta;

/** One small, non-flashing celebration burst. */
public final class Fireworks {
    private Fireworks() {}

    public static void burst(Location at) {
        if (PlayerSettings.anyInWorld(at.getWorld(), Comfort.REDUCE_FLASHES)) {
            at.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, at, 12, .5, .5, .5, .02);
            return;
        }
        Firework firework = at.getWorld().spawn(at, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL)
                .withColor(Color.YELLOW, Color.AQUA, Color.LIME).withFade(Color.WHITE).build());
        firework.setFireworkMeta(meta);
        firework.detonate();
    }
}
