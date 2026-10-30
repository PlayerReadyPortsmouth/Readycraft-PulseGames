package uk.co.playerready.pulsenetwork;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** What /role needs from the permissions plugin. Futures complete off the proxy event thread. */
interface RoleStore {
    CompletableFuture<Optional<UUID>> resolve(String exactName);
    CompletableFuture<String> username(UUID id);
    /** Direct group names; loads offline users. */
    CompletableFuture<Set<String>> groups(UUID id);
    /** Direct group names for an online (already loaded) user, else empty. No I/O. */
    Set<String> cachedGroups(UUID id);
    boolean groupExists(String group);
    CompletableFuture<Void> addGroup(UUID id, String group);
    CompletableFuture<Void> removeGroup(UUID id, String group);
    CompletableFuture<Set<UUID>> members(String group);
    CompletableFuture<Void> setHidden(UUID id, boolean hidden);
    CompletableFuture<Boolean> isHidden(UUID id);
    /** Best-effort entry in the permissions plugin's own action log. */
    void audit(UUID actor, String actorName, UUID target, String targetName, String text);
}
