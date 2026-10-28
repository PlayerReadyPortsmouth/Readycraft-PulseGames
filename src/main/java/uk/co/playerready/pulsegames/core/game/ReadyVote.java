package uk.co.playerready.pulsegames.core.game;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Waiting-room "ready" vote: the item and the pure start rule. */
public final class ReadyVote {
    private ReadyVote() { }

    public static ItemStack item() {
        ItemStack lime = new ItemStack(Material.LIME_DYE);
        lime.editMeta(meta -> {
            meta.displayName(Text.mm("<green><b>I'm ready!</b> <gray>(right-click)"));
            meta.lore(java.util.List.of(Component.text("Start early when everyone is ready")));
        });
        return lime;
    }

    public static boolean isItem(ItemStack item) {
        return item != null && item.getType() == Material.LIME_DYE;
    }

    /** Every human has clicked and the minimum player count is met. */
    public static boolean shouldStart(Collection<UUID> humans, Set<UUID> ready, int participants, int minPlayers) {
        return !humans.isEmpty() && ready.containsAll(humans) && participants >= minPlayers;
    }

    public static int shortenedCountdown(int current, int readySeconds) {
        return Math.min(current, Math.max(1, readySeconds));
    }
}
