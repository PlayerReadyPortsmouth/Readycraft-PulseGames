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
    /** Name we have already warned about, so the fallback doesn't spam the log. */
    private String warnedMissingWorld;

    public LobbyService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** The world players are actually sent to - the configured one, or the server's
     *  main world if that name doesn't exist. */
    public World lobbyWorld() {
        String worldName = plugin.getConfig().getString("lobby.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world != null) return world;
        World fallback = Bukkit.getWorlds().get(0);
        if (!worldName.equals(warnedMissingWorld)) {
            warnedMissingWorld = worldName;
            plugin.getLogger().warning("lobby.world '" + worldName + "' does not exist; using '"
                    + fallback.getName() + "' instead. Set it correctly with /pulse setlobby, "
                    + "otherwise the hub is protected under the wrong name.");
        }
        return fallback;
    }

    public Location lobbyLocation() {
        return LocUtil.parse(plugin.getConfig().getString("lobby.location", "0.5,100,0.5"), lobbyWorld());
    }

    public void setLobby(Location location) {
        plugin.getConfig().set("lobby.world", location.getWorld().getName());
        plugin.getConfig().set("lobby.location", LocUtil.serialize(location));
        plugin.saveConfig();
        warnedMissingWorld = null;
    }

    /** Must resolve through {@link #lobbyWorld()}: comparing the raw config name here
     *  while sendToLobby() falls back to another world silently turns off every lobby
     *  guard - damage cancellation, void safety, double jump and hub block protection. */
    public boolean isLobbyWorld(World world) {
        return lobbyWorld().equals(world);
    }

    public void sendToLobby(Player player) {
        // A mounted player (e.g. in a kart boat) can't be teleported - dismount first,
        // otherwise they stay behind in an instance world that then fails to unload.
        if (player.isInsideVehicle()) player.leaveVehicle();
        player.teleport(lobbyLocation());
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.setAllowFlight(true); // lobby double jump
        if (plugin.getConfig().getBoolean("lobby.give-menu-item", true)) {
            giveMenuItem(player);
        }
    }

    /**
     * Places the hub items. Deliberately does not clear the inventory: this runs
     * straight after a game restores the inventory the player came in with, and
     * clearing here made that whole snapshot/restore mechanism a no-op.
     */
    public void giveMenuItem(Player player) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        compass.editMeta(meta -> meta.displayName(Text.mm("<gradient:#ff5f6d:#ffc371><b>Game Menu</b></gradient> <gray>(right-click)")));
        giveMenuSlot(player, 4, compass);
        ItemStack shop = new ItemStack(Material.SUNFLOWER);
        shop.editMeta(meta -> meta.displayName(Text.mm("<gold><b>Token Shop</b> <gray>(right-click)")));
        giveMenuSlot(player, 6, shop);
    }

    private void giveMenuSlot(Player player, int slot, ItemStack item) {
        ItemStack existing = player.getInventory().getItem(slot);
        player.getInventory().setItem(slot, item);
        if (existing == null || existing.getType() == Material.AIR
                || isMenuItem(existing) || isShopItem(existing)) {
            return;
        }
        player.getInventory().addItem(existing).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
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
