package uk.co.playerready.pulsegames.core.ui.shop;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.util.Text;

/** One voice for purchase results: a soft sound and a short, kind sentence. */
public final class ShopFeedback {
    private ShopFeedback() { }

    public enum Outcome { OK, NEED_MORE, NO_ROOM, OWNED, UNAVAILABLE }

    /** Chat line for a result, or null when nothing needs saying. Plain words, no glyphs. */
    public static String message(Outcome outcome, int shortfall, String unit, String what) {
        return switch (outcome) {
            case OK, OWNED -> null;
            case NEED_MORE -> "<red>" + ShopRules.needMore(Math.max(1, shortfall), unit) + " for " + what + ".";
            case NO_ROOM -> "<red>Your bag is full. Make some space first.";
            case UNAVAILABLE -> "<red>You can't buy that right now.";
        };
    }

    public static void play(Player player, Outcome outcome, int shortfall, String unit, String what) {
        Theme.sound(player, outcome == Outcome.OK ? Theme.Cue.SUCCESS : Theme.Cue.ERROR);
        String line = message(outcome, shortfall, unit, what);
        if (line != null) player.sendMessage(Text.msg(line));
    }
}
