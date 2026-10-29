package uk.co.playerready.pulsegames.core.game;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Pure catch-up rules for the side that is behind. Config under {@code comeback.*}, all on by default. */
public final class Comeback {
    private Comeback() {}

    public static boolean enabled(ConfigurationSection config, String gameId) {
        return config.getBoolean("comeback.enabled", true) && config.getBoolean("comeback." + gameId + ".enabled", true);
    }

    /**
     * Bedwars: a team with no bed, still fighting, and outnumbered by the biggest opposing team. A team that
     * still has a bed (including the only bed left) never qualifies.
     */
    public static boolean bedwarsTeam(boolean hasBed, int ownAlive, int strongestEnemyAlive) {
        return !hasBed && ownAlive > 0 && strongestEnemyAlive > ownAlive;
    }

    /** Teams that qualify right now, from each team's bed and living-player count. */
    public static Set<Integer> bedwarsTeams(Map<Integer, Boolean> bed, Map<Integer, Integer> alive) {
        Set<Integer> out = new HashSet<>();
        for (var entry : alive.entrySet()) {
            int team = entry.getKey();
            int strongest = 0;
            for (var other : alive.entrySet()) if (other.getKey() != team) strongest = Math.max(strongest, other.getValue());
            if (bedwarsTeam(bed.getOrDefault(team, false), entry.getValue(), strongest)) out.add(team);
        }
        return out;
    }

    /** Kit PvP and Quake: this many kills behind the leader earns the respawn boost. */
    public static boolean killsBehind(int own, int leader, int threshold) {
        return threshold > 0 && leader - own >= threshold;
    }

    /** Dodgeball: only a strictly smaller side gets the extra ball. */
    public static boolean smallerSide(int own, int biggest) { return own < biggest; }

    /** Kit PvP / Quake respawn boost due: {@code comeback.<game>.kills-behind} (5) kills behind the leader. */
    public static boolean boostDue(ConfigurationSection config, String gameId, int own, int leader) {
        return enabled(config, gameId) && killsBehind(own, leader, config.getInt("comeback." + gameId + ".kills-behind", 5));
    }

    public static int boostTicks(ConfigurationSection config, String gameId) {
        return Math.max(1, config.getInt("comeback." + gameId + ".boost-seconds", 3)) * 20;
    }
}
