package uk.co.playerready.pulsegames.core.admin;

import uk.co.playerready.pulsegames.core.command.Routes;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.economy.Cosmetic;
import uk.co.playerready.pulsegames.core.economy.CosmeticsService;
import uk.co.playerready.pulsegames.core.events.EventSchedule;
import uk.co.playerready.pulsegames.core.events.EventText;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.progression.NameStyleGrants;
import uk.co.playerready.pulsegames.core.progression.ProgressionAdminCommand;
import uk.co.playerready.pulsegames.core.ui.AnvilPrompt;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.util.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static uk.co.playerready.pulsegames.core.admin.AdminScreen.Entry;

/**
 * {@code /admin games adminmenu}: one place for the things an admin does by command. Every action calls an existing
 * service or command (nothing here changes game rules) and logs {@code PGADMIN <actor> <action> <target>}.
 */
public final class AdminMenu {
    private final PulseGamesPlugin plugin;
    private final AdminUi ui;
    private final AdminAudit audit;
    private final ArenaFiles files;
    private ProgressionAdminCommand progression;

    public AdminMenu(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.ui = new AdminUi(plugin);
        this.audit = new AdminAudit(plugin.getLogger()::info);
        this.files = new ArenaFiles(Path.of(plugin.getDataFolder().getPath(), "arenas"));
    }

    private ProgressionAdminCommand progression() {
        if (progression == null) progression = new ProgressionAdminCommand(plugin);
        return progression;
    }

    private void say(Player p, String miniMessage) { p.sendMessage(Text.msg(miniMessage)); }

    // ---- top ------------------------------------------------------------------------------

    public void open(Player p) {
        List<AdminSections> shown = AdminSections.visible(p.hasPermission("pulsegames.admin"),
                plugin.levels() != null, plugin.events() != null && plugin.events().enabled());
        if (shown.isEmpty()) { say(p, "<red>No permission."); return; }
        List<Entry> entries = new ArrayList<>();
        for (AdminSections s : shown) {
            switch (s) {
                case GAMES -> entries.add(Entry.button(Material.GRASS_BLOCK, Theme.NAME + s.label(), this::games, "Maps on and off, bots per map, start or end a round"));
                case MATCHES -> entries.add(Entry.button(Material.CLOCK, Theme.NAME + s.label(), this::matches, plugin.instances().all().size() + " running or waiting"));
                case PLAYERS -> entries.add(Entry.button(Material.PLAYER_HEAD, Theme.NAME + s.label(), this::players, "Level, XP and name styles"));
                case EVENTS -> entries.add(Entry.button(Material.GOLDEN_APPLE, Theme.NAME + s.label(), this::events, plugin.events().statusLine()));
                case BOTS -> entries.add(Entry.button(Material.ARMOR_STAND, Theme.NAME + s.label(), this::bots, "Difficulty: " + difficulty()));
                case SERVER -> entries.add(Entry.button(Material.COMMAND_BLOCK, Theme.NAME + s.label(), this::server, "Reload, map board, lobby placements"));
            }
        }
        ui.show(p, "Admin", "Pick a section.", entries, null);
    }

    // ---- games and arenas -----------------------------------------------------------------

    private void games(Player p) {
        List<Entry> entries = new ArrayList<>();
        plugin.registry().all().stream().sorted(Comparator.comparing(GameType::id)).forEach(t -> {
            List<ArenaFiles.Entry> maps = files.list(t.id());
            long live = maps.stream().filter(ArenaFiles.Entry::enabled).count();
            entries.add(Entry.button(t.icon(), Theme.NAME + t.displayName(), q -> arenas(q, t),
                    live + " maps on, " + (maps.size() - live) + " off"));
        });
        ui.show(p, "Games", "Pick a game.", entries, this::open);
    }

    private void arenas(Player p, GameType type) {
        List<Entry> entries = new ArrayList<>();
        for (ArenaFiles.Entry a : files.list(type.id())) {
            entries.add(Entry.button(a.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, (a.enabled() ? Theme.GOOD : Theme.TEXT) + a.displayName(),
                    q -> arena(q, type, a), a.enabled() ? "On" : "Off", "Bots " + (a.botsEnabled() ? "on" : "off")));
        }
        ui.show(p, type.displayName() + " maps", entries.isEmpty() ? "No maps for this game." : "Pick a map.", entries, this::games);
    }

