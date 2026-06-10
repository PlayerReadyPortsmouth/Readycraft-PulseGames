package uk.co.playerready.pulsegames.games.deathrun;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
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
 * Death Run: runners race the course; Deaths trigger traps to stop them.
 * Modes: classic (1 death), doubletrouble (2 deaths).
 *
 * Arena: regions trap1..trapN (blocks that vanish) + trigger1..triggerN (where the
 * Death stands/clicks to fire each trap), region finish, settings death-spawn.
 */
public final class DeathRunGame extends MiniGame {

    private final Set<UUID> deaths = new HashSet<>();
    private final Map<Integer, Long> trapCooldown = new HashMap<>();
    private List<Cuboid> traps = List.of();
    private List<Cuboid> triggers = List.of();

    public DeathRunGame(GameInstance game) {
        super(game);
    }

    private int deathCount() {
        return game.mode().id().equals("doubletrouble") ? 2 : 1;
    }

    @Override
    public void onStart() {
        traps = game.arena().regionsByPrefix("trap");
        triggers = game.arena().regionsByPrefix("trigger");
        List<Player> players = new ArrayList<>(game.alivePlayers());
        java.util.Collections.shuffle(players);
        Location deathSpawn = game.arena().settingLocation("death-spawn", game.world());
        for (int i = 0; i < Math.min(deathCount(), players.size() - 1); i++) {
            Player death = players.get(i);
            deaths.add(death.getUniqueId());
            if (deathSpawn != null) death.teleport(deathSpawn);
            Text.title(death, "<red><b>YOU ARE THE DEATH", "<gray>Trigger traps to stop the runners!");
        }
        game.broadcast("<red>Death(s): <yellow>"
                + String.join(", ", players.stream().limit(deathCount()).map(Player::getName).toList()));
        game.broadcast("Runners: reach the finish line. Watch out for traps!");
    }

    private boolean isDeath(Player player) { return deaths.contains(player.getUniqueId()); }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isDeath(player) || event.getClickedBlock() == null || !event.getAction().isRightClick()) return;
        Location clicked = event.getClickedBlock().getLocation();
        for (int i = 0; i < triggers.size() && i < traps.size(); i++) {
            if (triggers.get(i).contains(clicked)) {
                fireTrap(i, player);
                return;
            }
        }
    }

    private void fireTrap(int index, Player death) {
        long now = System.currentTimeMillis();
        if (trapCooldown.getOrDefault(index, 0L) > now) return;
        trapCooldown.put(index, now + game.arena().settings().getInt("trap-cooldown", 5) * 1000L);
        Cuboid trap = traps.get(index);
        Map<Location, BlockData> saved = new HashMap<>();
        for (Block block : trap.blocks(game.world())) {
            if (!block.getType().isAir()) {
                saved.put(block.getLocation(), block.getBlockData());
                block.setType(Material.AIR);
            }
        }
        for (Player p : game.everyone()) {
            p.playSound(p.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 1f, 0.8f);
        }
        game.runLater(60L, () -> saved.forEach((loc, data) -> loc.getBlock().setBlockData(data)));
    }

    @Override
    public void onMove(Player player, Location from, Location to) {
        if (isDeath(player)) return;
        Cuboid finish = game.arena().region("finish");
        if (finish != null && finish.contains(to)) {
            game.broadcast("<green><b>" + player.getName() + " reached the finish!</b></green> <gray>Runners win!");
            game.end(runners(), "finish");
        }
    }

    private List<Player> runners() {
        return game.alivePlayers().stream().filter(p -> !isDeath(p)).toList();
    }

    /** Runners respawn at the start when they die; Deaths can't die. */
    @Override
    public void onDeath(Player victim, Player killer) {
        if (isDeath(victim)) return;
        victim.setFallDistance(0);
        victim.teleport(game.spawnFor(victim));
        victim.sendActionBar(Text.mm("<red>You died! Back to the start."));
    }

    /** Time ran out: the Deaths win. */
    @Override
    public void onTimeUp() {
        game.broadcast("<red>Time's up - the Deaths win!");
        game.end(game.alivePlayers().stream().filter(this::isDeath).toList(), "time");
    }

    @Override
    public List<String> sidebar(Player player) {
        return List.of(
                "<gray>Role: " + (isDeath(player) ? "<red>Death" : "<green>Runner"),
                "<gray>Runners: <white>" + runners().size(),
                "<gray>Time left: <white>" + Text.time(Math.max(0, timeLimitSeconds() - game.gameTime())));
    }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 300);
    }
}
