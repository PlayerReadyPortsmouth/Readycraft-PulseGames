package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** {@code /namestyle}: pick a name style you have unlocked. The choice is LuckPerms user meta, so the proxy and every backend read it. */
final class NameStyleCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final LuckPermsLink lp;
    private final LuckPermsMeta meta = new LuckPermsMeta();
    private final Map<UUID, Long> changed = new HashMap<>();

    NameStyleCommand(JavaPlugin plugin, LuckPermsLink lp) { this.plugin = plugin; this.lp = lp; }

    private static Component line(String text) { return Component.text(text, NamedTextColor.YELLOW); }
    private static Component name(Player p, String style) { return NameStyle.render(new NameStyle.Identity(p.getName(), null, style, null), false, 0); }
    private List<NameStyleModel.Entry> entries(Player p) {
        return NameStyleModel.entries(p::hasPermission, NameStyleModel.known(meta.value(p.getUniqueId(), IdentityResolver.STYLE_KEY)));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can pick a name style."); return true; }
        if (lp == null) { p.sendMessage(line("Name styles need LuckPerms, which is not running on this server.")); return true; }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> open(p);
            case "list" -> list(p);
            case "clear" -> apply(p, null);
            case "set" -> { if (args.length == 2) apply(p, args[1].toLowerCase(Locale.ROOT)); else p.sendMessage(line("Use /namestyle set <style>, or /namestyle list to see them.")); }
            default -> p.sendMessage(line("Use /namestyle to pick from a menu, or /namestyle list, /namestyle set <style>, /namestyle clear."));
        }
        return true;
    }

    private void list(Player p) {
        var all = entries(p);
        p.sendMessage(line("Unlocked: " + names(all, true)));
        p.sendMessage(Component.text("Locked (unlock by levelling up): " + names(all, false), NamedTextColor.GRAY));
    }
    private static String names(List<NameStyleModel.Entry> all, boolean unlocked) {
        var out = all.stream().filter(e -> e.unlocked() == unlocked).map(e -> e.id()).toList();
        return out.isEmpty() ? "none" : String.join(", ", out);
    }

    private void open(Player p) {
        var all = entries(p);
        if (LocalChatDelivery.bedrock(p) && BedrockForms.available()) {
            var buttons = NameStyleModel.formButtons(all);
            var labels = new ArrayList<>(buttons.stream().map(NameStyleModel::button).toList());
            labels.add("Clear my style");
            if (BedrockForms.simple(plugin, p, "Name style", NameStyleModel.formContent(all), labels,
                    i -> apply(p, i < buttons.size() ? buttons.get(i).id() : null))) return;
        }
        var menu = new ClickMenu(Component.text("Pick a name style"), NameStyleModel.MENU_SIZE);
        NameStyleModel.slots(all).forEach((slot, id) -> {
            if (id.equals(NameStyleModel.CLEAR)) {
                menu.set(slot, item(org.bukkit.Material.BARRIER, Component.text("Clear my style", NamedTextColor.RED), List.of(), false), () -> apply(p, null));
                return;
            }
            var e = all.get(slot);
            menu.set(slot, entryItem(p, e), e.unlocked() ? () -> apply(p, e.id()) : () -> p.sendMessage(Component.text("That style is locked. Unlock it by levelling up.", NamedTextColor.GRAY)));
        });
        menu.open(p);
    }

    private ItemStack entryItem(Player p, NameStyleModel.Entry e) {
        if (!e.unlocked()) {
            return item(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, Component.text(NameStyleModel.title(e.id()) + " (locked)", NamedTextColor.DARK_GRAY),
                    List.of(Component.text("Unlock by levelling up", NamedTextColor.GRAY)), false);
        }
        return item(NameStyleModel.material(e.id()), name(p, e.id()),
                List.of(Component.text(e.current() ? "Selected. Click to keep it." : "Click to use this style.", NamedTextColor.GRAY)), e.current());
    }
    private static ItemStack item(org.bukkit.Material m, Component title, List<Component> lore, boolean glow) {
        var stack = new ItemStack(m); var im = stack.getItemMeta();
        im.displayName(title.decoration(TextDecoration.ITALIC, false));
        im.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        if (glow) im.setEnchantmentGlintOverride(true);
        stack.setItemMeta(im);
        return stack;
    }

    /** Checks the unlock again, rate-limits, writes the meta, then confirms with the rendered name. {@code style} null clears. */
    void apply(Player p, String style) {
        if (style != null && !NameStyle.hasStyle(style)) { p.sendMessage(line("I do not know that style. Try /namestyle list.")); return; }
        if (style != null && !p.hasPermission(NameStyleModel.PERMISSION_PREFIX + style)) { p.sendMessage(line("You have not unlocked that style yet. Unlock it by levelling up.")); return; }
        if (!NameStyleModel.allow(changed, p.getUniqueId(), System.currentTimeMillis())) { p.sendMessage(line("Slow down a moment, then try again.")); return; }
        lp.setStyle(p.getUniqueId(), style).whenComplete((ok, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) { p.sendMessage(line("Sorry, that did not save. Try again in a moment.")); return; }
            if (style == null) p.sendMessage(line("Your name style is cleared. Other servers and the tab list catch up in a few seconds."));
            else p.sendMessage(line("Your name now looks like ").append(name(p, style)).append(line(". Other servers and the tab list catch up in a few seconds.")));
        }));
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("list", "set", "clear").stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        if (a.length == 2 && a[0].equalsIgnoreCase("set") && s instanceof Player p)
            return entries(p).stream().filter(NameStyleModel.Entry::unlocked).map(NameStyleModel.Entry::id).filter(x -> x.startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
