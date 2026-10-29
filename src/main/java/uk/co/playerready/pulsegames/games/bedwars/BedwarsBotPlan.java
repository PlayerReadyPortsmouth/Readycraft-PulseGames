package uk.co.playerready.pulsegames.games.bedwars;

import uk.co.playerready.pulsegames.games.bedwars.BedwarsBotTeams.Role;

/** What a bot is doing this think, in priority order. Pure: the game feeds it facts and acts on the answer. */
final class BedwarsBotPlan {
    private BedwarsBotPlan() {}

    enum Goal { FIGHT, RESPOND_BED, REBUILD, HOLD_BED, SPEND, GATHER, DIAMONDS, ATTACK }

    record Facts(Role role, int gameTime, boolean enemyClose, boolean bedThreatened, boolean nearOwnBed,
                 boolean bedAlive, boolean hasBlocks, boolean hasSword, boolean defenceGap, boolean atBase,
                 boolean affordableItem, boolean diamondTripWanted, boolean gatherDue) {}

    static Goal decide(Facts f, BedwarsBotConfig cfg) {
        if (f.enemyClose()) return Goal.FIGHT;
        if (f.bedAlive() && f.bedThreatened() && (f.role() == Role.GUARD || f.nearOwnBed())) return Goal.RESPOND_BED;
        if (f.bedAlive() && f.defenceGap() && f.hasBlocks() && (f.role() == Role.GUARD || f.gameTime() < cfg.attackStartSeconds()))
            return Goal.REBUILD;
        if (f.role() == Role.GUARD && f.bedAlive()) return Goal.HOLD_BED;
        if (!f.hasBlocks() || !f.hasSword() || f.role() == Role.GATHER) return Goal.GATHER;
        if (f.affordableItem() && !f.atBase()) return Goal.SPEND;
        if (f.diamondTripWanted() && f.gameTime() >= cfg.midGameSeconds()) return Goal.DIAMONDS;
        if (f.gatherDue()) return Goal.GATHER;
        return Goal.ATTACK;
    }
}
