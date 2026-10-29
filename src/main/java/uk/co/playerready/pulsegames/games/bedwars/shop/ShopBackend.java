package uk.co.playerready.pulsegames.games.bedwars.shop;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/** What the shop menu needs from the running match. Keeps the menu free of Bedwars internals. */
public interface ShopBackend {
    enum Result { OK, NEED_MORE, NO_ROOM, ALREADY_OWNED }

    ShopCatalogue catalogue();
    /** True while this player can still shop (match running and they are alive). */
    boolean live(Player player);
    boolean teamUpgradesOn();
    /** True while this player's team is on a comeback and may use the Comeback shelf. */
    default boolean comebackShop(Player player) { return false; }
    /** The buyer's team wool, or null for the plain catalogue material. */
    Material woolFor(Player player);
    int upgradeLevel(Player player, ShopCatalogue.Upgrade upgrade);
    Result buy(Player player, ShopCatalogue.Item item);
    Result buyUpgrade(Player player, ShopCatalogue.Upgrade upgrade);
    void runLater(Runnable task);
}
