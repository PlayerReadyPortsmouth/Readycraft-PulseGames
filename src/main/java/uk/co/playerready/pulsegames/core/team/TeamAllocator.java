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

    /**
     * @param maxTeams what the arena can actually host (spawn points, bed regions...).
     *                 Teams are never fewer than 2: on a single team nobody can be
     *                 damaged and every player "wins" the moment the clock runs out.
     */
    public static List<GameTeam> allocate(List<Player> players, int teamSize, int maxTeams, PartyManager parties) {
        int wanted = (int) Math.ceil(players.size() / (double) teamSize);
        int teamCount = Math.max(2, Math.min(wanted, maxTeams));
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
                        // Teams can be capped below players/teamSize, so fall back to the
                        // emptiest team rather than piling everyone onto team 1.
                        .orElseGet(() -> teams.stream()
                                .min(Comparator.comparingInt(t -> t.members().size()))
                                .orElseThrow());
                best.add(player);
            }
        }
        return teams;
    }
}
