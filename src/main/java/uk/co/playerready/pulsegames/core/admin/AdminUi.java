package uk.co.playerready.pulsegames.core.admin;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Confirm;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Paginator;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.ui.forms.FormLayout;
import uk.co.playerready.pulsegames.core.ui.forms.Forms;

import java.util.List;
import java.util.function.Consumer;

/** Draws an {@link AdminScreen} entry list on whichever platform the admin is on. */
final class AdminUi {
    private static final int PER_PAGE = 36;

    private final Plugin plugin;

    AdminUi(Plugin plugin) { this.plugin = plugin; }

    /** @param back null for the top screen */
    void show(Player player, String title, String intro, List<AdminScreen.Entry> entries, Consumer<Player> back) {
        List<FormLayout.Button> buttons = AdminScreen.buttons(entries, back != null);
        if (Forms.list(plugin, player, "Pulse - " + title, intro, buttons, target -> {
            int i = AdminScreen.indexOf(target);
            if (i >= 0 && i < entries.size() && entries.get(i).action() != null) entries.get(i).action().accept(player);
            else if (FormLayout.BACK.equals(target) && back != null) back.accept(player);
            else if (i >= 0) show(player, title, intro, entries, back);
        })) return;
        int[] page = {0};
        Menu menu = new Menu(plugin, player, Theme.title(title), 6);
        menu.renderer(() -> {
            Paginator.Page<AdminScreen.Entry> view = Paginator.page(entries, PER_PAGE, page[0]);
            page[0] = view.page();
            int slot = 0;
            for (AdminScreen.Entry e : view.items()) {
                Lore lore = Lore.of();
                e.lines().forEach(lore::desc);
                if (e.action() != null) lore.hint("Click to open");
                menu.set(slot++, Buttons.item(e.icon(), e.name(), lore), e.action());
            }
            menu.pager(view, () -> { page[0]--; menu.refresh(); }, () -> { page[0]++; menu.refresh(); });
            if (back != null) menu.back(back);
            menu.close();
            menu.fillEmpty(org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
        });
        menu.open(player);
    }

    /** Yes/no on the player's platform; No simply goes {@code back}. */
    void confirm(Player player, String title, List<String> summary, boolean danger, Consumer<Player> yes, Consumer<Player> back) {
        String body = String.join("\n", summary.stream().map(uk.co.playerready.pulsegames.core.util.Text::plain).toList());
        if (Forms.confirm(plugin, player, "Pulse - " + title, body, "Yes", "No, go back", () -> yes.accept(player), () -> back.accept(player))) return;
        Confirm.ask(plugin, player, title, summary, danger, yes, back);
    }
}
