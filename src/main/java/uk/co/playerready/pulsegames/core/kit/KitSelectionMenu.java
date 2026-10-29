package uk.co.playerready.pulsegames.core.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Paginator;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.economy.Cosmetic;
import uk.co.playerready.pulsegames.core.economy.CosmeticsService;
import uk.co.playerready.pulsegames.core.util.Text;
import java.util.*;

/** Selection only: never purchases a kit or replaces a fighting player's inventory. */
public final class KitSelectionMenu {
    private final PulseGamesPlugin plugin;
    public KitSelectionMenu(PulseGamesPlugin plugin) { this.plugin = plugin; }
    public boolean select(Player player, String cosmeticId) {
        if (cosmeticId != null) {
            var choice = CosmeticsService.byCategory(Cosmetic.Category.KIT).stream()
                    .filter(c -> c.id().equals(cosmeticId)).findFirst().orElse(null);
            if (choice == null || !plugin.economy().isUnlocked(player, cosmeticId)
                    || plugin.kits().get(cosmeticId.substring("kit-".length())) == null) return false;
        }
        plugin.economy().equip(player, Cosmetic.Category.KIT, cosmeticId);
        return true;
    }
    /** Bounded, read-only preview; selecting a kit never applies these items immediately. */
    public static List<String> contents(Kit kit) {
        List<String> result = new ArrayList<>();
        List<ItemStack> armor = Arrays.asList(kit.helmet(), kit.chestplate(), kit.leggings(), kit.boots());
        String worn = armor.stream().filter(Objects::nonNull).map(item -> item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '))
                .collect(java.util.stream.Collectors.joining(", "));
        result.add("<gray>Armour: " + (worn.isEmpty() ? "none" : worn));
        List<ItemStack> items = kit.items() == null ? List.of() : kit.items();
        for (ItemStack item : items.stream().filter(Objects::nonNull).limit(6).toList())
            result.add("<gray>" + item.getAmount() + " × " + item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (items.size() > 6) result.add("<gray>And " + (items.size() - 6) + " more item stacks");
        return List.copyOf(result);
    }
    private static final int PER_PAGE = 5;

    public void open(Player player) { open(player, 0); }
    private void open(Player player, int requestedPage) {
        var choices = CosmeticsService.byCategory(Cosmetic.Category.KIT);
        int[] page = {requestedPage};
        Menu menu = new Menu(plugin, player, Theme.title("KitPvP loadout"), 3);
        menu.renderer(() -> {
            var shown = Paginator.page(choices, PER_PAGE, page[0]);
            page[0] = shown.page();
            String equipped = plugin.economy().equipped(player, Cosmetic.Category.KIT);
            menu.set(10, Buttons.item(Material.IRON_SWORD, "<yellow>Map default", Lore.of()
                    .line(equipped == null ? "<green>Selected" : "<gray>Click to select the arena's default kit")
                    .line("<gray>Applies at your next KitPvP spawn").line("<gray>One in the Chamber uses its own fixed kit")),
                    p -> choose(p, menu, null));
            int slot = 12;
            for (var choice : shown.items()) {
                boolean owned = plugin.economy().isUnlocked(player, choice.id());
                boolean available = plugin.kits().get(choice.id().substring(4)) != null;
                Lore lore = Lore.of().line("<gray>" + choice.description())
                        .line(!available ? "<red>Loadout unavailable on this server" : choice.id().equals(equipped) && owned ? "<green>Selected"
                                : owned ? "<green>Click to select" : "<gold>Locked: " + choice.price() + " tokens · view in shop");
                if (available) contents(plugin.kits().get(choice.id().substring(4))).forEach(lore::line);
                lore.line("<gray>Applies at your next KitPvP spawn");
                menu.set(slot++, Buttons.item(choice.icon(), "<yellow>" + choice.name(), lore), p -> choose(p, menu, choice.id()));
            }
            menu.set(22, Buttons.item(Material.SUNFLOWER, "<gold>Browse kit unlocks", Lore.of().line("<gray>Open the token shop")),
                    p -> plugin.tokenShop().openCategory(p, Cosmetic.Category.KIT));
            // Kit row layout is fixed: previous 18, next 26 (same as before the kit rewrite).
            if (shown.hasPrev()) menu.set(18, Buttons.item(Material.ARROW, "<gray>Previous kits", Lore.of()), p -> { page[0]--; menu.refresh(); });
            if (shown.hasNext()) menu.set(26, Buttons.item(Material.ARROW, "<gray>Next kits", Lore.of()), p -> { page[0]++; menu.refresh(); });
        });
        menu.open(player);
    }

    private void choose(Player player, Menu menu, String id) {
        if (!select(player, id)) {
            player.sendMessage(Text.msg("<yellow>This kit needs an unlock and an available loadout. View it in the token shop."));
            return;
        }
        player.sendMessage(Text.msg("<green>Kit selected for your next KitPvP spawn. <gray>One in the Chamber keeps its fixed loadout."));
        menu.refresh();
    }
}
