package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.common.CheckpointRaceGame;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PulseKarts: kart (boat) racing with item boxes - shells, boosts and lightning.
 * Modes: sprint (1 lap), cup (3 laps).
 */
public final class PulseKartsGame extends CheckpointRaceGame {

    private List<Cuboid> itemBoxes = List.of();
    private final Map<UUID, Long> boxCooldown = new HashMap<>();

    public PulseKartsGame(GameInstance game) {
        super(game);
    }

    @Override
    protected int laps() {
        return game.mode().id().equals("cup") ? game.arena().settings().getInt("laps", 3) : 1;
    }

    @Override
    public void onStart() {
        super.onStart();
        itemBoxes = game.arena().regionsByPrefix("itembox");
        game.alivePlayers().forEach(this::giveKart);
        game.broadcast("Race! Drive through <yellow>item boxes</yellow> to grab power-ups!");
    }

    private void giveKart(Player player) {
        Boat boat = (Boat) game.world().spawnEntity(player.getLocation(), org.bukkit.entity.EntityType.OAK_BOAT);
        boat.addPassenger(player);
    }

    @Override
    public void onMove(Player player, Location from, Location to) {
        super.onMove(player, from, to);
        if (boxCooldownOver(player) && itemBoxes.stream().anyMatch(b -> b.contains(to))) {
            boxCooldown.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
            giveRandomItem(player);
        }
    }

    private boolean boxCooldownOver(Player player) {
        return boxCooldown.getOrDefault(player.getUniqueId(), 0L) < System.currentTimeMillis();
    }

    private void giveRandomItem(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
        switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> {
                ItemStack shell = new ItemStack(Material.SNOWBALL, 3);
                shell.editMeta(meta -> meta.displayName(Text.mm("<aqua><b>Shell</b> <gray>(throw to slow a racer)")));
                player.getInventory().addItem(shell);
                player.sendActionBar(Text.mm("<aqua>You got Shells!"));
            }
            case 1 -> {
                ItemStack boost = new ItemStack(Material.SUGAR, 2);
                boost.editMeta(meta -> meta.displayName(Text.mm("<yellow><b>Turbo</b> <gray>(right-click to boost)")));
                player.getInventory().addItem(boost);
                player.sendActionBar(Text.mm("<yellow>You got Turbo!"));
            }
            case 2 -> {
                ItemStack bolt = new ItemStack(Material.NETHER_STAR);
                bolt.editMeta(meta -> meta.displayName(Text.mm("<light_purple><b>Lightning</b> <gray>(zap the leader)")));
                player.getInventory().addItem(bolt);
                player.sendActionBar(Text.mm("<light_purple>You got Lightning!"));
            }
        }
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getItem() == null) return;
        Player player = event.getPlayer();
        switch (event.getItem().getType()) {
            case SUGAR -> {
                event.setCancelled(true);
                event.getItem().subtract();
                var vehicle = player.getVehicle();
                var mover = vehicle != null ? vehicle : player;
                mover.setVelocity(player.getLocation().getDirection().setY(0).normalize().multiply(2.2));
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.2f);
            }
            case NETHER_STAR -> {
                event.setCancelled(true);
                event.getItem().subtract();
                List<Player> standings = standings();
                if (!standings.isEmpty()) {
                    Player leader = standings.get(0);
                    if (leader.equals(player) && standings.size() > 1) leader = standings.get(1);
                    leader.getWorld().strikeLightningEffect(leader.getLocation());
                    leader.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3));
                    game.broadcast("<light_purple>" + player.getName() + "</light_purple> zapped <yellow>"
                            + leader.getName() + "</yellow>!");
                }
            }
            default -> { }
        }
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Snowball && event.getHitEntity() instanceof Player hit
                && game.isAlive(hit)) {
            hit.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 2));
            hit.playSound(hit.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
            hit.sendActionBar(Text.mm("<red>Hit by a shell!"));
        }
    }

    /** Fell off the track: back to the last checkpoint with a fresh kart. */
    @Override
    protected void sendBack(Player player) {
        if (player.getVehicle() instanceof Boat boat) {
            boat.eject();
            boat.remove();
        }
        super.sendBack(player);
        giveKart(player);
    }

    @Override
    public void onQuit(Player player) {
        super.onQuit(player);
        boxCooldown.remove(player.getUniqueId());
    }

    @Override
    public void onEnd(List<Player> winners) {
        game.world().getEntitiesByClass(Boat.class).forEach(Boat::remove);
    }

    @Override
    public int startFreezeSeconds() { return 5; }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 600);
    }
}
