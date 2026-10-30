package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.economy.Cosmetic;
import uk.co.playerready.pulsegames.core.economy.CosmeticsService;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Bukkit side of {@link ProgressionAdmin}: resolves the online target, sends the summary, logs the audit line. */
public final class ProgressionAdminCommand implements ProgressionAdmin.Backend {
    private final PulseGamesPlugin plugin;
    private final ProgressionAdmin admin;

    public ProgressionAdminCommand(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.admin = new ProgressionAdmin(this, plugin.getLogger()::info);
    }

    /** True when {@code args[0]} is one of our verbs. */
    public static boolean handles(String[] args) {
        return args.length > 0 && ProgressionAdmin.VERBS.contains(args[0].toLowerCase(Locale.ROOT));
    }

    /** Drops a leading "admin" so both /admin games progress xp ... and the legacy "admin xp ..." spelling work; null if not ours. */
    public static String[] strip(String[] args) {
        if (args.length > 1 && args[0].equalsIgnoreCase("admin")) args = java.util.Arrays.copyOfRange(args, 1, args.length);
        return handles(args) ? args : null;
    }

    public void handle(CommandSender sender, String[] args) {
        if (plugin.levels() == null) { sender.sendMessage(Text.msg("<red>Levels are not running.")); return; }
        ProgressionAdmin.Parsed parsed = ProgressionAdmin.parse(args);
        if (parsed.error() != null) { sender.sendMessage(Text.msg("<red>" + parsed.error())); return; }
        Player target = Bukkit.getPlayerExact(parsed.target());
        if (target == null || target.hasMetadata("NPC")) {
            sender.sendMessage(Text.msg("<red>" + parsed.target() + " must be online."));
            return;
        }
        plugin.levels().ensure(target);
        admin.run(sender.getName(), target.getUniqueId(), target.getName(), parsed)
                .forEach(line -> sender.sendMessage(Text.msg(line)));
    }

    public List<String> tab(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pulsegames.admin")) return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        String verb = args[0].toLowerCase(Locale.ROOT);
        List<String> choices = switch (args.length) {
            case 1 -> ProgressionAdmin.VERBS;
            case 2 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            case 3 -> switch (verb) {
                case "xp" -> List.of("add", "set");
                case "level" -> List.of("set", "reset");
                case "namestyle" -> List.of("grant", "revoke");
                default -> List.of();
            };
            case 4 -> verb.equals("namestyle") ? java.util.stream.Stream.concat(java.util.stream.Stream.of("all"), styleIds().stream()).toList() : List.of();
            default -> List.of();
        };
        return choices.stream().filter(c -> c.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(64).toList();
    }

    // ---- Backend ------------------------------------------------------------------------

    private Player online(UUID id) { return Bukkit.getPlayer(id); }

    @Override public long xp(UUID p) { return plugin.levels().adminXp(online(p)); }
    @Override public void setXp(UUID p, long xp) { plugin.levels().adminSetXp(online(p), xp); }
    @Override public List<String> unlock(UUID p, int level) { return plugin.levels().adminUnlock(online(p), level); }
    @Override public void refresh(UUID p) { plugin.levels().refreshName(online(p)); }
    @Override public int clearUnlocks(UUID p) { return plugin.levels().adminClearUnlocks(online(p)); }
    @Override public int unlockedCount(UUID p) { return plugin.economy().unlockedCount(online(p)); }
    @Override public boolean holds(UUID p, String id) { return online(p).hasPermission(NameStyleGrants.node(id)); }
    @Override public boolean grant(UUID p, String id) { return plugin.levels().nameStyleGrants().adminGrant(p, id); }
    @Override public boolean revoke(UUID p, String id) { return plugin.levels().nameStyleGrants().adminRevoke(p, id); }

    @Override public List<String> styleIds() {
        return CosmeticsService.levelRewards().stream().map(Cosmetic::nameStyleId).filter(java.util.Objects::nonNull).toList();
    }
}
