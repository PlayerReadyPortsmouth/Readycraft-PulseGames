package uk.co.playerready.pulsegames.games.bedwars;

import java.util.*;

/** Shared per-team bot state and the pure rules that read it: roles and which enemy bed each bot goes for. */
final class BedwarsBotTeams {
    private BedwarsBotTeams() {}

    enum Role { GATHER, GUARD, ATTACK }

    /** One per team: roles, who is attacking which bed, and the last enemy seen close to our bed. */
    static final class TeamState {
        final Map<UUID, Role> roles = new HashMap<>();
        final Map<UUID, Integer> targets = new HashMap<>();
        long nextRoleRefresh;
        long threatTick = Long.MIN_VALUE / 2;
        double threatX, threatY, threatZ;
        void seeThreat(long tick, double x, double y, double z) { threatTick = tick; threatX = x; threatY = y; threatZ = z; }
        /** Game second until which our alarm trap's ring counts, and where the intruder was. */
        int alarmUntil = Integer.MIN_VALUE;
        double alarmX, alarmY, alarmZ;
        void alarm(int gameSecond, double x, double y, double z) { alarmUntil = gameSecond + 20; alarmX = x; alarmY = y; alarmZ = z; }
        boolean alarmActive(int gameSecond) { return gameSecond < alarmUntil; }
        boolean threatened(long tick) { return tick - threatTick < 80; }
        Role role(UUID id) { return roles.getOrDefault(id, Role.GATHER); }
    }

    /**
     * One guard per team once the team has two bots (a lone bot guards only for the first soloGuardSeconds), none when
     * the bed is gone. Everyone else gathers and gears up until attackStartSeconds, then attacks.
     * Order is by id so the split is stable between calls.
     */
    static Map<UUID, Role> assign(Collection<UUID> bots, boolean bedAlive, int gameTime, BedwarsBotConfig cfg) {
        List<UUID> sorted = new ArrayList<>(bots);
        sorted.sort(Comparator.comparing(UUID::toString));
        Map<UUID, Role> roles = new LinkedHashMap<>();
        boolean guard = bedAlive && !sorted.isEmpty() && (sorted.size() >= 2 || gameTime < cfg.soloGuardSeconds());
        for (int i = 0; i < sorted.size(); i++) {
            if (guard && i == 0) roles.put(sorted.get(i), Role.GUARD);
            else roles.put(sorted.get(i), gameTime >= cfg.attackStartSeconds() ? Role.ATTACK : Role.GATHER);
        }
        return roles;
    }

    record BedCandidate(int team, double distance, double defence, int gap, int claims) {}

    /**
     * Least-defended, shortest-bridge, nearest bed that fewer teammates have already claimed.
     * defence is the weighted count of covering blocks (wool 1, wood 2, end stone 3).
     */
    static Integer chooseBed(List<BedCandidate> beds) {
        BedCandidate best = null; double bestScore = Double.MAX_VALUE;
        for (BedCandidate bed : beds) {
            double score = bed.defence() * 4 + bed.gap() * 3 + bed.distance() * .5 + bed.claims() * 40;
            if (score < bestScore) { best = bed; bestScore = score; }
        }
        return best == null ? null : best.team();
    }
}
