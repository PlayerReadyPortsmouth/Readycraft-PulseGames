package uk.co.playerready.pulsegames.games.elytra;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.games.common.CheckpointRaceGame;

/**
 * Elytra Racing: glide through the checkpoint rings. Boost with firework rockets.
 * Modes: sprint (1 lap), grandprix (3 laps).
 */
public final class ElytraRacingGame extends CheckpointRaceGame {

    public ElytraRacingGame(GameInstance game) {
        super(game);
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
