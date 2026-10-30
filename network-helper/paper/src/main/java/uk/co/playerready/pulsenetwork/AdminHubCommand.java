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
 * {@code /admin [area [args]]} (hidden aliases {@code /adminmenu}, {@code /am}, kept one release): the one admin front door for dev, admin and staff. A bare
 * {@code /admin} opens the menu; {@code /admin <area> ...} does the same by text, with one area per menu section. It only shows what exists on this
 * server and what the viewer's group allows.
 * Role, identity and tab-hide changes are sent to the proxy as the player (its gating and audit stay the single path); name styles go to
 * LuckPerms; whitelist changes run UltimateWhitelist commands as the console after the permission check. Every action logs one
 * {@code ADMIN <actor> <action> <target>} line. Text goes through {@link AnvilPrompt}; Bedrock gets forms.
 */
final class AdminHubCommand implements CommandExecutor, TabCompleter {
    /** A button on a page: Java shows an item, Bedrock a form button. {@code head} makes it that player's head. */
    private record Btn(Material icon, String head, String label, String lore, boolean glow, Runnable run) {}

    private final JavaPlugin plugin;
    private final LuckPermsLink lp;
    private final BiPredicate<Player, List<String>> send;
    private final Env env;
    private final Announcer announcer;
    private final RoleMenuCommand roles;
    private final Map<String, String> minGroups;

    AdminHubCommand(JavaPlugin plugin, LuckPermsLink lp, BiPredicate<Player, List<String>> send, Announcer announcer, RoleMenuCommand roles) {
        this.plugin = plugin; this.lp = lp; this.send = send; this.announcer = announcer; this.roles = roles;
        var groups = new HashMap<String, String>();
        var sec = plugin.getConfig().getConfigurationSection("admin.areas");
        if (sec != null) for (Area a : Area.values()) { String g = sec.getString(a.id + ".min-group"); if (g != null) groups.put(a.id, g); }
        this.minGroups = Map.copyOf(groups);
        this.env = new Env() {
            public boolean pluginEnabled(String n) { return plugin.getServer().getPluginManager().isPluginEnabled(n); }
            public boolean routeAvailable(AdminRoutes.Owner o) {
                return plugin.getServer().getPluginManager().getPlugin(o.plugin()) instanceof JavaPlugin jp && jp.getCommand(AdminRoutes.ROUTE) != null;
            }
            public boolean bridgeEnabled() { return announcer != null && announcer.enabled(); }
        };
    }

    private static Component line(String text) { return Component.text(text, NamedTextColor.YELLOW); }

