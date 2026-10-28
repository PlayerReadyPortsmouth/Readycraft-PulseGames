package uk.co.playerready.pulsegames.games.elytra;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.common.CheckpointRaceGame;

/**
 * Elytra Racing: glide through the checkpoint rings. Boost with firework rockets.
 * Modes: sprint (1 lap), grandprix (3 laps).
 */
public final class ElytraRacingGame extends CheckpointRaceGame {

    public ElytraRacingGame(GameInstance game) {
        super(game);
    }

    private static final long BOOST_COOLDOWN_MS = 3000;
    private static final long READOUT_MS = 250;

    private List<Cuboid> boosts = List.of();
    private final Map<UUID, Double> speed = new HashMap<>();
    private final Map<UUID, Long> lastReadout = new HashMap<>();
    private final Map<String, Long> lastBoost = new HashMap<>();

    @Override protected boolean racePack() { return true; }

    /** Blocks per second to km/h, for a friendlier readout. */
    static int kmh(double blocksPerSecond) { return (int) Math.round(blocksPerSecond * 3.6); }

    @Override
    protected String hudExtra(Player player) {
        Double v = speed.get(player.getUniqueId());
        return v == null ? "" : "<aqua>" + kmh(v) + " km/h";
    }

    /** Ring pass: particle burst at the ring and a chime. */
    @Override
    protected void onGatePassed(Player player, Progress p, Cuboid gate) {
        Location centre = gate.center(game.world());
        game.world().spawnParticle(Particle.END_ROD, centre, 40, 1.5, 1.5, 1.5, 0.15);
        player.playSound(player.getLocation(), "minecraft:block.amethyst_block.chime", 1f, 1.4f);
    }

    @Override
    public void onMove(Player player, Location from, Location to) {
        super.onMove(player, from, to);
        if (finishes == null || raceClosed() || !game.isAlive(player) || from.getWorld() != to.getWorld()) return;
        UUID id = player.getUniqueId();
        long now = nowMs();
        // PlayerMoveEvent fires per tick, so distance moved is roughly blocks per 1/20 s.
        speed.put(id, from.distance(to) * 20);
        for (int i = 0; i < boosts.size(); i++) {
            if (!boosts.get(i).contains(to)) continue;
            String key = id + ":" + i;
            if (now - lastBoost.getOrDefault(key, -BOOST_COOLDOWN_MS) < BOOST_COOLDOWN_MS) continue;
            lastBoost.put(key, now);
            refillRockets(player);
        }
        if (now - lastReadout.getOrDefault(id, 0L) >= READOUT_MS) {
            lastReadout.put(id, now);
            sendPlaceBar(player);
        }
    }

    /** Optional boost1..N regions top rockets back up to the round's rocket count. */
    private void refillRockets(Player player) {
        int max = game.arena().settings().getInt("rockets", 64);
        ItemStack rockets = player.getInventory().getItem(0);
        if (rockets == null || rockets.getType() != Material.FIREWORK_ROCKET) rockets = new ItemStack(Material.FIREWORK_ROCKET, 0);
        if (rockets.getAmount() >= max) return;
        rockets.setAmount(max);
        player.getInventory().setItem(0, rockets);
        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 1f, 1.2f);
        game.world().spawnParticle(Particle.FIREWORK, player.getLocation(), 25, 0.6, 0.6, 0.6, 0.05);
        player.sendActionBar(Text.mm("<gold>Boost ring! <gray>Rockets refilled"));
    }

    @Override
    protected boolean sweptCheckpoints() { return true; }

    @Override
    protected boolean validAuthoredReset(org.bukkit.Location location) {
        var world = location.getWorld();
        if (world == null || world != game.world() || !Double.isFinite(location.getX())
                || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())
                || !Float.isFinite(location.getYaw()) || !Float.isFinite(location.getPitch())
                || location.getY() <= world.getMinHeight() || location.getY() + 1.8 >= world.getMaxHeight()) return false;
        if (world.getWorldBorder() != null && !world.getWorldBorder().isInside(location)) return false;
        // The standing player must fit before gliding is reapplied; check every touched block.
        for (int x=(int)Math.floor(location.getX()-.3); x<=Math.floor(location.getX()+.3); x++)
            for (int z=(int)Math.floor(location.getZ()-.3); z<=Math.floor(location.getZ()+.3); z++) {
                if (!world.isChunkLoaded(x>>4,z>>4)) return false;
                for (int y=location.getBlockY(); y<=Math.floor(location.getY()+1.8); y++) {
                    var block=world.getBlockAt(x,y,z);
                    if (block.isLiquid() || !block.isPassable()) return false;
                }
            }
        return true;
    }

    @Override
    protected void afterReset(Player player, org.bukkit.Location target) {
        if (!validAuthoredReset(target)) return;
        player.setGliding(true);
        var direction=target.getDirection();
        direction.setY(Math.max(.15,direction.getY()));
        player.setVelocity(direction.normalize().multiply(.8));
    }

    @Override
    protected int laps() {
        return game.mode().id().equals("grandprix") ? game.arena().settings().getInt("laps", 3) : 1;
    }

    @Override
    public void onStart() {
        super.onStart();
        boosts = game.arena().regionsByPrefix("boost");
        for (Player player : game.alivePlayers()) {
            player.getInventory().setChestplate(unbreakable(new ItemStack(Material.ELYTRA)));
            player.getInventory().setItem(0, new ItemStack(Material.FIREWORK_ROCKET,
                    game.arena().settings().getInt("rockets", 64)));
        }
        game.broadcast("Fly through every checkpoint ring! Use rockets to boost.");
    }

    private ItemStack unbreakable(ItemStack item) {
        item.editMeta(meta -> meta.setUnbreakable(true));
        return item;
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        // Vanilla rocket-boost while gliding; nothing to do, but block other interactions.
        if (event.getItem() == null || event.getItem().getType() != Material.FIREWORK_ROCKET) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent event) {
        // Crashing isn't lethal - it just sends you back.
        if (event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL
                || event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            sendBack(victim);
        }
    }

    // Let the listener reach onDamage, which cancels the damage and resets the racer.
    @Override
    public boolean fallDamage() { return true; }

    @Override
    public int startFreezeSeconds() { return 5; }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 600);
    }
}
