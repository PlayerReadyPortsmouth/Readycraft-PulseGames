package uk.co.playerready.pulsegames.core.ui;

import java.util.List;

/** Pure paging maths shared by every menu. */
public final class Paginator {
    private Paginator() { }

    public record Page<T>(List<T> items, int page, int pages) {
        public boolean hasPrev() { return page > 0; }
        public boolean hasNext() { return page < pages - 1; }
    }

    /** Page count; an empty list still has one (empty) page. */
    public static int pages(int total, int perPage) {
        return Math.max(1, (Math.max(0, total) + perPage - 1) / perPage);
    }

    /** Requested page, clamped into range so a stale page number never fails or shows nothing. */
    public static <T> Page<T> page(List<T> all, int perPage, int requested) {
        int pages = pages(all.size(), perPage);
        int page = Math.max(0, Math.min(requested, pages - 1));
        int from = Math.min(all.size(), page * perPage);
        return new Page<>(all.subList(from, Math.min(all.size(), from + perPage)), page, pages);
    }
}
