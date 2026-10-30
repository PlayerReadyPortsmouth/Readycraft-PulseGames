package uk.co.playerready.pulsegames.core.stats;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.economy.Cosmetic;
import uk.co.playerready.pulsegames.core.economy.CosmeticsService;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.settings.SettingsMenu;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Paginator;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.List;

/** "My stats" profile: only ever shows the viewer's own numbers, tokens included. */
public final class StatsMenu {
    static final int TOKENS_SLOT = 2;
    static final int STREAK_SLOT = 3;
    static final int WEEKLY_SLOT = 4;
    static final int COSMETICS_SLOT = 5;
    static final int SETTINGS_SLOT = 6;
    static final int LEVEL_SLOT = 1;
    static final int RANKED_SLOT = 7;
    /** Games per page: rows 2-4, columns 1-7. */
    static final int PER_PAGE = 21;

    private final PulseGamesPlugin plugin;

    public StatsMenu(PulseGamesPlugin plugin) { this.plugin = plugin; }

    static int gameSlot(int index) { return 19 + (index / 7) * 9 + index % 7; }

    public void open(Player player) {
        Menu menu = new Menu(plugin, player, Theme.title(plugin.levels().tag(player) + " My stats"), 6);
        int[] page = {0};
        menu.renderer(() -> render(menu, player, page));
        menu.open(player);
    }

    /** Games the player has played, with the icon from the catalogue. */
    private List<StatsFormat.Row> rows(Player player) {
        List<StatsFormat.Row> rows = new ArrayList<>();
        for (GameType type : plugin.registry().all())
            rows.add(new StatsFormat.Row(type.id(), type.displayName(),
                    plugin.stats().get(player, type.id(), "played"), plugin.stats().get(player, type.id(), "wins")));
        return StatsFormat.playedGames(rows);
    }

    private void render(Menu menu, Player player, int[] pageState) {
        menu.set(TOKENS_SLOT, Buttons.item(Material.SUNFLOWER, Theme.MONEY + "<b>Pulse Tokens</b>",
                Lore.of().kv("You have", String.valueOf(plugin.economy().tokens(player))).blank().desc("Win games to earn more!")));
        int streak = plugin.engagement().currentStreak(player);
        menu.set(STREAK_SLOT, Buttons.item(Material.BLAZE_POWDER, Theme.BAD + "<b>Win streak: " + streak + "</b>",
                Lore.of().kv("Best", String.valueOf(plugin.engagement().bestStreak(player))).blank().desc("Wins in a row.")));
        menu.set(WEEKLY_SLOT, Buttons.item(Material.GOLDEN_HELMET, Theme.NAME + "Wins this week",
                Lore.of().kv("Wins", String.valueOf(plugin.stats().weeklyWins(player))).blank().desc("Starts again every week.")));
        var level = plugin.levels().progress(player);
        menu.set(LEVEL_SLOT, Buttons.item(Material.EXPERIENCE_BOTTLE, uk.co.playerready.pulsegames.core.progression.XpRules.colouredTag(level.level()) + " <b>Level " + level.level(),
                Lore.of().line("<white>" + uk.co.playerready.pulsegames.core.progression.XpRules.bar(level, 10)).blank().hint("Click for details")),
                p -> plugin.levelMenu().open(p));
        menu.set(COSMETICS_SLOT, cosmetics(player));
        if (plugin.ranked() != null && plugin.ranked().enabled()) {
            String division = plugin.ranked().bestDivision(player.getUniqueId());
            menu.set(RANKED_SLOT, Buttons.item(Material.IRON_SWORD, Theme.NAME + "Ranked Duels",
                    Lore.of().kv("Division", division).blank().hint("Click for ratings and boards")),
                    p -> plugin.rankedMenu().open(p));
        }
        menu.set(SETTINGS_SLOT, Buttons.item(Material.COMPARATOR, Theme.ACCENT + "My settings",
                Lore.of().desc("Party invites and comfort options.").blank().hint("Click to open")),
                p -> new SettingsMenu(plugin, () -> open(p)).open(p));

        var all = rows(player);
        var page = Paginator.page(all, PER_PAGE, pageState[0]);
        pageState[0] = page.page();
        for (int i = 0; i < page.items().size(); i++) {
            var row = page.items().get(i);
            Material icon = plugin.registry().all().stream().filter(t -> t.id().equals(row.gameId()))
                    .map(GameType::icon).findFirst().orElse(Material.PAPER);
            menu.set(gameSlot(i), Buttons.item(icon, Theme.NAME + row.name(),
                    Lore.of().kv("Played", StatsFormat.games(row.played())).kv("Won", StatsFormat.wins(row.wins()))
                            .kv("Win rate", StatsFormat.winRate(row.played(), row.wins()))));
        }
        if (all.isEmpty())
            menu.set(22, Buttons.item(Material.PAPER, Theme.TEXT + "No games yet",
                    Lore.of().desc("Play a game and it will show up here!")));
        menu.pager(page, () -> { pageState[0]--; menu.refresh(); }, () -> { pageState[0]++; menu.refresh(); });
        menu.close();
        menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private org.bukkit.inventory.ItemStack cosmetics(Player player) {
        Lore lore = Lore.of();
        for (Cosmetic.Category category : Cosmetic.Category.values()) {
            if (category == Cosmetic.Category.NAME_STYLE) continue; // chosen with /namestyle
            Cosmetic equipped = CosmeticsService.byId(plugin.economy().equipped(player, category));
            lore.kv(category.displayName, equipped == null ? "None" : equipped.name());
        }
        return Buttons.item(Material.NETHER_STAR, Theme.ACCENT + "What you are using", lore);
    }

    /** The old chat summary, for {@code /stats --text} and the console. */
    public static void sendText(PulseGamesPlugin plugin, org.bukkit.command.CommandSender to, Player target) {
        to.sendMessage(Text.msg("<yellow><b>Stats for " + target.getName() + "</b>"));
        int totalWins = 0, totalPlayed = 0;
        for (var row : new StatsMenu(plugin).rows(target)) {
            totalWins += row.wins();
            totalPlayed += row.played();
            to.sendMessage(Text.mm(" <gray>" + row.name() + ": <green>" + StatsFormat.wins(row.wins())
                    + "</green> <white>" + row.played() + " played</white>"));
        }
        to.sendMessage(Text.mm(" <gray>Total: <green>" + StatsFormat.wins(totalWins) + "</green><gray>, <white>"
                + StatsFormat.games(totalPlayed) + "</white>"));
    }
}
