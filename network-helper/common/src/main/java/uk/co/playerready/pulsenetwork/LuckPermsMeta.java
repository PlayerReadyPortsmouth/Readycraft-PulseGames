package uk.co.playerready.pulsenetwork;

import java.lang.reflect.Method;
import java.util.UUID;

/** Reflective LuckPerms {@code CachedMetaData#getMetaValue}, so LuckPerms is never a hard dependency. Same on Paper and Velocity. */
public final class LuckPermsMeta implements IdentityResolver.Meta {
    private Method get, users, user, cached, meta, value;
    private boolean tried, failed;

    @Override public synchronized String value(UUID id, String key) {
        if (failed) return null;
        try {
            if (!tried) {
                tried = true;
                get = Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get");
                users = Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager");
                user = Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser", UUID.class);
                cached = Class.forName("net.luckperms.api.model.user.User").getMethod("getCachedData");
                meta = Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("getMetaData");
                value = Class.forName("net.luckperms.api.cacheddata.CachedMetaData").getMethod("getMetaValue", String.class);
            }
            Object u = user.invoke(users.invoke(get.invoke(null)), id);
            return u == null ? null : (String) value.invoke(meta.invoke(cached.invoke(u)), key);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return null; // LuckPerms present but not ready yet: retry next time.
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            failed = true;
            return null;
        }
    }
}
