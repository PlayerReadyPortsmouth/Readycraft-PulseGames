package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.*;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.function.Predicate;

/** One owned placement, backed by a finite held stack and ordinary protection events. */
public final class IslandBotPlacement {
    private IslandBotPlacement() {}
    public static boolean place(GameInstance game,Player bot,Block target,Block support,int slot,Predicate<Block> rule) {
        if(slot<0 || slot>=36 || game.state()!=GameState.RUNNING || game.frozen() || !game.isActive(bot)
                || bot.getWorld()!=game.world() || !game.world().isChunkLoaded(target.getX()>>4,target.getZ()>>4)
                || !SkywarsBotTerrain.air(target.getType()) || support.isPassable()) return false;
        Location centre=target.getLocation().add(.5,.5,.5),eye=bot.getEyeLocation();
        if(eye.distanceSquared(centre)>4.5*4.5) return false;
        var border=game.world().getWorldBorder();
        if(border==null || !border.isInside(centre) || target.getY()<game.world().getMinHeight() || target.getY()>=game.world().getMaxHeight()) return false;
        var box=new org.bukkit.util.BoundingBox(target.getX(),target.getY(),target.getZ(),target.getX()+1,target.getY()+1,target.getZ()+1);
        if(game.world().getNearbyEntities(box).stream().anyMatch(e->e instanceof LivingEntity && e.getBoundingBox().overlaps(box))) return false;
        if(!SkywarsBotTerrain.loadedSegment(eye,centre)) return false;
        var direction=centre.toVector().subtract(eye.toVector());
        var hit=game.world().rayTraceBlocks(eye,direction.clone().normalize(),direction.length(),FluidCollisionMode.NEVER,true);
        if(hit!=null && hit.getHitBlock()!=support && hit.getHitPosition().distanceSquared(eye.toVector())<direction.lengthSquared()-.01) return false;
        if(slot>=9) {
            ItemStack displaced=bot.getInventory().getItem(7);
            bot.getInventory().setItem(7,bot.getInventory().getItem(slot)); bot.getInventory().setItem(slot,displaced); slot=7;
        }
        bot.getInventory().setHeldItemSlot(slot);
        ItemStack stack=bot.getInventory().getItem(slot);
        if(stack==null || stack.getAmount()<1 || !rule.test(target)) return false;
        ItemStack held=stack.clone(); var before=target.getState(); target.setType(stack.getType(),false);
        var event=new BlockPlaceEvent(target,before,support,held,bot,true,EquipmentSlot.HAND);
        boolean accepted=false;
        try {
            game.plugin().getServer().getPluginManager().callEvent(event);
            ItemStack remaining=bot.getInventory().getItem(slot);
            if(event.isCancelled() || !event.canBuild() || !game.isActive(bot) || game.frozen()
                    || remaining==null || !remaining.isSimilar(held) || remaining.getAmount()!=held.getAmount()
                    || bot.getInventory().getHeldItemSlot()!=slot || bot.getWorld()!=game.world()
                    || game.state()!=GameState.RUNNING || target.getType()!=held.getType()) return false;
            if(remaining.getAmount()==1) bot.getInventory().setItem(slot,null);
            else { ItemStack reduced=remaining.clone(); reduced.setAmount(reduced.getAmount()-1); bot.getInventory().setItem(slot,reduced); }
            bot.swingMainHand(); accepted=true; return true;
        } finally {
            if(!accepted) before.update(true,false);
            SkywarsBotInventory.equip(bot.getInventory());
        }
    }
}
