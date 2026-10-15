package uk.co.playerready.pulsegames.games.karts;

import java.util.Objects;

/** Moderate catch-up rewards: lightning requires strictly trailing checkpoint progress. */
final class KartItemBalance {
    private KartItemBalance() {}

    /** Caller determines clearlyBehind from authoritative race progress; ties count as leading. */
    static PulseKartsGame.Item choose(KartStyle style, int roll, boolean clearlyBehind) {
        Objects.requireNonNull(style, "style");
        int value = Math.floorMod(roll, 100);
        boolean trickster = style == KartStyle.TRICKSTER;
        int shell = clearlyBehind ? (trickster ? 20 : 35) : (trickster ? 40 : 60);
        int lightning = clearlyBehind ? (trickster ? 15 : 10) : 0;
        if (value < shell) return PulseKartsGame.Item.SHELL;
        if (value < 100 - lightning) return PulseKartsGame.Item.TURBO;
        return PulseKartsGame.Item.LIGHTNING;
    }
}
