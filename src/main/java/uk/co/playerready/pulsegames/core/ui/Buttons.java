package uk.co.playerready.pulsegames.core.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/** Item builders for menus: every name and lore line is non-italic. */
public final class Buttons {
    private Buttons() { }

    public static ItemStack item(Material material, String nameMiniMessage, List<Component> lore) {
        return item(material, Theme.mm(nameMiniMessage), lore);
    }

    public static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Theme.plain(name));
            if (!lore.isEmpty()) meta.lore(lore.stream().map(Theme::plain).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return stack;
    }

    public static ItemStack item(Material material, String nameMiniMessage, Lore lore) {
        return item(material, nameMiniMessage, lore.build());
    }

    /** Border/spacer pane with no tooltip at all on Java (Bedrock may still show a blank one). */
    public static ItemStack filler(Material pane) {
        ItemStack stack = new ItemStack(pane);
        stack.editMeta(meta -> {
            meta.displayName(Component.space());
            meta.setHideTooltip(true);
        });
        return stack;
    }

    public static ItemStack filler() { return filler(Material.GRAY_STAINED_GLASS_PANE); }

    public static ItemStack glow(ItemStack stack) {
        stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return stack;
    }

    /** Stack size as a glanceable count (1-64); zero shows as a single item. */
    public static ItemStack count(ItemStack stack, int amount) {
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        return stack;
    }

    public static ItemStack head(Player player, String nameMiniMessage, List<Component> lore) {
        ItemStack stack = item(Material.PLAYER_HEAD, nameMiniMessage, lore);
        stack.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
        return stack;
    }

    public static ItemStack back() {
        return item(Material.ARROW, Theme.GOOD + "Back", Lore.of().hint("Click to go back"));
    }

    public static ItemStack close() {
        return item(Material.BARRIER, Theme.BAD + "Close", Lore.of().hint("Click to close"));
    }

    public static ItemStack prev(int page, int pages) {
        return item(Material.SPECTRAL_ARROW, Theme.GOOD + "Previous page",
                Lore.of().kv("Page", page + "/" + pages).hint("Click for the last page"));
    }

    public static ItemStack next(int page, int pages) {
        return item(Material.SPECTRAL_ARROW, Theme.GOOD + "Next page",
                Lore.of().kv("Page", page + "/" + pages).hint("Click for the next page"));
    }

    public static ItemStack pageIndicator(int page, int pages) {
        return item(Material.PAPER, Theme.TEXT + "Page <white>" + page + "/" + pages, List.of());
    }
}
