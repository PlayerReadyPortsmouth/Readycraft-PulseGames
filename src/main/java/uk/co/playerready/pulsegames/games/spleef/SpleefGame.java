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
    public String startProblem(List<Player> players) {
        if (!isRounds()) return null;
        return uk.co.playerready.pulsegames.games.common.RegionSnapshot.refusal("Spleef",
                game.arena().regionsByPrefix("floor"), game.plugin().getLogger()::warning);
    }

    @Override
    public void onStart() {
        floors = game.arena().regionsByPrefix("floor");
        refreshFloorBlocks();
        if (isRounds()) {
            var snapshot = uk.co.playerready.pulsegames.games.common.RegionSnapshot.capture(game.world(), floors);
            rounds = new uk.co.playerready.pulsegames.games.common.RoundController(game,
                    new uk.co.playerready.pulsegames.games.common.RoundController.Host() {
                        @Override public void beginRound(int round) { refreshFloorBlocks(); equip(); }
                        @Override public void resetWorld() { cracking.clear(); clearProjectiles(); }
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
                launcher.editMeta(meta -> meta.displayName(Text.mm("<gold><b>Splegg Launcher</b> <gray>("+uk.co.playerready.pulsegames.core.util.PlatformDetector.useVerb(player)+")")));
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

    // ---- Bot play -------------------------------------------------------------------------------

    private static final int REMEMBER_TICKS = 60, STUCK_TICKS = 40, AWARE_BLOCKS = 32;

    /** Per-bot memory: who it is chasing, when it noticed them, where it is roaming, whether it is stuck. */
    private static final class Brain {
        java.util.UUID enemy; long reactAt, lastSeen;
        org.bukkit.Location lastKnown;
        SpleefTactics.Cell roam; long roamUntil, unstickUntil; int strafe = 1; long strafeUntil;
        boolean wantsMove; final SpleefTactics.StuckWatch stuck = new SpleefTactics.StuckWatch();
    }
    private record Target(java.util.UUID id, org.bukkit.Location loc, double vx, double vz, boolean live) {}
    private final java.util.Map<java.util.UUID, Brain> brains = new java.util.HashMap<>();
    /** id -> {x, z, vx, vz, tick}: velocity per tick sampled from position, since NPC velocity is not reliable. */
    private final java.util.Map<java.util.UUID, double[]> motion = new java.util.HashMap<>();
    private long lastTrack = -1, now;

    private void track(long tick) {
        if (tick == lastTrack) return;
        lastTrack = tick;
        for (Player p : game.alivePlayers()) {
            var at = p.getLocation(); double[] m = motion.get(p.getUniqueId());
            if (m == null) { motion.put(p.getUniqueId(), new double[]{at.getX(), at.getZ(), 0, 0, tick}); continue; }
            double dt = Math.max(1, tick - m[4]);
            m[2] = m[2] * 0.5 + (at.getX() - m[0]) / dt * 0.5; m[3] = m[3] * 0.5 + (at.getZ() - m[1]) / dt * 0.5;
            m[0] = at.getX(); m[1] = at.getZ(); m[4] = tick;
        }
    }

    /** Nearest living opponent within 32 blocks, sight not required (a line of sight counts as 4 blocks nearer). */
    private Target acquire(Player bot, Brain brain, long tick) {
        var here = bot.getLocation(); Player best = null; double bestScore = Double.MAX_VALUE;
        for (Player p : game.alivePlayers()) {
            if (p.getUniqueId().equals(bot.getUniqueId()) || !game.isActive(p) || !p.getWorld().equals(bot.getWorld())) continue;
            double d = Math.sqrt(here.distanceSquared(p.getLocation()));
            if (d > AWARE_BLOCKS) continue;
            double score = d - (bot.hasLineOfSight(p) ? 4 : 0);
            if (score < bestScore) { bestScore = score; best = p; }
        }
        if (best != null) {
            double[] m = motion.get(best.getUniqueId());
            brain.lastKnown = best.getLocation().clone(); brain.lastSeen = tick;
            return new Target(best.getUniqueId(), brain.lastKnown, m == null ? 0 : m[2], m == null ? 0 : m[3], true);
        }
        if (brain.lastKnown != null && tick - brain.lastSeen <= REMEMBER_TICKS)
            return new Target(brain.enemy, brain.lastKnown, 0, 0, false);
        return null;
    }

    private SpleefTactics.Grid grid(int y) {
        var cache = new java.util.HashMap<Long, Boolean>();
        return (x, z) -> cache.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), k -> {
            Block b = game.world().getBlockAt(x, y, z);
            return !b.getType().isAir() && inFloor(b);
        });
    }

    private SpleefTactics.Grid crackingGrid(int y) {
        return (x, z) -> { for (Block b : cracking) if (b.getY() == y && b.getX() == x && b.getZ() == z) return true; return false; };
    }

    private boolean onFloor(org.bukkit.Location point) {
        return floors.stream().anyMatch(floor -> floor.contains(point.clone().subtract(0, 1, 0)));
    }

    @Override public int navigationIntervalTicks(uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) { return 5; }

    @Override
    public org.bukkit.Location botDestination(Player bot) {
        var here = bot.getLocation();
        Brain brain = brains.computeIfAbsent(bot.getUniqueId(), id -> new Brain());
        Target enemy = acquire(bot, brain, now);
        int y = here.getBlockY() - 1;
        SpleefTactics.Grid g = grid(y), crack = crackingGrid(y);
        if (brain.stuck.update(now, here.getBlockX(), here.getBlockZ(), brain.wantsMove, STUCK_TICKS)) {
            brain.unstickUntil = now + 40; brain.roam = null;
            var step = SpleefTactics.randomNeighbour(new SpleefTactics.Cell(here.getBlockX(), here.getBlockZ()), random,
                    c -> g.solid(c.x(), c.z()));
            if (step != null) brain.roam = new SpleefTactics.Cell(step.x() + (int) Math.signum(step.x() - here.getBlockX()) * 2,
                    step.z() + (int) Math.signum(step.z() - here.getBlockZ()) * 2);
        }
        boolean unsticking = now < brain.unstickUntil && brain.roam != null;
        if (!unsticking && (enemy == null || !enemy.live()) && (brain.roam == null || now > brain.roamUntil
                || Math.hypot(here.getX() - brain.roam.x() - .5, here.getZ() - brain.roam.z() - .5) < 1.5)) {
            brain.roam = pickRoam(y); brain.roamUntil = now + 160;
        }
        if (now > brain.strafeUntil) { brain.strafe = random.applyAsInt(2) == 0 ? -1 : 1; brain.strafeUntil = now + 30 + random.applyAsInt(40); }
        double preferred = isSplegg() ? 6.5 : isDecay() ? 4 : 2.5, weight = isDecay() ? 0.8 : 2;
        var next = uk.co.playerready.pulsegames.core.bot.GroundNavigation.nextStepDeep(here, this::onFloor, point -> {
            double score = SpleefTactics.comfort(g, crack, point.getBlockX(), point.getBlockZ());
            if (unsticking) return score - Math.hypot(point.getX() - brain.roam.x() - .5, point.getZ() - brain.roam.z() - .5);
            if (enemy != null && enemy.live()) {
                double d = Math.hypot(point.getX() - enemy.loc().getX(), point.getZ() - enemy.loc().getZ());
                double ux = (enemy.loc().getX() - here.getX()), uz = (enemy.loc().getZ() - here.getZ());
                double len = Math.max(0.01, Math.hypot(ux, uz));
                double side = ((point.getX() - here.getX()) * -uz + (point.getZ() - here.getZ()) * ux) / len;
                return score - Math.abs(d - preferred) * weight + brain.strafe * side * 0.4;
            }
            org.bukkit.Location goal = enemy != null ? enemy.loc() : null;
            if (goal != null) return score - Math.hypot(point.getX() - goal.getX(), point.getZ() - goal.getZ()) * 0.5;
            return brain.roam == null ? score
                    : score - Math.hypot(point.getX() - brain.roam.x() - .5, point.getZ() - brain.roam.z() - .5) * 0.5;
        });
        if (next == null) {
            var step = SpleefTactics.randomNeighbour(new SpleefTactics.Cell(here.getBlockX(), here.getBlockZ()), random, c -> {
                var point = new org.bukkit.Location(here.getWorld(), c.x() + .5, here.getBlockY(), c.z() + .5);
                return onFloor(point) && uk.co.playerready.pulsegames.core.bot.GroundNavigation.walkable(point)
                        && !crack.solid(c.x(), c.z());
            });
            if (step != null) next = new org.bukkit.Location(here.getWorld(), step.x() + .5, here.getBlockY(), step.z() + .5);
        }
        brain.wantsMove = next != null;
        return next;
    }

    /** A random intact block of the floor, or the middle of it, to head for when nobody is around. */
    private SpleefTactics.Cell pickRoam(int y) {
        if (floorBlocks.isEmpty()) return null;
        for (int tries = 0; tries < 8; tries++) {
            Block b = floorBlocks.get(random.applyAsInt(floorBlocks.size()));
            if (b.getY() == y && !b.getType().isAir()) return new SpleefTactics.Cell(b.getX(), b.getZ());
        }
        var mid = floors.get(0);
        return new SpleefTactics.Cell((mid.minX() + mid.maxX()) / 2, (mid.minZ() + mid.maxZ()) / 2);
    }

    private boolean reaches(Player bot, Block target) {
        org.bukkit.Location aim = target.getLocation().add(0.5, 0.5, 0.5);
        var delta = aim.toVector().subtract(bot.getEyeLocation().toVector());
        double distance = delta.length();
        if (distance < 0.01 || distance > 4.5) return false;
        var hit = bot.getWorld().rayTraceBlocks(bot.getEyeLocation(), delta.normalize(), distance + 0.05,
                org.bukkit.FluidCollisionMode.NEVER, true);
        return hit != null && target.equals(hit.getHitBlock());
    }

    private void dig(Player bot, Block target) {
        bot.getInventory().setHeldItemSlot(0);
        bot.swingMainHand();
        // Real BlockBreakEvent enforces game floor rules, protection and snowball awards.
        bot.breakBlock(target);
    }

    @Override
    public void botTick(Player bot, long tick, uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        if (!game.isBot(bot) || !game.isActive(bot)) return;
        now = tick; track(tick);
        Brain brain = brains.computeIfAbsent(bot.getUniqueId(), id -> new Brain());
        Target enemy = acquire(bot, brain, tick);
        if (enemy == null) { brain.enemy = null; return; }
        if (!enemy.id().equals(brain.enemy)) {
            brain.enemy = enemy.id();
            brain.reactAt = tick + 6 + random.applyAsInt(difficulty.profile().reactionJitter() * 3 + 1);
        }
        if (!enemy.live() || tick < brain.reactAt || tick < nextAction.getOrDefault(bot.getUniqueId(), 0L)) return;
        var profile = difficulty.profile();
        var me = bot.getLocation(); var at = enemy.loc();
        int y = me.getBlockY() - 1;
        double lead = Math.sqrt(me.distanceSquared(at)) / 2.0;
        var predicted = SpleefTactics.ahead(at.getX(), at.getZ(), enemy.vx(), enemy.vz(), isSplegg() ? lead : 8, 2);
        long delay = (isSplegg() ? difficulty.projectileDelayTicks() : difficulty.meleeDelayTicks()) + random.applyAsInt(9);
        double wrongPercent = profile.aimErrorDegrees() * 4;
        if (isSplegg()) {
            if (Math.abs(at.getY() - me.getY()) > 6 || me.distanceSquared(at) > 16 * 16) return;
            var aim = new org.bukkit.Location(bot.getWorld(), predicted.x() + .5, at.getBlockY() - .5, predicted.z() + .5);
            if (aim.getBlockX() == me.getBlockX() && aim.getBlockZ() == me.getBlockZ()) return;
            var delta = aim.toVector().subtract(bot.getEyeLocation().toVector());
            boolean hit = random.applyAsInt(100) < difficulty.accuracyPercent();
            double error = profile.aimErrorDegrees() * (hit ? 1 : 3) * (random.applyAsInt(201) - 100) / 100.0;
            if (throwEgg(bot, delta.rotateAroundY(Math.toRadians(error)))) nextAction.put(bot.getUniqueId(), tick + delay);
            return;
        }
        SpleefTactics.Grid g = grid(y);
        var self = new SpleefTactics.Cell(me.getBlockX(), me.getBlockZ());
        // Easy bots sometimes dig right beside themselves, which is how children also lose.
        if (difficulty == uk.co.playerready.pulsegames.core.bot.BotDifficulty.EASY && !isDecay() && random.applyAsInt(100) < 4) {
            var near = SpleefTactics.randomNeighbour(self, random, c -> g.solid(c.x(), c.z()));
            if (near != null) { dig(bot, game.world().getBlockAt(near.x(), y, near.z())); nextAction.put(bot.getUniqueId(), tick + delay); return; }
        }
        boolean sameLevel = Math.abs(at.getY() - me.getY()) <= 1.5;
        var targets = sameLevel
                ? SpleefTactics.classicTargets(g, self, me.getX(), me.getZ(), at.getX(), at.getZ(), enemy.vx(), enemy.vz(),
                        difficulty == uk.co.playerready.pulsegames.core.bot.BotDifficulty.HARD, isDecay() ? 2 : 1)
                : List.<SpleefTactics.Cell>of();
        for (var cell : targets) {
            Block block = game.world().getBlockAt(cell.x(), y, cell.z());
            if (!reaches(bot, block)) continue;
            nextAction.put(bot.getUniqueId(), tick + delay);
            if (random.applyAsInt(100) >= difficulty.accuracyPercent()) { bot.swingMainHand(); return; }
            Block chosen = block;
            if (random.applyAsInt(100) < wrongPercent) {
                var wrong = SpleefTactics.randomNeighbour(cell, random, c -> SpleefTactics.safeToDig(g, self, c, isDecay() ? 2 : 1)
                        && reaches(bot, game.world().getBlockAt(c.x(), y, c.z())));
                if (wrong != null) chosen = game.world().getBlockAt(wrong.x(), y, wrong.z());
            }
            dig(bot, chosen);
            nextAction.put(bot.getUniqueId(), tick + delay + 6 + random.applyAsInt(8)); // a beat to admire the hole
            return;
        }
        double distance = Math.sqrt(me.distanceSquared(at));
        if (distance > 4.5 && distance <= 14 && !bot.hasCooldown(Material.SNOWBALL)) {
            var inventory = bot.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                var item = inventory.getItem(slot);
                if (item == null || item.getType() != Material.SNOWBALL) continue;
                var aim = new org.bukkit.Location(bot.getWorld(), predicted.x() + .5, at.getBlockY() - .5, predicted.z() + .5);
                var delta = aim.toVector().subtract(bot.getEyeLocation().toVector());
                double error = profile.aimErrorDegrees() * (random.applyAsInt(201) - 100) / 100.0;
                if (item.getAmount() == 1) inventory.setItem(slot, null); else item.setAmount(item.getAmount() - 1);
                bot.setCooldown(Material.SNOWBALL, 10);
                bot.launchProjectile(org.bukkit.entity.Snowball.class, delta.rotateAroundY(Math.toRadians(error)).normalize().multiply(1.5));
                nextAction.put(bot.getUniqueId(), tick + delay);
                break;
            }
        }
    }

    @Override public void onCleanup() { nextAction.clear(); brains.clear(); motion.clear(); cracking.clear(); if (rounds != null) rounds.close(); }
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

    /** Snowballs and eggs still flying at round end must not reach the floor after it has been restored. */
    private void clearProjectiles() {
        if (game.world() == null) return;
        game.world().getEntitiesByClass(org.bukkit.entity.Snowball.class).forEach(org.bukkit.entity.Entity::remove);
        game.world().getEntitiesByClass(Egg.class).forEach(org.bukkit.entity.Entity::remove);
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        if (rounds != null && !rounds.active()) return;
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
