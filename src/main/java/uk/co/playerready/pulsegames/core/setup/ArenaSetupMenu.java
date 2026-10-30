package uk.co.playerready.pulsegames.core.setup;

import uk.co.playerready.pulsegames.core.command.Routes;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.util.Text;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Standard inventory menus work through Geyser without client-specific forms. */
final class ArenaSetupMenu implements Listener {
    private final PulseGamesPlugin plugin;
    private final ArenaSetupManager manager;
    private final NamespacedKey bookKey;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private final Set<ArenaSetupManager.Session> saving = new HashSet<>();
    private record Prompt(ArenaSetupManager.Session session, Consumer<String> accept, long expires) {}
    private record Button(Material icon, String title, List<String> lore, Runnable action) {}
    static final class Holder implements InventoryHolder {
        final UUID owner;
        final ArenaSetupManager.Session session;
        final Map<Integer, Runnable> actions = new HashMap<>();
        Inventory inventory;
        Holder(Player player, ArenaSetupManager.Session session) {
            this.owner = player.getUniqueId(); this.session = session;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    ArenaSetupMenu(PulseGamesPlugin plugin, ArenaSetupManager manager) {
        this.plugin = plugin; this.manager = manager;
        bookKey = new NamespacedKey(plugin, "arena_setup_book");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    boolean saving(ArenaSetupManager.Session session) { return saving.contains(session); }
    void setSaving(ArenaSetupManager.Session session, boolean value) {
        if (value) saving.add(session); else saving.remove(session);
    }
    private boolean allowed(Player player, ArenaSetupManager.Session session) {
        return player.isOnline() && player.hasPermission("pulsegames.admin")
                && manager.session(player) == session && !saving(session);
    }
    private boolean isBook(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(bookKey, PersistentDataType.BYTE);
    }
    void giveBook(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (isBook(item)) return;
        ItemStack book = new ItemStack(Material.BOOK);
        book.editMeta(meta -> {
            meta.displayName(Component.text("Arena Setup Book"));
            meta.lore(List.of(Component.text("Right-click to configure your arena"),
                    Component.text("Requirements, points, regions and settings")));
            meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (!player.getInventory().addItem(book).isEmpty())
            player.sendMessage(Text.msg("<yellow>Inventory full. Make space and use /admin games setup book, or open /admin games setup gui."));
    }
    @EventHandler
    public void onBook(PlayerInteractEvent event) {
        if (!isBook(event.getItem())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        ArenaSetupManager.Session session = manager.session(player);
        if (session != null && allowed(player, session)) open(player, session);
        else player.sendMessage(Text.msg("<yellow>Begin with /admin games setup start <game> <arenaId>."));
    }
    private void palette(Player player, ArenaSetupManager.Session session) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(button(Material.BLAZE_ROD, "Use wand selection as palette region", () -> {
            manager.handle(player,new String[]{"region","palette"});palette(player,session);
        }, "Select opposite corners around the playable map first"));
        buttons.add(button(Material.SPYGLASS, "Scan palette region", () -> { player.closeInventory();manager.handle(player,new String[]{"palette","scan"}); }, "Define region palette with wand/corner commands first", "Scan in batches; existing palette survives scan failure"));
        buttons.add(button(Material.PAPER, "Use global defaults", () -> { manager.handle(player,new String[]{"palette","global"});palette(player,session); }));
        var config = new org.bukkit.configuration.file.YamlConfiguration();session.settings.forEach(config::set);
        try {
            var selected = uk.co.playerready.pulsegames.games.prophunt.PropPalette.read(plugin.getConfig(),config);
            for (var option : uk.co.playerready.pulsegames.games.prophunt.PropPalette.read(plugin.getConfig())) {
                boolean enabled = selected.contains(option);
                buttons.add(button(option.material(), (enabled ? "Enabled: " : "Disabled: ") + option.name(), () -> {
                    manager.handle(player,new String[]{"palette",enabled?"remove":"add",option.material().name()});palette(player,session);
                }, "Click to toggle this supported disguise"));
            }
        } catch (IllegalArgumentException invalid) { player.sendMessage(Text.msg("<red>"+invalid.getMessage())); }
        buttons.add(button(Material.ARROW,"Back",()->open(player,session)));
        show(player,session,"Map disguise palette",buttons,0);
    }

    private Button button(Material material, String title, Runnable action, String... lore) {
        return new Button(material, title, List.of(lore), action);
    }
    private void show(Player player, ArenaSetupManager.Session session, String title, List<Button> buttons, int page) {
        if (!allowed(player, session)) return;
        Holder holder = new Holder(player, session);
        holder.inventory = Bukkit.createInventory(holder, 54, Component.text(title));
        int lastPage = Math.max(0, (buttons.size() - 1) / 45);
        int current = Math.max(0, Math.min(page, lastPage));
        for (int i = current * 45; i < Math.min(buttons.size(), current * 45 + 45); i++)
            put(holder, i % 45, buttons.get(i));
        if (current > 0) put(holder, 45, button(Material.ARROW, "Previous page", () -> show(player, session, title, buttons, current - 1)));
        put(holder, 49, button(Material.BOOK, "Setup overview", () -> open(player, session)));
        put(holder, 50, button(Material.BARRIER, "Close (keep setup)", player::closeInventory));
        if (current < lastPage) put(holder, 53, button(Material.ARROW, "Next page", () -> show(player, session, title, buttons, current + 1)));
        player.openInventory(holder.inventory);
    }
    private void put(Holder holder, int slot, Button button) {
        ItemStack item = new ItemStack(button.icon());
        item.editMeta(meta -> {
            meta.displayName(Component.text(button.title()));
            meta.lore(button.lore().stream().map(Component::text).toList());
        });
        holder.inventory.setItem(slot, item);
        holder.actions.put(slot, button.action());
    }
    void open(Player player, ArenaSetupManager.Session session) {
        prompts.remove(player.getUniqueId());
        List<Button> buttons = new ArrayList<>();
        for (ArenaSetupManager.CheckEntry entry : manager.checklist(session)) {
            buttons.add(button(entry.done() ? Material.LIME_STAINED_GLASS_PANE
                            : entry.required() ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    (entry.done() ? "Done: " : entry.required() ? "Required: " : "Optional: ") + entry.label(),
                    () -> requirement(player, session, entry), "Click to configure", entry.command()));
        }
        buttons.add(button(Material.NAME_TAG, "Name: " + session.displayName,
                () -> ask(player, session, "Arena display name", value -> session.displayName = value)));
        buttons.add(button(Material.PLAYER_HEAD, "Manage spawns (" + session.spawns.size() + ")", () -> spawns(player, session)));
        buttons.add(button(Material.MAP, "Manage regions (" + session.regions.size() + ")", () -> regions(player, session)));
        buttons.add(button(Material.REPEATER, "Game modes: " + (session.modes.isEmpty() ? "All" : session.modes), () -> modes(player, session)));
        buttons.add(button(Material.COMPARATOR, "Player limits", () -> limits(player, session)));
        buttons.add(button(Material.ARMOR_STAND, "Queue bots: " + session.settings.getOrDefault("bots-enabled", false), () -> {
            session.settings.put("bots-enabled", !Boolean.TRUE.equals(session.settings.get("bots-enabled"))); open(player, session);
        }, "Toggle arena bot permission", "Only supported games can fill queues"));
        buttons.add(button(Material.IRON_SWORD, "Bot difficulty: " + session.settings.getOrDefault("bot-difficulty", "adaptive"), () -> {
            String current = String.valueOf(session.settings.getOrDefault("bot-difficulty", "adaptive"));
            session.settings.put("bot-difficulty", switch (current) { case "easy" -> "normal"; case "normal" -> "hard"; case "hard" -> "adaptive"; default -> "easy"; });
            open(player, session);
        }, "Click to cycle Adaptive / Easy / Normal / Hard", "Normal damage and reach at every level"));
        if (session.game.id().equals("prophunt")) buttons.add(button(Material.BOOKSHELF, "Map disguise palette", () -> palette(player, session), "Scan supported map blocks and review available disguises"));
        buttons.add(button(Material.COMPASS, "Bot navigation (optional)", () -> navigation(player, session),
                "Patrol points, hiding spots and named safe routes", "Recovery points are walk targets, never teleports"));
        buttons.add(button(Material.BLAZE_ROD, "Get selection wand", () -> { manager.giveWand(player); player.closeInventory(); }));
        buttons.add(button(Material.SPYGLASS, "Validate arena", () -> { player.closeInventory(); manager.validate(player, session); }, "Check coordinates, objectives, teams and spawn clearance", "Read-only; does not load unexplored chunks"));
        long missing = manager.checklist(session).stream().filter(e -> e.required() && !e.done()).count();
        buttons.add(button(Material.EMERALD_BLOCK, "Review and save (" + missing + " missing)", () -> review(player, session)));
        buttons.add(button(Material.BARRIER, "Discard setup", () -> confirm(player, session, "Discard unsaved setup?",
                () -> manager.handle(player, new String[]{"cancel"}))));
        show(player, session, "Setup: " + session.arenaId, buttons, 0);
    }
    private void run(Player player, ArenaSetupManager.Session session, String... args) {
        manager.handle(player, args); open(player, session);
    }
    private void requirement(Player player, ArenaSetupManager.Session session, ArenaSetupManager.CheckEntry entry) {
        String[] args = entry.command().substring(Routes.SETUP_PREFIX.length()).trim().split(" +");
        if (args[0].equals("set")) {
            String key = args[1];
            if (key.equals("diamond-generators")) { run(player, session, "addloc", key); return; }
            prompt(player, session, key + (key.equals("item-pool") ? " (comma-separated material names)" : " (number)"), value -> {
                if (key.equals("item-pool")) {
                    List<String> materials = Arrays.stream(value.split(",")).map(String::trim).map(v -> v.toUpperCase(Locale.ROOT)).toList();
                    if (materials.isEmpty() || materials.stream().anyMatch(v -> Material.matchMaterial(v) == null))
                        throw new IllegalArgumentException("Use valid Minecraft material names.");
                    session.settings.put(key, materials);
                } else if (key.equals("throw-cooldown-ticks")) {
                    int ticks = positive(value);
                    if (ticks > 40) throw new IllegalArgumentException("Use 1–40 ticks.");
                    session.settings.put(key, ticks);
                } else {
                    double number = Double.parseDouble(value);
                    if (!Double.isFinite(number)) throw new IllegalArgumentException("Use a finite number.");
                    session.settings.put(key, number);
                }
            });
            return;
        }
        if (args[0].equals("region")) {
            for (SetupRequirement req : session.game.setup()) {
                if (req.kind() == SetupRequirement.Kind.REGION_PREFIX && args[1].equals(req.key() + "1")) {
                    args[1] = nextRegionName(session.regions.keySet(), req.key()); break;
                }
            }
        }
        if (entry.done() && !args[0].equals("addspawn") && !args[0].equals("region"))
            confirm(player, session, "Replace with your current position?", () -> run(player, session, args));
        else if (args[0].equals("region") && session.regions.containsKey(args[1]))
            confirm(player, session, "Replace region with current selection?", () -> run(player, session, args));
        else run(player, session, args);
    }
    static String nextRegionName(Set<String> names, String prefix) {
        int index = 1;
        while (names.contains(prefix + index)) index++;
        return prefix + index;
    }
    private void spawns(Player player, ArenaSetupManager.Session session) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(button(Material.EMERALD, "Add spawn here", () -> run(player, session, "addspawn")));
        for (int i = 0; i < session.spawns.size(); i++) {
            int index = i;
            buttons.add(button(Material.RED_BED, "Remove spawn " + (i + 1),
                    () -> confirm(player, session, "Remove spawn " + (index + 1) + "?", () -> {
                        session.spawns.remove(index); open(player, session);
                    }), session.spawns.get(i), "Removing shifts later team spawn numbers"));
        }
        show(player, session, "Spawn points", buttons, 0);
    }
    private void regions(Player player, ArenaSetupManager.Session session) {
        List<Button> buttons = new ArrayList<>();
        session.regions.forEach((name, region) -> buttons.add(button(Material.MAP, "Remove region: " + name,
                () -> confirm(player, session, "Remove region " + name + "?", () -> {
                    session.regions.remove(name); open(player, session);
                }), region.toString())));
        show(player, session, "Saved regions", buttons, 0);
    }
    private void navigation(Player player, ArenaSetupManager.Session session) {
        List<Button> buttons = new ArrayList<>();
        for (String key : List.of("bot-patrol-points", "bot-hide-points", "bot-recovery-points")) {
            String label = switch (key) {
                case "bot-patrol-points" -> "Patrol points";
                case "bot-hide-points" -> "Hiding spots";
                default -> "Recovery walk targets";
            };
            buttons.add(button(Material.COMPASS, label + " (" + navigationPoints(session.settings, key).size() + ")",
                    () -> navigationPoints(player, session, key), "/admin games setup addloc " + key,
                    key.equals("bot-recovery-points") ? "Reachable safe ground; never teleports bots" : "Optional guidance for supported bot controllers"));
        }
        session.settings.keySet().stream().filter(key -> key.startsWith("bot-route-")).sorted().forEach(key ->
                buttons.add(button(Material.RAIL, "Route: " + key.substring("bot-route-".length())
                                + " (" + navigationPoints(session.settings, key).size() + ")",
                        () -> navigationPoints(player, session, key), "Waypoints are followed in saved order",
                        "/admin games setup addloc " + key)));
        buttons.add(button(Material.EMERALD, "Create named safe route", () -> ask(player, session,
                "Route name (1–32 letters, numbers, underscores or hyphens)", value -> {
                    String key = navigationRouteKey(value);
                    if (session.settings.containsKey(key)) throw new IllegalArgumentException("That route already exists.");
                    session.settings.put(key, new ArrayList<String>());
                }), "Then select the route and add points in order", "Use walkable ground; avoid hazards and jumps"));
        show(player, session, "Bot navigation", buttons, 0);
    }
    private void navigationPoints(Player player, ArenaSetupManager.Session session, String key) {
        List<Button> buttons = new ArrayList<>();
        List<String> snapshot = navigationPoints(session.settings, key);
        buttons.add(button(Material.EMERALD, "Add your current position (" + snapshot.size() + "/128)", () -> {
            manager.handle(player, new String[]{"addloc", key}); navigationPoints(player, session, key);
        }, "/admin games setup addloc " + key, "Or: /admin games setup addloc " + key + " x y z [yaw pitch]"));
        for (int i = 0; i < snapshot.size(); i++) {
            int index = i;
            buttons.add(button(Material.REDSTONE, "Remove point " + (index + 1), () -> confirm(player, session,
                    "Remove point " + (index + 1) + "?", () -> {
                        if (removeNavigationPoint(session.settings, key, snapshot, index))
                            navigationPoints(player, session, key);
                        else {
                            player.sendMessage(Component.text("These points changed. Review the refreshed list first."));
                            navigationPoints(player, session, key);
                        }
                    }), snapshot.get(index), "/admin games setup removeloc " + key + " " + (index + 1),
                    "Removing shifts later point numbers"));
        }
        buttons.add(button(Material.BARRIER, key.startsWith("bot-route-") ? "Delete this route" : "Clear all points",
                () -> confirm(player, session, "Clear " + snapshot.size() + " points?", () -> {
                    if (navigationPoints(session.settings, key).equals(snapshot)) session.settings.remove(key);
                    else player.sendMessage(Component.text("These points changed. Review the refreshed list first."));
                    navigation(player, session);
                }), "/admin games setup clearloc " + key, "Requires confirmation; affects unsaved setup only"));
        buttons.add(button(Material.ARROW, "Back to bot navigation", () -> navigation(player, session)));
        show(player, session, key, buttons, 0);
    }
    static String navigationRouteKey(String value) {
        String name = value.trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}"))
            throw new IllegalArgumentException("Use 1–32 letters, numbers, underscores or hyphens.");
        return "bot-route-" + name;
    }
    static List<String> navigationPoints(Map<String, Object> settings, String key) {
        Object value = settings.get(key);
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }
    static boolean removeNavigationPoint(Map<String, Object> settings, String key, List<String> expected, int index) {
        List<String> current = navigationPoints(settings, key);
        if (!current.equals(expected) || index < 0 || index >= current.size()) return false;
        List<String> updated = new ArrayList<>(current);
        updated.remove(index);
        settings.put(key, updated);
        return true;
    }
    private boolean supportsSetupMode(ArenaSetupManager.Session session, uk.co.playerready.pulsegames.core.game.GameMode mode) {
        return session.modes.isEmpty() || session.modes.contains(mode.id())
                || !Boolean.parseBoolean(String.valueOf(session.settings.getOrDefault("combat-profiles-explicit", false)))
                && session.modes.contains(uk.co.playerready.pulsegames.core.game.GameMode.mapModeId(mode.id()));
    }
    private void modes(Player player, ArenaSetupManager.Session session) {
        List<Button> buttons = new ArrayList<>();
        session.game.modes().forEach(mode -> {
            boolean enabled = supportsSetupMode(session, mode);
            buttons.add(button(enabled ? Material.LIME_DYE : Material.GRAY_DYE, mode.displayName() + (enabled ? " (on)" : " (off)"), () -> {
                List<String> selected = new ArrayList<>(session.game.modes().stream()
                        .filter(m -> supportsSetupMode(session, m)).map(m -> m.id()).toList());
                if (enabled && selected.size() == 1) {
                    player.sendMessage(Text.msg("<yellow>Keep at least one mode enabled.")); return;
                }
                if (enabled) selected.remove(mode.id()); else selected.add(mode.id());
                session.settings.put("combat-profiles-explicit", true);
                session.modes = List.copyOf(selected); modes(player, session);
            }, mode.minPlayers() + "–" + mode.maxPlayers() + " players"));
        });
        show(player, session, "Game modes", buttons, 0);
    }
    private void limits(Player player, ArenaSetupManager.Session session) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(button(Material.PLAYER_HEAD, "Default minimum: " + (session.minPlayers == null ? 2 : session.minPlayers),
                () -> ask(player, session, "Minimum players (positive whole number)", value -> session.minPlayers = positive(value))));
        buttons.add(button(Material.PLAYER_HEAD, "Default maximum: " + (session.maxPlayers == null ? Math.max(2, session.spawns.size()) : session.maxPlayers),
                () -> ask(player, session, "Maximum players (positive whole number)", value -> session.maxPlayers = positive(value))));
        session.game.modes().stream().filter(mode -> supportsSetupMode(session, mode)).forEach(mode -> {
            String prefix = "modes." + mode.id() + ".";
            for (String key : List.of("min-players", "max-players")) {
                buttons.add(button(Material.COMPARATOR, mode.displayName() + " " + key + ": " + session.settings.getOrDefault(prefix + key, "default"),
                        () -> ask(player, session, mode.displayName() + " " + key + " (number or default)", value -> {
                            if (value.equalsIgnoreCase("default")) session.settings.remove(prefix + key);
                            else session.settings.put(prefix + key, positive(value));
                        }), "Overrides only this mode", "Mode minimum: " + mode.minPlayers()));
            }
            if (mode.isTeams()) buttons.add(button(Material.WHITE_BANNER, mode.displayName() + " teams: " + session.settings.getOrDefault(prefix + "team-count", "spawn count"),
                    () -> ask(player, session, "Team count (2–32, or default)", value -> {
                        if (value.equalsIgnoreCase("default")) session.settings.remove(prefix + "team-count");
                        else { int count = positive(value); if (count < 2 || count > 32) throw new IllegalArgumentException("Use 2–32 teams.");
                            session.settings.put(prefix + "team-count", count); }
                    }), mode.teamSize() + " players per team", "One spawn per team; Bedwars also needs one bed", "Duels uses two opposing teams"));
        });
        show(player, session, "Map capacity by mode", buttons, 0);
    }
    static int positive(String value) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1) throw new IllegalArgumentException("Use a positive whole number.");
        return parsed;
    }
    private void review(Player player, ArenaSetupManager.Session session) {
        List<Button> buttons = new ArrayList<>();
        for (ArenaSetupManager.CheckEntry entry : manager.checklist(session))
            buttons.add(button(entry.done() ? Material.LIME_DYE : Material.RED_DYE, entry.label(), () -> open(player, session),
                    entry.done() ? "Complete" : entry.required() ? "Required: missing" : "Optional: not set"));
        boolean replacing = new File(plugin.getDataFolder(), "maps/" + session.arenaId).exists()
                || new File(plugin.getDataFolder(), "arenas/" + session.game.id() + "-" + session.arenaId + ".yml").exists();
        buttons.add(button(Material.EMERALD_BLOCK, replacing ? "Confirm REPLACE existing arena" : "Confirm save arena", () -> {
            player.closeInventory(); manager.save(player, session);
        }, "World: " + session.world.getName(), "Captures the entire world as the template",
                replacing ? "Existing saved template will be overwritten" : "Creates the arena and its template"));
        show(player, session, "Review: " + session.arenaId, buttons, 0);
    }
    private void confirm(Player player, ArenaSetupManager.Session session, String title, Runnable action) {
        show(player, session, title, List.of(button(Material.LIME_DYE, "Confirm", action),
                button(Material.RED_DYE, "Back", () -> open(player, session))), 0);
    }
    /** Short answers use an anvil (or a Bedrock input form); the setter throws IllegalArgumentException to ask again. */
    private void ask(Player player, ArenaSetupManager.Session session, String label, Consumer<String> accept) {
        player.closeInventory();
        uk.co.playerready.pulsegames.core.ui.AnvilPrompt.ask(plugin, player, label, value -> {
            try { accept.accept(value); return null; }
            catch (NumberFormatException ex) { return "Use a whole number."; }
            catch (IllegalArgumentException ex) { return ex.getMessage() == null ? "That does not work." : ex.getMessage(); }
        }, value -> { if (allowed(player, session)) open(player, session); }, () -> { if (allowed(player, session)) open(player, session); });
    }
    private void prompt(Player player, ArenaSetupManager.Session session, String label, Consumer<String> accept) {
        player.closeInventory();
        prompts.put(player.getUniqueId(), new Prompt(session, accept, System.currentTimeMillis() + 120_000));
        player.sendMessage(Component.text(label + ": type your value in chat, or cancel. This answer is private (2 minute limit)."));
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Prompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String value = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!allowed(player, prompt.session())) return;
            if (System.currentTimeMillis() > prompt.expires()) {
                player.sendMessage(Text.msg("<yellow>Input expired. Choose the option again.")); open(player, prompt.session()); return;
            }
            if (!value.equalsIgnoreCase("cancel")) {
                try {
                    if (value.isBlank() || value.length() > 128) throw new IllegalArgumentException("Enter 1–128 characters.");
                    prompt.accept().accept(value);
                } catch (IllegalArgumentException ex) {
                    player.sendMessage(Component.text("Invalid value: " + ex.getMessage()));
                    prompt(player, prompt.session(), "Try again", prompt.accept()); return;
                }
            }
            open(player, prompt.session());
        });
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) { prompts.remove(event.getPlayer().getUniqueId()); }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        Runnable action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        // Never open/close inventories inside InventoryClickEvent; reject stale views next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory() == holder.inventory && allowed(player, holder.session)) action.run();
        });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
