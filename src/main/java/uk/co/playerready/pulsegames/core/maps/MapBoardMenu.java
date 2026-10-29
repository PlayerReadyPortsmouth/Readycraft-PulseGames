package uk.co.playerready.pulsegames.core.maps;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Paginator;
import uk.co.playerready.pulsegames.core.ui.Theme;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Admin-only, read-only readiness dashboard. Marks still go through {@code /pulse maps mark}. */
public final class MapBoardMenu {
    private static final int GAMES_PER_PAGE = 28, MAPS_PER_PAGE = 4, FILTER_SLOT = 8;
    private final Plugin plugin;
    private final Player viewer;
    private final Map<String, String> gameNames;       // id -> display name (ordered)
    private final Map<String, Material> gameIcons;
    private final Function<String, List<MapBoardModel.Row>> rows;
    private String game;       // null = game list
    private boolean onlyMissing;
    private int page;
    private final Menu menu;

    public MapBoardMenu(Plugin plugin, Player viewer, Map<String, String> gameNames, Map<String, Material> gameIcons,
                        Function<String, List<MapBoardModel.Row>> rows) {
        this.plugin = plugin; this.viewer = viewer; this.gameNames = gameNames; this.gameIcons = gameIcons; this.rows = rows;
        this.menu = new Menu(plugin, viewer, Theme.title("Map readiness"), 6);
        menu.renderer(this::render);
    }

    public void open() { menu.open(viewer); }

    private void go(String newGame, int newPage) { game = newGame; page = newPage; menu.refresh(); }

    private void render() {
        if (game == null) renderGames(); else renderMaps();
        menu.close().fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private void renderGames() {
        var ids = List.copyOf(gameNames.keySet());
        var pg = Paginator.page(ids, GAMES_PER_PAGE, page);
        int i = 0;
        for (String id : pg.items()) {
            int slot = 10 + (i / 7) * 9 + (i % 7); i++;
            var list = rows.apply(id);
            long missing = list.stream().filter(MapBoardModel.Row::missingEvidence).count();
            var lore = Lore.of().line(Theme.TEXT + MapBoardModel.summary(list))
                    .line((missing == 0 ? Theme.GOOD + "✔ " : Theme.BAD + "✘ ") + missing + " with missing evidence")
                    .blank().hint("Click to see maps");
            menu.set(slot, Buttons.item(gameIcons.getOrDefault(id, Material.PAPER), Theme.NAME + gameNames.get(id), lore),
                    p -> go(id, 0));
        }
        menu.pager(pg, () -> go(null, pg.page() - 1), () -> go(null, pg.page() + 1));
    }

    private void renderMaps() {
        var all = MapBoardModel.order(rows.apply(game));
        var shown = onlyMissing ? all.stream().filter(MapBoardModel.Row::missingEvidence).toList() : all;
        var pg = Paginator.page(shown, MAPS_PER_PAGE, page);
        menu.set(0, Buttons.item(gameIcons.getOrDefault(game, Material.PAPER), Theme.NAME + gameNames.getOrDefault(game, game),
                Lore.of().line(Theme.TEXT + MapBoardModel.summary(all))));
        int c = 0;
        for (var check : MapReviewStore.Check.values())
            menu.set(3 + c++, Buttons.item(Material.NAME_TAG, Theme.ACCENT + title(check), List.of()));
        menu.set(FILTER_SLOT, Buttons.item(onlyMissing ? Material.HOPPER : Material.CHEST,
                Theme.ACCENT + "Filter: " + (onlyMissing ? "missing evidence only" : "all maps"),
                Lore.of().hint("Click to switch")), p -> { onlyMissing = !onlyMissing; page = 0; menu.refresh(); });
        int r = 0;
        for (var row : pg.items()) {
            int base = (r + 1) * 9; r++;
            menu.set(base + 1, Buttons.item(Material.MAP, Theme.NAME + row.map(), Lore.of().line(Theme.TEXT + (row.fullyChecked() ? "Fully checked" : "Needs evidence"))));
            int k = 0;
            for (var check : MapReviewStore.Check.values()) {
                var cell = row.cells().get(check);
                var lore = Lore.of().line(Theme.TEXT + "Status: <white>" + cell.status().symbol + " " + cell.status().word)
                        .kv("Evidence", cell.age());
                if (!cell.note().isBlank()) lore.desc(cell.note().length() > 80 ? cell.note().substring(0, 80) + "..." : cell.note());
                if (check == MapReviewStore.Check.VISUAL) lore.desc("Recording this needs pulsegames.maps.approve.");
                lore.blank().hint("Click for the mark command");
                String name = pane(cell.status()) + cell.status().symbol + " " + title(check);
                menu.set(base + 3 + k, Buttons.item(material(cell.status()), name, lore), p -> prompt(p, row.game(), row.map(), check));
                k++;
            }
        }
        menu.back(p -> go(null, 0));
        menu.pager(pg, () -> go(game, pg.page() - 1), () -> go(game, pg.page() + 1));
    }

    private void prompt(Player p, String g, String m, MapReviewStore.Check check) {
        String cmd = MapBoardModel.markCommand(g, m, check);
        p.closeInventory();
        p.sendMessage(Component.text("Mark " + check + " for " + g + " / " + m + ": replace pass with fail or pending if needed, "
                + "and put your evidence in place of <evidence note>.", NamedTextColor.GRAY));
        p.sendMessage(Component.text(cmd, NamedTextColor.GREEN).clickEvent(ClickEvent.suggestCommand(cmd)));
        p.sendMessage(Component.text("(Click the line to put it in your chat box.)", NamedTextColor.DARK_GRAY));
    }

    private static String title(MapReviewStore.Check check) {
        String n = check.name();
        return n.charAt(0) + n.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String pane(MapBoardModel.Status s) {
        return switch (s) { case PASS -> "<green>"; case FAIL -> "<red>"; case PENDING -> "<yellow>"; case STALE -> "<gold>"; case MISSING -> "<gray>"; };
    }

    private static Material material(MapBoardModel.Status s) {
        return switch (s) {
            case PASS -> Material.LIME_STAINED_GLASS_PANE; case FAIL -> Material.RED_STAINED_GLASS_PANE;
            case PENDING -> Material.YELLOW_STAINED_GLASS_PANE; case STALE -> Material.ORANGE_STAINED_GLASS_PANE;
            case MISSING -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
        };
    }
}
