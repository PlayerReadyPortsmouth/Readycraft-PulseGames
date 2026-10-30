package uk.co.playerready.pumpkinhunt;

import uk.co.playerready.pumpkinhunt.logic.ClickCooldown;
import uk.co.playerready.pumpkinhunt.logic.Lantern;
import uk.co.playerready.pumpkinhunt.logic.PumpkinProgress;
import uk.co.playerready.pumpkinhunt.logic.PumpkinProgress.Result;
import uk.co.playerready.pumpkinhunt.logic.SeasonWindow;
import java.io.File;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Halloween spawn pumpkin hunt. Registered jack-o'-lanterns are found by right-click; progress is per player
 * and the last find fires the one-off completion. Nothing here moves, breaks or places a block.
 *
 * <p>The listener runs at MONITOR and does not ignore cancelled events: the spawn region denies {@code use}, which
 * WorldGuard applies by denying the interaction, and a denied right-click on a lantern must still count. Counting
 * is harmless because a click only reads the block.
 */
public final class PumpkinHuntManager implements Listener {
    private static final long COOLDOWN_MS = 1000L;

    private final JavaPlugin plugin;
    private final PumpkinProgress progress = new PumpkinProgress();
    private final ProgressStore store;
    private final Messages messages;
    private final ClickCooldown cooldown = new ClickCooldown();
    private final NamespacedKey fireworkKey;
    private volatile Map<String, Lantern> byPosition = Map.of();
    private volatile List<Lantern> lanterns = List.of();

