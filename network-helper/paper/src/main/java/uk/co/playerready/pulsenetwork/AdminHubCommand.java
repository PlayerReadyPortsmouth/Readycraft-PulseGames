package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.BiPredicate;
import static uk.co.playerready.pulsenetwork.AdminHubModel.*;

/**
 * {@code /admin} (aliases {@code /adminmenu}, {@code /am}): one hub for dev, admin and staff that only shows what exists on this server.
 * Role, identity and tab-hide changes are sent to the proxy as the player (its gating and audit stay the single path); name styles go to
 * LuckPerms; whitelist changes run UltimateWhitelist commands as the console after the permission check. Every action logs one
 * {@code ADMIN <actor> <action> <target>} line. Text goes through {@link AnvilPrompt}; Bedrock gets forms.
 */
final class AdminHubCommand implements CommandExecutor {
    /** A button on a page: Java shows an item, Bedrock a form button. {@code head} makes it that player's head. */
    private record Btn(Material icon, String head, String label, String lore, boolean glow, Runnable run) {}

    private final JavaPlugin plugin;
    private final LuckPermsLink lp;
    private final BiPredicate<Player, List<String>> send;
    private final Env env;

    AdminHubCommand(JavaPlugin plugin, LuckPermsLink lp, BiPredicate<Player, List<String>> send) {
        this.plugin = plugin; this.lp = lp; this.send = send;
        this.env = new Env() {
            public boolean pluginEnabled(String n) { return plugin.getServer().getPluginManager().isPluginEnabled(n); }
            public boolean hasCommand(String n) { return Bukkit.getCommandMap().getCommand(n) != null; }
        };
    }

