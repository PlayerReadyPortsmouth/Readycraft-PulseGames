package uk.co.playerready.pulsenetwork;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.actionlog.Action;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** LuckPerms-backed store. Only constructed when the plugin is loaded, so the API classes are never touched otherwise. */
final class LuckPermsStore implements RoleStore {
    private final LuckPerms lp = LuckPermsProvider.get();

    public CompletableFuture<Optional<UUID>> resolve(String name) {
        return lp.getUserManager().lookupUniqueId(name).thenApply(Optional::ofNullable);
    }
    public CompletableFuture<String> username(UUID id) { return lp.getUserManager().lookupUsername(id); }
    public CompletableFuture<Set<String>> groups(UUID id) {
        return lp.getUserManager().loadUser(id).thenApply(u -> u.getNodes(NodeType.INHERITANCE).stream().map(InheritanceNode::getGroupName).collect(Collectors.toSet()));
    }
    public Set<String> cachedGroups(UUID id) {
        var u = lp.getUserManager().getUser(id);
        return u == null ? Set.of() : u.getNodes(NodeType.INHERITANCE).stream().map(InheritanceNode::getGroupName).collect(Collectors.toSet());
    }
    public boolean groupExists(String group) { return lp.getGroupManager().getGroup(group) != null; }
    public CompletableFuture<Void> addGroup(UUID id, String group) {
        return lp.getUserManager().modifyUser(id, u -> u.data().add(InheritanceNode.builder(group).build()));
    }
    public CompletableFuture<Void> removeGroup(UUID id, String group) {
        return lp.getUserManager().modifyUser(id, u -> u.data().remove(InheritanceNode.builder(group).build()));
    }
    public CompletableFuture<Set<UUID>> members(String group) {
        return lp.getUserManager().searchAll(NodeMatcher.key(InheritanceNode.builder(group).build())).thenApply(m -> new TreeSet<>(m.keySet()));
    }
    public CompletableFuture<Void> setHidden(UUID id, boolean hidden) {
        return lp.getUserManager().modifyUser(id, u -> {
            u.data().clear(NodeType.META.predicate(n -> n.getMetaKey().equals(RoleLogic.HIDDEN_META)));
            if (hidden) u.data().add(MetaNode.builder(RoleLogic.HIDDEN_META, "true").build());
        });
    }
    public CompletableFuture<Boolean> isHidden(UUID id) {
        return lp.getUserManager().loadUser(id).thenApply(u -> "true".equalsIgnoreCase(u.getCachedData().getMetaData().getMetaValue(RoleLogic.HIDDEN_META)));
    }
    public CompletableFuture<String> nickname(UUID id) {
        return lp.getUserManager().loadUser(id).thenApply(u -> u.getNodes(NodeType.META).stream().filter(n -> n.getMetaKey().equals(IdentityResolver.NICK_KEY)).map(MetaNode::getMetaValue).findFirst().orElse(null));
    }
    public CompletableFuture<Void> setNickname(UUID id, String nick) {
        return lp.getUserManager().modifyUser(id, u -> {
            u.data().clear(NodeType.META.predicate(n -> n.getMetaKey().equals(IdentityResolver.NICK_KEY)));
            if (nick != null) u.data().add(MetaNode.builder(IdentityResolver.NICK_KEY, nick).build());
        });
    }
    public void audit(UUID actor, String actorName, UUID target, String targetName, String text) {
        try {
            lp.getActionLogger().submit(lp.getActionLogger().actionBuilder().source(actor).sourceName(actorName).target(target)
                    .targetName(targetName).targetType(Action.Target.Type.USER).description(text).build());
        } catch (RuntimeException | LinkageError ignored) { /* The proxy log line is the authoritative record. */ }
    }
}
