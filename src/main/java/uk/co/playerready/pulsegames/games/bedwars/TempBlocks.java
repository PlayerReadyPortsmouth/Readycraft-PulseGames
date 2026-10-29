package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Blocks that go back to what they were: shield walls, ice roads, bounce pads, honey patches. A block someone
 * has already broken or replaced is left alone. {@link #clear()} puts everything back at once (match end).
 */
final class TempBlocks {
    private record Entry(Block block, BlockData original, Material placed, int expiresAt) {}

    private final List<Entry> entries = new ArrayList<>();
    private final Consumer<Block> onGone;

    /** @param onGone told each time a block leaves the list, so the game can forget it was a player block. */
    TempBlocks(Consumer<Block> onGone) { this.onGone = onGone; }

    /** Puts {@code data} in the block and schedules it to revert at {@code expiresAt} (game seconds). */
    void place(Block block, BlockData data, int expiresAt) {
        BlockData original = block.getBlockData();
        block.setBlockData(data, false);
        entries.add(new Entry(block, original, data.getMaterial(), expiresAt));
    }

    /** Tracks a block something else already placed (a bounce pad): {@code original} is what to restore. */
    void adopt(Block block, BlockData original, int expiresAt) {
        entries.add(new Entry(block, original, block.getType(), expiresAt));
    }

    boolean tracks(Block block) {
        for (Entry e : entries) if (e.block().equals(block)) return true;
        return false;
    }

    int size() { return entries.size(); }

    void expire(int now) {
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.expiresAt() > now) continue;
            it.remove();
            revert(e);
        }
    }

    void clear() {
        List<Entry> all = new ArrayList<>(entries);
        entries.clear();
        all.forEach(this::revert);
    }

    private void revert(Entry e) {
        if (e.block().getType() == e.placed()) e.block().setBlockData(e.original(), false);
        onGone.accept(e.block());
    }
}
