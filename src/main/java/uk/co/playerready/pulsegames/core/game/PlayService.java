package uk.co.playerready.pulsegames.core.game;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.party.Party;
import uk.co.playerready.pulsegames.core.team.TeamAllocator;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Matchmaking entry point: routes players (and their whole party) into instances. */
public final class PlayService {

    private static final int MAX_REQUEUES = 3;
    private final PulseGamesPlugin plugin;
    private final Map<UUID, QueueTicket> pending = new HashMap<>();
    private final java.util.Set<GameInstance> admissions = new java.util.HashSet<>();

    public boolean admitting(GameInstance instance) { return admissions.contains(instance); }

    /** One ticket owns the entire group until admission succeeds or someone cancels. */
    private record QueueTicket(List<Player> group, GameType type, GameMode mode, GameInstance preferred, Party party) {}

    public boolean hasPending(Player player) { return pending.containsKey(player.getUniqueId()); }

    public PlayService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void join(Player player, GameType type, GameMode mode) {
        join(player, type, mode, null);
    }

    public void joinNetworkQueue(Player player, String instanceId) {
        GameInstance instance = plugin.instances().all().stream().filter(g -> g.id().equals(instanceId)).findFirst().orElse(null);
        if (instance == null || instance.practice() || !instance.joinable(1)) {
            player.sendMessage(Text.msg("<red>That queue has closed. Use /queues to choose another."));
            return;
        }
        if (plugin.instances().byPlayer(player) == instance) {
            player.sendMessage(Text.msg("<yellow>You are already in that queue."));
            return;
        }
        join(player, instance.type(), instance.mode(), instance);
    }

