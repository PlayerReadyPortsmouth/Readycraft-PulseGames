package uk.co.playerready.pulsegames.core.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.util.Text;

/**
 * Look and feel shared by every chest menu: palette, titles, sounds and the non-italic rule.
 * The Java client italicises any custom name or lore whose italic flag is unset, so menu text
 * must go through {@link #mm(String)} (or {@link Lore}/{@link Buttons}) and never {@link Text#mm}.
 * {@code Text.mm} itself stays untouched because hub control items are compared by component.
 */
public final class Theme {
    private Theme() { }

    public static final String NAME = "<yellow><b>";
    public static final String TEXT = "<gray>";
    public static final String GOOD = "<green>";
    public static final String BAD = "<red>";
    public static final String MONEY = "<gold>";
    public static final String ACCENT = "<aqua>";
    public static final String HINT = "<dark_gray>";

    /** Soft menu sounds, by key so they resolve without the server registry; quiet on purpose, the players are young and may be wearing headphones. */
    public enum Cue {
        OPEN("block.chest.open", 0.3f, 1.4f),
        CLICK("ui.button.click", 0.4f, 1.2f),
        SUCCESS("entity.experience_orb.pickup", 0.4f, 1.2f),
        ERROR("block.note_block.bass", 0.4f, 0.8f);

        final String sound;
        final float volume;
        final float pitch;

        Cue(String sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    public static void sound(Player player, Cue cue) {
        player.playSound(player.getLocation(), cue.sound, cue.volume, cue.pitch);
    }

    /** MiniMessage text with italic switched off explicitly (an unset flag renders italic on Java). */
    public static Component mm(String miniMessage) {
        return plain(Text.mm(miniMessage));
    }

    public static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /** Menu title: {@code Pulse · Games}. */
    public static Component title(String screen) {
        return Text.mm("<dark_gray>Pulse · " + screen);
    }
}
