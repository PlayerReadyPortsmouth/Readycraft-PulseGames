package uk.co.playerready.pulsenetwork;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The only class that touches the LuckPerms API; callers load it only when the LuckPerms plugin is present. */
final class LuckPermsLink {
    private final LuckPerms lp = LuckPermsProvider.get();

    /** Replaces user meta {@code pulse.namestyle}; a null id clears it. LuckPerms saves and syncs it. */
    CompletableFuture<Void> setStyle(UUID id, String style) {
        return lp.getUserManager().modifyUser(id, user -> {
            user.data().clear(NodeType.META.predicate(n -> n.getMetaKey().equals(IdentityResolver.STYLE_KEY)));
            if (style != null) user.data().add(MetaNode.builder(IdentityResolver.STYLE_KEY, style).build());
        });
    }

    /** Direct group membership (never permissions: admins hold {@code *}). */
    boolean inAnyGroup(UUID id, Set<String> groups) {
        var user = lp.getUserManager().getUser(id);
        return user != null && user.getNodes(NodeType.INHERITANCE).stream().anyMatch(n -> groups.contains(n.getGroupName()));
    }

    /** Looks a name up in the LuckPerms user store (works offline); completes with null when unknown. */
    CompletableFuture<UUID> lookup(String name) { return lp.getUserManager().lookupUniqueId(name); }

    /** The {@code pulse.namestyle.<id>} permissions the user holds (set true), as bare ids. Works offline. */
    CompletableFuture<Set<String>> heldStyles(UUID id) {
        return lp.getUserManager().loadUser(id).thenApply(user -> {
            var out = new java.util.HashSet<String>();
            for (var n : user.getNodes(NodeType.PERMISSION)) {
                if (n.getValue() && n.getPermission().startsWith(NameStyleModel.PERMISSION_PREFIX)) out.add(n.getPermission().substring(NameStyleModel.PERMISSION_PREFIX.length()));
            }
            return out;
        });
    }

    /** Grants or revokes one permission node directly on the user. LuckPerms saves and syncs it. */
    CompletableFuture<Void> setPermission(UUID id, String permission, boolean grant) {
        return lp.getUserManager().modifyUser(id, user -> {
            user.data().clear(NodeType.PERMISSION.predicate(n -> n.getPermission().equals(permission)));
            if (grant) user.data().add(net.luckperms.api.node.Node.builder(permission).build());
        });
    }
}