    private static Component line(String text) { return Component.text(text, NamedTextColor.YELLOW); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("The admin hub is for players in game."); return true; }
        if (!may(p, Gate.HUB)) { p.sendMessage(line("The admin menu is for staff only.")); return true; }
        main(p);
        return true;
    }

    /** Checked on open and again on every action, so a demoted player's open menu stops working. */
    private boolean may(Player p, Gate gate) {
        if (lp == null) return false;
        var groups = new HashSet<String>();
        for (String g : HUB_GROUPS) if (lp.inAnyGroup(p.getUniqueId(), Set.of(g))) groups.add(g);
        return allowed(gate, groups);
    }
    private Runnable guarded(Player p, Gate gate, Runnable run) {
        return () -> { if (may(p, gate)) run.run(); else { p.closeInventory(); p.sendMessage(line(gate == Gate.ADMIN ? "That needs a Dev or Admin." : "The admin menu is for staff only.")); } };
    }
    private void audit(Player p, String action, String target) { plugin.getLogger().info(AdminHubModel.audit(p.getName(), action, target)); }

    // ---- rendering: one page model, a chest menu for Java and a form for Bedrock

    private void show(Player p, String title, String content, List<Btn> buttons) {
        if (LocalChatDelivery.bedrock(p) && BedrockForms.available()
                && BedrockForms.simple(plugin, p, title, content, buttons.stream().map(Btn::label).toList(), i -> buttons.get(i).run().run())) return;
        var menu = new ClickMenu(Component.text(title), menuSize(buttons.size()));
        for (int i = 0; i < buttons.size() && i < 54; i++) { var b = buttons.get(i); menu.set(i, item(b), b.run()); }
        menu.open(p);
    }
    private static ItemStack item(Btn b) {
        var stack = new ItemStack(b.head() != null ? Material.PLAYER_HEAD : b.icon());
        var im = stack.getItemMeta();
        if (im instanceof SkullMeta sm && b.head() != null) { Player online = Bukkit.getPlayerExact(b.head()); if (online != null) sm.setOwningPlayer(online); }
        im.displayName(Component.text(b.label(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        if (b.lore() != null) im.lore(List.of(Component.text(b.lore(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        if (b.glow()) im.setEnchantmentGlintOverride(true);
        stack.setItemMeta(im);
        return stack;
    }
    private static Btn btn(Material m, String label, String lore, Runnable run) { return new Btn(m, null, label, lore, false, run); }

    // ---- main

    private void main(Player p) {
        var buttons = new ArrayList<Btn>();
        for (var s : visible(env)) {
            switch (s) {
                case PLAYERS -> buttons.add(btn(Material.PLAYER_HEAD, "Players", "Roles, name styles, identity, tab, teleport", () -> players(p)));
                case WHITELIST -> buttons.add(btn(Material.WRITABLE_BOOK, "Whitelist", "Add, remove, switch on or off, list", () -> whitelist(p)));
                case NETWORK -> buttons.add(btn(Material.COMPASS, "Network", "Announce to every server", () -> network(p)));
                default -> plugin(s).ifPresent(ps -> buttons.add(btn(Material.CHEST, ps.label(), "Opens " + ps.label() + "'s own admin menu", guarded(p, Gate.HUB, () -> {
                    audit(p, "open " + ps.label(), "-"); p.closeInventory(); p.performCommand(ps.command()); }))));
            }
        }
        if (!env.pluginEnabled(WHITELIST_PLUGIN)) buttons.add(new Btn(Material.GRAY_DYE, null, "Whitelist lives on the Lobby", "Click to go to the Lobby", false,
                () -> { p.closeInventory(); p.performCommand("server lobby"); }));
        show(p, "Admin menu", "Everything you can manage on this server.", buttons);
    }

    // ---- players

    private void players(Player p) {
        var buttons = new ArrayList<Btn>();
        buttons.add(btn(Material.NAME_TAG, "Search player", "Type a name, even if they are offline", () -> ask(p, "Player name", AnvilPromptLogic.Rule.playerName(), name -> lookup(p, name))));
        var names = Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(RoleMenuCommands::validName).sorted(String.CASE_INSENSITIVE_ORDER).limit(51).toList();
        for (String n : names) buttons.add(new Btn(null, n, n, "Click for this player", false, () -> target(p, new Target(null, n))));
        buttons.add(btn(Material.ARROW, "Back", null, () -> main(p)));
        show(p, "Players", "Pick a player, or search by name.", buttons);
    }
    private record Target(UUID id, String name) {}

    private void ask(Player p, String title, AnvilPromptLogic.Rule rule, java.util.function.Consumer<String> done) {
        p.closeInventory();
        AnvilPrompt.ask(plugin, p, title, rule, done, () -> { });
    }

    private void lookup(Player p, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) { target(p, new Target(online.getUniqueId(), online.getName())); return; }
        lp.lookup(name).whenComplete((id, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null || id == null) p.sendMessage(line("I do not know a player called " + name + "."));
            else target(p, new Target(id, name));
        }));
    }

    private void target(Player p, Target t) {
        Player online = Bukkit.getPlayerExact(t.name());
        var b = new ArrayList<Btn>();
        b.add(btn(Material.BOOK, "View identity", "Runs /identity " + t.name(), guarded(p, Gate.HUB, () -> proxy(p, "identity", "view identity", t.name(), List.of("identity", t.name())))));
        b.add(btn(Material.DIAMOND, "Set role", "Opens the role menu", guarded(p, Gate.HUB, () -> { audit(p, "open rolemenu", t.name()); p.closeInventory(); p.performCommand("rolemenu " + t.name()); })));
        b.add(btn(Material.NAME_TAG, "Name styles", "Grant or revoke styles (Dev/Admin)", guarded(p, Gate.ADMIN, () -> styles(p, t))));
        b.add(btn(Material.ENDER_EYE, "Hide from tab", "Runs /role hide", guarded(p, Gate.ADMIN, () -> proxy(p, "role", "hide", t.name(), List.of("hide", t.name())))));
        b.add(btn(Material.ENDER_PEARL, "Show in tab", "Runs /role unhide", guarded(p, Gate.ADMIN, () -> proxy(p, "role", "unhide", t.name(), List.of("unhide", t.name())))));
        if (online != null && !online.equals(p)) b.add(btn(Material.ELYTRA, "Teleport to", "Go to " + t.name(), guarded(p, Gate.HUB, () -> {
            Player now = Bukkit.getPlayerExact(t.name());
            if (now == null) { p.sendMessage(line(t.name() + " is not on this server any more.")); return; }
            audit(p, "teleport", t.name()); p.closeInventory(); p.teleport(now);
        })));
        b.add(btn(Material.ARROW, "Back", null, () -> players(p)));
        show(p, "Player: " + t.name(), "What would you like to do for " + t.name() + "?", b);
    }

    private void proxy(Player p, String command, String action, String name, List<String> fields) {
        if (RoleMenuCommands.fromFields(fields).isEmpty()) { p.sendMessage(line("That is not a request the admin menu can make.")); return; }
        if (!send.test(p, fields)) { p.sendMessage(line("The admin menu cannot reach the network proxy from this server.")); return; }
        audit(p, action, name);
        p.sendMessage(Component.text("Sent /" + (command.equals("identity") ? "" : "role ") + String.join(" ", fields) + ". The answer arrives in chat.", NamedTextColor.GRAY));
    }

    private void styles(Player p, Target t) {
        withId(p, t, id -> lp.heldStyles(id).whenComplete((held, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) { p.sendMessage(line("Sorry, I could not read that player's styles.")); return; }
            var b = new ArrayList<Btn>();
            for (var c : grid(p2 -> held.contains(p2.substring(NameStyleModel.PERMISSION_PREFIX.length())))) {
                b.add(new Btn(NameStyleModel.material(c.id()), null, NameStyleModel.title(c.id()) + (c.held() ? " (held)" : ""),
                        c.held() ? "Click to take away" : "Click to give", c.held(), guarded(p, Gate.ADMIN, () -> toggleStyle(p, t, id, c))));
            }
            b.add(btn(Material.ARROW, "Back", null, () -> target(p, t)));
            show(p, "Name styles: " + t.name(), "Held styles are marked. Tap one to switch it.", b);
        })));
    }
    private void toggleStyle(Player p, Target t, UUID id, Cell c) {
        boolean grant = !c.held();
        lp.setPermission(id, permission(c.id()), grant).whenComplete((ok, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) { p.sendMessage(line("Sorry, that did not save.")); return; }
            audit(p, (grant ? "grant style " : "revoke style ") + c.id(), t.name());
            p.sendMessage(line((grant ? "Gave " : "Took away ") + NameStyleModel.title(c.id()) + (grant ? " to " : " from ") + t.name() + "."));
            styles(p, t);
        }));
    }
    private void withId(Player p, Target t, java.util.function.Consumer<UUID> then) {
        if (t.id() != null) { then.accept(t.id()); return; }
        Player online = Bukkit.getPlayerExact(t.name());
        if (online != null) { then.accept(online.getUniqueId()); return; }
        lp.lookup(t.name()).whenComplete((id, err) -> Bukkit.getScheduler().runTask(plugin, () -> { if (id == null) p.sendMessage(line("I do not know that player.")); else then.accept(id); }));
    }

    // ---- whitelist

    private void whitelist(Player p) {
        var b = new ArrayList<Btn>();
        b.add(btn(Material.LIME_DYE, "Add player", "Type a name to whitelist", guarded(p, Gate.HUB, () -> ask(p, "Add to whitelist", AnvilPromptLogic.Rule.playerName(), n -> wl(p, WhitelistOp.ADD, n)))));
        b.add(btn(Material.RED_DYE, "Remove player", "Type a name, then confirm", guarded(p, Gate.ADMIN, () -> ask(p, "Remove from whitelist", AnvilPromptLogic.Rule.playerName(),
                n -> confirm(p, "Remove " + n + " from the whitelist?", () -> wl(p, WhitelistOp.REMOVE, n))))));
        b.add(btn(Material.LEVER, "Switch on", "Confirm first", guarded(p, Gate.ADMIN, () -> confirm(p, "Turn the whitelist ON?", () -> wl(p, WhitelistOp.ON, null)))));
        b.add(btn(Material.REDSTONE_TORCH, "Switch off", "Confirm first", guarded(p, Gate.ADMIN, () -> confirm(p, "Turn the whitelist OFF? Anyone can then join.", () -> wl(p, WhitelistOp.OFF, null)))));
        b.add(btn(Material.PAPER, "List", "Shows the whitelist in chat", guarded(p, Gate.HUB, () -> wl(p, WhitelistOp.LIST, null))));
        b.add(btn(Material.CLOCK, "Status", "On or off", guarded(p, Gate.HUB, () -> wl(p, WhitelistOp.STATUS, null))));
        b.add(btn(Material.ARROW, "Back", null, () -> main(p)));
        show(p, "Whitelist", "Managed by UltimateWhitelist on this server.", b);
    }
    private void wl(Player p, WhitelistOp op, String name) {
        if (!may(p, AdminHubModel.gate(op))) { p.sendMessage(line("That needs a Dev or Admin.")); return; }
        var cmd = whitelistCommand(op, name);
        if (cmd.isEmpty() || !env.pluginEnabled(WHITELIST_PLUGIN)) { p.sendMessage(line("The whitelist cannot do that here.")); return; }
        audit(p, "whitelist " + op.name().toLowerCase(Locale.ROOT), name);
        var console = Bukkit.createCommandSender(c -> p.sendMessage(c));
        Bukkit.dispatchCommand(console, cmd.get());
        if (op != WhitelistOp.LIST && op != WhitelistOp.STATUS) p.sendMessage(line("Done: " + cmd.get() + "."));
    }

    private void confirm(Player p, String text, Runnable yes) {
        if (LocalChatDelivery.bedrock(p) && BedrockForms.available()
                && BedrockForms.modal(plugin, p, "Are you sure?", text, "Yes", "No", yes)) return;
        new ClickMenu(Component.text(text), 9)
                .set(2, item(btn(Material.LIME_CONCRETE, "Yes", null, null)), () -> { p.closeInventory(); yes.run(); })
                .set(6, item(btn(Material.RED_CONCRETE, "No", null, null)), p::closeInventory).open(p);
    }

    // ---- network

    private void network(Player p) {
        var b = new ArrayList<Btn>();
        b.add(btn(Material.GOAT_HORN, "Announce", "Type a message for every server", guarded(p, Gate.ADMIN,
                () -> ask(p, "Announcement", AnvilPromptLogic.Rule.text(150), text -> confirm(p, "Send this announcement?", () -> {
                    audit(p, "announce", text); p.performCommand("announce " + text); })))));
        b.add(btn(Material.ARROW, "Back", null, () -> main(p)));
        show(p, "Network", "Announcements reach every server.", b);
    }
}
