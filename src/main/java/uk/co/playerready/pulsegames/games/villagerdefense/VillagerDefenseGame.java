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
public final class VillagerDefenseGame extends MiniGame {

    private Villager mayor;
    private int wave = 0;
    private int breakTimer = 5;
    private boolean waveActive;
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
        for (Player player : game.alivePlayers()) {
            player.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
            player.getInventory().setItem(1, new ItemStack(Material.BOW));
            player.getInventory().setItem(9, new ItemStack(Material.ARROW, 32));
        }
        game.broadcast("Protect <gold>The Mayor</gold> for <yellow>" + totalWaves() + "</yellow> waves!");
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
            if (waveZombies.isEmpty()) {
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
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Cuboid region = mobSpawns.isEmpty() ? null : mobSpawns.get(random.nextInt(mobSpawns.size()));
            Location loc = region != null ? region.center(game.world()) : game.arena().spawn(0, game.world());
            game.runLater(i * 10L, () -> {
                if (game.world() == null || !waveActive) return;
                Zombie zombie = (Zombie) game.world().spawnEntity(loc, EntityType.ZOMBIE);
                zombie.setShouldBurnInDay(false);
                zombie.setRemoveWhenFarAway(false);
                if (mayor != null && !mayor.isDead()) zombie.setTarget(mayor);
                waveZombies.add(zombie.getUniqueId());
            });
        }
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
        if (entity instanceof Villager) {
            game.broadcast("<red><b>The Mayor has fallen!</b></red>");
            game.end(List.of(), "mayor-died");
        } else if (killer != null && waveZombies.contains(entity.getUniqueId())) {
            game.addScore(killer, 1);
        }
    }

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
}
