package uk.co.playerready.pulsegames.core.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.ui.forms.FormLayout;
import uk.co.playerready.pulsegames.core.ui.forms.Forms;
import uk.co.playerready.pulsegames.core.util.Text;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** /event: Java chest menus on the shared kit, and a Bedrock form for the same actions. */
public final class EventMenu {

    private static final String JOIN = "join", LEAVE = "leave", BRACKET = "bracket", RESULTS = "results", BACK = "back", WATCH = "watch:";

    private final PulseGamesPlugin plugin;
    private final EventService events;

    EventMenu(PulseGamesPlugin plugin, EventService events) {
        this.plugin = plugin;
        this.events = events;
    }

    // ---- main screen -----------------------------------------------------------------

    public void open(Player player) {
        if (!events.enabled()) { player.sendMessage(Text.msg("<yellow>Tournaments are switched off for now.")); return; }
        if (openForm(player)) return;
        Menu menu = new Menu(plugin, player, Theme.title("Tournaments"), 3);
        menu.renderer(() -> {
            menu.set(4, Buttons.item(Material.CLOCK, "<gold><b>Tournament", Lore.of().desc(events.statusLine())
                    .line(events.weeklyChampions().isEmpty() ? "" : "<gray>Winners this week: <white>" + events.weeklyChampions())));
            Instant now = Instant.now();
            int slot = 10;
            for (var o : events.upcoming(3)) {
                menu.set(slot, Buttons.item(Material.PAPER, "<yellow>" + EventText.when(now, o.start(), events.zone()),
                        Lore.of().kv("Game", events.describe(o.pick())).kv("Starts", EventText.until(now, o.start()))
                                .kv("Tokens", "x" + o.pick().multiplier())));
                slot += 2;
            }
            menu.set(16, Buttons.item(Material.WRITABLE_BOOK, "<aqua>Last results", Lore.of().hint("Click to see who won")), this::openResults);
            if (events.signupOpen()) {
                boolean in = events.signedUp(player);
                menu.set(21, Buttons.item(in ? Material.BARRIER : Material.LIME_DYE, in ? "<red>Leave the tournament" : "<green>Join the tournament",
                        Lore.of().desc(in ? "You can change your mind until it starts." : "Optional. We bring you to the arena when it starts.").hint("Click")),
                        p -> { if (in) events.leave(p); else events.join(p); menu.refresh(); });
            }
            if (events.running())
                menu.set(22, Buttons.item(Material.FILLED_MAP, events.bracketRunning() ? "<aqua>Bracket" : "<aqua>Scores",
                        Lore.of().desc("See the matches and watch them live.").hint("Click")), this::openBracket);
            menu.close();
        });
        menu.open(player);
    }

    private boolean openForm(Player player) {
        List<FormLayout.Button> buttons = new ArrayList<>();
        Instant now = Instant.now();
        StringBuilder body = new StringBuilder(events.statusLine()).append("\n\nNext tournaments:");
        for (var o : events.upcoming(3)) body.append("\n").append(EventText.when(now, o.start(), events.zone())).append("  ")
                .append(events.describe(o.pick())).append("  tokens x").append(o.pick().multiplier());
        if (!events.weeklyChampions().isEmpty()) body.append("\n\nWinners this week: ").append(events.weeklyChampions());
        if (events.signupOpen()) buttons.add(new FormLayout.Button(events.signedUp(player) ? "Leave the tournament" : "Join the tournament",
                events.signedUp(player) ? LEAVE : JOIN));
        if (events.running()) buttons.add(new FormLayout.Button(events.bracketRunning() ? "Bracket" : "Scores", BRACKET));
        buttons.add(new FormLayout.Button("Last results", RESULTS));
        return Forms.list(plugin, player, "Tournaments", body.toString(), buttons, target -> {
            if (!player.isOnline()) return;
            switch (target) {
                case JOIN -> events.join(player);
                case LEAVE -> events.leave(player);
                case BRACKET -> openBracket(player);
                case RESULTS -> openResults(player);
                default -> { }
            }
        });
    }

    // ---- bracket / scores ------------------------------------------------------------

