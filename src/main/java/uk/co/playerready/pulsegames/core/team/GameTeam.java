package uk.co.playerready.pulsegames.core.team;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class GameTeam {

    public record Palette(String name, String chatColor, NamedTextColor color, Material wool) {}

    public static final Palette[] PALETTES = {
            new Palette("Red", "<red>", NamedTextColor.RED, Material.RED_WOOL),
            new Palette("Blue", "<blue>", NamedTextColor.BLUE, Material.BLUE_WOOL),
            new Palette("Green", "<green>", NamedTextColor.GREEN, Material.LIME_WOOL),
            new Palette("Yellow", "<yellow>", NamedTextColor.YELLOW, Material.YELLOW_WOOL),
            new Palette("Aqua", "<aqua>", NamedTextColor.AQUA, Material.CYAN_WOOL),
            new Palette("Pink", "<light_purple>", NamedTextColor.LIGHT_PURPLE, Material.PINK_WOOL),
            new Palette("White", "<white>", NamedTextColor.WHITE, Material.WHITE_WOOL),
            new Palette("Gray", "<gray>", NamedTextColor.GRAY, Material.GRAY_WOOL),
    };

    private final int index;
    private final Palette palette;
    private final Set<UUID> members = new LinkedHashSet<>();

    public GameTeam(int index) {
        this.index = index;
        this.palette = PALETTES[index % PALETTES.length];
    }

    public int index() { return index; }
    public Palette palette() { return palette; }
    public String coloredName() { return palette.chatColor() + palette.name(); }
    public Set<UUID> members() { return members; }

    public void add(Player player) { members.add(player.getUniqueId()); }
    public boolean contains(Player player) { return members.contains(player.getUniqueId()); }
    public boolean contains(UUID uuid) { return members.contains(uuid); }
}
