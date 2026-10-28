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
                p -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 600, 0))),
            new Upgrade("Crossbow", Material.CROSSBOW, 350, p -> {
                p.getInventory().addItem(new ItemStack(Material.CROSSBOW));
                p.getInventory().addItem(new ItemStack(Material.ARROW, 16));
            }),
            new Upgrade("Tough Skin Perk", Material.GOLDEN_APPLE, 400,
                p -> p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 600, 1))),
            new Upgrade("Regen Perk", Material.GLISTERING_MELON_SLICE, 450,
                p -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 600, 0))));
    /** Supply Shop chest (27 slots, Bedrock-safe): upgrades first, then the Mystery Box / Pack-a-Punch fallbacks. */
    static final int BOX_SLOT = 12, PAP_SLOT = 14;
    private final java.util.Map<UUID, Integer> boxUses = new java.util.HashMap<>();
    private final Set<UUID> spinning = new HashSet<>();
    private ZCombatBalance.Difficulty difficulty = ZCombatBalance.Difficulty.NORMAL;
    private int bestRound;

    ZMapProgression progression;
    private final java.util.Map<UUID, ZCombatBalance.Variant> variants = new java.util.HashMap<>();
    private long elapsedTicks, nextDropTick;
    private int lastNukeRound = -100;
    private boolean nukeActive;
    private long previousTime;
    private Boolean previousDaylight, previousWeather;
    private boolean nightApplied;
    private int round = 0;
    private int lastZombieSeconds;
    private final java.util.Map<UUID, Integer> kills = new java.util.HashMap<>();
    private final java.util.Map<UUID, Long> lastKill = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> chains = new java.util.HashMap<>();
    java.util.function.LongSupplier clock = System::currentTimeMillis;

    private int breakTimer = 8;
    private boolean roundActive;
    private int pendingSpawns;
    private final Set<UUID> zombies = new HashSet<>();
    private final Set<UUID> downed = new HashSet<>();
    private final java.util.Map<UUID, Long> bleedOut = new java.util.HashMap<>();
    private final java.util.Map<UUID, UUID> rescuers = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> rescueSeconds = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> revives = new java.util.HashMap<>();
    private boolean soloSelfRevive, selfReviveUsed;

    private final java.util.Map<UUID, String> powerupItems = new java.util.HashMap<>();
    private long doublePointsUntil;
    private List<Cuboid> mobSpawns = List.of();
    /** Per-player elapsedTicks of the next darkness pulse; absent until the player's first scheduling. */
    private final java.util.Map<UUID, Long> nextPulse = new java.util.HashMap<>();
    java.util.function.DoubleSupplier pulseRoll = () -> ThreadLocalRandom.current().nextDouble();
    /** Points a bot keeps back after buying a door so it can still afford a heal. */
    static final int BOT_DOOR_RESERVE = 75;

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
        soloSelfRevive = game.alivePlayers().size() == 1;
        difficulty = ZCombatBalance.Difficulty.parse(game.arena().settings().getString("difficulty"),
            game.players().size());
        if (game.stats() != null) bestRound = game.stats().record(bestKey());
        mobSpawns = game.arena().regionsByPrefix("mobspawn");
        String failure = startProblem(game.everyone());
        if(failure==null)failure=progression.startProblem();
        if(failure==null)progression.spawnHolograms();
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
            applyLegacyCombat(player);
        }
        game.broadcast("Survive <yellow>" + totalRounds() + "</yellow> rounds of zombies! Kills earn points "
            + "- spend them in the <green>Supply Shop</green> (emerald).");
    }

    private String bestKey() { return game.arena().gameId() + "." + game.arena().id() + ".best-round"; }

    void equipBase(Player player) {
        player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
        ItemStack shop = new ItemStack(Material.EMERALD);
        shop.editMeta(meta -> meta.displayName(Text.mm("<green><b>Supply Shop</b> <gray>(right-click)")));
        player.getInventory().setItem(8, shop);
        player.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
    }

    @Override
    public void onSecond(int gameTime) {
        elapsedTicks += 20;
        progression.tick();
        tickRevives();
        if (game.state() == GameState.ENDING || game.state() == GameState.RESETTING) return;
        if (roundActive) {
            pulseDarkness();
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
            if (zombies.size() == 1 && pendingSpawns == 0) {
                if (++lastZombieSeconds == 20) {
                    var last = game.world().getEntity(zombies.iterator().next());
                    if (last != null) last.setGlowing(true);
                    game.everyone().forEach(p -> p.sendActionBar(Text.mm("<yellow>Last zombie — follow the glow!")));
                }
            } else lastZombieSeconds = 0;
            // Spawns are staggered across the round, and a nuke empties the live set while
            // the rest of the wave is still queued - both would read as "round survived".
            if (zombies.isEmpty() && pendingSpawns <= 0) {
                roundActive = false;
                game.everyone().forEach(this::clearDarkness);
                nextPulse.clear();
                breakTimer = isFrenzy() ? 5 : 10;
                announce("<green>Round " + round + " clear!", "Time to resupply", "minecraft:block.note_block.chime");
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
        lastZombieSeconds = 0;
        roundActive = true;
        int count = Math.max(1, (int) ((6 + round * 4) * (isFrenzy() ? 1.5 : 1.0) * difficulty.count));
        boxUses.clear();
        announce("<gold>Round " + round, rushRound() ? "Rush round! Stay together" : count + " zombies incoming", "minecraft:event.raid.horn");
        progression.setRound(round);
        pendingSpawns = count;
        for (int i = 0; i < count; i++) {
            boolean boss = round % 5 == 0 && i == count - 1;
            int expectedRound = round;
            game.runLater(i * (isFrenzy() || rushRound() ? 6L : 12L), () -> spawnQueued(expectedRound, boss, 0));
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
        if (rushRound() && !boss) variant = ZCombatBalance.Variant.RUNNER;
        LivingEntity spawned = null;
        try {
            String id = model(variant);
            LivingEntity enemy = id.isBlank()
                ? (LivingEntity) game.world().spawnEntity(location.get(), (variant == ZCombatBalance.Variant.VENOM ? EntityType.HUSK : EntityType.ZOMBIE))
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
            var stats = ZCombatBalance.stats(round, variant, tuning());
            // Vanilla spawns can roll random armour/weapons, which silently inflates time-to-kill.
            if (enemy.getEquipment() != null) {
                enemy.getEquipment().clear();
            }
            setAttribute(enemy, org.bukkit.attribute.Attribute.ARMOR, variant == ZCombatBalance.Variant.BRUTE ? 4 : 0);
            if (variant == ZCombatBalance.Variant.BRUTE && enemy.getEquipment() != null) {
                enemy.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                enemy.getEquipment().setHelmetDropChance(0);
            }
            setAttribute(enemy, org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS, 0);
            setAttribute(enemy, org.bukkit.attribute.Attribute.SPAWN_REINFORCEMENTS, 0);
            setAttribute(enemy, org.bukkit.attribute.Attribute.MAX_HEALTH, stats.health());
            enemy.setHealth(stats.health());
            setAttribute(enemy, org.bukkit.attribute.Attribute.MOVEMENT_SPEED, stats.speed());
            setAttribute(enemy, org.bukkit.attribute.Attribute.ATTACK_DAMAGE, stats.damage());
            setAttribute(enemy, org.bukkit.attribute.Attribute.FOLLOW_RANGE, bounded("aggro-range", 48, 16, 96));
            setAttribute(enemy, org.bukkit.attribute.Attribute.SCALE, stats.scale());
            enemy.customName(Text.mm("<red>"
                + (variant == ZCombatBalance.Variant.BOSS ? "Brute Boss"
                                                          : variant.name().toLowerCase(java.util.Locale.ROOT))));
            if (variant == ZCombatBalance.Variant.VENOM)
                enemy.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
    }
    /** Zombie health curve from arena settings (zombie-health-base/-per-round/-max, boss-health-multiplier). */
    ZCombatBalance.Tuning tuning() {
        var settings = game.arena().settings();
        var d = ZCombatBalance.Tuning.DEFAULT;
        return ZCombatBalance.Tuning.of(settings.getDouble("zombie-health-base", d.base()),
            settings.getDouble("zombie-health-per-round", d.perRound()),
            settings.getDouble("zombie-health-max", d.max()),
            settings.getDouble("boss-health-multiplier", d.bossMultiplier())).scaled(difficulty.health);
    }

    // ---- legacy (1.8-style) combat: instance-scoped, restored on every exit path ----

    /** Original ATTACK_SPEED base per human, present only while legacy combat is applied to them. */
    private final java.util.Map<UUID, Double> originalAttackSpeed = new java.util.HashMap<>();
    static final double LEGACY_ATTACK_SPEED = LegacyAttackSpeed.LEGACY;

    boolean legacyCombat() {
        return game.arena().settings().getBoolean("legacy-combat", true);
    }

    org.bukkit.attribute.AttributeInstance attackSpeed(Player player) {
        return player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED);
    }

    void applyLegacyCombat(Player player) {
        if (!legacyCombat() || game.isBot(player) || originalAttackSpeed.containsKey(player.getUniqueId()))
            return;
        var attribute = attackSpeed(player);
        if (attribute == null)
            return;
        originalAttackSpeed.put(player.getUniqueId(), attribute.getBaseValue());
        LegacyAttackSpeed.mark(player, attribute.getBaseValue());
        attribute.setBaseValue(LEGACY_ATTACK_SPEED);
    }

    void restoreCombat(Player player) {
        Double original = originalAttackSpeed.remove(player.getUniqueId());
        var attribute = original == null ? null : attackSpeed(player);
        if (attribute != null)
            attribute.setBaseValue(original);
        if (original != null)
            LegacyAttackSpeed.clear(player);
    }

    /** Sweep attacks hit every zombie beside the target; 1.8 had none. */
    @Override
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent hit) {
            org.bukkit.entity.Entity source = hit.getDamager();
            if (source instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player p) source = p;
            if (source instanceof Player p && downed.contains(p.getUniqueId())) { event.setCancelled(true); return; }
        }
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
            && legacyCombat()) { event.setCancelled(true); return; }
        if (!event.isCancelled() && event.getFinalDamage() > 0 && zombies.contains(event.getEntity().getUniqueId())
                && event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent hit) {
            var source = hit.getDamager();
            Player attacker = source instanceof Player p ? p
                    : source instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player p ? p : null;
            if (attacker != null && game.isActive(attacker) && !downed.contains(attacker.getUniqueId())) awardPoints(attacker, 5, false);
        }
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
            bleedOut.remove(id);
            rescuers.remove(id);
            rescueSeconds.remove(id);
            if (player != null && game.isParticipant(player)) {
                clearDownState(player);
                player.setGameMode(playGameMode());
                player.teleport(game.spawnFor(player));
                game.plugin().playerState().reset(player);
                equipBase(player);
                applyLegacyCombat(player);
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
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
            awardPoints(killer, 10, true);
            maybeDropPowerup(entity.getLocation());
        }
    }

    private boolean rushRound() { return round > 0 && round % 3 == 0 && round % 5 != 0; }

    private void announce(String title, String subtitle, String sound) {
        for (Player p : game.everyone()) {
            Text.title(p, title, subtitle);
            p.playSound(p.getLocation(), sound, 1f, .9f);
        }
    }

    void awardPoints(Player player, int base, boolean kill) {
        UUID id = player.getUniqueId();
        long now = clock.getAsLong();
        int chain = chains.getOrDefault(id, 0);
        if (kill) {
            chain = now - lastKill.getOrDefault(id, Long.MIN_VALUE / 2) <= 2000 ? Math.min(8, chain + 1) : 1;
            lastKill.put(id, now); chains.put(id, chain);
        }
        int points = base * (now < doublePointsUntil ? 2 : 1);
        game.addScore(player, points);
        player.sendActionBar(Text.mm("<gold>+" + points + (kill && chain > 1 ? " • " + chain + " chain!" : "")));
        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", .6f, Math.min(2f, 1f + .12f * Math.max(0, chain - 1)));
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
        if (type.equals("team-shield")) icon = Material.SHIELD;
        if (type.equals("second-wind")) icon = Material.HONEY_BOTTLE;
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
            case "team-shield" -> activeSquad().forEach(p -> p.setNoDamageTicks(200));
            case "second-wind" -> activeSquad().forEach(p -> {
                var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                p.setHealth(Math.min(max == null ? 20 : max.getValue(), p.getHealth() + 8));
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
        if (downed.contains(victim.getUniqueId())) { event.setCancelled(true); return; }
        if (event.isCancelled() || !game.isActive(victim))
            return;
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent hit
            && variants.get(hit.getDamager().getUniqueId()) == ZCombatBalance.Variant.VENOM)
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true));
    }

    @Override
    public boolean acceptsDeath(Player player) { return !downed.contains(player.getUniqueId()); }

    @Override
    public void onDeath(Player victim, Player killer) {
        if (!downed.add(victim.getUniqueId())) return;
        clearDarkness(victim);
        restoreCombat(victim);
        bleedOut.put(victim.getUniqueId(), elapsedTicks + 900);
        victim.setHealth(1);
        victim.closeInventory();
        applyDownState(victim);
        Text.title(victim, "<yellow>Hold on!", soloSelfRevive && !selfReviveUsed
                ? "Self-revive in 8 seconds" : "Teammate: crouch within 2 blocks to revive");
        checkOverrun();
    }

    void tickRevives() {
        for (UUID id : new HashSet<>(bleedOut.keySet())) {
            Player patient = game.player(id);
            if (patient == null) continue;
            if (elapsedTicks >= bleedOut.get(id)) {
                bleedOut.remove(id);
                rescuers.remove(id);
                rescueSeconds.remove(id);
                clearDownState(patient);
                patient.setGameMode(org.bukkit.GameMode.SPECTATOR);
                patient.teleport(game.arena().spectator(game.world()));
                continue;
            }
            if (soloSelfRevive && !selfReviveUsed && elapsedTicks >= bleedOut.get(id) - 740) {
                selfReviveUsed = true;
                revive(patient, null);
                continue;
            }
            Player rescuer = activeSquad().stream().filter(Player::isSneaking)
                    .filter(p -> p.getLocation().distanceSquared(patient.getLocation()) <= 4)
                    .filter(p -> p.hasLineOfSight(patient))
                    .min(java.util.Comparator.comparing(p -> p.getUniqueId().toString())).orElse(null);
            if (rescuer == null) { rescuers.remove(id); rescueSeconds.remove(id); }
            else {
                if (!rescuer.getUniqueId().equals(rescuers.put(id, rescuer.getUniqueId()))) rescueSeconds.remove(id);
                int seconds = rescueSeconds.merge(id, 1, Integer::sum);
                rescuer.sendActionBar(Text.mm("<green>Reviving: " + seconds + "/8 seconds"));
                if (seconds >= 8) { revive(patient, rescuer); continue; }
            }
            patient.sendActionBar(Text.mm("<yellow>Hold on: " + (bleedOut.get(id) - elapsedTicks) / 20 + "s • crouch nearby to help"));
        }
        if (!downed.isEmpty()) checkOverrun();
    }

    private void revive(Player patient, Player rescuer) {
        UUID id = patient.getUniqueId();
        downed.remove(id); bleedOut.remove(id); rescuers.remove(id); rescueSeconds.remove(id);
        clearDownState(patient);
        patient.setHealth(maxHealth(patient) / 2);
        patient.setNoDamageTicks(40);
        applyLegacyCombat(patient);
        if (rescuer != null) {
            game.addScore(rescuer, 50);
            revives.merge(rescuer.getUniqueId(), 1, Integer::sum);
        }
        Text.title(patient, "<green>Back together!", "Half health • stay near your squad");
    }

    @Override
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        rescuers.entrySet().removeIf(entry -> {
            Player patient = game.player(entry.getKey());
            if (!entry.getValue().equals(event.getPlayer().getUniqueId()) || patient == null) return false;
            if (event.getTo().getWorld() == patient.getWorld()
                    && event.getTo().distanceSquared(patient.getLocation()) <= 4) return false;
            rescueSeconds.remove(entry.getKey()); return true;
        });
    }

    double maxHealth(Player player) {
        var health = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return health == null ? 20 : health.getValue();
    }

    void applyDownState(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 900, 4, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 900, 10, false, false));
    }

    void clearDownState(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
    }

    @Override
    public void onQuit(Player player) {
        clearDarkness(player);
        if (downed.contains(player.getUniqueId())) clearDownState(player);
        restoreCombat(player);
        UUID id = player.getUniqueId();
        downed.remove(id); bleedOut.remove(id); rescuers.remove(id); rescueSeconds.remove(id);
        rescuers.entrySet().removeIf(entry -> {
            if (!entry.getValue().equals(id)) return false;
            rescueSeconds.remove(entry.getKey()); return true;
        });
        if (game.state() == GameState.RUNNING) checkOverrun();
    }

    /** Seconds between pulses, settings-driven; the next gap is randomised between 1x and 1.5x this. */
    private long pulseGapTicks() {
        double interval = bounded("darkness-interval", 30, 10, 120) * (isFrenzy() ? 0.75 : 1.0);
        return Math.round(interval * (1 + 0.5 * pulseRoll.getAsDouble()) * 20);
    }

    /** Short darkness pulses at randomised times per player; never while a player is at a door prompt. */
    void pulseDarkness() {
        if (!roundActive || !game.arena().settings().getBoolean("darkness", true))
            return;
        int ticks = (int) Math.round(Math.max(1, Math.min(6, game.arena().settings().getDouble("darkness-duration", 2.5))) * 20);
        for (Player player : game.alivePlayers()) {
            if (game.isBot(player) || downed.contains(player.getUniqueId()))
                continue;
            if (progression.nearDoorPrompt(player)) {
                clearDarkness(player);
                continue;
            }
            Long due = nextPulse.get(player.getUniqueId());
            if (due == null)
                nextPulse.put(player.getUniqueId(), elapsedTicks + pulseGapTicks());
            else if (elapsedTicks >= due) {
                darken(player, ticks);
                nextPulse.put(player.getUniqueId(), elapsedTicks + pulseGapTicks());
            }
        }
    }

    void darken(Player player, int ticks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, true, false, true));
    }

    void clearDarkness(Player player) {
        player.removePotionEffect(PotionEffectType.DARKNESS);
    }

    @Override
    public void onEliminated(Player player) {
        clearDarkness(player);
        restoreCombat(player);
    }
    @Override
    public void onEnd(List<Player> winners) {
        onCleanup();
    }
    @Override
    public void onCleanup() {
        roundActive = false;
        game.everyone().stream().filter(p -> downed.contains(p.getUniqueId())).forEach(this::clearDownState);
        downed.clear(); bleedOut.clear(); rescuers.clear(); rescueSeconds.clear();
        game.everyone().forEach(this::clearDarkness);
        game.everyone().forEach(this::restoreCombat);
        originalAttackSpeed.clear();
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
        nextPulse.clear();
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

    /** Co-op end screen for the engine: framed as "Well played", never "game over", also on overrun. */
    @Override
    public EndSummary endSummary(List<Player> winners) {
        boolean cleared = !winners.isEmpty() && round >= totalRounds();
        boolean newBest = round > 0 && game.stats() != null && game.ranked()
            && game.stats().recordBest(bestKey(), round);
        if (newBest) bestRound = round;
        List<uk.co.playerready.pulsegames.games.common.CoopSummary.Entry> entries = new java.util.ArrayList<>();
        for (Player p : game.players())
            entries.add(new uk.co.playerready.pulsegames.games.common.CoopSummary.Entry(p.getName(),
                kills.getOrDefault(p.getUniqueId(), 0), revives.getOrDefault(p.getUniqueId(), 0), game.score(p)));
        return new EndSummary(uk.co.playerready.pulsegames.games.common.CoopSummary.title(cleared),
            uk.co.playerready.pulsegames.games.common.CoopSummary.subtitle(round, newBest),
            uk.co.playerready.pulsegames.games.common.CoopSummary.lines(round, bestRound, entries));
    }

    private void checkOverrun() {
        List<Player> squad = game.alivePlayers();
        if (soloSelfRevive && !selfReviveUsed && !bleedOut.isEmpty()) return;
        if (squad.isEmpty() || squad.stream().allMatch(p -> downed.contains(p.getUniqueId()))) {
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
    public Location botGuardPoint() { return guardPointFor(null); }

    /** Each bot follows the standing human nearest to it, not the same one as its squadmates. */
    @Override
    public Location botGuardPoint(Player bot) { return guardPointFor(bot); }

    private Location guardPointFor(Player bot) {
        Player patient = downedHuman();
        if (patient != null) return patient.getLocation();
        ZMapProgression.Door door = progression.cheapestAvailable();
        if (door != null && game.world() != null && game.alivePlayers().stream()
                .anyMatch(p -> game.isBot(p) && (bot == null || p == bot)
                    && game.score(p) >= door.cost() + BOT_DOOR_RESERVE))
            return progression.approach(door);
        var humans = activeSquad().stream().filter(p -> !game.isBot(p));
        Player human = bot == null ? humans.findFirst().orElse(null)
            : humans.filter(p -> p.getWorld() == bot.getWorld())
                .min(java.util.Comparator.comparingDouble(p -> p.getLocation().distanceSquared(bot.getLocation())))
                .orElse(null);
        return human != null ? human.getLocation() : game.arena().spawn(0, game.world());
    }

    private Player downedHuman() {
        return game.alivePlayers().stream()
            .filter(p -> !game.isBot(p) && downed.contains(p.getUniqueId()) && p.getWorld() == game.world())
            .findFirst().orElse(null);
    }

    @Override
    public void botTick(Player bot) {
        if (!game.isBot(bot) || !game.isActive(bot) || downed.contains(bot.getUniqueId()))
            return;
        // Revive: a downed human within 8 blocks; crouch once beside them (tickRevives counts the 8 seconds).
        Player patient = downedHuman();
        boolean helping = patient != null && patient.getWorld() == bot.getWorld()
            && patient.getLocation().distanceSquared(bot.getLocation()) <= 64;
        bot.setSneaking(helping && patient.getLocation().distanceSquared(bot.getLocation()) <= 3);
        if (bot.getHealth() <= 10 && purchase(bot, 6))
            return;
        // Cheapest unlocked door first, but only with a reserve left over; buyDoor itself needs the bot at the door.
        ZMapProgression.Door door = progression.cheapestAvailable();
        if (door != null && game.score(bot) >= door.cost() + BOT_DOOR_RESERVE && progression.buyDoor(bot, door))
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
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
            && event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
            var clicked = event.getClickedBlock();
            if (boxLocations().stream().anyMatch(l -> sameCell(l, clicked))) {
                event.setCancelled(true);
                if (!mysteryBox(event.getPlayer())) rejected(event.getPlayer());
                return;
            }
            var pap = packLocation();
            if (pap != null && sameCell(pap, clicked)) {
                event.setCancelled(true);
                if (!packAPunch(event.getPlayer())) rejected(event.getPlayer());
                return;
            }
        }
        if (event.getItem() != null && event.getItem().getType() == Material.EMERALD
            && event.getAction().isRightClick()) {
            event.setCancelled(true);
            openShop(event.getPlayer());
        }
    }

    private static boolean sameCell(Location l, org.bukkit.block.Block b) {
        return l.getWorld() == b.getWorld() && l.getBlockX() == b.getX() && l.getBlockY() == b.getY()
            && l.getBlockZ() == b.getZ();
    }

    private void rejected(Player player) {
        player.sendActionBar(Text.mm("<red>Not enough points, or nothing to use here right now."));
    }

    /** Optional arena locations {@code mysterybox1..N}; empty means the Supply Shop offers the box instead. */
    List<Location> boxLocations() {
        List<Location> out = new java.util.ArrayList<>();
        if (game.world() == null) return out;
        for (int i = 1; i <= 16; i++) {
            Location l = game.arena().settingLocation("mysterybox" + i, game.world());
            if (l != null) out.add(l);
        }
        return out;
    }

    Location packLocation() {
        return game.world() == null ? null : game.arena().settingLocation("packapunch", game.world());
    }

    boolean playable(Player player) {
        return game.state() == GameState.RUNNING && game.isActive(player) && !downed.contains(player.getUniqueId())
            && player.getWorld() == game.world();
    }

    /** 950 points (+10% per use this round): 3 s action-bar spin, then a weighted weapon or perk. */
    boolean mysteryBox(Player player) {
        UUID id = player.getUniqueId();
        int price = ZMysteryBox.price(boxUses.getOrDefault(id, 0));
        if (!playable(player) || spinning.contains(id) || game.score(player) < price) return false;
        game.addScore(player, -price);
        boxUses.merge(id, 1, Integer::sum);
        spinning.add(id);
        var prize = ZMysteryBox.roll(ThreadLocalRandom.current());
        for (int step = 0; step < ZMysteryBox.SPIN_STEPS; step++) {
            boolean last = step == ZMysteryBox.SPIN_STEPS - 1;
            var shown = last ? prize : ZMysteryBox.roll(ThreadLocalRandom.current());
            game.runLater((long) step * ZMysteryBox.SPIN_STEP_TICKS, () -> {
                if (!playable(player)) return;
                player.sendActionBar(Text.mm((last ? "<green>" : "<light_purple>") + "[ " + shown.label + " ]"));
                player.playSound(player.getLocation(), "minecraft:block.note_block.hat", .7f, last ? 1.6f : 1f);
            });
        }
        game.runLater((long) ZMysteryBox.SPIN_STEPS * ZMysteryBox.SPIN_STEP_TICKS, () -> {
            spinning.remove(id);
            if (!playable(player) || (prize.weapon() && player.getInventory().firstEmpty() < 0)) {
                if (game.state() == GameState.RUNNING && game.isActive(player)) game.addScore(player, price);
                return;
            }
            grant(player, prize);
            Text.title(player, "<light_purple>" + prize.label, "<gray>From the Mystery Box");
        });
        return true;
    }

    private void grant(Player player, ZMysteryBox.Prize prize) {
        var inv = player.getInventory();
        switch (prize) {
            case STONE_SHARP -> {
                ItemStack sword = new ItemStack(Material.STONE_SWORD);
                sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 2);
                inv.addItem(sword);
            }
            case IRON_SWORD -> inv.addItem(new ItemStack(Material.IRON_SWORD));
            case DIAMOND_SWORD -> inv.addItem(new ItemStack(Material.DIAMOND_SWORD));
            case BOW -> { inv.addItem(new ItemStack(Material.BOW)); inv.addItem(new ItemStack(Material.ARROW, 24)); }
            case CROSSBOW -> { inv.addItem(new ItemStack(Material.CROSSBOW)); inv.addItem(new ItemStack(Material.ARROW, 24)); }
            case SPEED -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 600, 1));
            case TOUGH -> player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 600, 1));
            case REGEN -> player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 600, 0));
        }
    }

    static final String PUNCHED_LORE = "Pack-a-Punched";

    static boolean isPunched(ItemStack item) {
        var meta = item.getItemMeta();
        return meta != null && meta.hasLore() && meta.lore().stream()
            .anyMatch(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(line).contains(PUNCHED_LORE));
    }

    /** 5000 points: upgrades the weapon in hand once (Sharpness III / Power III / Quick Charge II, renamed, glint). */
    boolean packAPunch(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!playable(player) || held == null || !ZMysteryBox.punchable(held.getType()) || isPunched(held)
            || game.score(player) < ZMysteryBox.PACK_A_PUNCH_PRICE)
            return false;
        game.addScore(player, -ZMysteryBox.PACK_A_PUNCH_PRICE);
        var type = held.getType();
        var enchant = type == Material.BOW ? org.bukkit.enchantments.Enchantment.POWER
            : type == Material.CROSSBOW ? org.bukkit.enchantments.Enchantment.QUICK_CHARGE
            : org.bukkit.enchantments.Enchantment.SHARPNESS;
        held.addUnsafeEnchantment(enchant, type == Material.CROSSBOW ? 2 : 3);
        held.editMeta(meta -> {
            String base = type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            meta.displayName(Text.mm("<aqua><b>Punched " + base));
            meta.lore(List.of(Text.mm("<gray>" + PUNCHED_LORE)));
            meta.setEnchantmentGlintOverride(true);
        });
        player.getInventory().setItemInMainHand(held);
        player.playSound(player.getLocation(), "minecraft:block.anvil.use", .8f, 1.4f);
        Text.title(player, "<aqua>Pack-a-Punched!", "<gray>Your weapon hits harder");
        return true;
    }

    private void openShop(Player player) {
        if(game.state()!=GameState.RUNNING||!game.isActive(player)||downed.contains(player.getUniqueId())||player.getWorld()!=game.world())return;
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm("<dark_gray>Supply Shop"));
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
        if (boxLocations().isEmpty())
            inv.setItem(BOX_SLOT, shopIcon(Material.ENDER_CHEST, "<light_purple>Mystery Box",
                ZMysteryBox.price(boxUses.getOrDefault(player.getUniqueId(), 0)) + " points",
                "Random weapon or perk"));
        if (packLocation() == null)
            inv.setItem(PAP_SLOT, shopIcon(Material.ANVIL, "<aqua>Pack-a-Punch", ZMysteryBox.PACK_A_PUNCH_PRICE + " points",
                "Upgrade the weapon in your hand once"));
        player.openInventory(inv);
    }

    private ItemStack shopIcon(Material icon, String name, String cost, String detail) {
        ItemStack item = new ItemStack(icon);
        item.editMeta(meta -> {
            meta.displayName(Text.mm(name));
            meta.lore(List.of(Text.mm("<gold>" + cost), Text.mm("<gray>" + detail)));
        });
        return item;
    }

    @Override
    public void onInventoryClick(Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder))
            return;
        event.setCancelled(true);
        int slot = event.getSlot();
        if (event.getClickedInventory() != event.getInventory() || slot < 0
            || !(slot < upgrades.size() || slot == BOX_SLOT || slot == PAP_SLOT))
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
        if (slot == BOX_SLOT) return boxLocations().isEmpty() && mysteryBox(player);
        if (slot == PAP_SLOT) return packLocation() == null && packAPunch(player);
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
        List<String> lines = new java.util.ArrayList<>(List.of("<gray>Round: <red>" + round + "/" + totalRounds(),
            "<gray>Zombies left: <red>" + (zombies.size() + pendingSpawns), "<gold>Points: <white>" + game.score(player),
            "<gray>Team: <white>" + (game.alivePlayers().size() - downed.size()) + " up / " + downed.size() + " down"));
        if (bestRound > 0) lines.add("<gray>Map best: <white>round " + bestRound);
        if (progression.doorCount() > 0) {
            var next = progression.nextDoor();
            lines.add("<gray>Doors: <white>" + progression.doorsOpened() + "/" + progression.doorCount()
                + (next == null ? "" : " <gray>next <gold>" + next.cost()));
        }
        return lines;
    }

    @Override
    public int timeLimitSeconds() {
        return 2400;
    }
    @Override
    public String phaseCountdown() { return !roundActive && breakTimer > 0 ? "Next wave: " + breakTimer + "s" : null; }

}
