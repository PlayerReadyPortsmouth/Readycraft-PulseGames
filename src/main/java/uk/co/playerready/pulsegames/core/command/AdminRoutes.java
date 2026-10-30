package uk.co.playerready.pulsegames.core.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.progression.ProgressionAdminCommand;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;
import java.util.Locale;

/**
 * The PulseGames admin area, addressed as {@code games} (and {@code server} for its menu) by the PulseNetwork
 * {@code /admin} hub through the hidden {@link Routes#ADMIN_ROUTE} command. The old {@code /pulse} command is a
 * hidden alias onto the same handler for one release.
 */
public final class AdminRoutes {
    /** Every verb under {@code /admin games}, in the order the usage line shows them. */
    public static final List<String> VERBS = List.of("adminmenu", "setup", "shape", "build", "blueprints", "bots",
            "diagnose", "maps", "lobby", "practice", "event", "progress", "world", "types", "matches", "arenas",
            "start", "end", "setlobby", "reload");
    /** Old spellings that the {@code /pulse} alias still accepts, mapped to the standard verb. */
    private static final java.util.Map<String, String> LEGACY = java.util.Map.of("games", "types", "list", "matches");

    private final PulseGamesPlugin plugin;
    private ProgressionAdminCommand progression;

    public AdminRoutes(PulseGamesPlugin plugin) { this.plugin = plugin; }

    /** Maps a legacy {@code /pulse} verb to its standard name; other args pass through. */
    public static String[] fromLegacy(String[] args) {
        if (args.length == 0 || !LEGACY.containsKey(args[0].toLowerCase(Locale.ROOT))) return args;
        String[] out = args.clone();
        out[0] = LEGACY.get(args[0].toLowerCase(Locale.ROOT));
        return out;
    }

    /** True for verbs that also run from the console. */
    static boolean consoleVerb(String[] args) {
        if (args.length == 0) return false;
        String sub = args[0].toLowerCase(Locale.ROOT);
        return progressionArgs(args) != null
                || List.of("build", "blueprints", "bots", "diagnose", "maps").contains(sub);
    }

    private static String[] progressionArgs(String[] args) {
        if (args.length > 1 && args[0].equalsIgnoreCase("progress")) args = java.util.Arrays.copyOfRange(args, 1, args.length);
        return ProgressionAdminCommand.strip(args);
    }

    /** Entry point; {@code args} excludes the area word. */
    public void handle(CommandSender sender, String[] args) {
        if (consoleVerb(args)) {
            console(sender, args);
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("In-game only. Console can use: /admin games <maps|bots|diagnose|build|blueprints|progress>");
            return;
        }
        player(p, args);
    }

