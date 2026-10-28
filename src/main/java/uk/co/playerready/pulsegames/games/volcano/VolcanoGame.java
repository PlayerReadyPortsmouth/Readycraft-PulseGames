package uk.co.playerready.pulsegames.games.volcano;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.common.HazardFeedback;
import uk.co.playerready.pulsegames.games.common.RegionSnapshot;
import uk.co.playerready.pulsegames.games.common.RoundController;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Volcano: the lava rises - climb for your life!
 * Modes: classic, eruption (faster lava + flaming rocks).
 */
public final class VolcanoGame extends MiniGame implements uk.co.playerready.pulsegames.core.bot.BotMovement {

    /** Block writes per tick while a layer floods; a whole plane in one tick stalls the server. */
    private static final int BLOCKS_PER_TICK = 1024;

    private final uk.co.playerready.pulsegames.core.bot.SurfaceMemory memory = new uk.co.playerready.pulsegames.core.bot.SurfaceMemory();
    private Cuboid area;
    private int lavaY;
    private boolean flooding;
    private int nextRiseAt;
    private boolean closed;
    private long floodGeneration;
    private int startLavaY;
    private int lastRiseAt;
    private int lastInterval = 1;
    private RoundController rounds;
    private net.kyori.adventure.bossbar.BossBar bar;
    private Location pendingRock;

    public VolcanoGame(GameInstance game) {
        super(game);
    }

    private boolean isEruption() { return game.mode().id().equals("eruption"); }

    static int riseInterval(int initial, int accelerationSeconds, int elapsed) {
        return Math.max(1, Math.max(1, initial) - Math.max(0, elapsed) / Math.max(1, accelerationSeconds));
    }

    /**
     * The late rise rate is capped. Lava climbing a block a second outpaces what a child on a tall
     * mountain can climb, so the interval never drops below {@code min-lava-interval} (default 2 s).
     */
    static int cappedInterval(int raw, int minimum) {
        return Math.max(Math.max(1, minimum), raw);
    }

    private int lavaInterval(int elapsed) {
        int raw = riseInterval(game.arena().settings().getInt("lava-interval", isEruption() ? 3 : 4),
                game.arena().settings().getInt("lava-acceleration-seconds", 30), elapsed);
        return cappedInterval(raw, game.arena().settings().getInt("min-lava-interval", 2));
    }

    private boolean isRounds() { return RoundController.isRounds(game); }

    private void out(Player player) {
        if (rounds != null) rounds.knockOut(player); else game.eliminate(player);
    }

    private List<Player> contestants() { return rounds != null ? rounds.contestants() : game.alivePlayers(); }

    private void resetLava() {
        closed = false;
        floodGeneration++;
        flooding = false;
        lavaY = area != null ? Math.max(area.minY(), Math.min(area.maxY(),
                game.arena().settings().getInt("lava-start-y", area.minY()))) : 0;
        startLavaY = lavaY;
        nextRiseAt = Math.max(1, game.arena().settings().getInt("lava-grace-seconds", isEruption() ? 3 : 5));
        lastRiseAt = 0;
        lastInterval = nextRiseAt;
        pendingRock = null;
    }

    @Override public String startProblem(List<Player> players) {
        if (!isRounds()) return null;
        Cuboid arena = game.arena().region("arena");
        return arena != null && arena.volume() > 300_000
                ? "Volcano rounds need an arena under 300000 blocks to snapshot" : null;
    }

    @Override
    public void onStart() {
        area = game.arena().region("arena");
        resetLava();
        bar = net.kyori.adventure.bossbar.BossBar.bossBar(Text.mm("<red>Next rise"), 0f,
                net.kyori.adventure.bossbar.BossBar.Color.RED, net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);
        game.players().forEach(p -> p.showBossBar(bar));
        if (isRounds() && area != null) {
            rounds = new RoundController(game, new RoundController.Host() {
                @Override public void beginRound(int round) { resetLava(); }
                @Override public void resetWorld() { floodGeneration++; flooding = false; }
            }, RegionSnapshot.capture(game.world(), List.of(area)), 75,
                    "Climb above the lava - last one standing scores most");
            rounds.start();
            return;
        }
        game.broadcast("<red>The volcano is erupting - climb above the lava!");
    }