    private static final String UNKNOWN = "Unknown admin area. Type /admin to see what you can use.";

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("The admin hub is for players in game."); return true; }
        if (!may(p, Gate.HUB)) { p.sendMessage(line("The admin menu is for staff only.")); return true; }
        AdminHubModel.aliasHint(label, args).ifPresent(hint -> p.sendMessage(Component.text(hint, NamedTextColor.GRAY)));
        if (label.equalsIgnoreCase("rolemenu")) { route(p, new String[] {"roles", args.length > 0 ? args[0] : ""}); return true; }
        if (args.length == 0) main(p); else route(p, args);
        return true;
    }

    private List<Area> visibleTo(Player p) { return visible(env, g -> may(p, g), minGroups); }
    private Gate gateOf(Area a) { return AdminHubModel.gateOf(a, minGroups); }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p) || !may(p, Gate.HUB)) return List.of();
        var names = Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return complete(visibleTo(p), g -> may(p, g), names, a);
    }

    /** {@code /admin <area> [args]}. Hidden and unknown areas answer the same line. */
    private void route(Player p, String[] args) {
        var area = resolve(args[0], visibleTo(p));
        if (area.isEmpty()) { p.sendMessage(line(UNKNOWN)); return; }
        String arg1 = args.length > 1 ? args[1] : "";
        String rest = String.join(" ", Arrays.copyOfRange(args, Math.min(2, args.length), args.length));
        switch (area.get()) {
            case PLAYERS -> { if (arg1.isEmpty()) players(p); else lookup(p, arg1); }
            case ROLES -> { if (arg1.isEmpty() || RoleMenuCommands.validName(arg1)) { audit(p, "open roles", arg1); roles.open(p, arg1.isEmpty() ? null : arg1); } else p.sendMessage(line("Use /admin roles [player name].")); }
            case NICK -> {
                if (!RoleMenuCommands.validName(arg1) || rest.isEmpty()) { p.sendMessage(line("Use /admin nick <player> <nickname|reset>.")); return; }
                proxy(p, "nick", rest.equalsIgnoreCase(NickRules.RESET) ? "reset nickname" : "set nickname", arg1, List.of("nick", arg1, rest.equalsIgnoreCase(NickRules.RESET) ? NickRules.RESET : rest));
            }
            case STYLES -> { if (!RoleMenuCommands.validName(arg1)) p.sendMessage(line("Use /admin styles <player>.")); else styles(p, new Target(null, arg1)); }
            case IDENTITY -> { if (!RoleMenuCommands.validName(arg1)) p.sendMessage(line("Use /admin identity <player>.")); else proxy(p, "identity", "view identity", arg1, List.of("identity", arg1)); }
            case TAB -> {
                boolean hide = arg1.equalsIgnoreCase("hide");
                if ((!hide && !arg1.equalsIgnoreCase("show")) || args.length < 3 || !RoleMenuCommands.validName(args[2])) { p.sendMessage(line("Use /admin tab hide|show <player>.")); return; }
                proxy(p, "role", hide ? "hide" : "unhide", args[2], List.of(hide ? "hide" : "unhide", args[2]));
            }
            case WHITELIST -> whitelistText(p, arg1, args.length > 2 ? args[2] : null);
            case ANNOUNCE -> { if (arg1.isEmpty()) network(p); else announceStyle(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length))); }
            case GAMES, SURVIVAL, HUNT -> owner(area.get()).ifPresent(o -> ownerRoute(p, o, o.area(), String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
            case SERVER -> serverOwner(env).ifPresent(o -> ownerRoute(p, o, o.serverArea(), String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
        }
    }

    private void whitelistText(Player p, String verb, String name) {
        if (verb.isEmpty()) { whitelist(p); return; }
        var op = WHITELIST_VERBS.contains(verb.toLowerCase(Locale.ROOT)) ? WhitelistOp.valueOf(verb.toUpperCase(Locale.ROOT)) : null;
        if (op == null) { p.sendMessage(line("Use /admin whitelist [add|remove|on|off|list|status] [player].")); return; }
        wl(p, op, name);
    }

    /** Runs the owner's hidden route command as the player; the owner checks its own permission and answers in chat. */
    private void ownerRoute(Player p, AdminRoutes.Owner o, String area, String verbs) {
        audit(p, "open " + o.label(), verbs.isEmpty() ? "-" : verbs);
        p.closeInventory();
        p.performCommand(o.line(area, verbs));
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
        for (var a : visibleTo(p)) {
            switch (a) {
                case PLAYERS -> buttons.add(btn(Material.PLAYER_HEAD, "Players", "Roles, nicknames, name styles, identity, tab, teleport", () -> players(p)));
                case WHITELIST -> buttons.add(btn(Material.WRITABLE_BOOK, "Whitelist", "Add, remove, switch on or off, list", () -> whitelist(p)));
                case ANNOUNCE -> buttons.add(btn(Material.COMPASS, "Network", "Announce to every server", () -> network(p)));
                case GAMES, SURVIVAL, HUNT -> owner(a).ifPresent(o -> buttons.add(btn(Material.CHEST, o.label(), "Opens " + o.label() + "'s own admin menu",
                        guarded(p, gateOf(a), () -> ownerRoute(p, o, o.area(), "")))));
                default -> { } // roles, nick, styles, identity, tab live on the player page; server repeats the owner button
            }
        }
        if (!env.pluginEnabled(WHITELIST_PLUGIN)) buttons.add(new Btn(Material.GRAY_DYE, null, "Whitelist lives on the Lobby", "Click to go to the Lobby", false,
                () -> { p.closeInventory(); p.performCommand(AdminRoutes.GO_LOBBY); }));
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
        b.add(btn(Material.BOOK, "View identity", "Runs /identity " + t.name(), guarded(p, gateOf(Area.IDENTITY), () -> proxy(p, "identity", "view identity", t.name(), List.of("identity", t.name())))));
        b.add(btn(Material.DIAMOND, "Set role", "Opens the role menu", guarded(p, gateOf(Area.ROLES), () -> { audit(p, "open roles", t.name()); p.closeInventory(); roles.open(p, t.name()); })));
        b.add(btn(Material.NAME_TAG, "Name styles", "Grant or revoke styles (Dev/Admin)", guarded(p, gateOf(Area.STYLES), () -> styles(p, t))));
        b.add(btn(Material.WRITABLE_BOOK, "Set nickname", "Type a nickname for " + t.name(), guarded(p, gateOf(Area.NICK), () -> ask(p, "Nickname for " + t.name(), AnvilPromptLogic.Rule.nickname(),
                nick -> proxy(p, "nick", "set nickname", t.name(), List.of("nick", t.name(), nick))))));
        b.add(btn(Material.BARRIER, "Reset nickname", "Show their real name again", guarded(p, gateOf(Area.NICK), () -> confirm(p, "Reset " + t.name() + "'s nickname?",
                () -> proxy(p, "nick", "reset nickname", t.name(), List.of("nick", t.name(), "reset"))))));
        b.add(btn(Material.ENDER_EYE, "Hide from tab", "Runs /role hide", guarded(p, gateOf(Area.TAB), () -> proxy(p, "role", "hide", t.name(), List.of("hide", t.name())))));
        b.add(btn(Material.ENDER_PEARL, "Show in tab", "Runs /role unhide", guarded(p, gateOf(Area.TAB), () -> proxy(p, "role", "unhide", t.name(), List.of("unhide", t.name())))));
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
        p.sendMessage(Component.text("Sent /" + (RoleMenuCommands.bare(command) ? "" : "role ") + String.join(" ", fields) + ". The answer arrives in chat.", NamedTextColor.GRAY));
    }

    private void styles(Player p, Target t) {
        withId(p, t, id -> lp.heldStyles(id).whenComplete((held, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) { p.sendMessage(line("Sorry, I could not read that player's styles.")); return; }
            var b = new ArrayList<Btn>();
            for (var c : grid(p2 -> held.contains(p2.substring(NameStyleModel.PERMISSION_PREFIX.length())))) {
                b.add(new Btn(NameStyleModel.material(c.id()), null, NameStyleModel.title(c.id()) + (c.held() ? " (held)" : ""),
                        c.held() ? "Click to take away" : "Click to give", c.held(), guarded(p, gateOf(Area.STYLES), () -> toggleStyle(p, t, id, c))));
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
        b.add(btn(Material.GOAT_HORN, "Announce", "Type a message for every server", guarded(p, gateOf(Area.ANNOUNCE),
                () -> ask(p, "Announcement", AnvilPromptLogic.Rule.text(150), text -> announceStyle(p, text)))));
        b.add(btn(Material.ARROW, "Back", null, () -> main(p)));
        show(p, "Network", "Announcements reach every server.", b);
    }
    /** Pick how it shows (chat, title or action bar), confirm, then send through the proxy link. Titles split on " | " into title and subtitle. */
    private void announceStyle(Player p, String text) {
        var b = new ArrayList<Btn>();
        for (var st : ANNOUNCE_STYLES) b.add(btn(st.style().equals("title") ? Material.NAME_TAG : st.style().equals("bar") ? Material.PAPER : Material.OAK_SIGN,
                st.label(), "Send as " + st.label().toLowerCase(Locale.ROOT), guarded(p, gateOf(Area.ANNOUNCE), () -> confirm(p, "Send this announcement?", () -> {
                    if (!may(p, gateOf(Area.ANNOUNCE))) { p.sendMessage(line("That needs a Dev or Admin.")); return; }
                    audit(p, "announce " + st.style(), text);
                    p.sendMessage(line(announcer != null && announcer.announce(text, st.style())
                            ? "Announcement sent to the proxy. It allows one every 5 seconds; a second one sooner is dropped."
                            : "The announcement could not be sent: the network link is not configured, or the text was empty."));
                }))));
        b.add(btn(Material.ARROW, "Back", null, () -> network(p)));
        show(p, "Announce as", text, b);
    }
}
