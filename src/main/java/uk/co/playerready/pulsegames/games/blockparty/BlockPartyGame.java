package uk.co.playerready.pulsegames.games.blockparty;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Block Party: the floor turns to colors, stand on the called color before the rest vanishes.
 * Modes: classic, hardcore (faster, more colors).
 */
public final class BlockPartyGame extends MiniGame implements uk.co.playerready.pulsegames.core.bot.BotMovement {

    private static final Material[] COLORS = {
            Material.RED_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL, Material.YELLOW_WOOL,
            Material.PURPLE_WOOL, Material.ORANGE_WOOL, Material.PINK_WOOL, Material.CYAN_WOOL,
            Material.WHITE_WOOL, Material.LIGHT_BLUE_WOOL
    };

    private enum Phase { SHUFFLE, STAND, CLEARED }

    private Cuboid floor;
    private Phase phase = Phase.SHUFFLE;
    private int phaseTimer = 3;
    private int round = 0;
    private Material target;

    public BlockPartyGame(GameInstance game) {
        super(game);
    }

    private boolean isHardcore() { return game.mode().id().equals("hardcore"); }

    private int maxRounds() { return game.arena().settings().getInt("rounds", 15); }

    private int colorCount() { return Math.min(isHardcore() ? 8 : 5, COLORS.length); }

    private int standSeconds() {
        int base = isHardcore() ? 5 : 7;
        return Math.max(isHardcore() ? 1 : 2, base - round / 2);
    }

    @Override
    public void onStart() {
        floor = game.arena().region("floor");
        game.broadcast("Stand on the called color before the floor vanishes!");
        shuffleFloor();
    }

    @Override
    public void onSecond(int gameTime) {
        if (floor == null || --phaseTimer > 0) {
            if (phase == Phase.STAND && phaseTimer > 0) {
                game.alivePlayers().forEach(p -> p.setLevel(phaseTimer));
            }
            return;
        }
        switch (phase) {
            case SHUFFLE -> {
                round++;
                if (round > maxRounds()) {
                    game.end(game.alivePlayers(), "survived");
                    return;
                }
                pickTarget();
                phase = Phase.STAND;
                phaseTimer = standSeconds();
            }
            case STAND -> {
                clearFloor();
                phase = Phase.CLEARED;
                phaseTimer = 3;
            }
            case CLEARED -> {
                shuffleFloor();
                phase = Phase.SHUFFLE;
                phaseTimer = 2;
            }
        }
    }

    private void shuffleFloor() {
        var random = ThreadLocalRandom.current();
        for (Block block : floor.blocks(game.world())) {
            block.setType(COLORS[random.nextInt(colorCount())]);
        }
    }

    private void pickTarget() {
        target = COLORS[ThreadLocalRandom.current().nextInt(colorCount())];
        String name = target.name().replace("_WOOL", "").replace('_', ' ');
        game.broadcast("<yellow><b>" + name + "!</b></yellow> <gray>(" + standSeconds() + "s)");
        boolean hideHint = isHardcore() && round > 5;
        for (Player player : game.alivePlayers()) {
            Text.title(player, "<yellow><b>" + name, "");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
            player.getInventory().clear();
            if (!hideHint) {
                for (int i = 0; i < 9; i++) {
                    player.getInventory().setItem(i, new ItemStack(target));
                }
            }
        }
    }

    private void clearFloor() {
        for (Block block : floor.blocks(game.world())) {
            if (block.getType() != target && !block.getType().isAir()) {
                block.setType(Material.AIR);
            }
        }
        game.alivePlayers().forEach(p ->
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.8f));
    }

    @Override
    public org.bukkit.Location botDestination(Player bot) {
        if (floor == null || target == null || phase == Phase.SHUFFLE) return null;
        var here = bot.getLocation();
        org.bukkit.Location best = null;
        double distance = Double.MAX_VALUE;
        int y = floor.maxY();
        // The called colour is public, including when Hardcore hides the inventory hint.
        // Keep scanning bounded independently of the builder's total floor dimensions.
        for (int x = Math.max(floor.minX(), here.getBlockX() - 12); x <= Math.min(floor.maxX(), here.getBlockX() + 12); x++) {
            for (int z = Math.max(floor.minZ(), here.getBlockZ() - 12); z <= Math.min(floor.maxZ(), here.getBlockZ() + 12); z++) {
                if (game.world().getBlockAt(x, y, z).getType() != target
                        || !game.world().getBlockAt(x, y + 1, z).isPassable()
                        || !game.world().getBlockAt(x, y + 2, z).isPassable()) continue;
                var point = new org.bukkit.Location(game.world(), x + 0.5, y + 1, z + 0.5);
                // Stable per-bot preference breaks equally good choices without changing the public colour cue.
                double candidate = here.distanceSquared(point)
                        + uk.co.playerready.pulsegames.core.util.BotChoiceVariation.preference(bot.getUniqueId(), x, z, round);
                if (candidate < distance) { best = point; distance = candidate; }
            }
        }
        return best;
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        game.broadcast("<red>" + victim.getName() + "</red> fell in round <yellow>" + round + "</yellow>!");
        game.eliminate(victim);
    }

    @Override
    public List<String> sidebar(Player player) {
        return List.of(
                "<gray>Round: <white>" + round + "/" + maxRounds(),
                "<gray>Alive: <white>" + game.alivePlayers().size());
    }

    @Override
    public int timeLimitSeconds() { return 600; }
    @Override
    public String phaseCountdown() { if (phaseTimer <= 0) return null;
        return (phase == Phase.STAND ? "Floor drops: " : phase == Phase.CLEARED ? "Floor returns: " : "Next colour: ") + phaseTimer + "s"; }

}
