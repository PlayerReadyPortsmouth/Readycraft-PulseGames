package uk.co.playerready.pulsegames.games.duels;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.games.skywars.IslandBotPlacement;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

/** Finite kit actions. Damage, explosions, wind lift and mace smash remain vanilla server physics. */
final class DuelBotCombat {
    private final GameInstance game;
    private final DuelsGame duels;
    private final IntUnaryOperator random;
    private final Map<UUID,Long> nextAction = new HashMap<>(), nextLaunch = new HashMap<>();
    private final Map<UUID,String> status = new HashMap<>();
    DuelBotCombat(GameInstance game, DuelsGame duels) { this(game,duels,bound->ThreadLocalRandom.current().nextInt(bound)); }
    DuelBotCombat(GameInstance game, DuelsGame duels, IntUnaryOperator random) { this.game=game;this.duels=duels;this.random=random; }
    void clear() { nextAction.clear(); nextLaunch.clear(); status.clear(); }
    void forget(Player bot) { nextAction.remove(bot.getUniqueId()); nextLaunch.remove(bot.getUniqueId()); status.remove(bot.getUniqueId()); }
    String status(Player bot) { return status.getOrDefault(bot.getUniqueId(),"Seeking a visible opponent"); }
    Location objective(Player bot,Location proposed) {
        if (game.mode().rulesId().equals("mace") && !bot.isOnGround()) return null;
        return proposed;
    }
    boolean combat(Player bot,LivingEntity enemy,long tick,BotDifficulty difficulty) {
        String mode=game.mode().rulesId();
        if(!mode.equals("crystal") && !mode.equals("mace")) return false;
        if(!(enemy instanceof Player target) || !eligible(bot) || !game.isActive(target) || !duels.acceptsDeath(target)
                || target.getWorld()!=bot.getWorld() || !bot.hasLineOfSight(target)
                || (game.teamOf(bot)!=null && game.teamOf(bot).contains(target))
                || bot.getLocation().distanceSquared(target.getLocation())>24*24) return true;
        UUID id=bot.getUniqueId();
        if(tick<nextAction.computeIfAbsent(id,ignored->tick+difficulty.meleeDelayTicks())) return true;
        nextAction.put(id,tick+difficulty.meleeDelayTicks());
        if(mode.equals("mace")) mace(bot,target,tick,difficulty); else crystal(bot,target,difficulty);
        return true;
    }
    private static boolean air(Material material) { return material==Material.AIR || material==Material.CAVE_AIR || material==Material.VOID_AIR; }
    private boolean eligible(Player bot) {
        return game.state()==GameState.RUNNING && !game.frozen() && game.isBot(bot) && game.isActive(bot)
                && duels.acceptsDeath(bot) && bot.getWorld()==game.world();
    }
    private void mace(Player bot,Player target,long tick,BotDifficulty difficulty) {
        int mace=select(bot,Material.MACE);
        if(mace<0) { status.put(bot.getUniqueId(),"No mace in this loadout"); return; }
        double distance=bot.getLocation().distanceSquared(target.getLocation());
        if(!bot.isOnGround()) {
            status.put(bot.getUniqueId(),"Descending for a vanilla mace strike");
            if(bot.getVelocity().getY()<-.08 && bot.getFallDistance()>1.5 && distance<=2.8*2.8) strike(bot,target,difficulty);
            return;
        }
        if(distance>=1 && distance<=4*4 && tick>=nextLaunch.getOrDefault(bot.getUniqueId(),0L)
                && find(bot,Material.WIND_CHARGE)>=0 && safeLaunch(bot.getLocation())) {
            nextLaunch.put(bot.getUniqueId(),tick+Math.max(40,difficulty.projectileDelayTicks()*3L));
            if(launchWind(bot)) { status.put(bot.getUniqueId(),"Wind charge jump; waiting for real lift"); return; }
        }
        select(bot,Material.MACE);
        status.put(bot.getUniqueId(),"Ground mace combat; no safe charged jump available");
        if(distance<=2.8*2.8) strike(bot,target,difficulty);
    }
    boolean launchWind(Player bot) {
        if(!eligible(bot) || bot.hasCooldown(Material.WIND_CHARGE)) return false;
        int slot=select(bot,Material.WIND_CHARGE);
        if(slot<0) return false;
        ItemStack held=bot.getInventory().getItem(slot).clone();
        var interact=new PlayerInteractEvent(bot,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,held,null,BlockFace.SELF,EquipmentSlot.HAND);
        game.plugin().getServer().getPluginManager().callEvent(interact);
        if(interact.useItemInHand()==Event.Result.DENY || !eligible(bot) || !sameHeld(bot,slot,held)) return false;
        WindCharge charge=bot.launchProjectile(WindCharge.class,new Vector(0,-1.5,0));
        if(!charge.isValid()) return false;
        if(!eligible(bot) || !sameHeld(bot,slot,held)) { charge.remove();return false; }
        consume(bot,slot,held); bot.setCooldown(Material.WIND_CHARGE,10); bot.swingMainHand();
        select(bot,Material.MACE);
        return true;
    }
    private void crystal(Player bot,Player target,BotDifficulty difficulty) {
        if(equipTotem(bot)) { status.put(bot.getUniqueId(),"Equipping a finite off-hand totem"); return; }
        selectSword(bot);
        // Only registered round crystals; never acquire an opponent through walls or global entity scans.
        EnderCrystal best=null;double bestDistance=Double.POSITIVE_INFINITY;
        for(UUID id:duels.botCrystals()) {
            var entity=game.world().getEntity(id);
            if(!(entity instanceof EnderCrystal crystal) || !crystal.isValid()) continue;
            double distance=bot.getEyeLocation().distanceSquared(crystal.getLocation().add(0,.5,0));
            if(distance<bestDistance && distance<=3*3 && bot.hasLineOfSight(crystal) && safeExplosion(bot,target,crystal.getLocation())) {
                best=crystal; bestDistance=distance;
            }
        }
        if(best!=null) {
            status.put(bot.getUniqueId(),"Breaking a safely positioned crystal");
            if(random.applyAsInt(100)<difficulty.accuracyPercent()) { bot.swingMainHand();bot.attack(best); }
            return;
        }
        if(find(bot,Material.END_CRYSTAL)>=0 && bot.getHealth()>=8) {
            Block base=findBase(bot,target);
            if(base!=null) {
                if(base.getType()!=Material.OBSIDIAN && base.getType()!=Material.BEDROCK) {
                    int slot=find(bot,Material.OBSIDIAN);
                    var previous = base.getState();
                    if(slot>=0 && IslandBotPlacement.place(game,bot,base,base.getRelative(BlockFace.DOWN),slot,
                            block->duels.botCombatArea(block.getLocation()))) {
                        duels.rememberBotBlock(base, previous);
                        status.put(bot.getUniqueId(),"Placed one obsidian base from inventory"); return;
                    }
                } else if(placeCrystal(bot,base)) {
                    status.put(bot.getUniqueId(),"Placed one crystal; reassessing blast safety"); return;
                }
            }
        }
        status.put(bot.getUniqueId(),"Sword fallback; waiting for a safe crystal position");
        selectSword(bot);
        if(bot.getLocation().distanceSquared(target.getLocation())<=2.8*2.8) strike(bot,target,difficulty);
    }
    private Block findBase(Player bot,Player target) {
        var here=bot.getLocation(); Block best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(int x=here.getBlockX()-3;x<=here.getBlockX()+3;x++) for(int z=here.getBlockZ()-3;z<=here.getBlockZ()+3;z++) {
            if(!game.world().isChunkLoaded(x>>4,z>>4)) continue;
            for(int y=here.getBlockY()-1;y<=here.getBlockY();y++) {
                Block base=game.world().getBlockAt(x,y,z);
                if(base.getType()!=Material.OBSIDIAN && base.getType()!=Material.BEDROCK && !air(base.getType())) continue;
                if(!duels.botCombatArea(base.getLocation()) || !clearCrystalSpace(base)) continue;
                Location position=base.getLocation().add(.5,1,.5);
                double reach=bot.getEyeLocation().distanceSquared(position);
                if(reach>4.5*4.5 || !safeExplosion(bot,target,position)) continue;
                if(air(base.getType()) && (find(bot,Material.OBSIDIAN)<0 || base.getRelative(BlockFace.DOWN).isPassable())) continue;
                double score=target.getLocation().distanceSquared(position);
                if(score<bestScore) { best=base;bestScore=score; }
            }
        }
        return best;
    }
    private boolean safeExplosion(Player bot,Player target,Location crystal) {
        double own=bot.getLocation().distanceSquared(crystal), opponent=target.getLocation().distanceSquared(crystal);
        if(own<4 || opponent>16 || opponent+1>=own || bot.getHealth()<8) return false;
        var team=game.teamOf(bot);
        return team==null || game.alivePlayers().stream().filter(p->!p.equals(bot)&&team.contains(p))
                .noneMatch(p->p.getWorld()==crystal.getWorld() && p.getLocation().distanceSquared(crystal)<36);
    }
    boolean placeCrystal(Player bot,Block base) {
        if(!eligible(bot) || (base.getType()!=Material.OBSIDIAN && base.getType()!=Material.BEDROCK)
                || !duels.botCombatArea(base.getLocation()) || !clearCrystalSpace(base)) return false;
        Location position=base.getLocation().add(.5,1,.5);
        if(!duels.botCombatArea(position) || bot.getEyeLocation().distanceSquared(position)>4.5*4.5) return false;
        if(!loadedRay(bot.getEyeLocation(),position))return false;
        var ray=position.clone().add(0,-.5,0).toVector().subtract(bot.getEyeLocation().toVector());
        var hit=game.world().rayTraceBlocks(bot.getEyeLocation(),ray.clone().normalize(),ray.length()+.05,FluidCollisionMode.NEVER,true);
        if(hit==null || !base.equals(hit.getHitBlock())) return false;
        int slot=select(bot,Material.END_CRYSTAL); if(slot<0)return false;
        ItemStack held=bot.getInventory().getItem(slot).clone();
        var interact=new PlayerInteractEvent(bot,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,held,base,BlockFace.UP,EquipmentSlot.HAND);
        game.plugin().getServer().getPluginManager().callEvent(interact);
        if(interact.useItemInHand()==Event.Result.DENY || interact.useInteractedBlock()==Event.Result.DENY
                || !eligible(bot) || !sameHeld(bot,slot,held) || !clearCrystalSpace(base)) return false;
        EnderCrystal crystal=game.world().spawn(position,EnderCrystal.class);
        boolean accepted=false;
        try {
            var event=new EntityPlaceEvent(crystal,bot,base,BlockFace.UP,EquipmentSlot.HAND);
            game.plugin().getServer().getPluginManager().callEvent(event);
            if(event.isCancelled() || !eligible(bot) || !sameHeld(bot,slot,held) || !crystal.isValid()) return false;
            // Ordinary listener usually registers it. This is idempotent and also protects test/direct callers.
            duels.onEntityPlace(event);
            if(event.isCancelled()) return false;
            consume(bot,slot,held); bot.swingMainHand(); accepted=true; return true;
        } finally { if(!accepted) { crystal.remove();duels.forgetBotCrystal(crystal.getUniqueId()); } }
    }
    private boolean loadedRay(Location from,Location to) {
        if(from.getWorld()!=to.getWorld())return false;
        Vector delta=to.toVector().subtract(from.toVector());
        int samples=(int)Math.ceil(delta.length()*2);
        if(samples>12)return false;
        for(int i=0;i<=samples;i++) {
            Location point=from.clone().add(delta.clone().multiply((double)i/Math.max(1,samples)));
            if(!from.getWorld().isChunkLoaded(point.getBlockX()>>4,point.getBlockZ()>>4))return false;
        }
        return true;
    }
    private boolean clearCrystalSpace(Block base) {
        var world=base.getWorld();
        if(base.getY()<world.getMinHeight() || base.getY()+2>=world.getMaxHeight()
                || !world.isChunkLoaded(base.getX()>>4,base.getZ()>>4))return false;
        Location centre=base.getLocation().add(.5,1,.5);
        if(!world.getWorldBorder().isInside(centre) || !air(base.getRelative(BlockFace.UP).getType())
                || !air(base.getRelative(BlockFace.UP,2).getType()))return false;
        var box=new BoundingBox(base.getX(),base.getY()+1,base.getZ(),base.getX()+1,base.getY()+3,base.getZ()+1);
        return world.getNearbyEntities(box).isEmpty();
    }
    boolean safeLaunch(Location feet) {
        World world=feet.getWorld();
        if(world==null || feet.getY()+8>=world.getMaxHeight() || !world.getWorldBorder().isInside(feet))return false;
        for(int x=feet.getBlockX()-1;x<=feet.getBlockX()+1;x++) for(int z=feet.getBlockZ()-1;z<=feet.getBlockZ()+1;z++) {
            if(!world.isChunkLoaded(x>>4,z>>4))return false;
            var floor=world.getBlockAt(x,feet.getBlockY()-1,z);
            var shape=floor.getBoundingBox();
            if(floor.isLiquid() || floor.getType()==Material.MAGMA_BLOCK || floor.isPassable()
                    || shape.getMinX()>x || shape.getMaxX()<x+1 || shape.getMinZ()>z || shape.getMaxZ()<z+1
                    || Math.abs(shape.getMaxY()-feet.getY())>.05)return false;
            for(int y=feet.getBlockY();y<=feet.getBlockY()+7;y++) if(!air(world.getBlockAt(x,y,z).getType()))return false;
        }
        return true;
    }
    boolean equipTotem(Player bot) {
        ItemStack off=bot.getInventory().getItemInOffHand();
        if(off!=null && off.getType()==Material.TOTEM_OF_UNDYING)return false;
        int slot=select(bot,Material.TOTEM_OF_UNDYING);if(slot<0)return false;
        ItemStack held=bot.getInventory().getItem(slot).clone();
        ItemStack previous=off==null?null:off.clone();
        var event=new PlayerSwapHandItemsEvent(bot,previous,held);
        game.plugin().getServer().getPluginManager().callEvent(event);
        if(event.isCancelled() || !eligible(bot) || !sameHeld(bot,slot,held)
                || !Objects.equals(off,bot.getInventory().getItemInOffHand()))return false;
        bot.getInventory().setItem(slot,event.getMainHandItem());
        bot.getInventory().setItemInOffHand(event.getOffHandItem());
        return true;
    }
    private void strike(Player bot,Player target,BotDifficulty difficulty) {
        if(bot.getAttackCooldown()>=.9f && random.applyAsInt(100)<difficulty.accuracyPercent()) { bot.swingMainHand();bot.attack(target); }
    }
    private static boolean sameHeld(Player bot,int slot,ItemStack before) {
        ItemStack now=bot.getInventory().getItem(slot);
        return bot.getInventory().getHeldItemSlot()==slot && now!=null && now.isSimilar(before) && now.getAmount()==before.getAmount();
    }
    private static void consume(Player bot,int slot,ItemStack held) {
        if(held.getAmount()<=1)bot.getInventory().setItem(slot,null);
        else { ItemStack next=held.clone();next.setAmount(held.getAmount()-1);bot.getInventory().setItem(slot,next); }
    }
    private static int find(Player bot,Material type) {
        for(int i=0;i<36;i++) {var item=bot.getInventory().getItem(i);if(item!=null && item.getType()==type && item.getAmount()>0)return i;}
        return -1;
    }
    private static int select(Player bot,Material type) {
        int slot=find(bot,type);if(slot<0)return -1;
        if(slot>=9) {ItemStack old=bot.getInventory().getItem(7);bot.getInventory().setItem(7,bot.getInventory().getItem(slot));bot.getInventory().setItem(slot,old);slot=7;}
        bot.getInventory().setHeldItemSlot(slot);return slot;
    }
    private static void selectSword(Player bot) {
        for(Material sword:List.of(Material.NETHERITE_SWORD,Material.DIAMOND_SWORD,Material.IRON_SWORD,Material.STONE_SWORD,Material.WOODEN_SWORD))
            if(select(bot,sword)>=0)return;
    }
}
