package uk.co.playerready.pulsegames.core.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.team.GameTeam;

import java.util.List;

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
        if (event.getPlayer().hasMetadata("NPC")) return;
        if (!plugin.instances().rejoin(event.getPlayer())) {
            uk.co.playerready.pulsegames.core.player.PlayerSanitiser.sanitise(event.getPlayer(), true);
            plugin.lobby().sendToLobby(event.getPlayer());
        }
        plugin.economy().handleDailyBonus(event.getPlayer());
        if (plugin.engagement() != null) plugin.engagement().onJoin(event.getPlayer());
    }

    /** Anything left on a player who has landed outside every live instance is cleared (the inventory is kept). */
    @EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata("NPC") || player.hasPermission("pulsegames.admin") || instanceOf(player) != null || plugin.instances().byWorld(player.getWorld()) != null) return;
        uk.co.playerready.pulsegames.core.player.PlayerSanitiser.sanitise(player, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().hasMetadata("NPC")) return;
        GameInstance instance = instanceOf(event.getPlayer());
        if (instance != null) plugin.instances().disconnect(event.getPlayer());
        plugin.playService().cancelPending(event.getPlayer());
        plugin.parties().handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onInput(org.bukkit.event.player.PlayerInputEvent event) {
        GameInstance instance = instanceOf(event.getPlayer());
        if (instance != null && instance.state() == GameState.RUNNING && instance.isActive(event.getPlayer()))
            instance.logic().onInput(event.getPlayer(), event.getInput());
    }

    // ---- movement -----------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.bots() != null && plugin.bots().isBot(player)) return;
        GameInstance instance = instanceOf(player);
        if (instance == null) {
            if (plugin.lobby().isLobbyWorld(player.getWorld())) {
                Location lobby = plugin.lobby().lobbyLocation();
                // Safety net: lobby damage is cancelled (incl. void), so a fall off
                // the hub would never stop. Return players who drop far below it.
                if (event.getTo().getY() < lobby.getY() - 48) {
                    player.teleport(lobby);
                    return;
                }
                // Re-arm the lobby double jump once back on the ground.
                if (!player.getAllowFlight() && player.getGameMode() == org.bukkit.GameMode.ADVENTURE
                        && player.isOnGround()) {
                    player.setAllowFlight(true);
                }
                plugin.cosmetics().playTrail(player);
            }
            return;
        }
        if (instance.state() == GameState.RUNNING && instance.isSpectator(player) && instance.world() != null
                && event.getTo().getY() < instance.logic().voidY() - 5) {
            // Ghosts that drift under the map are put back on the viewing platform.
            player.teleport(instance.arena().spectator(instance.world()));
            return;
        }
        if (instance.state() == GameState.RUNNING && instance.isAlive(player)) {
            instance.logic().onPlayerMove(event);
            if (event.isCancelled()) return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        boolean blockChanged = from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
        if (!blockChanged) return;
        if (instance.state() == GameState.RUNNING && instance.isAlive(player)
                && uk.co.playerready.pulsegames.core.economy.CosmeticsService.trailInMatch(instance.type().id())) {
            plugin.cosmetics().playTrail(player);
        }

        // Safety net for pre-game states: damage (incl. void) is cancelled until the
        // game is RUNNING, so a player who walks off the waiting lobby would fall
        // forever. Put them back on the arena's waiting platform.
        if ((instance.state() == GameState.WAITING || instance.state() == GameState.COUNTDOWN)
                && instance.world() != null) {
            Location arenaLobby = instance.arena().lobby(instance.world());
            if (to.getY() < arenaLobby.getY() - 48) {
                player.teleport(arenaLobby);
                return;
            }
        }

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
        if (instance.state() == GameState.RUNNING && instance.isAlive(player)) {
            instance.logic().onMove(player, from, to);
        }
    }

    /**
     * Players riding a vehicle (e.g. kart boats) do NOT fire PlayerMoveEvent - the
     * vehicle moves, not the player. Route the vehicle's movement to the same game
     * logic so checkpoints, item boxes and void falls work while mounted.
     */
    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (event.getVehicle().getPassengers().isEmpty()) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        for (var passenger : event.getVehicle().getPassengers()) {
            if (!(passenger instanceof Player player)) continue;
            GameInstance instance = instanceOf(player);
            if (instance == null || instance.state() != GameState.RUNNING || !instance.isAlive(player)) continue;
            if (instance.frozen()) {
                // Hold karts on the grid during the start countdown freeze.
                event.getVehicle().setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                continue;
            }
            if (to.getY() < instance.logic().voidY()) {
                instance.handleDeath(player, null);
                continue;
            }
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
        if (instance.state() != GameState.RUNNING || !instance.isActive(victim)) {
            event.setCancelled(true);
            return;
        }
        Player damager = resolveDamager(event);
        if (damager != null) {
            if (!logic.pvp() || (damager.equals(victim) && !logic.allowsSelfDamage(event))
                    || (!damager.equals(victim) && sameTeam(instance, victim, damager))
                    || !instance.isActive(damager) || instance.frozen()) {
                event.setCancelled(true);
                return;
            }
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
        if (damager != null && !damager.equals(victim)) instance.recordDamager(victim, damager);
        if (event.getFinalDamage() >= victim.getHealth()) {
            if (logic.allowsTotems() && (victim.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                    || victim.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING)) return;
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
        Player player = event.getEntity();
        GameInstance instance = instanceOf(player);
        // Inventories are plugin-managed everywhere on this server (lobby menu items,
        // game kits) - a vanilla death must never scatter them on the ground.
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setShouldDropExperience(false);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.isDead()) player.spigot().respawn();
            if (instance != null && instance.state() == GameState.RUNNING && instance.isAlive(player)) {
                instance.handleDeath(player, null);
            }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GameInstance instance = instanceOf(player);
        if (instance == null || instance.world() == null) {
            // Out-of-game deaths (e.g. /kill) would otherwise respawn at world spawn
            // with an empty hotbar - put them back in the lobby with the menu items.
            event.setRespawnLocation(plugin.lobby().lobbyLocation());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && instanceOf(player) == null) {
                    plugin.lobby().sendToLobby(player);
                }
            });
            return;
        }
        // Keep in-game respawns inside the instance world; game logic repositions them after.
        event.setRespawnLocation(instance.arena().spectator(instance.world()));
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

    @EventHandler
    public void onBlockDamage(org.bukkit.event.block.BlockDamageEvent event) {
        GameInstance instance = instanceOf(event.getPlayer());
        if (instance != null && instance.state() == GameState.RUNNING && instance.isAlive(event.getPlayer()))
            instance.logic().onBlockDamage(event);
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
        } else instance.logic().onBlockPlaced(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPlace(org.bukkit.event.entity.EntityPlaceEvent event) {
        if (event.getPlayer() == null) return;
        var instance = instanceOf(event.getPlayer());
        if (instance == null) return;
        if (instance.state() != GameState.RUNNING || !instance.isActive(event.getPlayer()) || instance.frozen()) {
            event.setCancelled(true); return;
        }
        instance.logic().onEntityPlace(event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onNonPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) return;
        var instance = plugin.instances().byWorld(event.getEntity().getWorld());
        if (instance != null) instance.logic().onEntityDamage(event);
    }

    /**
     * Explosions bypass BlockBreakEvent entirely. Without this a Bedwars TNT could take a
     * bed out without {@code destroyBed} ever running, leaving that team respawning off a
     * bed that no longer exists.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplosion(event.getEntity().getWorld(), event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosion(event.getBlock().getWorld(), event.blockList());
    }

    private void filterExplosion(World world, List<Block> blocks) {
        GameInstance instance = plugin.instances().byWorld(world);
        if (instance == null) {
            // Nothing outside an instance world is destructible on this server.
            blocks.clear();
            return;
        }
        MiniGame logic = instance.logic();
        blocks.removeIf(block -> !logic.canExplode(block));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        GameInstance instance = instanceOf(event.getPlayer());
        if (instance == null || instance.state() != GameState.RUNNING || instance.isSpectator(event.getPlayer())
                || !instance.logic().itemDrops()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GameInstance instance = instanceOf(player);
        if (instance == null || instance.state() != GameState.RUNNING || instance.isSpectator(player)
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
            } else if (plugin.lobby().isQuickPlayItem(event.getItem()) && event.getAction().isRightClick()) {
                event.setCancelled(true);
                plugin.quickPlay().quickPlay(player);
            } else if (plugin.lobby().isShopItem(event.getItem()) && event.getAction().isRightClick()) {
                event.setCancelled(true);
                plugin.tokenShop().openMain(player);
            }
            return;
        }
        // Out-of-round players are ghosts with a tool hotbar: only our items do anything.
        if (instance.state() == GameState.RUNNING && instance.isSpectator(player)) {
            event.setCancelled(true);
            if (event.getAction().isRightClick() && event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                instance.spectatorTools().use(player, event.getItem());
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
        if (ReadyVote.isItem(event.getItem()) && event.getAction().isRightClick()
                && (instance.state() == GameState.WAITING || instance.state() == GameState.COUNTDOWN)) {
            event.setCancelled(true);
            instance.voteReady(player);
            return;
        }
        if (instance.state() == GameState.RUNNING && instance.isAlive(player)) {
            instance.logic().onInteract(event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(org.bukkit.event.player.PlayerAnimationEvent event) {
        if (event.getAnimationType() != org.bukkit.event.player.PlayerAnimationType.ARM_SWING) return;
        GameInstance instance = instanceOf(event.getPlayer());
        if (instance != null && instance.state() == GameState.RUNNING && instance.isActive(event.getPlayer()))
            instance.logic().onSwing(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        GameInstance instance = instanceOf(event.getPlayer());
        if (instance == null) return;
        // Ghosts only get their tool hotbar: no riding, trading, leashing or poking armour stands.
        if (instance.state() == GameState.RUNNING && instance.isSpectator(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (instance.state() == GameState.RUNNING && instance.isAlive(event.getPlayer())) {
            instance.logic().onInteractEntity(event.getPlayer(), event.getRightClicked());
        }
    }

    /** Projectiles pass through out-of-round ghosts instead of being used up on them. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHitGhost(ProjectileHitEvent event) {
        if (event.getHitEntity() instanceof Player hit) {
            GameInstance instance = instanceOf(hit);
            if (instance != null && instance.state() == GameState.RUNNING && instance.isSpectator(hit)) {
                event.setCancelled(true);
            }
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
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GameInstance instance = instanceOf(player);
        if (instance != null) instance.logic().onInventoryDrag(player, event);
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
        if (instance.state() == GameState.RUNNING && instance.isSpectator(player)) {
            event.setCancelled(true);
            if (event.getView().getTopInventory().getHolder() instanceof SpectatorTools.MenuHolder menu
                    && event.getClickedInventory() == event.getView().getTopInventory()) {
                instance.spectatorTools().menuClick(player, menu, event.getSlot());
            }
            return;
        }
        instance.logic().onInventoryClick(player, event);
    }
}
