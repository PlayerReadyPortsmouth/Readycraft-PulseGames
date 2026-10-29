package uk.co.playerready.pulsegames.core.play;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Paginator;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.ui.forms.FormLayout;
import uk.co.playerready.pulsegames.core.ui.forms.Forms;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The leader's private-match flow: one list of choices (game, mode, map, bots, difficulty, Start) and a
 * picker for each. Every screen is a {@link Choice} list, drawn as a chest menu for Java and as a
 * simple form for Bedrock, so both platforms get exactly the same steps.
 */
final class PrivateMatchMenu {
    private static final int PER_PAGE = 28;
    private static final String BACK = "back", RANDOM = "random";

    /** One row: what it does, how it looks in a chest, and a line of help. */
    private record Choice(String id, Material icon, String name, String help, boolean glow) { }

    private final PulseGamesPlugin plugin;
    private final Map<UUID, PrivateDraft> drafts = new HashMap<>();

    PrivateMatchMenu(PulseGamesPlugin plugin) { this.plugin = plugin; }

    void open(Player player) {
        if (drafts.size() > 256) drafts.clear();
        hub(player, drafts.computeIfAbsent(player.getUniqueId(), id -> new PrivateDraft()));
    }

    private static int contentSlot(int index) { return 10 + (index / 7) * 9 + index % 7; }

    // ---- the choices list -------------------------------------------------------------------

    private void choose(Player player, String title, String intro, List<Choice> choices,
                        Consumer<String> pick, Runnable back) {
        List<FormLayout.Button> buttons = new ArrayList<>();
        for (Choice c : choices) buttons.add(new FormLayout.Button(c.name() + "\n" + c.help(), c.id()));
        buttons.add(new FormLayout.Button("Back", BACK));
        boolean shown = Forms.list(plugin, player, title, intro, buttons, target -> {
            if (!player.isOnline()) return;
            if (target.equals(BACK)) back.run(); else pick.accept(target);
        });
        if (shown) return;
        Menu menu = new Menu(plugin, player, Theme.title(title), 6);
        int[] page = {0};
        menu.renderer(() -> {
            var shownPage = Paginator.page(choices, PER_PAGE, page[0]);
            page[0] = shownPage.page();
            for (int i = 0; i < shownPage.items().size(); i++) {
                Choice c = shownPage.items().get(i);
                var icon = Buttons.item(c.icon(), Theme.NAME + c.name(), Lore.of().desc(c.help()).blank().hint("Click to choose"));
                menu.set(contentSlot(i), c.glow() ? Buttons.glow(icon) : icon, p -> pick.accept(c.id()));
            }
            menu.set(4, Buttons.item(Material.PAPER, Theme.ACCENT + title, Lore.of().desc(intro)));
            menu.pager(shownPage, () -> { page[0]--; menu.refresh(); }, () -> { page[0]++; menu.refresh(); });
            menu.back(p -> back.run());
            menu.close();
            menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
        });
        menu.open(player);
    }

    // ---- hub ------------------------------------------------------------------------------

