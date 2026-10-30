package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import uk.co.playerready.pulsenetwork.RoleLogic.Action;
import uk.co.playerready.pulsenetwork.RoleLogic.Level;

/** Proxy-owned {@code /role}: role names are fixed, groups come from config, LuckPerms stays the source of truth. Plain text only, so Bedrock sees the same. */
final class RoleCommand implements SimpleCommand {
    private static final UUID CONSOLE_ID = new UUID(0, 0);
    private static final int LIST_CAP = 50;
    private record Removal(UUID target, String targetName, String role) {}
    private final ProxyServer proxy;
    private final Logger logger;
    private final RoleStore store;
    private final Map<String, String> groups;
    private final Set<String> configuredHidden;
    private final RoleLogic.Confirmations<Removal> pending = new RoleLogic.Confirmations<>(System::currentTimeMillis);

    RoleCommand(ProxyServer proxy, Logger logger, RoleStore store, Map<String, String> groups, Set<String> configuredHidden) {
        this.proxy = proxy; this.logger = logger; this.store = store; this.groups = groups; this.configuredHidden = configuredHidden;
    }

    @Override public List<String> suggest(Invocation i) {
        return RoleLogic.complete(i.arguments(), proxy.getAllPlayers().stream().map(Player::getUsername).toList());
    }

