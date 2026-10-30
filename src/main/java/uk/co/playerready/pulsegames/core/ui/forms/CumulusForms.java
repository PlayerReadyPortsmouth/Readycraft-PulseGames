package uk.co.playerready.pulsegames.core.ui.forms;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Consumer;

/**
 * The only class that touches Floodgate/Cumulus. {@link Forms} calls it inside a guard, so it is never
 * loaded when Floodgate is absent. Result handlers run off the main thread, so they hop back first.
 */
final class CumulusForms {
    private CumulusForms() { }

    static void simple(Plugin plugin, Player player, String title, String content,
                       List<FormLayout.Button> buttons, Consumer<String> onTarget) {
        var builder = org.geysermc.cumulus.form.SimpleForm.builder().title(title).content(content);
        for (FormLayout.Button b : buttons) builder.button(b.label());
        var form = builder.validResultHandler(r -> {
            String target = FormLayout.targetAt(buttons, r.clickedButtonId());
            if (target != null) Bukkit.getScheduler().runTask(plugin, () -> onTarget.accept(target));
        }).build();
        org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    static void modal(Plugin plugin, Player player, String title, String body, String yes, String no,
                      Runnable onYes, Runnable onNo) {
        var form = org.geysermc.cumulus.form.ModalForm.builder().title(title).content(body)
                .button1(yes).button2(no)
                .validResultHandler(r -> Bukkit.getScheduler().runTask(plugin, r.clickedFirst() ? onYes : onNo))
                .closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, onNo))
                .build();
        org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    static void input(Plugin plugin, Player player, String title, String label, Consumer<String> onText, Runnable onCancel) {
        var form = org.geysermc.cumulus.form.CustomForm.builder().title(title).input(label, "Type here", "")
                .validResultHandler(r -> { String text = r.asInput(0); Bukkit.getScheduler().runTask(plugin, () -> onText.accept(text == null ? "" : text)); })
                .closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, onCancel))
                .build();
        org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
}
