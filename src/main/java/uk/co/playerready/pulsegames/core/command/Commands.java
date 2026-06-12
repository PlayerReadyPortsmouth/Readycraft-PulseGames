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
import uk.co.playerready.pulsegames.core.npc.NpcDefinition;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;
import java.util.Locale;

/** /play, /party, /lobby and /pulse admin commands. */
public final class Commands implements CommandExecutor, TabCompleter {

    private final PulseGamesPlugin plugin;

    public Commands(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        for (String name : List.of("play", "party", "lobby", "pulse", "stats", "shop", "practice", "calm",
                "quests", "achievements")) {
            var command = plugin.getCommand(name);
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("In-game only.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "play" -> play(player, args);
            case "party" -> party(player, args);
            case "lobby" -> plugin.playService().leave(player);
            case "stats" -> stats(player);
            case "shop" -> plugin.tokenShop().openMain(player);
            case "practice" -> practice(player, args);
            case "quests" -> plugin.quests().show(player);
            case "achievements" -> plugin.achievements().show(player);
            case "calm" -> {
                boolean calm = plugin.prefs().toggleCalm(player);
                player.sendMessage(Text.msg(calm
                        ? "<green>Calm mode on.</green> <gray>Quieter sounds, no flashing titles or particle bursts."
                        : "Calm mode off - full effects restored."));
            }
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
        if (type.wip()) {
            player.sendMessage(Text.msg("<red>" + type.displayName() + " is coming soon!"));
            return;
        }
        GameMode mode = args.length > 1 ? type.mode(args[1]) : type.defaultMode();
        if (mode == null) {
            player.sendMessage(Text.msg("<red>Unknown mode. Modes: <yellow>"
                    + String.join(", ", type.modes().stream().map(GameMode::id).toList())));
            return;
        }
        plugin.playService().join(player, type, mode);
    }

    private void stats(Player player) {
        player.sendMessage(Text.msg("<yellow><b>Your stats</b>"));
        int totalWins = 0, totalKills = 0, totalPlayed = 0;
        for (GameType type : plugin.registry().all()) {
            int played = plugin.stats().get(player, type.id(), "played");
            if (played == 0) continue;
            int wins = plugin.stats().get(player, type.id(), "wins");
            int kills = plugin.stats().get(player, type.id(), "kills");
            totalWins += wins;
            totalKills += kills;
            totalPlayed += played;
            player.sendMessage(Text.mm(" <gray>" + type.displayName() + ": <green>" + wins
                    + "W</green> <white>" + played + " played</white>"
                    + (kills > 0 ? " <red>" + kills + " kills" : "")));
        }
        player.sendMessage(Text.mm(" <gray>Total: <green>" + totalWins + " wins</green><gray>, <white>"
                + totalPlayed + " games</white><gray>, <red>" + totalKills + " kills"));
    }

    private void practice(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(Text.msg("Usage: <yellow>/practice <game> [mode]</yellow> <gray>- solo, pressure-free."));
            return;
        }
        GameType type = plugin.registry().get(args[0]);
        if (type == null) {
            player.sendMessage(Text.msg("<red>Unknown game. See <yellow>/pulse games"));
            return;
        }
        GameMode mode = args.length > 1 ? type.mode(args[1]) : type.defaultMode();
        if (mode == null) {
            player.sendMessage(Text.msg("<red>Unknown mode. Modes: <yellow>"
                    + String.join(", ", type.modes().stream().map(GameMode::id).toList())));
            return;
        }
        plugin.playService().practice(player, type, mode);
    }

    private void party(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(Text.msg("Usage: <yellow>/party <invite|accept|deny|leave|disband|list|chat>"));
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
            case "chat" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("<red>Usage: /party chat <message>"));
                    return;
                }
                plugin.parties().chat(player, String.join(" ", List.of(args).subList(1, args.length)));
            }
            case "assist" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("<red>Usage: /party assist <member> <gray>- toggle buddy-assist "
                            + "(joins games protected, non-competing - for helpers/support workers)"));
                    return;
                }
                var party = plugin.parties().partyOf(player);
                if (party == null || !party.isLeader(player)) {
                    player.sendMessage(Text.msg("<red>Only the party leader can set assistants."));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !party.contains(target.getUniqueId())) {
                    player.sendMessage(Text.msg("<red>That player isn't in your party."));
                    return;
                }
                boolean nowAssist = party.toggleAssistant(target.getUniqueId());
                plugin.parties().broadcast(party, nowAssist
                        ? "<yellow>" + target.getName() + "</yellow> is now a <aqua>buddy assistant</aqua> - "
                        + "they'll join games protected and non-competing."
                        : "<yellow>" + target.getName() + "</yellow> is a normal player again.");
            }
            default -> player.sendMessage(Text.msg("<red>Unknown subcommand."));
        }
    }

    private void admin(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(Text.msg("Usage: <yellow>/pulse <setup|npc|world|games|list|arenas|start|end|setlobby|reload>"));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
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
                    player.sendMessage(Text.msg("<red>Usage: /pulse world <name> <gray>- load/create a build world"));
                    return;
                }
                plugin.setup().loadBuildWorld(player, args[1]);
            }
            case "leaderboard" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("Usage: <yellow>/pulse leaderboard <add <game|overall>|clear>"));
                    return;
                }
                if (args[1].equalsIgnoreCase("clear")) {
                    plugin.leaderboards().clear(player);
                } else if (args[1].equalsIgnoreCase("add") && args.length >= 3) {
                    plugin.leaderboards().add(player, args[2].toLowerCase(Locale.ROOT));
                } else {
                    player.sendMessage(Text.msg("<red>Usage: /pulse leaderboard add <game|overall>"));
                }
            }
            case "event" -> {
                if (args.length < 2) {
                    player.sendMessage(Text.msg("Usage: <yellow>/pulse event <on|off|name <text>|multiplier <x>>"));
                    return;
                }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "on" -> plugin.getConfig().set("event.active", true);
                    case "off" -> plugin.getConfig().set("event.active", false);
                    case "name" -> plugin.getConfig().set("event.name",
                            String.join(" ", List.of(args).subList(2, args.length)));
                    case "multiplier" -> plugin.getConfig().set("event.token-multiplier",
                            Double.parseDouble(args[2]));
                    default -> {
                        player.sendMessage(Text.msg("<red>Unknown event option."));
                        return;
                    }
                }
                plugin.saveConfig();
                boolean active = plugin.getConfig().getBoolean("event.active");
                player.sendMessage(Text.msg("Event <yellow>" + plugin.getConfig().getString("event.name")
                        + "</yellow> is now " + (active ? "<green>ACTIVE</green> <gray>("
                        + plugin.getConfig().getDouble("event.token-multiplier") + "x tokens)" : "<red>off")));
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
            case "npc" -> npc(player, args);
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

    /** /pulse npc - Citizens-backed lobby/map NPCs. */
    private void npc(Player player, String[] args) {
        if (!plugin.npcs().available()) {
            player.sendMessage(Text.msg("<red>NPCs need the <yellow>Citizens</yellow> plugin installed."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Text.msg("Usage: <yellow>/pulse npc <create|line|skin|radius|remove|list>"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(Text.msg("Usage: <yellow>/pulse npc create game <game|menu> [name]</yellow> "
                            + "or <yellow>/pulse npc create greeter [name]"));
                    return;
                }
                switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "game" -> {
                        if (args.length < 4) {
                            player.sendMessage(Text.msg("<red>Usage: /pulse npc create game <game|menu> [name]"));
                            return;
                        }
                        String gameId = args[3].toLowerCase(Locale.ROOT);
                        GameType type = plugin.registry().get(gameId);
                        if (type == null && !gameId.equals("menu")) {
                            player.sendMessage(Text.msg("<red>Unknown game. Use a game id, or <yellow>menu</yellow> "
                                    + "for the full game list."));
                            return;
                        }
                        String name = args.length > 4
                                ? String.join(" ", List.of(args).subList(4, args.length))
                                : (type != null ? "<yellow><b>" + type.displayName() : "<yellow><b>Games");
                        var def = plugin.npcs().create(player,
                                NpcDefinition.Kind.GAME,
                                type != null ? type.id() : null, name);
                        player.sendMessage(Text.msg("Created game NPC <yellow>" + def.id() + "</yellow> here. "
                                + "Right-clicking it opens " + (type != null ? type.displayName() : "the game menu")
                                + ". Set a skin with <yellow>/pulse npc skin " + def.id() + " <player>"));
                    }
                    case "greeter" -> {
                        String name = args.length > 3
                                ? String.join(" ", List.of(args).subList(3, args.length))
                                : "<aqua><b>Greeter";
                        var def = plugin.npcs().create(player,
                                NpcDefinition.Kind.GREETER, null, name);
                        player.sendMessage(Text.msg("Created greeter NPC <yellow>" + def.id() + "</yellow> here. "
                                + "Give it things to say with <yellow>/pulse npc line " + def.id() + " <text>"));
                        if (!plugin.lobby().isLobbyWorld(player.getWorld())) {
                            player.sendMessage(Text.mm(" <gray>Placed in world <white>" + player.getWorld().getName()
                                    + "</white> - if that's a map template, it'll appear in every game on that map."));
                        }
                    }
                    default -> player.sendMessage(Text.msg("<red>NPC kinds: <yellow>game</yellow>, <yellow>greeter"));
                }
            }
            case "line" -> {
                if (args.length < 4) {
                    player.sendMessage(Text.msg("<red>Usage: /pulse npc line <id> <text> <gray>(MiniMessage ok)"));
                    return;
                }
                var def = plugin.npcs().byId(args[2]);
                if (def == null) {
                    player.sendMessage(Text.msg("<red>No NPC with id <yellow>" + args[2] + "</yellow>. See /pulse npc list"));
                    return;
                }
                String line = String.join(" ", List.of(args).subList(3, args.length));
                plugin.npcs().addLine(def, line);
                player.sendMessage(Text.msg("Added line " + def.lines().size() + " to <yellow>" + def.id() + "</yellow>:"));
                player.sendMessage(Text.mm(" " + def.name() + " <dark_gray>» <gray>" + line));
            }
            case "skin" -> {
                if (args.length < 4) {
                    player.sendMessage(Text.msg("<red>Usage: /pulse npc skin <id> <playerName>"));
                    return;
                }
                var def = plugin.npcs().byId(args[2]);
                if (def == null) {
                    player.sendMessage(Text.msg("<red>No NPC with id <yellow>" + args[2] + "</yellow>."));
                    return;
                }
                plugin.npcs().setSkin(def, args[3]);
                player.sendMessage(Text.msg("NPC <yellow>" + def.id() + "</yellow> now wears <yellow>"
                        + args[3] + "</yellow>'s skin."));
            }
            case "radius" -> {
                if (args.length < 4) {
                    player.sendMessage(Text.msg("<red>Usage: /pulse npc radius <id> <blocks>"));
                    return;
                }
                var def = plugin.npcs().byId(args[2]);
                if (def == null) {
                    player.sendMessage(Text.msg("<red>No NPC with id <yellow>" + args[2] + "</yellow>."));
                    return;
                }
                try {
                    plugin.npcs().setRadius(def, Double.parseDouble(args[3]));
                } catch (NumberFormatException e) {
                    player.sendMessage(Text.msg("<red>Not a number: " + args[3]));
                    return;
                }
                player.sendMessage(Text.msg("NPC <yellow>" + def.id() + "</yellow> now chats within <yellow>"
                        + def.radius() + "</yellow> blocks."));
            }
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(Text.msg("<red>Usage: /pulse npc remove <id>"));
                    return;
                }
                player.sendMessage(plugin.npcs().remove(args[2])
                        ? Text.msg("Removed NPC <yellow>" + args[2] + "</yellow>.")
                        : Text.msg("<red>No NPC with id <yellow>" + args[2] + "</yellow>."));
            }
            case "list" -> {
                player.sendMessage(Text.msg("NPCs: <yellow>" + plugin.npcs().all().size()));
                for (var def : plugin.npcs().all()) {
                    player.sendMessage(Text.mm(" <gray>- <yellow>" + def.id() + "</yellow> " + def.name()
                            + " <gray>world=<white>" + def.world() + "</white>"
                            + (def.kind() == NpcDefinition.Kind.GAME
                                    ? " opens=<white>" + (def.gameId() == null ? "menu" : def.gameId())
                                    : " lines=<white>" + def.lines().size())));
                }
            }
            default -> player.sendMessage(Text.msg("<red>Unknown npc subcommand."));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "play", "practice" -> {
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
                    return List.of("invite", "accept", "deny", "leave", "disband", "list", "chat", "assist");
                }
            }
            case "pulse" -> {
                if (args.length == 1) {
                    return List.of("setup", "shape", "world", "games", "list", "arenas", "start", "end",
                            "setlobby", "reload", "leaderboard", "event", "npc");
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
                    return List.of("create", "line", "skin", "radius", "remove", "list");
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("npc")) {
                    if (args[1].equalsIgnoreCase("create")) return List.of("game", "greeter");
                    if (List.of("line", "skin", "radius", "remove").contains(args[1].toLowerCase(Locale.ROOT))) {
                        return plugin.npcs().all().stream()
                                .map(NpcDefinition::id).toList();
                    }
                }
                if (args.length == 4 && args[0].equalsIgnoreCase("npc") && args[1].equalsIgnoreCase("create")
                        && args[2].equalsIgnoreCase("game")) {
                    var ids = new java.util.ArrayList<>(plugin.registry().all().stream().map(GameType::id).toList());
                    ids.add("menu");
                    return ids;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("leaderboard")) {
                    return List.of("add", "clear");
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("event")) {
                    return List.of("on", "off", "name", "multiplier");
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("shape")) {
                    return List.of("list", "paste", "undo");
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("shape") && args[1].equalsIgnoreCase("paste")) {
                    return plugin.shapes().available();
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
                    return List.of("start", "wand", "lobby", "spectator", "addspawn", "clearspawns",
                            "region", "setloc", "addloc", "set", "check", "gui", "save", "cancel");
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("start")) {
                    return plugin.registry().all().stream().map(GameType::id).toList();
                }
            }
        }
        return List.of();
    }
}
