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

    public enum Category {
        KILL_EFFECT("Kill Effects", Material.IRON_SWORD),
        WIN_EFFECT("Victory Effects", Material.FIREWORK_ROCKET),
        TRAIL("Lobby Trails", Material.BLAZE_POWDER),
        EMOTE("Spectator Reactions", Material.FIREWORK_STAR),
        KIT("Kit Unlocks", Material.CHEST);

        public final String displayName;
        public final Material icon;

        Category(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }
    }
}
