package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Owns only this round's scale modifier and visibility layer. */
final class HiderBody implements AutoCloseable {
    private final Plugin plugin;
    private final Player player;
    private final NamespacedKey scaleKey;
    private final Set<Player> hidden = new HashSet<>();
    private final boolean collidable;
    private final PotionEffect previousInvisibility;
    private boolean closed;
    HiderBody(Plugin plugin, Player player) {
        this.plugin = plugin; this.player = player;
        scaleKey = new NamespacedKey(plugin, "prophunt_body");
        collidable = player.isCollidable(); previousInvisibility = player.getPotionEffect(PotionEffectType.INVISIBILITY);
    }
    void apply(double propHeight, Collection<Player> viewers) {
        scale(propHeight);
        player.setCollidable(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        for (Player viewer : viewers) if (!viewer.getUniqueId().equals(player.getUniqueId())) {
            hidden.add(viewer); viewer.hidePlayer(plugin, player);
        }
    }
    void scale(double propHeight) {
        var attribute = player.getAttribute(Attribute.SCALE);
        if (attribute == null) throw new IllegalStateException("Player scaling is unavailable");
        attribute.removeModifier(scaleKey);
        double current = attribute.getValue();
        if (!Double.isFinite(current) || current <= 0) throw new IllegalStateException("Invalid player scale");
        // 1.8 * 0.5 = 0.9 blocks, leaving room in a one-block gap. Heads use 0.45.
        attribute.addModifier(new AttributeModifier(scaleKey, propHeight * .5 / current - 1,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }
    @Override public void close() {
        if (closed) return;
        var attribute = player.getAttribute(Attribute.SCALE);
        if (attribute != null) attribute.removeModifier(scaleKey);
        player.setCollidable(collidable);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        if (previousInvisibility != null) player.addPotionEffect(previousInvisibility);
        for (Player viewer : Set.copyOf(hidden)) { viewer.showPlayer(plugin, player); hidden.remove(viewer); }
        closed = true;
    }
}
