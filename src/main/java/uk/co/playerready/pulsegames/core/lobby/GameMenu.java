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
        var counts = plugin.quickPlay().counts();
        String featured = plugin.quickPlay().featuredId();
        for (GameType game : games) {
            ItemStack item = new ItemStack(game.icon());
            item.editMeta(meta -> {
                meta.displayName(Text.mm("<yellow><b>" + game.displayName() + (!plugin.gameAvailable(game) ? " <red>(soon)" : "")));
                List<Component> lore = new ArrayList<>();
                lore.add(Text.mm("<gray>" + game.description()));
                lore.add(Text.mm(""));
                String tag = uk.co.playerready.pulsegames.games.GameTags.of(game.id());
                if (tag != null) lore.add(Text.mm("<aqua>" + tag));
                var count = counts.getOrDefault(game.id(), new QueueCounts.Count(0, 0));
                lore.add(Text.mm("<gray>Now: <white>" + count.waiting() + " waiting / " + count.playing() + " playing"));
                if (game.id().equals(featured))
                    lore.add(Text.mm("<gold>Featured today: x" + plugin.quickPlay().multiplier() + " tokens"));
                lore.add(Text.mm("<gray>Modes: <white>" + game.modes().size()));
                lore.add(Text.mm(!plugin.gameAvailable(game) ? "<red>Coming soon!" : "<green>Click to play!"));
                meta.lore(lore);
            });
            inv.setItem(slot++, item);
        }
        addKitButton(holder, inv);
        ItemStack party = new ItemStack(Material.PLAYER_HEAD);
        party.editMeta(meta -> {
            meta.displayName(Text.mm("<aqua>Play with a party"));
            meta.lore(List.of(Text.mm("<gray>Invite friends with /party invite [name]"),
                    Text.mm("<gray>Your leader queues everyone together"),
                    Text.mm("<yellow>Click for party commands")));
        });
        inv.setItem(inv.getSize() - 2, party);
        inv.setItem(inv.getSize() - 3, button(Material.GOLDEN_HELMET, "<gold><b>Weekly top 3</b>", "Who is winning this week"));
        inv.setItem(inv.getSize() - 4, button(Material.WRITABLE_BOOK, "<yellow><b>Daily challenges</b>", "Earn bonus tokens"));
        inv.setItem(inv.getSize() - 5, button(Material.ENDER_EYE, "<green><b>Quick Play</b>", "Join the busiest game now"));
        player.openInventory(inv);
    }

    private ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> { meta.displayName(Text.mm(name)); meta.lore(List.of(Text.mm("<gray>" + lore), Text.mm("<yellow>Click!"))); });
        return item;
    }

    public void openModes(Player player, GameType game) {
        if (game.id().equals("duels")) { plugin.duelMenu().open(player); return; }
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
        addKitButton(holder, inv);
        player.openInventory(inv);
    }

    private void addKitButton(MenuHolder holder, Inventory inv) {
        if (holder.gameOrNull != null && !holder.gameOrNull.id().equals("kitpvp")) return;
        var kits = new ItemStack(Material.CHEST);
        kits.editMeta(meta -> { meta.displayName(Text.mm("<aqua>Choose KitPvP loadout"));
            meta.lore(List.of(Text.mm("<gray>Select your default or owned kit"), Text.mm("<gray>Applies at your next KitPvP spawn"))); });
        inv.setItem(holder.gameOrNull == null ? inv.getSize() - 1 : 4, kits);
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
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
            int fromEnd = event.getInventory().getSize() - event.getSlot();
            if (fromEnd == 3) { player.closeInventory(); plugin.engagement().top(player, new String[0]); return; }
            if (fromEnd == 4) { player.closeInventory(); plugin.engagement().openChallenges(player); return; }
            if (fromEnd == 5) { player.closeInventory(); plugin.quickPlay().quickPlay(player); return; }
        }
        if (holder.gameOrNull == null && event.getSlot() == event.getInventory().getSize() - 2) {
            player.closeInventory();
            player.sendMessage(Text.msg("<yellow>/party invite [name]</yellow> — invite a friend (network-wide when PulseNetwork is active)"));
            player.sendMessage(Text.msg("<yellow>/party accept</yellow>, <yellow>/party list</yellow>, <yellow>/party leave</yellow>"));
            player.sendMessage(Text.msg("Your party leader selects the game. Parties may face each other in solo modes."));
            return;
        }
        if ((holder.gameOrNull == null && event.getSlot() == event.getInventory().getSize() - 1)
                || (holder.gameOrNull != null && holder.gameOrNull.id().equals("kitpvp") && event.getSlot() == 4)) {
            plugin.kitSelection().open(player); return;
        }
        if (holder.gameOrNull == null) {
            List<GameType> games = new ArrayList<>(plugin.registry().all());
            int index = event.getSlot();
            if (index >= games.size()) return;
            GameType game = games.get(index);
            if (!plugin.gameAvailable(game)) {
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
