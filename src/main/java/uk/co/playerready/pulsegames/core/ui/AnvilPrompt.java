package uk.co.playerready.pulsegames.core.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;
import uk.co.playerready.pulsegames.core.ui.forms.Forms;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Text input for the Java client: an anvil with a paper named after the prompt; the player renames it and
 * clicks the result. Bedrock gets a Cumulus input form instead (see {@link Forms#input}). A bad answer keeps
 * the window open with a message; closing it cancels. The anvil is emptied before it closes, so no paper ever
 * reaches the player. Register {@link Events} once.
 */
public final class AnvilPrompt {
    private static final Map<UUID, AnvilPrompt> ACTIVE = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final Player player;
    private final String prompt;
    private final AnvilPromptState state;
    private final Consumer<String> onAccept;
    private final Runnable onCancel;
    private Inventory anvil;

    private AnvilPrompt(Plugin plugin, Player player, String prompt, Function<String, String> validator,
                        Consumer<String> onAccept, Runnable onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.prompt = prompt;
        this.state = new AnvilPromptState(prompt, validator);
        this.onAccept = onAccept;
        this.onCancel = onCancel == null ? () -> { } : onCancel;
    }

    /**
     * Asks for one line of text. Exactly one of {@code onAccept} (validated text, next tick) or
     * {@code onCancel} runs. Call from the main thread, and not inside a click event.
     */
    public static void ask(Plugin plugin, Player player, String prompt, Function<String, String> validator,
                           Consumer<String> onAccept, Runnable onCancel) {
        AnvilPrompt existing = ACTIVE.remove(player.getUniqueId());
        if (existing != null) existing.finish(false);
        AnvilPrompt ask = new AnvilPrompt(plugin, player, prompt, validator, onAccept, onCancel);
        if (!ask.showForm("")) ask.showAnvil();
    }

    /** Bedrock input form; false when the player should get the anvil. */
    private boolean showForm(String error) {
        return Forms.input(plugin, player, "Pulse", prompt, error, text -> {
            AnvilPromptState.Outcome out = state.submit(text);
            switch (out.kind()) {
                case ACCEPTED -> onAccept.accept(out.text());
                case REJECTED -> showForm(out.error());
                case IGNORED -> { }
            }
        }, () -> { if (state.cancel()) onCancel.run(); });
    }

    private void showAnvil() {
        InventoryView view = player.openAnvil(null, true);
        if (view == null) { if (state.cancel()) onCancel.run(); return; }
        anvil = view.getTopInventory();
        ACTIVE.put(player.getUniqueId(), this);
        anvil.setItem(0, paper());
        Theme.sound(player, Theme.Cue.OPEN);
    }

    private ItemStack paper() {
        ItemStack paper = new ItemStack(Material.PAPER);
        paper.editMeta(meta -> meta.displayName(Theme.plain(Component.text(prompt))));
        return paper;
    }

    /** Empties the window so nothing is handed back, then (optionally) closes it. */
    private void finish(boolean close) {
        ACTIVE.remove(player.getUniqueId(), this);
        if (anvil != null) anvil.clear();
        if (close && player.getOpenInventory().getTopInventory() == anvil) player.closeInventory();
    }

    private static AnvilPrompt of(InventoryView view) {
        if (!(view.getPlayer() instanceof Player p)) return null;
        AnvilPrompt ask = ACTIVE.get(p.getUniqueId());
        return ask != null && ask.anvil == view.getTopInventory() ? ask : null;
    }

    public static final class Events implements Listener {
        @EventHandler
        public void onPrepare(PrepareAnvilEvent event) {
            AnvilPrompt ask = of(event.getView());
            if (ask == null || !(event.getView() instanceof AnvilView view)) return;
            String text = AnvilPromptState.clean(view.getRenameText());
            if (text.isEmpty() || text.equals(ask.prompt)) { event.setResult(null); return; }
            ItemStack result = new ItemStack(Material.PAPER);
            result.editMeta(meta -> meta.displayName(Theme.plain(Component.text(text))));
            event.setResult(result);
            view.setRepairCost(0);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onClick(InventoryClickEvent event) {
            AnvilPrompt ask = of(event.getView());
            if (ask == null) return;
            event.setCancelled(true);
            if (event.getRawSlot() != 2 || !(event.getView() instanceof AnvilView view)) return;
            AnvilPromptState.Outcome out = ask.state.submit(view.getRenameText());
            switch (out.kind()) {
                case ACCEPTED -> // stays registered (and cancelling clicks) until the next tick closes it
                    Bukkit.getScheduler().runTask(ask.plugin, () -> {
                        ask.finish(true);
                        ask.onAccept.accept(out.text());
                    });
                case REJECTED -> {
                    ask.player.sendMessage(Text.msg("<red>" + out.error()));
                    Theme.sound(ask.player, Theme.Cue.ERROR);
                }
                case IGNORED -> { }
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onDrag(InventoryDragEvent event) {
            if (of(event.getView()) != null) event.setCancelled(true);
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            AnvilPrompt ask = of(event.getView());
            if (ask == null) return;
            ask.finish(false);
            if (ask.state.cancel()) Bukkit.getScheduler().runTask(ask.plugin, ask.onCancel);
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            AnvilPrompt ask = ACTIVE.remove(event.getPlayer().getUniqueId());
            if (ask != null) { ask.finish(false); ask.state.cancel(); }
        }
    }
}
