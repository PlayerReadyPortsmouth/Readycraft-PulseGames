package uk.co.playerready.pulsegames.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;
import java.util.Locale;

/** /play, /party, /lobby and /pulse admin commands. */
public final class Commands implements CommandExecutor, TabCompleter {

    private final PulseGamesPlugin plugin;

    public Commands(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        for (String name : List.of("play", "party", "lobby", "pulse", "stats", "shop", "pulsekits", "duels", "results", "challenges", "top")) {
            var command = plugin.getCommand(name);
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Console-capable admin subcommands (map generation needs no player).
        if (command.getName().equalsIgnoreCase("pulse") && args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("build") || sub.equals("blueprints") || sub.equals("bots") || sub.equals("diagnose") || sub.equals("maps")) {
                adminConsole(sender, args);
                return true;
            }
        }
        if (!(sender instanceof Player player)) {
            if (command.getName().equalsIgnoreCase("stats") && args.length >= 1 && Bukkit.getPlayerExact(args[0]) != null) {
                uk.co.playerready.pulsegames.core.stats.StatsMenu.sendText(plugin, sender, Bukkit.getPlayerExact(args[0]));
                return true;
            }
            sender.sendMessage("In-game only. Console: /stats <player>");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "play" -> play(player, args);
            case "party" -> party(player, args);
            case "lobby" -> plugin.playService().leave(player);
            case "stats" -> stats(player, args);
            case "challenges" -> plugin.engagement().openChallenges(player);
            case "top" -> plugin.engagement().top(player, args);
            case "results" -> plugin.results().command(player,args);
            case "duels" -> plugin.duelMenu().open(player);
            case "pulsekits" -> plugin.kitSelection().open(player);
            case "shop" -> plugin.tokenShop().openMain(player);
            case "pulse" -> admin(player, args);
        }
        return true;
    }

    private void play(Player player, String[] args) {
        if (args.length == 0) {
            plugin.gameMenu().openGames(player);
            return;
        }
        GameType type = plugin.registry().get(args[0]);
        if (type == null) {
            player.sendMessage(Text.msg("<red>Unknown game. Try: <yellow>"
                    + String.join(", ", plugin.registry().all().stream().map(GameType::id).toList())));
            return;
        }
        if (!plugin.gameAvailable(type)) {
            player.sendMessage(Text.msg("<red>" + type.displayName() + " is coming soon!"));
            return;
        }
        if (args.length == 1 && type.id().equals("duels")) { plugin.duelMenu().open(player); return; }
        GameMode mode = args.length > 1 ? type.mode(args[1]) : type.defaultMode();
        if (mode == null) {
            player.sendMessage(Text.msg("<red>Unknown mode. Modes: <yellow>"
                    + String.join(", ", type.modes().stream().map(GameMode::id).toList())));
            return;
        }
        plugin.playService().join(player, type, mode);
    }

