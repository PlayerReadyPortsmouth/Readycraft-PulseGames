package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Test-only proxy defence before public/party fanout. Backend adapters still own local surfaces. */
final class NetworkSafeguarding implements AutoCloseable {
    static final String REVIEW = "pulsenetwork.safeguarding.review";
    private final SafeguardingConfig config;
    private final SafeguardingFilter filter;
    private final SafeguardingJournal journal;
    private final ProxyServer proxy;
    private final Logger logger;
    private final ThreadPoolExecutor alerts = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32), runnable -> { Thread thread = new Thread(runnable, "pulsenetwork-safeguarding-alert"); thread.setDaemon(true); return thread; });
    private final URI webhook;
    private final URI readyApp;
    private final String readyAppToken;
    private boolean healthy = true;
    NetworkSafeguarding(Properties properties, Path directory, ProxyServer proxy, Logger logger) throws IOException {
        config = SafeguardingConfig.parse(properties); this.proxy = proxy; this.logger = logger;
        filter = new SafeguardingFilter(config.extraWords());
        journal = config.testEnvironment() && config.logging()
                ? new SafeguardingJournal(directory.resolve("safeguarding"), Clock.systemUTC(), config.retentionDays()) : null;
        webhook = config.testEnvironment() ? webhook(config.webhookEnv()) : null;
        readyApp = config.testEnvironment() ? readyApp(config.readyAppEndpoint()) : null;
        String env = config.readyAppTokenEnv();
        readyAppToken = config.testEnvironment() && env.matches("[A-Z][A-Z0-9_]{0,79}") ? Objects.toString(System.getenv(env), "") : "";
        if (config.testEnvironment() && !config.webhookEnv().isEmpty() && webhook == null)
            logger.warn("Safeguarding Discord configuration unavailable or invalid; urgent staff alerts and the local queue remain available.");
        if (config.testEnvironment() && !config.readyAppEndpoint().isEmpty() && (readyApp == null || readyAppToken.isBlank()))
            logger.warn("Safeguarding ReadyApp configuration unavailable or invalid; staff must raise concerns from the local queue.");
    }
    private static URI webhook(String envName) {
        if (envName.isEmpty()) return null;
        String value = System.getenv(envName); if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || !Set.of("discord.com", "discordapp.com").contains(uri.getHost())
                    || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null
                    || !uri.getPath().matches("/api/webhooks/[0-9]+/[A-Za-z0-9_-]+")) return null;
            return uri;
        } catch (IllegalArgumentException ignored) { return null; }
    }
    static URI readyApp(String value) {
        if (value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !(uri.getPort() == -1 || uri.getPort() == 443)
                    || !"/safeguarding".equals(uri.getPath())) return null;
            return uri;
        } catch (IllegalArgumentException ignored) { return null; }
    }
    boolean mayRelay(String source, String target) { return !enabled() || config.backends().contains(source) == config.backends().contains(target); }
    boolean enabled() { return config.testEnvironment() && (config.filter() || config.logging()); }
    synchronized boolean allow(Player player, String backend, String channel, String input) {
        if (!config.applies(backend)) return true;
        SafeguardingFilter.Result verdict = filter.evaluate(input);
        String reference = UUID.randomUUID().toString();
        if (!healthy) {
            if (verdict.severity() == SafeguardingFilter.Severity.URGENT) urgent(reference, backend, channel, verdict.reasons(), true);
            player.sendMessage(Component.text("Chat safety checks are unavailable. Please ask a member of staff for help.")); return false;
        }
        boolean personal = verdict.reasons().stream().anyMatch(reason -> Set.of("email", "phone", "address", "off-platform-contact").contains(reason));
        String evidence = channel.equals("proxy-mail") ? "[private mail arguments withheld]" : personal ? "[withheld: personal/contact information]" : input;
        var entry = new SafeguardingJournal.Entry(reference, System.currentTimeMillis(), player.getUniqueId(), backend, channel,
                evidence, verdict.severity(), verdict.reasons(), (channel.startsWith("party:") || channel.equals("proxy-private") || channel.equals("proxy-mail")) ? "held-private" : config.filter() && verdict.blocked() ? "blocked" : "allowed");
        try { if (journal != null) journal.record(entry); }
        catch (IOException failure) {
            healthy = false; logger.error("Safeguarding journal unavailable; monitored proxy messages are held. No content logged.");
            staffNotice("[Safeguarding storage failure] Proxy messages are held on " + backend + ". Staff: check private storage immediately; no message content is in this alert.");
            if (verdict.severity() == SafeguardingFilter.Severity.URGENT) urgent(reference, backend, channel, verdict.reasons(), true);
            player.sendMessage(Component.text("We couldn't safely send that message. Please ask a member of staff for help.")); return false;
        }
        if (verdict.severity() == SafeguardingFilter.Severity.URGENT) urgent(reference, backend, channel, verdict.reasons(), false);
        if (config.filter() && verdict.blocked()) {
            player.sendMessage(Component.text(verdict.severity() == SafeguardingFilter.Severity.URGENT
                    ? "We held that message and let the safety team know. If you need help, please speak to a trusted adult or member of staff."
                    : "Please keep chat kind and keep personal/contact details private. Your message wasn't sent."));
            return false;
        }
        if (channel.startsWith("party:") || channel.equals("proxy-private") || channel.equals("proxy-mail")) { player.sendMessage(Component.text("Private and party text is held on safeguarding test servers until shared controls are verified. Please use moderated public chat.")); return false; }
        return true;
    }
    private void staffNotice(String notice) {
        for (Player staff : proxy.getAllPlayers()) if (staff.hasPermission(REVIEW)) staff.sendMessage(Component.text(notice));
    }
    private void urgent(String reference, String backend, String channel, List<String> reasons, boolean storageFailure) {
        String notice = "[URGENT safeguarding] " + reference + " · " + backend + " · " + channel + " · "
                + String.join(", ", reasons) + (storageFailure ? ". STORAGE FAILURE: message held, journal unavailable. Intervene now; raise a concern in ReadyApp."
                : ". Review /networkflagged. Raise a concern in ReadyApp.");
        staffNotice(notice);
        discord(reference, backend, reasons);
    }
    private void discord(String reference, String backend, List<String> reasons) {
        if (webhook == null && (readyApp == null || readyAppToken.isBlank())) return;
        // Only an opaque reference and rule codes leave the server; never chat, UUIDs or player names.
        String content = "URGENT ReadyCraft safeguarding reference " + reference + " on " + backend + ": " + String.join(", ", reasons) + ". Staff: review the private proxy journal and raise a concern in ReadyApp.";
        try { alerts.execute(() -> {
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build()) {
                if (webhook != null) deliver(client, webhook, "", "{\"allowed_mentions\":{\"parse\":[]},\"content\":\"" + content + "\"}", "Discord");
                if (readyApp != null && !readyAppToken.isBlank()) {
                    String description = "ReadyCraft URGENT proxy flag " + reference + " on " + backend + ": " + String.join(", ", reasons)
                            + ". Review the private proxy journal with /networkcontext. No automatic CYP or deal link was made.";
                    deliver(client, readyApp, readyAppToken, "{\"category\":\"out_of_venue\",\"severity\":\"critical\",\"description\":\"" + description + "\"}", "ReadyApp");
                }
            } catch (RuntimeException failure) { logger.warn("Safeguarding alert unavailable; review the private queue. Delivery may be ambiguous; do not retry without checking ReadyApp."); }
        }); } catch (RejectedExecutionException full) { logger.warn("Safeguarding alert queue full; review private flags immediately."); }
    }
    private void deliver(HttpClient client, URI endpoint, String token, String body, String service) {
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json");
            if (!token.isBlank()) request.header("x-mcp-token", token);
            int status = client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status < 200 || status >= 300) logger.warn("Safeguarding {} alert failed; review the private queue. HTTP {}", service, status);
        } catch (IOException | RuntimeException failure) { logger.warn("Safeguarding {} alert delivery uncertain; review the local queue and check the destination before any manual retry.", service); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
    void register(Object owner) {
        var commands = proxy.getCommandManager();
        commands.register(commands.metaBuilder("networkcontext").plugin(owner).build(), (SimpleCommand) invocation -> {
            if (!invocation.source().hasPermission(REVIEW)) return;
            if (invocation.arguments().length != 1) { invocation.source().sendMessage(Component.text("/networkcontext <flag reference>")); return; }
            try {
                UUID.fromString(invocation.arguments()[0]);
                if (journal == null) return;
                var lines = journal.context(invocation.arguments()[0]);
                invocation.source().sendMessage(Component.text("Private context: up to five preceding/following messages in this backend/channel. A party channel uses its leader UUID."));
                for (String line : lines) invocation.source().sendMessage(Component.text(line));
                logger.info("Safeguarding context reviewed by {}.", actor(invocation));
            } catch (IllegalArgumentException invalid) { invocation.source().sendMessage(Component.text("Use a flag reference from /networkflagged.")); }
            catch (IOException failure) { invocation.source().sendMessage(Component.text("Private journal unavailable. Contact the safeguarding lead.")); }
        });
        commands.register(commands.metaBuilder("networkflagged").plugin(owner).build(), (SimpleCommand) invocation -> {
            if (!invocation.source().hasPermission(REVIEW)) return;
            review(invocation, null, true);
        });
        commands.register(commands.metaBuilder("networkhistory").plugin(owner).build(), (SimpleCommand) invocation -> {
            if (!invocation.source().hasPermission(REVIEW)) return;
            if (invocation.arguments().length != 1) { invocation.source().sendMessage(Component.text("/networkhistory <player UUID>")); return; }
            try { review(invocation, UUID.fromString(invocation.arguments()[0]), false); }
            catch (IllegalArgumentException invalid) { invocation.source().sendMessage(Component.text("Use the player's UUID from the private flag record.")); }
        });
    }
    private synchronized void review(SimpleCommand.Invocation invocation, UUID player, boolean flagged) {
        if (journal == null) { invocation.source().sendMessage(Component.text("Proxy safeguarding logging is disabled.")); return; }
        try {
            var lines = journal.read(player, flagged, 20);
            invocation.source().sendMessage(Component.text("Private safeguarding records: latest " + lines.size() + ". UTC milliseconds, UUID, backend, channel, severity, reasons, outcome, message."));
            for (String line : lines) invocation.source().sendMessage(Component.text(line));
            if (flagged) invocation.source().sendMessage(Component.text("Use /networkcontext <reference> for surrounding messages, /networkhistory <UUID> for player history. Unreviewed flags remain in this queue until retention expiry; record the outcome in ReadyApp."));
            logger.info("Safeguarding {} reviewed by {}.", flagged ? "flags" : "history", actor(invocation));
        } catch (IOException failure) { invocation.source().sendMessage(Component.text("Private journal unavailable. Contact the safeguarding lead.")); }
    }
    private static String actor(SimpleCommand.Invocation invocation) {
        return invocation.source() instanceof Player staff ? staff.getUniqueId().toString() : "console";
    }
    @Override public void close() { alerts.shutdownNow(); }
}