    public PumpkinHuntManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.store = new ProgressStore(new File(plugin.getDataFolder(), "progress.json").toPath(), plugin.getLogger(), 5000L, progress::toJson);
        this.messages = new Messages(plugin);
        this.fireworkKey = new NamespacedKey(plugin, "pumpkin_firework");
    }

    // ---------- lifecycle ----------

    public void start() {
        messages.load();
        loadProgress();
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        var cmd = plugin.getCommand("pumpkins");
        if (cmd != null) {
            PumpkinCommand exec = new PumpkinCommand(this);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        } else {
            plugin.getLogger().warning("[PumpkinHunt] /pumpkins command missing from plugin.yml");
        }
    }

    /** Disable: write anything pending now. Call before storage closes. */
    public void stop() { store.flush(); }

    public Messages messages() { return messages; }

    private void loadProgress() {
        try {
            progress.loadJson(store.read());
            plugin.getLogger().info("[PumpkinHunt] Loaded progress for " + progress.players() + " player(s)");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[PumpkinHunt] Could not read progress.json; starting empty", e);
        }
    }

    /** Re-reads config.yml and messages.yml, then the lantern list. */
    public void reloadAll() {
        plugin.reloadConfig();
        messages.load();
        reload();
    }

    /** Re-reads the lantern list from config and checks each position holds a jack-o'-lantern. */
    public void reload() {
        List<Lantern> list = new ArrayList<>();
        Map<String, Lantern> index = new LinkedHashMap<>();
        for (Map<?, ?> m : plugin.getConfig().getMapList("lanterns")) {
            try {
                Lantern l = new Lantern(String.valueOf(m.get("id")), String.valueOf(m.get("world")),
                        num(m.get("x")), num(m.get("y")), num(m.get("z")),
                        m.get("hint") == null ? "" : String.valueOf(m.get("hint")));
                if (index.putIfAbsent(l.key(), l) == null && list.stream().noneMatch(o -> o.id().equals(l.id()))) list.add(l);
                else plugin.getLogger().warning("[PumpkinHunt] Duplicate lantern id or position skipped: " + l.id());
            } catch (RuntimeException bad) {
                plugin.getLogger().warning("[PumpkinHunt] Skipping unreadable lantern entry: " + m);
            }
        }
        lanterns = List.copyOf(list);
        Map<String, Lantern> byPos = new LinkedHashMap<>();
        for (Lantern l : list) byPos.put(l.key(), l);
        byPosition = Map.copyOf(byPos);
        for (String bad : List.of("start", "end")) {
            String v = plugin.getConfig().getString(bad, "");
            if (SeasonWindow.unreadable(v, zone())) plugin.getLogger().warning("[PumpkinHunt] " + bad + " is not an ISO date, ignoring: " + v);
        }
        validate();
    }

    /** Warns in the console for any registered position that no longer holds a jack-o'-lantern. */
    private void validate() {
        for (Lantern l : lanterns) {
            World w = Bukkit.getWorld(l.world());
            if (w == null) {
                plugin.getLogger().warning("[PumpkinHunt] Lantern " + l.id() + ": world " + l.world() + " is not loaded");
                continue;
            }
            w.getChunkAtAsync(l.x() >> 4, l.z() >> 4).thenAccept(chunk -> {
                if (w.getBlockAt(l.x(), l.y(), l.z()).getType() != Material.JACK_O_LANTERN) {
                    plugin.getLogger().warning("[PumpkinHunt] Lantern " + l.id() + " at " + coords(l) + " is not a jack-o'-lantern (skipped until it is)");
                }
            });
        }
    }

    // ---------- state used by the command ----------

    public boolean enabled() { return plugin.getConfig().getBoolean("enabled", false); }

    public SeasonWindow.State seasonState() { return window().state(System.currentTimeMillis()); }

    private SeasonWindow window() {
        return new SeasonWindow(enabled(), plugin.getConfig().getString("start", ""),
                plugin.getConfig().getString("end", ""), zone());
    }

    private ZoneId zone() {
        try { return ZoneId.of(plugin.getConfig().getString("timezone", "Europe/London")); }
        catch (RuntimeException e) { return ZoneId.of("Europe/London"); }
    }

    public PumpkinProgress progress() { return progress; }

    public List<Lantern> lanterns() { return lanterns; }

    public Set<String> ids() {
        Set<String> out = new LinkedHashSet<>();
        for (Lantern l : lanterns) out.add(l.id());
        return out;
    }

    public void saveProgress() { store.save(); }

    /** Adds (or replaces by id) a lantern and writes the list back to config.yml. */
    public void addLantern(Lantern l) {
        List<Lantern> next = new ArrayList<>(lanterns);
        next.removeIf(o -> o.id().equals(l.id()) || o.key().equals(l.key()));
        next.add(l);
        writeLanterns(next);
    }

    public boolean removeLantern(String id) {
        List<Lantern> next = new ArrayList<>(lanterns);
        if (!next.removeIf(o -> o.id().equalsIgnoreCase(id))) return false;
        writeLanterns(next);
        return true;
    }

    private void writeLanterns(List<Lantern> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Lantern l : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.id());
            m.put("world", l.world());
            m.put("x", l.x());
            m.put("y", l.y());
            m.put("z", l.z());
            m.put("hint", l.hint());
            out.add(m);
        }
        plugin.getConfig().set("lanterns", out);
        plugin.saveConfig();
        reload();
    }

    public static String coords(Lantern l) { return l.x() + " " + l.y() + " " + l.z(); }

    private static int num(Object o) {
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(o).trim());
    }

    // ---------- finding ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.JACK_O_LANTERN) return;
        Lantern lantern = byPosition.get(Lantern.key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
        if (lantern == null || !seasonState().equals(SeasonWindow.State.OPEN)) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("pumpkinhunt.play")) return;
        long now = System.currentTimeMillis();
        if (!cooldown.tryAcquire(player.getUniqueId(), lantern.id(), now, COOLDOWN_MS)) return;

        int total = lanterns.size();
        Result r = progress.record(player.getUniqueId(), player.getName(), lantern.id(), ids(), now);
        switch (r.outcome()) {
            case ALREADY -> player.sendActionBar(Component.text(messages.get("already", "n", r.found(), "total", total), NamedTextColor.GOLD));
            case ALREADY_COMPLETE -> player.sendActionBar(Component.text(messages.get("done_already", "total", total), NamedTextColor.GOLD));
            case NEW, COMPLETED -> {
                saveProgress();
                celebrateFind(player, block);
                player.sendActionBar(Component.text(messages.get("found", "n", r.found(), "total", total), NamedTextColor.GOLD));
                if (r.outcome() == PumpkinProgress.Outcome.COMPLETED) complete(player, total);
            }
        }
    }

    /** Sound and flames go to the finder only, so nobody else is bothered. */
    private void celebrateFind(Player player, Block block) {
        Location at = block.getLocation().add(0.5, 0.6, 0.5);
        player.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        player.spawnParticle(Particle.FLAME, at, 20, 0.25, 0.25, 0.25, 0.02);
    }

    private void complete(Player player, int total) {
        player.showTitle(Title.title(
                Component.text(messages.get("done_title", "total", total), NamedTextColor.GOLD),
                Component.text(messages.get("done_subtitle"), NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1))));
        launchFirework(player.getLocation());
        for (String cmd : plugin.getConfig().getStringList("reward-commands")) {
            if (cmd == null || cmd.isBlank()) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName()));
        }
        if (plugin.getConfig().getBoolean("broadcast-completion", true)) {
            Bukkit.broadcast(Component.text(messages.get("broadcast", "name", player.getName(), "total", total), NamedTextColor.GOLD));
        }
        plugin.getLogger().info("[PumpkinHunt] " + player.getName() + " found all " + total + " pumpkins");
    }

    /** A tagged firework that explodes straight away; its damage is cancelled below. */
    private void launchFirework(Location at) {
        Location spot = at.clone().add(0, 1, 0);
        spot.getWorld().spawn(spot, Firework.class, fw -> {
            fw.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.ORANGE, Color.PURPLE).withFade(Color.YELLOW).withFlicker().build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
            fw.setTicksToDetonate(5);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework fw
                && fw.getPersistentDataContainer().has(fireworkKey, PersistentDataType.BYTE)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { cooldown.forget(event.getPlayer().getUniqueId()); }
}
