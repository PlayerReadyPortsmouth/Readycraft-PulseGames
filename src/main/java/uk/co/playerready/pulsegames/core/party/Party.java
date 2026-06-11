package uk.co.playerready.pulsegames.core.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Party {

    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    /** Buddy assistants: join games alongside the party but protected and non-competing. */
    private final Set<UUID> assistants = new LinkedHashSet<>();

    public Party(Player leader) {
        this.leader = leader.getUniqueId();
        this.members.add(leader.getUniqueId());
    }

    public UUID leader() { return leader; }
    public Set<UUID> members() { return members; }
    public int size() { return members.size(); }
    public boolean isLeader(Player player) { return leader.equals(player.getUniqueId()); }
    public boolean contains(UUID uuid) { return members.contains(uuid); }

    public boolean isAssistant(UUID uuid) { return assistants.contains(uuid); }

    /** Toggles assistant status; returns the new state. */
    public boolean toggleAssistant(UUID uuid) {
        if (assistants.remove(uuid)) return false;
        assistants.add(uuid);
        return true;
    }

    public void promoteNextLeader() {
        members.stream().findFirst().ifPresent(next -> leader = next);
    }

    public List<Player> onlineMembers() {
        return members.stream().map(Bukkit::getPlayer).filter(p -> p != null && p.isOnline()).toList();
    }
}