    private void arena(Player p, GameType type, ArenaFiles.Entry a) {
        String target = type.id() + "/" + a.id();
        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.button(a.enabled() ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                (a.enabled() ? Theme.BAD + "Turn this map off" : Theme.GOOD + "Turn this map on"), q -> ui.confirm(q,
                        a.enabled() ? "Turn map off?" : "Turn map on?",
                        List.of(Theme.TEXT + a.displayName(), a.enabled() ? "Players will stop getting this map." : "Players can get this map again."),
                        a.enabled(), r -> toggle(r, type, a), r -> arena(r, type, a)),
                "Moves the map file " + (a.enabled() ? "to" : "out of") + " arenas/disabled and reloads maps"));
        entries.add(Entry.button(Material.ARMOR_STAND, Theme.NAME + "Bots on this map: " + (a.botsEnabled() ? "on" : "off"), q -> {
            try {
                files.setBots(a, !a.botsEnabled());
                plugin.arenas().load();
                audit.log(q.getName(), a.botsEnabled() ? "bots-off" : "bots-on", target);
            } catch (IOException ex) { say(q, "<red>Could not save the map file: " + ex.getMessage()); }
            reopenArena(q, type, a.id());
        }, "Click to switch (queue bots also need bots.enabled in config)"));
        for (GameInstance g : plugin.instances().all()) {
            if (!g.type().id().equals(type.id()) || !g.arena().id().equalsIgnoreCase(a.id())) continue;
            entries.addAll(instanceEntries(g, q -> reopenArena(q, type, a.id())));
        }
        ui.show(p, a.displayName(), a.enabled() ? "Map is on." : "Map is off.", entries, q -> arenas(q, type));
    }

    private void reopenArena(Player p, GameType type, String id) {
        files.list(type.id()).stream().filter(x -> x.id().equals(id)).findFirst().ifPresentOrElse(x -> arena(p, type, x), () -> arenas(p, type));
    }

    private void toggle(Player p, GameType type, ArenaFiles.Entry a) {
        try {
            files.toggle(a);
            plugin.arenas().load();
            audit.log(p.getName(), a.enabled() ? "arena-disable" : "arena-enable", type.id() + "/" + a.id());
            say(p, a.displayName() + " is now " + (a.enabled() ? "<red>off" : "<green>on") + "<gray>.");
        } catch (IOException ex) {
            say(p, "<red>Could not move the map file: " + ex.getMessage());
        }
        arenas(p, type);
    }

    /** Start / end tiles for one running instance; {@code again} redraws the screen the admin came from. */
    private List<Entry> instanceEntries(GameInstance g, Consumer<Player> again) {
        List<Entry> out = new ArrayList<>();
        String label = g.mode().id() + " " + g.id();
        if (g.state() == GameState.WAITING || g.state() == GameState.COUNTDOWN) {
            out.add(Entry.button(Material.EMERALD, Theme.GOOD + "Start now: " + label, q -> {
                g.start();
                audit.log(q.getName(), "match-start", g.id());
                again.accept(q);
            }, g.players().size() + " players waiting"));
        }
        if (g.state() != GameState.RESETTING) {
            out.add(Entry.button(Material.TNT, Theme.BAD + "End match: " + label, q -> ui.confirm(q, "End this match?",
                    List.of(Theme.TEXT + label, "It ends now and players go back to the lobby."), true, r -> {
                        g.forceEnd();
                        audit.log(r.getName(), "match-end", g.id());
                        again.accept(r);
                    }, again), g.state().name().toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    // ---- matches --------------------------------------------------------------------------

    private void matches(Player p) {
        List<Entry> entries = new ArrayList<>();
        for (GameInstance g : plugin.instances().all()) {
            entries.add(Entry.button(g.type().icon(), Theme.NAME + g.type().displayName() + " " + g.mode().id(), q -> match(q, g),
                    g.arena().displayName(), g.state().name().toLowerCase(Locale.ROOT) + ", " + g.players().size() + " players"));
        }
        ui.show(p, "Matches", entries.isEmpty() ? "Nothing is running." : "Pick a match.", entries, this::open);
    }

    private void match(Player p, GameInstance g) {
        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.info(Material.PAPER, Theme.TEXT + "Players", g.players().isEmpty() ? "nobody" : String.join(", ", g.players().stream().map(Player::getName).toList())));
        if (g.state() == GameState.RUNNING) {
            entries.add(Entry.button(Material.ENDER_EYE, Theme.GOOD + "Spectate", q -> {
                q.closeInventory();
                if (g.watch(q)) audit.log(q.getName(), "match-spectate", g.id());
                else say(q, "<red>You cannot watch that one right now.");
            }, "Join as a spectator"));
        }
        entries.addAll(instanceEntries(g, this::matches));
        ui.show(p, g.type().displayName() + " " + g.id(), g.arena().displayName(), entries, this::matches);
    }

    // ---- players --------------------------------------------------------------------------

    private void players(Player p) {
        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.button(Material.NAME_TAG, Theme.NAME + "Type a name", q -> AnvilPrompt.ask(plugin, q, "Player name",
                AdminInput::nameError, name -> {
                    Player t = Bukkit.getPlayerExact(name);
                    if (t == null) { say(q, "<red>" + name + " must be online."); players(q); } else player(q, t.getName());
                }, () -> players(q)), "For someone you cannot see in the list"));
        for (Player t : Bukkit.getOnlinePlayers()) {
            if (t.hasMetadata("NPC") || plugin.instances().byPlayer(t) != null && plugin.instances().byPlayer(t).isBot(t)) continue;
            entries.add(Entry.button(Material.PLAYER_HEAD, Theme.NAME + t.getName(), q -> player(q, t.getName())));
        }
        ui.show(p, "Players", "Pick a player.", entries, this::open);
    }

    private void player(Player p, String name) {
        if (Bukkit.getPlayerExact(name) == null) { say(p, "<red>" + name + " is not online."); players(p); return; }
        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.button(Material.BOOK, Theme.NAME + "View progression", q -> { q.closeInventory(); run(q, "progression", name); }, "Level, XP and name styles, in chat"));
        entries.add(Entry.button(Material.EXPERIENCE_BOTTLE, Theme.NAME + "Set level", q -> AnvilPrompt.ask(plugin, q, "Level 1 to 100",
                AdminInput::levelError, v -> { run(q, "level", name, "set", v); audit.log(q.getName(), "level-set", name + ":" + v); player(q, name); },
                () -> player(q, name)), "Type a number"));
        entries.add(Entry.button(Material.EXPERIENCE_BOTTLE, Theme.NAME + "Add XP", q -> AnvilPrompt.ask(plugin, q, "XP to add",
                AdminInput::xpError, v -> { run(q, "xp", name, "add", v); audit.log(q.getName(), "xp-add", name + ":" + v); player(q, name); },
                () -> player(q, name)), "Type a number"));
        entries.add(Entry.button(Material.TNT, Theme.BAD + "Reset progression", q -> ui.confirm(q, "Reset " + name + "?",
                List.of(Theme.TEXT + name, "Level 1, 0 XP, level rewards locked again."), true, r -> {
                    run(r, "level", name, "reset");
                    audit.log(r.getName(), "level-reset", name);
                    player(r, name);
                }, r -> player(r, name)), "Back to level 1"));
        entries.add(Entry.button(Material.NAME_TAG, Theme.NAME + "Name styles", q -> styles(q, name), "Grant or take away the 16 styles"));
        ui.show(p, name, "Progression", entries, this::players);
    }

    private void run(Player admin, String... args) { progression().handle(admin, args); }

    private void styles(Player p, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) { say(p, "<red>" + name + " is not online."); players(p); return; }
        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.button(Material.LIME_CONCRETE, Theme.GOOD + "Grant all", q -> { run(q, "namestyle", name, "grant", "all");
            audit.log(q.getName(), "namestyle-grant", name + ":all"); later(q, name); }, "All 16"));
        entries.add(Entry.button(Material.RED_CONCRETE, Theme.BAD + "Take all away", q -> ui.confirm(q, "Take all styles?",
                List.of(Theme.TEXT + name, "Every name style permission is removed."), true, r -> { run(r, "namestyle", name, "revoke", "all");
                    audit.log(r.getName(), "namestyle-revoke", name + ":all"); later(r, name); }, r -> styles(r, name)), "All 16"));
        for (Cosmetic c : CosmeticsService.levelRewards()) {
            String id = c.nameStyleId();
            if (id == null) continue;
            boolean held = target.hasPermission(NameStyleGrants.node(id));
            entries.add(Entry.button(held ? Material.LIME_DYE : Material.GRAY_DYE, (held ? Theme.GOOD : Theme.TEXT) + id,
                    q -> { run(q, "namestyle", name, held ? "revoke" : "grant", id);
                        audit.log(q.getName(), held ? "namestyle-revoke" : "namestyle-grant", name + ":" + id); later(q, name); },
                    held ? "Held. Click to take away" : "Not held. Click to grant"));
        }
        ui.show(p, name + " name styles", "Tap a style to flip it.", entries, q -> player(q, name));
    }

    /** LuckPerms answers a moment later, so redraw the grid after a short wait. */
    private void later(Player p, String name) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) styles(p, name); }, 10L);
    }

    // ---- events ---------------------------------------------------------------------------

    private void events(Player p) {
        var events = plugin.events();
        Instant now = Instant.now();
        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.info(Material.PAPER, Theme.TEXT + "Right now", events.statusLine()));
        EventSchedule.Occurrence next = null;
        for (var o : events.upcoming(5)) {
            boolean passed = !o.start().isAfter(events.lastOpened());
            if (!passed && next == null) next = o;
            entries.add(Entry.info(passed ? Material.GRAY_DYE : Material.CLOCK, (passed ? Theme.TEXT : Theme.NAME) + EventText.when(now, o.start(), events.zone()),
                    events.describe(o.pick()), passed ? "Opened or skipped" : "Scheduled"));
        }
        if (next != null && !events.active()) {
            var pick = next.pick();
            GameType type = plugin.registry().get(pick.game());
            String mode = pick.mode() != null ? pick.mode() : type != null ? type.defaultMode().id() : null;
            if (mode != null) entries.add(Entry.button(Material.EMERALD, Theme.GOOD + "Start the next one now", q -> {
                events.admin(q, new String[]{"start", pick.game(), mode, pick.format().name().toLowerCase(Locale.ROOT)});
                audit.log(q.getName(), "event-start", pick.game() + "/" + mode);
                events(q);
            }, events.describe(pick), "Opens signup in about a minute"));
            entries.add(Entry.button(Material.BARRIER, Theme.BAD + "Skip the next one", q -> {
                Instant skipped = events.skipNext();
                audit.log(q.getName(), "event-skip", skipped == null ? "none" : skipped.toString());
                events(q);
            }, "It will not open on its own"));
        }
        ui.show(p, "Events", "Scheduled tournaments.", entries, this::open);
    }

    // ---- bots -----------------------------------------------------------------------------

    private String difficulty() { return plugin.getConfig().getString("bots.difficulty", "adaptive").toLowerCase(Locale.ROOT); }

    private void bots(Player p) {
        List<Entry> entries = new ArrayList<>();
        for (String level : List.of("adaptive", "easy", "normal", "hard")) {
            boolean current = difficulty().equals(level);
            entries.add(Entry.button(current ? Material.LIME_DYE : Material.GRAY_DYE, (current ? Theme.GOOD : Theme.NAME) + level,
                    q -> {
                        plugin.getConfig().set("bots.difficulty", level);
                        plugin.saveConfig();
                        audit.log(q.getName(), "bots-difficulty", level);
                        bots(q);
                    }, current ? "Current server setting" : "Set as the server setting"));
        }
        entries.add(Entry.button(Material.SPYGLASS, Theme.NAME + "Diagnose bots", q -> {
            q.closeInventory();
            audit.log(q.getName(), "bots-diagnose", "server");
            Bukkit.dispatchCommand(q, Routes.BOTS);
        }, "Same as /admin games bots"));
        ui.show(p, "Bots", "Maps can still override this.", entries, this::open);
    }

    // ---- server ---------------------------------------------------------------------------

    private void server(Player p) {
        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.button(Material.REDSTONE, Theme.BAD + "Reload", q -> ui.confirm(q, "Reload?",
                List.of(Theme.TEXT + "Reload PulseGames", "Config, maps, kits, lobby placements and events."), true, r -> {
                    r.closeInventory();
                    audit.log(r.getName(), "reload", "server");
                    Bukkit.dispatchCommand(r, Routes.RELOAD);
                }, this::server), "Same as /admin games reload"));
        entries.add(Entry.button(Material.FILLED_MAP, Theme.NAME + "Map readiness board", q -> {
            q.closeInventory();
            audit.log(q.getName(), "maps-board", "server");
            Bukkit.dispatchCommand(q, Routes.MAPS_BOARD);
        }, "Same as /admin games maps board"));
        entries.add(Entry.button(Material.LECTERN, Theme.NAME + "Lobby placements", q -> {
            q.closeInventory();
            audit.log(q.getName(), "lobby-placements", "server");
            Bukkit.dispatchCommand(q, Routes.LOBBY);
        }, "Shows the /admin games lobby commands"));
        ui.show(p, "Server", "Shortcuts.", entries, this::open);
    }
}
