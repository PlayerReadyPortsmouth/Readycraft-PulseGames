package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

/** {@code /announce} and the signed backend {@code ANNOUNCE} message. Admins and developers (direct groups) and the console only. */
final class Announcer implements SimpleCommand {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Function<UUID, Set<String>> groupsOf;
    private final Map<String, String> roleGroups;
    private final Announce.Limiter limiter = new Announce.Limiter(5000, System::currentTimeMillis);

    Announcer(ProxyServer proxy, Logger logger, Function<UUID, Set<String>> groupsOf, Map<String, String> roleGroups) {
        this.proxy = proxy; this.logger = logger; this.groupsOf = groupsOf; this.roleGroups = roleGroups;
    }

    @Override public void execute(Invocation i) {
        CommandSource src = i.source();
        var level = src instanceof Player p ? RoleLogic.level(groupsOf.apply(p.getUniqueId()), roleGroups) : RoleLogic.Level.CONSOLE;
        if (!RoleLogic.canAnnounce(level)) { src.sendMessage(Component.text("You do not have permission to use /announce.", NamedTextColor.RED)); return; }
        Announce.Request request;
        try { request = Announce.parse(i.arguments()); }
        catch (IllegalArgumentException e) { src.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)); return; }
        if (!limiter.tryAcquire()) { src.sendMessage(Component.text("Please wait a few seconds before the next announcement.", NamedTextColor.RED)); return; }
        String by = src instanceof Player p ? p.getUsername() : "console";
        logger.info("ANNOUNCE by {} style={}: {}", by, request.style(), request.text());
        send(request.style(), request.text(), null);
        src.sendMessage(Component.text("Announcement sent.", NamedTextColor.GREEN));
    }

    /** Fields: text, style, scope. Already authenticated and sender-checked by the bridge. */
    void fromBackend(String server, List<String> fields) {
        if (fields.size() != 3) return;
        String text = Announce.clean(fields.get(0)), style = Announce.style(fields.get(1)), scope = fields.get(2).toLowerCase(Locale.ROOT);
        if (text == null || style == null || !Announce.SCOPES.contains(scope) || !limiter.tryAcquire()) return;
        logger.info("ANNOUNCE from server {} style={} scope={}: {}", server, style, scope, text);
        send(style, text, scope.equals("here") ? server : null);
    }

    private void send(String style, String text, String onlyServer) {
        Component line = Announce.chatLine(text);
        for (Player p : proxy.getAllPlayers()) {
            if (onlyServer != null && p.getCurrentServer().map(c -> !c.getServerInfo().getName().equals(onlyServer)).orElse(true)) continue;
            p.sendMessage(line); // Titles and bars also go to chat, so a missed one can be read again.
            if (style.equals("title")) {
                Component[] parts = Announce.titleParts(text);
                p.showTitle(Title.title(parts[0], parts[1], Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))));
            } else if (style.equals("bar")) p.sendActionBar(Announce.barLine(text));
        }
    }
}
