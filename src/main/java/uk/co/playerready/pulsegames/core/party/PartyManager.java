package uk.co.playerready.pulsegames.core.party;

import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PartyManager {

    private record Invite(UUID partyLeader, long expiresAt) {}

    private final JavaPlugin plugin;
    private final List<Party> parties = new ArrayList<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private record NetworkParty(UUID leader, Set<UUID> members) {}
    private final Map<UUID, NetworkParty> network = new HashMap<>();
    private final Set<Party> imported = new java.util.HashSet<>();

    /** Trusted backend bridge snapshot; clients cannot invoke this method through commands. */
    public void networkState(Player player, UUID leader, List<UUID> members) {
        UUID id=player.getUniqueId();
        NetworkParty known=network.get(id);
        if(known!=null && java.util.Objects.equals(known.leader(),leader) && members!=null
                && known.members().equals(new LinkedHashSet<>(members))) return;
        Party old=partyOf(player);
        if (old != null && imported.remove(old)) parties.remove(old);
        network.remove(id);
        if (leader==null || members==null || members.isEmpty()) return;
        Set<UUID> ids=new LinkedHashSet<>(members);
        if(ids.size()!=members.size() || ids.size()>Math.min(8,maxSize()) || !ids.contains(leader) || !ids.contains(id)) return;
        network.put(id,new NetworkParty(leader,Set.copyOf(ids)));
        Player lead=plugin.getServer().getPlayer(leader);
        if(lead!=null && lead.isOnline()) importNetworkParty(lead,List.copyOf(ids));
    }

    /** All-or-nothing import, refusing to overwrite unrelated backend parties. */
    public boolean importNetworkParty(Player leader, List<UUID> members) {
        if(members==null || members.isEmpty() || members.size()>Math.min(8,maxSize())) return false;
        Set<UUID> ids=new LinkedHashSet<>(members);
        if(ids.size()!=members.size() || !ids.contains(leader.getUniqueId())) return false;
        List<Player> local=new ArrayList<>();
        for(UUID id:ids) {
            Player p=plugin.getServer().getPlayer(id);
            if(p==null || !p.isOnline()) return false;
            Party existing=partyOf(p);
            if(existing!=null && (!existing.leader().equals(leader.getUniqueId()) || !existing.members().equals(ids))) return false;
            local.add(p);
        }
        Party party=partyOf(leader);
        if(party==null) { party=new Party(leader);party.members().addAll(ids);parties.add(party);imported.add(party); }
        NetworkParty snapshot=new NetworkParty(leader.getUniqueId(),Set.copyOf(ids));
        local.forEach(p->network.put(p.getUniqueId(),snapshot));
        return true;
    }

    public boolean localOnly(Player player) {
        Party party=partyOf(player);
        return party!=null && !imported.contains(party) && !network.containsKey(player.getUniqueId());
    }

    public boolean localQueueReady(Player player) {
        NetworkParty party=network.get(player.getUniqueId());
        if(party==null) return true;
        Player leader=plugin.getServer().getPlayer(party.leader());
        if(leader!=null && leader.isOnline() && importNetworkParty(leader,List.copyOf(party.members()))) return true;
        player.sendMessage(Component.text("Your network party is spread across servers or has a conflicting local party. Use /queues and let your leader join the group."));
        return false;
    }


    public PartyManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Party partyOf(Player player) {
        for (Party party : parties) if (party.contains(player.getUniqueId())) return party;
        return null;
    }

    public int maxSize() { return plugin.getConfig().getInt("party.max-size", 8); }

    public Party createOrGet(Player leader) {
        Party party = partyOf(leader);
        if (party == null) {
            party = new Party(leader);
            parties.add(party);
        }
        return party;
    }

    public boolean invite(Player inviter, Player target) {
        Party party = createOrGet(inviter);
        if (!party.isLeader(inviter)) {
            inviter.sendMessage(Text.msg("<red>Only the party leader can invite."));
            return false;
        }
        if (party.size() >= maxSize()) {
            inviter.sendMessage(Text.msg("<red>Your party is full."));
            return false;
        }
        if (partyOf(target) != null) {
            inviter.sendMessage(Text.msg("<red>" + target.getName() + " is already in a party."));
            return false;
        }
        long expiry = plugin.getConfig().getInt("party.invite-expiry-seconds", 60) * 1000L;
        invites.put(target.getUniqueId(), new Invite(party.leader(), System.currentTimeMillis() + expiry));
        inviter.sendMessage(Text.msg("Invited <yellow>" + target.getName() + "</yellow> to your party."));
        target.sendMessage(Text.msg("<yellow>" + inviter.getName()
                + "</yellow> invited you to a party. <green><click:run_command:'/party accept'>[ACCEPT]</click></green> "
                + "<red><click:run_command:'/party deny'>[DENY]</click></red>"));
        target.sendMessage(Text.msg("Type <yellow>/party accept</yellow> or <yellow>/party deny</yellow>. Your leader queues the group together."));
        return true;
    }

    public void accept(Player player) {
        if (partyOf(player) != null) {
            player.sendMessage(Text.msg("<red>Leave your current party before accepting another invite."));
            return;
        }
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null || invite.expiresAt() < System.currentTimeMillis()) {
            player.sendMessage(Text.msg("<red>You have no pending party invite."));
            return;
        }
        Party party = parties.stream().filter(p -> p.leader().equals(invite.partyLeader())).findFirst().orElse(null);
        if (party == null || party.size() >= maxSize()) {
            player.sendMessage(Text.msg("<red>That party no longer exists or is full."));
            return;
        }
        party.members().add(player.getUniqueId());
        broadcast(party, "<yellow>" + player.getName() + "</yellow> joined the party!");
    }

    public void deny(Player player) {
        invites.remove(player.getUniqueId());
        player.sendMessage(Text.msg("Invite declined."));
    }

    public void leave(Player player) {
        Party party = partyOf(player);
        if (party == null) {
            player.sendMessage(Text.msg("<red>You are not in a party."));
            return;
        }
        party.members().remove(player.getUniqueId());
        player.sendMessage(Text.msg("You left the party."));
        if (party.members().isEmpty()) {
            parties.remove(party);
        } else {
            if (party.leader().equals(player.getUniqueId())) {
                party.promoteNextLeader();
            }
            broadcast(party, "<yellow>" + player.getName() + "</yellow> left the party.");
        }
    }

    public void disband(Player leader) {
        Party party = partyOf(leader);
        if (party == null || !party.isLeader(leader)) {
            leader.sendMessage(Text.msg("<red>You don't lead a party."));
            return;
        }
        broadcast(party, "The party was disbanded.");
        parties.remove(party);
    }

    public void chat(Player sender, String message) {
        Party party = partyOf(sender);
        if (party == null) {
            sender.sendMessage(Text.msg("<red>You are not in a party."));
            return;
        }
        for (Player member : party.onlineMembers()) {
            member.sendMessage(Text.mm("<blue>Party</blue> <dark_gray>» <yellow>" + sender.getName()
                    + "</yellow><gray>: <white>").append(Component.text(message)));
        }
    }

    public void broadcast(Party party, String miniMessage) {
        for (Player member : party.onlineMembers()) {
            member.sendMessage(Text.msg(miniMessage));
        }
    }

    public void handleQuit(Player player) {
        network.remove(player.getUniqueId());
        Party party=partyOf(player);
        if(party!=null && imported.remove(party)) parties.remove(party);
        else if(party!=null) leave(player);
        invites.remove(player.getUniqueId());
    }

    /**
     * Groups the given players by party (players without one become singleton groups).
     * Used by matchmaking and team allocation.
     */
    public List<List<Player>> groupsOf(List<Player> players) {
        List<List<Player>> groups = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (Player player : players) {
            if (seen.contains(player.getUniqueId())) continue;
            Party party = partyOf(player);
            if (party == null) {
                groups.add(List.of(player));
                seen.add(player.getUniqueId());
            } else {
                List<Player> group = players.stream()
                        .filter(p -> party.contains(p.getUniqueId()))
                        .toList();
                group.forEach(p -> seen.add(p.getUniqueId()));
                groups.add(group);
            }
        }
        return groups;
    }
}
