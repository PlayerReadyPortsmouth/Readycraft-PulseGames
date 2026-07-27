package uk.co.playerready.pulsegames.games.buildbattle;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Build Battle: build to a theme, then vote on everyone's creations.
 * Modes: classic (5 min build), speed (2 min build).
 * Requires regions plot1..plotN (one per player).
 */
public final class BuildBattleGame extends MiniGame {

    private static final List<String> DEFAULT_THEMES = List.of(
            "Castle", "Dragon", "Pirate Ship", "Snowman", "Rocket", "Dinosaur", "Waterfall",
            "Robot", "Treehouse", "Rainbow", "Lighthouse", "Cake", "Spaceship", "Volcano");

    private static final Material[] VOTE_ITEMS = {
            Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.EMERALD_BLOCK
    };

    private enum Phase { THEME, BUILD, VOTE, DONE }

    private Phase phase = Phase.THEME;
    private int phaseTimer = 5;
    private String theme = "?";
    private List<Cuboid> plots = List.of();
    private final Map<UUID, Integer> plotOf = new HashMap<>();
    private final Map<Integer, List<Integer>> votes = new HashMap<>();
    private int votingPlot = -1;
    private final Set<UUID> votedThisPlot = new HashSet<>();

    public BuildBattleGame(GameInstance game) {
        super(game);
    }

    private int buildSeconds() {
        return game.arena().settings().getInt("build-seconds",
                game.mode().id().equals("speed") ? 120 : 300);
    }

    @Override
    public void onStart() {
        plots = game.arena().regionsByPrefix("plot");
        List<String> themes = game.arena().settings().getStringList("themes");
        if (themes.isEmpty()) themes = DEFAULT_THEMES;
        theme = themes.get(ThreadLocalRandom.current().nextInt(themes.size()));
        int i = 0;
        for (Player player : game.alivePlayers()) {
            // One plot each, never shared: co-occupants could delete each other's blocks
            // and only the first of them would be credited with the build.
            if (i >= plots.size()) {
                player.sendMessage(Text.msg("<red>This map has no free plot for you - you're watching this round."));
                continue;
            }
            plotOf.put(player.getUniqueId(), i);
            teleportToPlot(player, i);
            i++;
        }
        game.broadcast("<yellow><b>Theme: " + theme + "</b></yellow> <gray>- you have "
                + buildSeconds() / 60 + "m " + buildSeconds() % 60 + "s!");
    }

    private void teleportToPlot(Player player, int plotIndex) {
        if (plots.isEmpty()) return;
        Cuboid plot = plots.get(plotIndex);
        Location center = plot.center(game.world());
        center.setY(plot.minY() + 1);
        player.teleport(center);
    }

    @Override
    public void onSecond(int gameTime) {
        switch (phase) {
            case THEME -> {
                if (--phaseTimer <= 0) {
                    phase = Phase.BUILD;
                    phaseTimer = buildSeconds();
                    for (Player p : game.alivePlayers()) {
                        p.setGameMode(org.bukkit.GameMode.CREATIVE);
                        Text.title(p, "<green><b>BUILD!", "<gray>Theme: <yellow>" + theme);
                    }
                }
            }
            case BUILD -> {
                if (phaseTimer == 60 || phaseTimer == 30 || phaseTimer <= 10 && phaseTimer > 0) {
                    game.broadcast("<yellow>" + phaseTimer + "s</yellow> of build time left!");
                }
                if (--phaseTimer <= 0) startVoting();
            }
            case VOTE -> {
                if (--phaseTimer <= 0) nextVotingPlot();
            }
            case DONE -> { }
        }
    }

    private void startVoting() {
        phase = Phase.VOTE;
        game.broadcast("<yellow><b>Time's up!</b></yellow> <gray>Vote for each build (1-5 in your hotbar).");
        for (Player p : game.alivePlayers()) {
            p.setGameMode(org.bukkit.GameMode.ADVENTURE);
            p.setAllowFlight(true);
            p.setFlying(true);
        }
        votingPlot = -1;
        nextVotingPlot();
    }

