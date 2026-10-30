package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.economy.Cosmetic;
import uk.co.playerready.pulsegames.core.economy.CosmeticsService;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.ui.forms.FormLayout;
import uk.co.playerready.pulsegames.core.ui.forms.Forms;

import java.util.ArrayList;
import java.util.List;

/** {@code /level}: level, XP bar, next unlock and recent XP. A Bedrock form where forms work, otherwise a chest. */
public final class LevelMenu {
    private static final String WARDROBE = "wardrobe";
    private static final String CLOSE = "close";
    private static final String NAMESTYLE = "namestyle";

    private final PulseGamesPlugin plugin;

    public LevelMenu(PulseGamesPlugin plugin) { this.plugin = plugin; }

    /** The next level reward the player has not reached, or null at the end of the road. */
    static Cosmetic nextReward(int level) {
        return CosmeticsService.levelRewards().stream().filter(c -> c.level() > level).findFirst().orElse(null);
    }

    /** Plain-text description of the next unlock, shared by the form and the chest. */
    static String nextUnlockText(int level) {
        Cosmetic next = nextReward(level);
        return next == null ? "You have unlocked every level reward!"
                : next.name() + " (" + next.category().displayName + ") at level " + next.level();
    }

    /** Name styles reached at {@code level}, as "3/16". */
    static String nameStyleProgress(int level) {
        var styles = CosmeticsService.byCategory(Cosmetic.Category.NAME_STYLE);
        return styles.stream().filter(c -> c.level() <= level).count() + "/" + styles.size();
    }

    /** The Bedrock form body. */
    static String formBody(int level, XpRules.Progress progress, long totalXp, List<String> recent) {
        List<String> lines = new ArrayList<>();
        lines.add("Level " + level + (progress.maxed() ? " (the top!)" : ""));
        lines.add(XpRules.bar(progress, 12) + (progress.maxed() ? "" : "  " + progress.into() + "/" + progress.needed() + " XP"));
        lines.add("Total XP: " + totalXp);
        lines.add("");
        lines.add("Next unlock: " + nextUnlockText(level));
        lines.add("Name styles: " + nameStyleProgress(level) + ". Set one with /namestyle.");
        lines.add("");
        lines.add("Recent XP:");
        if (recent.isEmpty()) lines.add("Play a game to earn some!");
        else lines.addAll(recent);
        return String.join("\n", lines);
    }

    public void open(Player player) {
        var progress = plugin.levels().progress(player);
        int level = progress.level();
        long total = plugin.levels().xp(player);
        List<String> recent = plugin.levels().recent(player);
        if (Forms.list(plugin, player, "My level " + XpRules.tag(level), formBody(level, progress, total, recent),
                List.of(new FormLayout.Button("Open the wardrobe", WARDROBE), new FormLayout.Button("Choose a name style", NAMESTYLE),
                        new FormLayout.Button("Close", CLOSE)),
                target -> {
                    if (!player.isOnline()) return;
                    if (target.equals(WARDROBE)) plugin.wardrobe().open(player);
                    else if (target.equals(NAMESTYLE)) player.performCommand("namestyle");
                })) return;
        Menu menu = new Menu(plugin, player, Theme.title("My level"), 3);
        menu.renderer(() -> {
            var p = plugin.levels().progress(player);
            menu.set(4, Buttons.glow(Buttons.item(Material.NETHER_STAR, XpRules.colouredTag(p.level()) + " <b>Level " + p.level(),
                    Lore.of().line("<white>" + XpRules.bar(p, 12))
                            .kv("XP", p.maxed() ? "Top level!" : p.into() + "/" + p.needed())
                            .kv("Total XP", String.valueOf(plugin.levels().xp(player))))));
            menu.set(11, Buttons.item(Material.CHEST, Theme.ACCENT + "Next unlock",
                    Lore.of().desc(nextUnlockText(p.level())).blank().desc("A new cosmetic every 5 levels.")));
            Lore recentLore = Lore.of();
            List<String> now = plugin.levels().recent(player);
            if (now.isEmpty()) recentLore.desc("Play a game to earn some!");
            else now.forEach(line -> recentLore.line(Theme.TEXT + line));
            menu.set(13, Buttons.item(Material.PAPER, Theme.NAME + "Recent XP", recentLore));
            menu.set(15, Buttons.item(Material.ENDER_CHEST, Theme.MONEY + "<b>Wardrobe",
                    Lore.of().desc("See what you own, what you can unlock and what you can buy.").blank().hint("Click to open")),
                    pl -> plugin.wardrobe().open(pl));
            menu.set(22, Buttons.item(Material.NAME_TAG, Theme.ACCENT + "Name styles",
                    Lore.of().kv("Unlocked", nameStyleProgress(p.level())).desc("Set it with /namestyle").blank().hint("Click to choose")),
                    pl -> pl.performCommand("namestyle"));
            menu.close();
            menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
        });
        menu.open(player);
    }
}
