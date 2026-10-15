package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import io.papermc.paper.entity.TeleportFlag;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.common.CheckpointRaceGame;
import uk.co.playerready.pulsegames.games.common.RaceGateCrossing;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Server-controlled karts. Armor stands are only seats/model anchors, never movement physics. */
public final class PulseKartsGame extends CheckpointRaceGame {
    static final String MODEL = "readycraft:racing_kart";
    enum Item { SHELL, TURBO, LIGHTNING }
    static final class Kart {
        final Player driver; final ArmorStand seat; AutoCloseable visual;
        Set<UUID> viewers=Set.of();
        KartPhysics.State motion;
        KartPhysics.Input input = new KartPhysics.Input(0,0);
        long boostUntil, slowUntil, boxReady, resetReady;
        Item item;
        boolean insideBox;
        Kart(Player driver, ArmorStand seat, AutoCloseable visual, KartPhysics.State motion) {
            this.driver=driver;this.seat=seat;this.visual=visual;this.motion=motion;
        }
    }
    private final Map<UUID,Kart> karts = new HashMap<>();
    private List<Cuboid> itemBoxes=List.of();
    private KartPhysics.CollisionSpace space;
    private long tick;
    private boolean closed;

    public PulseKartsGame(GameInstance game) { super(game); }
    @Override protected boolean sweptCheckpoints() { return true; }
    @Override protected int laps() { return game.mode().id().equals("cup") ? Math.max(1,game.arena().settings().getInt("laps",3)) : 1; }
    @Override public String startProblem(List<Player> players) {
        if(game.plugin().content()==null)return "PulseContent is required for kart models.";
        return game.plugin().content().kartStartProblem(MODEL,players);
    }
    @Override public void onStart() {
        super.onStart();space=KartPhysics.world(game.world());itemBoxes=game.arena().regionsByPrefix("itembox");
        game.alivePlayers().forEach(this::giveKart);
        game.broadcast("<yellow>Accelerate forwards, brake/reverse backwards, steer left/right. Right-click to use your item; compass resets your kart.");
        game.runRepeating(1,1,()->{
            try { drive(); }
            catch(RuntimeException | LinkageError failure) {
                game.plugin().getLogger().log(java.util.logging.Level.SEVERE,"Kart controller failed",failure);
                onCleanup();game.end(List.of(),"kart-controller-error");
            }
        });
    }
    static KartPhysics.State at(Location at) { return new KartPhysics.State(at.getX(),at.getY(),at.getZ(),at.getYaw(),0,0,false); }
    private Location location(KartPhysics.State state) { return new Location(game.world(),state.x(),state.y(),state.z(),(float)state.yaw(),0); }
    private Set<UUID> viewers() {
        return game.everyone().stream().filter(p->!game.isBot(p) && p.isOnline() && p.getWorld().equals(game.world()))
                .map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
    }
    private void giveKart(Player player) {
        ArmorStand seat=game.world().spawn(player.getLocation(),ArmorStand.class,e->{
            e.setVisible(false);e.setSmall(true);e.setMarker(true);e.setGravity(false);e.setInvulnerable(true);e.setPersistent(false);e.setSilent(true);e.setCollidable(false);
        });
        AutoCloseable visual=null;
        try {
            visual=game.plugin().content().attachKart(MODEL,seat,player,
                    viewers());
            if(!seat.addPassenger(player))throw new IllegalStateException("Could not mount kart driver");
            Kart kart=new Kart(player,seat,visual,at(seat.getLocation()));kart.viewers=viewers();
            karts.put(player.getUniqueId(),kart);
            player.getInventory().clear();
            ItemStack reset=new ItemStack(Material.COMPASS);reset.editMeta(m->m.displayName(Text.mm("<yellow>Reset kart <gray>(right-click)")));
            player.getInventory().setItem(8,reset);
        } catch(RuntimeException | LinkageError failure) {
            if(visual!=null)try{visual.close();}catch(Exception ignored){} seat.remove();throw failure;
        }
    }
    @Override public void onInput(Player player,Input input) {
        Kart kart=karts.get(player.getUniqueId());if(kart==null)return;
        kart.input=new KartPhysics.Input((input.isForward()?1:0)-(input.isBackward()?1:0),
                (input.isRight()?1:0)-(input.isLeft()?1:0));
    }
    private void drive() {
        if(closed || game.state()!=GameState.RUNNING)return;tick++;
        for(Kart kart:List.copyOf(karts.values())) {
            Player player=kart.driver;
            if(!game.isActive(player))continue;
            if(!kart.seat.isValid())throw new IllegalStateException("Kart seat disappeared");
            if(tick%20==0) {
                Set<UUID> audience=viewers();
                if(!audience.equals(kart.viewers) || !game.plugin().content().kartActive(kart.seat)) {
                    try {kart.visual.close();}catch(Exception failure){throw new IllegalStateException("Kart visual refresh failed",failure);}
                    kart.visual=game.plugin().content().attachKart(MODEL,kart.seat,player,audience);kart.viewers=audience;
                }
            }
            if(player.getVehicle()!=kart.seat) { reset(kart);continue; }
            player.setFallDistance(0);
            if(game.frozen())continue;
            Location from=location(kart.motion);
            double multiplier=speedMultiplier(tick,kart.boostUntil,kart.slowUntil);
            KartPhysics.CollisionSpace obstacles=(x,y,z,width,height)->space.clear(x,y,z,width,height)
                    && karts.values().stream().filter(other->other!=kart)
                    .noneMatch(other->Math.abs(y-other.motion.y())<height
                            && Math.abs(x-other.motion.x())<width && Math.abs(z-other.motion.z())<width);
            KartPhysics.State next=KartPhysics.tick(kart.motion,kart.input,multiplier,obstacles);
            Location to=location(next);
            if(to.getY()<voidY()) { reset(kart);continue; }
            if(!kart.seat.teleport(to,TeleportFlag.EntityState.RETAIN_PASSENGERS)) {reset(kart);continue;}
            kart.motion=next;
            super.onMove(player,from,to);
            if(closed || game.state()!=GameState.RUNNING)return;
            collect(kart,from,to);
        }
    }
    static double speedMultiplier(long tick,long boost,long slow) {return (tick<boost?1.55:1)*(tick<slow?.4:1);}
    // Only controller-authoritative mounted motion advances the race; walking cannot count.
    @Override public void onMove(Player player,Location from,Location to) {}
    private void collect(Kart kart,Location from,Location to) {
        boolean inside=itemBoxes.stream().anyMatch(b->b.contains(to));
        boolean crossed=itemBoxes.stream().anyMatch(b->!Double.isNaN(RaceGateCrossing.entry(b,from,to)));
        if(!kart.insideBox && crossed && tick>=kart.boxReady && kart.item==null) {
            kart.boxReady=tick+100;kart.item=Item.values()[ThreadLocalRandom.current().nextInt(Item.values().length)];
            showItem(kart);kart.driver.playSound(to,"minecraft:entity.item.pickup",1,.8f);
        }
        kart.insideBox=inside;
    }
    private void showItem(Kart kart) {
        if(kart.item==null){kart.driver.getInventory().setItem(0,null);return;}
        Material material=switch(kart.item){case SHELL->Material.SNOWBALL;case TURBO->Material.SUGAR;case LIGHTNING->Material.NETHER_STAR;};
        ItemStack item=new ItemStack(material);item.editMeta(m->m.displayName(Text.mm("<aqua>"+kart.item+" <gray>(right-click)")));
        kart.driver.getInventory().setItem(0,item);
        kart.driver.sendActionBar(Text.mm("<aqua>Power-up: "+kart.item));
    }
    @Override public void onInventoryClick(Player player,InventoryClickEvent event){event.setCancelled(true);}
    @Override public void onInteract(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND || !event.getAction().isRightClick() || event.getItem()==null)return;
        Kart kart=karts.get(event.getPlayer().getUniqueId());if(kart==null)return;
        event.setCancelled(true);
        if(game.frozen() || event.getPlayer().getVehicle()!=kart.seat)return;
        if(event.getItem().getType()==Material.COMPASS){if(tick>=kart.resetReady){kart.resetReady=tick+60;reset(kart);}return;}
        if(event.getPlayer().getInventory().getHeldItemSlot()!=0 || kart.item==null)return;
        Item used=kart.item;kart.item=null;showItem(kart);
        switch(used) {
            case TURBO -> {kart.boostUntil=tick+40;kart.driver.playSound(location(kart.motion),"minecraft:entity.firework_rocket.launch",1,1.2f);}
            case SHELL -> shell(kart);
            case LIGHTNING -> {
                Player target=standings().stream().filter(p->p!=kart.driver && karts.containsKey(p.getUniqueId())).findFirst().orElse(null);
                if(target!=null){Kart other=karts.get(target.getUniqueId());slow(other,60);game.world().strikeLightningEffect(location(other.motion));}
            }
        }
    }
    private void slow(Kart kart,int duration) {
        kart.slowUntil=Math.max(kart.slowUntil,tick+duration);
        kart.driver.playSound(location(kart.motion),"minecraft:entity.player.hurt",1,1);
        kart.driver.sendActionBar(Text.mm("<red>Your kart was hit!"));
    }
    /** Chassis hit detection, including marker seats whose vanilla hitboxes are intentionally absent. */
    private void shell(Kart shooter) {
        Location origin=location(shooter.motion).add(0,.45,0);Vector direction=origin.getDirection().setY(0).normalize();
        var wall=game.world().rayTraceBlocks(origin,direction,24,FluidCollisionMode.ALWAYS,true);
        double range=wall==null?24:wall.getHitPosition().distance(origin.toVector());
        Kart target=null;double nearest=range;
        for(Kart other:karts.values())if(other!=shooter && game.isActive(other.driver)) {
            Vector relative=location(other.motion).add(0,.45,0).toVector().subtract(origin.toVector());
            double along=relative.dot(direction);
            if(along>0 && along<nearest && relative.clone().subtract(direction.clone().multiply(along)).lengthSquared()<=1) {target=other;nearest=along;}
        }
        for(double d=.5;d<nearest;d+=.75)game.world().spawnParticle(Particle.SNOWFLAKE,origin.clone().add(direction.clone().multiply(d)),1,0,0,0,0);
        if(target!=null){slow(target,50);shooter.driver.sendActionBar(Text.mm("<green>Shell hit!"));}
    }
    private boolean safeReset(Location at) {
        return space.clear(at.getX(),at.getY(),at.getZ(),KartPhysics.WIDTH,KartPhysics.HEIGHT)
                && !space.clear(at.getX(),at.getY()-.1,at.getZ(),KartPhysics.WIDTH,KartPhysics.HEIGHT);
    }
    private void reset(Kart kart) {
        Location target=resetLocation(kart.driver);kart.input=new KartPhysics.Input(0,0);kart.boostUntil=0;kart.slowUntil=0;
        if(!safeReset(target))target=game.spawnFor(kart.driver);
        if(!safeReset(target))throw new IllegalStateException("Kart reset/grid point obstructed");
        kart.motion=at(target);
        Location resetPoint=target;kart.insideBox=itemBoxes.stream().anyMatch(box->box.contains(resetPoint));
        if(!kart.seat.teleport(target,TeleportFlag.EntityState.RETAIN_PASSENGERS))throw new IllegalStateException("Kart reset failed");
        if(kart.driver.getVehicle()!=kart.seat && !kart.seat.addPassenger(kart.driver))throw new IllegalStateException("Kart remount failed");
        kart.driver.sendActionBar(Text.mm("<yellow>Back to your checkpoint"));
    }
    @Override protected void sendBack(Player player) {Kart kart=karts.get(player.getUniqueId());if(kart!=null)reset(kart);}
    private void removeKart(Player player) {
        Kart kart=karts.remove(player.getUniqueId());if(kart==null)return;
        try{kart.visual.close();}catch(Exception failure){game.plugin().getLogger().warning("Kart visual cleanup: "+failure.getMessage());}
        finally {kart.seat.eject();kart.seat.remove();}
    }
    @Override public void onQuit(Player player){removeKart(player);super.onQuit(player);}
    @Override public void onEliminated(Player player){removeKart(player);}
    @Override public void onEnd(List<Player> winners){onCleanup();}
    @Override public void onCleanup(){closed=true;for(Kart kart:List.copyOf(karts.values()))removeKart(kart.driver);}
    @Override public int startFreezeSeconds(){return 5;}
    @Override public int timeLimitSeconds(){return game.arena().settings().getInt("time-limit",600);}
}