    /** Accept a complete transferred network party only after every member is actually admitted. */
    public boolean joinNetworkPartyQueue(Player leader, String instanceId, List<Player> members) {
        if (members == null || members.isEmpty() || members.size() > 64 || !members.contains(leader)
                || members.stream().anyMatch(p -> p == null || !p.isOnline() || hasPending(p)
                    || plugin.instances().byPlayer(p) != null)) return false;
        var requested = members.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
        if (requested.size() != members.size()) return false;
        Party party = plugin.parties().partyOf(leader);
        if (party == null || !party.isLeader(leader) || !party.onlineMembers().stream()
                .map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet()).equals(requested)) return false;
        GameInstance destination = plugin.instances().all().stream().filter(g -> g.id().equals(instanceId)).findFirst().orElse(null);
        if (destination == null || destination.practice() || destination.world() == null
                || !destination.joinable(members.size()) || admitting(destination)) return false;
        join(leader, destination.type(), destination.mode(), destination);
        boolean admitted = members.stream().allMatch(p -> plugin.instances().byPlayer(p) == destination && destination.isParticipant(p));
        if (!admitted) members.forEach(this::cancelPending);
        return admitted;
    }

    private void join(Player player, GameType type, GameMode mode, GameInstance preferred) {
        if (!plugin.parties().localQueueReady(player)) return;
        if (!plugin.gameAvailable(type)) {
            player.sendMessage(Text.msg("<red>This game is not enabled yet."));
            return;
        }
        if ("duels".equals(type.id()) && mode.combatStyle() != GameMode.CombatStyle.DEFAULT) {
            if (mode.combatStyle() == GameMode.CombatStyle.CLASSIC
                    && (plugin.combat() == null || !plugin.combat().available())) {
                player.sendMessage(Text.msg("<red>Classic combat is not available on this server yet.")); return;
            }
            if (plugin.kits().duelChoices(mode).isEmpty()) {
                player.sendMessage(Text.msg("<red>No compatible loadouts are configured for this discipline.")); return;
            }
        }
        Party party = plugin.parties().partyOf(player);
        if (party != null && !party.isLeader(player)) {
            player.sendMessage(Text.msg("<red>Only your party leader can queue the party."));
            return;
        }
        List<Player> group = party == null ? List.of(player) : List.copyOf(party.onlineMembers());
        if (group.isEmpty() || group.size() > Math.min(mode.maxPlayers(), 64)) {
            player.sendMessage(Text.msg("<red>Your party is too big for this mode."));
            return;
        }
        if (mode.isTeams() && mode.teamSize() > 1 && group.size() > mode.teamSize()) {
            player.sendMessage(Text.msg("<red>Your party has " + group.size() + " players, but this mode has teams of "
                    + mode.teamSize() + ". Choose a larger team size or a solo mode."));
            return;
        }
        if (type.wip() && group.stream().anyMatch(member -> !plugin.canUseNewFeatures(member))) {
            player.sendMessage(Text.msg("<red>This preview is limited to the configured test players."));
            return;
        }
        if ("duels".equals(type.id()) && mode.combatStyle() != GameMode.CombatStyle.DEFAULT
                && group.stream().anyMatch(member -> plugin.duelMenu().kitFor(member, mode) == null)) {
            player.sendMessage(Text.msg("<red>Someone in your party has no compatible unlocked kit for this discipline.")); return;
        }
        // A new request supersedes old requests even if no map is available for it.
        group.forEach(this::cancelPending);
        QueueTicket ticket = new QueueTicket(group, type, mode, preferred, party);
        group.forEach(member -> pending.put(member.getUniqueId(), ticket));
        findDestination(ticket, 0);
    }

    private boolean current(QueueTicket ticket) {
        return ticket.group().stream().allMatch(p -> pending.get(p.getUniqueId()) == ticket);
    }

    private void release(QueueTicket ticket) {
        ticket.group().forEach(p -> pending.remove(p.getUniqueId(), ticket));
    }

    /** Called on quit as well as /lobby; reconnecting must not resurrect a queued join. */
    public void cancelPending(Player player) {
        QueueTicket ticket = pending.get(player.getUniqueId());
        if (ticket != null) release(ticket);
    }

    private boolean readyGroup(QueueTicket ticket) {
        if (!current(ticket)) return false;
        if (ticket.group().stream().allMatch(Player::isOnline)) {
            boolean sameParty = ticket.group().stream().allMatch(p -> plugin.parties().partyOf(p) == ticket.party());
            boolean sameSize = ticket.party() == null || ticket.party().size() == ticket.group().size();
            if (sameParty && sameSize) return true;
            fail(ticket, "<red>Your party changed while loading. Please queue again.");
            return false;
        }
        fail(ticket, "<red>A queued player left. Please queue your party again.");
        return false;
    }

    private void findDestination(QueueTicket ticket, int requeues) {
        if (!readyGroup(ticket)) return;
        GameInstance instance = ticket.preferred() != null ? ticket.preferred() : plugin.instances().findOrCreate(ticket.type(), ticket.mode(), ticket.group().size(),
                candidate -> fitsTeams(candidate, ticket));
        if (instance == null) {
            fail(ticket, "<red>No maps available for " + ticket.type().displayName() + " <gray>("
                    + ticket.mode().displayName() + ")</gray><red> right now.");
            return;
        }
        joinWhenReady(instance, ticket, 0, requeues);
    }

    private boolean fitsTeams(GameInstance instance, QueueTicket ticket) {
        if (!ticket.mode().isTeams() || ticket.mode().teamSize() == 1) return true;
        Map<UUID, Player> humans = new java.util.LinkedHashMap<>();
        instance.players().stream().filter(p -> !instance.isBot(p)).forEach(p -> humans.put(p.getUniqueId(), p));
        ticket.group().forEach(p -> humans.put(p.getUniqueId(), p));
        return TeamAllocator.canFit(new ArrayList<>(humans.values()), ticket.mode().teamSize(), instance.teamCount(), plugin.parties());
    }

    /** Keep the current game until the destination can accept the whole group. */
    private void joinWhenReady(GameInstance instance, QueueTicket ticket, int attempts, int requeues) {
        if (!readyGroup(ticket)) return;
        if (instance.state() == GameState.LOADING) {
            if (attempts >= 60) {
                instance.note("queue-timeout", "Map loading exceeded 30 seconds");
                fail(ticket, "<red>The game took too long to load. Reference: " + instance.id());
                return;
            }
            if (attempts == 0) ticket.group().forEach(p -> p.sendMessage(Text.msg(
                    "Loading <yellow>" + instance.arena().displayName() + "</yellow>...")));
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> joinWhenReady(instance, ticket, attempts + 1, requeues), 10L);
            return;
        }
        // Teleport and plugin callbacks may synchronously re-enter matchmaking.
        if (admitting(instance)) {
            if (attempts >= 60) {
                fail(ticket, "<red>The queue is busy. Please try again."); return;
            }
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> joinWhenReady(instance, ticket, attempts + 1, requeues), 10L);
            return;
        }
        admissions.add(instance);
        List<Player> attempted = new ArrayList<>();
        boolean shouldRetry = false;
        try {
            int incoming = (int) ticket.group().stream().filter(p -> !instance.isParticipant(p)).count();
            if (!instance.joinable(incoming) || !fitsTeams(instance, ticket)) {
                shouldRetry = true;
            } else if (ticket.mode().combatStyle() == GameMode.CombatStyle.CLASSIC
                    && (plugin.combat() == null || !plugin.combat().allowsClassic(instance.world()))) {
                fail(ticket, "<red>This arena is not configured for Classic combat.");
            } else {
                if (plugin.bots() != null) plugin.bots().makeRoom(instance, incoming);
                boolean complete = true;
                for (Player member : ticket.group()) {
                    if (!readyGroup(ticket)) { complete = false; break; }
                    if (instance.isParticipant(member)) continue;
                    GameInstance old = plugin.instances().byPlayer(member);
                    if (old != null) old.remove(member, false);
                    if (!readyGroup(ticket)) { complete = false; break; }
                    attempted.add(member);
                    if (!instance.add(member)) { complete = false; shouldRetry = current(ticket); break; }
                    if (!readyGroup(ticket)) { complete = false; break; }
                }
                if (complete) release(ticket);
                else rollback(instance, attempted);
            }
        } catch (RuntimeException failure) {
            rollback(instance, attempted);
            instance.note("party-admission-failed", failure.getClass().getSimpleName());
            fail(ticket, "<red>Could not admit your party safely. Please queue again.");
        } finally {
            admissions.remove(instance);
        }
        if (shouldRetry && current(ticket)) retry(ticket, requeues);
    }

    private void rollback(GameInstance instance, List<Player> attempted) {
        for (Player player : attempted) {
            try { instance.remove(player, true); }
            catch (RuntimeException failure) {
                instance.note("party-rollback-failed", failure.getClass().getSimpleName());
            }
        }
    }

    private void retry(QueueTicket ticket, int requeues) {
        if (ticket.preferred() != null) {
            fail(ticket, "<red>That queue has started or no longer has room for your party. Use /queues again.");
            return;
        }
        if (requeues >= MAX_REQUEUES) {
            fail(ticket, "<red>Couldn't get your group into " + ticket.type().displayName()
                    + " right now - please try again shortly.");
            return;
        }
        findDestination(ticket, requeues + 1);
    }

    private void fail(QueueTicket ticket, String message) {
        if (!current(ticket)) return;
        release(ticket);
        for (Player member : ticket.group()) {
            if (!member.isOnline()) continue;
            member.sendMessage(Text.msg(message));
            if (plugin.instances().byPlayer(member) == null) plugin.lobby().sendToLobby(member);
        }
    }

    public void leave(Player player) {
        if (plugin.results() != null) plugin.results().clearReturnHold(player);
        cancelPending(player);
        GameInstance instance = plugin.instances().byPlayer(player);
        if (instance == null) {
            plugin.lobby().sendToLobby(player);
            return;
        }
        instance.remove(player, true);
        player.sendMessage(Text.msg("You left the game."));
    }
}
