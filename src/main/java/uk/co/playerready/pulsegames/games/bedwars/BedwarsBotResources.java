package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import uk.co.playerready.pulsegames.core.game.*;

/** Picks up actual nearby generator drops through the ordinary pickup event. */
final class BedwarsBotResources {
    private BedwarsBotResources() {}
    static void collect(GameInstance game,Player bot) {
        int inspected=0;
        for(Entity entity:game.world().getNearbyEntities(bot.getLocation(),1.5,1.5,1.5)) {
            if(++inspected>32) break;
            if(entity instanceof Item item) pickup(game,bot,item);
        }
    }
    static int pickup(GameInstance game,Player bot,Item item) {
        if(!valid(game,bot,item)) return 0;
        var stack=item.getItemStack();
        if(!currency(stack.getType())) return 0;
        int capacity=0;
        for(int slot=0;slot<36;slot++) {
            var existing=bot.getInventory().getItem(slot);
            if(existing==null || existing.getType()==Material.AIR) capacity+=Math.max(1,stack.getMaxStackSize());
            else if(existing.isSimilar(stack)) capacity+=Math.max(0,stack.getMaxStackSize()-existing.getAmount());
        }
        if(capacity==0) return 0;
        var event=new EntityPickupItemEvent(bot,item,Math.max(0,stack.getAmount()-capacity));
        game.plugin().getServer().getPluginManager().callEvent(event);
        if(event.isCancelled() || !valid(game,bot,item)) return 0;
        stack=item.getItemStack().clone(); // A protection listener may have changed or removed the original stack.
        if(!currency(stack.getType())) return 0;
        int remaining=bot.getInventory().addItem(stack.clone()).values().stream().mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
        int picked=Math.max(0,stack.getAmount()-remaining);
        if(picked==0) return 0;
        if(remaining==0) item.remove();
        else { stack.setAmount(remaining); item.setItemStack(stack); }
        return picked;
    }
    private static boolean valid(GameInstance game,Player bot,Item item) {
        return game.state()==GameState.RUNNING && !game.frozen() && game.isActive(bot) && game.isBot(bot)
                && item.isValid() && !item.isDead() && item.canPlayerPickup() && item.getPickupDelay()<=0
                && item.getWorld()==bot.getWorld() && bot.getWorld()==game.world()
                && item.getLocation().distanceSquared(bot.getLocation())<=2.25 && bot.hasLineOfSight(item)
                && (item.getOwner()==null || item.getOwner().equals(bot.getUniqueId()));
    }
    private static boolean currency(Material material) { return material==Material.IRON_INGOT || material==Material.GOLD_INGOT || material==Material.DIAMOND; }
}
