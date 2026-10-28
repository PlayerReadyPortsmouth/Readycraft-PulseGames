package uk.co.playerready.pulsegames.games.partygames;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Draws every item once, in random order, before any item can come round again. */
public final class ShuffleBag<T> {
    private final List<T> items;
    private final List<T> bag = new ArrayList<>();
    private final RandomGenerator random;
    private T last;

    public ShuffleBag(List<T> items, RandomGenerator random) {
        this.items = List.copyOf(items);
        this.random = random;
    }

    /** Next item, or null if there are no items. A refill never starts with the previous draw. */
    public T draw() {
        if (items.isEmpty()) return null;
        if (bag.isEmpty()) {
            bag.addAll(items);
            java.util.Collections.shuffle(bag, new java.util.Random(random.nextLong()));
            if (bag.size() > 1 && bag.get(bag.size() - 1).equals(last)) {
                java.util.Collections.swap(bag, bag.size() - 1, random.nextInt(bag.size() - 1));
            }
        }
        last = bag.remove(bag.size() - 1);
        return last;
    }
}
