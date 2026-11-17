package uk.co.playerready.pulsenetwork;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Optional metadata-only destinations. No retries: an ambiguous concern POST may already be accepted. */
public final class SafeguardingDelivery implements AutoCloseable {
    private final URI discord, readyApp;
    private final String token;
    private final Consumer<String> log;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32), task -> {
        Thread thread = new Thread(task, "paper-safeguarding-alerts"); thread.setDaemon(true); return thread;
    });
    public SafeguardingDelivery(SafeguardingConfig config, Consumer<String> log) {
        this.log = log;
        discord = config.testEnvironment() ? endpoint(environment(config.webhookEnv()), true) : null;
        readyApp = config.testEnvironment() ? endpoint(config.readyAppEndpoint(), false) : null;
        token = config.testEnvironment() ? environment(config.readyAppTokenEnv()) : "";
    }
    private static String environment(String name) { return name.matches("[A-Z][A-Z0-9_]{0,79}") ? Objects.toString(System.getenv(name), "") : ""; }
    public static URI endpoint(String value, boolean webhook) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !(uri.getPort() == -1 || uri.getPort() == 443)) return null;
            if (webhook ? !(Set.of("discord.com", "discordapp.com").contains(uri.getHost()) && uri.getPath().matches("/api/webhooks/[0-9]+/[A-Za-z0-9_-]+")) : !uri.getPath().equals("/safeguarding")) return null;
            return uri;
        } catch (IllegalArgumentException invalid) { return null; }
    }
    public void send(String reference, String backend, List<String> reasons) {
        if (discord == null && (readyApp == null || token.isBlank())) return;
        // All substitutions are generated UUIDs, validated backend IDs and built-in rule codes, never user text.
        String description = "URGENT ReadyCraft safeguarding reference " + reference + " on " + backend + ": " + String.join(", ", reasons)
                + ". Review the protected server safety queue. No automatic CYP or deal link was made.";
        try { worker.execute(() -> {
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build()) {
                if (discord != null) deliver(client, discord, "", "{\"allowed_mentions\":{\"parse\":[]},\"content\":\"" + description + "\"}", "Discord");
                if (readyApp != null && !token.isBlank()) deliver(client, readyApp, token, "{\"category\":\"out_of_venue\",\"severity\":\"critical\",\"description\":\"" + description + "\"}", "ReadyApp");
            } catch (RuntimeException failure) { log.accept("Safeguarding transport failed; check the local safety queue."); }
        }); } catch (RejectedExecutionException busy) { log.accept("Safeguarding transport full; review local urgent flags immediately."); }
    }
    private void deliver(HttpClient client, URI endpoint, String token, String body, String service) {
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json");
            if (!token.isBlank()) request.header("x-mcp-token", token);
            int status = client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status < 200 || status >= 300) log.accept("Safeguarding " + service + " refused the alert (HTTP " + status + "); check the private queue.");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (Exception failure) { log.accept("Safeguarding " + service + " delivery uncertain; check the destination before manually retrying. Review the private queue."); }
    }
    @Override public void close() { worker.shutdownNow(); }
}
