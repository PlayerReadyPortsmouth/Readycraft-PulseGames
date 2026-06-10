package uk.co.playerready.pulsegames.games.readyplayerz;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
 * ReadyPlayerZ: family-friendly round-based zombie survival (CoD-zombies style).
 * Earn points for kills and spend them in the Mystery Shop. Survive all rounds to win!
 * Modes: classic (15 rounds), frenzy (faster, 10 rounds).
 * Arena: regions mobspawn1..N.
 */
public final class ReadyPlayerZGame extends MiniGame {

    private static final class ShopHolder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private record Upgrade(String name, Material icon, int cost, java.util.function.Consumer<Player> apply) {}

    private final List<Upgrade> upgrades = List.of(
            new Upgrade("Stone Sword", Material.STONE_SWORD, 50, p -> p.getInventory().addItem(new ItemStack(Material.STONE_SWORD))),
            new Upgrade("Iron Sword", Material.IRON_SWORD, 150, p -> p.getInventory().addItem(new ItemStack(Material.IRON_SWORD))),
            new Upgrade("Diamond Sword", Material.DIAMOND_SWORD, 500, p -> p.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD))),
            new Upgrade("Bow", Material.BOW, 200, p -> p.getInventory().addItem(new ItemStack(Material.BOW))),
            new Upgrade("Arrows x16", Material.ARROW, 100, p -> p.getInventory().addItem(new ItemStack(Material.ARROW, 16))),
            new Upgrade("Iron Chestplate", Material.IRON_CHESTPLATE, 300, p -> p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE))),
            new Upgrade("Heal Up", Material.GOLDEN_APPLE, 75, p -> p.setHealth(20)),
            new Upgrade("Speed Perk", Material.SUGAR, 250,
                    p -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 600, 0))));

    private int round = 0;
    private int breakTimer = 8;
    private boolean roundActive;
    private final Set<UUID> zombies = new HashSet<>();
    private final Set<UUID> downed = new HashSet<>();
    private final java.util.Map<UUID, String> powerupItems = new java.util.HashMap<>();
    private long doublePointsUntil;
    private List<Cuboid> mobSpawns = List.of();

    public ReadyPlayerZGame(GameInstance game) {
        super(game);
    }

    private boolean isFrenzy() { return game.mode().id().equals("frenzy"); }

    private int totalRounds() {
        return game.arena().settings().getInt("rounds", isFrenzy() ? 10 : 15);
    }

    @Override
    public void onStart() {
        mobSpawns = game.arena().regionsByPrefix("mobspawn");
        for (Player player : game.alivePlayers()) {
            equipBase(player);
        }
        game.broadcast("Survive <yellow>" + totalRounds() + "</yellow> rounds of zombies! Kills earn points "
                + "- spend them in the <green>Mystery Shop</green> (emerald).");
    }

    private void equipBase(Player player) {
        player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
        ItemStack shop = new ItemStack(Material.EMERALD);
        shop.editMeta(meta -> meta.displayName(Text.mm("<green><b>Mystery Shop</b> <gray>(right-click)")));
        player.getInventory().setItem(8, shop);
        player.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
    }

    @Override
    public void onSecond(int gameTime) {
        if (roundActive) {
            zombies.removeIf(id -> {
                var entity = game.world().getEntity(id);
                return entity == null || entity.isDead();
            });
            if (zombies.isEmpty()) {
                roundActive = false;
                breakTimer = isFrenzy() ? 5 : 10;
                game.broadcast("<green><b>Round " + round + " survived!</b></green>");
                reviveDowned();
                if (round >= totalRounds()) {
                    game.end(game.players(), "rounds-complete");
                }
            }
        } else if (--breakTimer <= 0) {
            startRound();
        }
    }

    private void startRound() {
        round++;
        roundActive = true;
        int count = (int) ((6 + round * 4) * (isFrenzy() ? 1.5 : 1.0));
        game.broadcast("<red><b>ROUND " + round + "</b></red> <gray>- " + count + " zombies!");
        for (Player p : game.everyone()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WOLF_HOWL, 1f, 0.7f);
        }
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Cuboid region = mobSpawns.isEmpty() ? null : mobSpawns.get(random.nextInt(mobSpawns.size()));
            Location loc = region != null ? region.center(game.world()) : game.arena().spawn(0, game.world());
            game.runLater(i * (isFrenzy() ? 6L : 12L), () -> {
                if (game.world() == null || !roundActive) return;
                Zombie zombie = (Zombie) game.world().spawnEntity(loc, EntityType.ZOMBIE);
                zombie.setShouldBurnInDay(false);
                zombie.setRemoveWhenFarAway(false);
                if (round >= 5) zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
                zombies.add(zombie.getUniqueId());
            });
        }
    }

    private void reviveDowned() {
        for (UUID id : new HashSet<>(downed)) {
            Player player = Bukkit.getPlayer(id);
            downed.remove(id);
            if (player != null && player.isOnline() && game.isParticipant(player)) {
                player.setGameMode(playGameMode());
                player.teleport(game.spawnFor(player));
                game.plugin().playerState().reset(player);
                equipBase(player);
                game.broadcast("<green>" + player.getName() + "</green> is back in the fight!");
            }
        }
    }

    @Override
    public void onEntityDeath(LivingEntity entity, Player killer) {
        if (killer != null && zombies.contains(entity.getUniqueId())) {
            int points = System.currentTimeMillis() < doublePointsUntil ? 20 : 10;
            game.addScore(killer, points);
            killer.sendActionBar(Text.mm("<gold>+" + points + " points"));
            maybeDropPowerup(entity.getLocation());
        }
    }

    // ---- power-up drops (CoD style) -------------------------------------------

    private void maybeDropPowerup(Location location) {
        if (ThreadLocalRandom.current().nextInt(100) >= 8) return; // 8% chance
        String[] types = {"insta-kill", "double-points", "max-ammo", "nuke"};
        Material[] icons = {Material.BLAZE_POWDER, Material.GOLD_INGOT, Material.ARROW, Material.TNT};
        int pick = ThreadLocalRandom.current().nextInt(types.length);
        ItemStack stack = new ItemStack(icons[pick]);
        stack.editMeta(meta -> meta.displayName(Text.mm("<gold><b>" + types[pick].toUpperCase().replace('-', ' '))));
        var item = game.world().dropItem(location.clone().add(0, 0.5, 0), stack);
        item.setGlowing(true);
        item.setVelocity(new org.bukkit.util.Vector(0, 0.2, 0));
        powerupItems.put(item.getUniqueId(), types[pick]);
        game.world().spawnParticle(org.bukkit.Particle.FIREWORK, location, 20, 0.3, 0.5, 0.3, 0.05);
        // Despawn after 20s if nobody grabs it.
        game.runLater(400L, () -> {
            if (powerupItems.remove(item.getUniqueId()) != null && item.isValid()) item.remove();
        });
    }

    @Override
    public boolean itemPickup(Player player, org.bukkit.entity.Item item) {
        String type = powerupItems.remove(item.getUniqueId());
        if (type == null) return false;
        item.remove();
        activatePowerup(player, type);
        return false; // consumed by the effect, never enters the inventory
    }

    private void activatePowerup(Player collector, String type) {
        for (Player p : game.everyone()) {
            Text.title(p, "<gold><b>" + type.toUpperCase().replace('-', ' '), "<gray>grabbed by " + collector.getName());
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
        }
        switch (type) {
            case "insta-kill" -> game.alivePlayers().forEach(p -> p.addPotionEffect(new PotionEffect(
                    PotionEffectType.STRENGTH, 20 * 10, 9, false, true)));
            case "double-points" -> doublePointsUntil = System.currentTimeMillis() + 30_000;
            case "max-ammo" -> game.alivePlayers().forEach(p -> {
                if (p.getInventory().contains(Material.BOW)) {
                    p.getInventory().addItem(new ItemStack(Material.ARROW, 16));
                }
            });
            case "nuke" -> {
                int killed = 0;
                for (UUID id : new HashSet<>(zombies)) {
                    if (game.world().getEntity(id) instanceof LivingEntity zombie && !zombie.isDead()) {
                        game.world().strikeLightningEffect(zombie.getLocation());
                        zombie.setHealth(0);
                        killed++;
                    }
                }
                zombies.clear();
                int bonus = Math.max(killed * 10, 50);
                game.alivePlayers().forEach(p -> game.addScore(p, bonus));
                game.broadcast("<gold><b>NUKE!</b></gold> <gray>" + killed + " zombies vaporised <gold>(+"
                        + bonus + " points each)");
            }
            default -> { }
        }
    }

    /** Going down doesn't end your game - teammates clearing the round revives you. */
    @Override
    public void onDeath(Player victim, Player killer) {
        downed.add(victim.getUniqueId());
        victim.setGameMode(org.bukkit.GameMode.SPECTATOR);
        victim.teleport(game.arena().spectator(game.world()));
        game.broadcast("<red>" + victim.getName() + "</red> is down! Survive the round to revive them.");
        boolean allDown = game.alivePlayers().stream().allMatch(p -> downed.contains(p.getUniqueId()));
        if (allDown) {
            game.broadcast("<red><b>The team was overrun on round " + round + "!");
            game.end(List.of(), "overrun");
        }
    }

    // ---- shop ------------------------------------------------------------------

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.EMERALD
                && event.getAction().isRightClick()) {
            event.setCancelled(true);
            openShop(event.getPlayer());
        }
    }

    private void openShop(Player player) {
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 9, Text.mm("<dark_gray>Mystery Shop"));
        holder.inventory = inv;
        for (int i = 0; i < upgrades.size(); i++) {
            Upgrade upgrade = upgrades.get(i);
            ItemStack item = new ItemStack(upgrade.icon());
            item.editMeta(meta -> {
                meta.displayName(Text.mm("<yellow>" + upgrade.name()));
                meta.lore(List.of(Text.mm("<gold>" + upgrade.cost() + " points")));
            });
            inv.setItem(i, item);
        }
        player.openInventory(inv);
    }

    @Override
    public void onInventoryClick(Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);
        int slot = event.getSlot();
        if (event.getClickedInventory() != event.getInventory() || slot < 0 || slot >= upgrades.size()) return;
        Upgrade upgrade = upgrades.get(slot);
        if (game.score(player) < upgrade.cost()) {
            player.sendMessage(Text.msg("<red>Not enough points! <gray>(" + game.score(player) + "/" + upgrade.cost() + ")"));
            return;
        }
        game.addScore(player, -upgrade.cost());
        upgrade.apply().accept(player);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        player.sendMessage(Text.msg("<green>Bought " + upgrade.name() + "!"));
    }

    @Override
    public List<String> sidebar(Player player) {
        return List.of(
                "<gray>Round: <red>" + round + "/" + totalRounds(),
                "<gray>Zombies left: <red>" + zombies.size(),
                "<gold>Points: <white>" + game.score(player),
                "<gray>Team: <white>" + (game.alivePlayers().size() - downed.size()) + " up");
    }

    @Override
    public int timeLimitSeconds() { return 2400; }
}
