package uk.co.playerready.pulsegames.core.game;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.List;

/**
 * Per-instance game logic. One MiniGame object is created for every {@link GameInstance};
 * keep all round state in fields here. Override only the hooks you need.
 */
public abstract class MiniGame {

    protected final GameInstance game;

    protected MiniGame(GameInstance game) {
        this.game = game;
    }

    // ---- lifecycle -------------------------------------------------------

    /** Instance world has been cloned and loaded; arena is available. */
    public void onWorldReady() {}

    /** Player entered the waiting lobby. */
    public void onJoin(Player player) {}

    /** Player left (quit or /lobby) at any stage. */
    public void onQuit(Player player) {}

    public void onCountdownTick(int secondsLeft) {}

    /** Players have been teleported to their spawns; the round begins. */
    /** A non-null reason keeps the lobby waiting; no kits, statistics or models are started. */
    public String startProblem(List<Player> players) { return null; }

    public void onStart() {}

    /** Always called before removing players/world, including shutdown and aborted startup. */
    public void onCleanup() {}

    /** Called every second while RUNNING, with seconds elapsed since start. */
    public void onSecond(int gameTime) {}

    /** Winners decided (may be empty for a draw). Clean up entities/tasks here. */
    public void onEnd(List<Player> winners) {}

    public void onEliminated(Player player) {}

    // ---- death & respawning ----------------------------------------------

    /**
     * A player died in-game (lethal damage is intercepted; no vanilla death screen).
     * Default: announce, then respawn or eliminate depending on {@link #respawnable()}.
     */
    public void onDeath(Player victim, Player killer) {
        if (killer != null) {
            game.broadcast("<red>" + victim.getName() + "</red> was killed by <red>" + killer.getName());
            game.stats().addKill(killer, game.type().id());
        } else {
            game.broadcast("<red>" + victim.getName() + "</red> died");
        }
        if (respawnable()) {
            game.respawn(victim, respawnDelaySeconds());
        } else {
            game.eliminate(victim);
        }
    }

    /** Reject duplicate life-ending events during a game's own intermission. */
    public boolean acceptsDeath(Player player) { return true; }

    public boolean respawnable() { return false; }

    public int respawnDelaySeconds() { return 3; }

    public Location respawnLocation(Player player) { return game.spawnFor(player); }

    /** Called after a respawned player is teleported back in. */
    public void onRespawn(Player player) {}

    // ---- rules -------------------------------------------------------------

    public boolean pvp() { return false; }

    /** Let native held-totem resurrection run before intercepting lethal damage. */
    public boolean allowsTotems() { return false; }

    /**
     * Cooperative games are won or lost together, so the engine's FFA "last player
     * standing" rule must not fire when a teammate leaves or is downed.
     */
    public boolean cooperative() { return false; }

    /**
     * Hard cap on teams this arena can host, where the map needs one dedicated
     * feature per team (Bedwars: one bed region). Default: no game-specific limit.
     */
    public int maxTeams() { return Integer.MAX_VALUE; }

    /**
     * Hard cap on players this arena can host, where the map needs one dedicated
     * feature per player (Build Battle: one plot). Default: no game-specific limit.
     */
    public int arenaPlayerCap() { return Integer.MAX_VALUE; }

    public boolean fallDamage() { return false; }

    /** All other environmental damage (fire, lava, suffocation, mobs...). */
    public boolean environmentalDamage() { return true; }

    public boolean hunger() { return false; }

    public boolean canBreak(Player player, Block block) { return false; }

    public boolean canPlace(Player player, Block block) { return false; }
    public void onBlockPlaced(org.bukkit.event.block.BlockPlaceEvent event) {}
    public void onEntityPlace(org.bukkit.event.entity.EntityPlaceEvent event) {}
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {}
    public boolean allowsSelfDamage(org.bukkit.event.entity.EntityDamageEvent event) { return false; }

    /**
     * May an explosion remove this block? Games that own destructible state (Bedwars beds)
     * must route it through their own bookkeeping here, not just allow the removal.
     */
    public boolean canExplode(Block block) { return false; }

    public boolean itemDrops() { return false; }

    public boolean itemPickup(Player player, Item item) { return false; }

    public int timeLimitSeconds() { return 600; }

    /** Seconds players are frozen in place after the start teleport. */
    public int startFreezeSeconds() { return 3; }

    public org.bukkit.GameMode playGameMode() { return org.bukkit.GameMode.SURVIVAL; }

    /** Falling below this Y counts as a void death. */
    public double voidY() {
        return game.arena().settings().getDouble("void-y", -64);
    }

    /** Survival games reward survivors; co-op objectives must actually be completed. */
    public void onTimeUp() {
        game.end(cooperative() ? List.of() : game.alivePlayers(), "time");
    }

    // ---- event hooks -------------------------------------------------------

    /** Every player move, before block-change filtering; may constrain a stationary disguise. */
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {}

    public void onInventoryDrag(org.bukkit.entity.Player player, org.bukkit.event.inventory.InventoryDragEvent event) {}

    /** Block-change moves only (not head rotation). */
    public void onMove(Player player, Location from, Location to) {}

    public void onInteract(PlayerInteractEvent event) {}

    public void onSwing(Player player) {}

    public void onInteractEntity(Player player, Entity clicked) {}

    public void onProjectileHit(ProjectileHitEvent event) {}

    public void onToggleFlight(Player player, PlayerToggleFlightEvent event) {}

    /** A non-player entity died; killer may be null. */
    public void onEntityDeath(LivingEntity entity, Player killer) {}

    /** Any damage to an in-game player, after rule filtering, before lethality check. */
    public void onDamage(Player victim, EntityDamageEvent event) {}

    public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {}

    /** Clicks in any inventory while in this game (use for shops/menus). */
    public void onInventoryClick(Player player, InventoryClickEvent event) {}

    /** Sidebar lines (MiniMessage). Empty = default state lines. */
    public List<String> sidebar(Player player) { return List.of(); }
}
