package uk.co.playerready.pulsegames.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class Text {

    public static final String PREFIX = "<gradient:#ff5f6d:#ffc371>Pulse</gradient> <dark_gray>» <gray>";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** Set at startup; lets title() respect calm mode everywhere without plumbing. */
    private static uk.co.playerready.pulsegames.core.player.PrefsService prefs;

    private Text() {
    }

    public static void init(uk.co.playerready.pulsegames.core.player.PrefsService prefsService) {
        prefs = prefsService;
    }

    public static Component mm(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public static Component msg(String miniMessage) {
        return MM.deserialize(PREFIX + miniMessage);
    }

    public static String legacy(Component component) {
        return LEGACY.serialize(component);
    }

    public static void title(Player player, String main, String sub) {
        // Calm mode: a quiet action bar line instead of a full-screen flash.
        if (prefs != null && prefs.isCalm(player)) {
            player.sendActionBar(mm(main + (sub.isEmpty() ? "" : " <gray>- ") + sub));
            return;
        }
        player.showTitle(Title.title(mm(main), mm(sub),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1500), Duration.ofMillis(500))));
    }

    /** True if the player has toned-down effects enabled. */
    public static boolean calm(Player player) {
        return prefs != null && prefs.isCalm(player);
    }

    public static String time(int seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }
}
