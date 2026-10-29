package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.shop.ShopTiles;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsKitShapes.Pos;
import uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The behaviours behind the {@code custom:} key of bedwars-shop.yml. One instance per match, owned by BedwarsGame,
 * which forwards the engine hooks. Items are plain vanilla materials with a name, lore and a hidden tag (works on
 * Bedrock through Geyser, no resource pack). Everything is cosmetic or tactical, every use has a cooldown, and
 * {@link #clear()} removes every block, entity and task it made when the match ends.
 * Author: AurorasChaos (on behalf of Player Ready).
 */
public final class BedwarsCustomItems {

    /** Every behaviour the shop file may name. A switch over this enum is exhaustive, so no id lacks a handler. */
    public enum Custom {
        KNOCKBACK_STICK(0), MAGIC_MILK(30), BRIDGE_EGG(0), POPUP_TOWER(0), DREAM_DEFENDER(240), BED_BUG(15),
        GRAPPLING_HOOK(0), BOUNCE_PAD(60), ICE_ROAD(45), SMOKE_BOMB(5), FREEZE_BALL(3), ROCKET_BOOTS(0),
        RECALL_TOTEM(5), SHIELD_WALL(30), HONEY_TRAP(60), DECOY_DUMMY(30);

        public final String id;
        /** Default effect or object lifetime in seconds when the shop file gives none. */
        public final int seconds;
        Custom(int seconds) { this.id = name().toLowerCase(java.util.Locale.ROOT); this.seconds = seconds; }

        public static Custom byId(String id) {
            for (Custom c : values()) if (c.id.equals(id)) return c;
            return null;
        }
    }

    public static final Set<String> IDS = Set.of(java.util.Arrays.stream(Custom.values()).map(c -> c.id).toArray(String[]::new));

    static final int ROCKET_USES = 3;
    static final int DECOY_HITS = 5;
    static final int RECALL_SECONDS = 5;
    private static final NamespacedKey TAG = NamespacedKey.fromString("pulsegames:bw_custom");
    private static final NamespacedKey USES = NamespacedKey.fromString("pulsegames:bw_uses");

    private enum Outcome { FAILED, CONSUMED, KEPT }
    private record Thrown(Custom custom, UUID owner) {}
    private static final class Summon {
        final Entity entity; final Custom kind; final int team; final int expiresAt; int hits = DECOY_HITS;
        Summon(Entity entity, Custom kind, int team, int expiresAt) { this.entity = entity; this.kind = kind; this.team = team; this.expiresAt = expiresAt; }
    }
    private record Patch(Location centre, int team, int expiresAt) {}

    private final GameInstance game;
    private final Supplier<ShopCatalogue> catalogue;
    private final Predicate<Block> bedProtected;
    private final Consumer<Block> markPlayerBlock;
    private final Function<GameTeam, Location> home;
    private final Cooldowns cooldowns = new Cooldowns(System::currentTimeMillis);
    private final TempBlocks temp;
    private final Map<UUID, Thrown> thrown = new HashMap<>();
    private final Map<UUID, Long> milkUntil = new HashMap<>();
    private final Map<UUID, BukkitTask> recalls = new HashMap<>();
    private final Set<Block> pads = new HashSet<>();
    private final List<Summon> summons = new ArrayList<>();
    private final List<Patch> patches = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    /**
     * @param bedProtected true for any block inside a bed region (never built over or turned to honey)
     * @param markPlayerBlock registers a block as player-placed so it can be broken and exploded
     * @param home where Recall Totem takes this player's team, or null when their bed is gone
     */
    BedwarsCustomItems(GameInstance game, Supplier<ShopCatalogue> catalogue, Predicate<Block> bedProtected,
                       Consumer<Block> markPlayerBlock, Consumer<Block> forgetPlayerBlock, Function<GameTeam, Location> home) {
        this.game = game;
        this.catalogue = catalogue;
        this.bedProtected = bedProtected;
        this.markPlayerBlock = markPlayerBlock;
        this.home = home;
        this.temp = new TempBlocks(block -> { pads.remove(block); forgetPlayerBlock.accept(block); });
    }

    // ---- items ---------------------------------------------------------------------

    /** Name, lore and the hidden tag for a bought item. Plain vanilla material underneath. */
    public ItemStack decorate(ItemStack stack, ShopCatalogue.Item entry) {
        return decorate(stack, entry, entry.custom() != null && entry.custom().equals(Custom.ROCKET_BOOTS.id) ? ROCKET_USES : 0);
    }

    private ItemStack decorate(ItemStack stack, ShopCatalogue.Item entry, int uses) {
        Lore lore = Lore.of();
        if (entry.isNew()) lore.line("<yellow><b>NEW!</b> <gray>Funky item");
        lore.desc(entry.blurb());
        if (entry.cooldown() > 0) lore.kv("Cooldown", entry.cooldown() + " seconds");
        if (uses > 0) lore.kv("Uses left", String.valueOf(uses));
        ShopTiles.dress(stack, "<green><b>" + entry.name() + (uses > 0 ? " (" + uses + " left)" : ""), lore.build(), false);
        stack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, entry.custom());
            if (uses > 0) meta.getPersistentDataContainer().set(USES, PersistentDataType.INTEGER, uses);
            if (entry.custom().equals(Custom.KNOCKBACK_STICK.id)) meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
            if (entry.custom().equals(Custom.GRAPPLING_HOOK.id)) meta.setUnbreakable(true);
        });
        return stack;
    }

    Custom custom(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(TAG, PersistentDataType.STRING);
        return id == null ? null : Custom.byId(id);
    }

    private ShopCatalogue.Item entry(Custom c) { return catalogue.get().byId(c.id); }
    private int cooldownOf(Custom c) { var e = entry(c); return e == null ? 0 : e.cooldown(); }
    private int secondsOf(Custom c) { var e = entry(c); return e == null || e.seconds() <= 0 ? c.seconds : e.seconds(); }

    // ---- engine hooks ----------------------------------------------------------------

    /** @return true when the click belonged to a shop custom item. */
    public boolean onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return false;
        Custom c = custom(event.getItem());
        if (c == null) return false;
        Player player = event.getPlayer();
        if (c == Custom.KNOCKBACK_STICK) return false;
        if (c == Custom.BOUNCE_PAD) return true; // placed like a block; see onBlockPlaced
        if (c == Custom.GRAPPLING_HOOK) { grapple(player); return true; }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (!cooldowns.ready(player.getUniqueId(), c.id)) { tellWait(player, c); return true; }
        Outcome outcome = use(c, player, event);
        if (outcome == Outcome.FAILED) return true;
        cooldowns.start(player.getUniqueId(), c.id, cooldownOf(c));
        if (outcome == Outcome.CONSUMED) consume(player, event.getHand());
        return true;
    }

    /** TNT primes the moment it is placed; a bounce pad is registered; any other tagged block is refused. */
    public void onBlockPlaced(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.TNT) {
            block.setType(Material.AIR);
            Location at = block.getLocation().add(0.5, 0, 0.5);
            game.world().spawn(at, TNTPrimed.class, tnt -> { tnt.setFuseTicks(60); tnt.setSource(player); });
            player.playSound(at, Sound.ENTITY_TNT_PRIMED, 1f, 1f);
            return;
        }
        Custom c = custom(event.getItemInHand());
        if (c == null) return;
        if (c != Custom.BOUNCE_PAD || !cooldowns.ready(player.getUniqueId(), c.id)) {
            if (c == Custom.BOUNCE_PAD) tellWait(player, c);
            event.setCancelled(true);
            return;
        }
        cooldowns.start(player.getUniqueId(), c.id, cooldownOf(c));
        temp.adopt(block, event.getBlockReplacedState().getBlockData(), game.gameTime() + secondsOf(c));
        pads.add(block);
        player.playSound(block.getLocation(), Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 1f);
    }

    public void onProjectileHit(ProjectileHitEvent event) {
        Thrown t = thrown.remove(event.getEntity().getUniqueId());
        if (t == null) return;
        Player owner = game.player(t.owner());
        Location at = event.getHitBlock() != null ? event.getHitBlock().getLocation().add(0.5, 1, 0.5)
                : event.getHitEntity() != null ? event.getHitEntity().getLocation() : event.getEntity().getLocation();
        switch (t.custom()) {
            case BED_BUG -> bedBug(owner, at);
            case SMOKE_BOMB -> smoke(owner, at);
            case FREEZE_BALL -> {
                if (event.getHitEntity() instanceof Player victim && owner != null && isEnemy(owner, victim)) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, secondsOf(Custom.FREEZE_BALL) * 20, 1));
                    victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
                    victim.playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 1.6f);
                }
            }
            default -> { }
        }
    }

    /** Stepping on a bounce pad. Called for block-change moves only. */
    public void onMove(Player player, Location to) {
        if (pads.isEmpty()) return;
        Block under = to.clone().subtract(0, 0.2, 0).getBlock();
        if (!pads.contains(under) || !cooldowns.ready(player.getUniqueId(), "pad-step")) return;
        cooldowns.start(player.getUniqueId(), "pad-step", 1);
        Vector push = player.getLocation().getDirection().setY(0).multiply(0.5);
        player.setVelocity(push.setY(1.2));
        player.setFallDistance(0);
        player.playSound(to, Sound.ENTITY_SLIME_JUMP, 1f, 1.2f);
        to.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, to.clone().add(0, 0.3, 0), 10, 0.3, 0.1, 0.3);
    }

    /** Being hurt breaks a Recall Totem's concentration. */
    public void onDamage(Player victim, EntityDamageEvent event) {
        if (recalls.containsKey(victim.getUniqueId()) && event.getFinalDamage() > 0) cancelRecall(victim, "You got hurt, so the recall stopped.");
    }

    /** Hits on defenders, bugs and decoys: no drops, no friendly fire, decoys pop after a few hits. */
    public void onEntityDamage(EntityDamageEvent event) {
        Summon s = summonOf(event.getEntity());
        if (s == null) return;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity source = byEntity.getDamager() instanceof org.bukkit.entity.Projectile p && p.getShooter() instanceof Entity e ? e : byEntity.getDamager();
            GameTeam mine = teamIndexOf(s.team);
            if (source instanceof Player attacker && mine != null && mine.contains(attacker)) { event.setCancelled(true); return; }
        }
        if (s.kind == Custom.DECOY_DUMMY) {
            event.setCancelled(true);
            s.entity.getWorld().playSound(s.entity.getLocation(), Sound.ENTITY_ARMOR_STAND_HIT, 1f, 1f);
            if (--s.hits <= 0) pop(s);
            return;
        }
        if (s.entity instanceof LivingEntity living && event.getFinalDamage() >= living.getHealth()) {
            event.setCancelled(true);
            pop(s);
        }
    }

    /** Once a second: melt and expire things, keep summons hunting, slow whoever stands in honey. */
    public void tick(int now) {
        temp.expire(now);
        milkUntil.values().removeIf(until -> until < System.currentTimeMillis());
        for (Iterator<Summon> it = summons.iterator(); it.hasNext(); ) {
            Summon s = it.next();
            if (!s.entity.isValid() || now >= s.expiresAt) { it.remove(); vanish(s.entity); continue; }
            if (s.entity instanceof Mob mob) hunt(mob, s.team);
        }
        for (Iterator<Patch> it = patches.iterator(); it.hasNext(); ) {
            Patch patch = it.next();
            if (now >= patch.expiresAt()) { it.remove(); continue; }
            for (Player p : game.alivePlayers()) {
                GameTeam mine = teamIndexOf(patch.team());
                if (mine != null && mine.contains(p)) continue;
                Location l = p.getLocation();
                if (l.getWorld() == patch.centre().getWorld() && Math.abs(l.getX() - patch.centre().getX()) <= 1.8
                        && Math.abs(l.getZ() - patch.centre().getZ()) <= 1.8 && Math.abs(l.getY() - patch.centre().getY()) <= 2)
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true));
            }
        }
    }

    /** True while Magic Milk protects this player from team traps. */
    public boolean trapImmune(Player player) {
        return milkUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    public void playerGone(Player player) { cancelRecall(player, null); }

    /** Match over (or world unloading): put every block back, remove every entity and stop every task. */
    public void clear() {
        temp.clear();
        for (Summon s : summons) vanish(s.entity);
        summons.clear();
        patches.clear();
        pads.clear();
        thrown.clear();
        milkUntil.clear();
        recalls.values().forEach(BukkitTask::cancel);
        recalls.clear();
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        cooldowns.clear();
    }

    /** A bot uses a gadget it owns. @return true when it did. */
    boolean botUse(Player bot, Custom c) { return botUse(bot, c, null, null); }

    /**
     * A bot uses a gadget from its own inventory by the same route a child does: the item goes to its hand and a real
     * {@link PlayerInteractEvent} (or {@link BlockPlaceEvent} for a Bounce Pad) is fired, so {@link #onInteract}, the
     * cooldowns, the engine's rules and any protection plugin apply unchanged. The Grappling Hook has no cast to wait
     * for, so it shares the pull and the cooldown with the human path.
     * @param aim where to look (and throw), or null to use the way the bot already faces
     * @param clicked the block "clicked" for the Honey Trap and Bounce Pad (the ground), else null
     */
    boolean botUse(Player bot, Custom c, Location aim, Block clicked) {
        if (!cooldowns.ready(bot.getUniqueId(), c.id) || recalls.containsKey(bot.getUniqueId())) return false;
        var inventory = bot.getInventory();
        int slot = -1;
        for (int i = 0; i < 36 && slot < 0; i++) if (custom(inventory.getItem(i)) == c) slot = i;
        if (slot < 0) return false;
        int hand = inventory.getHeldItemSlot();
        if (slot >= 9) { // swap it into the hotbar slot that is in hand
            ItemStack other = inventory.getItem(hand);
            inventory.setItem(hand, inventory.getItem(slot));
            inventory.setItem(slot, other);
        } else { hand = slot; inventory.setHeldItemSlot(hand); }
        if (c == Custom.KNOCKBACK_STICK) return true; // holding it is the use
        if (aim != null) face(bot, aim, c == Custom.FREEZE_BALL || c == Custom.SMOKE_BOMB || c == Custom.BED_BUG);
        if (c == Custom.GRAPPLING_HOOK) return botGrapple(bot, aim);
        ItemStack stack = inventory.getItem(hand);
        if (c == Custom.BOUNCE_PAD) return botPlacePad(bot, clicked, stack, hand);
        var event = new PlayerInteractEvent(bot, clicked == null ? org.bukkit.event.block.Action.RIGHT_CLICK_AIR : org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                stack, clicked, BlockFace.UP, EquipmentSlot.HAND);
        game.plugin().getServer().getPluginManager().callEvent(event);
        return !cooldowns.ready(bot.getUniqueId(), c.id);
    }

    private void face(Player bot, Location aim, boolean lob) {
        Vector d = aim.toVector().subtract(bot.getEyeLocation().toVector());
        double flat = Math.hypot(d.getX(), d.getZ());
        if (lob) d.setY(d.getY() + flat * 0.05); // a thrown ball drops a little on the way
        if (flat < 0.01 && Math.abs(d.getY()) < 0.01) return;
        bot.setRotation((float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ())), (float) Math.toDegrees(-Math.atan2(d.getY(), flat)));
    }

    private boolean botGrapple(Player bot, Location target) {
        if (target == null || target.getWorld() != bot.getWorld()) return false;
        return pull(bot, target); // the rod is unbreakable and kept
    }

    private boolean botPlacePad(Player bot, Block ground, ItemStack stack, int hand) {
        if (ground == null) return false;
        Block at = ground.getRelative(BlockFace.UP);
        if (!at.getType().isAir() || bedProtected.test(at)) return false;
        var replaced = at.getState();
        at.setType(Material.SLIME_BLOCK, false);
        var event = new BlockPlaceEvent(at, replaced, ground, stack, bot, true, EquipmentSlot.HAND);
        game.plugin().getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || !event.canBuild() || cooldowns.ready(bot.getUniqueId(), Custom.BOUNCE_PAD.id)) {
            replaced.update(true, false);
            return false;
        }
        takeFrom(bot, hand);
        return true;
    }

    /** Held and off cooldown, by shop id, plus Invisibility Potion: what a bot could reach for right now. */
    Set<String> botReady(Player bot) {
        Set<String> ready = new HashSet<>();
        if (recalls.containsKey(bot.getUniqueId())) return ready;
        for (ItemStack item : bot.getInventory().getContents()) {
            Custom c = custom(item);
            if (c != null && (c == Custom.KNOCKBACK_STICK || cooldowns.ready(bot.getUniqueId(), c.id))) ready.add(c.id);
            else if (item != null && item.getType() == Material.POTION && item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta meta
                    && meta.getCustomEffects().stream().anyMatch(e -> e.getType().equals(PotionEffectType.INVISIBILITY))) ready.add(BedwarsBotFunky.INVISIBILITY);
        }
        return ready;
    }

    /** Test hook: starts a cooldown as a use would. */
    void startCooldown(UUID player, Custom c) { cooldowns.start(player, c.id, Math.max(1, cooldownOf(c))); }

    /** A hostile thing a bot may fight: another team's decoy, or a defender or bug that is attacking it. */
    record Foe(LivingEntity entity, boolean decoy, UUID id) {}

    List<Foe> foesOf(Player bot) {
        List<Foe> foes = new ArrayList<>();
        GameTeam mine = game.teamOf(bot);
        for (Summon s : summons) {
            if (!(s.entity instanceof LivingEntity living) || !s.entity.isValid() || (mine != null && s.team == mine.index())) continue;
            if (s.kind == Custom.DECOY_DUMMY) foes.add(new Foe(living, true, s.entity.getUniqueId()));
            else if (s.entity instanceof Mob mob && bot.equals(mob.getTarget())) foes.add(new Foe(living, false, s.entity.getUniqueId()));
        }
        return foes;
    }

    boolean defenderActive(int team) {
        for (Summon s : summons) if (s.kind == Custom.DREAM_DEFENDER && s.team == team && s.entity.isValid()) return true;
        return false;
    }

    int liveSummons() { return summons.size(); }
    int tempBlockCount() { return temp.size(); }

    // ---- behaviours ------------------------------------------------------------------

    private Outcome use(Custom c, Player p, PlayerInteractEvent event) {
        return switch (c) {
            case MAGIC_MILK -> {
                milkUntil.put(p.getUniqueId(), System.currentTimeMillis() + secondsOf(c) * 1000L);
                p.sendMessage(Text.msg("<white>Magic Milk! <gray>Enemy traps ignore you for " + secondsOf(c) + " seconds."));
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
                yield Outcome.CONSUMED;
            }
            case BRIDGE_EGG -> { bridgeEgg(p); yield Outcome.CONSUMED; }
            case POPUP_TOWER -> popUpTower(p);
            case DREAM_DEFENDER -> dreamDefender(p);
            case BED_BUG -> { throwBall(p, c, Material.SILVERFISH_SPAWN_EGG); yield Outcome.CONSUMED; }
            case SMOKE_BOMB -> { throwBall(p, c, Material.GUNPOWDER); yield Outcome.CONSUMED; }
            case FREEZE_BALL -> { throwBall(p, c, Material.SNOWBALL); yield Outcome.CONSUMED; }
            case ICE_ROAD -> iceRoad(p);
            case ROCKET_BOOTS -> rocketBoots(p, event);
            case RECALL_TOTEM -> recall(p);
            case SHIELD_WALL -> shieldWall(p);
            case HONEY_TRAP -> honeyTrap(p, event.getClickedBlock());
            case DECOY_DUMMY -> decoy(p);
            case KNOCKBACK_STICK, BOUNCE_PAD, GRAPPLING_HOOK -> Outcome.FAILED;
        };
    }

    private void grapple(Player p) {
        FishHook hook = p.getFishHook();
        if (hook == null || !hook.isValid()) return; // first click casts: vanilla does that
        boolean landed = hook.getVelocity().lengthSquared() < 0.04 || hook.getHookedEntity() != null;
        if (!landed) return;
        if (!cooldowns.ready(p.getUniqueId(), Custom.GRAPPLING_HOOK.id)) { tellWait(p, Custom.GRAPPLING_HOOK); return; }
        pull(p, hook.getLocation());
    }

    /** Swing the player toward a point and start the cooldown. @return false when it is too near or too far. */
    private boolean pull(Player p, Location to) {
        Vector pull = to.toVector().subtract(p.getLocation().toVector());
        double distance = pull.length();
        if (distance < 1.5 || distance > 40) return false;
        cooldowns.start(p.getUniqueId(), Custom.GRAPPLING_HOOK.id, cooldownOf(Custom.GRAPPLING_HOOK));
        p.setVelocity(pull.normalize().multiply(Math.min(1.7, 0.4 + distance * 0.06)).add(new Vector(0, 0.35, 0)));
        p.setFallDistance(0);
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.8f);
        return true;
    }

    private <T extends org.bukkit.entity.ThrowableProjectile> T throwBall(Player p, Custom c, Material look, Class<T> type) {
        T ball = p.launchProjectile(type);
        ball.setItem(new ItemStack(look));
        thrown.put(ball.getUniqueId(), new Thrown(c, p.getUniqueId()));
        p.playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 1f);
        return ball;
    }
    private void throwBall(Player p, Custom c, Material look) { throwBall(p, c, look, Snowball.class); }

    /** The egg lays a one-block line of team wool under its flight, from the third tick, for at most 3 seconds. */
    private void bridgeEgg(Player p) {
        Egg egg = throwBall(p, Custom.BRIDGE_EGG, Material.EGG, Egg.class);
        GameTeam team = game.teamOf(p);
        Material wool = team == null ? Material.WHITE_WOOL : team.palette().wool();
        Location[] last = {egg.getLocation()};
        int[] ticks = {0}, placed = {0};
        BukkitTask[] self = new BukkitTask[1];
        self[0] = game.runRepeating(1, 1, () -> {
            if (!egg.isValid() || ++ticks[0] > 60 || placed[0] >= BedwarsKitShapes.BRIDGE_MAX_CELLS) { self[0].cancel(); thrown.remove(egg.getUniqueId()); return; }
            Location now = egg.getLocation();
            if (ticks[0] > 2) {
                for (Pos cell : BedwarsKitShapes.bridgeCells(last[0].getX(), last[0].getY(), last[0].getZ(), now.getX(), now.getY(), now.getZ())) {
                    if (placed[0] >= BedwarsKitShapes.BRIDGE_MAX_CELLS) break;
                    if (placeWool(now.getWorld().getBlockAt(cell.x(), cell.y(), cell.z()), wool)) {
                        placed[0]++;
                        now.getWorld().playSound(now, Sound.BLOCK_WOOL_PLACE, 0.5f, 1.2f);
                    }
                }
            }
            last[0] = now;
        });
        tasks.add(self[0]);
    }

    private boolean placeWool(Block block, Material wool) {
        if (!BedwarsKitShapes.canReplace(block.getType()) || bedProtected.test(block)) return false;
        block.setType(wool, false);
        markPlayerBlock.accept(block);
        return true;
    }

    private Outcome popUpTower(Player p) {
        GameTeam team = game.teamOf(p);
        Material wool = team == null ? Material.WHITE_WOOL : team.palette().wool();
        int[] f = BedwarsKitShapes.cardinal(p.getLocation().getDirection().getX(), p.getLocation().getDirection().getZ());
        Block feet = p.getLocation().getBlock();
        var world = feet.getWorld();
        var pieces = BedwarsKitShapes.tower(new Pos(feet.getX(), feet.getY(), feet.getZ()), f[0], f[1]);
        int built = 0;
        for (var piece : pieces) {
            if (piece.kind() != BedwarsKitShapes.Kind.WOOL) continue;
            if (placeWool(world.getBlockAt(piece.pos().x(), piece.pos().y(), piece.pos().z()), wool)) built++;
        }
        if (built == 0) { p.sendMessage(Text.msg("<red>No room for a tower here. Try somewhere open.")); return Outcome.FAILED; }
        BlockFace facing = face(-f[0], -f[1]);
        for (var piece : pieces) {
            if (piece.kind() != BedwarsKitShapes.Kind.LADDER) continue;
            Block at = world.getBlockAt(piece.pos().x(), piece.pos().y(), piece.pos().z());
            Block wall = at.getRelative(facing.getOppositeFace());
            if (!BedwarsKitShapes.canReplace(at.getType()) || bedProtected.test(at) || !wall.getType().isSolid()) continue;
            Ladder ladder = (Ladder) Material.LADDER.createBlockData();
            ladder.setFacing(facing);
            at.setBlockData(ladder, false);
            markPlayerBlock.accept(at);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_WOOL_PLACE, 1f, 0.8f);
        return Outcome.CONSUMED;
    }

    private Outcome shieldWall(Player p) {
        GameTeam team = game.teamOf(p);
        Material wool = team == null ? Material.WHITE_WOOL : team.palette().wool();
        int[] f = BedwarsKitShapes.cardinal(p.getLocation().getDirection().getX(), p.getLocation().getDirection().getZ());
        Block feet = p.getLocation().getBlock();
        int built = 0, expires = game.gameTime() + secondsOf(Custom.SHIELD_WALL);
        for (Pos cell : BedwarsKitShapes.wall(new Pos(feet.getX(), feet.getY(), feet.getZ()), f[0], f[1])) {
            Block b = feet.getWorld().getBlockAt(cell.x(), cell.y(), cell.z());
            if (!BedwarsKitShapes.canReplace(b.getType()) || bedProtected.test(b)) continue;
            temp.place(b, wool.createBlockData(), expires);
            markPlayerBlock.accept(b);
            built++;
        }
        if (built == 0) { p.sendMessage(Text.msg("<red>No room for a wall here.")); return Outcome.FAILED; }
        p.playSound(p.getLocation(), Sound.BLOCK_WOOL_PLACE, 1f, 0.8f);
        return Outcome.CONSUMED;
    }

    private Outcome iceRoad(Player p) {
        int[] f = BedwarsKitShapes.cardinal(p.getLocation().getDirection().getX(), p.getLocation().getDirection().getZ());
        Block feet = p.getLocation().getBlock();
        int built = 0, expires = game.gameTime() + secondsOf(Custom.ICE_ROAD);
        for (Pos cell : BedwarsKitShapes.iceRoad(new Pos(feet.getX(), feet.getY(), feet.getZ()), f[0], f[1])) {
            Block b = feet.getWorld().getBlockAt(cell.x(), cell.y(), cell.z());
            if (!BedwarsKitShapes.canReplace(b.getType()) || bedProtected.test(b)) continue;
            temp.place(b, Material.PACKED_ICE.createBlockData(), expires);
            markPlayerBlock.accept(b);
            built++;
        }
        if (built == 0) { p.sendMessage(Text.msg("<red>The ice has nowhere to go. Face open space.")); return Outcome.FAILED; }
        p.playSound(p.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 1.4f);
        return Outcome.CONSUMED;
    }

    private Outcome rocketBoots(Player p, PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        int uses = item.getItemMeta().getPersistentDataContainer().getOrDefault(USES, PersistentDataType.INTEGER, 1);
        p.setVelocity(p.getVelocity().setY(1.6));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
        p.setFallDistance(0);
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 20, 0.2, 0.1, 0.2, 0.05);
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
        if (uses <= 1) return Outcome.CONSUMED;
        ShopCatalogue.Item entry = entry(Custom.ROCKET_BOOTS);
        if (entry != null) decorate(item, entry, uses - 1);
        return Outcome.KEPT;
    }

    private Outcome recall(Player p) {
        GameTeam team = game.teamOf(p);
        if (team == null || home.apply(team) == null) { p.sendMessage(Text.msg("<red>Your bed is gone, so there is nowhere to recall to.")); return Outcome.FAILED; }
        if (recalls.containsKey(p.getUniqueId())) return Outcome.FAILED;
        int[] left = {RECALL_SECONDS};
        BukkitTask[] self = new BukkitTask[1];
        self[0] = game.runRepeating(0, 20, () -> {
            if (!p.isOnline() || !game.isAlive(p)) { cancelRecall(p, null); return; }
            if (left[0] > 0) {
                p.sendActionBar(Text.mm("<aqua>Recalling in " + left[0] + "... <gray>(getting hurt cancels it)"));
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.3);
                left[0]--;
                return;
            }
            self[0].cancel(); recalls.remove(p.getUniqueId());
            Location dest = home.apply(team);
            if (dest == null) { p.sendMessage(Text.msg("<red>Your bed was destroyed, so the recall fizzled.")); return; }
            if (!takeOne(p, Material.TOTEM_OF_UNDYING)) return;
            cooldowns.start(p.getUniqueId(), Custom.RECALL_TOTEM.id, cooldownOf(Custom.RECALL_TOTEM));
            p.teleport(dest);
            p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            p.sendMessage(Text.msg("<aqua>Home safe!"));
        });
        recalls.put(p.getUniqueId(), self[0]);
        return Outcome.KEPT;
    }

    private void cancelRecall(Player p, String message) {
        BukkitTask task = recalls.remove(p.getUniqueId());
        if (task == null) return;
        task.cancel();
        if (message != null) p.sendMessage(Text.msg("<yellow>" + message));
    }

    private Outcome honeyTrap(Player p, Block clicked) {
        if (clicked == null || !clicked.getRelative(BlockFace.UP).getType().isAir()) {
            p.sendMessage(Text.msg("<red>Right-click the ground where you want the honey."));
            return Outcome.FAILED;
        }
        GameTeam team = game.teamOf(p);
        int expires = game.gameTime() + secondsOf(Custom.HONEY_TRAP), made = 0;
        for (Pos cell : BedwarsKitShapes.honeyPatch(new Pos(clicked.getX(), clicked.getY(), clicked.getZ()))) {
            Block b = clicked.getWorld().getBlockAt(cell.x(), cell.y(), cell.z());
            Material t = b.getType();
            if (!t.isSolid() || t.getHardness() < 0 || b.getState() instanceof TileState || t.name().endsWith("_BED")
                    || !b.getRelative(BlockFace.UP).getType().isAir() || bedProtected.test(b)) continue;
            temp.place(b, Material.HONEY_BLOCK.createBlockData(), expires);
            made++;
        }
        if (made == 0) { p.sendMessage(Text.msg("<red>The honey will not stick there.")); return Outcome.FAILED; }
        patches.add(new Patch(clicked.getLocation().add(0.5, 1, 0.5), team == null ? -1 : team.index(), expires));
        p.playSound(clicked.getLocation(), Sound.BLOCK_HONEY_BLOCK_PLACE, 1f, 1f);
        return Outcome.CONSUMED;
    }

    private Outcome dreamDefender(Player p) {
        GameTeam team = game.teamOf(p);
        if (team == null) return Outcome.FAILED;
        for (Summon s : summons) if (s.kind == Custom.DREAM_DEFENDER && s.team == team.index()) {
            p.sendMessage(Text.msg("<red>Your team already has a Dream Defender.")); return Outcome.FAILED;
        }
        IronGolem golem = game.world().spawn(p.getLocation(), IronGolem.class, g -> {
            g.setPlayerCreated(true);
            g.setCustomName(team.palette().name() + " Defender");
            g.setCustomNameVisible(true);
            g.setRemoveWhenFarAway(false);
            var damage = g.getAttribute(Attribute.ATTACK_DAMAGE);
            if (damage != null) damage.setBaseValue(4); // a nudge, not a thump
        });
        summons.add(new Summon(golem, Custom.DREAM_DEFENDER, team.index(), game.gameTime() + secondsOf(Custom.DREAM_DEFENDER)));
        p.sendMessage(Text.msg("<gold>Dream Defender summoned! <gray>It guards your team for " + secondsOf(Custom.DREAM_DEFENDER) / 60 + " minutes."));
        return Outcome.CONSUMED;
    }

    private void bedBug(Player owner, Location at) {
        GameTeam team = owner == null ? null : game.teamOf(owner);
        if (team == null) return;
        Silverfish bug = game.world().spawn(at, Silverfish.class, s -> s.setRemoveWhenFarAway(false));
        summons.add(new Summon(bug, Custom.BED_BUG, team.index(), game.gameTime() + secondsOf(Custom.BED_BUG)));
    }

    private Outcome decoy(Player p) {
        GameTeam team = game.teamOf(p);
        if (team == null) return Outcome.FAILED;
        Vector ahead = p.getLocation().getDirection().setY(0);
        Location at = p.getLocation().add(ahead.lengthSquared() < 0.01 ? new Vector(0, 0, 0) : ahead.normalize().multiply(2));
        Color colour = Color.fromRGB(team.palette().color().value());
        ArmorStand stand = game.world().spawn(at, ArmorStand.class, a -> {
            a.setArms(true);
            a.setBasePlate(false);
            a.setCustomName(team.palette().name() + " Decoy");
            a.setCustomNameVisible(true);
            a.getEquipment().setHelmet(new ItemStack(team.palette().wool()));
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            chest.editMeta(LeatherArmorMeta.class, m -> m.setColor(colour));
            a.getEquipment().setChestplate(chest);
        });
        summons.add(new Summon(stand, Custom.DECOY_DUMMY, team.index(), game.gameTime() + secondsOf(Custom.DECOY_DUMMY)));
        p.playSound(at, Sound.ENTITY_ARMOR_STAND_PLACE, 1f, 1f);
        return Outcome.CONSUMED;
    }

    /** A cloud for five seconds: harmless particles, and Blindness I for enemies of the thrower within 3 blocks. */
    private void smoke(Player owner, Location at) {
        int[] pulses = {secondsOf(Custom.SMOKE_BOMB) * 4};
        BukkitTask[] self = new BukkitTask[1];
        self[0] = game.runRepeating(0, 5, () -> {
            if (pulses[0]-- <= 0 || game.world() == null) { self[0].cancel(); return; }
            at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 1, 0), 30, 1.2, 0.8, 1.2, 0.01);
            at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 1, 0), 12, 1.0, 0.6, 1.0, 0.01);
            for (Player p : game.alivePlayers()) {
                if (p.getWorld() != at.getWorld() || p.getLocation().distanceSquared(at) > 9) continue;
                if (owner == null || isEnemy(owner, p)) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
            }
        });
        tasks.add(self[0]);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1f, 0.8f);
    }

    // ---- helpers ---------------------------------------------------------------------

    private void hunt(Mob mob, int team) {
        GameTeam mine = teamIndexOf(team);
        Player best = null; double bestDistance = 16 * 16;
        for (Player p : game.alivePlayers()) {
            if (!game.isActive(p) || (mine != null && mine.contains(p)) || p.getWorld() != mob.getWorld()) continue;
            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (d < bestDistance) { best = p; bestDistance = d; }
        }
        mob.setTarget(best);
    }

    private boolean isEnemy(Player owner, Player other) {
        GameTeam team = game.teamOf(owner);
        return !owner.equals(other) && (team == null || !team.contains(other));
    }

    private GameTeam teamIndexOf(int index) {
        for (GameTeam t : game.teams()) if (t.index() == index) return t;
        return null;
    }

    private Summon summonOf(Entity entity) {
        for (Summon s : summons) if (s.entity.equals(entity)) return s;
        return null;
    }

    private void pop(Summon s) {
        summons.remove(s);
        vanish(s.entity);
    }

    private void vanish(Entity entity) {
        if (entity.isValid()) {
            entity.getWorld().spawnParticle(Particle.POOF, entity.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
            entity.remove();
        }
    }

    private void tellWait(Player p, Custom c) {
        long ms = cooldowns.remaining(p.getUniqueId(), c.id);
        if (ms > 0) p.sendActionBar(Text.mm("<yellow>Ready again in " + (ms + 999) / 1000 + "s"));
    }

    private static void consume(Player p, EquipmentSlot hand) {
        takeFrom(p, hand == EquipmentSlot.OFF_HAND ? 40 : p.getInventory().getHeldItemSlot());
    }

    private static boolean takeFrom(Player p, int slot) {
        ItemStack s = p.getInventory().getItem(slot);
        if (s == null || s.getType().isAir()) return false;
        if (s.getAmount() <= 1) p.getInventory().setItem(slot, null); else s.setAmount(s.getAmount() - 1);
        return true;
    }

    private static boolean takeOne(Player p, Material material) {
        for (int i = 0; i < 41; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s != null && s.getType() == material) return takeFrom(p, i);
        }
        return false;
    }

    private static BlockFace face(int dx, int dz) {
        return dx > 0 ? BlockFace.EAST : dx < 0 ? BlockFace.WEST : dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** Test hook: records an entity the way a real summon is, so cleanup can be checked without a server. */
    void adopt(Entity entity, Custom kind, int team, int expiresAt) { summons.add(new Summon(entity, kind, team, expiresAt)); }
}