    private void stats(Player player, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("--text")) {
            uk.co.playerready.pulsegames.core.stats.StatsMenu.sendText(plugin, player, player);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("settings")) {
            new uk.co.playerready.pulsegames.core.settings.SettingsMenu(plugin, null).open(player);
            return;
        }
        new uk.co.playerready.pulsegames.core.stats.StatsMenu(plugin).open(player);
    }

    private void party(Player player, String[] args) {
        if (!plugin.parties().localOnly(player) && plugin.networkPartyCommand(player,args)) return;
        if (args.length == 0) {
            player.sendMessage(Text.msg("Usage: <yellow>/party <invite|accept|deny|leave|disband|list|chat|invites>"));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("<red>Usage: /party invite <player>"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || target.equals(player)) {
                    player.sendMessage(Text.msg("<red>Player not found."));
                    return;
                }
                plugin.parties().invite(player, target);
            }
            case "accept" -> plugin.parties().accept(player);
            case "deny" -> plugin.parties().deny(player);
            case "leave" -> plugin.parties().leave(player);
            case "disband" -> plugin.parties().disband(player);
            case "list" -> {
                var party = plugin.parties().partyOf(player);
                if (party == null) {
                    player.sendMessage(Text.msg("<red>You are not in a party."));
                    return;
                }
                player.sendMessage(Text.msg("Party (<yellow>" + party.size() + "</yellow>): <white>"
                        + String.join(", ", party.onlineMembers().stream().map(Player::getName).toList())));
            }
            case "invites" -> {
                if (args.length < 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
                    player.sendMessage(Text.msg("Party invites are " + (plugin.parties().acceptsInvites(player) ? "<green>on" : "<red>off")
                            + "</white>. Usage: <yellow>/party invites <on|off>"));
                    return;
                }
                plugin.parties().setAcceptsInvites(player, args[1].equalsIgnoreCase("on"));
            }
            case "chat" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("<red>Usage: /party chat <message>"));
                    return;
                }
                plugin.parties().chat(player, String.join(" ", List.of(args).subList(1, args.length)));
            }
            default -> player.sendMessage(Text.msg("<red>Unknown subcommand."));
        }
    }

    /** Subcommands runnable from the server console as well as in-game. */
    private void adminConsole(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("pulsegames.admin")) {
            sender.sendMessage(Text.msg("<red>No permission."));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "maps" -> plugin.maps().handle(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "diagnose" -> {
                var rows=args.length>1?plugin.diagnostics().report(args[1]):plugin.diagnostics().recent();
                sender.sendMessage(net.kyori.adventure.text.Component.text(args.length>1?"Match report (latest 20 events):":"Recent match references: /pulse diagnose <reference>"));
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
                    sender.sendMessage(net.kyori.adventure.text.Component.text("Usage: /pulse bots inspect [bot name or UUID]"));
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
                    sender.sendMessage(Text.msg("<red>Usage: /pulse build <blueprint> "
                            + "<gray>(see /pulse blueprints)"));
                    return;
                }
                plugin.mapBuilder().build(sender, args[1]);
            }
            default -> { }
        }
    }

    private void admin(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(Text.msg("Usage: <yellow>/pulse <setup|shape|build|blueprints|bots|diagnose|maps|world|games|list|arenas|start|end|setlobby|reload>"));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "results" -> plugin.results().command(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "practice" -> plugin.practice().handle(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "setup" -> plugin.setup().handle(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "shape" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("Usage: <yellow>/pulse shape <list|paste|undo> [file]"));
                    return;
                }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "list" -> player.sendMessage(Text.msg("Shape files: <yellow>"
                            + String.join(", ", plugin.shapes().available())));
                    case "paste" -> {
                        if (args.length < 3) {
                            player.sendMessage(Text.msg("<red>Usage: /pulse shape paste <file>"));
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
                    player.sendMessage(Text.msg("<red>Usage: /pulse world <name> [void|normal] <gray>- new worlds default to void"));
                    return;
                }
                if (args.length > 3 || (args.length == 3 && !args[2].equalsIgnoreCase("void") && !args[2].equalsIgnoreCase("normal"))) {
                    player.sendMessage(Text.msg("<red>World type must be void or normal. Existing worlds are never cleared."));
                    return;
                }
                plugin.setup().loadBuildWorld(player, args[1], args.length < 3 || args[2].equalsIgnoreCase("void"));
            }
            case "games" -> {
                player.sendMessage(Text.msg("Registered games:"));
                for (GameType type : plugin.registry().all()) {
                    player.sendMessage(Text.mm(" <gray>- <yellow>" + type.id() + "</yellow> <gray>modes: <white>"
                            + String.join(", ", type.modes().stream().map(GameMode::id).toList())));
                }
            }
            case "list" -> {
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
            case "reload" -> {
                plugin.reloadConfig();
                plugin.arenas().load();
                plugin.kits().load();
                player.sendMessage(Text.msg("Config, arenas and kits reloaded."));
            }
            default -> player.sendMessage(Text.msg("<red>Unknown subcommand."));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "play" -> {
                if (args.length == 1) {
                    return plugin.registry().all().stream().map(GameType::id)
                            .filter(id -> id.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
                }
                if (args.length == 2) {
                    GameType type = plugin.registry().get(args[0]);
                    if (type != null) {
                        return type.modes().stream().map(GameMode::id)
                                .filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
                    }
                }
            }
            case "party" -> {
                if (args.length == 1) {
                    return List.of("invite", "accept", "deny", "leave", "disband", "list", "chat", "invites");
                }
            }
            case "pulse" -> {
                if (args.length >= 2 && args[0].equalsIgnoreCase("maps")) {
                    if (!sender.hasPermission("pulsegames.admin")) return List.of();
                    List<String> choices = List.of();
                    if (args.length == 2) choices = List.of("list", "check", "report", "export", "register", "revision", "mark");
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
                if (args.length == 1) {
                    return List.of("setup", "shape", "build", "blueprints", "bots", "diagnose", "maps", "practice", "results", "world", "games", "list", "arenas", "start", "end", "setlobby", "reload");
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
            }
        }
        return List.of();
    }
}
