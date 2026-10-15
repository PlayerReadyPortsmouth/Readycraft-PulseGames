package uk.co.playerready.pulsegames.games.readyplayerz;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

/**
 * ReadyPlayerZ: family-friendly round-based zombie survival (CoD-zombies style).
 * Earn points for kills and spend them in the Mystery Shop. Survive all rounds to win!
 * Modes: classic (15 rounds), frenzy (faster, 10 rounds).
 * Arena: regions mobspawn1..N.
 */
public final class ReadyPlayerZGame extends MiniGame implements uk.co.playerready.pulsegames.core.bot.BotObjectives {
    private static final class ShopHolder implements InventoryHolder {
        Inventory inventory;
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record Upgrade(String name, Material icon, int cost, java.util.function.Consumer<Player> apply) {}

    private final List<Upgrade> upgrades =
        List.of(new Upgrade("Stone Sword", Material.STONE_SWORD, 50,
                    p -> p.getInventory().addItem(new ItemStack(Material.STONE_SWORD))),
            new Upgrade("Iron Sword", Material.IRON_SWORD, 150,
                p -> p.getInventory().addItem(new ItemStack(Material.IRON_SWORD))),
            new Upgrade("Diamond Sword", Material.DIAMOND_SWORD, 500,
                p -> p.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD))),
            new Upgrade("Bow", Material.BOW, 200, p -> p.getInventory().addItem(new ItemStack(Material.BOW))),
            new Upgrade(
                "Arrows x16", Material.ARROW, 100, p -> p.getInventory().addItem(new ItemStack(Material.ARROW, 16))),
            new Upgrade("Iron Chestplate", Material.IRON_CHESTPLATE, 300,
                p -> p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE))),
            new Upgrade("Heal Up", Material.GOLDEN_APPLE, 75, p -> p.setHealth(20)),
            new Upgrade("Speed Perk", Material.SUGAR, 250,
                p -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 600, 0))));

    private final ZMapProgression progression;
    private final java.util.Map<UUID, ZCombatBalance.Variant> variants = new java.util.HashMap<>();
    private long elapsedTicks, nextDropTick;
    private int lastNukeRound = -100;
    private boolean nukeActive;
    private long previousTime;
    private Boolean previousDaylight, previousWeather;
    private boolean nightApplied;
    private int round = 0;
    private int breakTimer = 8;
    private boolean roundActive;
    private int pendingSpawns;
    private final Set<UUID> zombies = new HashSet<>();
    private final Set<UUID> downed = new HashSet<>();
    private final java.util.Map<UUID, String> powerupItems = new java.util.HashMap<>();
    private long doublePointsUntil;
    private List<Cuboid> mobSpawns = List.of();

    public ReadyPlayerZGame(GameInstance game) {
        super(game);
        progression = new ZMapProgression(game);
    }

    private boolean isFrenzy() {
        return game.mode().id().equals("frenzy");
    }

    private int totalRounds() {
        return Math.max(1, Math.min(50, game.arena().settings().getInt("rounds", isFrenzy() ? 10 : 15)));
    }

    @Override
    public String startProblem(List<Player> players) {
        String mapProblem = ZMapProgression.validationError(game.arena());
        if (mapProblem != null)
            return mapProblem;
        java.util.List<String> models = new java.util.ArrayList<>();
        for (var variant : ZCombatBalance.Variant.values()) {
            String id = model(variant);
            if (!id.isBlank())
                models.add(id);
        }
        return models.isEmpty() ? null : game.plugin().content().mobReadiness(players, models);
    }

    @Override
    public void onStart() {
        mobSpawns = game.arena().regionsByPrefix("mobspawn");
        String failure = startProblem(game.everyone());
        if(failure==null)failure=progression.startProblem();
        if (failure != null) {
            game.note("z-model-unavailable", failure);
            game.broadcast("<red>Arena progression or configured zombie models are unavailable; this match cannot start.");
            game.forceEnd();
            return;
        }
        previousTime = game.world().getTime();
        previousDaylight = game.world().getGameRuleValue(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE);
        previousWeather = game.world().getGameRuleValue(org.bukkit.GameRule.DO_WEATHER_CYCLE);
        game.world().setTime(18000);
        game.world().setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        game.world().setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        nightApplied = true;
        for (Player player : game.alivePlayers()) {
            equipBase(player);
        }
        game.broadcast("Survive <yellow>" + totalRounds() + "</yellow> rounds of zombies! Kills earn points "
            + "- spend them in the <green>Mystery Shop</green> (emerald).");
    }

    void equipBase(Player player) {
        player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
        ItemStack shop = new ItemStack(Material.EMERALD);
        shop.editMeta(meta -> meta.displayName(Text.mm("<green><b>Mystery Shop</b> <gray>(right-click)")));
        player.getInventory().setItem(8, shop);
        player.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
    }

    @Override
    public void onSecond(int gameTime) {
        elapsedTicks += 20;
        if (roundActive) {
            refreshDarkness();
            double range = bounded("aggro-range", 48, 16, 96);
            for (LivingEntity enemy : botEnemies())
                if (enemy instanceof org.bukkit.entity.Mob mob) {
                    Player nearest =
                        activeSquad()
                            .stream()
                            .filter(p -> p.getLocation().distanceSquared(enemy.getLocation()) <= range * range)
                            .min(java.util.Comparator.comparingDouble(
                                p -> p.getLocation().distanceSquared(enemy.getLocation())))
                            .orElse(null);
                    mob.setTarget(nearest);
                }
            zombies.removeIf(id -> {
                var entity = game.world().getEntity(id);
                boolean gone=entity==null||entity.isDead();if(gone)variants.remove(id);return gone;
            });
            // Spawns are staggered across the round, and a nuke empties the live set while
            // the rest of the wave is still queued - both would read as "round survived".
            if (zombies.isEmpty() && pendingSpawns <= 0) {
                roundActive = false;
                game.everyone().forEach(this::clearDarkness);
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
            p.playSound(p.getLocation(), "minecraft:entity.zombie.ambient", 1f, 0.7f);
        }
        refreshDarkness();
        progression.setRound(round);
        pendingSpawns = count;
        for (int i = 0; i < count; i++) {
            boolean boss = round % 5 == 0 && i == count - 1;
            int expectedRound = round;
            game.runLater(i * (isFrenzy() ? 6L : 12L), () -> spawnQueued(expectedRound, boss, 0));
        }
    }

    private int bounded(String key, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, game.arena().settings().getInt(key, fallback)));
    }
    private String model(ZCombatBalance.Variant variant) {
        String key=variant.name().toLowerCase(java.util.Locale.ROOT);
        return game.arena().settings().getString("zombie-model-"+key,game.arena().settings().getString("zombie-models."+key,"")).trim();
    }
    private List<Player> activeSquad() {
        return game.alivePlayers()
            .stream()
            .filter(game::isActive)
            .filter(p -> !downed.contains(p.getUniqueId()) && p.getWorld() == game.world())
            .toList();
    }
    private void spawnQueued(int expectedRound, boolean boss, int attempt) {
        if (game.state() != GameState.RUNNING || game.world() == null || !roundActive || round != expectedRound)
            return;
        List<Player> squad = activeSquad();
        if (squad.isEmpty()) {
            checkOverrun();
            return;
        }
        java.util.List<Cuboid> available = new java.util.ArrayList<>();
        for (int i = 0; i < mobSpawns.size(); i++)
            if (progression.spawnUnlocked(i + 1))
                available.add(mobSpawns.get(i));
        int minimum = bounded("spawn-min-distance", 8, 3, 24),
            maximum = bounded("spawn-max-distance", 48, minimum + 1, 96);
        var location = ZSpawnSearch.choose(game.world(), available, squad, minimum, maximum, boss ? 3 : 2.2,
            ignored -> true, ThreadLocalRandom.current());
        if (location.isEmpty()) {
            if (attempt < 5) {
                game.runLater(20, () -> spawnQueued(expectedRound, boss, attempt + 1));
                return;
            }
            pendingSpawns = 0;
            roundActive = false;
            game.note("z-spawn-unavailable",
                "No safe unlocked mobspawn within configured distance after six bounded searches");
            game.broadcast("<red>This arena has no safe nearby unlocked zombie spawn. Match stopped; please check "
                + "mobspawn regions.");
            game.forceEnd();
            return;
        }
        ZCombatBalance.Variant variant = ZCombatBalance.variant(round, boss, ThreadLocalRandom.current());
        LivingEntity spawned = null;
        try {
            String id = model(variant);
            LivingEntity enemy = id.isBlank()
                ? (LivingEntity) game.world().spawnEntity(location.get(), EntityType.ZOMBIE)
                : game.plugin().content().spawnMob(id, location.get(), game.everyone());
            spawned = enemy;
            if (enemy != null) {
                zombies.add(enemy.getUniqueId());
                variants.put(enemy.getUniqueId(), variant);
            }
            if (!(enemy instanceof org.bukkit.entity.Mob mob)) {
                if (enemy != null)
                    enemy.remove();
                throw new IllegalStateException("Configured enemy is not a Mob");
            }
            enemy.setRemoveWhenFarAway(false);
            enemy.setCanPickupItems(false);
            if (enemy instanceof Zombie zombie) {
                zombie.setAdult();
                zombie.setShouldBurnInDay(false);
            }
            configureEnemy(enemy,variant);
            zombies.add(enemy.getUniqueId());
            variants.put(enemy.getUniqueId(), variant);
            pendingSpawns--;
            mob.setTarget(squad.stream()
                    .min(java.util.Comparator.comparingDouble(p -> p.getLocation().distanceSquared(location.get())))
                    .orElse(null));
        } catch (RuntimeException error) {
            if (spawned != null) {
                zombies.remove(spawned.getUniqueId());
                variants.remove(spawned.getUniqueId());
                spawned.remove();
            }
            game.note("z-spawn-error", error.getClass().getSimpleName());
            game.broadcast("<red>A configured zombie could not spawn safely; match stopped.");
            game.forceEnd();
        }
    }
    void configureEnemy(LivingEntity enemy,ZCombatBalance.Variant variant){
            var stats = ZCombatBalance.stats(round, variant);
            setAttribute(enemy, org.bukkit.attribute.Attribute.MAX_HEALTH, stats.health());
            enemy.setHealth(stats.health());
            setAttribute(enemy, org.bukkit.attribute.Attribute.MOVEMENT_SPEED, stats.speed());
            setAttribute(enemy, org.bukkit.attribute.Attribute.ATTACK_DAMAGE, stats.damage());
            setAttribute(enemy, org.bukkit.attribute.Attribute.FOLLOW_RANGE, bounded("aggro-range", 48, 16, 96));
            setAttribute(enemy, org.bukkit.attribute.Attribute.SCALE, stats.scale());
            enemy.customName(Text.mm("<red>"
                + (variant == ZCombatBalance.Variant.BOSS ? "Foreman"
                                                          : variant.name().toLowerCase(java.util.Locale.ROOT))));
            if (variant == ZCombatBalance.Variant.BRUTE)
                enemy.addPotionEffect(
                    new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            if (variant == ZCombatBalance.Variant.VENOM)
                enemy.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
    }
    private void setAttribute(LivingEntity entity, org.bukkit.attribute.Attribute attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance != null)
            instance.setBaseValue(value);
    }

    private void reviveDowned() {
        for (UUID id : new HashSet<>(downed)) {
            Player player = game.player(id);
            downed.remove(id);
            if (player != null && game.isParticipant(player)) {
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
        if (!zombies.remove(entity.getUniqueId()))
            return;
        variants.remove(entity.getUniqueId());
        if (!nukeActive && killer != null && game.isActive(killer)) {
            int points = System.currentTimeMillis() < doublePointsUntil ? 20 : 10;
            game.addScore(killer, points);
            killer.sendActionBar(Text.mm("<gold>+" + points + " points"));
            maybeDropPowerup(entity.getLocation());
        }
    }

    // ---- power-up drops (CoD style) -------------------------------------------

    private void maybeDropPowerup(Location location) {
        double chance = game.arena().settings().getDouble("powerup-drop-chance", .03);
        if (!Double.isFinite(chance))
            chance = .03;
        String type =
            ZCombatBalance.drop(round, lastNukeRound, elapsedTicks, nextDropTick, chance, ThreadLocalRandom.current());
        if (type == null)
            return;
        nextDropTick = elapsedTicks + bounded("powerup-cooldown-seconds", 30, 10, 120) * 20L;
        if (type.equals("nuke"))
            lastNukeRound = round;
        Material icon = Material.ARROW;
        if (type.equals("insta-kill"))
            icon = Material.BLAZE_POWDER;
        else if (type.equals("double-points"))
            icon = Material.GOLD_INGOT;
        else if (type.equals("nuke"))
            icon = Material.TNT;
        ItemStack stack = new ItemStack(icon);
        stack.editMeta(meta -> meta.displayName(Text.mm("<gold><b>" + type.toUpperCase().replace('-', ' '))));
        var item = game.world().dropItem(location.clone().add(0, 0.5, 0), stack);
        item.setGlowing(true);
        item.setVelocity(new org.bukkit.util.Vector(0, 0.2, 0));
        powerupItems.put(item.getUniqueId(), type);
        game.world().spawnParticle(org.bukkit.Particle.FIREWORK, location, 20, 0.3, 0.5, 0.3, 0.05);
        // Despawn after 20s if nobody grabs it.
        game.runLater(400L, () -> {
            if (powerupItems.remove(item.getUniqueId()) != null && item.isValid())
                item.remove();
        });
    }

    @Override
    public boolean itemPickup(Player player, org.bukkit.entity.Item item) {
        if (!game.isActive(player) || downed.contains(player.getUniqueId()) || player.getWorld() != game.world())
            return false;
        String type = powerupItems.remove(item.getUniqueId());
        if (type == null)
            return false;
        item.remove();
        activatePowerup(player, type);
        return false; // consumed by the effect, never enters the inventory
    }

    private void activatePowerup(Player collector, String type) {
        for (Player p : game.everyone()) {
            Text.title(
                p, "<gold><b>" + type.toUpperCase().replace('-', ' '), "<gray>grabbed by " + collector.getName());
            p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 1f, 0.7f);
        }
        switch (type) {
            case "insta-kill" ->
                game.alivePlayers().forEach(
                    p -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 10, 9, false, true)));
            case "double-points" -> doublePointsUntil = System.currentTimeMillis() + 30_000;
            case "max-ammo" ->
                game.alivePlayers().forEach(p -> {
                    if (p.getInventory().contains(Material.BOW) || p.getInventory().contains(Material.CROSSBOW)) {
                        p.getInventory().addItem(new ItemStack(Material.ARROW, 16));
                    }
                });
            case "nuke" -> {
                int killed = 0;
                nukeActive = true;
                try {
                    for (UUID id : new HashSet<>(zombies)) {
                        if (game.world().getEntity(id) instanceof LivingEntity zombie && !zombie.isDead()) {
                            game.world().strikeLightningEffect(zombie.getLocation());
                            if (variants.get(id) == ZCombatBalance.Variant.BOSS) {
                                zombie.setHealth(Math.max(1, zombie.getHealth() * .5));
                                continue;
                            }
                            zombie.setHealth(0);
                            zombies.remove(id);
                            variants.remove(id);
                            killed++;
                        }
                    }
                } finally {
                    nukeActive = false;
                }
                int bonus = Math.max(killed * 10, 50);
                game.alivePlayers().forEach(p -> game.addScore(p, bonus));
                game.broadcast("<gold><b>NUKE!</b></gold> <gray>" + killed + " zombies vaporised <gold>(+" + bonus
                    + " points each)");
            }
            default -> {
            }
        }
    }

    @Override
    public void onDamage(Player victim, org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.isCancelled() || !game.isActive(victim) || downed.contains(victim.getUniqueId()))
            return;
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent hit
            && variants.get(hit.getDamager().getUniqueId()) == ZCombatBalance.Variant.VENOM)
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true));
    }

    /** Going down doesn't end your game - teammates clearing the round revives you. */
    @Override
    public void onDeath(Player victim, Player killer) {
        clearDarkness(victim);
        downed.add(victim.getUniqueId());
        victim.setGameMode(org.bukkit.GameMode.SPECTATOR);
        victim.teleport(game.arena().spectator(game.world()));
        game.broadcast("<red>" + victim.getName() + "</red> is down! Survive the round to revive them.");
        checkOverrun();
    }

    /** Someone leaving must not leave a downed squad counted as still fighting. */
    @Override
    public void onQuit(Player player) {
        clearDarkness(player);
        downed.remove(player.getUniqueId());
        if (game.state() == GameState.RUNNING)
            checkOverrun();
    }

    // Short refreshes expire even if a match is interrupted; state restoration also clears effects.
    void refreshDarkness() {
        if (!roundActive || !game.arena().settings().getBoolean("darkness", true))
            return;
        for (Player player : game.alivePlayers()) {
            if (!game.isBot(player) && !downed.contains(player.getUniqueId()))
                darken(player);
        }
    }

    void darken(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false, true));
    }

    void clearDarkness(Player player) {
        player.removePotionEffect(PotionEffectType.DARKNESS);
    }

    @Override
    public void onEliminated(Player player) {
        clearDarkness(player);
    }
    @Override
    public void onEnd(List<Player> winners) {
        onCleanup();
    }
    @Override
    public void onCleanup() {
        roundActive = false;
        game.everyone().forEach(this::clearDarkness);
        for (UUID id : new HashSet<>(zombies)) {
            var entity = game.world() == null ? null : game.world().getEntity(id);
            if (entity != null)
                entity.remove();
        }
        zombies.clear();
        variants.clear();
        for (UUID id : new HashSet<>(powerupItems.keySet())) {
            var entity = game.world() == null ? null : game.world().getEntity(id);
            if (entity != null)
                entity.remove();
        }
        powerupItems.clear();
        pendingSpawns = 0;
        progression.cleanup();
        if (nightApplied && game.world() != null) {
            game.world().setTime(previousTime);
            if (previousDaylight != null)
                game.world().setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, previousDaylight);
            if (previousWeather != null)
                game.world().setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, previousWeather);
            nightApplied = false;
        }
    }

    private void checkOverrun() {
        List<Player> squad = game.alivePlayers();
        if (squad.isEmpty() || squad.stream().allMatch(p -> downed.contains(p.getUniqueId()))) {
            game.broadcast("<red><b>The team was overrun on round " + round + "!");
            game.end(List.of(), "overrun");
        }
    }

    @Override
    public List<LivingEntity> botEnemies() {
        if (game.world() == null)
            return List.of();
        return zombies.stream()
            .map(id -> game.world().getEntity(id))
            .filter(entity -> entity instanceof LivingEntity && !entity.isDead())
            .map(entity -> (LivingEntity) entity)
            .toList();
    }

    @Override
    public Location botGuardPoint() {
        return game.arena().spawn(0, game.world());
    }

    @Override
    public void botTick(Player bot) {
        if (!game.isBot(bot) || !game.isActive(bot))
            return;
        if (bot.getHealth() <= 10 && purchase(bot, 6))
            return;
        var inventory = bot.getInventory();
        if (!inventory.contains(Material.DIAMOND_SWORD) && game.score(bot) >= 500)
            purchase(bot, 2);
        else if (!inventory.contains(Material.DIAMOND_SWORD) && !inventory.contains(Material.IRON_SWORD)
            && game.score(bot) >= 150)
            purchase(bot, 1);
        else if (!inventory.contains(Material.DIAMOND_SWORD) && !inventory.contains(Material.IRON_SWORD)
            && !inventory.contains(Material.STONE_SWORD) && game.score(bot) >= 50)
            purchase(bot, 0);
        else if ((inventory.getChestplate() == null || inventory.getChestplate().getType() != Material.IRON_CHESTPLATE)
            && game.score(bot) >= 300)
            purchase(bot, 5);
        for (UUID id : new HashSet<>(powerupItems.keySet())) {
            if (game.world().getEntity(id) instanceof org.bukkit.entity.Item item && item.isValid()
                && item.getLocation().distanceSquared(bot.getLocation()) <= 1.5 * 1.5)
                itemPickup(bot, item);
        }
    }

    @Override
    public boolean cooperative() {
        return true;
    }

    // ---- shop ------------------------------------------------------------------

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if(downed.contains(event.getPlayer().getUniqueId())){event.setCancelled(true);return;}
        if (progression.onInteract(event))
            return;
        if (event.getItem() != null && event.getItem().getType() == Material.EMERALD
            && event.getAction().isRightClick()) {
            event.setCancelled(true);
            openShop(event.getPlayer());
        }
    }

    private void openShop(Player player) {
        if(game.state()!=GameState.RUNNING||!game.isActive(player)||downed.contains(player.getUniqueId())||player.getWorld()!=game.world())return;
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
        if (!(event.getInventory().getHolder() instanceof ShopHolder))
            return;
        event.setCancelled(true);
        int slot = event.getSlot();
        if (event.getClickedInventory() != event.getInventory() || slot < 0 || slot >= upgrades.size())
            return;
        if (!purchase(player, slot))
            player.sendMessage(Text.msg("<red>Purchase unavailable. Check your points and inventory space."));
    }

    @Override
    public void onInventoryDrag(Player player,org.bukkit.event.inventory.InventoryDragEvent event){
        if(event.getView().getTopInventory().getHolder() instanceof ShopHolder)event.setCancelled(true);
    }

    /** Shared shop action: bots pay exactly the same prices and obey active-player checks. */
    boolean purchase(Player player, int slot) {
        if (slot < 0 || slot >= upgrades.size() || game.state() != GameState.RUNNING || !game.isActive(player)
            || downed.contains(player.getUniqueId()) || player.getWorld() != game.world())
            return false;
        Upgrade upgrade = upgrades.get(slot);
        if (game.score(player) < upgrade.cost())
            return false;
        if (slot <= 4 && player.getInventory().firstEmpty() < 0)
            return false;
        game.addScore(player, -upgrade.cost());
        upgrade.apply().accept(player);
        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 1f, 1.2f);
        player.sendMessage(Text.msg("<green>Bought " + upgrade.name() + "!"));
        return true;
    }

    @Override
    public List<String> sidebar(Player player) {
        return List.of("<gray>Round: <red>" + round + "/" + totalRounds(), "<gray>Zombies left: <red>" + zombies.size(),
            "<gold>Points: <white>" + game.score(player),
            "<gray>Team: <white>" + (game.alivePlayers().size() - downed.size()) + " up");
    }

    @Override
    public int timeLimitSeconds() {
        return 2400;
    }
    @Override
    public String phaseCountdown() { return !roundActive && breakTimer > 0 ? "Next wave: " + breakTimer + "s" : null; }

}
