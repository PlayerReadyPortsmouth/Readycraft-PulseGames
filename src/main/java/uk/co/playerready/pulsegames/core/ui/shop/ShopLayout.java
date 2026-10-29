package uk.co.playerready.pulsegames.core.ui.shop;

/** Slot map of the tabbed 54-slot shop: tab row, quick-buy row, page, footer. */
public final class ShopLayout {
    private ShopLayout() { }

    public static final int ROWS = 6;
    public static final int TABS = 9;          // 0..8
    public static final int QUICK_ROW = 9;     // 9..16
    public static final int QUICK_CAPACITY = 8;
    public static final int PAGE = 27;         // 27..44
    public static final int PAGE_CAPACITY = 18;
    public static final int HINT = 31;
    /** Spaced slots 28,30..44: nine targets across the two page rows. */
    public static final int SPACED_CAPACITY = 9;
    public static final int FOOTER = 49;
    public static final int CLOSE = 53;

    public static int quickSlot(int i) { return QUICK_ROW + i; }
    public static int pageSlot(int i) { return PAGE + i; }
    /** Spaced entries sit in the middle of the page, one slot apart so thumbs can hit them. */
    public static int spacedSlot(int i) { return PAGE + 1 + i * 2; }
}
