package uk.co.playerready.pulsegames.core.content;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import uk.co.playerready.pulsegames.PulseGamesPlugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Optional versioned PulseContent API. No renderer, pack or encounter implementation lives here. */
public final class PulseContentBridge implements AutoCloseable {
    private final PulseGamesPlugin plugin;
    private final Set<OwnedVisual> handles = new HashSet<>();
    private Api api;

    public PulseContentBridge(PulseGamesPlugin plugin) { this.plugin = plugin; }

    /** Optional wardrobe capability is independent of prop APIs and must never break a match. */
    public void wardrobeContext(Player player, String game, String phase) {
        wardrobeCall("wardrobeContext", new Class<?>[]{Plugin.class, Player.class, String.class, String.class},
                plugin, player, game, phase);
    }

    public void clearWardrobeContext(Player player) {
        wardrobeCall("clearWardrobeContext", new Class<?>[]{Plugin.class, Player.class}, plugin, player);
    }

    public void wardrobeCelebration(Player player, String category) {
        if (!Set.of("finish_event", "win_animation").contains(category)) return;
        wardrobeCall("wardrobeCelebration", new Class<?>[]{Plugin.class, Player.class, String.class},
                plugin, player, category);
    }

    private void wardrobeCall(String method, Class<?>[] parameters, Object... arguments) {
        try {
            Plugin provider = plugin.getServer().getPluginManager().getPlugin("PulseContent");
            if (provider == null || !provider.isEnabled()) return;
            Class<?> type = provider.getClass();
            if (!Integer.valueOf(1).equals(type.getMethod("wardrobeApiVersion").invoke(provider))) return;
            type.getMethod(method, parameters).invoke(provider, arguments);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Old, disabled, or failed cosmetic providers cannot interrupt match or inventory cleanup.
        }
    }

    /** A user-facing reason, or null when every participant and configured prop is ready. */
    public String startProblem(List<String> props, List<Player> viewers) {
        if (props.isEmpty()) return "Custom Prop Hunt has no configured PulseContent props.";
        if (viewers.isEmpty()) return "Custom Prop Hunt requires human players.";
        try {
            Api current = api();
            for (String prop : props) {
                if (prop == null || prop.isBlank() || !(boolean) current.hasVisual.invoke(current.provider, prop))
                    return "A configured Prop Hunt model is unavailable in PulseContent.";
            }
            for (Player viewer : viewers) {
                if (!plugin.canUseNewFeatures(viewer)) return "Custom Prop Hunt is limited to the current trial players.";
                if (!(boolean) current.ready.invoke(current.provider, viewer))
                    return "Every player must finish loading the active PulseContent pack before custom Prop Hunt can start.";
            }
            return null;
        } catch (ReflectiveOperationException | IllegalStateException e) {
            return "Custom Prop Hunt needs the compatible PulseContent minigames API and enabled content.";
        }
    }

    public AutoCloseable attach(String id, Player subject, Collection<UUID> viewers) {
        if (viewers.isEmpty()) throw new IllegalArgumentException("A visual requires human viewers");
        try {
            Api current = api();
            Object result = current.attach.invoke(current.provider, plugin, id, subject, List.copyOf(viewers));
            if (!(result instanceof AutoCloseable visual)) throw new IllegalStateException("PulseContent returned no visual handle");
            OwnedVisual owned = new OwnedVisual(visual);
            handles.add(owned);
            return owned;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("PulseContent could not attach the prop", e); }
    }

    public boolean isActive(Player subject) {
        try { Api current = api(); return (boolean) current.active.invoke(current.provider, subject); }
        catch (ReflectiveOperationException | IllegalStateException e) { return false; }
    }

    public boolean blockPropsReady() {
        try {
            Api current = api();
            return Boolean.TRUE.equals(current.blocks().ready.invoke(current.provider));
        } catch (ReflectiveOperationException | IllegalStateException e) { return false; }
    }

    public boolean isBlockPropActive(Player subject) {
        try { Api current = api(); return Boolean.TRUE.equals(current.blocks().active.invoke(current.provider, subject)); }
        catch (ReflectiveOperationException | IllegalStateException e) { return false; }
    }

