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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.LocUtil;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.core.world.WorldService;

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
 * /admin games setup start <game> <id>, mark everything with the wand and
 * commands, then /admin games setup save - the world is captured as a template
 * and the arena goes live without touching a config file.
 */
public final class ArenaSetupManager implements Listener {

    public static final class Session {
        public final GameType game;
        public final String arenaId;
        public World world;
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
    private final ArenaSetupMenu menu;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public ArenaSetupManager(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.menu = new ArenaSetupMenu(plugin, this);
    }

    public Session session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    // ---- subcommand dispatch (called from /admin games setup ...) ----------------------

    public void handle(Player player, String[] args) {
        if (!player.hasPermission("pulsegames.admin")) return;
        if (args.length == 0) {
            help(player);
            return;
        }
        if (session(player) != null && menu.saving(session(player))) {
            player.sendMessage(Text.msg("<yellow>Wait for the current world capture to finish."));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("start")) {
            start(player, args);
            return;
        }
        Session session = session(player);
        if (session == null) {
            player.sendMessage(Text.msg("<red>No setup session. Begin with <yellow>/admin games setup start <game> <arenaId>"));
            return;
        }
        // Every point below is stored as bare coordinates and re-read against the
        // cloned template world, so capturing one outside the map world is meaningless.
        if (List.of("lobby", "spectator", "addspawn", "setloc", "addloc", "pos1", "pos2", "region").contains(sub)
                && !player.getWorld().equals(session.world)) {
            player.sendMessage(Text.msg("<red>You're in <yellow>" + player.getWorld().getName()
                    + "</yellow>; this setup is for <yellow>" + session.world.getName() + "</yellow>."));
            return;
        }
        try {
        switch (sub) {
            case "palette" -> palette(player, session, args);
            case "validate" -> validate(player, session);
            case "wand" -> giveWand(player);
            case "book" -> menu.giveBook(player);
            case "pos1", "pos2" -> {
                Location point = SetupCoordinates.point(session.world, player.getLocation(), args, 1, true);
                if (sub.equals("pos1")) session.pos1 = point; else session.pos2 = point;
                player.sendMessage(Text.msg("<green>" + sub + ": " + point.getBlockX() + ", " + point.getBlockY() + ", " + point.getBlockZ()));
            }
            case "lobby" -> {
                session.lobby = LocUtil.serialize(SetupCoordinates.point(session.world, player.getLocation(), args, 1, false));
                player.sendMessage(Text.msg("<green>Waiting lobby point set."));
            }
            case "spectator" -> {
                session.spectator = LocUtil.serialize(SetupCoordinates.point(session.world, player.getLocation(), args, 1, false));
                player.sendMessage(Text.msg("<green>Spectator point set."));
            }
            case "addspawn" -> {
                session.spawns.add(LocUtil.serialize(SetupCoordinates.point(session.world, player.getLocation(), args, 1, false)));
                player.sendMessage(Text.msg("<green>Spawn #" + session.spawns.size() + " added."));
            }
            case "clearspawns" -> {
                session.spawns.clear();
                player.sendMessage(Text.msg("Spawns cleared."));
            }
            case "removespawn" -> {
                if (args.length != 2) throw new IllegalArgumentException("Usage: /admin games setup removespawn <1-based index>");
                int index = Integer.parseInt(args[1]) - 1;
                if (index < 0 || index >= session.spawns.size()) throw new IllegalArgumentException("Spawn index is out of range.");
                session.spawns.remove(index);
                player.sendMessage(Text.msg("<green>Spawn removed; later spawn numbers shift down."));
            }
            case "removeregion" -> {
                if (args.length != 2) throw new IllegalArgumentException("Usage: /admin games setup removeregion <name>");
                session.regions.remove(SetupCoordinates.key(args[1]));
                player.sendMessage(Text.msg("<green>Region removed."));
            }
            case "region" -> {
                if (args.length != 2 && args.length != 8) throw new IllegalArgumentException("Usage: /admin games setup region <name> [x1 y1 z1 x2 y2 z2]");
                String name = SetupCoordinates.key(args[1]);
                Location first = session.pos1, second = session.pos2;
                if (args.length == 8) {
                    first = SetupCoordinates.point(session.world, player.getLocation(), new String[]{args[2], args[3], args[4]}, 0, true);
                    second = SetupCoordinates.point(session.world, player.getLocation(), new String[]{args[5], args[6], args[7]}, 0, true);
                }
                if (first == null || second == null) throw new IllegalArgumentException("Set pos1 and pos2 with commands or the wand, or supply both corners directly.");
                Cuboid region = new Cuboid(first.getBlockX(), first.getBlockY(), first.getBlockZ(), second.getBlockX(), second.getBlockY(), second.getBlockZ());
                session.regions.put(name, region);
                player.sendMessage(Text.msg("<green>Region <yellow>" + name + "</yellow> saved."));
            }
            case "setloc" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("<red>Usage: /admin games setup setloc <key>"));
                    return;
                }
                session.settings.put(SetupCoordinates.key(args[1]), LocUtil.serialize(SetupCoordinates.point(session.world, player.getLocation(), args, 2, false)));
                player.sendMessage(Text.msg("<green>Location <yellow>" + args[1] + "</yellow> set."));
            }
            case "addloc" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("<red>Usage: /admin games setup addloc <list-key>"));
                    return;
                }
                String key = SetupCoordinates.key(args[1]);
                String point = LocUtil.serialize(SetupCoordinates.point(session.world, player.getLocation(), args, 2, false));
                if (session.settings.containsKey(key) && !(session.settings.get(key) instanceof List<?>))
                    throw new IllegalArgumentException("That setting is not a location list.");
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) session.settings.computeIfAbsent(key, k -> new ArrayList<String>());
                if (key.startsWith("bot-") && list.size() >= 128) throw new IllegalArgumentException("Bot point lists are limited to 128 entries.");
                list.add(point);
                player.sendMessage(Text.msg("<green>Added location #" + list.size() + " to <yellow>" + key + "</yellow>."));
            }
            case "removeloc", "clearloc" -> {
                if (args.length != (sub.equals("removeloc") ? 3 : 2)) throw new IllegalArgumentException("Usage: /admin games setup " + sub + " <key>" + (sub.equals("removeloc") ? " <1-based index>" : ""));
                String key = SetupCoordinates.key(args[1]);
                Object existing = session.settings.get(key);
                if (!(existing instanceof List<?> list)) throw new IllegalArgumentException("That setting is not a location list.");
                if (sub.equals("clearloc")) session.settings.remove(key);
                else {
                    int index = Integer.parseInt(args[2]) - 1;
                    if (index < 0 || index >= list.size()) throw new IllegalArgumentException("Index must be between 1 and " + list.size());
                    List<Object> updated = new ArrayList<>(list); updated.remove(index); session.settings.put(key, updated);
                }
                player.sendMessage(Text.msg("<green>Location list updated."));
            }
            case "set" -> {
                if (args.length < 3) {
                    player.sendMessage(Text.msg("<red>Usage: /admin games setup set <key> <value>"));
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
            case "gui" -> menu.open(player, session);
            case "save" -> save(player, session);
            case "cancel" -> {
                sessions.remove(player.getUniqueId());
                player.sendMessage(Text.msg("Setup cancelled."));
            }
            default -> help(player);
        }
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Text.msg("<red>" + ex.getMessage()));
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
            player.sendMessage(Text.msg("<red>Usage: /admin games setup start <game> <arenaId> <gray>- run this standing in your map world"));
            return;
        }
        GameType game = plugin.registry().get(args[1]);
        if (game == null) {
            player.sendMessage(Text.msg("<red>Unknown game <yellow>" + args[1] + "</yellow>. See /admin games types"));
            return;
        }
        String arenaId = args[2].toLowerCase(Locale.ROOT);
        // The id becomes a map-template folder that /admin games setup save deletes
        // recursively, so anything that could escape plugins/PulseGames/maps is refused.
        if (!WorldService.isValidTemplateId(arenaId)) {
            player.sendMessage(Text.msg("<red>Invalid arena id <yellow>" + args[2]
                    + "</yellow>. Use a-z, 0-9, <white>_</white> and <white>-</white> (max 32 characters)."));
            return;
        }
        Session session = new Session(game, arenaId, player.getWorld());
        sessions.put(player.getUniqueId(), session);
        giveWand(player);
        menu.giveBook(player);
        player.sendMessage(Text.msg("<green>Setting up <yellow>" + game.displayName() + "</yellow> arena <yellow>"
                + session.arenaId + "</yellow> in world <yellow>" + player.getWorld().getName() + "</yellow>."));
        sendChecklist(player, session);
    }

    private void help(Player player) {
        player.sendMessage(Text.msg("Arena setup commands:"));
        for (String line : List.of(
                "start <game> <arenaId> <dark_gray>- begin in your map world",
                "wand / book <dark_gray>- recover setup tools",
                "lobby / spectator / addspawn [x y z [yaw pitch]] <dark_gray>- set points",
                "pos1 / pos2 [x y z] <dark_gray>- select region corners",
                "region <name> [x1 y1 z1 x2 y2 z2] <dark_gray>- save a region",
                "setloc / addloc <key> [x y z [yaw pitch]] <dark_gray>- location settings",
                "removeloc <key> <index> / clearloc <key> <dark_gray>- edit location lists",
                "removespawn <index> / removeregion <name> <dark_gray>- remove saved entries",
                "set <key> <value> <dark_gray>- e.g. set void-y 40",
                "check / gui <dark_gray>- requirement checklist",
                "palette list|scan|add|remove|global <dark_gray>- Prop Hunt disguises",
                "save / cancel")) {
            player.sendMessage(Text.mm(" <gray>/admin games setup " + line));
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
        if (session == null || !player.hasPermission("pulsegames.admin") || menu.saving(session) || event.getClickedBlock() == null) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return;
        event.setCancelled(true);
        Location loc = event.getClickedBlock().getLocation();
        // Region coordinates are stored world-less and re-read against the cloned
        // template world, so corners from another world silently produce a region
        // spanning the distance between the two maps.
        if (!session.world.equals(loc.getWorld())) {
            player.sendMessage(Text.msg("<red>That block is in <yellow>" + loc.getWorld().getName()
                    + "</yellow>, but this setup is for <yellow>" + session.world.getName()
                    + "</yellow>. Select corners inside the map world."));
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            session.pos1 = loc;
            player.sendMessage(Text.msg("Pos1: <yellow>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            session.pos2 = loc;
            player.sendMessage(Text.msg("Pos2: <yellow>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
        }
    }

    // ---- checklist -------------------------------------------------------------------

    record CheckEntry(String label, String command, boolean done, boolean required) {}

    List<CheckEntry> checklist(Session session) {
        List<CheckEntry> entries = new ArrayList<>();
        entries.add(new CheckEntry("Waiting lobby", "/admin games setup lobby", session.lobby != null, true));
        entries.add(new CheckEntry("Spectator point", "/admin games setup spectator", session.spectator != null, true));
        for (SetupRequirement req : session.game.setup()) {
            switch (req.kind()) {
                case SPAWNS -> entries.add(new CheckEntry(
                        req.min() + "+ spawns - " + req.description() + " (" + session.spawns.size() + " set)",
                        "/admin games setup addspawn", session.spawns.size() >= req.min(), req.required()));
                case REGION -> entries.add(new CheckEntry(
                        "Region '" + req.key() + "' - " + req.description(),
                        "/admin games setup region " + req.key(),
                        session.regions.containsKey(req.key()), req.required()));
                case REGION_PREFIX -> {
                    long count = session.regions.keySet().stream().filter(k -> k.startsWith(req.key())).count();
                    entries.add(new CheckEntry(
                            req.min() + "+ regions '" + req.key() + "1..N' - " + req.description() + " (" + count + " set)",
                            "/admin games setup region " + req.key() + "1", count >= req.min(), req.required()));
                }
                case LOCATION_SETTING -> entries.add(new CheckEntry(
                        "Location '" + req.key() + "' - " + req.description(),
                        "/admin games setup setloc " + req.key(),
                        session.settings.containsKey(req.key()), req.required()));
                case VALUE_SETTING -> entries.add(new CheckEntry(
                        "Setting '" + req.key() + "' - " + req.description(),
                        "/admin games setup set " + req.key() + " ",
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
        player.sendMessage(Text.mm(" <gray>Then: <click:suggest_command:'/admin games setup save'><yellow>/admin games setup save</yellow></click>"));
    }

    public ArenaValidation.Report validation(Session session) { return ArenaValidation.check(session); }
    public void validate(Player player, Session session) {
        var report=validation(session);
        player.sendMessage(net.kyori.adventure.text.Component.text(report.valid()?"Arena validation: no blocking errors. Review warnings before publishing.":"Arena validation: fix these errors before publishing."));
        report.issues().stream().limit(40).forEach(i->player.sendMessage(net.kyori.adventure.text.Component.text((i.error()?"ERROR: ":"CHECK: ")+i.message())));
        if(report.issues().size()>40)player.sendMessage(net.kyori.adventure.text.Component.text("More issues remain; fix these and validate again."));
    }

    /** Explicit commands share the setup UI's working selection; saving publishes it. */
    void palette(Player player, Session session, String[] args) {
        if (!session.game.id().equals("prophunt")) throw new IllegalArgumentException("Palettes are for Prop Hunt arenas.");
        if (paletteScanning.contains(session)) { player.sendMessage(Text.msg("<yellow>Palette scan is still running.")); return; }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("scan")) { scanPalette(player, session); return; }
        if (action.equals("global")) {
            session.settings.remove("prop-palette");session.settings.put("prop-palette-source", "global");
        } else if (action.equals("add") || action.equals("remove")) {
            if (args.length != 3) throw new IllegalArgumentException("Usage: /admin games setup palette add|remove <material>");
            var config = new YamlConfiguration();session.settings.forEach(config::set);
            var options = uk.co.playerready.pulsegames.games.prophunt.PropPalette.read(plugin.getConfig());
            Material material = Material.matchMaterial(args[2]);
            if (material == null || options.stream().noneMatch(o -> o.material() == material))
                throw new IllegalArgumentException("That material has no supported configured disguise.");
            var selected = new java.util.LinkedHashSet<>(uk.co.playerready.pulsegames.games.prophunt.PropPalette.read(plugin.getConfig(), config)
                    .stream().map(o -> o.material().name()).toList());
            if (action.equals("add")) selected.add(material.name()); else selected.remove(material.name());
            if (selected.isEmpty()) throw new IllegalArgumentException("Keep at least one disguise.");
            session.settings.put("prop-palette", List.copyOf(selected));session.settings.put("prop-palette-source", "reviewed");
        } else if (!action.equals("list")) throw new IllegalArgumentException("Usage: /admin games setup palette list|scan|add|remove|global");
        var config = new YamlConfiguration();session.settings.forEach(config::set);
        player.sendMessage(Text.msg("<yellow>Disguises: <white>" + String.join(", ",
                uk.co.playerready.pulsegames.games.prophunt.PropPalette.read(plugin.getConfig(), config).stream().map(o -> o.material().name()).toList())));
    }

    private final java.util.Set<Session> paletteScanning = new java.util.HashSet<>();
    private void scanPalette(Player player, Session session) {
        Cuboid region = session.regions.get("palette");
        if (region == null) { player.sendMessage(Text.msg("<yellow>First select the playable map: /admin games setup region palette <x1> <y1> <z1> <x2> <y2> <z2>")); return; }
        var supported = uk.co.playerready.pulsegames.games.prophunt.PropPalette.read(plugin.getConfig());
        var scan = new uk.co.playerready.pulsegames.games.prophunt.PropPaletteScan(session.world,region,supported);
        if (!paletteScanning.add(session)) return;
        player.sendMessage(Text.msg("<yellow>Scanning map blocks for supported disguises..."));
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                if (session(player) != session || !player.isOnline() || org.bukkit.Bukkit.getWorld(session.world.getUID()) != session.world) { finish(); return; }
                try {
                    if (!region.equals(session.regions.get("palette"))) throw new IllegalArgumentException("Palette region changed; scan again.");
                    if (scan.step()) {
                        List<String> materials = scan.result();
                        session.settings.put("prop-palette", materials);session.settings.put("prop-palette-source", "map-scan");
                        finish(); player.sendMessage(Text.msg("<green>Map palette: " + String.join(", ", materials) + ". Review in the setup book before saving."));
                    }
                } catch (RuntimeException error) { finish();player.sendMessage(Text.msg("<red>Palette scan stopped: " + error.getMessage())); }
            }
            private void finish() { paletteScanning.remove(session);cancel(); }
        }.runTaskTimer(plugin, 1, 1);
    }

    // ---- save --------------------------------------------------------------------------

    void save(Player player, Session session) {
        if (!player.hasPermission("pulsegames.admin") || session(player) != session || menu.saving(session)) return;
        if (paletteScanning.contains(session)) { player.sendMessage(Text.msg("<yellow>Wait for the palette scan to finish.")); return; }
        if (session.game.id().equals("prophunt")) {
            if (!session.settings.containsKey("prop-palette") && session.regions.containsKey("palette")
                    && !"global".equals(session.settings.get("prop-palette-source"))) {
                scanPalette(player, session);return; // Require review and a second save, never publish unseen scan results.
            }
            try { var config = new YamlConfiguration();session.settings.forEach(config::set);
                uk.co.playerready.pulsegames.games.prophunt.PropPalette.read(plugin.getConfig(), config);
            } catch (IllegalArgumentException invalid) { player.sendMessage(Text.msg("<red>" + invalid.getMessage()));return; }
        }
        if (!validation(session).valid()) { validate(player,session); return; }
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
        int minimum = session.minPlayers != null ? session.minPlayers : 2;
        int maximum = session.maxPlayers != null ? session.maxPlayers : Math.max(session.spawns.size(), 2);
        if (minimum < 1 || maximum < minimum) {
            player.sendMessage(Text.msg("<red>Player limits must be positive, with maximum at least minimum."));
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
        menu.setSaving(session, true);
        plugin.worlds().saveAsTemplate(session.world, session.arenaId, () -> {
            menu.setSaving(session, false);
            sessions.remove(player.getUniqueId(), session);
            if (plugin.maps() != null) plugin.maps().captured(session.game.id(), session.arenaId);
            plugin.arenas().load();
            player.sendMessage(Text.msg("<green><b>Arena " + session.arenaId + " is live!</b> <gray>Try: <yellow>/play "
                    + session.game.id()));
        }, error -> {
            menu.setSaving(session, false);
            player.sendMessage(Text.msg("<red>World capture failed: " + error));
        });
    }

    /** New build worlds default to void; existing worlds are never regenerated. */
    public void loadBuildWorld(Player player, String name) {
        loadBuildWorld(player, name, true);
    }

    public void loadBuildWorld(Player player, String name, boolean empty) {
        String instancePrefix = plugin.getConfig().getString("instances.world-prefix", "pulse_");
        if (!name.matches("[a-z0-9_-]{1,64}") || name.startsWith(instancePrefix) || name.startsWith("pulsebuild_")) {
            player.sendMessage(Text.msg("<red>Use a permanent build-world name containing a-z, 0-9, _ or -. Match-world names are reserved."));
            return;
        }
        World world = Bukkit.getWorld(name);
        boolean created = false;
        if (world == null) {
            WorldCreator creator = new WorldCreator(name);
            File folder = WorldService.worldDirectory(creator);
            boolean existing = folder.exists() || new File(Bukkit.getWorldContainer(), name).exists();
            // Persist the generator choice so later loads cannot grow normal terrain.
            boolean useVoid = (!existing && empty) || new File(folder, ".pulsegames-void").isFile();
            if (useVoid) creator.generator(new WorldService.VoidGenerator()).generateStructures(false);
            world = creator.createWorld();
            created = !existing;
            if (world != null && created && useVoid) {
                initialiseEmptyBuildWorld(world);
                try {
                    java.nio.file.Files.writeString(world.getWorldFolder().toPath().resolve(".pulsegames-void"), "1\n");
                } catch (java.io.IOException ex) {
                    plugin.getLogger().severe("Cannot persist void generator for " + name + ": " + ex.getMessage());
                    player.sendMessage(Text.msg("<red>World created, but its generator marker could not be saved. Ask an administrator before reopening it."));
                    return;
                }
                world.save(true);
            }
        }
        if (world == null) {
            player.sendMessage(Text.msg("<red>Could not load world " + name));
            return;
        }
        plugin.buildWorlds().manage(world);
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.teleport(world.getSpawnLocation());
        player.sendMessage(Text.msg((created ? "Created " : "Loaded existing ") + "world <yellow>" + name
                + "</yellow> (creative)." + (created ? "" : " Existing terrain was preserved.")));
    }

    @org.bukkit.event.EventHandler
    public void reopened(org.bukkit.event.world.WorldLoadEvent event) {
        for (Session session : sessions.values()) {
            if (!session.world.getName().equals(event.getWorld().getName())) continue;
            session.world = event.getWorld();
            if (session.pos1 != null) session.pos1.setWorld(event.getWorld());
            if (session.pos2 != null) session.pos2.setWorld(event.getWorld());
        }
    }

    static void initialiseEmptyBuildWorld(World world) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) world.getBlockAt(x, 80, z).setType(Material.SMOOTH_QUARTZ, false);
        }
        world.setSpawnLocation(0, 81, 0);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(6000);
    }
}
