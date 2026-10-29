package uk.co.playerready.pulsegames.games.villagerdefense;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.bot.BotObjectives;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Villager Defense: co-op - protect the Mayor from waves of zombies.
 * Modes: easy (5 waves), hard (10 waves, tougher mobs).
 * Arena: settings villager ("x,y,z"), regions mobspawn1..N.
 */
public final class VillagerDefenseGame extends MiniGame implements BotObjectives {

    private Villager mayor;
    private int wave = 0;
    private int breakTimer = 5;
    private boolean waveActive;
    private int pendingSpawns;
    private final Set<UUID> waveZombies = new HashSet<>();
    private List<Cuboid> mobSpawns = List.of();

    public VillagerDefenseGame(GameInstance game) {
        super(game);
    }

    private boolean isHard() { return game.mode().id().equals("hard"); }

    private int totalWaves() {
        return game.arena().settings().getInt("waves", isHard() ? 10 : 5);
    }

    @Override
    public void onStart() {
        mobSpawns = game.arena().regionsByPrefix("mobspawn");
        Location home = game.arena().settingLocation("villager", game.world());
        if (home == null) home = game.arena().spawn(0, game.world());
        mayor = (Villager) game.world().spawnEntity(home, EntityType.VILLAGER);
        mayor.customName(Text.mm("<gold><b>The Mayor"));
        mayor.setCustomNameVisible(true);
        mayor.setAI(false);
        mayor.setRemoveWhenFarAway(false);
        game.alivePlayers().forEach(this::equipDefender);
        game.broadcast("Protect <gold>The Mayor</gold> for <yellow>" + totalWaves() + "</yellow> waves!");
    }

