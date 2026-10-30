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
}
