package uk.co.playerready.pulsegames.core.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * "Are you sure?" window: Yes on the left, the summary in the middle, No on the right (see {@link ConfirmLayout}).
 * Both answers run next tick through {@link MenuListener}, so they may open or close windows freely. Yes can only fire
 * once, so a double-click cannot spend twice. Closing the window with Escape does nothing.
 */
public final class Confirm {
    private Confirm() { }

    /**
     * @param summary MiniMessage lines; the first becomes the item name, the rest its description
     * @param danger  colour Yes orange and say so, for spends and other things that cannot be taken back
     */
    public static void ask(Plugin plugin, Player player, String title, List<String> summary, boolean danger,
                           Consumer<Player> onYes, Consumer<Player> onNo) {
        List<String> lines = ConfirmLayout.summary(summary);
        AtomicBoolean answered = new AtomicBoolean();
        Menu menu = new Menu(plugin, player, Theme.title(title), ConfirmLayout.ROWS);
        menu.renderer(() -> {
            Lore lore = Lore.of();
            lines.stream().skip(1).forEach(lore::line);
            menu.set(ConfirmLayout.SUMMARY, Buttons.item(Material.PAPER, lines.isEmpty() ? Theme.TEXT + "Are you sure?" : lines.getFirst(), lore));
            menu.set(ConfirmLayout.YES, Buttons.item(ConfirmLayout.yesMaterial(danger), Theme.GOOD + "<b>Yes",
                    Lore.of().hint(danger ? "Click to confirm" : "Click to say yes")), p -> {
                if (answered.compareAndSet(false, true)) onYes.accept(p);
            });
            menu.set(ConfirmLayout.NO, Buttons.item(Material.RED_CONCRETE, Theme.BAD + "<b>No, go back",
                    Lore.of().hint("Nothing will change")), p -> {
                if (answered.compareAndSet(false, true)) onNo.accept(p);
            });
        });
        menu.open(player);
    }
}
