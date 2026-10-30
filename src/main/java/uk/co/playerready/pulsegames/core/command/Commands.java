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

/** /play, /party, /lobby, the hidden admin-route command and the retiring /pulse alias. */
public final class Commands implements CommandExecutor, TabCompleter {

    private final PulseGamesPlugin plugin;
    private final AdminRoutes adminRoutes;

    public Commands(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.adminRoutes = new AdminRoutes(plugin);
        for (String name : List.of("play", "party", "lobby", Routes.LEGACY, Routes.ADMIN_ROUTE, "stats", "shop", "pulsekits", "duels", "results", "challenges", "top", "level", "wardrobe", "event", "ranked")) {
            var command = plugin.getCommand(name);
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals(Routes.ADMIN_ROUTE)) {
            route(sender, args);
            return true;
        }
        if (name.equals(Routes.LEGACY)) {
            // Hidden alias for one release: same handler, one-line pointer to the standard spelling.
            sender.sendMessage(Text.msg("<gray>/pulse is moving: use <yellow>" + Routes.TYPED_GAMES + " "
                    + String.join(" ", args) + "</yellow>"));
            adminRoutes.handle(sender, AdminRoutes.fromLegacy(args));
            return true;
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
            case "event" -> plugin.events().command(player, args);
            case "results" -> plugin.results().command(player,args);
            case "duels" -> plugin.duelMenu().open(player);
            case "ranked" -> plugin.rankedMenu().command(player, args);
            case "pulsekits" -> plugin.kitSelection().open(player);
            case "shop" -> plugin.tokenShop().openMain(player);
            case "level" -> plugin.levelMenu().open(player);
            case "wardrobe" -> plugin.wardrobe().open(player);
        }
        return true;
    }

    /** {@code admin-route <area> [args]}: what the PulseNetwork hub dispatches. */
    private void route(CommandSender sender, String[] args) {
        String area = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (area.equals(Routes.AREA_GAMES) || area.equals(Routes.AREA_SERVER)) {
            String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
            // "server" is this plugin's own menu; "games" with no verb opens it as well.
            adminRoutes.handle(sender, area.equals(Routes.AREA_SERVER) || rest.length == 0 ? new String[]{"adminmenu"} : rest);
            return;
        }
        sender.sendMessage(Text.msg("<red>Unknown admin area."));
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
        if (args.length > 0 && (args[0].equalsIgnoreCase("private") || args[0].equalsIgnoreCase("spectate"))) {
            if (args[0].equalsIgnoreCase("private")) plugin.privateMatches().open(player);
            else plugin.privateMatches().spectate(player);
            return;
        }
        if (!plugin.parties().localOnly(player) && plugin.networkPartyCommand(player,args)) return;
        if (args.length == 0) {
            player.sendMessage(Text.msg("Usage: <yellow>/party <invite|accept|deny|leave|disband|list|chat|invites|private|spectate>"));
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


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "admin-route" -> {
                if (args.length >= 2 && args[0].equalsIgnoreCase(Routes.AREA_GAMES)) {
                    return adminRoutes.tab(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
                }
            }
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
            case "event" -> {
                if (args.length == 1) return List.of("join", "leave", "bracket", "results");
            }
            case "ranked" -> {
                if (args.length == 1) return List.of("queue", "leave", "history");
                if (args.length == 2 && args[0].equalsIgnoreCase("queue")) return plugin.ranked().kits();
            }
            case "party" -> {
                if (args.length == 1) {
                    return List.of("invite", "accept", "deny", "leave", "disband", "list", "chat", "invites", "private", "spectate");
                }
            }
        }
        return List.of();
    }
}
