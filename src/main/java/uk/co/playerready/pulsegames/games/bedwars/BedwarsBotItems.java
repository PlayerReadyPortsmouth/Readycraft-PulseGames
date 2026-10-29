package uk.co.playerready.pulsegames.games.bedwars;

/** Situational item use in a fight. The decision is pure; BedwarsGame performs it. */
final class BedwarsBotItems {
    private BedwarsBotItems() {}

    enum Use { NONE, GOLDEN_APPLE, PEARL_ESCAPE, FIREBALL }

    record Facts(double health, double enemyDistance, boolean hasApple, boolean hasPearl, boolean hasFireball,
                 long tick, long fireballReadyAt, boolean fireballSafe) {}

    /** Escape first, then heal, then knock the enemy back. TODO(BotMelee): melee itself moves to the shared core. */
    static Use choose(Facts f, BedwarsBotConfig cfg) {
        if (f.hasPearl() && f.health() <= cfg.pearlEscapeHealth() && f.enemyDistance() <= 7) return Use.PEARL_ESCAPE;
        if (f.hasApple() && f.health() <= cfg.appleHealth() && f.enemyDistance() >= 3) return Use.GOLDEN_APPLE;
        if (cfg.fireball() && f.hasFireball() && f.fireballSafe() && f.tick() >= f.fireballReadyAt()
                && f.enemyDistance() >= cfg.fireballMinRange() && f.enemyDistance() <= cfg.fireballMaxRange()) return Use.FIREBALL;
        return Use.NONE;
    }
}
