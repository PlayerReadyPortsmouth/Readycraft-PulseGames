package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Paginator;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;

/** Compass-driven GUI built on the shared menu kit: pick a game, then a mode. */
public final class GameMenu {

    /** Games per page: rows 1-4, columns 1-7 of the 54-slot menu. */
    static final int PER_PAGE = 28;
    static final int QUICK_PLAY_SLOT = 4;
    static final int PARTY_SLOT = 45;
    static final int CHALLENGES_SLOT = 46;
    static final int TOP_SLOT = 47;
    static final int KIT_SLOT = 51;
    /** One row of modes in a 27-slot menu, otherwise four rows in a 54-slot one. */
    static final int SMALL_MODES = 7;
    static final int MAX_MODES = PER_PAGE;

    private final PulseGamesPlugin plugin;

    public GameMenu(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Content slot for the nth item: rows 1-4, columns 1-7 (leaves the border and the nav row free). */
    static int contentSlot(int index) { return 10 + (index / 7) * 9 + index % 7; }

    /** Unreleased games are only listed for testers and admins; everyone else never sees them. */
    static boolean listed(boolean available, boolean seesUnreleased) { return available || seesUnreleased; }

    /** Mode picker rows: one row of modes fits a 27-slot menu, more needs the full 54. */
    static int modeRows(int modes) { return modes <= SMALL_MODES ? 3 : 6; }

    /** Mode slot: row 1 (27 slots) or the same content grid as the game list (54 slots). */
    static int modeSlot(int index, int rows) { return rows == 3 ? 10 + index : contentSlot(index); }

    /** Stack size cue: how many are waiting, 1-64, and a plain single item when quiet. */
    static int cue(int waiting) { return Math.max(1, Math.min(64, waiting)); }

    private boolean seesUnreleased(Player player) {
        return player.hasPermission("pulsegames.admin") || plugin.canUseNewFeatures(player);
    }

    private List<GameType> visibleGames(Player player) {
        boolean privileged = seesUnreleased(player);
        return plugin.registry().all().stream().filter(g -> listed(plugin.gameAvailable(g), privileged)).toList();
    }

    public void openGames(Player player) {
        Menu menu = new Menu(plugin, player, Theme.title("Games"), 6);
        int[] page = {0};
        menu.renderer(() -> renderGames(menu, player, page));
        menu.open(player);
    }

    private void renderGames(Menu menu, Player player, int[] pageState) {
        var counts = plugin.quickPlay().counts();
        String featured = plugin.quickPlay().featuredId();
        var page = Paginator.page(visibleGames(player), PER_PAGE, pageState[0]);
        pageState[0] = page.page();

        for (int i = 0; i < page.items().size(); i++) {
            GameType game = page.items().get(i);
            menu.set(contentSlot(i), gameIcon(game, counts.getOrDefault(game.id(), new QueueCounts.Count(0, 0)),
                    game.id().equals(featured)), p -> pick(p, game));
        }
        menu.set(QUICK_PLAY_SLOT, Buttons.glow(Buttons.item(Material.ENDER_EYE, Theme.GOOD + "<b>Quick Play</b>",
                Lore.of().desc("Jump into the busiest game that is starting now.").blank().hint("Click to play"))),
                p -> { p.closeInventory(); plugin.quickPlay().quickPlay(p); });
        menu.set(PARTY_SLOT, Buttons.item(Material.PLAYER_HEAD, Theme.ACCENT + "Play with a party",
                Lore.of().desc("Invite friends and your leader queues everyone together.").blank().hint("Click for party help")),
                p -> new uk.co.playerready.pulsegames.core.party.PartyMenu(plugin).open(p));
        menu.set(CHALLENGES_SLOT, Buttons.item(Material.WRITABLE_BOOK, Theme.NAME + "Daily challenges</b>",
                Lore.of().desc("Earn bonus tokens.").blank().hint("Click to see them")),
                p -> { p.closeInventory(); plugin.engagement().openChallenges(p); });
        menu.set(TOP_SLOT, Buttons.item(Material.GOLDEN_HELMET, Theme.MONEY + "<b>Weekly top 3</b>",
                Lore.of().desc("Who is winning this week.").blank().hint("Click to see")),
                p -> { p.closeInventory(); plugin.engagement().top(p, new String[0]); });
        menu.set(KIT_SLOT, kitButton(), p -> plugin.kitSelection().open(p));
        menu.pager(page, () -> { pageState[0]--; menu.refresh(); }, () -> { pageState[0]++; menu.refresh(); });
        menu.close();
        menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack kitButton() {
        return Buttons.item(Material.CHEST, Theme.ACCENT + "KitPvP loadout",
                Lore.of().desc("Choose your default or an owned kit. It applies at your next KitPvP spawn.")
                        .blank().hint("Click to choose"));
    }

    private ItemStack gameIcon(GameType game, QueueCounts.Count count, boolean featured) {
        if (!plugin.gameAvailable(game)) {
            // Testers and admins only: grey and clearly not playable yet.
            return Buttons.item(Material.GRAY_DYE, Theme.TEXT + game.displayName() + " <dark_gray>(soon)",
                    Lore.of().desc(game.description()).blank().status("<red>Coming soon!"));
        }
        Lore lore = Lore.of().desc(game.description()).blank();
        String tag = uk.co.playerready.pulsegames.games.GameTags.of(game.id());
        if (tag != null) lore.status(Theme.ACCENT + tag);
        lore.kv("Modes", String.valueOf(game.modes().size()));
        lore.status(activity(count));
        if (featured) lore.status(Theme.MONEY + "Featured today: x" + plugin.quickPlay().multiplier() + " tokens");
        lore.blank().hint(game.modes().size() == 1 ? "Click to play" : "Click to choose a mode");
        String name = Theme.NAME + game.displayName() + "</b>"
                + (count.waiting() > 0 ? " " + Theme.GOOD + "· " + count.waiting() + " waiting" : "");
        ItemStack icon = Buttons.count(Buttons.item(game.icon(), name, lore), cue(count.waiting()));
        return featured ? Buttons.glow(icon) : icon;
    }

    static String activity(QueueCounts.Count count) {
        if (count.waiting() > 0) return Theme.GOOD + "● " + count.waiting() + " waiting · " + count.playing() + " playing";
        if (count.playing() > 0) return Theme.TEXT + "● " + count.playing() + " playing";
        return Theme.HINT + "Quiet right now";
    }

    private void pick(Player player, GameType game) {
        if (!plugin.gameAvailable(game)) {
            Theme.sound(player, Theme.Cue.ERROR);
            player.sendMessage(Text.msg("<red>" + game.displayName() + " is coming soon!"));
            return;
        }
        if (game.modes().size() == 1) {
            player.closeInventory();
            plugin.playService().join(player, game, game.defaultMode());
        } else {
            openModes(player, game);
        }
    }

    private void partyHelp(Player player) {
        player.sendMessage(Text.msg("<yellow>/party invite [name]</yellow> — invite a friend (network-wide when PulseNetwork is active)"));
        player.sendMessage(Text.msg("<yellow>/party accept</yellow>, <yellow>/party list</yellow>, <yellow>/party leave</yellow>"));
        player.sendMessage(Text.msg("Your party leader selects the game. Parties may face each other in solo modes."));
    }

    public void openModes(Player player, GameType game) {
        if (game.id().equals("duels")) { plugin.duelMenu().open(player); return; }
        List<GameMode> modes = game.modes().subList(0, Math.min(game.modes().size(), MAX_MODES));
        int rows = modeRows(modes.size());
        Menu menu = new Menu(plugin, player, Theme.title(game.displayName()), rows);
        menu.renderer(() -> {
            var counts = plugin.quickPlay().modeCounts();
            for (int i = 0; i < modes.size(); i++) {
                GameMode mode = modes.get(i);
                var count = counts.getOrDefault(game.id() + "/" + mode.id(), new QueueCounts.Count(0, 0));
                menu.set(modeSlot(i, rows), modeIcon(game, mode, count), p -> {
                    p.closeInventory();
                    plugin.playService().join(p, game, mode);
                });
            }
            if (game.id().equals("kitpvp")) menu.set(rows == 3 ? 4 : 49, kitButton(), p -> plugin.kitSelection().open(p));
            menu.back(p -> openGames(p));
            menu.close();
            menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
        });
        menu.open(player);
    }

    private ItemStack modeIcon(GameType game, GameMode mode, QueueCounts.Count count) {
        Lore lore = Lore.of()
                .kv("Players", mode.minPlayers() + "-" + mode.maxPlayers())
                .line(mode.isTeams() ? Theme.TEXT + "Teams of <white>" + mode.teamSize() : Theme.TEXT + "Free for all")
                .status(activity(count)).blank().hint("Click to join");
        String name = Theme.NAME + mode.displayName() + "</b>"
                + (count.waiting() > 0 ? " " + Theme.GOOD + "· " + count.waiting() + " waiting" : "");
        return Buttons.count(Buttons.item(game.icon(), name, lore), cue(count.waiting()));
    }
}
