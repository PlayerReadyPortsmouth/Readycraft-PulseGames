package uk.co.playerready.pulsegames.core.ui.forms;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import uk.co.playerready.pulsegames.core.util.PlatformDetector;

import java.util.List;
import java.util.function.Consumer;

/**
 * Bedrock form entry points. Every method returns false when no form was shown (Java player, forms
 * switched off with {@code ui.bedrock-forms: false}, Floodgate missing or failing), so callers fall
 * back to the chest menu. Safe to load without Floodgate.
 */
public final class Forms {
    private Forms() { }

    /** True when this player should get a form instead of a chest. */
    public static boolean supports(Plugin plugin, Player player) {
        return FormLayout.useForm(plugin.getConfig().getBoolean("ui.bedrock-forms", true),
                Bukkit.getPluginManager().getPlugin("floodgate") != null, PlatformDetector.isBedrock(player));
    }

    /** A list of buttons; {@code onTarget} gets the tapped button's target on the main thread. */
    public static boolean list(Plugin plugin, Player player, String title, String content,
                               List<FormLayout.Button> buttons, Consumer<String> onTarget) {
        if (!supports(plugin, player)) return false;
        try {
            CumulusForms.simple(plugin, player, title, content, buttons, onTarget);
            return true;
        } catch (Throwable broken) {
            return false;
        }
    }

    /**
     * Yes/no for spending tokens or anything risky. Returns false when no form was shown, so the caller
     * opens its chest confirm instead; otherwise exactly one of the callbacks runs (closing counts as no).
     */
    public static boolean confirm(Plugin plugin, Player player, String title, String body,
                                  String yesLabel, String noLabel, Runnable onYes, Runnable onNo) {
        if (!supports(plugin, player)) return false;
        try {
            CumulusForms.modal(plugin, player, title, body, yesLabel, noLabel, onYes, onNo);
            return true;
        } catch (Throwable broken) {
            return false;
        }
    }
}
