package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.ui.*;
import uk.co.playerready.pulsegames.core.ui.forms.FormLayout;
import uk.co.playerready.pulsegames.core.ui.forms.Forms;

import java.util.List;

/** The same authored objective and reward terms on a chest card and a Bedrock form. */
final class GameGuide {
    private GameGuide() {}

    static List<String> steps(GameType game) {
        return switch (game.id()) {
            case "presentpanic" -> List.of("Grab a toy from a conveyor.",
                    "Match its named colour at a paper bench. Sneak, or use a carried toy to toggle wrapping.",
                    "Sneak at your bow bench, then walk to your team's sleigh. Matching paper: 3; with bow: 6. Final 30 seconds: double points.");
            case "grinchhunt" -> List.of("Grinch: sneak at trees, carry up to 3, bank 15 in the cave. At morning, hold at least one present to win (map settings may raise this).",
                    "Villager: follow green footprints and tree alarms. Tag with 3 hits or a 3-second sneak hold nearby.",
                    "Use your held gadget. Villagers use the iron nugget to change role. Each Grinch has one heart escape.");
            default -> List.of(game.card().title(), game.card().subtitle(), game.card().tip());
        };
    }

    static String rewards(int win, int finish) {
        return "Base rewards in eligible rounds: " + win + " tokens for a win; " + finish
                + " for other finishes. Placement, featured and booster bonuses may apply. Practice rounds give no match tokens or competitive records.";
    }

    static void open(PulseGamesPlugin plugin, Player player, GameType game, Runnable play, Runnable back) {
        List<String> instructions = steps(game);
        String rewards = rewards(plugin.getConfig().getInt("economy.win-reward", 50),
                plugin.getConfig().getInt("economy.participation-reward", 10));
        String body = "HOW TO PLAY\n" + String.join("\n\n", instructions) + "\n\nREWARDS\n" + rewards
                + "\n\nUse /lobby to leave. Your ready item votes to start sooner.";
        if (Forms.list(plugin, player, game.displayName(), body, List.of(
                new FormLayout.Button("Play / choose mode", "play"), new FormLayout.Button("Back", "back")),
                target -> { if (!player.isOnline()) return; if (target.equals("play")) play.run(); else if (target.equals("back")) back.run(); })) return;
        Menu menu = new Menu(plugin, player, Theme.title(game.displayName()), 3);
        menu.renderer(() -> {
            Lore how = Lore.of();
            instructions.forEach(line -> how.desc(line).blank());
            menu.set(11, Buttons.item(Material.WRITABLE_BOOK, "<gold>How to play", how));
            menu.set(13, Buttons.item(Material.GOLD_NUGGET, "<gold>Rewards", Lore.of().desc(rewards)));
            menu.set(15, Buttons.item(game.icon(), "<green>Play / choose mode", Lore.of().hint("Click to continue")), p -> play.run());
            menu.back(p -> back.run()); menu.close(); menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
        });
        menu.open(player);
    }
}
