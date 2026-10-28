package uk.co.playerready.pulsegames.core.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import java.util.*;

/** One immutable result per player, with no reward mutations in display/action handlers. */
public final class MatchResults implements Listener {
    public record Result(String match, String game, String mode, String map, String outcome, String team,
                         int score, int teamScore, int kills, int deaths, int endTokens,
                         boolean competitive, boolean practice, long expiresAt,
                         int place, int field, List<String> podium) {
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
    private static final class Holder implements InventoryHolder {
        final UUID owner;
        final String match;
        Inventory inventory;
        Holder(UUID owner, String match) { this.owner = owner; this.match = match; }
        @Override public Inventory getInventory() { return inventory; }
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
        Holder holder = new Holder(player.getUniqueId(), result.match());
        holder.inventory = plugin.getServer().createInventory(holder, 36, Component.text("Match results"));
        Material[] medals = {Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT};
        // Podium sits in the top row with 1st in the middle; actions stay on the bottom row.
        int[] podiumSlots = {4, 3, 5};
        for (int i = 0; i < Math.min(3, result.podium().size()); i++) {
            String line = result.podium().get(i);
            int colon = line.indexOf(": ");
            holder.inventory.setItem(podiumSlots[Math.min(2, Math.max(0, line.charAt(0) - '1'))],
                    item(medals[Math.min(2, Math.max(0, line.charAt(0) - '1'))], line.substring(0, Math.max(0, colon)),
                            List.of(line.substring(colon + 2))));
        }
        List<String> mine = new ArrayList<>(result.placeLines().stream().filter(l -> l.startsWith("You finished")).toList());
        mine.addAll(result.lines());
        holder.inventory.setItem(13, item(Material.PAPER, result.place() > 0
                ? "You finished " + Placements.ordinal(result.place()) : "Your result", mine));
        holder.inventory.setItem(29, item(Material.LIME_DYE, result.practice() ? "Repeat practice" : "Play again", List.of("Queue the same mode with your party")));
        holder.inventory.setItem(31, item(Material.COMPASS, "Change game", List.of("Open the game selector")));
        holder.inventory.setItem(33, item(Material.RED_BED, "Return", List.of("Return to your source server or lobby")));
        player.openInventory(holder.inventory);
    }
    private static ItemStack item(Material material, String title, List<String> lore) {
        var item = new ItemStack(material);
        item.editMeta(meta -> { meta.displayName(Component.text(title)); meta.lore(lore.stream().map(Component::text).map(c -> (Component)c).toList()); });
        return item;
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        Result result = recent.get(player.getUniqueId());
        if (result == null || !result.match().equals(holder.match)) return;
        String action = switch (event.getRawSlot()) { case 29 -> "again"; case 31 -> "games"; case 33 -> "return"; default -> null; };
        if (action != null) plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) command(player, new String[]{action});
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