    public AutoCloseable attachBlockProp(Player subject, org.bukkit.Material material,
                                         org.bukkit.Location position, Collection<UUID> viewers) {
        try {
            Api current = api();
            Object result = current.blocks().attach.invoke(current.provider, plugin, subject, material, position, List.copyOf(viewers));
            if (!(result instanceof AutoCloseable visual)) throw new IllegalStateException("No block prop handle returned");
            OwnedVisual owned = new OwnedVisual(visual);
            handles.add(owned);
            return owned;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot lock prop here", e); }
    }

    public boolean propVisualsReady() {
        try { api().props(); return true; }
        catch (ReflectiveOperationException | IllegalStateException e) { return false; }
    }
    public PropVisual createProp(String model, Player participant, org.bukkit.Location position, Collection<UUID> viewers, float scale) {
        try {
            Api current = api();
            Object result = current.props().create.invoke(current.provider, plugin, model, participant, position, List.copyOf(viewers), scale);
            if (!(result instanceof AutoCloseable visual)) throw new IllegalStateException("Missing prop visual handle");
            OwnedVisual owned = new OwnedVisual(visual); handles.add(owned);
            return new PropVisual(current, owned);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot create prop visual", e); }
    }
    public final class PropVisual implements AutoCloseable {
        private final Api provider;
        private final OwnedVisual owned;
        private PropVisual(Api provider, OwnedVisual owned) { this.provider = provider; this.owned = owned; }
        public boolean active() {
            try { return !owned.closed && api() == provider && Boolean.TRUE.equals(provider.props().active.invoke(provider.provider, owned.visual)); }
            catch (ReflectiveOperationException | IllegalStateException e) { return false; }
        }
        public void move(org.bukkit.Location position) {
            try {
                if (!active()) throw new IllegalStateException("Prop visual is inactive");
                provider.props().move.invoke(provider.provider, plugin, owned.visual, position);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot move prop visual", e); }
        }
        @Override public void close() throws Exception { owned.close(); }
    }

    private Api api() throws ReflectiveOperationException {
        Plugin provider = plugin.getServer().getPluginManager().getPlugin("PulseContent");
        if (provider == null || !provider.isEnabled()) throw new IllegalStateException("PulseContent unavailable");
        if (api == null || api.provider != provider) api = new Api(provider);
        return api;
    }

    @Override public void close() {
        RuntimeException failure = null;
        for (OwnedVisual handle : Set.copyOf(handles)) {
            try { handle.close(); }
            catch (Exception e) { if (failure == null) failure = new IllegalStateException("Failed to remove custom props", e); else failure.addSuppressed(e); }
        }
        if (failure != null) throw failure;
    }

    private final class OwnedVisual implements AutoCloseable {
        private final AutoCloseable visual;
        private boolean closed;
        OwnedVisual(AutoCloseable visual) { this.visual = visual; }
        @Override public void close() throws Exception {
            if (closed) return;
            visual.close();
            closed = true;
            handles.remove(this);
        }
    }

    private static final class Api {
        final Plugin provider;
        final Method ready, hasVisual, attach, active;
        private BlockApi blockApi;
        private PropApi propApi;
        PropApi props() throws ReflectiveOperationException {
            if (propApi == null) propApi = new PropApi(provider);
            return propApi;
        }
        BlockApi blocks() throws ReflectiveOperationException {
            if (blockApi == null) blockApi = new BlockApi(provider);
            return blockApi;
        }
        Api(Plugin provider) throws ReflectiveOperationException {
            this.provider = provider;
            Class<?> type = provider.getClass();
            if (!Integer.valueOf(1).equals(type.getMethod("minigamesApiVersion").invoke(provider)))
                throw new IllegalStateException("Unsupported PulseContent API");
            ready = type.getMethod("isContentReady", Player.class);
            hasVisual = type.getMethod("hasVisual", String.class);
            active = type.getMethod("isVisualActive", Player.class);
            attach = type.getMethod("attachVisual", Plugin.class, String.class, Player.class, Collection.class);
        }
    }
    private static final class PropApi {
        final Method create, move, active;
        PropApi(Plugin provider) throws ReflectiveOperationException {
            Class<?> type = provider.getClass();
            if (!Integer.valueOf(1).equals(type.getMethod("propVisualsApiVersion").invoke(provider)))
                throw new IllegalStateException("Unsupported PulseContent prop visual API");
            create = type.getMethod("createPropVisual", Plugin.class, String.class, Player.class, org.bukkit.Location.class, Collection.class, float.class);
            move = type.getMethod("movePropVisual", Plugin.class, AutoCloseable.class, org.bukkit.Location.class);
            active = type.getMethod("isPropVisualActive", AutoCloseable.class);
        }
    }
    private static final class BlockApi {
        final Method ready, active, attach;
        BlockApi(Plugin provider) throws ReflectiveOperationException {
            Class<?> type = provider.getClass();
            if (!Integer.valueOf(1).equals(type.getMethod("blockPropsApiVersion").invoke(provider)))
                throw new IllegalStateException("Unsupported PulseContent block-prop API");
            ready = type.getMethod("blockPropsReady");
            active = type.getMethod("isBlockPropActive", Player.class);
            attach = type.getMethod("attachBlockProp", Plugin.class, Player.class,
                    org.bukkit.Material.class, org.bukkit.Location.class, Collection.class);
        }
    }

}
