package uk.co.playerready.pulsegames.core.combat;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameInstance;

import java.lang.reflect.Method;
import java.util.Set;

/** Optional bridge to OCM's public service API; no OCM implementation is bundled. */
public final class ClassicCombatService implements Listener {
    interface Modesets {
        Set<String> names() throws ReflectiveOperationException;
        Set<String> allowed(World world) throws ReflectiveOperationException;
        String current(Player player) throws ReflectiveOperationException;
        void select(Player player, String name) throws ReflectiveOperationException;
    }
    private final PulseGamesPlugin plugin;
    private Modesets api;
    private boolean warned;
    private static final String CLASSIC = "pulse-classic";
    private static final String MODERN = "pulse-modern";

    public ClassicCombatService(PulseGamesPlugin plugin) { this.plugin = plugin; }
    ClassicCombatService(PulseGamesPlugin plugin, Modesets api) { this.plugin = plugin; this.api = api; }

    public void start() {
        if (!plugin.getConfig().getBoolean("combat.classic-pvp", false)) return;
        Plugin ocm = plugin.getServer().getPluginManager().getPlugin("OldCombatMechanics");
        if (ocm == null || !ocm.isEnabled()) { warn("OldCombatMechanics is missing; classic PvP is unavailable."); return; }
        if (!"2.7.0-ready.1".equals(ocm.getDescription().getVersion())) {
            warn("Classic PvP requires the tested OldCombatMechanics 2.7.0-ready.1 NPC-compatible candidate."); return;
        }
        try {
            Class<?> type = Class.forName("kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI", true, ocm.getClass().getClassLoader());
            Object provider = plugin.getServer().getServicesManager().load(type);
            if (provider == null) throw new IllegalStateException("OCM API service is unavailable");
            api = new ReflectiveModesets(type, provider);
            if (!api.names().containsAll(Set.of(CLASSIC, MODERN))) throw new IllegalStateException("Install the PulseGames OCM configuration first");
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getServer().getOnlinePlayers().forEach(this::refresh);
            plugin.getLogger().info("Classic PvP ready through OldCombatMechanics; non-PvP worlds use pulse-modern.");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            api = null; warn("Classic PvP integration unavailable: " + ex.getMessage());
        }
    }
    public void refresh(Player player) {
        if (api == null) return;
        GameInstance instance = plugin.instances().byPlayer(player);
        boolean classic = usesClassic(instance, player);
        if (classic && instance.isBot(player))
            player.setMetadata("pulsegames-classic-combat", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        select(player, classic ? CLASSIC : MODERN);
        if (!classic) player.removeMetadata("pulsegames-classic-combat", plugin);
    }
    static boolean usesClassic(GameInstance instance, Player player) {
        return instance != null && instance.world() != null && instance.world().equals(player.getWorld())
                && instance.isParticipant(player) && !instance.isSpectator(player) && instance.logic().pvp();
    }
    public void leave(Player player) {
        if (api != null) select(player, MODERN);
        player.removeMetadata("pulsegames-classic-combat", plugin);
    }
    private void select(Player player, String name) {
        try {
            if (!api.allowed(player.getWorld()).contains(name)) {
                warn("OCM does not allow " + name + " in " + player.getWorld().getName()); return;
            }
            if (!name.equals(api.current(player))) api.select(player, name);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warn("Could not apply combat mode: " + ex.getMessage());
        }
    }
    private void warn(String message) {
        if (!warned) { plugin.getLogger().warning(message); warned = true; }
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { later(event.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { later(event.getPlayer()); }
    private void later(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> { if (player.isOnline()) refresh(player); });
    }
    private static final class ReflectiveModesets implements Modesets {
        private final Object provider;
        private final Method names, allowed, current, select;
        ReflectiveModesets(Class<?> type, Object provider) throws ReflectiveOperationException {
            this.provider = provider;
            names = type.getMethod("getModesetNames");
            allowed = type.getMethod("getAllowedModesets", World.class);
            current = type.getMethod("getModesetForPlayer", Player.class);
            select = type.getMethod("setModesetForPlayer", Player.class, String.class);
        }
        @SuppressWarnings("unchecked") public Set<String> names() throws ReflectiveOperationException { return (Set<String>) names.invoke(provider); }
        @SuppressWarnings("unchecked") public Set<String> allowed(World world) throws ReflectiveOperationException { return (Set<String>) allowed.invoke(provider, world); }
        public String current(Player player) throws ReflectiveOperationException { return (String) current.invoke(provider, player); }
        public void select(Player player, String name) throws ReflectiveOperationException { select.invoke(provider, player, name); }
    }
}
