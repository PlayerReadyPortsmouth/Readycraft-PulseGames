package uk.co.playerready.pulsegames.core.settings;

import org.bukkit.Material;

/** Per-player comfort options kept in the player's own data. Every one is off until a player turns it on. */
public enum Comfort {
    HIDE_REACTIONS("hide-reactions", "Hide spectator reactions", Material.PUFFERFISH,
            "Spectators' hearts, sparkles and cheers will not show or sound for you."),
    REDUCE_FLASHES("reduce-flashes", "Fewer flashes", Material.SEA_LANTERN,
            "Skips lightning and firework bursts in rounds you are in. Quiet sparks show instead."),
    COLOUR_PATTERNS("colour-patterns", "Colour patterns in Block Party", Material.CYAN_GLAZED_TERRACOTTA,
            "Block Party floors use patterns as well as colours. It applies to everyone in your round.");

    private final String id;
    private final String title;
    private final Material icon;
    private final String description;

    Comfort(String id, String title, Material icon, String description) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.description = description;
    }

    public String id() { return id; }
    public String title() { return title; }
    public Material icon() { return icon; }
    public String description() { return description; }
    public boolean defaultValue() { return false; }
}
