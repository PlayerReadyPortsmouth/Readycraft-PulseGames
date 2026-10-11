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
