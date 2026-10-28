package uk.co.playerready.pulsegames.games.spleef;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spleef: dig the floor out from under the other players.
 * Modes: classic, decay (floor rots away), splegg (egg launcher).
 */
public final class SpleefGame extends MiniGame implements uk.co.playerready.pulsegames.core.bot.BotMovement {

    private List<Cuboid> floors = List.of();
    private List<Block> floorBlocks = List.of();
    private final java.util.function.IntUnaryOperator random;
    private final java.util.Map<java.util.UUID, Long> nextAction = new java.util.HashMap<>();
    /** Decay blocks that have been marked (cracking) and vanish on the next second. */
    private final List<Block> cracking = new ArrayList<>();
    private uk.co.playerready.pulsegames.games.common.RoundController rounds;

    public SpleefGame(GameInstance game) {
        this(game, bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    SpleefGame(GameInstance game, java.util.function.IntUnaryOperator random) {
        super(game);
        this.random = random;
    }

    private boolean isSplegg() { return game.mode().id().equals("splegg"); }
    private boolean isDecay() { return game.mode().id().equals("decay"); }
    private boolean isRounds() { return uk.co.playerready.pulsegames.games.common.RoundController.isRounds(game); }

    @Override
    public void onStart() {
        floors = game.arena().regionsByPrefix("floor");
        refreshFloorBlocks();
        if (isRounds()) {
            var snapshot = uk.co.playerready.pulsegames.games.common.RegionSnapshot.capture(game.world(), floors);
            rounds = new uk.co.playerready.pulsegames.games.common.RoundController(game,
                    new uk.co.playerready.pulsegames.games.common.RoundController.Host() {
                        @Override public void beginRound(int round) { refreshFloorBlocks(); equip(); }
                        @Override public void resetWorld() { cracking.clear(); }
                    }, snapshot, 75, "Dig the floor out - last one standing scores most");
            rounds.start();
            return;
        }
        equip();
        game.broadcast(isSplegg() ? "Shoot the floor out from under your enemies!"
                : "Dig the floor out from under your enemies!");
    }

    private void refreshFloorBlocks() {
        List<Block> blocks = new ArrayList<>();
        floors.forEach(region -> blocks.addAll(region.blocks(game.world())));
        floorBlocks = blocks;
        cracking.clear();
    }

    private void equip() {
        for (Player player : isRounds() ? rounds.contestants() : game.alivePlayers()) {
            if (isSplegg()) {
                ItemStack launcher = new ItemStack(Material.GOLDEN_HOE);
                launcher.editMeta(meta -> meta.displayName(Text.mm("<gold><b>Splegg Launcher</b> <gray>(right-click)")));
                player.getInventory().setItem(0, launcher);
            } else {
                ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
                shovel.addUnsafeEnchantment(Enchantment.EFFICIENCY, 10);
                player.getInventory().setItem(0, shovel);
            }
        }
    }

    @Override
    public boolean canBreak(Player player, Block block) {
        if (isSplegg() || !inFloor(block)) return false;
        if (isRounds() && !rounds.isContesting(player)) return false;
        // Digging earns throwable snowballs (max 16) for ranged plays.
        if (countSnowballs(player) < 16) {
            player.getInventory().addItem(new ItemStack(Material.SNOWBALL));
        }
        digFeedback(player, block);
        return true;
    }

    /** Crack sound, a puff of the block and the snowball count, since the shovel breaks instantly. */
    private void digFeedback(Player player, Block block) {
        player.playSound(player.getLocation(), "minecraft:block.snow.break", 0.8f, 1.2f);
        if (block.getWorld() != null) {
            block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 1.0, 0.5), 8,
                    0.25, 0.1, 0.25, block.getBlockData());
        }
        player.sendActionBar(Text.mm("<aqua>Snowballs: <white>" + countSnowballs(player) + "<gray>/16"));
    }

