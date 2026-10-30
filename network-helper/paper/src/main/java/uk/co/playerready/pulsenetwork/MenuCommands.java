package uk.co.playerready.pulsenetwork;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;
import java.util.function.BiPredicate;

/** Registers /namestyle and /rolemenu. LuckPerms is optional: without it both commands refuse politely. */
final class MenuCommands {
    private MenuCommands() {}

    static void register(JavaPlugin plugin, BiPredicate<Player, List<String>> sendRole) {
        LuckPermsLink lp = null;
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            try { lp = new LuckPermsLink(); } catch (LinkageError | RuntimeException e) { plugin.getLogger().warning("LuckPerms is present but not ready; menus will refuse."); }
        }
        plugin.getServer().getPluginManager().registerEvents(new ClickMenu.Guard(), plugin);
        if (plugin.getConfig().getBoolean("namestyle.enabled", true)) bind(plugin, "namestyle", new NameStyleCommand(plugin, lp));
        if (plugin.getConfig().getBoolean("rolemenu.enabled", true)) bind(plugin, "rolemenu", new RoleMenuCommand(plugin, lp, sendRole));
    }
    private static <T extends org.bukkit.command.CommandExecutor & org.bukkit.command.TabCompleter> void bind(JavaPlugin plugin, String name, T handler) {
        var command = plugin.getCommand(name);
        if (command != null) { command.setExecutor(handler); command.setTabCompleter(handler); }
    }
}
