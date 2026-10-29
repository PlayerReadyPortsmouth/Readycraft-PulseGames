package uk.co.playerready.pulsegames.core.play;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Party-only games: builds the instance, remembers the setup for "Play again", lets late members watch. */
public final class PrivateMatchService {
    private static final int REMEMBERED = 64;

    private final PulseGamesPlugin plugin;
    private final PrivateMatchMenu menu;
    /** Instance id to setup; kept after the match ends so the results screen can offer a rematch. */
    private final Map<String, PrivateSetup> setups = new LinkedHashMap<>();

    public PrivateMatchService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.menu = new PrivateMatchMenu(plugin);
    }

    public boolean enabled() { return plugin.getConfig().getBoolean("private.enabled", true); }

    public boolean hasSetup(String matchId) { return setups.containsKey(matchId); }

    /** Leader-only entry: checks the basics, then opens the pick-a-game flow. */
    public void open(Player player) {
        if (!enabled()) { player.sendMessage(Text.msg("<red>Private matches are switched off right now.")); return; }
        var party = plugin.parties().partyOf(player);
        if (party != null && !party.isLeader(player)) {
            player.sendMessage(Text.msg("<red>Only your party leader can start a private match."));
            return;
        }
        menu.open(player);
    }

    /** Maps in use by a match that has not finished; a finished match's clone is separate and does not block. */
    Set<String> busyArenas(String gameId) {
        Set<String> busy = new LinkedHashSet<>();
        for (GameInstance g : plugin.instances().all()) {
            GameState s = g.state();
            if (g.type().id().equals(gameId) && (s == GameState.LOADING || s == GameState.WAITING
                    || s == GameState.COUNTDOWN || s == GameState.RUNNING)) busy.add(g.arena().id());
        }
        return busy;
    }

    /** Creates the private match for the leader's party and admits everyone. Members are taken from the party, not the draft. */
    public boolean start(Player leader, PrivateSetup draft) {
        if (!enabled()) { leader.sendMessage(Text.msg("<red>Private matches are switched off right now.")); return false; }
        var party = plugin.parties().partyOf(leader);
        if (party != null && !party.isLeader(leader)) {
            leader.sendMessage(Text.msg("<red>Only your party leader can start a private match."));
            return false;
        }
        if (plugin.playService().hasPending(leader)) {
            leader.sendMessage(Text.msg("<yellow>Your last queue is still loading."));
            return false;
        }
        List<Player> group = party == null ? List.of(leader) : List.copyOf(party.onlineMembers());
        int cap = Math.max(1, plugin.getConfig().getInt("private.max-party", 16));
        if (group.size() > cap) {
            leader.sendMessage(Text.msg("<red>Private matches take up to " + cap + " people. Your party has " + group.size() + "."));
            return false;
        }
        var type = plugin.registry().get(draft.gameId());
        var mode = type == null ? null : type.mode(draft.modeId());
        if (type == null || mode == null || type.id().equals("duels") || !plugin.gameAvailable(type)) {
            leader.sendMessage(Text.msg("<red>Pick a game and mode that can be played privately."));
            return false;
        }
        if (type.wip() && !plugin.gamePublic(type) && group.stream().anyMatch(m -> !plugin.canUseNewFeatures(m))) {
            leader.sendMessage(Text.msg("<red>This preview is limited to the configured test players."));
            return false;
        }
        List<PrivateRules.MapOption> options = plugin.arenas().forGame(type.id(), mode.id()).stream()
                .map(a -> new PrivateRules.MapOption(a.id(), a.worldTemplate())).toList();
        var pick = PrivateRules.pickArena(options, busyArenas(type.id()), draft.mapId(),
                bound -> ThreadLocalRandom.current().nextInt(bound));
        if (pick.status() == PrivateRules.Status.NONE) {
            leader.sendMessage(Text.msg("<red>There is no map for " + type.displayName() + " (" + mode.displayName() + ") yet."));
            return false;
        }
        if (pick.status() == PrivateRules.Status.BUSY) {
            leader.sendMessage(Text.msg("<yellow>That map is busy right now. Pick another map, or choose Random."));
            return false;
        }
        Arena arena = plugin.arenas().byId(type.id(), pick.arenaId());
        Set<UUID> people = new LinkedHashSet<>();
        group.forEach(p -> people.add(p.getUniqueId()));
        PrivateSetup setup = draft.withPeople(leader.getUniqueId(), people);
        GameInstance instance = plugin.instances().createPrivate(type, mode, arena, setup, group.size());
        if (instance == null) {
            leader.sendMessage(Text.msg("<red>That map is too small for your group, or the server is full. Try another map."));
            return false;
        }
        boolean botsOpen = PrivateRules.botsWanted(plugin.getConfig().getBoolean("private.allow-bots", true), setup.fillBots())
                && plugin.getConfig().getBoolean("bots.enabled", false) && group.stream().allMatch(plugin::canUseNewFeatures)
                && plugin.bots().supports(type.id(), mode.rulesId());
        int base = Math.max(arena.minPlayers(mode.id()), mode.minPlayers());
        if (group.size() < PrivateRules.minPlayers(base, botsOpen)) {
            instance.cleanup();
            leader.sendMessage(Text.msg("<red>" + type.displayName() + " needs at least two people here, because bots cannot fill in for it. Invite a friend."));
            return false;
        }
        remember(instance.id(), setup);
        group.forEach(p -> p.sendMessage(Text.msg("<aqua>" + leader.getName() + " is starting a private match: <yellow>"
                + type.displayName() + "</yellow> on <yellow>" + arena.displayName() + "</yellow>.")));
        plugin.playService().joinPrivate(leader, instance);
        return true;
    }

    private void remember(String id, PrivateSetup setup) {
        setups.put(id, setup);
        while (setups.size() > REMEMBERED) setups.remove(setups.keySet().iterator().next());
    }

    /** "Play again" on a private result: the same game, mode, map and bots, for the party as it is now. */
    public void rematch(Player player, String matchId) {
        PrivateSetup old = setups.get(matchId);
        if (old == null) return;
        var party = plugin.parties().partyOf(player);
        if (party != null && !party.isLeader(player)) {
            player.sendMessage(Text.msg("<yellow>Ask your party leader to press Play again."));
            return;
        }
        Set<UUID> people = new LinkedHashSet<>();
        if (party == null) people.add(player.getUniqueId());
        else party.onlineMembers().forEach(p -> people.add(p.getUniqueId()));
        start(player, old.withPeople(player.getUniqueId(), people));
    }

    /** {@code /party spectate}: a member who missed the start sits in a waiting lobby, or watches a running match. */
    public void spectate(Player player) {
        var party = plugin.parties().partyOf(player);
        if (party == null) { player.sendMessage(Text.msg("<red>You are not in a party.")); return; }
        GameInstance target = null;
        for (GameInstance g : plugin.instances().all()) {
            if (g.isPrivate() && party.contains(g.privateSetup().leader()) && g.state() != GameState.RESETTING) target = g;
        }
        if (target == null) { player.sendMessage(Text.msg("<yellow>Your party has no private match running.")); return; }
        var current = plugin.instances().byPlayer(player);
        if (current == target) { player.sendMessage(Text.msg("<yellow>You are already in that match.")); return; }
        if (current != null && current.state() != GameState.ENDING) {
            player.sendMessage(Text.msg("<red>Leave your game first with /lobby."));
            return;
        }
        if (target.state() == GameState.ENDING) { player.sendMessage(Text.msg("<yellow>That match has just finished.")); return; }
        if (current != null) current.remove(player, false);
        target.allowPrivate(player.getUniqueId());
        if (!target.admitLate(player)) player.sendMessage(Text.msg("<red>You cannot join that match right now."));
    }
}