    /** Subcommands runnable from the server console as well as in-game. */
    private void console(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("pulsegames.admin")) {
            sender.sendMessage(Text.msg("<red>No permission."));
            return;
        }
        var progressionArgs = progressionArgs(args);
        if (progressionArgs != null) {
            if (progression == null) progression = new uk.co.playerready.pulsegames.core.progression.ProgressionAdminCommand(plugin);
            progression.handle(sender, progressionArgs);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "maps" -> plugin.maps().handle(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "diagnose" -> {
                var rows=args.length>1?plugin.diagnostics().report(args[1]):plugin.diagnostics().recent();
                sender.sendMessage(net.kyori.adventure.text.Component.text(args.length>1?"Match report (latest 20 events):":"Recent match references: /admin games diagnose <reference>"));
                if(rows.isEmpty()) sender.sendMessage(net.kyori.adventure.text.Component.text("No report in memory. Older reports are in plugins/PulseGames/match-reports."));
                rows.forEach(row->sender.sendMessage(net.kyori.adventure.text.Component.text(row)));
            }
            case "bots" -> {
                if (args.length == 1) {
                    sender.sendMessage(Text.msg(plugin.bots().status()));
                    plugin.instances().all().stream()
                            .filter(g -> g.state() == uk.co.playerready.pulsegames.core.game.GameState.WAITING
                                    || g.state() == uk.co.playerready.pulsegames.core.game.GameState.COUNTDOWN)
                            .forEach(g -> sender.sendMessage(net.kyori.adventure.text.Component.text(
                                    g.id() + " — " + plugin.bots().queueStatus(g))));
                    return;
                }
                if (!sender.hasPermission("pulsegames.admin")) {
                    sender.sendMessage(Text.msg("<red>No permission."));
                    return;
                }
                if (!args[1].equalsIgnoreCase("inspect") || args.length > 3) {
                    sender.sendMessage(net.kyori.adventure.text.Component.text("Usage: /admin games bots inspect [bot name or UUID]"));
                    return;
                }
                uk.co.playerready.pulsegames.core.bot.BotDiagnostics.report(plugin.bots().diagnostics(),
                        args.length == 3 ? args[2] : null).forEach(line ->
                        sender.sendMessage(net.kyori.adventure.text.Component.text(line)));
            }
            case "blueprints" -> sender.sendMessage(Text.msg("Blueprints: <yellow>"
                    + String.join(", ", plugin.mapBuilder().available())));
            case "build" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.msg("<red>Usage: /admin games build <blueprint> "
                            + "<gray>(see /admin games blueprints)"));
                    return;
                }
                plugin.mapBuilder().build(sender, args[1]);
            }
            default -> { }
        }
    }

    private void player(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(Text.msg("Usage: <yellow>" + Routes.TYPED_GAMES + " <" + String.join("|", VERBS) + ">"));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "adminmenu" -> plugin.adminMenu().open(player);
            case "event" -> plugin.events().admin(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "practice" -> plugin.practice().handle(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "setup" -> plugin.setup().handle(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "shape" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("Usage: <yellow>/admin games shape <list|paste|undo> [file]"));
                    return;
                }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "list" -> player.sendMessage(Text.msg("Shape files: <yellow>"
                            + String.join(", ", plugin.shapes().available())));
                    case "paste" -> {
                        if (args.length < 3) {
                            player.sendMessage(Text.msg("<red>Usage: /admin games shape paste <file>"));
                            return;
                        }
                        plugin.shapes().paste(player, args[2]);
                    }
                    case "undo" -> plugin.shapes().undo(player);
                    default -> player.sendMessage(Text.msg("<red>Unknown shape subcommand."));
                }
            }
            case "world" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("<red>Usage: /admin games world <name> [void|normal] <gray>- new worlds default to void"));
                    return;
                }
                if (args.length > 3 || (args.length == 3 && !args[2].equalsIgnoreCase("void") && !args[2].equalsIgnoreCase("normal"))) {
                    player.sendMessage(Text.msg("<red>World type must be void or normal. Existing worlds are never cleared."));
                    return;
                }
                plugin.setup().loadBuildWorld(player, args[1], args.length < 3 || args[2].equalsIgnoreCase("void"));
            }
            case "types" -> {
                player.sendMessage(Text.msg("Registered games:"));
                for (GameType type : plugin.registry().all()) {
                    player.sendMessage(Text.mm(" <gray>- <yellow>" + type.id() + "</yellow> <gray>modes: <white>"
                            + String.join(", ", type.modes().stream().map(GameMode::id).toList())));
                }
            }
            case "matches" -> {
                player.sendMessage(Text.msg("Running instances: <yellow>" + plugin.instances().all().size()));
                for (GameInstance instance : plugin.instances().all()) {
                    player.sendMessage(Text.mm(" <gray>- <yellow>" + instance.type().id() + "</yellow>/"
                            + instance.mode().id() + " <gray>map=" + instance.arena().id()
                            + " state=<white>" + instance.state() + "</white> players=<white>"
                            + instance.players().size()));
                }
            }
            case "arenas" -> {
                player.sendMessage(Text.msg("Loaded arenas: <yellow>" + plugin.arenas().all().size()));
                plugin.arenas().all().forEach(a -> player.sendMessage(Text.mm(" <gray>- <yellow>" + a.id()
                        + "</yellow> game=<white>" + a.gameId() + "</white> template=<white>" + a.worldTemplate())));
            }
            case "start" -> {
                GameInstance instance = plugin.instances().byPlayer(player);
                if (instance == null) {
                    player.sendMessage(Text.msg("<red>You are not in a game."));
                    return;
                }
                instance.start();
                player.sendMessage(Text.msg("Force-started."));
            }
            case "end" -> {
                GameInstance instance = plugin.instances().byPlayer(player);
                if (instance == null) {
                    player.sendMessage(Text.msg("<red>You are not in a game."));
                    return;
                }
                instance.forceEnd();
                player.sendMessage(Text.msg("Force-ended."));
            }
            case "setlobby" -> {
                plugin.lobby().setLobby(player.getLocation());
                player.sendMessage(Text.msg("Main lobby set to your location."));
            }
            case "lobby" -> plugin.lobbyPlacements().handle(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "reload" -> {
                plugin.reloadConfig();
                plugin.arenas().load();
                plugin.kits().load();
                plugin.lobbyPlacements().reload();
                plugin.events().load();
                player.sendMessage(Text.msg("Config, arenas, kits and lobby placements reloaded."));
            }
            default -> player.sendMessage(Text.msg("<red>Unknown subcommand."));
        }
    }

    public List<String> tab(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pulsegames.admin")) return List.of();
        if (args.length == 0) return List.of();
        var progressionArgs = progressionArgs(args);
        if (progressionArgs != null || args.length == 1 && args[0].equalsIgnoreCase("progress")) {
            if (progression == null) progression = new uk.co.playerready.pulsegames.core.progression.ProgressionAdminCommand(plugin);
            return progression.tab(sender, progressionArgs == null ? new String[]{""} : progressionArgs);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("maps")) {
            if (!sender.hasPermission("pulsegames.admin")) return List.of();
            List<String> choices = List.of();
            if (args.length == 2) choices = List.of("board", "list", "check", "report", "export", "register", "revision", "mark");
            else if (args.length == 3) choices = plugin.registry().all().stream().map(GameType::id).toList();
            else if (args.length == 4 && !args[1].equalsIgnoreCase("list"))
                choices = plugin.arenas().all().stream().filter(arena -> arena.gameId().equalsIgnoreCase(args[2])).map(arena -> arena.id()).distinct().toList();
            else if (args.length == 5 && args[1].equalsIgnoreCase("mark"))
                choices = java.util.Arrays.stream(uk.co.playerready.pulsegames.core.maps.MapReviewStore.Check.values())
                        .filter(check -> check != uk.co.playerready.pulsegames.core.maps.MapReviewStore.Check.VISUAL || sender.hasPermission("pulsegames.maps.approve"))
                        .map(check -> check.name().toLowerCase(Locale.ROOT)).toList();
            else if (args.length == 6 && args[1].equalsIgnoreCase("mark"))
                choices = java.util.Arrays.stream(uk.co.playerready.pulsegames.core.maps.MapReviewStore.Outcome.values()).map(outcome -> outcome.name().toLowerCase(Locale.ROOT)).toList();
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(64).toList();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("event")) {
            if (!sender.hasPermission("pulsegames.admin")) return List.of();
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            List<String> choices = args.length == 2 ? List.of("start", "cancel", "list")
                    : args.length == 3 && args[1].equalsIgnoreCase("start") ? plugin.registry().all().stream().map(GameType::id).toList()
                    : args.length == 4 && args[1].equalsIgnoreCase("start") && plugin.registry().get(args[2]) != null
                        ? plugin.registry().get(args[2]).modes().stream().map(GameMode::id).toList()
                    : args.length == 5 && args[1].equalsIgnoreCase("start") ? List.of("bracket", "points") : List.of();
            return choices.stream().filter(choice -> choice.startsWith(prefix)).limit(64).toList();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("practice")) {
            if (!sender.hasPermission("pulsegames.admin")) return List.of();
            if (args.length == 2) return List.of("start", "repeat", "end", "report");
            if (args[1].equalsIgnoreCase("start")) {
                if (args.length == 3) return plugin.registry().all().stream().map(GameType::id).toList();
                var type = plugin.registry().get(args[2]);
                if (args.length == 4 && type != null) return type.modes().stream().map(GameMode::id).toList();
                if (args.length == 5 && type != null) return plugin.arenas().forGame(type.id(),args[3]).stream().map(a -> a.id()).toList();
                if (args.length == 6) return List.of("easy", "normal", "hard");
            }
            return List.of();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("bots")) {
            if (!sender.hasPermission("pulsegames.admin")) return List.of();
            if (args.length == 2) return "inspect".startsWith(args[1].toLowerCase(Locale.ROOT)) ? List.of("inspect") : List.of();
            if (args.length == 3 && args[1].equalsIgnoreCase("inspect")) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return plugin.bots().diagnostics().stream()
                        .flatMap(bot -> bot.id() == null ? java.util.stream.Stream.of(bot.name())
                                : java.util.stream.Stream.of(bot.name(), bot.id().toString()))
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().limit(64).toList();
            }
            return List.of();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("lobby")) return plugin.lobbyPlacements().tab(sender, args);
        if (args.length == 1) {
            return List.of("adminmenu", "lobby", "setup", "shape", "build", "blueprints", "bots", "diagnose", "maps", "practice", "event", "progress", "world", "types", "matches", "arenas", "start", "end", "setlobby", "reload");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("world")) return List.of("void", "normal");
        if (args.length == 2 && args[0].equalsIgnoreCase("build")) {
            return plugin.mapBuilder().available();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("shape")) {
            return List.of("list", "paste", "undo");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("shape") && args[1].equalsIgnoreCase("paste")) {
            return plugin.shapes().available();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            return List.of("start", "wand", "pos1", "pos2", "lobby", "spectator", "addspawn", "clearspawns",
                    "region", "removeregion", "removespawn", "setloc", "addloc", "removeloc", "clearloc", "set", "palette", "check", "validate", "gui", "book", "save", "cancel");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("palette"))
            return List.of("list", "scan", "add", "remove", "global");
        if (args.length == 3 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("start")) {
            return plugin.registry().all().stream().map(GameType::id).toList();
        }
        return List.of();
    }
}
