package uk.co.playerready.pulsegames.core.game;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.party.Party;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;

/** Matchmaking entry point: routes players (and their whole party) into instances. */
public final class PlayService {

    /** A broken arena rejects every join, so re-queueing must not be able to spin forever. */
    private static final int MAX_REQUEUES = 3;

    private final PulseGamesPlugin plugin;

    public PlayService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void join(Player player, GameType type, GameMode mode) {
        join(player, type, mode, 0);
    }

    private void join(Player player, GameType type, GameMode mode, int requeues) {
        Party party = plugin.parties().partyOf(player);
        List<Player> group;
        if (party != null) {
            if (!party.isLeader(player)) {
                player.sendMessage(Text.msg("<red>Only your party leader can queue the party."));
                return;
            }
            group = party.onlineMembers();
        } else {
            group = List.of(player);
        }
        if (group.size() > Math.min(mode.maxPlayers(), 64)) {
            player.sendMessage(Text.msg("<red>Your party is too big for this mode."));
            return;
        }
        GameInstance instance = plugin.instances().findOrCreate(type, mode, group.size());
        if (instance == null) {
            player.sendMessage(Text.msg("<red>No maps available for " + type.displayName() + " <gray>("
                    + mode.displayName() + ")</gray><red> right now."));
            return;
        }
        // Only leave the current game once the new one is secured - a failed queue used to
        // strand the player inside an instance world with no way back to the lobby.
        for (Player member : group) {
            GameInstance current = plugin.instances().byPlayer(member);
            if (current != null) current.remove(member, false);
        }
        joinWhenReady(instance, group, 0, requeues);
    }

    /** Instances clone their world async; poll briefly until the lobby is open. */
    private void joinWhenReady(GameInstance instance, List<Player> group, int attempts, int requeues) {
        if (instance.state() == GameState.LOADING) {
            if (attempts > 60) {
                group.forEach(p -> p.sendMessage(Text.msg("<red>The game took too long to load, try again.")));
                return;
            }
            if (attempts == 0) {
                group.forEach(p -> p.sendMessage(Text.msg("Loading <yellow>" + instance.arena().displayName() + "</yellow>...")));
            }
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> joinWhenReady(instance, group, attempts + 1, requeues), 10L);
            return;
        }
        for (Player member : group) {
            if (!member.isOnline()) continue;
            if (instance.add(member)) continue;
            if (requeues >= MAX_REQUEUES) {
                member.sendMessage(Text.msg("<red>Couldn't get you into " + instance.type().displayName()
                        + " right now - please try again shortly."));
                plugin.lobby().sendToLobby(member);
                return;
            }
            member.sendMessage(Text.msg("<red>That game filled up. Re-queueing..."));
            join(member, instance.type(), instance.mode(), requeues + 1);
            return;
        }
    }

    public void leave(Player player) {
        GameInstance instance = plugin.instances().byPlayer(player);
        if (instance == null) {
            plugin.lobby().sendToLobby(player);
            return;
        }
        instance.remove(player, true);
        player.sendMessage(Text.msg("You left the game."));
    }
}
