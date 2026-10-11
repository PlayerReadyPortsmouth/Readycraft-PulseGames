package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.kit.Kit;
import uk.co.playerready.pulsegames.core.util.Text;
import java.util.*;
import java.util.function.Consumer;

/** Combat rules, discipline, team size and personal loadout are independent choices. */
public final class DuelMenu implements Listener {
    private final PulseGamesPlugin plugin;
    public DuelMenu(PulseGamesPlugin plugin) { this.plugin = plugin; }
    private static final class Screen implements InventoryHolder {
        Inventory inventory;
        final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        public Inventory getInventory() { return inventory; }
    }
    private Screen screen(String title) {
        var s = new Screen(); s.inventory = Bukkit.createInventory(s, 27, Text.mm("<dark_gray>" + title)); return s;
    }
    private void button(Screen s, int slot, Material icon, String name, Consumer<Player> action, String... lore) {
        var item = new ItemStack(icon);
        item.editMeta(meta -> { meta.displayName(Text.mm(name)); meta.lore(Arrays.stream(lore).map(Text::mm).toList()); });
        s.inventory.setItem(slot, item); if (action != null) s.actions.put(slot, action);
    }
    public void open(Player player) {
        var current = plugin.instances().byPlayer(player);
        if (current != null && current.type().id().equals("duels") && current.mode().combatStyle() != GameMode.CombatStyle.DEFAULT)
            loadouts(player, current.mode());
        else styles(player);
    }
    public void styles(Player player) {
        var s = screen("Duels · Combat style");
        boolean classic = plugin.combat() != null && plugin.combat().available();
        button(s, 11, Material.IRON_SWORD, "<yellow>Classic · 1.8-style", p -> {
            if (classic) disciplines(p, GameMode.CombatStyle.CLASSIC);
            else p.sendMessage(Text.msg("<red>Classic combat is not available on this server yet."));
        }, classic ? "<gray>Classic combat rules" : "<red>Combat provider not ready", "<gray>Choose the discipline next");
        button(s, 15, Material.DIAMOND_SWORD, "<aqua>Modern · 1.9+", p -> disciplines(p, GameMode.CombatStyle.MODERN),
                "<gray>Modern attack cooldown and combat", "<gray>Regular PvP, Sumo, Crystal or Mace");
        player.openInventory(s.inventory);
    }
    private void disciplines(Player player, GameMode.CombatStyle style) {
        var s = screen("Duels · Discipline");
        String[] ids = style == GameMode.CombatStyle.CLASSIC ? new String[]{"classic", "sumo"} : new String[]{"classic", "sumo", "crystal", "mace"};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            Material icon = switch(id) { case "sumo" -> Material.LEAD; case "crystal" -> Material.END_CRYSTAL; case "mace" -> Material.MACE; default -> Material.IRON_SWORD; };
            button(s, 10 + i * 2, icon, "<yellow>" + disciplineName(id), p -> match(p, mode(style, id, 1)),
                    "<gray>Kit is chosen separately", id.equals("crystal") || id.equals("mace") ? "<gray>Requires an approved map for this discipline" : "<gray>Choose solo, doubles, triples or squads");
        }
        button(s, 18, Material.ARROW, "<gray>Back", this::styles); player.openInventory(s.inventory);
    }
    public GameMode mode(GameMode.CombatStyle style, String discipline, int size) {
        String suffix = switch(size) { case 2 -> "-doubles"; case 3 -> "-triples"; case 4 -> "-squads"; default -> ""; };
        return plugin.registry().get("duels").mode((style == GameMode.CombatStyle.CLASSIC ? "legacy-" : "modern-") + discipline + suffix);
    }
    private String profile(GameMode mode) { return mode.combatStyle().name().toLowerCase(Locale.ROOT) + "." + mode.rulesId(); }
    private boolean usable(Player player, Kit kit) {
        if (player.hasMetadata("NPC")) return true;
        return (!kit.id().equals("tank") && !kit.id().equals("archer")) || plugin.economy().isUnlocked(player, "kit-" + kit.id());
    }
    public Kit kitFor(Player player, GameMode mode) {
        var choices = plugin.kits().duelChoices(mode).stream().filter(k -> usable(player, k)).toList();
        String selected = plugin.economy().duelKit(player, profile(mode));
        return choices.stream().filter(k -> k.id().equals(selected)).findFirst().orElse(choices.isEmpty() ? null : choices.getFirst());
    }
    public boolean selectKit(Player player, GameMode mode, String kitId) {
        var kit = plugin.kits().duelChoices(mode).stream().filter(k -> k.id().equals(kitId) && usable(player, k)).findFirst().orElse(null);
        if (kit == null) return false;
        plugin.economy().duelKit(player, profile(mode), kit.id()); return true;
    }
    private void match(Player player, GameMode mode) {
        var s = screen("Duels · " + disciplineName(mode.rulesId()));
        for (int size = 1; size <= 4; size++) {
            int selectedSize = size;
            button(s, 9 + size * 2, Material.PAPER, (mode.teamSize() == size ? "<green>" : "<yellow>") + size + "v" + size,
                    p -> match(p, mode(mode.combatStyle(), mode.rulesId(), selectedSize)), "<gray>Team size · kits remain individual");
        }
        var kit = kitFor(player, mode);
        button(s, 4, Material.CHEST, "<aqua>Your kit", p -> loadouts(p, mode), kit == null ? "<red>No compatible kit available" : kit.displayName(),
                "<gray>Your opponent may choose a different kit", "<gray>Changes apply on the next round");
        button(s, 22, Material.LIME_DYE, "<green>Queue " + mode.teamSize() + "v" + mode.teamSize(), p -> {
            p.closeInventory(); plugin.playService().join(p, plugin.registry().get("duels"), mode);
        }, "<gray>" + (mode.combatStyle() == GameMode.CombatStyle.CLASSIC ? "Classic 1.8-style" : "Modern 1.9+") + " · " + disciplineName(mode.rulesId()),
                "<gray>Finds players with the same rules and team size");
        button(s, 18, Material.ARROW, "<gray>Back", p -> disciplines(p, mode.combatStyle()));
        player.openInventory(s.inventory);
    }
    private void loadouts(Player player, GameMode mode) {
        var s = screen("Duels · Your loadout");
        var current = kitFor(player, mode); int slot = 0;
        for (var kit : plugin.kits().duelChoices(mode)) {
            if (slot >= 18) break;
            boolean owned = usable(player, kit);
            button(s, slot++, kit.icon(), kit.displayName(), p -> {
                if (!selectKit(p, mode, kit.id())) p.sendMessage(Text.msg("<red>This kit is locked or no longer compatible."));
                else p.sendMessage(Text.msg("<green>Your Duels kit is saved for the next round. Your opponent keeps their own choice."));
                loadouts(p, mode);
            }, !owned ? "<gold>Unlock this kit in the token shop" : current != null && current.id().equals(kit.id()) ? "<green>Selected" : "<gray>Click to select",
                    "<gray>Only for this combat style and discipline", "<gray>Applies at the next round; no instant refill");
        }
        button(s, 18, Material.ARROW, "<gray>Back", p -> match(p, mode));
        player.openInventory(s.inventory);
    }
    private static String disciplineName(String id) { return switch(id) { case "classic" -> "Regular PvP"; case "sumo" -> "Sumo"; case "crystal" -> "Crystal"; case "mace" -> "Mace"; default -> id; }; }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Screen s)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != s.inventory) return;
        var action = s.actions.get(event.getSlot());
        if (action != null) plugin.getServer().getScheduler().runTask(plugin, () -> { if (player.isOnline()) action.accept(player); });
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Screen) event.setCancelled(true);
    }
}
