package uk.co.playerready.pulsegames.core.progression;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;

import java.util.UUID;
import java.util.logging.Logger;

/** The real {@link NameStyleGrants.Granter}. Only load this class when LuckPerms is installed. */
final class LuckPermsGranter implements NameStyleGrants.Granter {
    private final LuckPerms api = LuckPermsProvider.get();
    private final Logger log;

    LuckPermsGranter(Logger log) { this.log = log; }

    @Override public void grant(UUID player, String permission) {
        api.getUserManager().modifyUser(player, user -> user.data().add(Node.builder(permission).value(true).build()))
                .exceptionally(error -> { log.warning("Could not grant " + permission + ": " + error); return null; });
    }

    /** A granter, or null (with a one-line log) when LuckPerms is missing or not ready. */
    static NameStyleGrants.Granter create(org.bukkit.plugin.PluginManager plugins, Logger log) {
        if (plugins.getPlugin("LuckPerms") == null) {
            log.info("LuckPerms not found: name styles will not be granted.");
            return null;
        }
        try { return new LuckPermsGranter(log); }
        catch (Throwable notReady) {
            log.info("LuckPerms API not ready: name styles will not be granted (" + notReady.getClass().getSimpleName() + ").");
            return null;
        }
    }
}
