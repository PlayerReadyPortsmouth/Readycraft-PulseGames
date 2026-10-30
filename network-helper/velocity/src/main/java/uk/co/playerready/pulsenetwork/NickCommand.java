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
import java.util.function.Supplier;
import uk.co.playerready.pulsenetwork.RoleLogic.Level;

/** Proxy {@code /nick}: staff-only nicknames kept as LuckPerms user meta {@code pulse.nickname}. Plain text, so Bedrock sees the same. */
final class NickCommand implements SimpleCommand {
    private static final UUID CONSOLE_ID = new UUID(0, 0);
    private static final int STAFF_CAP = 200;
    private final ProxyServer proxy;
    private final Logger logger;
    private final RoleStore store;
    private final Map<String, String> groups;
    private final IdentityResolver.Meta meta;
    private final Set<String> blocked;
    private final Supplier<TabService> tab;

    NickCommand(ProxyServer proxy, Logger logger, RoleStore store, Map<String, String> groups, IdentityResolver.Meta meta, Set<String> blocked, Supplier<TabService> tab) {
        this.proxy = proxy; this.logger = logger; this.store = store; this.groups = groups; this.meta = meta; this.blocked = blocked; this.tab = tab;
    }

    @Override public boolean hasPermission(Invocation i) { return !(i.source() instanceof Player p) || RoleLogic.level(store.cachedGroups(p.getUniqueId()), groups) != Level.NONE; }

    @Override public List<String> suggest(Invocation i) {
        String[] a = i.arguments();
        if (a.length > 2) return List.of();
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        var pool = new ArrayList<String>(a.length == 2 ? List.of("reset") : proxy.getAllPlayers().stream().map(Player::getUsername).toList());
        if (a.length < 2) pool.add("reset");
        return pool.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).sorted().toList();
    }

    @Override public void execute(Invocation i) {
        CommandSource src = i.source(); String[] a = i.arguments();
        Level level = src instanceof Player p ? RoleLogic.level(store.cachedGroups(p.getUniqueId()), groups) : Level.CONSOLE;
        try {
            if (level == Level.NONE) { no(src, "Only staff can use /nick."); return; }
            if (a.length == 0 || a.length > 2) { help(src); return; }
            String value = a[a.length - 1];
            if (a.length == 1 && !(src instanceof Player)) { help(src); return; }
            String targetName = a.length == 2 ? a[0] : name(src);
            if (!targetName.matches("[.A-Za-z0-9_]{1,17}")) { no(src, "That does not look like a player name."); return; }
            boolean reset = value.equalsIgnoreCase(NickRules.RESET);
            boolean self = a.length == 1 || (src instanceof Player p && p.getUsername().equalsIgnoreCase(targetName));
            (self && src instanceof Player p ? CompletableFuture.completedFuture(Optional.of(p.getUniqueId())) : target(targetName)).thenCompose(t -> {
                if (t.isEmpty()) { no(src, "I do not know a player called " + targetName + ". They need to have joined the network once. Names must match exactly."); return CompletableFuture.completedFuture(null); }
                UUID target = t.get();
                return store.groups(target).thenCompose(have -> {
                    if (!NickLogic.mayChange(level, self, RoleLogic.level(have, groups))) { no(src, "You are not allowed to change " + (self ? "your" : targetName + "'s") + " nickname."); return CompletableFuture.completedFuture(null); }
                    if (reset) return store.setNickname(target, null).thenRun(() -> done(src, target, targetName, null));
                    return protectedNames(target).thenCompose(staff -> {
                        var result = NickRules.check(value, others(target), staff, blocked);
                        if (!result.ok()) { no(src, result.message()); return CompletableFuture.completedFuture(null); }
                        return store.setNickname(target, value).thenRun(() -> done(src, target, targetName, value));
                    });
                });
            }).exceptionally(e -> { logger.warn("NICK command failed: {}", e.getClass().getSimpleName()); no(src, "Something went wrong talking to the permissions system. Nothing was changed. Please tell a developer."); return null; });
        } catch (RuntimeException e) { no(src, "Something went wrong. Nothing was changed."); }
    }

    private CompletableFuture<Optional<UUID>> target(String name) {
        var online = proxy.getPlayer(name);
        return online.isPresent() ? CompletableFuture.completedFuture(Optional.of(online.get().getUniqueId())) : store.resolve(name);
    }
    /** Usernames and nicknames of everyone online except the target. */
    private List<String> others(UUID target) {
        var out = new ArrayList<String>();
        for (Player p : proxy.getAllPlayers()) {
            if (p.getUniqueId().equals(target)) continue;
            out.add(p.getUsername());
            String nick = IdentityResolver.nickname(meta.value(p.getUniqueId(), IdentityResolver.NICK_KEY));
            if (nick != null) out.add(nick);
        }
        return out;
    }
    /** Usernames of everyone in the dev, admin and staff groups except the target. */
    private CompletableFuture<List<String>> protectedNames(UUID target) {
        var lookups = new ArrayList<CompletableFuture<String>>();
        var done = new ArrayList<CompletableFuture<?>>();
        for (String role : List.of("dev", "admin", "staff")) {
            if (!store.groupExists(groups.get(role))) continue;
            done.add(store.members(groups.get(role)).thenAccept(ids -> {
                synchronized (lookups) { ids.stream().filter(u -> !u.equals(target)).limit(STAFF_CAP).forEach(u -> lookups.add(store.username(u))); }
            }));
        }
        return CompletableFuture.allOf(done.toArray(CompletableFuture[]::new)).thenCompose(v ->
                CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new)).thenApply(w -> lookups.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList()));
    }

    private void done(CommandSource src, UUID target, String targetName, String nick) {
        String verb = nick == null ? "reset" : "set";
        logger.info("NICK {} {} {}{}", name(src), verb, targetName, nick == null ? "" : " " + nick);
        store.audit(src instanceof Player p ? p.getUniqueId() : CONSOLE_ID, name(src), target, targetName, "PulseNetwork /nick " + verb + (nick == null ? "" : " " + nick));
        src.sendMessage(Component.text(nick == null ? targetName + " now shows their real name." : targetName + " is now shown as " + nick + ".", NamedTextColor.GREEN));
        proxy.getPlayer(target).filter(p -> !(src instanceof Player s && s.getUniqueId().equals(p.getUniqueId())))
                .ifPresent(p -> p.sendMessage(Component.text(nick == null ? "Your nickname was removed." : "Your nickname is now " + nick + ".", NamedTextColor.GRAY)));
        var service = tab.get();
        if (service != null) service.refresh();
    }
    private void help(CommandSource src) {
        src.sendMessage(Component.text("Use: /nick <name>, /nick reset, /nick <player> <name> or /nick <player> reset. 3 to 16 letters, numbers or _.", NamedTextColor.GRAY));
    }
    private static String name(CommandSource src) { return src instanceof Player p ? p.getUsername() : "console"; }
    private static void no(CommandSource src, String text) { src.sendMessage(Component.text(text, NamedTextColor.RED)); }
}
