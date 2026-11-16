package uk.co.playerready.pulsegames.games.christmas;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.PlatformDetector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** A bounded set of decorative displays, with visible vanilla fallbacks for mixed-edition matches. */
final class ChristmasVisuals implements AutoCloseable {
    static final int LIMIT = 230;
    static final int WORLD_LIMIT = 240;
    private final Set<ItemDisplay> owned = new LinkedHashSet<>();
    private final boolean custom;

    ChristmasVisuals(GameInstance game) {
        boolean ready = true;
        try {
            Plugin content = game.plugin().getServer().getPluginManager().getPlugin("PulseContent");
            boolean hasModels = hasChristmasModels(content);
            for (Player player : game.players()) {
                if (player.hasMetadata("NPC")) continue;
                boolean acknowledged = game.plugin().seasons() != null && game.plugin().seasons().customVisualsReady(player);
                if (!acknowledged && hasModels)
                    acknowledged = Boolean.TRUE.equals(content.getClass().getMethod("isContentReady", Player.class).invoke(content, player));
                if (PlatformDetector.isBedrock(player) || !acknowledged) {
                    ready = false;
                    break;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { ready = false; }
        custom = ready;
    }

    private static boolean hasChristmasModels(Plugin content) {
        if (content == null || !content.isEnabled()) return false;
        try {
            if (!Integer.valueOf(1).equals(content.getClass().getMethod("itemModelsApiVersion").invoke(content))) return false;
            var has = content.getClass().getMethod("hasItemModel", String.class);
            for (String suffix : java.util.List.of("present_red", "present_green", "present_blue", "present_gold",
                    "wrapping_paper_red", "wrapping_paper_green", "wrapping_paper_blue", "wrapping_paper_gold", "sleigh", "footprint", "grinch_eyes", "dog")) {
                if (!Boolean.TRUE.equals(has.invoke(content, "pulse:christmas_" + suffix))) return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) { return false; }
    }

    ItemDisplay spawn(Location at, String model, Material fallback, float scale) {
        // Leave room for Present Panic's 48 snowballs and the engine's capped finishing fireworks.
        if (at == null || at.getWorld() == null || owned.size() >= LIMIT || at.getWorld().getEntities().size() >= WORLD_LIMIT) return null;
        ItemStack item = new ItemStack(fallback);
        if (custom) item.editMeta(meta -> meta.setItemModel(NamespacedKey.fromString("pulse:" + model)));
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
            entity.setItemStack(item);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setPersistent(false);
            entity.setViewRange(0.65f);
            entity.setTeleportDuration(2);
        });
        owned.add(display);
        return display;
    }
    void remove(ItemDisplay display) { if (display != null) { display.remove(); owned.remove(display); } }
    int size() { return owned.size(); }
    boolean custom() { return custom; }
    void sound(Location at, String cue, org.bukkit.Sound fallback, float volume, float pitch) {
        if (custom) at.getWorld().playSound(at, "pulse:christmas." + cue, volume, pitch);
        else at.getWorld().playSound(at, fallback, volume, pitch);
    }
    @Override public void close() {
        for (ItemDisplay display : new ArrayList<>(owned)) display.remove();
        owned.clear();
    }
}
