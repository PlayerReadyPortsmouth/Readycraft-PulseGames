package uk.co.playerready.pulsegames.games.partygames;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Party Games: a rotation of quick-fire micro-games; points for surviving/winning
 * each round, highest total takes the crown.
 * Micro-games: Color Rush (stand on the called color), Sumo Brawl (knock-off),
 * Sprint (first to the finish region).
 * Arena: regions floor (Color Rush), sumo (platform), finish (Sprint).
 */
public final class PartyGamesGame extends MiniGame {

    private static final Material[] COLORS = {
            Material.RED_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL,
            Material.YELLOW_WOOL, Material.PURPLE_WOOL
    };

    private enum Micro { COLOR_RUSH, SUMO, SPRINT, NONE }

    private Micro current = Micro.NONE;
    private int round = 0;
    private int phaseTimer = 5;
    private int microStep;
    private Material targetColor;
    private final Set<UUID> roundAlive = new HashSet<>();
    private boolean roundDone;

    public PartyGamesGame(GameInstance game) {
        super(game);
    }

    private int totalRounds() { return game.arena().settings().getInt("rounds", 5); }

    @Override
    public void onStart() {
        game.broadcast("<yellow><b>PARTY TIME!</b></yellow> <gray>" + totalRounds()
                + " quick-fire rounds. Win rounds to earn points!");
    }

    @Override
    public void onSecond(int gameTime) {
        if (current == Micro.NONE) {
            if (--phaseTimer <= 0) nextRound();
            return;
        }
        switch (current) {
            case COLOR_RUSH -> tickColorRush();
            case SUMO -> tickSumo();
            case SPRINT -> tickSprint();
            default -> { }
        }
    }

    private void nextRound() {
        round++;
        if (round > totalRounds()) {
            Player best = winner();
            game.end(best == null ? List.of() : List.of(best), "rounds");
            return;
        }
        roundDone = false;
        microStep = 0;
        roundAlive.clear();
        game.alivePlayers().forEach(p -> roundAlive.add(p.getUniqueId()));
        List<Micro> available = new ArrayList<>();
        if (game.arena().region("floor") != null) available.add(Micro.COLOR_RUSH);
        if (game.arena().region("sumo") != null) available.add(Micro.SUMO);
        if (game.arena().region("finish") != null) available.add(Micro.SPRINT);
        if (available.isEmpty()) available.add(Micro.SUMO);
        current = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        int i = 0;
        for (Player p : game.alivePlayers()) {
            p.teleport(startLocation(i++));
            Text.title(p, "<yellow><b>ROUND " + round, "<gray>" + microName(current));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        }
        game.broadcast("<yellow><b>Round " + round + ":</b> " + microName(current));
    }

    /** Top scorer who is still here: a disconnected leader would otherwise draw the game. */
    private Player winner() {
        for (var entry : game.topScores()) {
            Player candidate = game.plugin().getServer().getPlayer(entry.getKey());
            if (candidate != null && candidate.isOnline() && game.isParticipant(candidate)) return candidate;
        }
        return null;
    }

    private Location startLocation(int index) {
        if (current == Micro.SUMO) {
            Cuboid sumo = game.arena().region("sumo");
            if (sumo != null) {
                Location center = sumo.center(game.world());
                center.setY(sumo.maxY() + 1);
                // Integer division on the row, or the whole grid collapses onto one line.
                return center.add(index % 3 - 1, 0, index / 3 - 1);
            }
        }
        return game.arena().spawn(index, game.world());
    }

    private String microName(Micro micro) {
        return switch (micro) {
            case COLOR_RUSH -> "Color Rush - stand on the called color!";
            case SUMO -> "Sumo Brawl - knock everyone off!";
            case SPRINT -> "Sprint - first to the finish!";
            default -> "?";
        };
    }

    // ---- Color Rush -------------------------------------------------------------

