package uk.co.playerready.pulsegames.games.bedwars;

/** Situational item use in a fight. The decision is pure; BedwarsGame performs it. */
final class BedwarsBotItems {
    private BedwarsBotItems() {}

    enum Use { NONE, GOLDEN_APPLE, PEARL_ESCAPE, FIREBALL, POTION, BRIDGE_EGG, POPUP_TOWER }

    /** The shop's newer kit a bot may be carrying. */
    record Kit(boolean hasPotion, boolean hasBridgeEgg, boolean hasTower) {
        static final Kit NONE = new Kit(false, false, false);
    }

    record Facts(double health, double enemyDistance, boolean hasApple, boolean hasPearl, boolean hasFireball,
                 long tick, long fireballReadyAt, boolean fireballSafe) {}

    /** Escape first, then heal, then knock the enemy back. Melee itself lives in the shared core (BotMelee). */
    static Use choose(Facts f, BedwarsBotConfig cfg) { return choose(f, Kit.NONE, cfg); }

    /** As above, then the new kit: a potion as a fight starts, a tower when hurt and cornered, an egg at range. */
    static Use choose(Facts f, Kit kit, BedwarsBotConfig cfg) {
        Use core = chooseCore(f, cfg);
        if (core != Use.NONE) return core;
        if (kit.hasPotion() && f.enemyDistance() <= 12) return Use.POTION;
        if (kit.hasTower() && f.health() <= 8 && f.enemyDistance() <= 6) return Use.POPUP_TOWER;
        if (kit.hasBridgeEgg() && f.enemyDistance() >= 8 && f.enemyDistance() <= 20) return Use.BRIDGE_EGG;
        return Use.NONE;
    }

    private static Use chooseCore(Facts f, BedwarsBotConfig cfg) {
        if (f.hasPearl() && f.health() <= cfg.pearlEscapeHealth() && f.enemyDistance() <= 7) return Use.PEARL_ESCAPE;
        if (f.hasApple() && f.health() <= cfg.appleHealth() && f.enemyDistance() >= 3) return Use.GOLDEN_APPLE;
        if (cfg.fireball() && f.hasFireball() && f.fireballSafe() && f.tick() >= f.fireballReadyAt()
                && f.enemyDistance() >= cfg.fireballMinRange() && f.enemyDistance() <= cfg.fireballMaxRange()) return Use.FIREBALL;
        return Use.NONE;
    }
}
