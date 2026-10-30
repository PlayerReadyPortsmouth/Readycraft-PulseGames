package uk.co.playerready.pulsenetwork;

import java.lang.reflect.Method;
import java.util.UUID;

/** Soft integrations, looked up reflectively so LuckPerms is never a hard dependency. Interface methods only (impl classes are not public). */
final class OptionalPlugins {
    private static final String[] NONE = {"", ""};
    private Method get, users, user, cached, meta, prefix, suffix;
    private boolean tried, failed;

    /** LuckPerms prefix/suffix (legacy ampersand text) for an online player, or empty strings when unavailable. */
    synchronized String[] meta(UUID id) {
        if (failed) return NONE;
        try {
            if (!tried) {
                tried = true;
                get = Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get");
                users = Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager");
                user = Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser", UUID.class);
                cached = Class.forName("net.luckperms.api.model.user.User").getMethod("getCachedData");
                meta = Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("getMetaData");
                Class<?> data = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
                prefix = data.getMethod("getPrefix"); suffix = data.getMethod("getSuffix");
            }
            Object u = user.invoke(users.invoke(get.invoke(null)), id);
            if (u == null) return NONE;
            Object m = meta.invoke(cached.invoke(u));
            String p = (String) prefix.invoke(m), s = (String) suffix.invoke(m);
            return new String[]{p == null ? "" : p, s == null ? "" : s};
        } catch (java.lang.reflect.InvocationTargetException e) {
            return NONE; // LuckPerms present but not ready yet: retry next refresh.
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            failed = true; // Not installed, not ready or API changed: plain names are fine.
            return NONE;
        }
    }

    private Method metaValue;
    /** A LuckPerms user meta value for an online player, or null when unavailable. */
    synchronized String metaValue(UUID id, String key) {
        if (failed) return null;
        try {
            meta(id); // Initialises the reflective handles.
            if (failed || user == null) return null;
            if (metaValue == null) metaValue = Class.forName("net.luckperms.api.cacheddata.CachedMetaData").getMethod("getMetaValue", String.class);
            Object u = user.invoke(users.invoke(get.invoke(null)), id);
            return u == null ? null : (String) metaValue.invoke(meta.invoke(cached.invoke(u)), key);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }
}
