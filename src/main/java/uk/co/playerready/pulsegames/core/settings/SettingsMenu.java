package uk.co.playerready.pulsegames.core.settings;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Theme;

/** Small "My settings" menu: party invites plus the {@link Comfort} options. Toggles refresh in place. */
public final class SettingsMenu {
    static final int PARTY_SLOT = 10;
    private static final int FIRST_COMFORT_SLOT = 12;

    private final PulseGamesPlugin plugin;
    private final Runnable back;

    /** @param back what the Back button does (null = no Back button). */
    public SettingsMenu(PulseGamesPlugin plugin, Runnable back) {
        this.plugin = plugin;
        this.back = back;
    }

    public void open(Player player) {
        Menu menu = new Menu(plugin, player, Theme.title("My settings"), 3);
        menu.renderer(() -> render(menu, player));
        menu.open(player);
    }

    private void render(Menu menu, Player player) {
        boolean invites = plugin.parties().acceptsInvites(player);
        menu.set(PARTY_SLOT, toggle(Material.PLAYER_HEAD, "Party invites", invites,
                        "Lets friends invite you to a party. Turn it off and nobody can."),
                p -> { plugin.parties().setAcceptsInvites(p, !plugin.parties().acceptsInvites(p)); menu.refresh(); });
        int slot = FIRST_COMFORT_SLOT;
        for (Comfort option : Comfort.values()) {
            menu.set(slot++, toggle(option.icon(), option.title(), PlayerSettings.enabled(player, option), option.description()),
                    p -> { PlayerSettings.set(p, option, !PlayerSettings.enabled(p, option)); menu.refresh(); });
        }
        if (back != null) menu.back(p -> back.run());
        menu.close();
        menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private static org.bukkit.inventory.ItemStack toggle(Material icon, String title, boolean on, String description) {
        var item = Buttons.item(icon, Theme.NAME + title,
                Lore.of().status(on ? Theme.GOOD + "On" : Theme.BAD + "Off").desc(description).blank().hint("Click to switch"));
        return on ? Buttons.glow(item) : item;
    }
}
