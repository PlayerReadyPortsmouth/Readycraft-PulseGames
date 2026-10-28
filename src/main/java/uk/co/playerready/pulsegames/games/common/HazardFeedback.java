package uk.co.playerready.pulsegames.games.common;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.Collection;

/** Small shared feel helpers for the hazard games: titles, pitch ramps and the louder elimination cue. */
public final class HazardFeedback {

    private HazardFeedback() {}

    /** Thunder at a volume children actually notice (the engine default is 0.3). */
    public static final MiniGame.Cue LOUD_ELIMINATION =
            new MiniGame.Cue("minecraft:entity.lightning_bolt.thunder", 1.0f, 1.3f);

    /** Title for the last three standing; only when the field began larger than three. */
    public static boolean finalThree(Collection<Player> audience, int alive, int started) {
        if (alive != 3 || started <= 3) return false;
        for (Player p : audience) {
            Text.title(p, "<gold><b>FINAL THREE", "<gray>Three left standing");
            p.playSound(p.getLocation(), "minecraft:block.note_block.bell", 1f, 1.2f);
        }
        return true;
    }

    /** Pitch for step {@code step} of {@code steps}, rising from 0.6 to 2.0 (the Minecraft limit). */
    public static float risingPitch(int step, int steps) {
        if (steps <= 1) return 1.4f;
        float t = Math.max(0, Math.min(steps - 1, step)) / (float) (steps - 1);
        return 0.6f + 1.4f * t;
    }
}
