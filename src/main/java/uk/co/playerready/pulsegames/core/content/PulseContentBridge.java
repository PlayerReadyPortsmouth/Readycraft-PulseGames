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

    @SuppressWarnings("unchecked")
    private <T> java.util.concurrent.CompletionStage<T> progressionCall(String method,Class<?>[] types,Object... args) {
        try {
            Plugin provider=plugin.getServer().getPluginManager().getPlugin("PulseContent");
            if(provider==null || !provider.isEnabled() || !Integer.valueOf(1).equals(provider.getClass().getMethod("matchProgressionApiVersion").invoke(provider)))
                throw new IllegalStateException("Progression provider unavailable");
            Object result=provider.getClass().getMethod(method,types).invoke(provider,args);
            if(!(result instanceof java.util.concurrent.CompletionStage<?> stage))throw new IllegalStateException("Missing progression acknowledgement");
            return (java.util.concurrent.CompletionStage<T>)stage;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){return java.util.concurrent.CompletableFuture.failedFuture(failure);}
    }
    public java.util.concurrent.CompletionStage<Boolean> recordCompetitiveMatch(uk.co.playerready.pulsegames.core.progression.ProgressionOutbox.Result row) {
        return progressionCall("recordCompetitiveMatch",new Class<?>[]{Plugin.class,UUID.class,UUID.class,String.class,boolean.class,int.class,String.class},
                plugin,row.player(),row.match(),row.game(),row.won(),row.kills(),row.event());
    }
    public java.util.concurrent.CompletionStage<List<String>> matchProgression(UUID player) {
        return progressionCall("matchProgression",new Class<?>[]{Plugin.class,UUID.class},plugin,player);
    }

    /** Encounter admission uses the existing explicit model allowlist and each human pack session. */
    public String mobReadiness(Collection<Player> viewers, Collection<String> models) {
        if (models.isEmpty()) return null;
        try {
            Plugin provider = plugin.getServer().getPluginManager().getPlugin("PulseContent");
            if (provider == null || !provider.isEnabled()) return "Custom zombies need enabled PulseContent.";
            Method has = provider.getClass().getMethod("hasVisual", String.class);
            Method ready = provider.getClass().getMethod("isContentReady", Player.class);
            provider.getClass().getMethod("spawnEntity", String.class, org.bukkit.Location.class);
            for (String id : models) {
                if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                        || !Boolean.TRUE.equals(has.invoke(provider, id)))
                    return "A configured zombie model is missing or not allowed in PulseContent.";
            }
            for (Player viewer : viewers) {
                if (viewer.hasMetadata("NPC")) continue;
                if (!Boolean.TRUE.equals(ready.invoke(provider, viewer)))
                    return "Every player must finish loading the PulseContent pack before custom zombies can start.";
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return "Custom zombies require the compatible PulseContent encounter API.";
        }
    }

    /** Returned mob belongs to the match: the caller removes it on every cleanup path. */
    public org.bukkit.entity.LivingEntity spawnMob(String id, org.bukkit.Location location, Collection<Player> viewers) {
        String problem = mobReadiness(viewers, List.of(id));
        if (problem != null) throw new IllegalStateException(problem);
        if (location == null || location.getWorld() == null) throw new IllegalArgumentException("Missing encounter world");
        try {
            Plugin provider = plugin.getServer().getPluginManager().getPlugin("PulseContent");
            // hasVisual above checked the exact namespace/catalogue pair. The encounter API expects its local ID.
            Object spawned = provider.getClass().getMethod("spawnEntity", String.class, org.bukkit.Location.class)
                    .invoke(provider, id.substring(id.indexOf(':') + 1), location);
            if (spawned instanceof org.bukkit.entity.Mob mob && mob.getWorld().equals(location.getWorld())) return mob;
            if (spawned instanceof org.bukkit.entity.Entity entity) entity.remove();
            throw new IllegalStateException("Configured zombie model did not spawn a mob in the match world");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("PulseContent could not spawn the configured zombie", failure);
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

    public String kartStartProblem(String id, List<Player> drivers) {
        try {
            Plugin provider = kartProvider();
            Method ready = provider.getClass().getMethod("isKartReady", String.class, Player.class);
            for (Player driver : drivers) {
                if (!Boolean.TRUE.equals(ready.invoke(provider, id, driver)))
                    return "Every racer needs the active PulseContent kart model and resource pack loaded.";
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return "PulseKarts needs the compatible PulseContent kart API and racing_kart model.";
        }
    }

    public AutoCloseable attachKart(String id, org.bukkit.entity.Entity anchor, Player driver, Collection<UUID> viewers) {
        try {
            Plugin provider = kartProvider();
            Object result = provider.getClass().getMethod("attachKartVisual", Plugin.class, String.class,
                    org.bukkit.entity.Entity.class, Player.class, Collection.class)
                    .invoke(provider, plugin, id, anchor, driver, List.copyOf(viewers));
            if (!(result instanceof AutoCloseable visual)) throw new IllegalStateException("Missing kart visual handle");
            OwnedVisual owned = new OwnedVisual(visual); handles.add(owned); return owned;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not attach kart", failure); }
    }

    public boolean kartActive(org.bukkit.entity.Entity anchor) {
        try {
            Plugin provider=kartProvider();
            return Boolean.TRUE.equals(provider.getClass().getMethod("isKartVisualActive",org.bukkit.entity.Entity.class).invoke(provider,anchor));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { return false; }
    }

    private Plugin kartProvider() throws ReflectiveOperationException {
        Plugin provider = plugin.getServer().getPluginManager().getPlugin("PulseContent");
        if (provider == null || !provider.isEnabled()
                || !Integer.valueOf(1).equals(provider.getClass().getMethod("kartApiVersion").invoke(provider)))
            throw new IllegalStateException("Kart API unavailable");
        return provider;
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
    public boolean npcPropVisualsReady() {
        try { Api current = api(); return Integer.valueOf(1).equals(current.provider.getClass().getMethod("npcPropVisualsApiVersion").invoke(current.provider)); }
        catch (ReflectiveOperationException | IllegalStateException e) { return false; }
    }
    public PropVisual createNpcProp(String model, Player participant, org.bukkit.Location position, Collection<UUID> viewers, float scale) {
        try {
            Api current = api();
            if (!npcPropVisualsReady()) throw new IllegalStateException("NPC props unavailable");
            Object result = current.provider.getClass().getMethod("createNpcPropVisual", Plugin.class, String.class,
                    Player.class, org.bukkit.Location.class, Collection.class, float.class)
                    .invoke(current.provider, plugin, model, participant, position, List.copyOf(viewers), scale);
            if (!(result instanceof AutoCloseable visual)) throw new IllegalStateException("Missing NPC prop visual");
            OwnedVisual owned = new OwnedVisual(visual); handles.add(owned);
            return new PropVisual(current, owned);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot create NPC prop visual", e); }
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
