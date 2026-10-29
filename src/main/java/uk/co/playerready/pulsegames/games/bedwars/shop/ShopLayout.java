package uk.co.playerready.pulsegames.games.bedwars.shop;

/** Slot map of the 54-slot shop: tabs, quick-buy row, category page, footer. */
public final class ShopLayout {
    private ShopLayout() {}
    public static final int SIZE = 54;
    public static final int TEAM_TAB = 8;
    public static final int QUICK_ROW = 9;     // 9..16
    public static final int PAGE = 27;         // 27..44
    public static final int HINT = 31;
    public static final int BALANCE = 49;
    public static final int CLOSE = 53;

    public static int tabSlot(int categoryOrdinal) { return categoryOrdinal; }
    public static int quickSlot(int i) { return QUICK_ROW + i; }
    public static int pageSlot(int i) { return PAGE + i; }
    /** Team upgrades sit in the middle of the page, one slot apart so thumbs can hit them. */
    public static int upgradeSlot(int i) { return PAGE + 1 + i * 2; }
}
