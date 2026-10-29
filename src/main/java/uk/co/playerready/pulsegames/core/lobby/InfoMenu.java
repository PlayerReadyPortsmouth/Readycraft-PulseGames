package uk.co.playerready.pulsegames.core.lobby;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.playerready.pulsegames.core.ui.Menu;

import java.util.Map;

/** Read-only chest GUI (challenges, leaderboard). Chest inventories work on Java and Bedrock alike. */
public final class InfoMenu {
    private InfoMenu() { }

    public static void open(Player player, Component title, int rows, Map<Integer, ItemStack> items) {
        Menu menu = new Menu(JavaPlugin.getProvidingPlugin(InfoMenu.class), player, title, rows);
        menu.renderer(() -> items.forEach(menu::set));
        menu.open(player);
    }
}
