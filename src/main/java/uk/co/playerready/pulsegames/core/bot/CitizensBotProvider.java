package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Citizens is optional: only its public API is resolved, once, through its plugin loader. */
public final class CitizensBotProvider implements BotProvider {
    private final Object registry;
    private final Api api;
    private final Set<Object> owned = new HashSet<>();
    private boolean closed;

    private CitizensBotProvider(Object registry, Api api) {
        this.registry = registry;
        this.api = api;
    }

    public static Optional<BotProvider> connect(Plugin owner) {
        Plugin citizens = owner.getServer().getPluginManager().getPlugin("Citizens");
        if (citizens == null || !citizens.isEnabled()) {
            owner.getLogger().warning("Queue bots unavailable: Citizens is not enabled.");
            return Optional.empty();
        }
        try {
            ClassLoader loader = citizens.getClass().getClassLoader();
            Api api = new Api(loader);
            Class<?> store = loader.loadClass("net.citizensnpcs.api.npc.NPCDataStore");
            Object memory = loader.loadClass("net.citizensnpcs.api.npc.MemoryNPCDataStore")
                    .getConstructor().newInstance();
            Object registry = call(loader.loadClass("net.citizensnpcs.api.CitizensAPI")
                    .getMethod("createAnonymousNPCRegistry", store), null, memory);
            return Optional.of(new CitizensBotProvider(Objects.requireNonNull(registry), api));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            owner.getLogger().warning("Queue bots unavailable: incompatible Citizens API ("
                    + failure.getClass().getSimpleName() + ").");
            return Optional.empty();
        }
    }

