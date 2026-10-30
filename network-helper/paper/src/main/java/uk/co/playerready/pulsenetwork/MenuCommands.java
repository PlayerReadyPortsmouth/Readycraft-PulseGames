package uk.co.playerready.pulsenetwork;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;
import java.util.function.BiPredicate;

/** Registers /namestyle, /rolemenu (hidden alias) and the /admin hub. LuckPerms is optional: without it both commands refuse politely. */
final class MenuCommands {
    private MenuCommands() {}

    static void register(JavaPlugin plugin, BiPredicate<Player, List<String>> sendRole, AdminHubModel.Announcer announcer) {
        LuckPermsLink lp = null;
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            try { lp = new LuckPermsLink(); } catch (LinkageError | RuntimeException e) { plugin.getLogger().warning("LuckPerms is present but not ready; menus will refuse."); }
        }
        plugin.getServer().getPluginManager().registerEvents(new ClickMenu.Guard(), plugin);
        if (plugin.getConfig().getBoolean("namestyle.enabled", true)) bind(plugin, "namestyle", new NameStyleCommand(plugin, lp));
        var roles = new RoleMenuCommand(plugin, lp, sendRole);
        boolean hub = plugin.getConfig().getBoolean("admin.enabled", true);
        AdminHubCommand hubCommand = hub ? new AdminHubCommand(plugin, lp, sendRole, announcer, roles) : null;
        // /rolemenu is a hidden alias of /admin roles for one release; the hub prints the hint and opens the same menu.
        if (plugin.getConfig().getBoolean("rolemenu.enabled", true)) {
            var old = plugin.getCommand("rolemenu");
            if (old != null) { old.setExecutor(hubCommand != null ? hubCommand : roles); old.setTabCompleter(roles); }
        }
        plugin.getServer().getPluginManager().registerEvents(new HiddenAliases(), plugin);
        plugin.getServer().getPluginManager().registerEvents(AnvilPrompt.listener(), plugin);
        if (hubCommand != null) { var cmd = plugin.getCommand("admin"); if (cmd != null) { cmd.setExecutor(hubCommand); cmd.setTabCompleter(hubCommand); } }
    }
    private static <T extends org.bukkit.command.CommandExecutor & org.bukkit.command.TabCompleter> void bind(JavaPlugin plugin, String name, T handler) {
        var command = plugin.getCommand(name);
        if (command != null) { command.setExecutor(handler); command.setTabCompleter(handler); }
    }

    /** Takes the old admin words out of the command list sent to clients, so they do not tab-complete. They still work when typed. */
    static final class HiddenAliases implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler void onSend(org.bukkit.event.player.PlayerCommandSendEvent e) {
            e.getCommands().removeIf(c -> AdminHubModel.HIDDEN_ALIASES.contains(c.toLowerCase(java.util.Locale.ROOT)) || c.toLowerCase(java.util.Locale.ROOT).startsWith("pulsenetwork:") && AdminHubModel.HIDDEN_ALIASES.contains(c.substring(c.indexOf(':') + 1).toLowerCase(java.util.Locale.ROOT)));
        }
    }
}
