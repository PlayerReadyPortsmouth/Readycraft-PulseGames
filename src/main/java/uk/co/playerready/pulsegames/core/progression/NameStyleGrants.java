package uk.co.playerready.pulsegames.core.progression;

import uk.co.playerready.pulsegames.core.economy.Cosmetic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Turns name-style level rewards into LuckPerms permissions ({@code pulse.namestyle.<id>}). Permissions are
 * only ever added, never removed. The LuckPerms side sits behind {@link Granter} so this is unit tested.
 */
public final class NameStyleGrants {
    public static final String NODE_PREFIX = "pulse.namestyle.";

    /** Adds a permission to a player's stored data (persists and syncs). Must not block the main thread. */
    public interface Granter { void grant(UUID player, String permission); }

    private final Granter granter;
    private final BooleanSupplier enabled;

    public NameStyleGrants(Granter granter, BooleanSupplier enabled) {
        this.granter = granter;
        this.enabled = enabled;
    }

    public static String node(String styleId) { return NODE_PREFIX + styleId; }

    /**
     * Grants every name style at or below {@code level} that the player does not already hold. Idempotent,
     * so it doubles as first-sight catch-up and as a retry if LuckPerms was unavailable earlier.
     *
     * @return the style ids newly granted
     */
    public List<String> grant(UUID player, int level, Predicate<String> alreadyHolds, List<Cosmetic> rewards) {
        List<String> granted = new ArrayList<>();
        if (!enabled.getAsBoolean() || granter == null) return granted;
        for (Cosmetic reward : rewards) {
            String style = reward.nameStyleId();
            if (style == null || reward.level() > level || alreadyHolds.test(node(style))) continue;
            granter.grant(player, node(style));
            granted.add(style);
        }
        return granted;
    }
}
