package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Text input through an anvil. A paper named with the prompt sits in the left slot; the player types in the rename box and clicks the
 * result. Nothing is ever given: every click in the window is cancelled and the paper is removed on close. Bedrock (Floodgate) players
 * get a one-field form instead. Bad input re-prompts with an error line (up to {@link AnvilPromptLogic#MAX_ATTEMPTS} times).
 *
 * <p>To reuse in another plugin, copy this class, {@link AnvilPromptLogic} and the {@code input} method of BedrockForms; register
 * {@link #listener()} once; call {@code AnvilPrompt.ask(...)}. Uses {@code MenuType.ANVIL} (Paper 1.21.4+), no NMS, no version checks.
 */
final class AnvilPrompt {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final class Session {
        final Plugin plugin; final Player player; final String title; final AnvilPromptLogic logic; final Consumer<String> done; final Runnable cancelled;
        AnvilView view;
        Session(Plugin plugin, Player player, String title, AnvilPromptLogic logic, Consumer<String> done, Runnable cancelled) {
            this.plugin = plugin; this.player = player; this.title = title; this.logic = logic; this.done = done; this.cancelled = cancelled;
        }
    }
    private AnvilPrompt() {}

    /** Asks {@code player} for text. Exactly one of {@code done} (validated, trimmed) or {@code cancelled} runs, on the main thread. */
    static void ask(Plugin plugin, Player player, String title, AnvilPromptLogic.Rule rule, Consumer<String> done, Runnable cancelled) {
        if (LocalChatDelivery.bedrock(player) && BedrockForms.available() && form(plugin, player, title, rule, null, done, cancelled)) return;
        var s = new Session(plugin, player, title, new AnvilPromptLogic(rule), done, cancelled);
        if (!open(s)) { player.sendMessage(Component.text("The text box could not open on this server.", NamedTextColor.YELLOW)); cancelled.run(); }
    }

    private static boolean form(Plugin plugin, Player player, String title, AnvilPromptLogic.Rule rule, String error, Consumer<String> done, Runnable cancelled) {
        return BedrockForms.input(plugin, player, title, error == null ? "Type below." : error, "", text -> {
            var problem = rule.check(text);
            if (problem.isEmpty()) done.accept(text.trim());
            else form(plugin, player, title, rule, problem.get(), done, cancelled);
        }, cancelled);
    }

    private static boolean open(Session s) {
        try {
            var view = org.bukkit.inventory.MenuType.ANVIL.builder().title(Component.text(s.title)).checkReachable(false).build(s.player);
            if (view == null) return false;
            if (s.view != null) s.view.setItem(0, null);
            s.view = view;
            view.setItem(0, paper(s));
            SESSIONS.put(s.player.getUniqueId(), s);
            s.player.openInventory(view);
            s.logic.reopened();
            return true;
        } catch (RuntimeException | LinkageError e) {
            SESSIONS.remove(s.player.getUniqueId());
            return false;
        }
    }
    private static ItemStack paper(Session s) {
        var stack = new ItemStack(Material.PAPER);
        var im = stack.getItemMeta();
        im.displayName(Component.text(s.title).decoration(TextDecoration.ITALIC, false));
        if (s.logic.error() != null) im.lore(List.of(Component.text(s.logic.error(), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(im);
        return stack;
    }

    static Listener listener() { return new Events(); }

    static final class Events implements Listener {
        private static Session of(InventoryView v) {
            for (var s : SESSIONS.values()) if (s.view == v) return s;
            return null;
        }
        @EventHandler(priority = EventPriority.HIGH) public void prepare(PrepareAnvilEvent e) {
            var s = of(e.getView());
            if (s == null) return;
            String text = ((AnvilView) e.getView()).getRenameText();
            if (text == null || text.isBlank()) { e.setResult(null); return; }
            var out = new ItemStack(Material.PAPER); var im = out.getItemMeta();
            im.displayName(Component.text(text)); out.setItemMeta(im);
            e.setResult(out);
            ((AnvilView) e.getView()).setRepairCost(0);
        }
        @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent e) {
            var s = of(e.getView());
            if (s == null) return;
            e.setCancelled(true);
            if (e.getRawSlot() != AnvilPromptLogic.RESULT_SLOT) return;
            switch (s.logic.click(e.getRawSlot(), ((AnvilView) e.getView()).getRenameText())) {
                case ACCEPT -> { String v = s.logic.value(); finish(s); Bukkit.getScheduler().runTask(s.plugin, () -> s.done.accept(v)); }
                case REPROMPT -> Bukkit.getScheduler().runTask(s.plugin, () -> { if (!open(s)) { finish(s); s.cancelled.run(); } });
                case CANCEL -> { finish(s); s.player.sendMessage(Component.text("Too many tries, so I closed the text box.", NamedTextColor.YELLOW)); Bukkit.getScheduler().runTask(s.plugin, s.cancelled); }
                case NONE -> { }
            }
        }
        @EventHandler(priority = EventPriority.HIGHEST) public void drag(InventoryDragEvent e) { if (of(e.getView()) != null) e.setCancelled(true); }
        @EventHandler public void close(InventoryCloseEvent e) {
            var s = of(e.getView());
            if (s == null) return;
            e.getView().setItem(0, null);
            if (s.logic.close() == AnvilPromptLogic.Outcome.CANCEL) { finish(s); Bukkit.getScheduler().runTask(s.plugin, s.cancelled); }
            else if (s.logic.state() != AnvilPromptLogic.State.REPROMPT) finish(s);
        }
        @EventHandler public void quit(PlayerQuitEvent e) { SESSIONS.remove(e.getPlayer().getUniqueId()); }
    }
    /** Always empties the paper first, so it can never be handed back when the window closes. */
    private static void finish(Session s) { if (s.view != null) { s.view.setItem(0, null); s.view.setItem(1, null); } SESSIONS.remove(s.player.getUniqueId(), s); }
}
