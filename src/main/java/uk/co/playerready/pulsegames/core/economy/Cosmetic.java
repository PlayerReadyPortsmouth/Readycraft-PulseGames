package uk.co.playerready.pulsegames.core.economy;

import org.bukkit.Material;

/** A purchasable unlock in the token shop. */
public record Cosmetic(String id, Category category, String name, Material icon, int price, String description,
                       int level, EffectSpec effect) {

    /** A token-priced item whose effect is coded in {@link CosmeticsService}. */
    public Cosmetic(String id, Category category, String name, Material icon, int price, String description) {
        this(id, category, name, icon, price, description, 0, null);
    }

    /** Unlocked by reaching a level instead of by paying tokens. */
    public boolean levelUnlock() { return level > 0; }

    /** Prefix of a name-style cosmetic id; the rest is the network style id ({@code ns-red} is {@code red}). */
    public static final String NAME_STYLE_PREFIX = "ns-";

    /** The network style id for a name-style cosmetic, or null for any other cosmetic. */
    public String nameStyleId() {
        return category == Category.NAME_STYLE && id.startsWith(NAME_STYLE_PREFIX) ? id.substring(NAME_STYLE_PREFIX.length()) : null;
    }

    public enum Category {
        KILL_EFFECT("Kill Effects", Material.IRON_SWORD),
        WIN_EFFECT("Victory Effects", Material.FIREWORK_ROCKET),
        TRAIL("Lobby Trails", Material.BLAZE_POWDER),
        EMOTE("Spectator Reactions", Material.FIREWORK_STAR),
        KIT("Kit Unlocks", Material.CHEST),
        /** Level rewards that unlock a network name style; picked with /namestyle, not equipped here. */
        NAME_STYLE("Name Styles", Material.NAME_TAG);

        public final String displayName;
        public final Material icon;

        Category(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }
    }
}
