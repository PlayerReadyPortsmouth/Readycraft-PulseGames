package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;
import java.util.function.Supplier;

/** Proxy {@code /identity [player]} and {@code /identity refresh}: shows what the network resolves for a player. Read-only; refresh only re-renders tabs. */
final class IdentityCommand implements SimpleCommand {
    private final ProxyServer proxy;
    private final RoleStore store;
    private final Map<String, String> groups;
    private final IdentityResolver.Meta meta;
    private final Supplier<TabService> tab;
    private final NameStyle.Tags tags;

    IdentityCommand(ProxyServer proxy, RoleStore store, Map<String, String> groups, IdentityResolver.Meta meta, Supplier<TabService> tab, NameStyle.Tags tags) {
        this.proxy = proxy; this.store = store; this.groups = groups; this.meta = meta; this.tab = tab; this.tags = tags;
    }

    @Override public List<String> suggest(Invocation i) {
        String last = i.arguments().length == 0 ? "" : i.arguments()[i.arguments().length - 1].toLowerCase(Locale.ROOT);
        if (i.arguments().length > 1) return List.of();
        var pool = new ArrayList<>(proxy.getAllPlayers().stream().map(Player::getUsername).toList());
        pool.add("refresh");
        return pool.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).sorted().toList();
    }

    @Override public void execute(Invocation i) {
        CommandSource src = i.source(); String[] a = i.arguments();
        var level = src instanceof Player p ? RoleLogic.level(store.cachedGroups(p.getUniqueId()), groups) : RoleLogic.Level.CONSOLE;
        try {
            if (a.length == 1 && a[0].equalsIgnoreCase("refresh")) { refresh(src, level); return; }
            if (a.length > 1) { say(src, "Use: /identity [player] or /identity refresh"); return; }
            Optional<Player> target = a.length == 1 ? proxy.getPlayer(a[0]) : src instanceof Player p ? Optional.of(p) : Optional.empty();
            if (target.isEmpty()) { say(src, a.length == 1 ? "That player is not online. Names must match exactly." : "Use: /identity <player>"); return; }
            Player t = target.get();
            boolean self = src instanceof Player p && p.getUniqueId().equals(t.getUniqueId());
            if (!IdentityReport.mayView(level, self)) { no(src, "You can only look at your own identity."); return; }
            var service = tab.get();
            var server = t.getCurrentServer().map(c -> c.getServerInfo().getName()).orElse(null);
            var facts = new IdentityReport.Facts(t.getUsername(), store.cachedGroups(t.getUniqueId()), meta.value(t.getUniqueId(), IdentityResolver.ROLE_KEY),
                    meta.value(t.getUniqueId(), IdentityResolver.STYLE_KEY), IdentityReport.stylesHeld(t::hasPermission),
                    service == null || server == null ? null : service.level(server, t.getUniqueId()), service != null && service.hidden(t),
                    service != null ? service.bedrock(t) : t.getUniqueId().getMostSignificantBits() == 0, server,
                    meta.value(t.getUniqueId(), IdentityResolver.NICK_KEY));
            IdentityReport.lines(facts, tags).forEach(l -> say(src, l));
        } catch (RuntimeException e) { no(src, "Something went wrong reading the identity. Nothing was changed."); }
    }

    private void refresh(CommandSource src, RoleLogic.Level level) {
        if (!IdentityReport.mayRefresh(level)) { no(src, "Only a Dev or Admin can refresh the tab list."); return; }
        var service = tab.get();
        if (service == null) { say(src, "The network tab is not enabled on this proxy."); return; }
        service.refresh();
        ok(src, "Tab lists will be rebuilt within a few seconds.");
    }
    private static void say(CommandSource src, String text) { src.sendMessage(Component.text(text, NamedTextColor.GRAY)); }
    private static void ok(CommandSource src, String text) { src.sendMessage(Component.text(text, NamedTextColor.GREEN)); }
    private static void no(CommandSource src, String text) { src.sendMessage(Component.text(text, NamedTextColor.RED)); }
}
