package uk.co.playerready.pulsegames.core.ranked;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.ui.forms.FormLayout;
import uk.co.playerready.pulsegames.core.ui.forms.Forms;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.List;

/** /ranked: ratings, last ten results, top ten per kit and the queue buttons. Chest menu for Java, form for Bedrock. */
public final class RankedMenu {
    static final int[] KIT_SLOTS = {10, 12, 14, 16};
    static final int[] BOARD_SLOTS = {28, 30, 32, 34};
    static final int HISTORY_SLOT = 22;
    static final int LEAVE_SLOT = 40;
    private static final String QUEUE = "queue:", BOARD = "board:", HISTORY = "history", LEAVE = "leave", BACK = "back";

    private final PulseGamesPlugin plugin;
    public RankedMenu(PulseGamesPlugin plugin) { this.plugin = plugin; }

    private RankedService ranked() { return plugin.ranked(); }

    static Material icon(String kit) {
        return switch (kit) { case "sumo" -> Material.LEAD; case "crystal" -> Material.END_CRYSTAL; case "mace" -> Material.MACE; default -> Material.IRON_SWORD; };
    }

    public void open(Player player) {
        if (openForm(player)) return;
        Menu menu = new Menu(plugin, player, Theme.title("Ranked Duels"), 5);
        menu.renderer(() -> render(menu, player));
        menu.open(player);
    }

    private void render(Menu menu, Player player) {
        var kits = ranked().kits();
        var divisions = ranked().divisions();
        boolean searching = ranked().queued(player) != null;
        for (int i = 0; i < Math.min(kits.size(), KIT_SLOTS.length); i++) {
            String kit = kits.get(i);
            var r = ranked().rating(player.getUniqueId(), kit);
            Lore lore = Lore.of().kv("Rank", RankedText.standing(divisions, r)).kv("Next", RankedText.nextStep(divisions, r))
                    .blank().hint(searching ? "Already searching" : "Click to find a match");
            menu.set(KIT_SLOTS[i], Buttons.item(icon(kit), Theme.NAME + RankedText.kitName(kit), lore), p -> { p.closeInventory(); ranked().join(p, kit); });
            menu.set(BOARD_SLOTS[i], Buttons.item(Material.GOLDEN_HELMET, Theme.MONEY + "Top 10: " + RankedText.kitName(kit),
                    Lore.of().hint("Click to see")), p -> board(p, kit));
        }
        menu.set(HISTORY_SLOT, Buttons.item(Material.BOOK, Theme.ACCENT + "Your last 10 matches",
                Lore.of().desc("Wins, losses and rating changes.").blank().hint("Click to see")), this::history);
        if (searching) menu.set(LEAVE_SLOT, Buttons.item(Material.BARRIER, Theme.BAD + "Stop searching", Lore.of()), p -> { ranked().leaveQueue(p); menu.refresh(); });
        menu.close();
        menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private void history(Player player) {
        if (Forms.supports(plugin, player) && textForm(player, "Your last 10 matches", historyLines(player))) return;
        Menu menu = new Menu(plugin, player, Theme.title("Last 10 matches"), 4);
        menu.renderer(() -> {
            var lines = historyLines(player);
            for (int i = 0; i < lines.size(); i++)
                menu.set(10 + (i / 5) * 9 + i % 5, Buttons.item(Material.PAPER, Theme.TEXT + lines.get(i), Lore.of()));
            if (lines.isEmpty()) menu.set(13, Buttons.item(Material.PAPER, Theme.TEXT + "No ranked matches yet", Lore.of()));
            menu.back(this::open);
            menu.close();
            menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
        });
        menu.open(player);
    }

    private List<String> historyLines(Player player) {
        return ranked().store().history(player.getUniqueId()).stream().map(RankedText::historyLine).toList();
    }

    private void board(Player player, String kit) {
        var lines = RankedText.boardLines(ranked().store().top(kit, 10), ranked().divisions());
        if (Forms.supports(plugin, player) && textForm(player, "Top 10: " + RankedText.kitName(kit), lines)) return;
        Menu menu = new Menu(plugin, player, Theme.title("Top 10: " + RankedText.kitName(kit)), 4);
        menu.renderer(() -> {
            for (int i = 0; i < lines.size(); i++)
                menu.set(10 + (i / 5) * 9 + i % 5, Buttons.item(Material.PAPER, Theme.TEXT + lines.get(i), Lore.of()));
            if (lines.isEmpty()) menu.set(13, Buttons.item(Material.PAPER, Theme.TEXT + "Nobody is ranked yet", Lore.of()));
            menu.back(this::open);
            menu.close();
            menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
        });
        menu.open(player);
    }

    // Bedrock

    private boolean openForm(Player player) {
        if (!Forms.supports(plugin, player)) return false;
        var divisions = ranked().divisions();
        StringBuilder content = new StringBuilder();
        List<FormLayout.Button> buttons = new ArrayList<>();
        for (String kit : ranked().kits()) {
            var r = ranked().rating(player.getUniqueId(), kit);
            content.append(RankedText.kitName(kit)).append(": ").append(RankedText.standing(divisions, r))
                    .append(" - ").append(RankedText.nextStep(divisions, r)).append('\n');
            buttons.add(new FormLayout.Button("Find a " + RankedText.kitName(kit) + " match", QUEUE + kit));
        }
        for (String kit : ranked().kits()) buttons.add(new FormLayout.Button("Top 10: " + RankedText.kitName(kit), BOARD + kit));
        buttons.add(new FormLayout.Button("Your last 10 matches", HISTORY));
        if (ranked().queued(player) != null) buttons.add(new FormLayout.Button("Stop searching", LEAVE));
        return Forms.list(plugin, player, "Ranked Duels", content.toString(), buttons, target -> {
            if (!player.isOnline()) return;
            if (target.startsWith(QUEUE)) ranked().join(player, target.substring(QUEUE.length()));
            else if (target.startsWith(BOARD)) board(player, target.substring(BOARD.length()));
            else if (target.equals(HISTORY)) history(player);
            else if (target.equals(LEAVE)) ranked().leaveQueue(player);
        });
    }

    private boolean textForm(Player player, String title, List<String> lines) {
        String body = lines.isEmpty() ? "Nothing to show yet." : String.join("\n", lines);
        return Forms.list(plugin, player, title, body, List.of(new FormLayout.Button("Back", BACK)), t -> open(player));
    }

    /** /ranked [queue <kit>|leave|top|history] */
    public void command(Player player, String[] args) {
        if (!ranked().enabled()) { player.sendMessage(Text.msg("<red>Ranked is not on right now.")); return; }
        if (args.length == 0) { open(player); return; }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "queue", "join" -> {
                if (args.length < 2) { player.sendMessage(Text.msg("<yellow>/ranked queue <" + String.join("|", ranked().kits()) + ">")); return; }
                ranked().join(player, args[1].toLowerCase(java.util.Locale.ROOT));
            }
            case "leave" -> {
                if (ranked().queued(player) == null) player.sendMessage(Text.msg("<gray>You are not searching."));
                else ranked().leaveQueue(player);
            }
            case "history" -> history(player);
            default -> open(player);
        }
    }
}
