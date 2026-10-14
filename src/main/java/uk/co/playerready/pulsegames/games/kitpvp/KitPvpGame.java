package uk.co.playerready.pulsegames.games.kitpvp;

import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.EquipmentSlot;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import uk.co.playerready.pulsegames.core.game.GameState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.kit.Kit;

import java.util.ArrayList;
import java.util.List;

/**
 * Kit PvP: free-for-all arena fighting with respawns.
 * Modes: ffa (first to kill target), oitc (One in the Chamber: bow one-shots, arrow back per kill).
 */
public final class KitPvpGame extends MiniGame {

    public KitPvpGame(GameInstance game) {
        super(game);
    }

    private final Map<UUID, Long> nextBotAttack = new HashMap<>();
    private final java.util.Set<UUID> drawingBow = new java.util.HashSet<>();

    /** Real arrows, finite ammunition and ordinary melee; the service supplies a visible opponent. */
    public boolean botCombat(Player bot, LivingEntity enemy, long tick, BotDifficulty difficulty) {
        if (!isOitc()) return false;
        if (game.state() != GameState.RUNNING || game.frozen() || !game.isBot(bot) || !game.isActive(bot)
                || !(enemy instanceof Player target) || !game.isActive(target)
                || !bot.getWorld().equals(target.getWorld()) || !bot.hasLineOfSight(target)
                || (game.teamOf(bot) != null && game.teamOf(bot).contains(target))) return true;
        double distance = bot.getLocation().distanceSquared(target.getLocation());
        if (distance > 24 * 24) return true;
        var inventory = bot.getInventory();
        int arrowSlot = inventory.first(Material.ARROW);
        int bowSlot = hotbarSlot(bot, Material.BOW);
        boolean ranged = arrowSlot >= 0 && bowSlot >= 0 && distance > 3 * 3;
        if (ranged) {
            inventory.setHeldItemSlot(bowSlot);
            if (drawingBow.add(bot.getUniqueId())) nextBotAttack.merge(bot.getUniqueId(), tick + 20, Math::max);
        }
        else {
            drawingBow.remove(bot.getUniqueId());
            int swordSlot = hotbarSlot(bot, Material.WOODEN_SWORD);
            if (swordSlot >= 0) inventory.setHeldItemSlot(swordSlot);
        }
        // A full bow draw takes one second; acquiring a target cannot produce an instant arrow.
        long ready = nextBotAttack.computeIfAbsent(bot.getUniqueId(), ignored -> tick + (ranged ? 20 : difficulty.meleeDelayTicks()));
        if (tick < ready) return true;
        if (ranged) {
            nextBotAttack.put(bot.getUniqueId(), tick + Math.max(20, difficulty.projectileDelayTicks()));
            var delta = target.getEyeLocation().toVector().subtract(bot.getEyeLocation().toVector());
            // Ordinary ballistic compensation, with bounded difficulty-dependent spread, never homing.
            delta.setY(delta.getY() + Math.sqrt(distance) * 0.08);
            delta.normalize();
            double spread = (100 - difficulty.accuracyPercent()) / 250.0;
            var random = ThreadLocalRandom.current();
            delta.add(new org.bukkit.util.Vector(random.nextDouble(-spread, spread),
                    random.nextDouble(-spread, spread), random.nextDouble(-spread, spread)));
            Arrow arrow = bot.launchProjectile(Arrow.class, delta.normalize().multiply(3));
            if (!arrow.isValid()) return true; // A protection plugin may cancel ProjectileLaunchEvent.
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            var event = new EntityShootBowEvent(bot, inventory.getItem(bowSlot), inventory.getItem(arrowSlot),
                    arrow, EquipmentSlot.HAND, 1.0f, true);
            game.plugin().getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) { arrow.remove(); return true; }
            if (event.getProjectile() != arrow) arrow.remove();
            if (event.shouldConsumeItem()) {
                var ammo = inventory.getItem(arrowSlot);
                if (ammo != null && ammo.getType() == Material.ARROW) {
                    if (ammo.getAmount() <= 1) inventory.setItem(arrowSlot, null);
                    else ammo.setAmount(ammo.getAmount() - 1);
                }
            }
            bot.swingMainHand();
        } else if (distance <= 2.8 * 2.8) {
            nextBotAttack.put(bot.getUniqueId(), tick + difficulty.meleeDelayTicks());
            if (ThreadLocalRandom.current().nextInt(100) < difficulty.accuracyPercent()
                    && bot.getAttackCooldown() >= 0.9f) {
                bot.swingMainHand();
                bot.attack(target);
            }
        }
        return true;
    }

    private static int hotbarSlot(Player bot, Material material) {
        for (int slot = 0; slot < 9; slot++) {
            var item = bot.getInventory().getItem(slot);
            if (item != null && item.getType() == material) return slot;
        }
        return -1;
    }

    @Override public void onQuit(Player player) { nextBotAttack.remove(player.getUniqueId()); drawingBow.remove(player.getUniqueId()); }
    @Override public void onCleanup() { nextBotAttack.clear(); drawingBow.clear(); }

    private boolean isOitc() { return game.mode().rulesId().equals("oitc"); }

    private int targetKills() {
        return game.arena().settings().getInt("target-kills", isOitc() ? 20 : 15);
    }

    @Override
    public void onStart() {
        game.alivePlayers().forEach(this::equip);
        if (!isOitc()) game.broadcast("<aqua>Choose a loadout with /pulsekits. Changes apply on your next spawn.");
        game.broadcast("First to <yellow>" + targetKills() + "</yellow> kills wins!");
    }

    private void equip(Player player) {
        if (isOitc()) {
            player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
            player.getInventory().setItem(1, new ItemStack(Material.BOW));
            player.getInventory().setItem(9, new ItemStack(Material.ARROW, 1));
        } else {
            // Token-shop kit unlocks override the arena default.
            String unlocked = game.plugin().cosmetics().equippedKitId(player);
            Kit kit = unlocked != null ? game.plugin().kits().get(unlocked) : null;
            if (kit == null) {
                kit = game.plugin().kits().get(game.arena().settings().getString("kit", "warrior"));
            }
            if (kit != null) kit.apply(player);
        }
    }

    @Override
    public void onRespawn(Player player) {
        nextBotAttack.remove(player.getUniqueId());
        drawingBow.remove(player.getUniqueId());
        equip(player);
    }

    @Override
    public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {
        if (isOitc() && event.getDamager() instanceof Arrow) {
            event.setDamage(1000); // one in the chamber
        }
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        if (killer != null) {
            game.addScore(killer, 1);
            game.stats().addKill(killer, game.type().id());
            game.broadcast("<red>" + victim.getName() + "</red> was slain by <yellow>" + killer.getName()
                    + "</yellow> <gray>(" + game.score(killer) + "/" + targetKills() + ")");
            if (isOitc()) {
                killer.getInventory().addItem(new ItemStack(Material.ARROW, 1));
            }
            if (sideScore(killer) >= targetKills()) {
                game.end(sideMembers(killer), "kills");
                return;
            }
        } else {
            game.broadcast("<red>" + victim.getName() + "</red> died");
        }
        game.respawn(victim, respawnDelaySeconds());
    }

    @Override
    public void onTimeUp() {
        int best = game.players().stream().mapToInt(this::sideScore).max().orElse(0);
        List<Player> leaders = game.players().stream().filter(p -> sideScore(p) == best).toList();
        boolean oneSide = game.mode().isTeams() ? leaders.stream().map(game::teamOf).distinct().count() == 1 : leaders.size() == 1;
        game.end(best > 0 && oneSide ? leaders : List.of(), "time");
    }

    private List<Player> sideMembers(Player player) {
        var team = game.teamOf(player);
        return team == null ? List.of(player) : game.players().stream().filter(team::contains).toList();
    }

    private int sideScore(Player player) { return sideMembers(player).stream().mapToInt(game::score).sum(); }

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Your kills: <white>" + game.score(player));
        if (game.mode().isTeams()) lines.add("<gray>Team kills: <white>" + sideScore(player));
        lines.add("<gray>Target: <yellow>" + targetKills());
        game.topScores().stream().limit(3).forEach(e -> {
            Player p = game.player(e.getKey());
            if (p != null) lines.add("<gray>" + p.getName() + ": <white>" + e.getValue());
        });
        return lines;
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public boolean respawnable() { return true; }

    @Override
    public int timeLimitSeconds() { return 600; }
}
