package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Citizens through reflection (a soft dependency, as for the queue bots). NPCs live in a private in-memory
 * registry, so Citizens never saves them: lobby.yml is the only record and they respawn from it on boot.
 */
final class LobbyNpcs {
    private final Object registry;
    private final Method create, spawn, isSpawned, entity, destroy, data, set, addTrait;
    private final Class<?> skinTrait;
    private final Method setSkinName;
    private final Object nameplate, saveKey, tablist;
    private final Set<Object> owned = new HashSet<>();

    private LobbyNpcs(Object registry, ClassLoader loader) throws ReflectiveOperationException {
        this.registry = registry;
        Class<?> npc = loader.loadClass("net.citizensnpcs.api.npc.NPC");
        create = loader.loadClass("net.citizensnpcs.api.npc.NPCRegistry").getMethod("createNPC", EntityType.class, String.class);
        spawn = npc.getMethod("spawn", Location.class);
        isSpawned = npc.getMethod("isSpawned");
        entity = npc.getMethod("getEntity");
        destroy = npc.getMethod("destroy");
        data = npc.getMethod("data");
        Class<?> metadata = loader.loadClass("net.citizensnpcs.api.npc.NPC$Metadata");
        set = loader.loadClass("net.citizensnpcs.api.npc.MetadataStore").getMethod("set", metadata, Object.class);
        nameplate = metadata.getField("NAMEPLATE_VISIBLE").get(null);
        saveKey = metadata.getField("SHOULD_SAVE").get(null);
        tablist = metadata.getField("REMOVE_FROM_TABLIST").get(null);
        addTrait = npc.getMethod("getOrAddTrait", Class.class);
        skinTrait = loader.loadClass("net.citizensnpcs.trait.SkinTrait");
        setSkinName = skinTrait.getMethod("setSkinName", String.class);
    }

    static LobbyNpcs connect(Plugin owner) {
        Plugin citizens = owner.getServer().getPluginManager().getPlugin("Citizens");
        if (citizens == null || !citizens.isEnabled()) return null;
        try {
            ClassLoader loader = citizens.getClass().getClassLoader();
            Class<?> store = loader.loadClass("net.citizensnpcs.api.npc.NPCDataStore");
            Object memory = loader.loadClass("net.citizensnpcs.api.npc.MemoryNPCDataStore").getConstructor().newInstance();
            Object registry = loader.loadClass("net.citizensnpcs.api.CitizensAPI")
                    .getMethod("createAnonymousNPCRegistry", store).invoke(null, memory);
            return new LobbyNpcs(registry, loader);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            owner.getLogger().warning("Lobby NPCs unavailable: incompatible Citizens API (" + failure.getClass().getSimpleName() + ").");
            return null;
        }
    }

    /** Creates and spawns a player NPC with a hidden nameplate (the hologram carries the label). */
    Object spawn(String name, String skin, Location where) {
        Object npc = call(create, registry, EntityType.PLAYER, name);
        owned.add(npc);
        try {
            Object d = call(data, npc);
            call(set, d, nameplate, false);
            call(set, d, saveKey, false);
            call(set, d, tablist, true);
            if (skin != null && !skin.isBlank()) call(setSkinName, call(addTrait, npc, skinTrait), skin);
            call(spawn, npc, where.clone());
            return npc;
        } catch (RuntimeException failure) {
            destroy(npc);
            throw failure;
        }
    }

    boolean isSpawned(Object npc) { return Boolean.TRUE.equals(call(isSpawned, npc)); }

    void respawn(Object npc, Location where) { call(spawn, npc, where.clone()); }

    Entity entityOf(Object npc) { return call(entity, npc) instanceof Entity e ? e : null; }

    void destroy(Object npc) {
        owned.remove(npc);
        try { call(destroy, npc); } catch (RuntimeException ignored) { }
    }

    void close() { for (Object npc : Set.copyOf(owned)) destroy(npc); }

    private static Object call(Method method, Object receiver, Object... args) {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException ex) { throw new IllegalStateException("Citizens " + method.getName() + " failed", ex.getCause()); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException("Citizens " + method.getName() + " unavailable", ex); }
    }
}