    private void hub(Player player, PrivateDraft draft) {
        GameType game = draft.gameId() == null ? null : plugin.registry().get(draft.gameId());
        GameMode mode = game == null || draft.modeId() == null ? null : game.mode(draft.modeId());
        var arena = game == null || draft.mapId() == null ? null : plugin.arenas().byId(game.id(), draft.mapId());
        boolean botsOn = draft.fillBots() && plugin.getConfig().getBoolean("private.allow-bots", true);
        List<Choice> rows = new ArrayList<>();
        rows.add(new Choice("game", game == null ? Material.COMPASS : game.icon(),
                "Game: " + (game == null ? "not chosen" : game.displayName()), "Pick what you will play.", game == null));
        rows.add(new Choice("mode", Material.REPEATER, "Mode: " + (mode == null ? "not chosen" : mode.displayName()),
                game == null ? "Pick a game first." : "Pick how it is played.", game != null && mode == null));
        rows.add(new Choice("map", Material.MAP, "Map: " + (arena == null ? "Random" : arena.displayName()),
                mode == null ? "Pick a mode first." : "Pick a map, or leave it on Random.", false));
        if (plugin.getConfig().getBoolean("private.allow-bots", true)) {
            rows.add(new Choice("bots", botsOn ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                    "Fill with bots: " + (botsOn ? "On" : "Off"),
                    "Bots take the empty seats so a small group can play. Games that cannot use bots need two people.", false));
            if (botsOn) rows.add(new Choice("level", Material.IRON_SWORD, "Bot difficulty: " + draft.level().label(),
                    "Easy, Normal, Hard or Adaptive. Map default follows the map's own setting.", false));
        }
        var party = plugin.parties().partyOf(player);
        int people = party == null ? 1 : party.onlineMembers().size();
        rows.add(new Choice("start", Material.EMERALD_BLOCK, "Start private match",
                people + (people == 1 ? " person" : " people") + " will play. " + PrivateRules.TOKEN_NOTE, draft.ready()));
        choose(player, "Private match", "Only your party can join. " + PrivateRules.TOKEN_NOTE, rows, id -> {
            switch (id) {
                case "game" -> pickGame(player, draft);
                case "mode" -> { if (game == null) hub(player, draft); else pickMode(player, draft, game); }
                case "map" -> { if (mode == null) hub(player, draft); else pickMap(player, draft, game, mode); }
                case "bots" -> { draft.toggleBots(); hub(player, draft); }
                case "level" -> { draft.nextLevel(); hub(player, draft); }
                case "start" -> {
                    if (!draft.ready()) {
                        player.sendMessage(Text.msg("<yellow>Pick a game and a mode first."));
                        hub(player, draft);
                        return;
                    }
                    player.closeInventory();
                    if (plugin.privateMatches().start(player, draft.toSetup(player.getUniqueId(), Set.of(player.getUniqueId())))) {
                        drafts.remove(player.getUniqueId());
                    }
                }
                default -> { }
            }
        }, () -> {
            if (Forms.supports(plugin, player)) plugin.gameMenu().openGames(player);
            else new uk.co.playerready.pulsegames.core.party.PartyMenu(plugin).open(player);
        });
    }

    // ---- pickers ----------------------------------------------------------------------------

    private boolean hasMap(GameType game, GameMode mode) { return !plugin.arenas().forGame(game.id(), mode.id()).isEmpty(); }

    private void pickGame(Player player, PrivateDraft draft) {
        boolean privileged = player.hasPermission("pulsegames.admin") || plugin.canUseNewFeatures(player);
        List<Choice> rows = new ArrayList<>();
        for (GameType g : plugin.registry().all()) {
            if (g.id().equals("duels") || !plugin.gameAvailable(g) && !privileged) continue;
            if (g.modes().stream().noneMatch(m -> hasMap(g, m))) continue;
            rows.add(new Choice(g.id(), g.icon(), g.displayName(), g.description(), g.id().equals(draft.gameId())));
        }
        choose(player, "Pick a game", "Choose what your party will play.", rows, id -> {
            GameType g = plugin.registry().get(id);
            if (g == null) { hub(player, draft); return; }
            var modes = g.modes().stream().filter(m -> hasMap(g, m)).toList();
            draft.setGame(id, modes.size() == 1 ? modes.get(0).id() : null);
            hub(player, draft);
        }, () -> hub(player, draft));
    }

    private void pickMode(Player player, PrivateDraft draft, GameType game) {
        List<Choice> rows = new ArrayList<>();
        for (GameMode m : game.modes()) {
            if (!hasMap(game, m)) continue;
            String bots = plugin.bots() != null && plugin.bots().supports(game.id(), m.rulesId()) ? "Bots can fill in." : "Needs two people.";
            rows.add(new Choice(m.id(), game.icon(), m.displayName(),
                    (m.isTeams() ? "Teams of " + m.teamSize() + ". " : "Free for all. ") + bots, m.id().equals(draft.modeId())));
        }
        choose(player, game.displayName() + " modes", "Pick how it is played.", rows, id -> {
            draft.setMode(id);
            hub(player, draft);
        }, () -> hub(player, draft));
    }

    private void pickMap(Player player, PrivateDraft draft, GameType game, GameMode mode) {
        Set<String> busy = plugin.privateMatches().busyArenas(game.id());
        List<Choice> rows = new ArrayList<>();
        rows.add(new Choice(RANDOM, Material.ENDER_EYE, "Random", "A free map picked for you.", draft.mapId() == null));
        for (var arena : plugin.arenas().forGame(game.id(), mode.id())) {
            boolean taken = busy.contains(arena.id());
            rows.add(new Choice(arena.id(), taken ? Material.GRAY_DYE : Material.MAP, arena.displayName(),
                    taken ? "Busy right now. Another copy of this map is used if one is free." : "Free to play.",
                    arena.id().equals(draft.mapId())));
        }
        choose(player, "Pick a map", "Random picks a free map.", rows, id -> {
            draft.setMap(id.equals(RANDOM) ? null : id);
            hub(player, draft);
        }, () -> hub(player, draft));
    }
}
