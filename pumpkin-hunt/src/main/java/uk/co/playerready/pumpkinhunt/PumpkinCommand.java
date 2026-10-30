package uk.co.playerready.pumpkinhunt;

import uk.co.playerready.pumpkinhunt.logic.Lantern;
import uk.co.playerready.pumpkinhunt.logic.PumpkinProgress.Finisher;
import uk.co.playerready.pumpkinhunt.logic.SeasonWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /pumpkins} for players (progress, hints, top) and {@code /pumpkins add|remove|list|reload|reset|stats} for
 * admins. Plain text, so it reads the same on Java and on Bedrock. Players only ever see hints, never coordinates.
 */
public final class PumpkinCommand implements CommandExecutor, TabCompleter {
    private static final String PLAY = "pumpkinhunt.play";
    private static final String ADMIN = "pumpkinhunt.admin";
    private static final List<String> ADMIN_SUBS = HuntAdminRoute.VERBS.stream()
            .filter(v -> !v.equals("enable") && !v.equals("disable")).toList();

    private final PumpkinHuntManager hunt;

    public PumpkinCommand(PumpkinHuntManager hunt) { this.hunt = hunt; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        if (sub.equals("admin")) {
            adminRoute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (ADMIN_SUBS.contains(sub)) {
            if (!sender.hasPermission(ADMIN)) { msg(sender, "No permission.", NamedTextColor.RED); return true; }
            admin(sender, sub, args);
            return true;
        }
        if (!sender.hasPermission(PLAY)) { msg(sender, "No permission.", NamedTextColor.RED); return true; }
        SeasonWindow.State state = hunt.seasonState();
        if (state != SeasonWindow.State.OPEN) {
            msg(sender, hunt.messages().get(state == SeasonWindow.State.NOT_STARTED ? "not_started" : "over"), NamedTextColor.GOLD);
            return true;
        }
        if (sub.equals("top")) top(sender);
        else progress(sender);
        return true;
    }

    /** {@code /pumpkins admin ...} and the hidden {@code /admin-route hunt ...}; args are the words after "admin". */
    public void adminRoute(CommandSender sender, String[] afterAdmin) {
        if (!sender.hasPermission(ADMIN)) { msg(sender, "No permission.", NamedTextColor.RED); return; }
        HuntAdminRoute.Parsed r = HuntAdminRoute.parse(afterAdmin);
        if (r == null) {
            msg(sender, "Usage: /pumpkins admin <stats|list|reset <player|all>|enable|disable|add|remove|reload>", NamedTextColor.RED);
            return;
        }
        String[] args = new String[r.args().length + 1];
        System.arraycopy(r.args(), 0, args, 1, r.args().length);
        switch (r.verb()) {
            case "" -> summary(sender);
            case "enable", "disable" -> {
                boolean on = r.verb().equals("enable");
                hunt.setEnabled(on);
                msg(sender, "Pumpkin hunt " + (on ? "enabled" : "disabled") + " and config reloaded.", NamedTextColor.GREEN);
            }
            default -> admin(sender, r.verb(), args);
        }
    }

    private void summary(CommandSender sender) {
        msg(sender, "Pumpkin hunt: " + (hunt.enabled() ? "enabled" : "disabled") + ", season " + hunt.seasonState() + ", "
                + hunt.lanterns().size() + " lanterns, " + hunt.progress().top(Integer.MAX_VALUE).size() + " finished.", NamedTextColor.GOLD);
        msg(sender, "Try: /pumpkins admin stats | list | reset <player|all> | enable | disable", NamedTextColor.GRAY);
    }

    private void progress(CommandSender sender) {
        if (!(sender instanceof Player p)) { msg(sender, "Players only. Admins: /pumpkins list|stats", NamedTextColor.GRAY); return; }
        Set<String> ids = hunt.ids();
        int total = ids.size();
        int n = hunt.progress().foundCount(p.getUniqueId(), ids);
        msg(sender, hunt.messages().get("progress", "n", n, "total", total), NamedTextColor.GOLD);
        if (hunt.progress().completed(p.getUniqueId()) || n >= total) {
            msg(sender, hunt.messages().get("all_found"), NamedTextColor.GREEN);
            return;
        }
        msg(sender, hunt.messages().get("intro"), NamedTextColor.GRAY);
        msg(sender, hunt.messages().get("hints_title"), NamedTextColor.YELLOW);
        Set<String> found = hunt.progress().found(p.getUniqueId());
        for (Lantern l : hunt.lanterns()) {
            if (!found.contains(l.id())) msg(sender, hunt.messages().get("hint_line", "hint", l.hint()), NamedTextColor.WHITE);
        }
    }

    private void top(CommandSender sender) {
        List<Finisher> top = hunt.progress().top(10);
        if (top.isEmpty()) { msg(sender, hunt.messages().get("top_empty"), NamedTextColor.GRAY); return; }
        msg(sender, hunt.messages().get("top_title", "total", hunt.lanterns().size()), NamedTextColor.GOLD);
        int rank = 1;
        for (Finisher f : top) {
            String name = f.name().isBlank() ? "Someone" : f.name();
            msg(sender, hunt.messages().get("top_line", "rank", rank++, "name", name), NamedTextColor.WHITE);
        }
    }

    private void admin(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "list" -> list(sender);
            case "stats" -> stats(sender);
            case "reload" -> {
                hunt.reloadAll();
                msg(sender, "Reloaded: " + hunt.lanterns().size() + " lanterns.", NamedTextColor.GREEN);
            }
            case "add" -> add(sender, args);
            case "remove" -> {
                if (args.length < 2) { msg(sender, "Usage: /pumpkins remove <id>", NamedTextColor.RED); return; }
                msg(sender, hunt.removeLantern(args[1]) ? "Removed " + args[1] + "." : "No lantern with id " + args[1] + ".",
                        NamedTextColor.YELLOW);
            }
            case "reset" -> reset(sender, args);
            default -> { }
        }
    }

