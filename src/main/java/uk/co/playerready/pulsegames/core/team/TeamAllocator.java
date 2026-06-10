package uk.co.playerready.pulsegames.core.team;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.party.PartyManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Splits players into teams of a fixed size, keeping party members together when possible. */
public final class TeamAllocator {

    private TeamAllocator() {
    }

    public static List<GameTeam> allocate(List<Player> players, int teamSize, PartyManager parties) {
        int teamCount = Math.max(1, (int) Math.ceil(players.size() / (double) teamSize));
        List<GameTeam> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) teams.add(new GameTeam(i));

        List<List<Player>> groups = parties.groupsOf(players);
        groups.sort(Comparator.comparingInt(g -> -g.size()));

        for (List<Player> group : groups) {
            for (Player player : group) {
                GameTeam best = teams.stream()
                        .filter(t -> t.members().size() < teamSize)
                        // Prefer the team that already has this player's group-mates.
                        .max(Comparator.comparingInt(t -> (int) group.stream().filter(t::contains).count() * 100
                                - t.members().size()))
                        .orElse(teams.get(0));
                best.add(player);
            }
        }
        return teams;
    }
}
