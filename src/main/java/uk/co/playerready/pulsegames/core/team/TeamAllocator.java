package uk.co.playerready.pulsegames.core.team;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.party.PartyManager;

import java.util.*;
import java.util.function.ToIntFunction;

/** Party integrity first, calibrated strength second. Solo modes remain opponents. */
public final class TeamAllocator {
    private TeamAllocator() {}

    public static List<GameTeam> allocate(List<Player> players, int teamSize, int maxTeams, PartyManager parties) {
        return allocate(players, teamSize, maxTeams, parties, player -> 2);
    }

    public static List<GameTeam> allocate(List<Player> players, int teamSize, int maxTeams,
                                          PartyManager parties, ToIntFunction<Player> strength) {
        if (teamSize < 1 || maxTeams < 2 || players.size() > (long) teamSize * maxTeams)
            throw new IllegalArgumentException("Players exceed the available team capacity");
        List<List<Player>> groups = groups(players, teamSize, parties);
        if (groups.stream().anyMatch(group -> group.size() > teamSize))
            throw new IllegalArgumentException("A party exceeds the selected team size");
        groups.sort(Comparator.<List<Player>>comparingInt(List::size).reversed()
                .thenComparing(Comparator.comparingInt((List<Player> group) -> power(group, strength)).reversed()));
        int minimum = Math.max(2, (players.size() + teamSize - 1) / teamSize);
        int[] budget = {20_000};
        for (int count = minimum; count <= maxTeams && budget[0] > 0; count++) {
            List<List<Player>> rosters = new ArrayList<>();
            for (int i = 0; i < count; i++) rosters.add(new ArrayList<>());
            if (!pack(groups, 0, rosters, teamSize, strength, new HashSet<>(), budget)) continue;
            List<GameTeam> teams = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                GameTeam team = new GameTeam(i);
                rosters.get(i).forEach(team::add);
                teams.add(team);
            }
            return teams;
        }
        throw new IllegalArgumentException("Parties cannot fit together in this map's teams");
    }

    public static boolean canFit(List<Player> players, int teamSize, int maxTeams, PartyManager parties) {
        try { allocate(players, teamSize, maxTeams, parties); return true; }
        catch (IllegalArgumentException noRoom) { return false; }
    }

    private static List<List<Player>> groups(List<Player> players, int teamSize, PartyManager parties) {
        return teamSize == 1 ? new ArrayList<>(players.stream().map(List::of).toList())
                : new ArrayList<>(parties.groupsOf(players));
    }

    private static int power(List<Player> group, ToIntFunction<Player> strength) {
        return group.stream().mapToInt(player -> Math.max(1, Math.min(3, strength.applyAsInt(player)))).sum();
    }

    private static boolean pack(List<List<Player>> groups, int index, List<List<Player>> teams,
                                int capacity, ToIntFunction<Player> strength, Set<String> failed, int[] budget) {
        if (--budget[0] < 0) return false;
        if (index == groups.size()) return true;
        String state = index + ":" + teams.stream().map(List::size).sorted().toList();
        if (failed.contains(state)) return false;
        List<Player> group = groups.get(index);
        List<List<Player>> candidates = teams.stream().filter(team -> team.size() + group.size() <= capacity)
                .sorted(Comparator.<List<Player>>comparingInt(team -> power(team, strength))
                        .thenComparingInt(List::size)).toList();
        Set<Integer> triedSizes = new HashSet<>();
        for (List<Player> team : candidates) {
            // Equal occupancy has identical packing feasibility; preferred strength wins.
            if (!triedSizes.add(team.size())) continue;
            int previous = team.size();
            team.addAll(group);
            if (pack(groups, index + 1, teams, capacity, strength, failed, budget)) return true;
            team.subList(previous, team.size()).clear();
        }
        failed.add(state);
        return false;
    }
}
