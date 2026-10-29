package uk.co.playerready.pulsegames.core.ui.shop;

import org.bukkit.Material;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure shop maths shared by every game shop: can they afford it, what do we say, which tier replaces which. */
public final class ShopRules {
    private ShopRules() { }

    public enum Status { CAN_BUY, NEED_MORE, OWNED_THIS, OWNED_BETTER }

    public record Verdict(Status status, int shortfall) {
        public boolean buyable() { return status == Status.CAN_BUY; }
    }

    public static Verdict afford(int have, int cost) {
        return have >= cost ? new Verdict(Status.CAN_BUY, 0) : new Verdict(Status.NEED_MORE, cost - have);
    }

    /** "Need 3 more iron": plain words, so it reads the same on Java and Bedrock. */
    public static String needMore(int shortfall, String unit) {
        return "Need " + shortfall + " more " + unit;
    }

    /** Owned tier beats cost: a better or equal tier in the same chain is never offered for sale. */
    public static Verdict tiered(int ownedTier, int itemTier, int have, int cost) {
        if (ownedTier > itemTier) return new Verdict(Status.OWNED_BETTER, 0);
        if (ownedTier == itemTier) return new Verdict(Status.OWNED_THIS, 0);
        return afford(have, cost);
    }

    /** Highest position in the chain among the held materials, or -1 for none. */
    public static int ownedTier(List<Material> chain, Collection<Material> held) {
        int best = -1;
        for (Material m : held) best = Math.max(best, chain.indexOf(m));
        return best;
    }

    /** Materials below {@code tier} in the chain: the ones a purchase of that tier replaces. */
    public static Set<Material> lowerTiers(List<Material> chain, int tier) {
        return new HashSet<>(chain.subList(0, tier));
    }
}
