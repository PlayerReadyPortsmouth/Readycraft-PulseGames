package uk.co.playerready.pulsenetwork;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.List;
import java.util.function.IntConsumer;

/** The only class that touches Floodgate/Cumulus; callers guard it with {@link #available()} so it never loads without Floodgate. */
final class BedrockForms {
    private BedrockForms() {}

    static boolean available() { return Bukkit.getPluginManager().getPlugin("floodgate") != null; }

    /** Sends a simple form to a Floodgate player. Returns false for a Java player. The handler runs on the main thread with the button index. */
    static boolean simple(Plugin plugin, Player player, String title, String content, List<String> buttons, IntConsumer onButton) {
        var api = org.geysermc.floodgate.api.FloodgateApi.getInstance();
        if (!api.isFloodgatePlayer(player.getUniqueId())) return false;
        var builder = org.geysermc.cumulus.form.SimpleForm.builder().title(title).content(content);
        for (String b : buttons) builder.button(b);
        return api.sendForm(player.getUniqueId(), builder.validResultHandler(r -> {
            int i = r.clickedButtonId();
            if (i >= 0 && i < buttons.size()) Bukkit.getScheduler().runTask(plugin, () -> onButton.accept(i));
        }).build());
    }

    static boolean modal(Plugin plugin, Player player, String title, String body, String yes, String no, Runnable onYes) {
        var api = org.geysermc.floodgate.api.FloodgateApi.getInstance();
        if (!api.isFloodgatePlayer(player.getUniqueId())) return false;
        return api.sendForm(player.getUniqueId(), org.geysermc.cumulus.form.ModalForm.builder().title(title).content(body)
                .button1(yes).button2(no).validResultHandler(r -> { if (r.clickedFirst()) Bukkit.getScheduler().runTask(plugin, onYes); }).build());
    }

    /** One text field. {@code onText} gets the typed text; {@code onClosed} runs when the form is dismissed. Both on the main thread. */
    static boolean input(Plugin plugin, Player player, String title, String label, String placeholder, java.util.function.Consumer<String> onText, Runnable onClosed) {
        var api = org.geysermc.floodgate.api.FloodgateApi.getInstance();
        if (!api.isFloodgatePlayer(player.getUniqueId())) return false;
        return api.sendForm(player.getUniqueId(), org.geysermc.cumulus.form.CustomForm.builder().title(title).input(label, placeholder)
                .validResultHandler(r -> { String t = r.asInput(0); Bukkit.getScheduler().runTask(plugin, () -> onText.accept(t == null ? "" : t)); })
                .closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, onClosed)).build());
    }
}
