package uk.co.playerready.pulsegames.core.ui.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Theme;

import java.util.List;
import java.util.function.IntFunction;

import static uk.co.playerready.pulsegames.core.ui.shop.ShopRules.Status;
import static uk.co.playerready.pulsegames.core.ui.shop.ShopRules.Verdict;

/**
 * Builds shop icons. Status is always words first ("Need 3 more iron", "You already have this", "Active now")
 * and colour second, so it reads for colour-blind players and on a Bedrock phone. Names and lore are
 * non-italic and wrapped.
 */
public final class ShopTiles {
    private ShopTiles() { }

    /** Everything a priced tile says. {@code unit} turns a count into "iron" / "diamonds" / "points". */
    public record Offer(String name, String blurb, int cost, IntFunction<String> unit, int have, Verdict verdict,
                        boolean active, String buyText) {
        public Offer(String name, String blurb, int cost, IntFunction<String> unit, int have, Verdict verdict) {
            this(name, blurb, cost, unit, have, verdict, false, "Click to buy");
        }
    }

    /** Dress {@code base} (which may already carry potion data) as a priced tile. */
    public static ItemStack offer(ItemStack base, Offer o) {
        Lore lore = Lore.of().line(status(o.verdict(), o.unit(), o.buyText(), "You already have this", "You already have a better one"));
        if (o.active()) lore.line(Theme.ACCENT + "Active now");
        if (o.blurb() != null && !o.blurb().isBlank()) lore.desc(o.blurb());
        lore.line("<gray>Cost: <yellow>" + o.cost() + " " + o.unit().apply(o.cost()));
        lore.line("<gray>You have: <white>" + o.have() + " " + o.unit().apply(o.have()));
        String suffix = switch (o.verdict().status()) {
            case CAN_BUY -> "";
            case NEED_MORE -> " - " + ShopRules.needMore(o.verdict().shortfall(), o.unit().apply(o.verdict().shortfall())).toLowerCase();
            case OWNED_THIS -> " - you have this";
            case OWNED_BETTER -> " - you have better";
        };
        if (o.active()) suffix += " - active";
        return dress(base, colour(o.verdict()) + "<b>" + o.name() + suffix, lore.build(), o.active());
    }

    /** A pick-one tile with no price (a Quake weapon, an ability): "Selected" is text and a glint. */
    public static ItemStack choice(Material icon, String name, String blurb, boolean selected, String pickText) {
        Lore lore = Lore.of().line(selected ? Theme.GOOD + "Selected" : Theme.GOOD + pickText);
        if (blurb != null && !blurb.isBlank()) lore.desc(blurb);
        return dress(new ItemStack(icon), (selected ? "<green><b>" : "<yellow><b>") + name + (selected ? " - selected" : ""),
                lore.build(), selected);
    }

    public static ItemStack tab(Material icon, String title, boolean selected) {
        return dress(new ItemStack(icon), (selected ? "<gold><b>" : "<yellow>") + title,
                Lore.of().line(selected ? "<gold>Showing now" : "<green>Click to open").build(), selected);
    }

    public static ItemStack info(Material icon, String name, Lore lore) {
        return dress(new ItemStack(icon), name, lore.build(), false);
    }

    public static String status(Verdict verdict, IntFunction<String> unit, String buyText, String ownedThis, String ownedBetter) {
        return switch (verdict.status()) {
            case CAN_BUY -> "<green>" + buyText;
            case NEED_MORE -> "<red>" + ShopRules.needMore(verdict.shortfall(), unit.apply(verdict.shortfall()));
            case OWNED_THIS -> "<gray>" + ownedThis;
            case OWNED_BETTER -> "<gray>" + ownedBetter;
        };
    }

    public static String colour(Verdict verdict) {
        return switch (verdict.status()) { case CAN_BUY -> "<green>"; case NEED_MORE -> "<red>"; default -> "<gray>"; };
    }

    /** Non-italic name and lore, no vanilla attribute lines, optional glint. */
    public static ItemStack dress(ItemStack stack, String nameMm, List<Component> lore, boolean glint) {
        stack.editMeta(meta -> {
            meta.displayName(Theme.mm(nameMm));
            meta.lore(lore.stream().map(Theme::plain).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
            if (glint) meta.setEnchantmentGlintOverride(true);
        });
        return stack;
    }
}
