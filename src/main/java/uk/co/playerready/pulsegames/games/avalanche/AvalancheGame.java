package uk.co.playerready.pulsegames.games.avalanche;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.common.HazardFeedback;
import uk.co.playerready.pulsegames.games.common.RegionSnapshot;
import uk.co.playerready.pulsegames.games.common.RoundController;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Avalanche: blocks rain from the sky and pile up - dodge or be buried.
 * One hit and you're out. Survive to the end (or be the last standing).
 */
public final class AvalancheGame extends MiniGame implements uk.co.playerready.pulsegames.core.bot.BotMovement {

    private Cuboid area;
    private Cuboid original;
    private int nextShrink;
    private int graceUntil;
    private boolean cleaning;
    private boolean closed;
    private long shrinkGeneration;
    private RoundController rounds;
    private final java.util.List<org.bukkit.entity.FallingBlock> inFlight = new java.util.ArrayList<>();
    private final java.util.Set<java.util.UUID> telegraphed = new java.util.HashSet<>();
    private final java.util.Set<String> nearMisses = new java.util.HashSet<>();

    /** A falling block gets its landing marker once it is within this many blocks of the ground (about a second out). */
    static final double TELEGRAPH_BLOCKS = 20;
    /** A block landing between a body's width and this far away is a near miss. */
    static final double NEAR_MISS_RADIUS = 2.0;
    static final double HIT_RADIUS = 0.9;

    public static boolean shouldTelegraph(double heightAboveLanding) {
        return heightAboveLanding >= 0 && heightAboveLanding <= TELEGRAPH_BLOCKS;
    }

    public static boolean isNearMiss(double horizontalDistance) {
        return horizontalDistance > HIT_RADIUS && horizontalDistance <= NEAR_MISS_RADIUS;
    }

    private boolean isRounds() { return RoundController.isRounds(game); }

    static Cuboid inset(Cuboid area, int minimum) {
        if (area.maxX() - area.minX() + 1 < minimum + 2
                || area.maxZ() - area.minZ() + 1 < minimum + 2) return area;
        return new Cuboid(area.minX()+1, area.minY(), area.minZ()+1,
                area.maxX()-1, area.maxY(), area.maxZ()-1);
    }

    private int interval() { return Math.max(10, game.arena().settings().getInt("shrink-interval", 15)); }
    private Cuboid nextArea() { return inset(area, Math.max(3, game.arena().settings().getInt("minimum-size", 7))); }

    private void removeEdges(java.util.Iterator<org.bukkit.block.Block> blocks, Cuboid keep, long generation) {
        if (closed || game.world() == null || generation != shrinkGeneration) { cleaning = false; return; }
        // Bounded work per tick, even with tall or unusually large map regions.
        int budget = 512;
        while (budget-- > 0 && blocks.hasNext()) {
            var block = blocks.next();
            if (!keep.containsColumn(block.getLocation())) block.setType(Material.AIR, false);
        }
        if (blocks.hasNext()) game.runLater(1, () -> removeEdges(blocks, keep, generation));
        else cleaning = false;
    }

    private void shrink(int time) {
        if (area == null || !game.arena().settings().getBoolean("shrinking", true)) return;
        Cuboid next = nextArea();
        if (next == area) return;
        if (time == nextShrink - 5) game.broadcast("<gold>The arena edges collapse in 5 seconds. Move inward!");
        if (time < nextShrink || cleaning) return;
        Cuboid previous = area;
        area = next;
        graceUntil = time + 5;
        nextShrink = time + interval();
        cleaning = true;
        game.broadcast("<red>The arena is shrinking! Move towards the centre!");
        int bottom = Math.max(game.world().getMinHeight(), Math.min(previous.minY()-1,
                game.arena().settings().getInt("void-y", previous.minY()-2) + 1));
        // Existing maps define the region from player feet upwards. Include the supporting
        // columns down to the void threshold so the platform really collapses.
        var edges = java.util.List.of(
                new Cuboid(previous.minX(),bottom,previous.minZ(),previous.minX(),previous.maxY(),previous.maxZ()),
                new Cuboid(previous.maxX(),bottom,previous.minZ(),previous.maxX(),previous.maxY(),previous.maxZ()),
                new Cuboid(next.minX(),bottom,previous.minZ(),next.maxX(),previous.maxY(),previous.minZ()),
                new Cuboid(next.minX(),bottom,previous.maxZ(),next.maxX(),previous.maxY(),previous.maxZ()));
        var blocks = edges.stream().flatMap(edge -> edge.blocks(game.world()).stream()).iterator();
        removeEdges(blocks, next, shrinkGeneration);
    }

