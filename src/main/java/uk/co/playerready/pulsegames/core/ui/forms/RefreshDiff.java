package uk.co.playerready.pulsegames.core.ui.forms;

import uk.co.playerready.pulsegames.core.lobby.QueueCounts;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Decides whether an open chest list needs redrawing: only when a count it shows has changed. */
public final class RefreshDiff {
    private RefreshDiff() { }

    /** Counts for the keys we show; empty (0/0) entries are dropped so "missing" equals "quiet". */
    public static Map<String, QueueCounts.Count> snapshot(Map<String, QueueCounts.Count> all, Predicate<String> shown) {
        Map<String, QueueCounts.Count> out = new HashMap<>();
        all.forEach((k, v) -> { if (shown.test(k) && (v.waiting() > 0 || v.playing() > 0)) out.put(k, v); });
        return out;
    }

    public static boolean changed(Map<String, QueueCounts.Count> before, Map<String, QueueCounts.Count> after) {
        return !before.equals(after);
    }
}