    private void list(CommandSender sender) {
        Map<String, Integer> counts = hunt.progress().stats(hunt.ids());
        msg(sender, "Pumpkin lanterns (" + hunt.lanterns().size() + "), season " + hunt.seasonState() + ":", NamedTextColor.GOLD);
        for (Lantern l : hunt.lanterns()) {
            msg(sender, l.id() + "  " + l.world() + " " + PumpkinHuntManager.coords(l) + "  found by " + counts.getOrDefault(l.id(), 0)
                    + "  (" + l.hint() + ")", NamedTextColor.WHITE);
        }
    }

    private void stats(CommandSender sender) {
        msg(sender, "Finds per lantern, fewest first (" + hunt.progress().top(Integer.MAX_VALUE).size() + " finished):", NamedTextColor.GOLD);
        hunt.progress().stats(hunt.ids()).entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry::getKey))
                .forEach(e -> msg(sender, e.getKey() + ": " + e.getValue(), NamedTextColor.WHITE));
    }

    private void add(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { msg(sender, "Run this in game, looking at the lantern.", NamedTextColor.RED); return; }
        if (args.length < 2) { msg(sender, "Usage: /pumpkins add <id> [hint...] (look at the jack-o'-lantern)", NamedTextColor.RED); return; }
        Block b = p.getTargetBlockExact(6);
        if (b == null || b.getType() != Material.JACK_O_LANTERN) { msg(sender, "Look at a jack-o'-lantern within 6 blocks.", NamedTextColor.RED); return; }
        String hint = args.length > 2 ? String.join(" ", List.of(args).subList(2, args.length)) : "a hidden pumpkin";
        hunt.addLantern(new Lantern(args[1].toLowerCase(), b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), hint));
        msg(sender, "Registered " + args[1].toLowerCase() + " at " + b.getX() + " " + b.getY() + " " + b.getZ() + ". Total: " + hunt.lanterns().size(), NamedTextColor.GREEN);
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) { msg(sender, "Usage: /pumpkins reset <player|all>", NamedTextColor.RED); return; }
        if (args[1].equalsIgnoreCase("all")) {
            int n = hunt.progress().resetAll();
            hunt.saveProgress();
            msg(sender, "Reset progress for " + n + " player(s).", NamedTextColor.YELLOW);
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) { msg(sender, "Unknown player " + args[1] + ".", NamedTextColor.RED); return; }
        UUID id = target.getUniqueId();
        boolean had = hunt.progress().reset(id);
        hunt.saveProgress();
        msg(sender, had ? "Reset " + args[1] + "." : args[1] + " had no progress.", NamedTextColor.YELLOW);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("top");
            if (sender.hasPermission(ADMIN)) { out.add("admin"); out.addAll(ADMIN_SUBS); }
        } else if (args.length >= 2 && sender.hasPermission(ADMIN)) {
            if (args[0].equalsIgnoreCase("admin")) {
                if (args.length == 2) out.addAll(HuntAdminRoute.VERBS);
                else if (args.length == 3 && args[1].equalsIgnoreCase("reset")) { out.add("all"); Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName())); }
                else if (args.length == 3 && args[1].equalsIgnoreCase("remove")) hunt.lanterns().forEach(l -> out.add(l.id()));
            }
            String sub = args.length == 2 ? args[0].toLowerCase() : "";
            if (sub.equals("remove")) hunt.lanterns().forEach(l -> out.add(l.id()));
            if (sub.equals("reset")) {
                out.add("all");
                Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return out;
    }

    private static void msg(CommandSender to, String text, NamedTextColor colour) { to.sendMessage(Component.text(text, colour)); }
}
