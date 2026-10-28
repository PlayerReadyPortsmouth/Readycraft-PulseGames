package uk.co.playerready.pulsegames.games.luckypillars;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/** Chaos-mode events for Lucky Pillars. Everything an event changes is undone through {@link Ctx#restoreAll()}. */
public enum LuckyChaos {
    LOW_GRAVITY("Low Gravity", "Everything feels floaty", 8, 12, false, 1, c -> c.allAttribute(Attribute.GRAVITY, 0.02)),
    MOON_JUMPS("Moon Jumps", "Jump boost, no brakes", 8, 15, false, 1,
            c -> c.allEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 15 * 20, 3))),
    LEVITATION("Everybody Up", "Going up, mind the landing", 6, 3, false, 1,
            c -> c.allEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 1))),
    SWAP("Swap Places", "Two players trade spots", 8, 0, false, 2, c -> {
        Player[] pair = pickPair(c.alive(), c.random);
        if (pair == null) return;
        Location a = pair[0].getLocation().clone();
        pair[0].teleport(pair[1].getLocation());
        pair[1].teleport(a);
        c.game.broadcast("<light_purple>" + pair[0].getName() + " and " + pair[1].getName() + " swapped places!");
    }),
    TNT_RAIN("TNT Rain", "Look up", 6, 10, true, 1, c -> c.repeat(10, () -> {
        List<Player> alive = c.alive();
        int strikes = Math.min(3, 1 + alive.size() / 4);
        for (int i = 0; i < strikes && !alive.isEmpty(); i++) {
            Location at = alive.get(c.random.nextInt(alive.size())).getLocation().add(
                    c.random.nextInt(7) - 3, 14, c.random.nextInt(7) - 3);
            TNTPrimed tnt = at.getWorld().spawn(at, TNTPrimed.class);
            tnt.setFuseTicks(60);
            c.track(tnt);
        }
    })),
    LAVA_RISING("Rising Lava", "The floor is getting hotter", 5, 20, true, 1, LuckyChaos::lavaRising),
    SHRINKING_BORDER("Shrinking Border", "Stay inside, stay alive", 5, 25, true, 1, LuckyChaos::shrinkBorder),
    SAME_ITEM("Group Gift", "Everybody gets the same item", 7, 0, false, 1, c -> {
        LuckyDrops.Drop drop = c.roller.get();
        if (drop != null) c.alive().forEach(p -> c.giver.accept(p, drop));
    }),
    ICE_FLOOR("Ice Floor", "Slippery when standing", 6, 12, false, 1, LuckyChaos::iceFloor),
    ITEM_FRENZY("Item Frenzy", "Four drops each, right now", 7, 0, false, 1, c -> {
        for (int i = 0; i < 4; i++) {
            c.game.runLater(i * 10L, () -> c.alive().forEach(p -> {
                LuckyDrops.Drop d = c.roller.get();
                if (d != null) c.giver.accept(p, d);
            }));
        }
    }),
    MOB_SWARM("Mob Swarm", "Something is crawling in", 5, 0, true, 1, c -> {
        int spawned = 0;
        for (Player p : c.alive()) {
            for (int i = 0; i < 3 && spawned < 30; i++, spawned++) {
                Location at = p.getLocation().add(c.random.nextInt(5) - 2, 1, c.random.nextInt(5) - 2);
                c.track(at.getWorld().spawnEntity(at, EntityType.SILVERFISH));
            }
        }
    }),
    WIND_BLAST("Wind Blast", "Hold on to something", 6, 0, true, 1, c -> c.alive().forEach(p -> {
        double angle = c.random.nextDouble() * Math.PI * 2;
        p.setVelocity(new Vector(Math.cos(angle) * 1.2, 0.5, Math.sin(angle) * 1.2));
        p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);
    })),
    TINY("Tiny Players", "Small but mighty", 5, 15, false, 1, c -> c.allAttribute(Attribute.SCALE, 0.4)),
    GIANT("Giant Players", "Big feet, big problems", 5, 15, false, 1, c -> c.allAttribute(Attribute.SCALE, 2.0)),
    DARKNESS("Lights Out", "Fog rolls in", 5, 8, false, 1,
            c -> c.allEffect(new PotionEffect(PotionEffectType.DARKNESS, 8 * 20, 0))),
    SPEED_ZOOM("Speed Zoom", "Gotta go fast", 6, 12, false, 1,
            c -> c.allEffect(new PotionEffect(PotionEffectType.SPEED, 12 * 20, 3))),
    HOTBAR_SHUFFLE("Hotbar Shuffle", "Your hotbar has opinions", 6, 0, false, 1, c -> c.alive().forEach(p -> {
        PlayerInventory inv = p.getInventory();
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) slots.add(inv.getItem(i));
        Collections.shuffle(slots, new java.util.Random(c.random.nextLong()));
        for (int i = 0; i < 9; i++) inv.setItem(i, slots.get(i));
    }));

    public static final int TELEGRAPH_SECONDS = 3;
    public static final int ROLL_EVERY_SECONDS = 15;
    public static final int FIRST_ROLL_SECONDS = 20;
    public static final int COOLDOWN_SECONDS = 20;
    public static final double ROLL_CHANCE = 0.5;

    final String title, subtitle;
    final int weight, seconds, minAlive;
    final boolean nasty;
    private final Consumer<Ctx> effect;

    LuckyChaos(String title, String subtitle, int weight, int seconds, boolean nasty, int minAlive, Consumer<Ctx> effect) {
        this.title = title; this.subtitle = subtitle; this.weight = weight;
        this.seconds = seconds; this.nasty = nasty; this.minAlive = minAlive; this.effect = effect;
    }

    public int weight() { return weight; }
    public int seconds() { return seconds; }
    public boolean nasty() { return nasty; }

    /** Two distinct random players, or null with fewer than two. */
    public static Player[] pickPair(List<Player> alive, RandomGenerator random) {
        if (alive.size() < 2) return null;
        int a = random.nextInt(alive.size());
        int b = random.nextInt(alive.size() - 1);
        if (b >= a) b++;
        return new Player[]{alive.get(a), alive.get(b)};
    }

    public static LuckyChaos pickEvent(int aliveCount, RandomGenerator random) {
        int total = 0;
        for (LuckyChaos e : values()) if (aliveCount >= e.minAlive) total += e.weight;
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (LuckyChaos e : values()) {
            if (aliveCount < e.minAlive) continue;
            roll -= e.weight;
            if (roll < 0) return e;
        }
        return null;
    }

    // ---- effects needing more than a line ----------------------------------

    private static void lavaRising(Ctx c) {
        List<Player> alive = c.alive();
        if (alive.isEmpty()) return;
        World world = c.game.world();
        int minY = alive.stream().mapToInt(p -> p.getLocation().getBlockY()).min().orElse(0);
        int cx = (int) alive.stream().mapToDouble(p -> p.getLocation().getX()).average().orElse(0);
        int cz = (int) alive.stream().mapToDouble(p -> p.getLocation().getZ()).average().orElse(0);
        int floor = Math.max((int) c.game.logic().voidY() + 1, world.getMinHeight());
        int start = Math.max(floor, minY - 8);
        int top = Math.min(Math.min(start + 10, minY + 1), world.getMaxHeight() - 1);
        int r = 18;
        int[] layer = {start};
        c.onRestore(() -> {
            for (int y = start - 1; y <= top + 1; y++)
                for (int x = cx - r - 2; x <= cx + r + 2; x++)
                    for (int z = cz - r - 2; z <= cz + r + 2; z++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (b.getType() == Material.LAVA) b.setType(Material.AIR, false);
                    }
        });
        c.repeat(40, () -> {
            if (layer[0] > top) return;
            for (int x = cx - r; x <= cx + r; x++)
                for (int z = cz - r; z <= cz + r; z++) {
                    Block b = world.getBlockAt(x, layer[0], z);
                    if (b.getType().isAir()) b.setType(Material.LAVA, false);
                }
            layer[0]++;
        });
    }

    private static void shrinkBorder(Ctx c) {
        List<Player> alive = c.alive();
        if (alive.isEmpty()) return;
        WorldBorder border = c.game.world().getWorldBorder();
        Location oldCenter = border.getCenter();
        double oldSize = border.getSize();
        c.onRestore(() -> { border.setSize(oldSize); border.setCenter(oldCenter); });
        // Arena maps have no border by default, so set one around the players first.
        double cx = alive.stream().mapToDouble(p -> p.getLocation().getX()).average().orElse(0);
        double cz = alive.stream().mapToDouble(p -> p.getLocation().getZ()).average().orElse(0);
        double spread = alive.stream().mapToDouble(p -> Math.max(Math.abs(p.getLocation().getX() - cx),
                Math.abs(p.getLocation().getZ() - cz))).max().orElse(0);
        border.setCenter(cx, cz);
        border.setSize(Math.max(40, spread * 2 + 20));
        border.setSize(Math.max(12, spread * 0.8), c.event.seconds);
    }

    private static void iceFloor(Ctx c) {
        for (Player p : c.alive()) {
            Block base = p.getLocation().subtract(0, 0.1, 0).getBlock();
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    Block b = base.getRelative(dx, 0, dz);
                    Material m = b.getType();
                    if (!m.isSolid() || m == Material.BLUE_ICE || m == Material.BEDROCK) continue;
                    BlockData old = b.getBlockData();
                    c.onRestore(() -> b.setBlockData(old, false));
                    b.setType(Material.BLUE_ICE, false);
                }
        }
    }

    // ---- per-event context --------------------------------------------------

    /** Everything one running event changes, so it can be undone in one call. */
    public static final class Ctx {
        final GameInstance game;
        final RandomGenerator random;
        final Supplier<LuckyDrops.Drop> roller;
        final BiConsumer<Player, LuckyDrops.Drop> giver;
        final LuckyChaos event;
        private final List<Entity> tracked;
        private final List<Runnable> restores = new ArrayList<>();
        /** Player-bound undo steps, so one player can be restored alone (quit) without waiting for the event to end. */
        private final java.util.Map<java.util.UUID, List<Runnable>> perPlayer = new java.util.HashMap<>();
        private final List<BukkitTask> tasks = new ArrayList<>();

        public Ctx(GameInstance game, RandomGenerator random, Supplier<LuckyDrops.Drop> roller,
                   BiConsumer<Player, LuckyDrops.Drop> giver, LuckyChaos event, List<Entity> tracked) {
            this.game = game; this.random = random; this.roller = roller;
            this.giver = giver; this.event = event; this.tracked = tracked;
        }

        public List<Player> alive() { return game.alivePlayers(); }
        void track(Entity e) { tracked.add(e); }
        void onRestore(Runnable r) { restores.add(r); }
        void repeat(long period, Runnable r) { tasks.add(game.runRepeating(0, period, r)); }

        /**
         * Sets an attribute to {@code value} with a transient modifier. Transient modifiers are never
         * written to playerdata, so a quit or crash mid-event cannot leave a changed base value behind.
         */
        public void setAttribute(Player p, Attribute attribute, double value) {
            AttributeInstance inst = p.getAttribute(attribute);
            if (inst == null) return;
            NamespacedKey key = modifierKey(game.plugin(), attribute);
            inst.removeModifier(key);
            inst.addTransientModifier(new AttributeModifier(key, modifierAmount(inst.getBaseValue(), value),
                    AttributeModifier.Operation.ADD_NUMBER));
            bind(p, () -> inst.removeModifier(key));
        }

        private void bind(Player p, Runnable undo) {
            restores.add(undo);
            perPlayer.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(undo);
        }

        /** Undo this event's changes for one player only (quit, kick, disconnect). */
        public void restoreFor(Player p) {
            List<Runnable> mine = perPlayer.remove(p.getUniqueId());
            if (mine == null) return;
            for (int i = mine.size() - 1; i >= 0; i--) {
                try { mine.get(i).run(); } catch (RuntimeException ignored) { /* already gone */ }
                restores.remove(mine.get(i));
            }
        }

        void allAttribute(Attribute attribute, double value) { alive().forEach(p -> setAttribute(p, attribute, value)); }

        void allEffect(PotionEffect effect) {
            for (Player p : alive()) {
                p.addPotionEffect(effect);
                bind(p, () -> p.removePotionEffect(effect.getType()));
            }
        }

        /** Undo in reverse order and cancel repeating tasks. Safe to call twice. */
        public void restoreAll() {
            tasks.forEach(t -> { if (!t.isCancelled()) t.cancel(); });
            tasks.clear();
            for (int i = restores.size() - 1; i >= 0; i--) {
                try { restores.get(i).run(); } catch (RuntimeException ignored) { /* player or world already gone */ }
            }
            restores.clear();
            perPlayer.clear();
        }
    }

    static NamespacedKey modifierKey(org.bukkit.plugin.Plugin plugin, Attribute attribute) {
        return new NamespacedKey(plugin, "lucky_chaos_" + attribute.getKey().getKey());
    }

    /** Amount that, added to {@code base}, gives exactly {@code target}. */
    static double modifierAmount(double base, double target) { return target - base; }

    // ---- scheduler ------------------------------------------------------------

    /** Own once-a-second timer: rolls, telegraphs, announces, ends and restores one event at a time. */
    public static final class Director {
        private final GameInstance game;
        private final RandomGenerator random;
        private final Supplier<LuckyDrops.Drop> roller;
        private final BiConsumer<Player, LuckyDrops.Drop> giver;
        private final List<Entity> tracked = new ArrayList<>();
        private int clock;
        private int nextRoll = FIRST_ROLL_SECONDS;
        private int lastEnd = -COOLDOWN_SECONDS;
        private LuckyChaos pending;
        private int fireAt;
        private LuckyChaos running;
        private Ctx ctx;
        private int endsAt;
        private BossBar bar;
        private boolean stopped;
        private BukkitTask task;

        public Director(GameInstance game, RandomGenerator random, Supplier<LuckyDrops.Drop> roller,
                        BiConsumer<Player, LuckyDrops.Drop> giver) {
            this.game = game; this.random = random; this.roller = roller; this.giver = giver;
        }

        public void start() { task = game.runRepeating(20L, 20L, this::tick); }
        public boolean busy() { return pending != null || running != null; }

        public void tick() {
            if (stopped) return;
            clock++;
            if (running != null) {
                if (bar != null) {
                    int left = Math.max(0, endsAt - clock);
                    bar.name(Text.mm("<light_purple><b>" + running.title + "</b> <gray>" + left + "s"));
                    bar.progress(Math.max(0f, Math.min(1f, left / (float) running.seconds)));
                }
                if (clock >= endsAt) finish();
            } else if (pending != null) {
                if (clock >= fireAt) { LuckyChaos e = pending; pending = null; begin(e); }
            } else if (clock >= nextRoll) {
                nextRoll = clock + ROLL_EVERY_SECONDS;
                if (clock - lastEnd < COOLDOWN_SECONDS || random.nextDouble() >= ROLL_CHANCE) return;
                LuckyChaos e = pickEvent(game.alivePlayers().size(), random);
                if (e == null) return;
                if (e.nasty) {
                    pending = e;
                    fireAt = clock + TELEGRAPH_SECONDS;
                    announce("<red><b>INCOMING</b>", "<yellow>" + e.title + " in " + TELEGRAPH_SECONDS + "s...",
                            "<red><b>WARNING:</b> " + e.title + " is coming!", Sound.ENTITY_ENDER_DRAGON_GROWL);
                } else begin(e);
            }
        }

        private void begin(LuckyChaos e) {
            if (game.alivePlayers().size() < e.minAlive) { lastEnd = clock; return; }
            announce("<light_purple><b>" + e.title.toUpperCase(), "<gray>" + e.subtitle,
                    "<light_purple><b>CHAOS:</b> " + e.title + " - " + e.subtitle, Sound.BLOCK_BEACON_ACTIVATE);
            ctx = new Ctx(game, random, roller, giver, e, tracked);
            try { e.effect.accept(ctx); }
            catch (RuntimeException ex) { ctx.restoreAll(); ctx = null; lastEnd = clock; return; }
            if (e.seconds > 0) {
                running = e;
                endsAt = clock + e.seconds;
                bar = BossBar.bossBar(Text.mm("<light_purple><b>" + e.title), 1f,
                        BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
                humans().forEach(p -> p.showBossBar(bar));
            } else {
                ctx.restoreAll();
                ctx = null;
                lastEnd = clock;
            }
        }

        private void finish() {
            if (ctx != null) ctx.restoreAll();
            ctx = null;
            running = null;
            hideBar();
            lastEnd = clock;
        }

        /** Game over or cleanup: undo everything, stop the timer, remove summoned mobs. */
        public void shutdown() {
            stopped = true;
            pending = null;
            if (task != null && !task.isCancelled()) task.cancel();
            finish();
            tracked.forEach(e -> { if (e.isValid()) e.remove(); });
            tracked.clear();
        }

        public boolean stopped() { return stopped; }

        /** A player left mid-event: put their attributes and effects back now. */
        public void restoreFor(Player p) { if (ctx != null) ctx.restoreFor(p); }

        private void hideBar() {
            if (bar != null) {
                BossBar b = bar;
                humans().forEach(p -> p.hideBossBar(b));
                bar = null;
            }
        }

        private List<Player> humans() { return game.everyone().stream().filter(p -> !game.isBot(p)).toList(); }

        private void announce(String title, String subtitle, String chat, Sound sound) {
            game.broadcast(chat);
            for (Player p : humans()) {
                Text.title(p, title, subtitle);
                p.playSound(p.getLocation(), sound, 0.6f, 1f);
            }
        }
    }
}