    private void nextVotingPlot() {
        votingPlot++;
        votedThisPlot.clear();
        List<Player> builders = game.alivePlayers();
        if (votingPlot >= usedPlotCount(builders)) {
            finish();
            return;
        }
        phaseTimer = game.arena().settings().getInt("vote-seconds", 10);
        Player owner = ownerOfPlot(votingPlot);
        game.broadcast("Now voting: <yellow>" + (owner != null ? owner.getName() + "'s build" : "plot " + (votingPlot + 1)));
        for (Player p : builders) {
            teleportToPlot(p, votingPlot);
            giveVoteItems(p);
        }
    }

    /** Highest assigned plot + 1, so a mid-game quit leaves a gap rather than truncating the rotation. */
    private int usedPlotCount(List<Player> builders) {
        return plotOf.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
    }

    /** One plot per player: the lobby must not admit more players than the map has plots. */
    @Override
    public int arenaPlayerCap() {
        return Math.max(1, game.arena().regionsByPrefix("plot").size());
    }

    /** A departing builder's plot must drop out of the voting rotation. */
    @Override
    public void onQuit(Player player) {
        plotOf.remove(player.getUniqueId());
        votedThisPlot.remove(player.getUniqueId());
    }

    private Player ownerOfPlot(int plotIndex) {
        return game.alivePlayers().stream()
                .filter(p -> plotOf.getOrDefault(p.getUniqueId(), -1) == plotIndex)
                .findFirst().orElse(null);
    }

    private void giveVoteItems(Player player) {
        player.getInventory().clear();
        Player owner = ownerOfPlot(votingPlot);
        if (owner != null && owner.equals(player)) return; // can't vote for yourself
        for (int i = 0; i < VOTE_ITEMS.length; i++) {
            ItemStack item = new ItemStack(VOTE_ITEMS[i]);
            final int score = i + 1;
            item.editMeta(meta -> meta.displayName(Text.mm("<yellow>Vote: " + score + "/5")));
            player.getInventory().setItem(i, item);
        }
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (phase != Phase.VOTE || event.getItem() == null || !event.getAction().isRightClick()) return;
        int score = -1;
        for (int i = 0; i < VOTE_ITEMS.length; i++) {
            if (event.getItem().getType() == VOTE_ITEMS[i]) score = i + 1;
        }
        if (score < 0) return;
        event.setCancelled(true);
        Player voter = event.getPlayer();
        if (!votedThisPlot.add(voter.getUniqueId())) return;
        votes.computeIfAbsent(votingPlot, k -> new ArrayList<>()).add(score);
        voter.sendMessage(Text.msg("You voted <yellow>" + score + "/5</yellow>!"));
    }

    private void finish() {
        phase = Phase.DONE;
        Player best = null;
        double bestAvg = -1;
        for (Player p : game.alivePlayers()) {
            int plot = plotOf.getOrDefault(p.getUniqueId(), -1);
            List<Integer> plotVotes = votes.getOrDefault(plot, List.of());
            double avg = plotVotes.isEmpty() ? 0 : plotVotes.stream().mapToInt(Integer::intValue).average().orElse(0);
            game.broadcast("<yellow>" + p.getName() + "</yellow><gray>: <white>"
                    + String.format("%.1f", avg) + "<gray>/5");
            if (avg > bestAvg) {
                bestAvg = avg;
                best = p;
            }
        }
        game.end(best == null ? List.of() : List.of(best), "votes");
    }

    private boolean inOwnPlot(Player player, Location location) {
        Integer plot = plotOf.get(player.getUniqueId());
        return plot != null && plot < plots.size() && plots.get(plot).contains(location);
    }

    @Override
    public boolean canBreak(Player player, Block block) {
        return phase == Phase.BUILD && inOwnPlot(player, block.getLocation());
    }

    @Override
    public boolean canPlace(Player player, Block block) {
        return phase == Phase.BUILD && inOwnPlot(player, block.getLocation());
    }

    @Override
    public org.bukkit.GameMode playGameMode() { return org.bukkit.GameMode.ADVENTURE; }

    @Override
    public List<String> sidebar(Player player) {
        return List.of(
                "<gray>Theme: <yellow>" + theme,
                "<gray>Phase: <white>" + phase.name().toLowerCase(),
                "<gray>Time: <white>" + Math.max(phaseTimer, 0) + "s");
    }

    @Override
    public int startFreezeSeconds() { return 0; }

    @Override
    public int timeLimitSeconds() { return 3600; }
}
