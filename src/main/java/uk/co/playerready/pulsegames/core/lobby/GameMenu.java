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
import uk.co.playerready.pulsegames.core.util.MenuFx;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compass-driven GUI: pick a game, then a mode, then (optionally) a map. */
public final class GameMenu implements Listener {

    private static final String TITLE_TAG = "<gradient:#ff5f6d:#ffc371><b>";

    private final PulseGamesPlugin plugin;

    public GameMenu(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class MenuHolder implements InventoryHolder {
        final Map<Integer, GameType> games = new HashMap<>();
        final Map<Integer, GameMode> modes = new HashMap<>();
        final Map<Integer, String> maps = new HashMap<>();
        GameType game;   // set on mode/map pages
        GameMode mode;   // set on the map page
        int randomSlot = -1;
        Inventory inventory;

        @Override
        public Inventory getInventory() { return inventory; }
    }

    public void openGames(Player player) {
        List<GameType> games = new ArrayList<>(plugin.registry().all());
        int rows = Math.min(6, 2 + (games.size() + 6) / 7);
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Text.mm(TITLE_TAG + "Select a Game"));
        holder.inventory = inv;
        List<Integer> slots = MenuFx.interiorSlots(rows * 9);
        for (int i = 0; i < games.size() && i < slots.size(); i++) {
            GameType game = games.get(i);
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
            int slot = slots.get(i);
            inv.setItem(slot, item);
            holder.games.put(slot, game);
        }
        plugin.menuFx().open(player, inv);
    }

    /** Entry point for game NPCs and clicks: wip-check, then mode menu or straight to queue. */
    public void openGame(Player player, GameType game) {
        if (game.wip()) {
            player.sendMessage(Text.msg("<red>" + game.displayName() + " is coming soon!"));
            return;
        }
        if (game.modes().size() == 1) {
            afterModeChosen(player, game, game.defaultMode());
        } else {
            openModes(player, game);
        }
    }

    public void openModes(Player player, GameType game) {
        MenuHolder holder = new MenuHolder();
        holder.game = game;
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm(TITLE_TAG + game.displayName()
                + "</b></gradient> <dark_gray>- pick a mode"));
        holder.inventory = inv;
        List<GameMode> modes = game.modes();
        int slot = 10 + Math.max(0, (7 - modes.size()) / 2); // centre the row
        for (GameMode mode : modes) {
            if (slot > 16) break; // row full
            ItemStack item = new ItemStack(Material.PAPER);
            item.editMeta(meta -> {
                meta.displayName(Text.mm("<yellow><b>" + mode.displayName()));
                meta.lore(List.of(
                        Text.mm("<gray>Players: <white>" + mode.minPlayers() + "-" + mode.maxPlayers()),
                        Text.mm(mode.isTeams() ? "<gray>Teams of <white>" + mode.teamSize() : "<gray>Free for all"),
                        Text.mm("<green>Click to queue!")));
            });
            inv.setItem(slot, item);
            holder.modes.put(slot, mode);
            slot++;
        }
        plugin.menuFx().open(player, inv);
    }

    /** Map selection: pick a specific map, or Random. */
    public void openMaps(Player player, GameType game, GameMode mode) {
        var arenas = plugin.arenas().forGame(game.id(), mode.id());
        int rows = Math.min(6, 2 + (arenas.size() + 7) / 7);
        MenuHolder holder = new MenuHolder();
        holder.game = game;
        holder.mode = mode;
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Text.mm(TITLE_TAG + game.displayName()
                + "</b></gradient> <dark_gray>- pick a map"));
        holder.inventory = inv;
        List<Integer> slots = MenuFx.interiorSlots(rows * 9);
        ItemStack random = new ItemStack(Material.ENDER_PEARL);
        random.editMeta(meta -> {
            meta.displayName(Text.mm("<light_purple><b>Random Map"));
            meta.lore(List.of(Text.mm("<gray>Let fate decide!")));
        });
        holder.randomSlot = slots.get(0);
        inv.setItem(holder.randomSlot, random);
        for (int i = 0; i < arenas.size() && i + 1 < slots.size(); i++) {
            var arena = arenas.get(i);
            ItemStack item = new ItemStack(Material.MAP);
            item.editMeta(meta -> {
                meta.displayName(Text.mm("<yellow><b>" + arena.displayName()));
                meta.lore(List.of(
                        Text.mm("<gray>Players: <white>" + arena.minPlayers() + "-" + arena.maxPlayers()),
                        Text.mm("<green>Click to queue on this map!")));
            });
            int slot = slots.get(i + 1);
            inv.setItem(slot, item);
            holder.maps.put(slot, arena.id());
        }
        plugin.menuFx().open(player, inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;
        int slot = event.getSlot();

        GameType game = holder.games.get(slot);
        if (game != null) {
            openGame(player, game);
            return;
        }
        GameMode mode = holder.modes.get(slot);
        if (mode != null && holder.game != null) {
            afterModeChosen(player, holder.game, mode);
            return;
        }
        if (holder.mode != null && slot == holder.randomSlot) {
            player.closeInventory();
            plugin.playService().join(player, holder.game, holder.mode);
            return;
        }
        String arenaId = holder.maps.get(slot);
        if (arenaId != null && holder.mode != null) {
            player.closeInventory();
            plugin.playService().join(player, holder.game, holder.mode, arenaId);
        }
    }

    /** Multiple maps -> offer map selection; otherwise queue straight away. */
    private void afterModeChosen(Player player, GameType game, GameMode mode) {
        if (plugin.arenas().forGame(game.id(), mode.id()).size() > 1) {
            openMaps(player, game, mode);
        } else {
            player.closeInventory();
            plugin.playService().join(player, game, mode);
        }
    }
}
