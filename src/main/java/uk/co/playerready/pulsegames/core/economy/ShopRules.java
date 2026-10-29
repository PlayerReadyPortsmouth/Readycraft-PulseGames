package uk.co.playerready.pulsegames.core.economy;

/** Pure sizing and spending rules for {@link TokenShopMenu}. */
final class ShopRules {
    private ShopRules() { }

    static final int MAX_ROWS = 6;

    /** Item rows plus one navigation row, at least two rows and at most a full double chest. */
    static int rows(int items) {
        int itemRows = Math.max(1, (Math.min(items, (MAX_ROWS - 1) * 9) + 8) / 9);
        return itemRows + 1;
    }

    /** Items per page: every row except the navigation row. */
    static int perPage(int rows) { return (rows - 1) * 9; }

    /** Buying at or above this price (and the booster) asks first. */
    static boolean needsConfirm(int price, int confirmAt) { return price >= confirmAt; }

    static int balanceAfter(int balance, int price) { return Math.max(0, balance - price); }
}