    private int countSnowballs(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.SNOWBALL) count += item.getAmount();
        }
        return count;
    }

    private boolean inFloor(Block block) {
        return floors.stream().anyMatch(region -> region.contains(block.getLocation()));
    }

    @Override
    public void onSecond(int gameTime) {
        if (isRounds()) {
            rounds.tick();
            return;
        }
        if (!isDecay()) return;
        // Blocks marked a second ago vanish now; the cracks were the warning.
        for (Block block : cracking) if (!block.isEmpty()) block.setType(Material.AIR);
        cracking.clear();
        if (gameTime > 15 && !floorBlocks.isEmpty()) {
            int amount = Math.min(3 + gameTime / 15, 12);
            // Retire sampled cells permanently, including holes made by players. Bound
            // skipped holes as well as writes so a depleted large floor cannot stall a tick.
            int removed = 0;
            for (int checked = 0; checked < amount * 8 && removed < amount && !floorBlocks.isEmpty(); checked++) {
                int index = random.applyAsInt(floorBlocks.size());
                Block block = floorBlocks.get(index);
                int last = floorBlocks.size() - 1;
                floorBlocks.set(index, floorBlocks.get(last));
                floorBlocks.remove(last);
                if (!block.isEmpty()) { telegraph(block); cracking.add(block); removed++; }
            }
        }
    }

    /** Crack particles and a creak on the block that will vanish next second. */
    private void telegraph(Block block) {
        if (block.getWorld() == null) return;
        block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 1.0, 0.5), 10,
                0.3, 0.05, 0.3, block.getBlockData());
        block.getWorld().playSound(block.getLocation(), "minecraft:block.snow.step", 0.7f, 0.6f);
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (isSplegg() && event.getAction().isRightClick()
                && event.getItem() != null && event.getItem().getType() == Material.GOLDEN_HOE) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            throwEgg(player, player.getLocation().getDirection());
        }
    }

    /** Human and NPC launchers share cooldown, equipment and projectile rules. */
    boolean throwEgg(Player player, org.bukkit.util.Vector direction) {
        if (!isSplegg() || game.state() != uk.co.playerready.pulsegames.core.game.GameState.RUNNING
                || !game.isActive(player) || !player.getWorld().equals(game.world())
                || (player.getInventory().getItemInMainHand().getType() != Material.GOLDEN_HOE
                    && player.getInventory().getItemInOffHand().getType() != Material.GOLDEN_HOE)
                || player.hasCooldown(Material.GOLDEN_HOE) || direction.lengthSquared() < 0.0001) return false;
        player.setCooldown(Material.GOLDEN_HOE, 10);
        player.launchProjectile(Egg.class, direction.clone().normalize().multiply(2.0));
        return true;
    }

    private Player visibleOpponent(Player bot) {
        var here = bot.getLocation();
        return game.alivePlayers().stream().filter(p -> !p.getUniqueId().equals(bot.getUniqueId()) && game.isActive(p))
                .filter(p -> p.getWorld().equals(bot.getWorld()) && here.distanceSquared(p.getLocation()) <= 16 * 16)
                .filter(bot::hasLineOfSight)
                .min(java.util.Comparator.comparingDouble(p -> here.distanceSquared(p.getLocation()))).orElse(null);
    }

    @Override
    public org.bukkit.Location botDestination(Player bot) {
        Player enemy = visibleOpponent(bot);
        var here = bot.getLocation();
        return uk.co.playerready.pulsegames.core.bot.GroundNavigation.nextStep(here,
                point -> floors.stream().anyMatch(floor -> floor.contains(point.clone().subtract(0, 1, 0))),
                point -> {
                    double safety = 0;
                    for (int[] direction : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        var neighbour = point.clone().add(direction[0], 0, direction[1]);
                        if (floors.stream().anyMatch(f -> f.contains(neighbour.clone().subtract(0, 1, 0)))
                                && uk.co.playerready.pulsegames.core.bot.GroundNavigation.walkable(neighbour)) safety += 0.5;
                    }
                    if (enemy == null) return safety;
                    return safety - Math.abs(Math.sqrt(point.distanceSquared(enemy.getLocation())) - 2.5) * 2;
                });
    }

    @Override
    public void botTick(Player bot, long tick, uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        if (!game.isBot(bot) || !game.isActive(bot) || tick < nextAction.getOrDefault(bot.getUniqueId(), 0L)) return;
        Player enemy = visibleOpponent(bot); if (enemy == null) return;
        Block target = enemy.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (!inFloor(target) || target.getType().isAir()
                || target.equals(bot.getLocation().clone().subtract(0, 1, 0).getBlock())) return;
        org.bukkit.Location aim = target.getLocation().add(0.5, 0.5, 0.5);
        var delta = aim.toVector().subtract(bot.getEyeLocation().toVector());
        double distance = delta.length(); if (distance < 0.01) return;
        nextAction.put(bot.getUniqueId(), tick + (isSplegg() ? difficulty.projectileDelayTicks() : difficulty.meleeDelayTicks()) + random.applyAsInt(9));
        if (random.applyAsInt(100) >= difficulty.accuracyPercent()) return;
        if (isSplegg()) {
            if (distance <= 12) {
                bot.getInventory().setHeldItemSlot(0);
                throwEgg(bot, delta);
            }
        } else if (distance <= 4.5) {
            var hit = bot.getWorld().rayTraceBlocks(bot.getEyeLocation(), delta.clone().normalize(), distance + 0.05,
                    org.bukkit.FluidCollisionMode.NEVER, true);
            if (hit != null && target.equals(hit.getHitBlock())) {
                bot.getInventory().setHeldItemSlot(0);
                bot.swingMainHand();
                // Real BlockBreakEvent enforces game floor rules, protection and snowball awards.
                bot.breakBlock(target);
            }
        } else if (distance <= 12 && !bot.hasCooldown(Material.SNOWBALL)) {
            var inventory = bot.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                var item = inventory.getItem(slot);
                if (item == null || item.getType() != Material.SNOWBALL) continue;
                if (item.getAmount() == 1) inventory.setItem(slot, null); else item.setAmount(item.getAmount() - 1);
                bot.setCooldown(Material.SNOWBALL, 10);
                bot.launchProjectile(org.bukkit.entity.Snowball.class, delta.normalize().multiply(1.5));
                break;
            }
        }
    }

    @Override public void onCleanup() { nextAction.clear(); cracking.clear(); if (rounds != null) rounds.close(); }
    @Override public void onEnd(List<Player> winners) { if (rounds != null) rounds.close(); }
    @Override public void onQuit(Player player) { if (rounds != null) rounds.playerLeft(player); }

    @Override public MiniGame.Cue eliminationSound() {
        return uk.co.playerready.pulsegames.games.common.HazardFeedback.LOUD_ELIMINATION;
    }

    @Override public void onEliminated(Player player) {
        uk.co.playerready.pulsegames.games.common.HazardFeedback.finalThree(game.players(), game.alivePlayers().size(), game.players().size());
    }

    @Override public void onTimeUp() {
        if (rounds != null) rounds.onTimeUp(); else super.onTimeUp();
    }

    @Override public List<String> sidebar(Player player) {
        return rounds != null ? rounds.sidebar(player) : List.of();
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        boolean breaker = event.getEntity() instanceof Egg
                || event.getEntity() instanceof org.bukkit.entity.Snowball;
        if (breaker && event.getHitBlock() != null && inFloor(event.getHitBlock())) {
            Block hit = event.getHitBlock();
            hit.getWorld().spawnParticle(org.bukkit.Particle.ITEM_SNOWBALL,
                    hit.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0.02);
            hit.setType(Material.AIR);
        }
        if (event.getEntity() instanceof Egg) event.setCancelled(true);
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        game.broadcast("<red>" + victim.getName() + "</red> fell!");
        if (rounds != null) rounds.knockOut(victim); else game.eliminate(victim);
    }

    @Override
    public int timeLimitSeconds() {
        return isRounds() ? uk.co.playerready.pulsegames.games.common.RoundController.matchSeconds(game, 75) : 300;
    }
}