    public void openBracket(Player player) {
        if (!events.running()) {
            player.sendMessage(Text.msg("<yellow>No tournament is under way. See /event for the next one."));
            return;
        }
        Map<String, GameInstance> watch = events.watchable();
        if (openBracketForm(player, watch)) return;
        Menu menu = new Menu(plugin, player, Theme.title("Bracket"), 6);
        menu.renderer(() -> {
            var bracket = events.bracket();
            if (bracket != null) {
                int row = 0;
                for (var round : bracket.rounds()) {
                    if (row > 4) break;
                    int col = 0;
                    for (var m : round) {
                        if (col > 8) break;
                        String a = m.a() == null ? "Bye" : events.labelOf(m.a()), b = m.b() == null ? "Bye" : events.labelOf(m.b());
                        GameInstance game = watch.get(a + " vs " + b);
                        Material icon = m.decided() ? Material.LIME_STAINED_GLASS_PANE : game != null ? Material.SPYGLASS : Material.PAPER;
                        Lore lore = Lore.of().kv("Round", Integer.toString(m.round() + 1) + (m.thirdPlace() ? " (third place)" : ""));
                        if (m.decided()) lore.kv("Winner", events.labelOf(m.winner()));
                        else if (game != null) lore.hint("Click to watch");
                        menu.set(row * 9 + col++, Buttons.item(icon, "<white>" + a + " <gray>vs <white>" + b, lore),
                                game == null ? null : p -> { p.closeInventory(); watch(p, game); });
                    }
                    row++;
                }
            } else if (events.tally() != null) {
                int i = 0;
                for (var s : events.tally().standings()) {
                    if (i >= 45) break;
                    menu.set(i++, Buttons.item(s.rank() == 1 ? Material.GOLD_INGOT : Material.IRON_INGOT,
                            "<white>" + EventText.ordinal(s.rank()) + " <yellow>" + events.labelOf(s.id()),
                            Lore.of().kv("Points", Integer.toString(s.points())).kv("Round wins", Integer.toString(s.wins()))));
                }
                int w = 45;
                for (var entry : watch.entrySet()) if (w < 53) {
                    GameInstance game = entry.getValue();
                    menu.set(w++, Buttons.item(Material.SPYGLASS, "<aqua>Watch the round", Lore.of().hint("Click to watch")), p -> { p.closeInventory(); watch(p, game); });
                }
            }
            menu.back(this::open);
            menu.close();
        });
        menu.open(player);
    }

    private boolean openBracketForm(Player player, Map<String, GameInstance> watch) {
        StringBuilder body = new StringBuilder(events.statusLine()).append("\n");
        var bracket = events.bracket();
        if (bracket != null) {
            for (var round : bracket.rounds()) for (var m : round) {
                body.append("\nRound ").append(m.round() + 1).append(m.thirdPlace() ? " (third place): " : ": ")
                        .append(m.a() == null ? "Bye" : events.labelOf(m.a())).append(" vs ").append(m.b() == null ? "Bye" : events.labelOf(m.b()));
                if (m.decided()) body.append("  - won by ").append(events.labelOf(m.winner()));
            }
        } else if (events.tally() != null) {
            for (var s : events.tally().standings()) body.append("\n").append(EventText.ordinal(s.rank())).append(" ")
                    .append(events.labelOf(s.id())).append(": ").append(s.points()).append(" points");
        }
        List<FormLayout.Button> buttons = new ArrayList<>();
        List<GameInstance> games = new ArrayList<>(watch.values());
        for (String label : watch.keySet()) buttons.add(new FormLayout.Button("Watch: " + label, WATCH + buttons.size()));
        buttons.add(new FormLayout.Button("Back", BACK));
        return Forms.list(plugin, player, "Bracket", body.toString(), buttons, target -> {
            if (!player.isOnline()) return;
            if (target.startsWith(WATCH)) {
                int idx = Integer.parseInt(target.substring(WATCH.length()));
                if (idx < games.size()) watch(player, games.get(idx));
            } else open(player);
        });
    }

    private void watch(Player player, GameInstance game) {
        if (!game.watch(player)) player.sendMessage(Text.msg("<yellow>You cannot watch that match right now. Leave your current game first, or try again in a moment."));
        else player.sendMessage(Text.msg("<gray>You are watching. Use the hotbar to move around, or <yellow>/lobby</yellow> to leave."));
    }

    // ---- results ---------------------------------------------------------------------

    public void openResults(Player player) {
        List<String> lines = events.lastResult();
        if (lines.isEmpty()) { player.sendMessage(Text.msg("<yellow>No tournament has finished yet.")); return; }
        String title = events.lastResultTitle();
        if (Forms.list(plugin, player, "Last results", title + "\n\n" + String.join("\n", lines),
                List.of(new FormLayout.Button("Back", BACK)), t -> { if (player.isOnline()) open(player); })) return;
        Menu menu = new Menu(plugin, player, Theme.title("Last results"), 3);
        menu.renderer(() -> {
            Lore lore = Lore.of();
            lines.forEach(lore::line);
            menu.set(13, Buttons.item(Material.GOLDEN_HELMET, "<gold><b>" + title, lore));
            menu.back(this::open);
            menu.close();
        });
        menu.open(player);
    }
}
