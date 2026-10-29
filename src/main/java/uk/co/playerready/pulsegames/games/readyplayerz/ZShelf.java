package uk.co.playerready.pulsegames.games.readyplayerz;

import org.bukkit.Material;

import java.util.List;

/**
 * Which Supply Shop lines sit on which tab. Numbers are indexes into {@link ReadyPlayerZGame#UPGRADES}, or the
 * special box / Pack-a-Punch slots, so the shop and the bots buy through the same {@code purchase(slot)}.
 */
enum ZShelf {
    WEAPONS("Weapons", Material.IRON_SWORD, List.of(0, 1, 2, 3, 8, 4)),
    GEAR("Gear", Material.IRON_CHESTPLATE, List.of(5, 6)),
    PERKS("Perks", Material.SUGAR, List.of(7, 9, 10)),
    SPECIAL("Specials", Material.ENDER_CHEST, List.of(ReadyPlayerZGame.BOX_SLOT, ReadyPlayerZGame.PAP_SLOT));

    final String title;
    final Material icon;
    final List<Integer> slots;

    ZShelf(String title, Material icon, List<Integer> slots) {
        this.title = title;
        this.icon = icon;
        this.slots = slots;
    }

    /** Slots that hand over an item and so need a free bag space. */
    static boolean needsBagSpace(int slot) { return slot >= 0 && slot <= 4; }
}
