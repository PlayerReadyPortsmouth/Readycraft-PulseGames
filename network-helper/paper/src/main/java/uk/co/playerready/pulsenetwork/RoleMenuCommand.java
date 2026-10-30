package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.BiPredicate;

/**
 * {@code /admin roles [player]} (old {@code /rolemenu}, hidden alias): a click menu (Java) or form (Bedrock) for dev, admin and staff. It only asks the proxy to run
 * {@code /role ...} as the player, so the proxy's gating, audit line and confirm step stay the single path. The reply arrives as normal chat.
 */
final class RoleMenuCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final LuckPermsLink lp;
    private final BiPredicate<Player, List<String>> send;

    /** {@code send} returns false when the proxy link is not available. */
    RoleMenuCommand(JavaPlugin plugin, LuckPermsLink lp, BiPredicate<Player, List<String>> send) { this.plugin = plugin; this.lp = lp; this.send = send; }

    private static Component line(String text) { return Component.text(text, NamedTextColor.YELLOW); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Use /role from the console."); return true; }
        if (lp == null || !lp.inAnyGroup(p.getUniqueId(), RoleMenuModel.GROUPS)) { p.sendMessage(line("The role menu is for staff only.")); return true; }
        if (args.length > 1 || (args.length == 1 && !RoleMenuCommands.validName(args[0]))) { p.sendMessage(line("Use /rolemenu, or /rolemenu <player name>.")); return true; }
        open(p, args.length == 1 ? args[0] : null);
        return true;
    }

    /** Opens the player picker, or the actions for {@code target}. The caller has checked the group; {@code /admin roles} calls this too. */
    void open(Player p, String target) {
        if (lp == null || !lp.inAnyGroup(p.getUniqueId(), RoleMenuModel.GROUPS)) { p.sendMessage(line("The role menu is for staff only.")); return; }
        if (target != null && !target.isEmpty()) actions(p, target); else players(p);
    }

    private void players(Player p) {
        var names = RoleMenuModel.pickable(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), p.getName());
        if (names.isEmpty()) { p.sendMessage(line("Nobody else is online here. Use /admin roles <player name> for someone who is not.")); return; }
        if (LocalChatDelivery.bedrock(p) && BedrockForms.available()
                && BedrockForms.simple(plugin, p, "Roles: pick a player", RoleMenuModel.playerFormContent(), names, i -> actions(p, names.get(i)))) return;
        var menu = new ClickMenu(Component.text("Roles: pick a player"), 54);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            var head = new ItemStack(org.bukkit.Material.PLAYER_HEAD); var m = (SkullMeta) head.getItemMeta();
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) m.setOwningPlayer(online);
            m.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            head.setItemMeta(m);
            menu.set(i, head, () -> actions(p, name));
        }
        p.sendMessage(line("Pick a player. For someone who is offline, use /admin roles <player name>."));
        menu.open(p);
    }

    private void actions(Player p, String target) {
        var list = RoleMenuModel.actions();
        if (LocalChatDelivery.bedrock(p) && BedrockForms.available()
                && BedrockForms.simple(plugin, p, "Roles: " + target, "What would you like to do for " + target + "?", list.stream().map(RoleMenuModel.Action::label).toList(),
                        i -> run(p, target, list.get(i)))) return;
        var menu = new ClickMenu(Component.text("Roles: " + target), RoleMenuModel.MENU_SIZE);
        for (var a : list) menu.set(a.slot(), button(a), () -> run(p, target, a));
        menu.open(p);
    }
    private static ItemStack button(RoleMenuModel.Action a) {
        var mat = a.role() == null ? org.bukkit.Material.BOOK : RoleMenuModel.material(a.role());
        var stack = new ItemStack(a.kind() == RoleMenuModel.Kind.REMOVE ? org.bukkit.Material.RED_DYE : mat);
        var im = stack.getItemMeta();
        im.displayName(Component.text(a.label(), a.kind() == RoleMenuModel.Kind.REMOVE ? NamedTextColor.RED : NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(im);
        return stack;
    }

    private void run(Player p, String target, RoleMenuModel.Action a) {
        if (a.kind() != RoleMenuModel.Kind.REMOVE) { dispatch(p, RoleMenuModel.fields(a, target)); return; }
        String text = RoleMenuModel.confirmText(target, a.role());
        if (LocalChatDelivery.bedrock(p) && BedrockForms.available()
                && BedrockForms.modal(plugin, p, "Are you sure?", text, "Yes, take it away", "No", () -> remove(p, target, a))) return;
        new ClickMenu(Component.text(text), 9)
                .set(2, named(org.bukkit.Material.LIME_CONCRETE, "Yes, take it away", NamedTextColor.GREEN), () -> { p.closeInventory(); remove(p, target, a); })
                .set(6, named(org.bukkit.Material.RED_CONCRETE, "No", NamedTextColor.RED), p::closeInventory).open(p);
    }
    private static ItemStack named(org.bukkit.Material m, String text, NamedTextColor c) {
        var s = new ItemStack(m); var im = s.getItemMeta(); im.displayName(Component.text(text, c).decoration(TextDecoration.ITALIC, false)); s.setItemMeta(im); return s;
    }

    /** Sends remove, then confirm half a second later (inside the proxy's 30-second window). */
    private void remove(Player p, String target, RoleMenuModel.Action a) {
        if (!dispatch(p, RoleMenuModel.fields(a, target))) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) dispatch(p, RoleMenuCommands.fields("confirm", null, null)); }, 10);
    }

    private boolean dispatch(Player p, Optional<List<String>> fields) {
        if (fields.isEmpty()) { p.sendMessage(line("That is not a request the role menu can make.")); return false; }
        if (!send.test(p, fields.get())) { p.sendMessage(line("The role menu cannot reach the network proxy from this server.")); return false; }
        p.sendMessage(Component.text("Sent /role " + String.join(" ", fields.get()) + ". The answer arrives in chat.", NamedTextColor.GRAY));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        return a.length == 1 ? Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }
}
