package uk.co.playerready.pulsegames.games.avalanche;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;

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

    static Cuboid inset(Cuboid area, int minimum) {
        if (area.maxX() - area.minX() + 1 < minimum + 2
                || area.maxZ() - area.minZ() + 1 < minimum + 2) return area;
        return new Cuboid(area.minX()+1, area.minY(), area.minZ()+1,
                area.maxX()-1, area.maxY(), area.maxZ()-1);
    }

    private int interval() { return Math.max(10, game.arena().settings().getInt("shrink-interval", 15)); }
    private Cuboid nextArea() { return inset(area, Math.max(3, game.arena().settings().getInt("minimum-size", 7))); }

    private void removeEdges(java.util.Iterator<org.bukkit.block.Block> blocks, Cuboid keep) {
        if (closed || game.world() == null) { cleaning = false; return; }
        // Bounded work per tick, even with tall or unusually large map regions.
        int budget = 512;
        while (budget-- > 0 && blocks.hasNext()) {
            var block = blocks.next();
            if (!keep.containsColumn(block.getLocation())) block.setType(Material.AIR, false);
        }
        if (blocks.hasNext()) game.runLater(1, () -> removeEdges(blocks, keep));
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
        removeEdges(blocks, next);
    }

    @Override public void onCleanup() { closed = true; }
    @Override public void onEnd(java.util.List<Player> winners) { onCleanup(); }


    public AvalancheGame(GameInstance game) {
        super(game);
    }

    @Override
    public void onStart() {
        area = game.arena().region("arena");
        original = area;
        nextShrink = Math.max(10, game.arena().settings().getInt("shrink-start", 30));
        game.broadcast("<yellow>Incoming avalanche! Dodge the falling blocks!");
    }

    @Override
    public void onSecond(int gameTime) {
        if (area == null || gameTime < 5) return;
        shrink(gameTime);
        if (gameTime >= graceUntil) {
            for (Player player : java.util.List.copyOf(game.alivePlayers())) {
                if (!area.containsColumn(player.getLocation())) {
                    game.broadcast("<red>" + player.getName() + "</red> was caught outside the avalanche arena!");
                    game.eliminate(player);
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
            game.eliminate(victim);
        }
    }

    /** Everyone still alive at the end survives together. */
    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("survive-seconds", 180);
    }
}
