package uk.co.playerready.pulsegames.core.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Theme;
import java.util.*;

/** One immutable result per player, with no reward mutations in display/action handlers. */
public final class MatchResults {
    public record Result(String match, String game, String mode, String map, String outcome, String team,
                         int score, int teamScore, int kills, int deaths, int endTokens,
                         boolean competitive, boolean practice, long expiresAt,
                         int place, int field, List<String> podium, List<String> summary) {
        /** Without a co-op summary. */
        public Result(String match, String game, String mode, String map, String outcome, String team,
                      int score, int teamScore, int kills, int deaths, int endTokens,
                      boolean competitive, boolean practice, long expiresAt,
                      int place, int field, List<String> podium) {
            this(match, game, mode, map, outcome, team, score, teamScore, kills, deaths, endTokens,
                    competitive, practice, expiresAt, place, field, podium, List.of());
        }
        /** Pre-placement shape, kept so existing callers and tests need no change. */
        public Result(String match, String game, String mode, String map, String outcome, String team,
                      int score, int teamScore, int kills, int deaths, int endTokens,
                      boolean competitive, boolean practice, long expiresAt) {
            this(match, game, mode, map, outcome, team, score, teamScore, kills, deaths, endTokens,
                    competitive, practice, expiresAt, 0, 0, List.of());
        }
        /** Friendly placement summary shown first in chat; empty when the game has no placings. */
        public List<String> placeLines() {
            List<String> out = new ArrayList<>();
            if (place > 0) out.add("You finished " + Placements.ordinal(place) + " of " + field + ". Well played!");
            if (!podium.isEmpty()) out.add("Podium - " + String.join(" | ", podium));
            return out;
        }
        public List<String> lines() {
            return List.of(outcome + " • " + game + " / " + mode + " • " + map,
                    "Team: " + team + " | Team score: " + teamScore,
                    "Your score: " + score + " | Kills: " + kills + " | Deaths: " + deaths,
                    "End-of-round tokens: +" + endTokens + " (kill bonuses were paid during play)",
                    competitive ? "Competitive: records and match rewards enabled"
                            : "Practice / unranked: no competitive records or match rewards");
        }
    }
    private final PulseGamesPlugin plugin;
    private final Map<UUID, Result> recent = new LinkedHashMap<>();
    private final Map<UUID, Long> returnHolds = new HashMap<>();
    public boolean holdsReturn(Player player) {
        long until = returnHolds.getOrDefault(player.getUniqueId(), 0L);
        if (until <= System.currentTimeMillis()) { returnHolds.remove(player.getUniqueId()); return false; }
        return true;
    }
    public void clearReturnHold(Player player) { returnHolds.remove(player.getUniqueId()); }
    private void holdReturn(Player player) {
        returnHolds.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis());
        if (returnHolds.size() < 1024 || returnHolds.containsKey(player.getUniqueId()))
            returnHolds.put(player.getUniqueId(), System.currentTimeMillis() + 30_000L);
    }
    public MatchResults(PulseGamesPlugin plugin) { this.plugin = plugin; }

    public void publish(Player player, Result result) {
        recent.entrySet().removeIf(entry -> entry.getValue().expiresAt() < System.currentTimeMillis());
        recent.put(player.getUniqueId(), result);
        holdReturn(player);
        while (recent.size() > 1024) recent.remove(recent.keySet().iterator().next());
        result.placeLines().forEach(line -> player.sendMessage(Component.text(line)));
        result.lines().forEach(line -> player.sendMessage(Component.text(line)));
        player.sendMessage(Component.text("[View results / next game]").clickEvent(ClickEvent.runCommand("/results")));
        player.sendMessage(Component.text("Java and Bedrock: /results opens the action menu."));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            var current = plugin.instances().byPlayer(player);
            if (player.isOnline() && current != null && current.id().equals(result.match())
                    && current.state() == GameState.ENDING) open(player, result);
        }, 20L);
    }

    /**
     * Result for a player who left the round early (queued the next game, or went to the lobby).
     * One chat line only: they may already be in another queue, so no menu is forced open.
     */
    public void publishLate(Player player, Result result) {
        recent.entrySet().removeIf(entry -> entry.getValue().expiresAt() < System.currentTimeMillis());
        recent.put(player.getUniqueId(), result);
        while (recent.size() > 1024) recent.remove(recent.keySet().iterator().next());
        String place = result.place() > 0 ? "You finished " + Placements.ordinal(result.place()) + " of " + result.field()
                + " in " + result.game() + ". " : "Your " + result.game() + " round has ended. ";
        player.sendMessage(Component.text(place + "[View results]").clickEvent(ClickEvent.runCommand("/results")));
    }

    /**
     * Result actions are only blocked while the player is actively playing another round.
     * Spectators, finished players and queue-waiters may act straight away.
     */
    static boolean canAct(GameInstance current, Player player, String matchId) {
        if (current == null || current.id().equals(matchId)) return true;
        if (current.state() == GameState.ENDING || current.state() == GameState.WAITING
                || current.state() == GameState.COUNTDOWN) return true;
        return current.state() != GameState.RUNNING || !current.isAlive(player);
    }

    public void command(Player player, String[] args) {
        Result result = recent.get(player.getUniqueId());
        if (result == null || result.expiresAt() < System.currentTimeMillis()) {
            recent.remove(player.getUniqueId());
            player.sendMessage(Component.text("No recent result. Finish a round to see its summary."));
            return;
        }
        if (args.length == 0) { open(player, result); return; }
        if (args.length != 1 || !Set.of("again", "games", "return").contains(args[0].toLowerCase(Locale.ROOT))) {
            player.sendMessage(Component.text("/results [again|games|return]")); return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        GameInstance current = plugin.instances().byPlayer(player);
        if (!canAct(current, player, result.match())) {
            player.sendMessage(Component.text("Finish or leave your current game before using the previous result's actions.")); return;
        }
        if (plugin.playService().hasPending(player)) {
            player.sendMessage(Component.text("Your next queue is already loading.")); return;
        }
        player.closeInventory();
        if (action.equals("return")) { clearReturnHold(player); plugin.playService().leave(player); return; }
        holdReturn(player);
        if (action.equals("again") && result.practice()) {
            plugin.practice().repeatFromResults(player); return;
        }
        if (action.equals("again")) {
            var type = plugin.registry().get(result.game());
            var mode = type == null ? null : type.mode(result.mode());
            if (mode == null) { player.sendMessage(Component.text("That mode is no longer available.")); return; }
            // Existing party admission keeps the group together and handles errors without partial joins.
            plugin.playService().join(player, type, mode);
        } else {
            // Remain in the ending match until another queue accepts the player.
            // Existing countdown still guarantees eventual cleanup if the menu is abandoned.
            plugin.gameMenu().openGames(player);
        }
    }

    private void open(Player player, Result result) {
        holdReturn(player);
        Menu menu = new Menu(plugin, player, Theme.title("Match results"), 4);
        menu.renderer(() -> {
            Material[] medals = {Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT};
            // Podium sits in the top row with 1st in the middle; actions stay on the bottom row.
            int[] podiumSlots = {4, 3, 5};
            for (int i = 0; i < Math.min(3, result.podium().size()); i++) {
                String line = result.podium().get(i);
                int colon = line.indexOf(": ");
                int rank = Math.min(2, Math.max(0, line.charAt(0) - '1'));
                menu.set(podiumSlots[rank], item(medals[rank], line.substring(0, Math.max(0, colon)), List.of(line.substring(colon + 2))));
            }
            if (!result.summary().isEmpty())
                menu.set(4, item(Material.NETHER_STAR, "Squad summary", result.summary()));
            List<String> mine = new ArrayList<>(result.placeLines().stream().filter(l -> l.startsWith("You finished")).toList());
            mine.addAll(result.lines());
            if (plugin.ranked() != null) mine.addAll(plugin.ranked().resultLines(player));
            menu.set(13, item(Material.PAPER, result.place() > 0
                    ? "You finished " + Placements.ordinal(result.place()) : "Your result", mine));
            menu.set(29, item(Material.LIME_DYE, result.practice() ? "Repeat practice" : "Play again", List.of("Queue the same mode with your party")), p -> act(p, result.match(), "again"));
            menu.set(31, item(Material.COMPASS, "Change game", List.of("Open the game selector")), p -> act(p, result.match(), "games"));
            menu.set(33, item(Material.RED_BED, "Return", List.of("Return to your source server or lobby")), p -> act(p, result.match(), "return"));
        });
        menu.open(player);
    }
    /** Result text is data, not markup: escape it, wrap it for phones, and keep it upright. */
    private static ItemStack item(Material material, String title, List<String> lore) {
        return Buttons.item(material, MiniMessage.miniMessage().escapeTags(title), lore.stream()
                .flatMap(l -> Lore.wrap(l, Lore.WIDTH).stream()).map(l -> Theme.plain(Component.text(l))).toList());
    }
    /** Only while this is still the player's latest result. */
    private void act(Player player, String match, String action) {
        Result result = recent.get(player.getUniqueId());
        if (result == null || !result.match().equals(match)) return;
        command(player, new String[]{action});
    }
}