    private void tickColorRush() {
        Cuboid floor = game.arena().region("floor");
        if (floor == null) {
            endRound(List.of());
            return;
        }
        microStep++;
        var random = ThreadLocalRandom.current();
        // 3 color calls per round, each: shuffle -> call -> clear -> restore
        int cycle = microStep % 9;
        if (cycle == 1) {
            for (Block block : floor.blocks(game.world())) {
                block.setType(COLORS[random.nextInt(COLORS.length)]);
            }
        } else if (cycle == 2) {
            targetColor = COLORS[random.nextInt(COLORS.length)];
            String name = targetColor.name().replace("_WOOL", "");
            game.broadcastRaw(Text.PREFIX + "<yellow><b>" + name + "!");
            game.alivePlayers().forEach(p -> Text.title(p, "<yellow><b>" + name, ""));
        } else if (cycle == 6) {
            for (Block block : floor.blocks(game.world())) {
                if (block.getType() != targetColor && !block.getType().isAir()) block.setType(Material.AIR);
            }
        } else if (cycle == 8 && microStep >= 26) {
            // Survivors of all three calls score.
            endRound(survivors());
            return;
        } else if (cycle == 8) {
            for (Block block : floor.blocks(game.world())) {
                if (block.getType().isAir()) block.setType(COLORS[random.nextInt(COLORS.length)]);
            }
        }
    }

    // ---- Sumo -------------------------------------------------------------------

    private void tickSumo() {
        microStep++;
        List<Player> in = survivors();
        if (in.size() <= 1 || microStep > 60) {
            endRound(in);
        }
    }

    // ---- Sprint -----------------------------------------------------------------

    private void tickSprint() {
        microStep++;
        if (microStep > 60) endRound(List.of());
    }

    @Override
    public void onMove(Player player, Location from, Location to) {
        if (current == Micro.SPRINT && !roundDone) {
            Cuboid finish = game.arena().region("finish");
            if (finish != null && finish.contains(to) && roundAlive.contains(player.getUniqueId())) {
                endRound(List.of(player));
            }
        }
    }

    // ---- shared round plumbing -----------------------------------------------------

    private List<Player> survivors() {
        return game.alivePlayers().stream().filter(p -> roundAlive.contains(p.getUniqueId())).toList();
    }

    /** Falling out of a micro-game knocks you out of the round, not the game. */
    @Override
    public void onDeath(Player victim, Player killer) {
        if (roundDone || !roundAlive.remove(victim.getUniqueId())) {
            victim.teleport(game.arena().lobby(game.world()));
            return;
        }
        victim.setFallDistance(0);
        victim.teleport(game.arena().lobby(game.world()));
        victim.sendActionBar(Text.mm("<red>Out of this round!"));
        if (current == Micro.SUMO && survivors().size() <= 1) {
            endRound(survivors());
        }
    }

    /** A leaver still counted as "in" would stall a sumo round waiting for a knock-off. */
    @Override
    public void onQuit(Player player) {
        if (!roundAlive.remove(player.getUniqueId())) return;
        if (current == Micro.SUMO && survivors().size() <= 1) endRound(survivors());
    }

    private void endRound(List<Player> winners) {
        if (roundDone) return;
        roundDone = true;
        current = Micro.NONE;
        phaseTimer = 5;
        if (winners.isEmpty()) {
            game.broadcast("<gray>Nobody won that round!");
        } else {
            for (Player winner : winners) {
                game.addScore(winner, 3);
            }
            game.broadcast("<green>Round winner(s): <yellow>"
                    + String.join(", ", winners.stream().map(Player::getName).toList())
                    + "</yellow> <gray>(+3 points)");
        }
        int i = 0;
        for (Player p : game.alivePlayers()) {
            p.teleport(game.arena().spawn(i++, game.world()));
        }
    }

    @Override
    public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {
        if (current != Micro.SUMO) {
            event.setCancelled(true);
        } else {
            event.setDamage(0); // sumo = knockback only
        }
    }

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Round: <white>" + Math.min(round, totalRounds()) + "/" + totalRounds());
        lines.add("<gray>Your points: <yellow>" + game.score(player));
        game.topScores().stream().limit(3).forEach(e -> {
            Player p = game.plugin().getServer().getPlayer(e.getKey());
            if (p != null) lines.add("<gray>" + p.getName() + ": <white>" + e.getValue());
        });
        return lines;
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public int timeLimitSeconds() { return 900; }
}
