package uk.co.playerready.pulsegames.core.setup;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.LocUtil;
import uk.co.playerready.pulsegames.core.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fully in-game arena creation: build your map in any world, run
 * /pulse setup start <game> <id>, mark everything with the wand and
 * commands, then /pulse setup save - the world is captured as a template
 * and the arena goes live without touching a config file.
 */
public final class ArenaSetupManager implements Listener {

    public static final class Session {
        public final GameType game;
        public final String arenaId;
        public final World world;
        public String displayName;
        public Integer minPlayers;
        public Integer maxPlayers;
        public List<String> modes = List.of();
        public String lobby;
        public String spectator;
        public final List<String> spawns = new ArrayList<>();
        public final Map<String, Cuboid> regions = new LinkedHashMap<>();
        public final Map<String, Object> settings = new LinkedHashMap<>();
        public Location pos1;
        public Location pos2;

        Session(GameType game, String arenaId, World world) {
            this.game = game;
            this.arenaId = arenaId;
            this.world = world;
            this.displayName = arenaId;
        }
    }

    private final PulseGamesPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public ArenaSetupManager(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public Session session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    // ---- subcommand dispatch (called from /pulse setup ...) ----------------------

    public void handle(Player player, String[] args) {
        if (args.length == 0) {
            help(player);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("start")) {
            start(player, args);
            return;
        }
        Session session = session(player);
        if (session == null) {
            player.sendMessage(Text.msg("<red>No setup session. Begin with <yellow>/pulse setup start <game> <arenaId>"));
            return;
        }
        switch (sub) {
            case "wand" -> giveWand(player);
            case "lobby" -> {
                session.lobby = LocUtil.serialize(player.getLocation());
                player.sendMessage(Text.msg("<green>Waiting lobby set here."));
            }
            case "spectator" -> {
                session.spectator = LocUtil.serialize(player.getLocation());
                player.sendMessage(Text.msg("<green>Spectator point set here."));
            }
            case "addspawn" -> {
                session.spawns.add(LocUtil.serialize(player.getLocation()));
                player.sendMessage(Text.msg("<green>Spawn #" + session.spawns.size() + " added."));
            }
            case "clearspawns" -> {
                session.spawns.clear();
                player.sendMessage(Text.msg("Spawns cleared."));
            }
            case "region" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("<red>Usage: /pulse setup region <name> <gray>(select with the wand first)"));
                    return;
                }
                if (session.pos1 == null || session.pos2 == null) {
                    player.sendMessage(Text.msg("<red>Select two corners with the wand first (<yellow>/pulse setup wand</yellow>)."));
                    return;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                session.regions.put(name, new Cuboid(
                        session.pos1.getBlockX(), session.pos1.getBlockY(), session.pos1.getBlockZ(),
                        session.pos2.getBlockX(), session.pos2.getBlockY(), session.pos2.getBlockZ()));
                player.sendMessage(Text.msg("<green>Region <yellow>" + name + "</yellow> saved."));
            }
            case "setloc" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("<red>Usage: /pulse setup setloc <key>"));
                    return;
                }
                session.settings.put(args[1].toLowerCase(Locale.ROOT), LocUtil.serialize(player.getLocation()));
                player.sendMessage(Text.msg("<green>Location <yellow>" + args[1] + "</yellow> set here."));
            }
            case "addloc" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("<red>Usage: /pulse setup addloc <list-key>"));
                    return;
                }
                String key = args[1].toLowerCase(Locale.ROOT);
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) session.settings.computeIfAbsent(key, k -> new ArrayList<String>());
                list.add(LocUtil.serialize(player.getLocation()));
                player.sendMessage(Text.msg("<green>Added location #" + list.size() + " to <yellow>" + key + "</yellow>."));
            }
            case "set" -> {
                if (args.length < 3) {
                    player.sendMessage(Text.msg("<red>Usage: /pulse setup set <key> <value>"));
                    return;
                }
                String key = args[1].toLowerCase(Locale.ROOT);
                String value = String.join(" ", List.of(args).subList(2, args.length));
                switch (key) {
                    case "display-name" -> session.displayName = value;
                    case "min-players" -> session.minPlayers = Integer.parseInt(value);
                    case "max-players" -> session.maxPlayers = Integer.parseInt(value);
                    case "modes" -> session.modes = List.of(value.split("\\s*,\\s*"));
                    default -> session.settings.put(key, parseValue(value));
                }
                player.sendMessage(Text.msg("<green>Set <yellow>" + key + "</yellow> = <white>" + value));
            }
            case "check" -> sendChecklist(player, session);
            case "gui" -> openChecklistGui(player, session);
            case "save" -> save(player, session);
            case "cancel" -> {
                sessions.remove(player.getUniqueId());
                player.sendMessage(Text.msg("Setup cancelled."));
            }
            default -> help(player);
        }
    }

    private Object parseValue(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }

    private void start(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Text.msg("<red>Usage: /pulse setup start <game> <arenaId> <gray>- run this standing in your map world"));
            return;
        }
        GameType game = plugin.registry().get(args[1]);
        if (game == null) {
            player.sendMessage(Text.msg("<red>Unknown game <yellow>" + args[1] + "</yellow>. See /pulse games"));
            return;
        }
        Session session = new Session(game, args[2].toLowerCase(Locale.ROOT), player.getWorld());
        sessions.put(player.getUniqueId(), session);
        giveWand(player);
        player.sendMessage(Text.msg("<green>Setting up <yellow>" + game.displayName() + "</yellow> arena <yellow>"
                + session.arenaId + "</yellow> in world <yellow>" + player.getWorld().getName() + "</yellow>."));
        sendChecklist(player, session);
    }

    private void help(Player player) {
        player.sendMessage(Text.msg("Arena setup commands:"));
        for (String line : List.of(
                "start <game> <arenaId> <dark_gray>- begin in your map world",
                "wand <dark_gray>- region selection wand",
                "lobby / spectator / addspawn <dark_gray>- set points where you stand",
                "region <name> <dark_gray>- save wand selection as region",
                "setloc <key> / addloc <key> <dark_gray>- location settings",
                "set <key> <value> <dark_gray>- e.g. set void-y 40",
                "check / gui <dark_gray>- requirement checklist",
                "save / cancel")) {
            player.sendMessage(Text.mm(" <gray>/pulse setup " + line));
        }
    }

    // ---- wand ----------------------------------------------------------------------

    public void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        wand.editMeta(meta -> meta.displayName(Text.mm("<gold><b>Arena Wand</b> <gray>(L=pos1, R=pos2)")));
        player.getInventory().addItem(wand);
    }

    @EventHandler
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Session session = session(player);
        if (session == null || event.getClickedBlock() == null) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return;
        event.setCancelled(true);
        Location loc = event.getClickedBlock().getLocation();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            session.pos1 = loc;
            player.sendMessage(Text.msg("Pos1: <yellow>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            session.pos2 = loc;
            player.sendMessage(Text.msg("Pos2: <yellow>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
        }
    }

    // ---- checklist -------------------------------------------------------------------

    private record CheckEntry(String label, String command, boolean done, boolean required) {}

    private List<CheckEntry> checklist(Session session) {
        List<CheckEntry> entries = new ArrayList<>();
        entries.add(new CheckEntry("Waiting lobby", "/pulse setup lobby", session.lobby != null, true));
        entries.add(new CheckEntry("Spectator point", "/pulse setup spectator", session.spectator != null, true));
        for (SetupRequirement req : session.game.setup()) {
            switch (req.kind()) {
                case SPAWNS -> entries.add(new CheckEntry(
                        req.min() + "+ spawns - " + req.description() + " (" + session.spawns.size() + " set)",
                        "/pulse setup addspawn", session.spawns.size() >= req.min(), req.required()));
                case REGION -> entries.add(new CheckEntry(
                        "Region '" + req.key() + "' - " + req.description(),
                        "/pulse setup region " + req.key(),
                        session.regions.containsKey(req.key()), req.required()));
                case REGION_PREFIX -> {
                    long count = session.regions.keySet().stream().filter(k -> k.startsWith(req.key())).count();
                    entries.add(new CheckEntry(
                            req.min() + "+ regions '" + req.key() + "1..N' - " + req.description() + " (" + count + " set)",
                            "/pulse setup region " + req.key() + "1", count >= req.min(), req.required()));
                }
                case LOCATION_SETTING -> entries.add(new CheckEntry(
                        "Location '" + req.key() + "' - " + req.description(),
                        "/pulse setup setloc " + req.key(),
                        session.settings.containsKey(req.key()), req.required()));
                case VALUE_SETTING -> entries.add(new CheckEntry(
                        "Setting '" + req.key() + "' - " + req.description(),
                        "/pulse setup set " + req.key() + " ",
                        session.settings.containsKey(req.key()), req.required()));
            }
        }
        return entries;
    }

    private void sendChecklist(Player player, Session session) {
        player.sendMessage(Text.msg("Checklist for <yellow>" + session.game.displayName() + "</yellow> / <yellow>"
                + session.arenaId + "</yellow> <gray>(click an item to run its command)"));
        for (CheckEntry entry : checklist(session)) {
            String icon = entry.done() ? "<green>✔" : (entry.required() ? "<red>✘" : "<gray>○");
            player.sendMessage(Text.mm(" " + icon + " <click:suggest_command:'" + entry.command() + "'><gray>"
                    + entry.label() + "</gray></click>"));
        }
        player.sendMessage(Text.mm(" <gray>Then: <click:suggest_command:'/pulse setup save'><yellow>/pulse setup save</yellow></click>"));
    }

    private static final class ChecklistHolder implements InventoryHolder {
        Session session;
        List<CheckEntry> entries;
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private void openChecklistGui(Player player, Session session) {
        ChecklistHolder holder = new ChecklistHolder();
        holder.session = session;
        holder.entries = checklist(session);
        int rows = Math.min(6, holder.entries.size() / 9 + 2);
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
                Text.mm("<dark_gray>Setup: " + session.game.displayName() + " / " + session.arenaId));
        holder.inventory = inv;
        for (int i = 0; i < holder.entries.size() && i < rows * 9 - 1; i++) {
            CheckEntry entry = holder.entries.get(i);
            ItemStack item = new ItemStack(entry.done() ? Material.LIME_STAINED_GLASS_PANE
                    : entry.required() ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
            item.editMeta(meta -> {
                meta.displayName(Text.mm((entry.done() ? "<green>" : "<red>") + entry.label()));
                meta.lore(List.of(Text.mm("<gray>Click to get: <yellow>" + entry.command())));
            });
            inv.setItem(i, item);
        }
        ItemStack save = new ItemStack(Material.EMERALD_BLOCK);
        save.editMeta(meta -> {
            meta.displayName(Text.mm("<green><b>SAVE ARENA"));
            meta.lore(List.of(Text.mm("<gray>Captures this world as the map template")));
        });
        inv.setItem(rows * 9 - 1, save);
        player.openInventory(inv);
    }

    @EventHandler
    public void onChecklistClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChecklistHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getSlot();
        if (event.getClickedInventory() != event.getInventory()) return;
        if (slot == event.getInventory().getSize() - 1) {
            player.closeInventory();
            save(player, holder.session);
        } else if (slot >= 0 && slot < holder.entries.size()) {
            player.closeInventory();
            CheckEntry entry = holder.entries.get(slot);
            player.sendMessage(Text.msg("Run: <click:suggest_command:'" + entry.command() + "'><yellow>"
                    + entry.command() + "</yellow></click> <gray>(click to prefill)"));
        }
    }

    // ---- save --------------------------------------------------------------------------

    private void save(Player player, Session session) {
        List<CheckEntry> missing = checklist(session).stream()
                .filter(e -> e.required() && !e.done()).toList();
        if (!missing.isEmpty()) {
            player.sendMessage(Text.msg("<red>Missing required setup:"));
            missing.forEach(e -> player.sendMessage(Text.mm(" <red>✘ <gray>" + e.label())));
            return;
        }
        if (!player.getWorld().equals(session.world)) {
            player.sendMessage(Text.msg("<red>You must be in the map world (<yellow>"
                    + session.world.getName() + "</yellow>) to save."));
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("id", session.arenaId);
        yml.set("game", session.game.id());
        yml.set("display-name", session.displayName);
        yml.set("world-template", session.arenaId);
        if (!session.modes.isEmpty()) yml.set("modes", session.modes);
        yml.set("min-players", session.minPlayers != null ? session.minPlayers : 2);
        yml.set("max-players", session.maxPlayers != null ? session.maxPlayers
                : Math.max(session.spawns.size(), 2));
        yml.set("lobby", session.lobby);
        yml.set("spectator", session.spectator);
        yml.set("spawns", session.spawns);
        session.regions.forEach((name, region) -> {
            yml.set("regions." + name + ".min", region.minX() + "," + region.minY() + "," + region.minZ());
            yml.set("regions." + name + ".max", region.maxX() + "," + region.maxY() + "," + region.maxZ());
        });
        session.settings.forEach((key, value) -> yml.set("settings." + key, value));

        File file = new File(new File(plugin.getDataFolder(), "arenas"),
                session.game.id() + "-" + session.arenaId + ".yml");
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (Exception ex) {
            player.sendMessage(Text.msg("<red>Could not write arena file: " + ex.getMessage()));
            return;
        }
        player.sendMessage(Text.msg("Arena file saved. Capturing world template <yellow>"
                + session.arenaId + "</yellow>..."));
        plugin.worlds().saveAsTemplate(session.world, session.arenaId, () -> {
            sessions.remove(player.getUniqueId());
            plugin.arenas().load();
            player.sendMessage(Text.msg("<green><b>Arena " + session.arenaId + " is live!</b> <gray>Try: <yellow>/play "
                    + session.game.id()));
        }, error -> player.sendMessage(Text.msg("<red>World capture failed: " + error)));
    }

    /** Loads (or creates) a build world so map makers can work on this server. */
    public void loadBuildWorld(Player player, String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).createWorld();
        }
        if (world == null) {
            player.sendMessage(Text.msg("<red>Could not load world " + name));
            return;
        }
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.teleport(world.getSpawnLocation());
        player.sendMessage(Text.msg("Teleported to world <yellow>" + name + "</yellow> (creative)."));
    }
}
