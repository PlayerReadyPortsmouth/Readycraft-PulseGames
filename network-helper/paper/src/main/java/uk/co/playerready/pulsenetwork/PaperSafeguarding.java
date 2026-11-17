package uk.co.playerready.pulsenetwork;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.*;

/** Independent Paper 1.21.4 test gate. Filters before final local delivery and proxy relay. */
final class PaperSafeguarding implements Listener, CommandExecutor, AutoCloseable {
    static final String REVIEW = "pulsenetwork.safeguarding.review";
    private static final Set<String> PRIVATE = Set.of("msg", "tell", "w", "pm", "whisper", "m", "message", "dm", "reply", "r", "emsg", "etell", "ewhisper", "epm", "er", "ereply", "mail", "email");
    private static final Set<String> CREDENTIAL = Set.of("login", "l", "register", "reg", "changepassword", "password", "auth", "2fa");
    private static final Set<String> OWN = Set.of("safetyflagged", "safetyhistory", "safetycontext", "safetymute", "safetyunmute", "safetyslow", "safetyreport", "report", "history", "flagged");
    private final JavaPlugin plugin;
    private final SafeguardingConfig config;
    private final SafeguardingFilter filter;
    private final SafeguardingDelivery delivery;
    private final String backend;
    private SafeguardingJournal journal;
    private volatile boolean healthy = true;
    private record Stamp(String digest, boolean allowed) {}
    private final Map<Cancellable, Stamp> seen = Collections.synchronizedMap(new WeakHashMap<>());
    private static String digest(String text) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Objects.toString(text, "").getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private void guarded(Cancellable event, Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) { event.setCancelled(true); healthy = false; plugin.getLogger().severe("Safeguarding event failed; monitored text held without logging content."); }
    }
    private void apply(Cancellable event, Player player, String surface, String text, boolean finalPass) {
        try {
            String hash = digest(text); Stamp previous = seen.get(event);
            if (finalPass && previous != null && previous.digest().equals(hash)) { if (!previous.allowed()) event.setCancelled(true); return; }
            boolean allowed = check(player, finalPass ? surface + "-final" : surface, text);
            seen.put(event, new Stamp(hash, allowed)); if (!allowed) event.setCancelled(true);
        } catch (RuntimeException failure) { event.setCancelled(true); healthy = false; plugin.getLogger().severe("Safeguarding event failed; text held without logging content."); }
    }
    private final Map<UUID, Long> muted = new HashMap<>(), last = new HashMap<>();
    private final Set<String> friends = new HashSet<>();
    private final Set<String> approvedCommands, credentialOwners;
    private long slowMs;
    private final boolean privateEnabled, legacyMode, minorFriends;
    private final Set<UUID> minors = new HashSet<>();
    private record Report(UUID target, String reason, String token, long until) {}
    private final Map<UUID, Report> reports = new HashMap<>();
    private final Map<UUID, Long> reportTimes = new HashMap<>();
    PaperSafeguarding(JavaPlugin plugin) {
        this.plugin = plugin; var yaml = plugin.getConfig();
        backend = yaml.getString("safeguarding.backend", "").trim();
        Properties properties = new Properties(); var section = yaml.getConfigurationSection("safeguarding");
        if (section != null) for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (value instanceof List<?> list) properties.setProperty("safeguarding." + key, String.join(",", list.stream().map(String::valueOf).toList()));
            else if (!(value instanceof org.bukkit.configuration.ConfigurationSection) && value != null) properties.setProperty("safeguarding." + key, String.valueOf(value));
        }
        SafeguardingConfig parsed;
        try { parsed = SafeguardingConfig.parse(properties); }
        catch (IllegalArgumentException invalid) {
            boolean requested = "test".equals(properties.getProperty("safeguarding.environment")) && Wire.id(backend)
                    && Arrays.asList(properties.getProperty("safeguarding.test-backends", "minigamestest,survivaltest").split(",")).contains(backend);
            parsed = new SafeguardingConfig(requested, requested, requested, 90, requested ? Set.of(backend) : Set.of(), "", "", "", "");
            healthy = false; plugin.getLogger().severe("Invalid test safeguarding configuration; monitored text is held until corrected.");
        }
        config = parsed; filter = new SafeguardingFilter(config.extraWords()); delivery = new SafeguardingDelivery(config, plugin.getLogger()::warning);
        legacyMode = yaml.getBoolean("safeguarding.legacy-chat", false) || plugin.getServer().getPluginManager() != null && plugin.getServer().getPluginManager().isPluginEnabled("AdvancedChat");
        minorFriends = yaml.getBoolean("safeguarding.private.allow-under-18-friends", false);
        for (String id : yaml.getStringList("safeguarding.under-18-players")) try { minors.add(UUID.fromString(id)); } catch (IllegalArgumentException ignored) { /* Empty grants no DM access. */ }
        privateEnabled = yaml.getBoolean("safeguarding.private.enabled", false);
        for (String pair : yaml.getStringList("safeguarding.private.friend-pairs")) {
            String[] ids = pair.split(":");
            if (ids.length == 2) try { friends.add(pair(UUID.fromString(ids[0].trim()), UUID.fromString(ids[1].trim()))); } catch (IllegalArgumentException ignored) { /* Fail closed. */ }
        }
        credentialOwners = Set.copyOf(yaml.getStringList("safeguarding.credential-command-owners"));
        approvedCommands = Set.copyOf(yaml.getStringList("safeguarding.text-commands"));
        if (active() && config.logging()) try {
            journal = new SafeguardingJournal(plugin.getDataFolder().toPath().resolve("safeguarding"), Clock.systemUTC(), config.retentionDays());
            loadControls();
        } catch (IOException failure) { healthy = false; plugin.getLogger().severe("Private safeguarding storage unavailable; monitored text is held."); }
    }
    boolean active() { return config.applies(backend); }
    void register() {
        if (!active()) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (String name : OWN) { if (Set.of("report", "history", "flagged").contains(name)) continue; PluginCommand command = plugin.getCommand(name); if (command != null) command.setExecutor(this); }
    }
    private static String pair(UUID a, UUID b) { return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a; }
    private void notifyStaff(String text) {
        Runnable action = () -> { for (Player staff : plugin.getServer().getOnlinePlayers()) if (staff.hasPermission(REVIEW)) staff.sendMessage(Component.text(text)); };
        if (plugin.getServer().isPrimaryThread()) action.run(); else plugin.getServer().getScheduler().runTask(plugin, action);
    }
    private static boolean personal(SafeguardingFilter.Result verdict) { return verdict.reasons().stream().anyMatch(reason -> Set.of("email", "phone", "address", "off-platform-contact").contains(reason)); }
    /** Filters/logs submissions; credential commands call this with a withholding marker instead of their arguments. */
    synchronized boolean check(Player player, String surface, String text) { return check(player, surface, text, true, null); }
    private synchronized boolean check(Player player, String surface, String text, boolean enforce, UUID reportTarget) { return check(player, surface, text, enforce, reportTarget, false); }
    private synchronized boolean check(Player player, String surface, String text, boolean enforce, UUID reportTarget, boolean forcedDenied) {
        try { return inspect(player, surface, text, enforce, reportTarget, forcedDenied); }
        catch (RuntimeException failure) {
            healthy = false; plugin.getLogger().severe("Unexpected safeguarding failure; monitored text held. No message content logged.");
            try { notifyStaff("[Safeguarding failure] Monitored test text is held. Staff intervention is required."); } catch (RuntimeException ignored) { /* Fail closed even if notification fails. */ }
            try { player.sendMessage(Component.text("Chat safety checks are unavailable. Please speak directly to staff.")); } catch (RuntimeException ignored) { /* Delivery remains denied. */ }
            return false;
        }
    }
    private boolean inspect(Player player, String surface, String text, boolean enforce, UUID reportTarget, boolean forcedDenied) {
        if (!active()) return true;
        text = text == null ? "" : text;
        boolean oversized = text.length() > 32768;
        String bounded = text.substring(0, Math.min(text.length(), 32768));
        var verdict = filter.evaluate(bounded); long now = System.currentTimeMillis(); String reference = UUID.randomUUID().toString();
        var reasons = new ArrayList<>(verdict.reasons()); if (oversized) reasons.add("oversized"); if (reportTarget != null) reasons.add("player-report");
        var severity = verdict.severity(); if ((oversized || reportTarget != null) && severity.ordinal() < SafeguardingFilter.Severity.HIGH.ordinal()) severity = SafeguardingFilter.Severity.HIGH;
        boolean limitedSurface = surface.equals("chat") || surface.startsWith("command:") || surface.startsWith("dm:");
        Long previous = limitedSurface && enforce ? last.put(player.getUniqueId(), now) : null;
        boolean rate = enforce && previous != null && now - previous < Math.max(1000, slowMs);
        boolean mute = enforce && muted.getOrDefault(player.getUniqueId(), 0L) > now;
        boolean party = surface.equals("party") || (surface.equals("command:party") || surface.equals("command:p")) && bounded.toLowerCase(Locale.ROOT).startsWith("chat ");
        String outcome = forcedDenied ? "blocked" : !enforce ? "recorded" : !healthy || forcedDenied || oversized || rate || mute || party || config.filter() && verdict.blocked() ? "blocked" : "allowed";
        boolean operational = healthy;
        String evidence = surface.equals("private-mail") ? "[private mail arguments withheld]" : personal(verdict) ? "[withheld: personal/contact information]" : bounded;
        try { if (journal != null) journal.record(new SafeguardingJournal.Entry(reference, now, player.getUniqueId(), backend,
                reportTarget == null ? surface : "report:" + reportTarget, evidence, severity, List.copyOf(reasons), outcome)); }
        catch (IOException failure) { healthy = false; operational = false; plugin.getLogger().severe("Private safeguarding write failed; monitored text held. No message content logged."); notifyStaff("[Safeguarding storage failure] Monitored text is held on " + backend + ". Intervene and check private storage."); }
        if (severity == SafeguardingFilter.Severity.URGENT) {
            notifyStaff("[URGENT safeguarding] " + reference + " · " + backend + " · " + surface + " · " + String.join(", ", reasons)
                    + (operational ? ". Review /safetycontext " + reference : ". STORAGE FAILURE: message held; review is unavailable. Intervene now."));
            delivery.send(reference, backend, reasons);
        } else if (reportTarget != null && operational) notifyStaff("[Player safety report] " + reference + ". Review /safetycontext " + reference);
        if (forcedDenied && operational) return false;
        if (enforce && (rate || mute || party)) {
            player.sendMessage(Component.text(party ? "Party text is held until shared safeguarding controls are verified. Please use moderated public chat."
                    : "Please wait before sending another message, or ask staff about your chat restriction.")); return false;
        }
        if (!operational || enforce && (oversized || config.filter() && verdict.blocked())) {
            player.sendMessage(Component.text(severity == SafeguardingFilter.Severity.URGENT
                    ? "We held that message and let the safety team know. Please speak to a trusted adult or member of staff if you need help."
                    : "Please keep chat kind and personal/contact details private. That text wasn't sent; ask a member of staff if you need help.")); return false;
        }
        return true;
    }
    @EventHandler(priority = EventPriority.LOWEST) public void chat(AsyncChatEvent event) { guarded(event, () -> {
        if (!legacyMode) apply(event, event.getPlayer(), "chat", PlainTextComponentSerializer.plainText().serialize(event.message()), false);
     }); }
    @EventHandler(priority = EventPriority.LOWEST) public void legacyChat(AsyncPlayerChatEvent event) { guarded(event, () -> {
        if (legacyMode) apply(event, event.getPlayer(), "chat", event.getMessage(), false);
     }); }
    @EventHandler(priority = EventPriority.LOWEST) public void sign(SignChangeEvent event) { guarded(event, () -> {
        apply(event, event.getPlayer(), "sign", String.join("\n", event.getLines()), false);
     }); }
    @EventHandler(priority = EventPriority.LOWEST) public void book(PlayerEditBookEvent event) { guarded(event, () -> {
        var meta = event.getNewBookMeta(); String text = Objects.toString(meta.getTitle(), "") + "\n" + String.join("\n", meta.getPages());
        apply(event, event.getPlayer(), "book", text, false);
     }); }
    @EventHandler(priority = EventPriority.LOWEST) public void anvil(InventoryClickEvent event) { guarded(event, () -> {
        if (event.getRawSlot() != 2 || !(event.getView() instanceof AnvilView view) || !(event.getWhoClicked() instanceof Player player)) return;
        String text = view.getRenameText(); if (text != null && !text.isBlank()) apply(event, player, "anvil", text, false);
     }); }
    static String label(String raw) { String label = raw.toLowerCase(Locale.ROOT); int colon = label.lastIndexOf(':'); return colon < 0 ? label : label.substring(colon + 1); }
    @EventHandler(priority = EventPriority.LOWEST) public void command(PlayerCommandPreprocessEvent event) {
        try { inspectCommand(event); seen.put(event, new Stamp(digest(event.getMessage()), !event.isCancelled())); }
        catch (RuntimeException failure) { event.setCancelled(true); healthy = false; plugin.getLogger().severe("Safeguarding command failed; text held without logging arguments."); }
    }
    private void inspectCommand(PlayerCommandPreprocessEvent event) {
        if (!active()) return;
        String raw = event.getMessage(); int space = raw.indexOf(' ');
        String name = label(space < 0 ? raw.substring(1) : raw.substring(1, space));
        boolean mail = Set.of("mail", "email").contains(name);
        if (space < 0 && !mail) return;
        String text = space < 0 ? "" : raw.substring(space + 1); Player player = event.getPlayer();
        if (OWN.contains(name)) { check(player, "command:" + name, "[arguments withheld]", false, null); return; }
        if (CREDENTIAL.contains(name)) {
            PluginCommand command = plugin.getServer().getPluginCommand(raw.substring(1, space));
            boolean trusted = command != null && credentialOwners.contains(command.getPlugin().getName());
            boolean recorded = check(player, "command:" + name, "[credential arguments withheld]", false, null, !trusted);
            if (!trusted || !recorded) { event.setCancelled(true); player.sendMessage(Component.text("That credential command is held until staff verify its installed plugin owner. Please ask staff for help.")); }
            return;
        }
        if (PRIVATE.contains(name)) {
            boolean reply = Set.of("reply", "r", "er", "ereply").contains(name);
            String targetName = text.split("\\s+", 2)[0]; Player target = mail || reply ? null : plugin.getServer().getPlayerExact(targetName);
            boolean consent = !mail && !reply && privateEnabled && target != null && (minorFriends || !minors.contains(player.getUniqueId()) && !minors.contains(target.getUniqueId()))
                    && friends.contains(pair(player.getUniqueId(), target.getUniqueId()));
            boolean safe = check(player, mail ? "private-mail" : "dm:" + (target == null ? "unknown" : target.getUniqueId()), text, true, null, !consent);
            if (!consent || !safe) { event.setCancelled(true); if (!consent) player.sendMessage(Component.text(mail ? "Private mail is held until a consent-aware adapter is verified. Use public chat or /safetyreport." : "Private messages are off here unless staff have approved both players as friends. Use public chat or /safetyreport.")); }
            return;
        }
        boolean safe = check(player, "command:" + name, approvedCommands.contains(name) ? text : "[unapproved command arguments withheld]", true, null, !approvedCommands.contains(name));
        if (!safe || !approvedCommands.contains(name)) { event.setCancelled(true); if (!approvedCommands.contains(name)) player.sendMessage(Component.text("That text command needs staff approval on this test server.")); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void finalChat(AsyncChatEvent event) { guarded(event, () -> { if (!legacyMode) apply(event, event.getPlayer(), "chat", PlainTextComponentSerializer.plainText().serialize(event.message()), true);  }); }
    @EventHandler(priority = EventPriority.HIGHEST) public void finalLegacyChat(AsyncPlayerChatEvent event) { guarded(event, () -> { if (legacyMode) apply(event, event.getPlayer(), "chat", event.getMessage(), true);  }); }
    @EventHandler(priority = EventPriority.HIGHEST) public void finalSign(SignChangeEvent event) { guarded(event, () -> { apply(event, event.getPlayer(), "sign", String.join("\n", event.getLines()), true);  }); }
    @EventHandler(priority = EventPriority.HIGHEST) public void finalBook(PlayerEditBookEvent event) { guarded(event, () -> {
        var meta = event.getNewBookMeta(); apply(event, event.getPlayer(), "book", Objects.toString(meta.getTitle(), "") + "\n" + String.join("\n", meta.getPages()), true);
     }); }
    @EventHandler(priority = EventPriority.HIGHEST) public void finalAnvil(InventoryClickEvent event) { guarded(event, () -> {
        if (event.getRawSlot() == 2 && event.getView() instanceof AnvilView view && event.getWhoClicked() instanceof Player player && view.getRenameText() != null)
            apply(event, player, "anvil", view.getRenameText(), true);
     }); }
    @EventHandler(priority = EventPriority.HIGHEST) public void finalCommand(PlayerCommandPreprocessEvent event) {
        try {
            Stamp previous = seen.get(event);
            if (previous != null && previous.digest().equals(digest(event.getMessage()))) { if (!previous.allowed()) event.setCancelled(true); }
            else inspectCommand(event);
        } catch (RuntimeException failure) { event.setCancelled(true); healthy = false; plugin.getLogger().severe("Safeguarding final command check failed; text held."); }
    }
    @Override public synchronized boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!active()) { sender.sendMessage(Component.text("Test safeguarding is disabled.")); return true; }
        String name = command.getName();
        if (name.equals("safetyreport")) { if (sender instanceof Player player) report(player, args); return true; }
        if (!sender.hasPermission(REVIEW)) { sender.sendMessage(Component.text("This safety tool is for authorised staff.")); return true; }
        if (!healthy && Set.of("safetymute", "safetyunmute", "safetyslow").contains(name)) {
            sender.sendMessage(Component.text("Private safety controls need repair. Preserve the file and ask the safeguarding lead to repair it before restarting; restrictions remain closed.")); return true;
        }
        try {
            if (name.equals("safetymute") && args.length == 2) {
                UUID player = UUID.fromString(args[0]); long seconds = Long.parseLong(args[1]);
                if (seconds < 0 || seconds > 2_592_000) throw new IllegalArgumentException();
                muted.put(player, seconds == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + seconds * 1000); saveControls(); sender.sendMessage(Component.text("Chat restriction saved. Zero seconds means indefinite."));
            } else if (name.equals("safetyunmute") && args.length == 1) { muted.remove(UUID.fromString(args[0])); saveControls(); sender.sendMessage(Component.text("Chat restriction removed.")); }
            else if (name.equals("safetyslow") && args.length == 1) { long seconds = Long.parseLong(args[0]); if (seconds < 0 || seconds > 300) throw new IllegalArgumentException(); slowMs = seconds * 1000; saveControls(); sender.sendMessage(Component.text("Slow mode saved.")); }
            else {
                if (journal == null) { sender.sendMessage(Component.text("Private logging is disabled.")); return true; }
                List<String> lines;
                if (name.equals("safetyflagged") && args.length == 0) lines = journal.read(null, true, 20);
                else if (name.equals("safetyhistory") && args.length == 1) lines = journal.read(UUID.fromString(args[0]), false, 20);
                else if (name.equals("safetycontext") && args.length == 1) { UUID.fromString(args[0]); lines = journal.context(args[0]); }
                else throw new IllegalArgumentException();
                sender.sendMessage(Component.text("Private safety records: " + lines.size())); for (String line : lines) sender.sendMessage(Component.text(line));
                sender.sendMessage(Component.text("Record review/resolution in ReadyApp. Latest 20 flags/history; context is five adjacent messages either side."));
            }
            plugin.getLogger().info("Safety tool " + name + " used by " + (sender instanceof Player player ? player.getUniqueId() : "console"));
        } catch (IllegalArgumentException invalid) { sender.sendMessage(Component.text("Use /safetyflagged, /safetyhistory <UUID>, /safetycontext <reference>, /safetymute <UUID> <seconds, 0=indefinite>, /safetyunmute <UUID>, /safetyslow <0..300 seconds>.")); }
        catch (IOException failure) { healthy = false; sender.sendMessage(Component.text("Private safety storage failed. Text is held; contact the safeguarding lead.")); }
        return true;
    }
    private void report(Player player, String[] args) {
        if (journal == null) { player.sendMessage(Component.text("Safety reporting is unavailable here. Please speak directly to staff.")); return; }
        long now = System.currentTimeMillis(); UUID id = player.getUniqueId(); reports.values().removeIf(report -> report.until() < now);
        if (args.length == 2 && args[0].equalsIgnoreCase("confirm")) {
            Report pending = reports.get(id); if (pending == null || pending.until() < now || !pending.token().equals(args[1])) { player.sendMessage(Component.text("That confirmation has expired. Please start /safetyreport again.")); return; }
            reports.remove(id);
            if (check(player, "report", pending.reason(), false, pending.target())) player.sendMessage(Component.text("Your safety report was received. Thank you; staff will review it.")); return;
        }
        if (args.length < 2) { player.sendMessage(Component.text("/safetyreport <player name or UUID> <what happened>")); return; }
        String draft = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (!check(player, "report-draft", draft, false, null)) return;
        if (now - reportTimes.getOrDefault(id, 0L) < 60_000) { player.sendMessage(Component.text("Please wait a minute, or speak directly to staff if you need help now.")); return; }
        Player target = plugin.getServer().getPlayerExact(args[0]); UUID targetId;
        try { targetId = target == null ? UUID.fromString(args[0]) : target.getUniqueId(); } catch (IllegalArgumentException invalid) { player.sendMessage(Component.text("Use an online player's name or their UUID.")); return; }
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length)); if (reason.length() > 2000 || reason.isBlank()) { player.sendMessage(Component.text("Please use 1–2000 characters.")); return; }
        String token = UUID.randomUUID().toString(); reports.put(id, new Report(targetId, reason, token, now + 60_000)); reportTimes.put(id, now);
        player.sendMessage(Component.text("Send this report to safeguarding staff? Confirm within a minute: /safetyreport confirm " + token));
    }
    private Path controls() { return plugin.getDataFolder().toPath().resolve("safeguarding/controls.properties"); }
    private void loadControls() throws IOException {
        Path path = controls(); if (!Files.exists(path)) return; if (Files.isSymbolicLink(path)) throw new IOException("Private controls cannot be a link");
        Properties saved = new Properties();
        try {
            try (var reader = Files.newBufferedReader(path)) { saved.load(reader); }
            long storedSlow = Long.parseLong(saved.getProperty("slow-ms", "0"));
            if (storedSlow < 0 || storedSlow > 300_000) throw new IllegalArgumentException();
            var storedMutes = new HashMap<UUID, Long>();
            for (String key : saved.stringPropertyNames()) {
                if (key.equals("slow-ms")) continue;
                if (!key.startsWith("mute.")) throw new IllegalArgumentException();
                String rawId = key.substring(5); UUID player = UUID.fromString(rawId);
                if (!player.toString().equalsIgnoreCase(rawId)) throw new IllegalArgumentException();
                long until = Long.parseLong(saved.getProperty(key));
                if (until <= 0) throw new IllegalArgumentException();
                storedMutes.put(player, until);
            }
            slowMs = storedSlow; muted.putAll(storedMutes);
        } catch (IllegalArgumentException invalid) {
            // Never turn corrupt persisted restrictions into an unrestricted player. Do not log field values.
            throw new IOException("Private safeguarding controls need staff repair");
        }
    }
    private void saveControls() throws IOException {
        if (journal == null) throw new IOException("Enable private logging before using persistent safety controls");
        Properties saved = new Properties(); saved.setProperty("slow-ms", Long.toString(slowMs));
        muted.forEach((id, until) -> saved.setProperty("mute." + id, until.toString()));
        Path temp = controls().resolveSibling("controls.tmp"); if (Files.isSymbolicLink(temp)) throw new IOException("Private controls cannot be a link");
        if (!Files.exists(temp)) Files.createFile(temp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
        try (var writer = Files.newBufferedWriter(temp)) { saved.store(writer, "Test safeguarding controls"); }
        try { Files.move(temp, controls(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, controls(), StandardCopyOption.REPLACE_EXISTING); }
    }
    @Override public void close() { delivery.close(); }
}
