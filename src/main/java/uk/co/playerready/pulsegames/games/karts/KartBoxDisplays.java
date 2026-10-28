package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import uk.co.playerready.pulsegames.core.content.PulseContentBridge;
import java.util.Collection;
import java.util.UUID;

/**
 * Visuals for the collectable item boxes. Prefers the PulseContent pickup model through the world-visual bridge
 * (it animates itself); otherwise a plain ItemDisplay glass cube with a glow. Either way each box emits a particle
 * marker column so a client whose display entities are missing (older Geyser) still sees where it is.
 */
final class KartBoxDisplays implements AutoCloseable {
    static final int ANIMATION_TICKS = 10;
    private final World world;
    private final KartItemBoxes boxes;
    private final ItemDisplay[] plain;
    private final PulseContentBridge.WorldVisual[] bridged;
    private PulseContentBridge content;
    private Collection<UUID> audience = java.util.Set.of();
    private boolean useBridge;

    KartBoxDisplays(World world, KartItemBoxes boxes) {
        this.world = world; this.boxes = boxes;
        this.plain = new ItemDisplay[boxes.size()];
        this.bridged = new PulseContentBridge.WorldVisual[boxes.size()];
    }
    /** Called when the ready audience changes; {@code content} may be null (no PulseContent). */
    void configure(PulseContentBridge content, boolean bridgeAvailable, Collection<UUID> audience) {
        boolean changed = !audience.equals(this.audience) || bridgeAvailable != useBridge;
        this.content = content; this.useBridge = bridgeAvailable && !audience.isEmpty(); this.audience = java.util.Set.copyOf(audience);
        if (changed) closeVisuals();
    }
    private Location at(int index) {
        var p = boxes.position(index);
        return new Location(world, p.x(), p.y() + KartItemBoxes.BOX_LIFT, p.z());
    }
    private boolean loaded(int index) {
        var p = boxes.position(index);
        return world.isChunkLoaded((int) Math.floor(p.x()) >> 4, (int) Math.floor(p.z()) >> 4);
    }

    /** Create, hide, fade and animate every box for this tick. Cheap: real work happens every 10 ticks. */
    void update(long tick) {
        if (tick % ANIMATION_TICKS != 0) return;
        for (int i = 0; i < boxes.size(); i++) {
            if (!loaded(i)) continue;
            double scale = boxes.scale(i, tick);
            if (useBridge) updateBridge(i, scale);
            else updatePlain(i, scale, tick);
            if (boxes.available(i, tick)) world.spawnParticle(Particle.END_ROD, at(i).add(0, 1.1, 0), 1, .08, .3, .08, .005);
        }
    }
    private void updateBridge(int i, double scale) {
        var visual = bridged[i];
        if (scale <= 0) { closeBridge(i); return; }
        if (visual != null && visual.active()) return;
        if (!closeBridge(i)) return; // a failed close is retried, never replaced over an orphan
        try { bridged[i] = content.createWorldVisual(PulseKartsGame.PICKUP_MODEL, at(i), audience); }
        catch (RuntimeException failure) { bridged[i] = null; useBridge = false; } // fall back to the plain cube
    }
    private boolean closeBridge(int i) {
        if (bridged[i] == null) return true;
        try { bridged[i].close(); bridged[i] = null; return true; }
        catch (Exception failure) { return false; }
    }
    private void updatePlain(int i, double scale, long tick) {
        var display = plain[i];
        if (display == null || !display.isValid()) {
            display = world.spawn(at(i), ItemDisplay.class, e -> {
                e.setItemStack(new ItemStack(Material.CYAN_STAINED_GLASS));
                e.setBillboard(Display.Billboard.FIXED);
                e.setGlowing(true);
                e.setGlowColorOverride(Color.AQUA);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                e.setViewRange(1.5f);
            });
            plain[i] = display;
        }
        double phase = tick / (double) ANIMATION_TICKS;
        float bob = (float) (Math.sin(phase * Math.PI / 2) * .12);
        float size = (float) (.8 * scale);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(scale <= 0 ? 0 : ANIMATION_TICKS);
        display.setTransformation(new Transformation(new Vector3f(0, bob, 0),
                new AxisAngle4f((float) (phase * Math.PI / 2), 0, 1, 0), new Vector3f(size, size, size), new AxisAngle4f()));
    }

    /** Burst for the collector and everyone nearby. */
    void burst(int index) {
        Location at = at(index);
        world.spawnParticle(Particle.END_ROD, at, 24, .5, .5, .5, .08);
        world.spawnParticle(Particle.CRIT, at, 10, .4, .4, .4, .2);
        world.playSound(at, "minecraft:block.amethyst_block.chime", 1f, 1.4f);
        if (useBridge) closeBridge(index);
    }

    private boolean closeVisuals() {
        boolean clean = true;
        for (int i = 0; i < bridged.length; i++) clean &= closeBridge(i);
        for (int i = 0; i < plain.length; i++) if (plain[i] != null) { plain[i].remove(); plain[i] = null; }
        return clean;
    }
    @Override public void close() { if (!closeVisuals()) throw new IllegalStateException("Kart pickup cleanup failed"); }
}