    @Override public void onCleanup() { closed = true; shrinkGeneration++; if (rounds != null) rounds.close(); }
    @Override public void onEnd(java.util.List<Player> winners) { onCleanup(); }
    @Override public void onQuit(Player player) { if (rounds != null) rounds.playerLeft(player); }
    @Override public MiniGame.Cue eliminationSound() { return HazardFeedback.LOUD_ELIMINATION; }

    /** Support columns count too: the collapse removes them down to the void threshold. */
    private Cuboid snapshotBox(Cuboid arena) {
        int bottom = Math.max(game.world().getMinHeight(), Math.min(arena.minY() - 1,
                game.arena().settings().getInt("void-y", arena.minY() - 2) + 1));
        return new Cuboid(arena.minX(), bottom, arena.minZ(), arena.maxX(), arena.maxY(), arena.maxZ());
    }

    @Override public String startProblem(java.util.List<Player> players) {
        if (!isRounds()) return null;
        Cuboid arena = game.arena().region("arena");
        if (arena == null) return null;
        Cuboid box;
        try { box = snapshotBox(arena); } catch (IllegalArgumentException | NullPointerException e) {
            box = null;
        }
        if (box == null) {
            game.plugin().getLogger().warning("Avalanche rounds mode refused: the arena region is over the Cuboid limit.");
            return "Avalanche rounds need an arena under " + RegionSnapshot.MAX_BLOCKS + " blocks to snapshot";
        }
        return RegionSnapshot.refusal("Avalanche", java.util.List.of(box), game.plugin().getLogger()::warning);
    }

    private void out(Player player) {
        if (rounds != null) rounds.knockOut(player); else game.eliminate(player);
    }

    @Override public void onEliminated(Player player) {
        int survived = game.gameTime();
        game.addScore(player, survived);
        player.sendActionBar(Text.mm("<gray>You survived <white>" + Text.time(survived)));
        var alive = game.alivePlayers();
        if (alive.size() == 1) game.addScore(alive.get(0), survived);
        HazardFeedback.finalThree(game.players(), alive.size(), game.players().size());
    }

    /** Surviving the clock is the objective here; survivors are ranked by survival time / height. */
    @Override public boolean survivorsWinOnTimeout() { return true; }

    @Override public void onTimeUp() {
        if (rounds != null) { rounds.onTimeUp(); return; }
        // Everyone left survived the whole clock, so they tie at the top of the survival ranking.
        for (Player p : game.alivePlayers()) game.addScore(p, game.gameTime());
        super.onTimeUp();
    }

    @Override public java.util.List<String> sidebar(Player player) {
        return rounds != null ? rounds.sidebar(player) : java.util.List.of();
    }

    /** Landing markers and near-miss cues for blocks in flight; runs every few ticks. */
    private void telegraphTick() {
        if (closed || game.world() == null) return;
        inFlight.removeIf(block -> !block.isValid());
        for (var block : inFlight) {
            var at = block.getLocation();
            var hit = game.world().rayTraceBlocks(at, new org.bukkit.util.Vector(0, -1, 0), 80,
                    org.bukkit.FluidCollisionMode.NEVER, true);
            if (hit == null || hit.getHitPosition() == null) continue;
            double height = at.getY() - hit.getHitPosition().getY();
            if (!shouldTelegraph(height)) continue;
            Location landing = new Location(game.world(), at.getX(), hit.getHitPosition().getY() + 0.1, at.getZ());
            game.world().spawnParticle(block.getBlockData().getMaterial() == Material.ANVIL
                    ? org.bukkit.Particle.CRIT : org.bukkit.Particle.CLOUD, landing, 3, 0.3, 0.02, 0.3, 0.0);
            if (telegraphed.add(block.getUniqueId()) && block.getBlockData().getMaterial() == Material.ANVIL) {
                game.world().playSound(landing, "minecraft:block.anvil.place", 0.25f, 1.8f);
            }
            if (height <= 2.5) {
                for (Player p : rounds != null ? rounds.contestants() : game.alivePlayers()) {
                    double dx = p.getLocation().getX() - at.getX(), dz = p.getLocation().getZ() - at.getZ();
                    if (Math.abs(p.getLocation().getY() - landing.getY()) > 2.5) continue;
                    if (isNearMiss(Math.sqrt(dx * dx + dz * dz)) && nearMisses.add(block.getUniqueId() + ":" + p.getUniqueId())) {
                        p.sendActionBar(Text.mm("<yellow>Close call!"));
                        p.playSound(p.getLocation(), "minecraft:entity.arrow.hit_player", 0.6f, 1.8f);
                    }
                }
            }
        }
        if (telegraphed.size() > 512) { telegraphed.retainAll(inFlight.stream().map(org.bukkit.entity.Entity::getUniqueId).collect(java.util.stream.Collectors.toSet())); nearMisses.clear(); }
    }

