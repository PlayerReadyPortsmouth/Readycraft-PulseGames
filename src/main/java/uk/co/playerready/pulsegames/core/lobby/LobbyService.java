package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.playerready.pulsegames.core.util.LocUtil;
import uk.co.playerready.pulsegames.core.util.Text;

public final class LobbyService {

    private final JavaPlugin plugin;

    public LobbyService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Location lobbyLocation() {
        String worldName = plugin.getConfig().getString("lobby.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) world = Bukkit.getWorlds().get(0);
        return LocUtil.parse(plugin.getConfig().getString("lobby.location", "0.5,100,0.5"), world);
    }

    public void setLobby(Location location) {
        plugin.getConfig().set("lobby.world", location.getWorld().getName());
        plugin.getConfig().set("lobby.location", LocUtil.serialize(location));
        plugin.saveConfig();
    }

    public boolean isLobbyWorld(World world) {
        return world.getName().equals(plugin.getConfig().getString("lobby.world", "world"));
    }

    public void sendToLobby(Player player) {
        player.teleport(lobbyLocation());
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.setAllowFlight(true); // lobby double jump
        if (plugin.getConfig().getBoolean("lobby.give-menu-item", true)) {
            giveMenuItem(player);
        }
    }

    public void giveMenuItem(Player player) {
        player.getInventory().clear();
        ItemStack compass = new ItemStack(Material.COMPASS);
        compass.editMeta(meta -> meta.displayName(Text.mm("<gradient:#ff5f6d:#ffc371><b>Game Menu</b></gradient> <gray>(right-click)")));
        player.getInventory().setItem(4, compass);
        ItemStack shop = new ItemStack(Material.SUNFLOWER);
        shop.editMeta(meta -> meta.displayName(Text.mm("<gold><b>Token Shop</b> <gray>(right-click)")));
        player.getInventory().setItem(6, shop);
        // Bedrock clients can't double-tap-jump through Geyser; give them a boost item.
        if (uk.co.playerready.pulsegames.core.util.BedrockUtil.isBedrock(player)) {
            ItemStack boost = new ItemStack(Material.FEATHER);
            boost.editMeta(meta -> meta.displayName(Text.mm("<aqua><b>Boost</b> <gray>(tap to leap)")));
            player.getInventory().setItem(2, boost);
        }
    }

    public boolean isBoostItem(ItemStack item) {
        return item != null && item.getType() == Material.FEATHER && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName();
    }

    public boolean isMenuItem(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName();
    }

    public boolean isShopItem(ItemStack item) {
        return item != null && item.getType() == Material.SUNFLOWER && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName();
    }
}
