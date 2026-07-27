package uk.co.playerready.pulsegames.core.lobby;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.List;

/** Compass-driven GUI: pick a game, then a mode. */
public final class GameMenu implements Listener {

    private final PulseGamesPlugin plugin;

    public GameMenu(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class MenuHolder implements InventoryHolder {
        final GameType gameOrNull; // null = top-level game list
        Inventory inventory;

        MenuHolder(GameType gameOrNull) {
            this.gameOrNull = gameOrNull;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    public void openGames(Player player) {
        List<GameType> games = new ArrayList<>(plugin.registry().all());
        int rows = Math.min(6, (games.size() + 8) / 9 + 1);
        MenuHolder holder = new MenuHolder(null);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Text.mm("<dark_gray>Select a game"));
        holder.inventory = inv;
        int slot = 0;
        for (GameType game : games) {
            ItemStack item = new ItemStack(game.icon());
            item.editMeta(meta -> {
                meta.displayName(Text.mm("<yellow><b>" + game.displayName() + (game.wip() ? " <red>(soon)" : "")));
                List<Component> lore = new ArrayList<>();
                lore.add(Text.mm("<gray>" + game.description()));
                lore.add(Text.mm(""));
                lore.add(Text.mm("<gray>Modes: <white>" + game.modes().size()));
                lore.add(Text.mm(game.wip() ? "<red>Coming soon!" : "<green>Click to play!"));
                meta.lore(lore);
            });
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
    }

    public void openModes(Player player, GameType game) {
        MenuHolder holder = new MenuHolder(game);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm("<dark_gray>" + game.displayName() + ": pick a mode"));
        holder.inventory = inv;
        int slot = 11;
        for (GameMode mode : game.modes()) {
            ItemStack item = new ItemStack(Material.PAPER);
            item.editMeta(meta -> {
                meta.displayName(Text.mm("<yellow><b>" + mode.displayName()));
                meta.lore(List.of(
                        Text.mm("<gray>Players: <white>" + mode.minPlayers() + "-" + mode.maxPlayers()),
                        Text.mm(mode.isTeams() ? "<gray>Teams of <white>" + mode.teamSize() : "<gray>Free for all"),
                        Text.mm("<green>Click to queue!")));
            });
            inv.setItem(slot++, item);
            if (slot % 9 == 8) slot += 3;
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Slots in the player's own inventory share the click event; without this a click
        // on your own hotbar queues you into whichever game sits in the matching menu slot.
        if (event.getClickedInventory() != event.getInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (holder.gameOrNull == null) {
            List<GameType> games = new ArrayList<>(plugin.registry().all());
            int index = event.getSlot();
            if (index >= games.size()) return;
            GameType game = games.get(index);
            if (game.wip()) {
                player.sendMessage(Text.msg("<red>" + game.displayName() + " is coming soon!"));
                return;
            }
            if (game.modes().size() == 1) {
                player.closeInventory();
                plugin.playService().join(player, game, game.defaultMode());
            } else {
                openModes(player, game);
            }
        } else {
            int modeIndex = modeIndexFromSlot(event.getSlot());
            if (modeIndex < 0 || modeIndex >= holder.gameOrNull.modes().size()) return;
            GameMode mode = holder.gameOrNull.modes().get(modeIndex);
            player.closeInventory();
            plugin.playService().join(player, holder.gameOrNull, mode);
        }
    }

    private int modeIndexFromSlot(int slot) {
        // Mirrors openModes layout: slots 11..16, then 20..25 ...
        int row = slot / 9;
        int col = slot % 9;
        if (col < 2 || col > 7 || row < 1) return -1;
        return (row - 1) * 6 + (col - 2);
    }
}