    private void resetRound() {
        shrinkGeneration++;
        cleaning = false;
        area = original;
        nextShrink = Math.max(10, game.arena().settings().getInt("shrink-start", 30));
        graceUntil = 0;
        inFlight.clear();
        telegraphed.clear();
        nearMisses.clear();
    }


    public AvalancheGame(GameInstance game) {
        super(game);
    }

    @Override
    public void onStart() {
        area = game.arena().region("arena");
        original = area;
        nextShrink = Math.max(10, game.arena().settings().getInt("shrink-start", 30));
        game.runRepeating(4L, 4L, this::telegraphTick);
        if (isRounds() && area != null) {
            var snapshot = RegionSnapshot.capture(game.world(), java.util.List.of(snapshotBox(area)));
            rounds = new RoundController(game, new RoundController.Host() {
                @Override public void beginRound(int round) { resetRound(); }
                @Override public void resetWorld() { shrinkGeneration++; cleaning = false; }
            }, snapshot, 75, "Dodge the falling blocks - last one standing scores most");
            rounds.start();
            return;
        }
        game.broadcast("<yellow>Incoming avalanche! Dodge the falling blocks!");
    }

    @Override
    public void onSecond(int matchTime) {
        int gameTime = matchTime;
        if (rounds != null) {
            rounds.tick();
            if (!rounds.active()) return;
            gameTime = rounds.roundElapsed();
        }
        if (area == null || gameTime < 5) return;
        shrink(gameTime);
        if (gameTime >= graceUntil) {
            for (Player player : java.util.List.copyOf(rounds != null ? rounds.contestants() : game.alivePlayers())) {
                if (!area.containsColumn(player.getLocation())) {
                    game.broadcast("<red>" + player.getName() + "</red> was caught outside the avalanche arena!");
                    out(player);
                }
            }
        }
        int intensity = Math.min(2 + gameTime / 10, 14);
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < intensity; i++) {
            double x = random.nextInt(area.minX(), area.maxX() + 1) + 0.5;
            double z = random.nextInt(area.minZ(), area.maxZ() + 1) + 0.5;
            Location spawn = new Location(game.world(), x, area.maxY(), z);
            Material material = random.nextInt(5) == 0 ? Material.ANVIL : Material.SNOW_BLOCK;
            var falling = game.world().spawnFallingBlock(spawn, material.createBlockData());
            falling.setDropItem(false);
            falling.setHurtEntities(true);
            inFlight.add(falling);
        }
    }

    @Override
    public Location botDestination(Player bot) {
        if (area == null || !bot.getWorld().equals(game.world())) return null;
        Location here = bot.getLocation();
        // Only currently visible falling entities, never the random next-wave positions.
        var threats = bot.getWorld().getNearbyEntities(here, 12, 24, 12).stream()
                .filter(entity -> entity instanceof org.bukkit.entity.FallingBlock && entity.isValid())
                .filter(entity -> entity.getLocation().getY() > here.getY() - 1 && bot.hasLineOfSight(entity))
                .limit(128).map(entity -> entity.getLocation()).toList();
        Cuboid target = game.arena().settings().getBoolean("shrinking", true) ? nextArea() : area;
        if (threats.isEmpty() && target.containsColumn(here)) return null;
        return uk.co.playerready.pulsegames.core.bot.GroundNavigation.nextStep(here, original::containsColumn,
                point -> {
                    double dxEdge = Math.max(0, Math.max(target.minX() + .5 - point.getX(), point.getX() - target.maxX() - .5));
                    double dzEdge = Math.max(0, Math.max(target.minZ() + .5 - point.getZ(), point.getZ() - target.maxZ() - .5));
                    return -1000 * (dxEdge + dzEdge) + threats.stream().mapToDouble(threat -> {
                    double dx = point.getX() - threat.getX(), dz = point.getZ() - threat.getZ();
                    return -100.0 / Math.max(0.25, dx * dx + dz * dz);
                }).sum(); });
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALLING_BLOCK
                || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            event.setCancelled(true);
            game.broadcast("<red>" + victim.getName() + "</red> was buried by the avalanche!");
            out(victim);
        }
    }

    /** Everyone still alive at the end survives together. */
    @Override
    public int timeLimitSeconds() {
        if (isRounds()) return RoundController.matchSeconds(game, 75);
        return game.arena().settings().getInt("survive-seconds", 180);
    }
}