    /** Base kit plus the wave milestones already reached, so a respawn isn't a demotion. */
    private void equipDefender(Player player) {
        player.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.BOW));
        player.getInventory().setItem(9, new ItemStack(Material.ARROW, 32));
        if (wave >= 2) player.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        if (wave >= 4) player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        if (wave >= 6) player.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
    }

    /** {@link GameInstance#respawn} resets the player, so the kit has to be handed back. */
    @Override
    public void onRespawn(Player player) {
        equipDefender(player);
    }

    @Override
    public void onSecond(int gameTime) {
        if (mayor == null || mayor.isDead()) {
            game.broadcast("<red><b>The Mayor has fallen!</b></red> <gray>The village is lost...");
            game.end(List.of(), "mayor-died");
            return;
        }
        if (waveActive) {
            waveZombies.removeIf(id -> {
                var entity = game.world().getEntity(id);
                return entity == null || entity.isDead();
            });
            // Spawns are staggered, so an empty set alone doesn't mean the wave is over.
            if (waveZombies.isEmpty() && pendingSpawns <= 0) {
                waveActive = false;
                breakTimer = 10;
                game.broadcast("<green><b>Wave " + wave + " cleared!</b></green>");
                rewardPlayers();
                if (wave >= totalWaves()) {
                    game.end(game.players(), "waves-complete");
                }
            }
        } else if (--breakTimer <= 0) {
            startWave();
        }
    }

    private void startWave() {
        wave++;
        waveActive = true;
        int count = (int) ((4 + wave * 3) * (isHard() ? 1.5 : 1.0));
        game.broadcast("<red><b>WAVE " + wave + "</b></red> <gray>- " + count + " zombies incoming!");
        for (Player p : game.everyone()) {
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1f);
        }
        pendingSpawns = count;
        for (int i = 0; i < count; i++) {
            game.runLater(i * 10L, () -> spawnQueued(0));
        }
    }

    private void spawnQueued(int attempt) {
        if (!waveActive || game.world() == null) return;
        var location = VSpawnSearch.choose(game.world(), mobSpawns, ThreadLocalRandom.current());
        if (location.isEmpty()) {
            if (attempt < 5) game.runLater(20L, () -> spawnQueued(attempt + 1));
            else {
                waveActive = false;
                pendingSpawns = 0;
                game.broadcast("<red>Wave stopped: no safe loaded mobspawn location. Check spawn floors and headroom.");
                game.forceEnd();
            }
            return;
        }
        Zombie zombie = (Zombie) game.world().spawnEntity(location.get(), EntityType.ZOMBIE);
        waveZombies.add(zombie.getUniqueId());
        pendingSpawns--;
        zombie.setShouldBurnInDay(false);
        zombie.setRemoveWhenFarAway(false);
        if (mayor != null && !mayor.isDead()) zombie.setTarget(mayor);
    }

    @Override
    public void onCleanup() {
        waveActive = false;
        pendingSpawns = 0;
        if (game.world() != null) {
            for (UUID id : waveZombies) {
                var entity = game.world().getEntity(id);
                if (entity != null) entity.remove();
            }
        }
        waveZombies.clear();
        if (mayor != null) mayor.remove();
        mayor = null;
    }

    private void rewardPlayers() {
        for (Player p : game.alivePlayers()) {
            p.setHealth(Math.min(p.getHealth() + 6, 20));
            p.getInventory().addItem(new ItemStack(Material.ARROW, 16));
            if (wave == 2) p.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
            if (wave == 4) p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            if (wave == 6) p.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
            p.sendMessage(Text.msg("<green>Wave reward received!"));
        }
    }

    @Override
    public void onEntityDeath(LivingEntity entity, Player killer) {
        // Only the Mayor ends the round - any other villager on the map is scenery.
        if (mayor != null && entity.getUniqueId().equals(mayor.getUniqueId())) {
            game.broadcast("<red><b>The Mayor has fallen!</b></red>");
            game.end(List.of(), "mayor-died");
        } else if (killer != null && waveZombies.contains(entity.getUniqueId())) {
            game.addScore(killer, 1);
        }
    }

    @Override
    public List<LivingEntity> botEnemies() {
        return waveZombies.stream().map(id -> game.world().getEntity(id))
                .filter(entity -> entity instanceof Zombie && !entity.isDead())
                .map(entity -> (LivingEntity) entity).toList();
    }

    @Override
    public Location botGuardPoint() {
        return mayor != null && !mayor.isDead() ? mayor.getLocation() : null;
    }

    /** Each bot holds its own lane on an arc round the Mayor, facing the nearest zombie, clear of any human. */
    @Override
    public Location botGuardPoint(Player bot) {
        Location mayorAt = botGuardPoint();
        if (mayorAt == null || bot.getWorld() != mayorAt.getWorld()) return mayorAt;
        var bots = game.alivePlayers().stream().filter(game::isBot).map(Player::getUniqueId).sorted().toList();
        Location zombie = botEnemies().stream().map(org.bukkit.entity.Entity::getLocation).filter(l -> l.getWorld() == mayorAt.getWorld())
                .min(java.util.Comparator.comparingDouble(l -> l.distanceSquared(mayorAt))).orElse(null);
        double[][] humans = game.alivePlayers().stream().filter(p -> !game.isBot(p) && p.getWorld() == mayorAt.getWorld())
                .map(p -> new double[]{p.getLocation().getX(), p.getLocation().getZ()}).toArray(double[][]::new);
        double[] spot = uk.co.playerready.pulsegames.core.bot.CoopTactics.lanePoint(
                new double[]{mayorAt.getX(), mayorAt.getZ()}, Math.max(0, bots.indexOf(bot.getUniqueId())), bots.size(), 3.5,
                zombie == null ? null : new double[]{zombie.getX(), zombie.getZ()}, humans);
        return new Location(mayorAt.getWorld(), spot[0], mayorAt.getY(), spot[1]);
    }

    @Override
    public boolean cooperative() { return true; }

    @Override
    public List<String> sidebar(Player player) {
        return List.of(
                "<gray>Wave: <white>" + wave + "/" + totalWaves(),
                "<gray>Zombies left: <red>" + waveZombies.size(),
                "<gold>Mayor HP: <white>" + (mayor != null && !mayor.isDead() ? (int) mayor.getHealth() : 0),
                "<gray>Your kills: <white>" + game.score(player));
    }

    @Override
    public boolean respawnable() { return true; }

    @Override
    public int respawnDelaySeconds() { return 5; }

    @Override
    public int timeLimitSeconds() { return 1800; }
    @Override
    public String phaseCountdown() { return !waveActive && breakTimer > 0 ? "Next wave: " + breakTimer + "s" : null; }

}