    @Override public BotActor create(String name, Location location) {
        if (closed) throw new IllegalStateException("Bot provider is closed");
        Objects.requireNonNull(location.getWorld(), "Bot spawn world");
        Object npc = call(api.create, registry, EntityType.PLAYER, botName(name));
        owned.add(npc);
        try {
            Object data = call(api.data, npc);
            // Keep bots out of online-player accounting and persistent Citizens data.
            metadata(data, "REMOVE_FROM_PLAYERLIST", true);
            metadata(data, "REMOVE_FROM_TABLIST", true);
            metadata(data, "SHOULD_SAVE", false);
            metadata(data, "DEFAULT_PROTECTED", false);
            metadata(data, "DROPS_ITEMS", false);
            metadata(data, "RESPAWN_DELAY", -1);
            metadata(data, "DISABLE_DEFAULT_STUCK_ACTION", true);
            metadata(data, "NAMEPLATE_VISIBLE", true);
            if (!Boolean.TRUE.equals(call(api.spawn, npc, location.clone())))
                throw new IllegalStateException("Citizens refused bot spawn");
            Object entity = call(api.entity, npc);
            if (!(entity instanceof Player player))
                throw new IllegalStateException("Citizens did not spawn a player NPC");
            Object navigator = call(api.navigator, npc);
            Object parameters = call(api.parameters, navigator);
            // One-block safe tiles need an actual arrival, not Citizens' broad default margin.
            call(api.distanceMargin, parameters, 0.15);
            call(api.pathDistanceMargin, parameters, 0.0);
            call(api.useNewPathfinder, parameters, true);
            call(api.teleportMargin, parameters, -1.0);
            Actor actor = new Actor(npc, navigator, player);
            return actor;
        } catch (RuntimeException | LinkageError failure) {
            try { remove(npc); }
            catch (RuntimeException | LinkageError cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    static String botName(String name) {
        String clean = Objects.requireNonNull(name).replaceAll("[^A-Za-z0-9_]", "");
        if (clean.startsWith("Bot_")) clean = clean.substring(4);
        if (clean.isEmpty()) clean = "Player";
        return "Bot_" + clean.substring(0, Math.min(12, clean.length()));
    }

    private void metadata(Object data, String key, Object value) { call(api.set, data, api.metadataKeys.get(key), value); }

    @Override public void close() {
        closed = true;
        RuntimeException failure = null;
        for (Object npc : Set.copyOf(owned)) {
            try { remove(npc); }
            catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }

    private void remove(Object npc) {
        call(api.destroy, npc);
        owned.remove(npc);
    }

    private final class Actor implements BotActor {
        private final Object npc;
        private final Object navigator;
        private Player player;
        private boolean destroyed;

        private Actor(Object npc, Object navigator, Player player) {
            this.npc = npc;
            this.navigator = navigator;
            this.player = player;
        }
        @Override public Player player() {
            // Citizens can replace the Bukkit entity after an asynchronous skin respawn.
            // The contestant UUID survives; retaining the removed entity falsely ends the match.
            Object current = call(api.entity, npc);
            if (current instanceof Player replacement) player = replacement;
            return player;
        }
        @Override public boolean valid() {
            return !destroyed && owned.contains(npc) && player().isValid() && !player().isDead();
        }
        @Override public void navigate(Location destination) {
            if (valid()) call(api.target, navigator, destination.clone());
        }
        @Override public void stopNavigation() {
            if (!destroyed && owned.contains(npc)) call(api.cancel, navigator);
        }
        @Override public void face(Location destination) {
            if (valid()) call(api.face, npc, destination.clone());
        }
        @Override public void destroy() {
            if (destroyed) return;
            // Keep failed removals owned so close() can retry them.
            if (owned.contains(npc)) remove(npc);
            destroyed = true;
        }
    }

    private static Object call(Method method, Object receiver, Object... arguments) {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException ex) {
            throw new IllegalStateException("Citizens " + method.getName() + " failed", ex.getCause());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Citizens " + method.getName() + " unavailable", ex);
        }
    }

    private static final class Api {
        final Method create, data, set, spawn, entity, navigator, destroy, target, cancel, face, parameters, distanceMargin, pathDistanceMargin, teleportMargin, useNewPathfinder;
        final java.util.Map<String, Object> metadataKeys = new java.util.HashMap<>();
        Api(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> npc = loader.loadClass("net.citizensnpcs.api.npc.NPC");
            Class<?> nav = loader.loadClass("net.citizensnpcs.api.ai.Navigator");
            create = loader.loadClass("net.citizensnpcs.api.npc.NPCRegistry")
                    .getMethod("createNPC", EntityType.class, String.class);
            data = npc.getMethod("data");
            Class<?> metadata = loader.loadClass("net.citizensnpcs.api.npc.NPC$Metadata");
            for (String key : java.util.List.of("REMOVE_FROM_PLAYERLIST", "REMOVE_FROM_TABLIST",
                    "SHOULD_SAVE", "DEFAULT_PROTECTED", "DROPS_ITEMS", "RESPAWN_DELAY",
                    "DISABLE_DEFAULT_STUCK_ACTION", "NAMEPLATE_VISIBLE")) {
                metadataKeys.put(key, metadata.getField(key).get(null));
            }
            // Modern Citizens stores enum settings separately from arbitrary string metadata.
            set = loader.loadClass("net.citizensnpcs.api.npc.MetadataStore")
                    .getMethod("set", metadata, Object.class);
            spawn = npc.getMethod("spawn", Location.class);
            entity = npc.getMethod("getEntity");
            navigator = npc.getMethod("getNavigator");
            destroy = npc.getMethod("destroy");
            face = npc.getMethod("faceLocation", Location.class);
            parameters = nav.getMethod("getDefaultParameters");
            Class<?> params = loader.loadClass("net.citizensnpcs.api.ai.NavigatorParameters");
            useNewPathfinder = params.getMethod("useNewPathfinder", boolean.class);
            distanceMargin = params.getMethod("distanceMargin", double.class);
            pathDistanceMargin = params.getMethod("pathDistanceMargin", double.class);
            teleportMargin = params.getMethod("destinationTeleportMargin", double.class);
            target = nav.getMethod("setTarget", Location.class);
            cancel = nav.getMethod("cancelNavigation");
        }
    }
}