    @Override public void execute(Invocation i) {
        CommandSource src = i.source(); String[] a = i.arguments();
        Level level = level(src);
        try {
            String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "set", "remove" -> change(src, level, sub.equals("set"), a);
                case "confirm" -> confirm(src, level);
                case "who" -> who(src, level, a);
                case "list" -> list(src, level, a);
                case "venues" -> venues(src, level);
                case "hide", "unhide" -> hide(src, level, sub.equals("hide"), a);
                default -> help(src, level);
            }
        } catch (RuntimeException e) { fail(src, e); }
    }

    private Level level(CommandSource src) {
        return src instanceof Player p ? RoleLogic.level(store.cachedGroups(p.getUniqueId()), groups) : Level.CONSOLE;
    }
    private static String key(CommandSource src) { return src instanceof Player p ? p.getUniqueId().toString() : "console"; }
    private static UUID id(CommandSource src) { return src instanceof Player p ? p.getUniqueId() : CONSOLE_ID; }
    private static String name(CommandSource src) { return src instanceof Player p ? p.getUsername() : "console"; }
    private static void say(CommandSource src, String text) { src.sendMessage(Component.text(text, NamedTextColor.GRAY)); }
    private static void ok(CommandSource src, String text) { src.sendMessage(Component.text(text, NamedTextColor.GREEN)); }
    private static void no(CommandSource src, String text) { src.sendMessage(Component.text(text, NamedTextColor.RED)); }
    private void fail(CommandSource src, Throwable e) {
        logger.warn("ROLE command failed: {}", e.getClass().getSimpleName());
        no(src, "Something went wrong talking to the permissions system. Nothing was changed. Please tell a developer.");
    }
    private static boolean validName(String s) { return s.matches("[.A-Za-z0-9_]{1,17}"); }

    private void help(CommandSource src, Level level) {
        if (!RoleLogic.allowed(level, Action.VIEW, null)) { no(src, "You do not have permission to use /role."); return; }
        say(src, "Role commands: /role set <player> <role>, /role remove <player> <role>, /role who <player>, /role list [role], /role venues, /role hide <player>, /role unhide <player>.");
        say(src, "Roles: " + String.join(", ", RoleLogic.ROLES) + ". Removing needs /role confirm within 30 seconds.");
    }

    /** Looks up an exact name (online first, then the permissions database). */
    private CompletableFuture<Optional<UUID>> target(String name) {
        var online = proxy.getPlayer(name);
        return online.isPresent() ? CompletableFuture.completedFuture(Optional.of(online.get().getUniqueId())) : store.resolve(name);
    }

    private void change(CommandSource src, Level level, boolean set, String[] a) {
        if (a.length != 3) { say(src, "Use: /role " + a[0].toLowerCase(Locale.ROOT) + " <player> <role>"); return; }
        var role = RoleLogic.parseRole(a[2]);
        if (role.isEmpty()) { no(src, "That is not a role I know. Roles: " + String.join(", ", RoleLogic.ROLES) + "."); return; }
        Action action = set ? Action.SET : Action.REMOVE;
        if (!RoleLogic.allowed(level, action, role.get())) {
            no(src, level == Level.NONE ? "You do not have permission to use /role." : "Only a Dev can change the " + role.get() + " role. You can change venue roles.");
            return;
        }
        String group = groups.get(role.get());
        if (!store.groupExists(group)) { no(src, "The group for " + role.get() + " does not exist yet. Ask a Dev to create it."); return; }
        if (!validName(a[1])) { no(src, "That does not look like a player name."); return; }
        target(a[1]).thenCompose(t -> {
            if (t.isEmpty()) { no(src, "I do not know a player called " + a[1] + ". They need to have joined the network once. Names must match exactly."); return CompletableFuture.completedFuture(null); }
            UUID target = t.get();
            if (src instanceof Player p && p.getUniqueId().equals(target)) { no(src, "You cannot change your own roles."); return CompletableFuture.completedFuture(null); }
            return store.groups(target).thenCompose(have -> {
                boolean has = have.contains(group);
                if (set && has) { say(src, a[1] + " already has the " + role.get() + " role."); return CompletableFuture.completedFuture(null); }
                if (!set && !has) { say(src, a[1] + " does not have the " + role.get() + " role."); return CompletableFuture.completedFuture(null); }
                if (!set) {
                    pending.put(key(src), new Removal(target, a[1], role.get()));
                    say(src, "Remove " + role.get() + " from " + a[1] + "? Type /role confirm within 30 seconds to do it.");
                    return CompletableFuture.completedFuture(null);
                }
                return store.addGroup(target, group).thenRun(() -> done(src, true, role.get(), target, a[1]));
            });
        }).exceptionally(e -> { fail(src, e); return null; });
    }

    private void confirm(CommandSource src, Level level) {
        var removal = pending.take(key(src));
        if (removal.isEmpty()) { no(src, "There is nothing to confirm, or it took longer than 30 seconds. Run the remove command again."); return; }
        var r = removal.get();
        if (!RoleLogic.allowed(level, Action.REMOVE, r.role())) { no(src, "You do not have permission to do that any more."); return; }
        store.removeGroup(r.target(), groups.get(r.role())).thenRun(() -> done(src, false, r.role(), r.target(), r.targetName()))
                .exceptionally(e -> { fail(src, e); return null; });
    }

    private void done(CommandSource src, boolean set, String role, UUID target, String targetName) {
        String verb = set ? "set" : "remove";
        logger.info("ROLE {} {} {} for {}", name(src), verb, role, targetName);
        store.audit(id(src), name(src), target, targetName, "PulseNetwork /role " + verb + " " + role);
        ok(src, set ? targetName + " now has the " + role + " role." : targetName + " no longer has the " + role + " role.");
        mirror(src, "[Roles] " + name(src) + " " + (set ? "gave " : "removed ") + role + (set ? " to " : " from ") + targetName
                + ". Backend servers update within a few seconds.");
    }
    private void mirror(CommandSource actor, String text) {
        for (Player p : proxy.getAllPlayers()) {
            if (actor instanceof Player a && a.getUniqueId().equals(p.getUniqueId())) continue;
            Level l = RoleLogic.level(store.cachedGroups(p.getUniqueId()), groups);
            if (l == Level.DEV || l == Level.ADMIN) say(p, text);
        }
    }

    private void who(CommandSource src, Level level, String[] a) {
        if (!RoleLogic.allowed(level, Action.VIEW, null)) { no(src, "You do not have permission to use /role."); return; }
        if (a.length != 2 || !validName(a[1])) { say(src, "Use: /role who <player>"); return; }
        target(a[1]).thenCompose(t -> {
            if (t.isEmpty()) { no(src, "I do not know a player called " + a[1] + "."); return CompletableFuture.completedFuture(null); }
            return store.groups(t.get()).thenCombine(store.isHidden(t.get()), (have, hidden) -> {
                var roles = RoleLogic.ROLES.stream().filter(r -> have.contains(groups.get(r))).collect(Collectors.toList());
                say(src, a[1] + ": " + (roles.isEmpty() ? "no network roles" : String.join(", ", roles))
                        + (hidden ? ". Hidden from the tab list." : configuredHidden.contains(a[1].toLowerCase(Locale.ROOT)) ? ". Hidden from the tab list (config)." : "."));
                return null;
            });
        }).exceptionally(e -> { fail(src, e); return null; });
    }

    private void list(CommandSource src, Level level, String[] a) {
        if (!RoleLogic.allowed(level, Action.VIEW, null)) { no(src, "You do not have permission to use /role."); return; }
        var roles = a.length >= 2 ? RoleLogic.parseRole(a[1]).map(List::of).orElse(List.of()) : RoleLogic.ROLES;
        if (roles.isEmpty()) { no(src, "That is not a role I know. Roles: " + String.join(", ", RoleLogic.ROLES) + "."); return; }
        for (String role : roles) {
            if (!store.groupExists(groups.get(role))) { say(src, role + ": group not created yet"); continue; }
            store.members(groups.get(role)).thenCompose(ids -> {
                var shown = ids.stream().limit(LIST_CAP).map(u -> store.username(u).thenApply(n -> n == null ? "?" : n)).toList();
                return CompletableFuture.allOf(shown.toArray(CompletableFuture[]::new)).thenRun(() ->
                        say(src, role + " (" + ids.size() + "): " + (ids.isEmpty() ? "nobody" : shown.stream().map(CompletableFuture::join).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", ")))
                                + (ids.size() > LIST_CAP ? " ... and " + (ids.size() - LIST_CAP) + " more" : "")));
            }).exceptionally(e -> { fail(src, e); return null; });
        }
    }

    private void venues(CommandSource src, Level level) {
        if (!RoleLogic.allowed(level, Action.VIEW, null)) { no(src, "You do not have permission to use /role."); return; }
        for (String role : RoleLogic.ROLES) {
            if (!RoleLogic.isVenue(role)) continue;
            String g = groups.get(role);
            if (!store.groupExists(g)) { say(src, role + " (group " + g + "): group not created yet"); continue; }
            store.members(g).thenAccept(ids -> say(src, role + " (group " + g + "): " + ids.size() + " account(s). Players see only the generic VENUE tag."))
                    .exceptionally(e -> { fail(src, e); return null; });
        }
    }

    private void hide(CommandSource src, Level level, boolean hide, String[] a) {
        if (!RoleLogic.allowed(level, Action.HIDE, null)) { no(src, level == Level.NONE ? "You do not have permission to use /role." : "Only a Dev or Admin can hide players from the tab list."); return; }
        if (a.length != 2 || !validName(a[1])) { say(src, "Use: /role " + a[0].toLowerCase(Locale.ROOT) + " <player>"); return; }
        target(a[1]).thenCompose(t -> {
            if (t.isEmpty()) { no(src, "I do not know a player called " + a[1] + "."); return CompletableFuture.completedFuture(null); }
            return store.setHidden(t.get(), hide).thenRun(() -> {
                logger.info("ROLE {} {} tab-hidden for {}", name(src), hide ? "set" : "remove", a[1]);
                store.audit(id(src), name(src), t.get(), a[1], "PulseNetwork /role " + (hide ? "hide" : "unhide"));
                ok(src, hide ? a[1] + " is now hidden from the tab list." : a[1] + " is visible in the tab list again.");
                mirror(src, "[Roles] " + name(src) + (hide ? " hid " : " unhid ") + a[1] + (hide ? " from" : " in") + " the tab list.");
            });
        }).exceptionally(e -> { fail(src, e); return null; });
    }
}