    private void hideBar() {
        if (bar == null) return;
        for (Player p : game.everyone()) p.hideBossBar(bar);
    }

    @Override
    public void onSecond(int matchTime) {
        if (closed || area == null) return;
        int gameTime = matchTime;
        if (rounds != null) {
            rounds.tick();
            if (!rounds.active()) { if (bar != null) bar.progress(0f); return; }
            gameTime = rounds.roundElapsed();
        }
        if (!flooding && gameTime >= nextRiseAt && lavaY <= area.maxY()) {
            lastRiseAt = gameTime;
            lastInterval = lavaInterval(gameTime);
            nextRiseAt = gameTime + lastInterval;
            flooding = true;
            floodSlice(lavaY, 0, floodGeneration);
            lavaY++;
            float pitch = HazardFeedback.risingPitch(lavaY - startLavaY, Math.max(2, area.maxY() - startLavaY + 1));
            for (Player p : game.everyone()) p.playSound(p.getLocation(), "minecraft:block.lava.pop", 0.8f, pitch);
        }
        updateFeedback(gameTime);
        if (isEruption() && gameTime > 15) {
            // The smoke column goes up a second before the rock drops, so the target is readable.
            var random = ThreadLocalRandom.current();
            if (gameTime % 3 == 2) {
                pendingRock = new Location(game.world(), random.nextInt(area.minX(), area.maxX() + 1) + 0.5,
                        area.maxY(), random.nextInt(area.minZ(), area.maxZ() + 1) + 0.5);
                smokeColumn(pendingRock);
            } else if (gameTime % 3 == 0 && pendingRock != null) {
                smokeColumn(pendingRock);
                var rock = game.world().spawnFallingBlock(pendingRock, Material.MAGMA_BLOCK.createBlockData());
                rock.setDropItem(false);
                rock.setHurtEntities(true);
                pendingRock = null;
            }
        }
    }

