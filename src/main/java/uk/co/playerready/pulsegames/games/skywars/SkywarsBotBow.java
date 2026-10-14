package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Shared island-game ranged action: finite real arrows, public sight and standard shooting events. */
public final class SkywarsBotBow {
    private record Draw(UUID target,long ready) {}
    private final Map<UUID,Draw> draws=new HashMap<>();
    public boolean combat(GameInstance game,Player bot,LivingEntity enemy,long tick,BotDifficulty difficulty) {
        if(!(enemy instanceof Player target) || !valid(game,bot,target)) { draws.remove(bot.getUniqueId()); return false; }
        double distance=bot.getLocation().distanceSquared(target.getLocation());
        if(distance<16 || distance>24*24) { draws.remove(bot.getUniqueId()); return false; }
        int bowSlot=find(bot,Material.BOW),ammoSlot=find(bot,Material.ARROW);
        if(bowSlot<0 || ammoSlot<0) { draws.remove(bot.getUniqueId()); return false; }
        if(bowSlot>=9) {
            ItemStack old=bot.getInventory().getItem(7);
            bot.getInventory().setItem(7,bot.getInventory().getItem(bowSlot)); bot.getInventory().setItem(bowSlot,old);
            bowSlot=7; ammoSlot=find(bot,Material.ARROW);
        }
        if(ammoSlot<0) return false;
        bot.getInventory().setHeldItemSlot(bowSlot);
        Draw draw=draws.get(bot.getUniqueId());
        if(draw==null || !draw.target().equals(target.getUniqueId())) {
            draws.put(bot.getUniqueId(),new Draw(target.getUniqueId(),tick+20)); return true;
        }
        if(tick<draw.ready()) return true;
        draws.put(bot.getUniqueId(),new Draw(target.getUniqueId(),tick+Math.max(20,difficulty.projectileDelayTicks())));
        ItemStack bow=bot.getInventory().getItem(bowSlot).clone(), ammo=bot.getInventory().getItem(ammoSlot).clone();
        var vector=target.getEyeLocation().toVector().subtract(bot.getEyeLocation().toVector());
        vector.setY(vector.getY()+Math.sqrt(distance)*.08);
        if(vector.lengthSquared()<.001) return true;
        vector.normalize(); double spread=(100-difficulty.accuracyPercent())/250.0;
        var random=ThreadLocalRandom.current();
        vector.add(new org.bukkit.util.Vector(random.nextDouble(-spread,spread),random.nextDouble(-spread,spread),random.nextDouble(-spread,spread)));
        Arrow arrow=bot.launchProjectile(Arrow.class,vector.normalize().multiply(3));
        if(!arrow.isValid()) return true;
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        var event=new EntityShootBowEvent(bot,bow,ammo,arrow,EquipmentSlot.HAND,1f,true);
        boolean accepted=false;
        try {
            game.plugin().getServer().getPluginManager().callEvent(event);
            ItemStack actualAmmo=bot.getInventory().getItem(ammoSlot),actualBow=bot.getInventory().getItem(bowSlot);
            if(event.isCancelled() || !valid(game,bot,target) || actualAmmo==null || actualBow==null
                    || !actualAmmo.isSimilar(ammo) || actualAmmo.getAmount()!=ammo.getAmount()
                    || !actualBow.isSimilar(bow) || bot.getInventory().getHeldItemSlot()!=bowSlot) return true;
            if(event.shouldConsumeItem()) {
                if(actualAmmo.getAmount()==1) bot.getInventory().setItem(ammoSlot,null);
                else { ItemStack reduced=actualAmmo.clone(); reduced.setAmount(reduced.getAmount()-1); bot.getInventory().setItem(ammoSlot,reduced); }
            }
            if(actualBow.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damage) {
                if(damage.getDamage()+1>=Material.BOW.getMaxDurability()) bot.getInventory().setItem(bowSlot,null);
                else { damage.setDamage(damage.getDamage()+1); actualBow.setItemMeta(damage); }
            }
            if(event.getProjectile()!=arrow) arrow.remove();
            bot.swingMainHand(); accepted=true; return true;
        } finally {
            if(!accepted) { arrow.remove(); if(event.getProjectile()!=arrow) event.getProjectile().remove(); }
        }
    }
    private static boolean valid(GameInstance game,Player bot,Player enemy) {
        return game.state()==GameState.RUNNING && !game.frozen() && game.isBot(bot) && game.isActive(bot)
                && game.isActive(enemy) && bot.getWorld()==enemy.getWorld() && bot.hasLineOfSight(enemy)
                && bot.canSee(enemy) && !enemy.isInvisible() && !enemy.isDead()
                && (game.teamOf(bot)==null || !game.teamOf(bot).contains(enemy));
    }
    private static int find(Player player,Material material) {
        for(int i=0;i<36;i++) { ItemStack item=player.getInventory().getItem(i); if(item!=null && item.getType()==material && item.getAmount()>0) return i; }
        return -1;
    }
    public void clear() { draws.clear(); }
    public void remove(Player bot) { draws.remove(bot.getUniqueId()); }
}
