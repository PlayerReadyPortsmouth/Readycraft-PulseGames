package uk.co.playerready.pulsegames.core.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.team.GameTeam;

/** Routes Bukkit events to the game instance the player is in and enforces game rules. */
public final class GameListener implements Listener {

    private final PulseGamesPlugin plugin;

    public GameListener(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    private GameInstance instanceOf(Player player) {
        return plugin.instances().byPlayer(player);
    }

    // ---- connection ---------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.lobby().sendToLobby(event.getPlayer());
        plugin.economy().handleDailyBonus(event.getPlayer());
        if (plugin.getConfig().getBoolean("event.active", false)) {
            event.getPlayer().sendMessage(uk.co.playerready.pulsegames.core.util.Text.msg(
                    "<light_purple><b>" + plugin.getConfig().getString("event.name", "Event")
                            + "</b></light_purple> <gray>is live - <gold>"
                            + plugin.getConfig().getDouble("event.token-multiplier", 2.0)
                            + "x tokens</gold> on everything!"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        GameInstance instance = instanceOf(event.getPlayer());
        if (instance != null) instance.remove(event.getPlayer(), false);
        plugin.parties().handleQuit(event.getPlayer());
    }

    // ---- movement -----------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameInstance instance = instanceOf(player);
        if (instance == null) {
            if (plugin.lobby().isLobbyWorld(player.getWorld())) {
                // Re-arm the lobby double jump once back on the ground.
                if (!player.getAllowFlight() && player.getGameMode() == org.bukkit.GameMode.ADVENTURE
                        && player.isOnGround()) {
                    player.setAllowFlight(true);
                }
                plugin.cosmetics().playTrail(player);
            }
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        boolean blockChanged = from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
        if (!blockChanged) return;

        if (instance.state() == GameState.RUNNING && instance.frozen() && instance.isAlive(player)) {
            Location reset = from.clone();
            reset.setYaw(to.getYaw());
            reset.setPitch(to.getPitch());
            event.setTo(reset);
            return;
        }
        if (instance.state() == GameState.RUNNING && instance.isAlive(player)
                && to.getY() < instance.logic().voidY()) {
            instance.handleDeath(player, null);
            return;
        }
        // Assistants can't die; falling just puts them back at the spectator point.
        if (instance.isAssistant(player) && instance.world() != null
                && to.getY() < instance.logic().voidY()) {
            player.setFallDistance(0);
            player.teleport(instance.arena().spectator(instance.world()));
            return;
        }
        if (instance.state() == GameState.RUNNING && instance.isAlive(player)) {
            instance.logic().onMove(player, from, to);
        }
    }

    // ---- damage & death --------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        GameInstance instance = instanceOf(victim);
        if (instance == null) {
            // No damage anywhere outside games on this server.
            if (plugin.lobby().isLobbyWorld(victim.getWorld())) event.setCancelled(true);
            return;
        }
        MiniGame logic = instance.logic();
        if (instance.state() != GameState.RUNNING || !instance.isAlive(victim)) {
            event.setCancelled(true);
            return;
        }
        Player damager = resolveDamager(event);
        if (damager != null) {
            if (!logic.pvp() || damager.equals(victim) || sameTeam(instance, victim, damager)
                    || !instance.isAlive(damager) || instance.frozen()) {
                event.setCancelled(true);
                return;
            }
            instance.recordDamager(victim, damager);
            logic.onDamageByPlayer(victim, damager, (EntityDamageByEntityEvent) event);
            if (event.isCancelled()) return;
        } else {
            switch (event.getCause()) {
                case FALL -> {
                    if (!logic.fallDamage()) {
                        event.setCancelled(true);
                        return;
                    }
                }
                case VOID -> {
                    event.setCancelled(true);
                    instance.handleDeath(victim, null);
                    return;
                }
                default -> {
                    if (!logic.environmentalDamage()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
        logic.onDamage(victim, event);
        if (event.isCancelled()) return;
        if (event.getFinalDamage() >= victim.getHealth()) {
            event.setCancelled(true);
            instance.handleDeath(victim, damager);
        }
    }

    private Player resolveDamager(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return null;
        if (byEntity.getDamager() instanceof Player player) return player;
        if (byEntity.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    private boolean sameTeam(GameInstance instance, Player a, Player b) {
        GameTeam team = instance.teamOf(a);
        return team != null && team.contains(b);
    }

    /** Safety net: vanilla deaths shouldn't happen, but never drop items or show a death screen. */
    @EventHandler
    public void onVanillaDeath(PlayerDeathEvent event) {
        GameInstance instance = instanceOf(event.getEntity());
        if (instance == null) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setShouldDropExperience(false);
        Player player = event.getEntity();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.isDead()) player.spigot().respawn();
            if (instance.state() == GameState.RUNNING && instance.isAlive(player)) {
                instance.handleDeath(player, null);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GameInstance instance = instanceOf(player);
        if (instance == null || instance.state() != GameState.RUNNING || !instance.logic().hunger()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        GameInstance instance = plugin.instances().byWorld(event.getEntity().getWorld());
        if (instance == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        LivingEntity entity = event.getEntity();
        instance.logic().onEntityDeath(entity, entity.getKiller());
    }

    // ---- blocks & items -----------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameInstance instance = instanceOf(player);
        if (instance == null) {
            if (plugin.lobby().isLobbyWorld(player.getWorld()) && !player.hasPermission("pulsegames.admin")) {
                event.setCancelled(true);
            }
            return;
        }
        if (instance.state() != GameState.RUNNING || !instance.isAlive(player) || instance.frozen()
                || !instance.logic().canBreak(player, event.getBlock())) {
            event.setCancelled(true);
        } else {
            event.setDropItems(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameInstance instance = instanceOf(player);
        if (instance == null) {
            if (plugin.lobby().isLobbyWorld(player.getWorld()) && !player.hasPermission("pulsegames.admin")) {
                event.setCancelled(true);
            }
            return;
        }
        if (instance.state() != GameState.RUNNING || !instance.isAlive(player) || instance.frozen()
                || !instance.logic().canPlace(player, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        GameInstance instance = instanceOf(event.getPlayer());
        if (instance == null || instance.state() != GameState.RUNNING || !instance.logic().itemDrops()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GameInstance instance = instanceOf(player);
        if (instance == null || instance.state() != GameState.RUNNING || !instance.isAlive(player)
                || !instance.logic().itemPickup(player, event.getItem())) {
            event.setCancelled(true);
        }
    }

    // ---- interactions ------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GameInstance instance = instanceOf(player);
        if (instance == null) {
            if (plugin.lobby().isMenuItem(event.getItem()) && event.getAction().isRightClick()) {
                event.setCancelled(true);
                plugin.gameMenu().openGames(player);
            } else if (plugin.lobby().isShopItem(event.getItem()) && event.getAction().isRightClick()) {
                event.setCancelled(true);
                plugin.tokenShop().openMain(player);
            } else if (plugin.lobby().isBoostItem(event.getItem()) && event.getAction().isRightClick()
                    && plugin.lobby().isLobbyWorld(player.getWorld())
                    && !player.hasCooldown(Material.FEATHER)) {
                event.setCancelled(true);
                player.setCooldown(Material.FEATHER, 40);
                player.setVelocity(player.getLocation().getDirection().multiply(0.8)
                        .add(new org.bukkit.util.Vector(0, 0.8, 0)));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1f, 1.4f);
            }
            return;
        }
        if (event.getItem() != null && event.getItem().getType() == Material.RED_BED
                && event.getAction().isRightClick()
                && (instance.state() == GameState.WAITING || instance.state() == GameState.COUNTDOWN)) {
            event.setCancelled(true);
            plugin.playService().leave(player);
            return;
        }
        if (instance.state() == GameState.RUNNING && instance.isAlive(player)) {
            instance.logic().onInteract(event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        GameInstance instance = instanceOf(event.getPlayer());
        if (instance == null) return;
        if (instance.state() == GameState.RUNNING && instance.isAlive(event.getPlayer())) {
            instance.logic().onInteractEntity(event.getPlayer(), event.getRightClicked());
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) return;
        GameInstance instance = instanceOf(shooter);
        if (instance != null && instance.state() == GameState.RUNNING) {
            instance.logic().onProjectileHit(event);
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        GameInstance instance = instanceOf(event.getPlayer());
        if (instance == null) {
            Player player = event.getPlayer();
            // Lobby double jump.
            if (plugin.lobby().isLobbyWorld(player.getWorld())
                    && player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                event.setCancelled(true);
                player.setFlying(false);
                player.setAllowFlight(false);
                player.setVelocity(player.getLocation().getDirection().multiply(0.8)
                        .add(new org.bukkit.util.Vector(0, 0.8, 0)));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1f, 1.4f);
            }
            return;
        }
        if (instance.state() == GameState.RUNNING
                && instance.isAlive(event.getPlayer())
                && event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE
                && event.getPlayer().getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            instance.logic().onToggleFlight(event.getPlayer(), event);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GameInstance instance = instanceOf(player);
        if (instance == null) return;
        if (instance.state() == GameState.WAITING || instance.state() == GameState.COUNTDOWN) {
            event.setCancelled(true);
            return;
        }
        instance.logic().onInventoryClick(player, event);
    }
}