    private void smokeColumn(Location top) {
        for (int y = (int) top.getY(); y >= Math.max(lavaY, area.minY()); y -= 2) {
            game.world().spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, top.getX(), y, top.getZ(), 1, 0.15, 0, 0.15, 0.01);
        }
    }

    /** "Lava is N blocks below you" on the action bar and the next-rise bossbar. */
    private void updateFeedback(int gameTime) {
        for (Player p : contestants()) {
            int gap = Math.max(0, (int) Math.floor(p.getLocation().getY()) - lavaY);
            p.sendActionBar(Text.mm((gap <= 2 ? "<red>" : "<yellow>") + "Lava is <white>" + gap + "</white> block"
                    + (gap == 1 ? "" : "s") + " below you"));
        }
        if (bar != null) {
            int left = Math.max(0, nextRiseAt - gameTime);
            bar.name(Text.mm("<red>Next rise in <white>" + left + "s <gray>(Y=" + lavaY + ")"));
            bar.progress(Math.max(0f, Math.min(1f, 1f - left / (float) Math.max(1, lastInterval))));
        }
    }

    /** Budget scanned cells, not rows: even a very long thin region cannot exceed this tick's limit. */
    private void floodSlice(int y, long cursor, long generation) {
        if (closed || generation != floodGeneration || game.world() == null) return;
        long depth = (long) area.maxZ() - area.minZ() + 1;
        long cells = ((long) area.maxX() - area.minX() + 1) * depth;
        long end = Math.min(cells, cursor + BLOCKS_PER_TICK);
        for (long cell = cursor; cell < end; cell++) {
            int x = (int) (area.minX() + cell / depth);
            int z = (int) (area.minZ() + cell % depth);
            Block block = game.world().getBlockAt(x, y, z);
            if (block.getType().isAir() || block.getType() == Material.WATER) block.setType(Material.LAVA);
        }
        if (end >= cells) { flooding = false; return; }
        game.runLater(1L, () -> floodSlice(y, end, generation));
    }

    @Override
    public Location botDestination(Player bot) {
        if (area == null || !bot.getWorld().equals(game.world())) return null;
        memory.remember(bot);
        Location here = bot.getLocation();
        var rocks = game.world().getNearbyEntities(here, 12, 24, 12).stream()
                .filter(e -> e instanceof org.bukkit.entity.FallingBlock && e.isValid())
                .filter(e -> e.getLocation().getY() > here.getY() - 1 && bot.hasLineOfSight(e))
                .limit(128).map(e -> e.getLocation()).toList();
        return memory.nextStep(bot, area::contains,
                p -> p.getY() * 12 - memory.penalty(bot, p) - rocks.stream().mapToDouble(rock -> {
                    double dx = p.getX() - rock.getX(), dz = p.getZ() - rock.getZ();
                    return 100.0 / Math.max(0.25, dx * dx + dz * dz);
                }).sum());
    }
    @Override public void onCleanup() {
        closed = true; floodGeneration++; flooding = false; memory.clear(); hideBar();
        if (rounds != null) rounds.close();
    }
    @Override public void onEnd(List<Player> winners) { hideBar(); }
    @Override public void onQuit(Player player) {
        if (bar != null) player.hideBossBar(bar);
        if (rounds != null) rounds.playerLeft(player);
    }
    @Override public MiniGame.Cue eliminationSound() { return HazardFeedback.LOUD_ELIMINATION; }

    @Override public void onEliminated(Player player) {
        game.addScore(player, game.gameTime());
        HazardFeedback.finalThree(game.players(), game.alivePlayers().size(), game.players().size());
    }

    /** Survivors rank by how high they climbed: everyone tops the clock, height breaks the tie. */
    /** Surviving the clock is the objective here; survivors are ranked by survival time / height. */
    @Override public boolean survivorsWinOnTimeout() { return true; }

    @Override public void onTimeUp() {
        if (rounds != null) { rounds.onTimeUp(); return; }
        List<Player> climbers = new java.util.ArrayList<>(game.alivePlayers());
        climbers.sort(java.util.Comparator.comparingDouble((Player p) -> p.getLocation().getY()));
        for (int i = 0; i < climbers.size(); i++) game.addScore(climbers.get(i), game.gameTime() + i + 1);
        if (!climbers.isEmpty()) game.broadcast("<gold>Highest climber: <yellow>" + climbers.get(climbers.size() - 1).getName());
        super.onTimeUp();
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent event) {
        switch (event.getCause()) {
            case LAVA, FIRE, FIRE_TICK -> {
                event.setCancelled(true);
                game.broadcast("<red>" + victim.getName() + "</red> was swallowed by the lava!");
                out(victim);
            }
            case FALLING_BLOCK -> {
                event.setCancelled(true);
                game.broadcast("<red>" + victim.getName() + "</red> was crushed by a volcanic rock!");
                out(victim);
            }
            default -> { }
        }
    }

    @Override
    public boolean fallDamage() { return false; }

    @Override
    public List<String> sidebar(Player player) {
        if (rounds != null) {
            List<String> lines = new java.util.ArrayList<>(rounds.sidebar(player));
            lines.add("<gray>Lava level: <red>Y=" + lavaY);
            return lines;
        }
        return List.of(
                "<gray>Lava level: <red>Y=" + lavaY,
                "<gray>Alive: <white>" + game.alivePlayers().size());
    }

    @Override
    public int timeLimitSeconds() {
        if (isRounds()) return RoundController.matchSeconds(game, 75);
        return game.arena().settings().getInt("survive-seconds", 300);
    }
}
