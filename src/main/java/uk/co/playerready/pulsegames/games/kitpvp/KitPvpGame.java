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
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.common.SafeRespawn;
import uk.co.playerready.pulsegames.games.common.SpawnProtection;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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

    private static final double SPAWN_PROTECTION_SECONDS = 2;

    private final SpawnProtection protection = new SpawnProtection();
    private final KillStreaks streaks = new KillStreaks();
    private final BountyBoard bounties = new BountyBoard();
    private UUID bountyHolder;
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

    @Override public void onQuit(Player player) {
        nextBotAttack.remove(player.getUniqueId()); drawingBow.remove(player.getUniqueId());
        protection.clear(player.getUniqueId()); streaks.died(player.getUniqueId());
        if (player.getUniqueId().equals(bountyHolder)) { player.setGlowing(false); bountyHolder = null; }
    }
    @Override public void onCleanup() {
        nextBotAttack.clear(); drawingBow.clear(); protection.clear(); streaks.clear(); bounties.clear();
        clearBounty();
    }
    @Override public void onEnd(List<Player> winners) { clearBounty(); }

    private boolean isOitc() { return game.mode().rulesId().equals("oitc"); }

    private int targetKills() {
        return game.arena().settings().getInt("target-kills", isOitc() ? 20 : 15);
    }

    private Kit selectedKit(Player player) {
        String equipped = game.plugin().cosmetics().equippedKitId(player);
        Kit selected = equipped == null ? null : game.plugin().kits().get(equipped);
        return selected != null ? selected : game.plugin().kits().get(game.arena().settings().getString("kit", "warrior"));
    }

    @Override public String startProblem(List<Player> players) {
        if (!isOitc()) for (Player player : players) if (selectedKit(player) == null)
            return "A Kit PvP loadout is unavailable. Check the arena default kit and selected kits.";
        return null;
    }

    @Override
    public void onStart() {
        game.alivePlayers().forEach(this::equip);
        game.alivePlayers().forEach(p -> protection.protect(p.getUniqueId(), SPAWN_PROTECTION_SECONDS));
        if (!isOitc()) game.broadcast("<aqua>Choose a loadout with /pulsekits. Changes apply on your next spawn.");
        game.broadcast("First to <yellow>" + targetKills() + "</yellow> kills wins!");
    }

    private void equip(Player player) {
        if (isOitc()) {
            player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
            player.getInventory().setItem(1, new ItemStack(Material.BOW));
            player.getInventory().setItem(9, new ItemStack(Material.ARROW, 1));
        } else {
            Kit kit = selectedKit(player);
            if (kit != null) kit.apply(player);
        }
    }

    @Override
    public Location respawnLocation(Player player) {
        return SafeRespawn.forPlayer(game, player, ThreadLocalRandom.current());
    }

    @Override
    public void onRespawn(Player player) {
        nextBotAttack.remove(player.getUniqueId());
        drawingBow.remove(player.getUniqueId());
        equip(player);
        protection.protect(player.getUniqueId(), SPAWN_PROTECTION_SECONDS);
        player.sendActionBar(Text.mm("<aqua>Spawn protected for " + (int) SPAWN_PROTECTION_SECONDS + "s"));
        if (player.getUniqueId().equals(bountyHolder)) player.setGlowing(true);
        refreshBounty();
    }

    @Override
    public void onDamage(Player victim, org.bukkit.event.entity.EntityDamageEvent event) {
        if (protection.isProtected(victim.getUniqueId())) event.setCancelled(true);
    }

    @Override
    public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {
        if (protection.isProtected(victim.getUniqueId())) { event.setCancelled(true); return; }
        protection.clear(damager.getUniqueId()); // attacking ends your own protection
        if (isOitc() && event.getDamager() instanceof Arrow) {
            event.setDamage(1000); // one in the chamber
        }
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        streaks.died(victim.getUniqueId());
        if (killer != null) {
            game.addScore(killer, 1);
            game.stats().addKill(killer, game.type().id());
            game.broadcast("<red>" + victim.getName() + "</red> was slain by <yellow>" + killer.getName()
                    + "</yellow> <gray>(" + game.score(killer) + "/" + targetKills() + ")");
            if (isOitc()) {
                killer.getInventory().addItem(new ItemStack(Material.ARROW, 1));
            }
            rewardKill(victim, killer);
            if (sideScore(killer) >= targetKills()) {
                game.end(sideMembers(killer), "kills");
                return;
            }
        } else {
            game.broadcast("<red>" + victim.getName() + "</red> died");
        }
        if (victim.getUniqueId().equals(bountyHolder)) victim.setGlowing(false);
        game.respawn(victim, respawnDelaySeconds());
        refreshBounty();
    }

    /** Kill feed, streak rewards and bounty payout for one kill. */
    private void rewardKill(Player victim, Player killer) {
        int streak = streaks.kill(killer.getUniqueId());
        String feed = "<red>⚔ <white>" + victim.getName() + " <gray>· " + game.score(killer) + "/" + targetKills()
                + (streak >= 2 ? " <gold>· streak " + streak : "");
        killer.sendActionBar(Text.mm(feed));
        victim.sendActionBar(Text.mm("<gray>Defeated by <yellow>" + killer.getName()));
        if (victim.getUniqueId().equals(bountyHolder) && bounties.claim(killer.getUniqueId())) {
            game.plugin().economy().addMatchTokens(killer, BountyBoard.REWARD_TOKENS, "bounty");
            announce("<gold>★ " + killer.getName() + " claimed the bounty on " + victim.getName() + "!");
        }
        KillStreaks.Reward reward = isOitc() ? KillStreaks.Reward.NONE : KillStreaks.reward(streak);
        switch (reward) {
            case GOLDEN_APPLE -> {
                killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE));
                Text.title(killer, "<gold><b>3 KILL STREAK", "<yellow>Golden apple!");
                announce("<gold>" + killer.getName() + " is on a 3 kill streak!");
            }
            case SPEED -> {
                killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 10, 1));
                Text.title(killer, "<gold><b>5 KILL STREAK", "<yellow>Speed boost!");
                announce("<gold>" + killer.getName() + " is on a 5 kill streak!");
            }
            default -> { }
        }
    }

    private void announce(String miniMessage) {
        game.players().forEach(p -> p.sendActionBar(Text.mm(miniMessage)));
    }

    /** Marks the strict leader (3+ kills) with a glow and tells the lobby. */
    private void refreshBounty() {
        UUID leader = BountyBoard.leader(game.topScores()).orElse(null);
        if (java.util.Objects.equals(leader, bountyHolder)) return;
        clearBounty();
        Player holder = leader == null ? null : game.player(leader);
        if (holder == null) return;
        bountyHolder = leader;
        holder.setGlowing(true);
        announce("<gold>★ Bounty on " + holder.getName() + "! Defeat them for " + BountyBoard.REWARD_TOKENS + " tokens");
    }

    private void clearBounty() {
        if (bountyHolder == null) return;
        Player old = game.player(bountyHolder);
        if (old != null) old.setGlowing(false);
        bountyHolder = null;
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
