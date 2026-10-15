package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import io.papermc.paper.entity.TeleportFlag;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
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
        Player driver; final ArmorStand seat; AutoCloseable visual;
        Set<UUID> viewers=Set.of();
        KartPhysics.State motion;
        KartPhysics.Input input = new KartPhysics.Input(0,0);
        long boostUntil, slowUntil, boxReady, resetReady;
        Item item;
        boolean insideBox;
        boolean selectionClosed = true;
        KartBotDriver bot;
        int waypoint;
        long nextItemTick;
        long spinUntil, bumpImmuneUntil, resetTick = -1;
        KartStyle style = KartStyle.BALANCED;
        Kart(Player driver, ArmorStand seat, AutoCloseable visual, KartPhysics.State motion) {
            this.driver=driver;this.seat=seat;this.visual=visual;this.motion=motion;
        }
    }
    private final class Menu implements InventoryHolder {
        final PulseKartsGame source = PulseKartsGame.this;
        final UUID owner;
        Inventory inventory;
        Menu(UUID owner) { this.owner=owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private final Map<UUID,Kart> karts = new HashMap<>();
    private List<Cuboid> itemBoxes=List.of();
    private KartPhysics.CollisionSpace space;
    private KartBotRoute botRoute;
    private List<Location> botPoints = List.of();
    private long tick;
    private boolean closed;

    public PulseKartsGame(GameInstance game) { super(game); }
    @Override protected boolean sweptCheckpoints() { return true; }
    @Override protected int laps() { return game.mode().id().equals("cup") ? Math.max(1,game.arena().settings().getInt("laps",3)) : 1; }
    @Override public String startProblem(List<Player> players) {
        if (players.stream().anyMatch(game::isBot)) {
            String routeProblem = KartBotRoute.validationError(game.arena());
            if (routeProblem != null) return "Kart bot route: " + routeProblem;
        }
        if(game.plugin().content()==null)return "PulseContent is required for kart models.";
        return game.plugin().content().kartStartProblem(MODEL,players);
    }
    @Override public void onStart() {
        super.onStart();space=KartPhysics.world(game.world());itemBoxes=game.arena().regionsByPrefix("itembox");
        if (game.alivePlayers().stream().anyMatch(game::isBot)) {
            botRoute = KartBotRoute.load(game.arena(), game.world());
            botPoints = botRoute.points();
        }
        game.alivePlayers().forEach(this::giveKart);
        game.alivePlayers().stream().filter(p -> !game.isBot(p)).forEach(this::openStyleMenu);
        game.broadcast("<aqua>Choose your kart style during the 15-second grid countdown. Balanced is selected by default.");
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
            if (game.isBot(player)) {
                kart.bot = new KartBotDriver(player.getUniqueId());
                kart.style = KartStyle.values()[Math.floorMod(player.getUniqueId().hashCode(),KartStyle.values().length)];
                kart.nextItemTick = 40 + Math.floorMod(player.getUniqueId().hashCode(), 20);
            }
            kart.selectionClosed = false;
            karts.put(player.getUniqueId(),kart);
            player.getInventory().clear();
            ItemStack reset=new ItemStack(Material.COMPASS);reset.editMeta(m->m.displayName(Text.mm("<yellow>Reset kart <gray>(right-click)")));
            player.getInventory().setItem(8,reset);
            if (!game.isBot(player)) player.getInventory().setItem(4,menuItem(Material.MINECART,"<aqua>Choose kart style",List.of("<gray>Choose before the race starts")));
        } catch(RuntimeException | LinkageError failure) {
            if(visual!=null)try{visual.close();}catch(Exception ignored){} seat.remove();throw failure;
        }
    }
    @Override public void onInput(Player player,Input input) {
        Kart kart=karts.get(player.getUniqueId());if(kart==null || kart.bot!=null)return;
        kart.input=new KartPhysics.Input((input.isForward()?1:0)-(input.isBackward()?1:0),
                (input.isRight()?1:0)-(input.isLeft()?1:0));
    }
    private void drive() {
        if(closed || game.state()!=GameState.RUNNING)return;tick++;
        Map<UUID,KartPhysics.State> previous = new HashMap<>();
        karts.forEach((id,kart) -> previous.put(id,kart.motion));
        for(Kart kart:List.copyOf(karts.values())) {
            if (kart.bot != null) {
                // Citizens may replace its Player during skin loading; retain UUID-owned chassis.
                Player current = game.plugin().bots().player(kart.driver.getUniqueId());
                if (current != null && current != kart.driver) {
                    kart.driver = current;
                    reset(kart);
                    try { kart.visual.close(); } catch (Exception failure) { throw new IllegalStateException("Kart visual refresh failed",failure); }
                    kart.visual = game.plugin().content().attachKart(MODEL,kart.seat,current,viewers());
                    kart.viewers = viewers();
                }
            }
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
            if (!kart.selectionClosed && kart.bot == null) {
                kart.selectionClosed = true;
                closeStyleMenu(player);
                player.getInventory().setItem(4,null);
            }
            if (tick >= kart.spinUntil && kart.bot != null && controlBot(kart)) continue;
            Location from=location(kart.motion);
            double multiplier=speedMultiplier(tick,kart.boostUntil,kart.slowUntil);
            // Karts may pass side by side; rear impacts are resolved after every chassis moves.
            KartPhysics.Input input = kart.input;
            if (tick < kart.spinUntil) {
                float yaw = kart.motion.yaw();
                for (int degree=0; degree<36; degree++) {
                    if (!space.clearAt(kart.motion.x(),kart.motion.y(),kart.motion.z(),yaw+1)) break;
                    yaw++;
                }
                kart.motion = new KartPhysics.State(kart.motion.x(),kart.motion.y(),kart.motion.z(),yaw,
                        kart.motion.speed(),kart.motion.verticalSpeed(),kart.motion.grounded());
                input = new KartPhysics.Input(0,0);
            }
            KartPhysics.State next=KartPhysics.tick(kart.motion,input,multiplier,space,kart.style);
            Location to=location(next);
            if(to.getY()<voidY()) { reset(kart);continue; }
            if(!kart.seat.teleport(to,TeleportFlag.EntityState.RETAIN_PASSENGERS)) {reset(kart);continue;}
            kart.motion=next;
            super.onMove(player,from,to);
            if(closed || game.state()!=GameState.RUNNING)return;
            collect(kart,from,to);
        }
        if (!game.frozen()) applyBumps(previous);
    }
    private void applyBumps(Map<UUID,KartPhysics.State> previous) {
        for (Kart victim : karts.values()) {
            if (tick < victim.bumpImmuneUntil || victim.resetTick == tick || !game.isActive(victim.driver)) continue;
            KartPhysics.State beforeVictim = previous.get(victim.driver.getUniqueId());
            if (beforeVictim == null) continue;
            for (Kart attacker : karts.values()) {
                if (attacker == victim || attacker.resetTick == tick || tick < attacker.spinUntil || !game.isActive(attacker.driver)) continue;
                KartPhysics.State beforeAttacker = previous.get(attacker.driver.getUniqueId());
                if (beforeAttacker != null && KartBumps.rearImpact(beforeAttacker,attacker.motion,beforeVictim,victim.motion)) {
                    int duration = (int)Math.round(20 * attacker.style.bumpPower() * victim.style.incomingDurationMultiplier());
                    victim.slowUntil = Math.max(victim.slowUntil,tick+duration);
                    victim.spinUntil = tick+11;
                    victim.bumpImmuneUntil = tick+60;
                    var m=victim.motion;
                    victim.motion = new KartPhysics.State(m.x(),m.y(),m.z(),m.yaw(),m.speed()*.45,m.verticalSpeed(),m.grounded());
                    victim.driver.sendActionBar(Text.mm("<red>Bumped! <gold>3 seconds of bump protection"));
                    victim.driver.playSound(location(m),"minecraft:entity.player.hurt",1,.8f);
                    attacker.driver.sendActionBar(Text.mm("<green>Bumper hit!"));
                    break;
                }
            }
        }
    }
    /** Bots submit the same bounded throttle/steering input as human drivers. */
    private boolean controlBot(Kart kart) {
        Location here = location(kart.motion);
        Location target = botPoints.get(kart.waypoint);
        // Advance only in route order. Checkpoints still use actual chassis crossings.
        if (here.distanceSquared(target) < KartBotDriver.ARRIVAL_RADIUS * KartBotDriver.ARRIVAL_RADIUS) {
            Progress progressNow = progress.get(kart.driver.getUniqueId());
            if (progressNow != null && botRoute.gateWaypoint(progressNow.nextCheckpoint) == kart.waypoint) {
                // We reached a segment endpoint without crossing its required gate.
                reset(kart);
                return true;
            }
            kart.waypoint = (kart.waypoint + 1) % botPoints.size();
            target = botPoints.get(kart.waypoint);
        }
        Location following = botPoints.get((kart.waypoint + 1) % botPoints.size());
        BotDifficulty difficulty = game.plugin().bots().difficultyFor(game, null);
        var decision = kart.bot.update(kart.motion, point(target), point(following), difficulty, space,
                karts.values().stream().filter(other -> other != kart).map(other -> other.motion).toList());
        if (decision.reset()) { reset(kart); return true; }
        kart.input = decision.input();
        if (kart.item != null && tick >= kart.nextItemTick) {
            boolean use = switch (kart.item) {
                case TURBO -> decision.useTurboSafe();
                case SHELL -> shellTargetAhead(kart);
                case LIGHTNING -> karts.values().stream().anyMatch(other -> other != kart && game.isActive(other.driver));
            };
            if (use) useItem(kart);
            kart.nextItemTick = tick + switch (difficulty) { case EASY -> 40; case NORMAL -> 20; case HARD -> 10; };
        }
        return false;
    }
    private static KartBotDriver.Point point(Location at) { return new KartBotDriver.Point(at.getX(),at.getY(),at.getZ()); }
    private boolean shellTargetAhead(Kart kart) {
        Location origin = location(kart.motion).add(0,.45,0);
        Vector direction = origin.getDirection().setY(0).normalize();
        var wall = game.world().rayTraceBlocks(origin,direction,24,FluidCollisionMode.ALWAYS,true);
        double range = wall == null ? 24 : wall.getHitPosition().distance(origin.toVector());
        return karts.values().stream().filter(other -> other != kart && game.isActive(other.driver)).anyMatch(other -> {
            Vector relative = location(other.motion).add(0,.45,0).toVector().subtract(origin.toVector());
            double along = relative.dot(direction);
            return along > 0 && along < range && relative.subtract(direction.clone().multiply(along)).lengthSquared() <= 1;
        });
    }
    public String botStatus(Player bot) {
        Kart kart = karts.get(bot.getUniqueId());
        return kart == null || kart.bot == null ? "Waiting for kart" :
                "Waypoint " + (kart.waypoint + 1) + "/" + botPoints.size() + (kart.bot.recovering() ? " (recovering)" : "");
    }
    static double speedMultiplier(long tick,long boost,long slow) {return (tick<boost?1.55:1)*(tick<slow?.4:1);}
    // Only controller-authoritative mounted motion advances the race; walking cannot count.
    @Override public void onMove(Player player,Location from,Location to) {}
    private void collect(Kart kart,Location from,Location to) {
        boolean inside=itemBoxes.stream().anyMatch(b->b.contains(to));
        boolean crossed=itemBoxes.stream().anyMatch(b->!Double.isNaN(RaceGateCrossing.entry(b,from,to)));
        if(!kart.insideBox && crossed && tick>=kart.boxReady && kart.item==null) {
            kart.boxReady=tick+100;kart.item=pickItem(kart.style,ThreadLocalRandom.current().nextInt(100));
            showItem(kart);kart.driver.playSound(to,"minecraft:entity.item.pickup",1,.8f);
        }
        kart.insideBox=inside;
    }
    static Item pickItem(KartStyle style,int roll) {
        int value=Math.floorMod(roll,100);
        if (style == KartStyle.TRICKSTER) return value < 15 ? Item.SHELL : value < 60 ? Item.TURBO : Item.LIGHTNING;
        return value < 34 ? Item.SHELL : value < 67 ? Item.TURBO : Item.LIGHTNING;
    }
    private void showItem(Kart kart) {
        if(kart.item==null){kart.driver.getInventory().setItem(0,null);return;}
        Material material=switch(kart.item){case SHELL->Material.SNOWBALL;case TURBO->Material.SUGAR;case LIGHTNING->Material.NETHER_STAR;};
        ItemStack item=new ItemStack(material);item.editMeta(m->m.displayName(Text.mm("<aqua>"+kart.item+" <gray>(right-click)")));
        kart.driver.getInventory().setItem(0,item);
        kart.driver.sendActionBar(Text.mm("<aqua>Power-up: "+kart.item));
    }
    private boolean choosingStyle() { return !closed && game.state()==GameState.RUNNING && game.frozen(); }
    private static ItemStack menuItem(Material material,String name,List<String> lore) {
        ItemStack item=new ItemStack(material);
        item.editMeta(meta->{meta.displayName(Text.mm(name));meta.lore(lore.stream().map(Text::mm).toList());});return item;
    }
    private void openStyleMenu(Player player) {
        Kart kart=karts.get(player.getUniqueId());if(kart==null || !choosingStyle() || !game.isActive(player))return;
        Menu menu=new Menu(player.getUniqueId());menu.inventory=Bukkit.createInventory(menu,9,Text.mm("<aqua>PulseKarts · choose your style"));
        populateStyleMenu(menu,kart);
        player.openInventory(menu.inventory);
    }
    private void populateStyleMenu(Menu menu,Kart kart) {
        Material[] icons={Material.MINECART,Material.FEATHER,Material.ANVIL,Material.NETHER_STAR};
        for (int i=0;i<KartStyle.values().length;i++) {
            KartStyle style=KartStyle.values()[i];
            menu.inventory.setItem(1+i*2,menuItem(icons[i],(kart.style==style?"<green>Selected: ":"<aqua>")+style.displayName(),
                    List.of("<gray>"+style.description(),"<yellow>Click to select")));
        }
    }
    @Override public void onInventoryClick(Player player,InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu) || menu.source!=this
                || !menu.owner.equals(player.getUniqueId()) || !choosingStyle() || !game.isActive(player))return;
        int slot=event.getRawSlot();
        if (slot>=1 && slot<=7 && slot%2==1) {
            if (chooseStyle(player,KartStyle.values()[(slot-1)/2])) populateStyleMenu(menu,karts.get(player.getUniqueId()));
        }
    }
    boolean chooseStyle(Player player,KartStyle style) {
        Kart kart=karts.get(player.getUniqueId());
        if (kart==null || kart.bot!=null || !choosingStyle() || !game.isActive(player)) return false;
        kart.style=Objects.requireNonNull(style);return true;
    }
    @Override public List<String> sidebar(Player player) {
        List<String> lines=new ArrayList<>(super.sidebar(player));
        Kart kart=karts.get(player.getUniqueId());
        if(kart!=null) lines.add("<gray>Kart: <white>"+kart.style.displayName());
        return lines;
    }
    @Override public void onInventoryDrag(Player player,InventoryDragEvent event) { event.setCancelled(true); }
    @Override public void onInteract(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND || !event.getAction().isRightClick() || event.getItem()==null)return;
        Kart kart=karts.get(event.getPlayer().getUniqueId());if(kart==null)return;
        event.setCancelled(true);
        if (event.getItem().getType()==Material.MINECART && event.getPlayer().getInventory().getHeldItemSlot()==4) {
            openStyleMenu(event.getPlayer());return;
        }
        if(game.frozen() || event.getPlayer().getVehicle()!=kart.seat || tick<kart.spinUntil)return;
        if(event.getItem().getType()==Material.COMPASS){if(tick>=kart.resetReady){kart.resetReady=tick+60;reset(kart);}return;}
        if(event.getPlayer().getInventory().getHeldItemSlot()!=0 || kart.item==null)return;
        useItem(kart);
    }
    private void useItem(Kart kart) {
        Item used=kart.item;if(used==null)return;kart.item=null;showItem(kart);
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
        return space.clearAt(at.getX(),at.getY(),at.getZ(),at.getYaw())
                && !space.clearAt(at.getX(),at.getY()-.1,at.getZ(),at.getYaw());
    }
    private void reset(Kart kart) {
        kart.resetTick=tick;kart.spinUntil=0;kart.bumpImmuneUntil=Math.max(kart.bumpImmuneUntil,tick+40);
        Location target=resetLocation(kart.driver);kart.input=new KartPhysics.Input(0,0);kart.boostUntil=0;kart.slowUntil=0;
        boolean gridFallback = !safeReset(target);
        if(gridFallback)target=game.spawnFor(kart.driver);
        if(!safeReset(target))throw new IllegalStateException("Kart reset/grid point obstructed");
        kart.motion=at(target);
        if (kart.bot != null) {
            kart.bot.reset();
            Progress p = progress.get(kart.driver.getUniqueId());
            kart.waypoint = gridFallback || p == null || p.nextCheckpoint == 0 ? 0 : botRoute.gateWaypoint(p.nextCheckpoint - 1);
        }
        Location resetPoint=target;kart.insideBox=itemBoxes.stream().anyMatch(box->box.contains(resetPoint));
        if(!kart.seat.teleport(target,TeleportFlag.EntityState.RETAIN_PASSENGERS))throw new IllegalStateException("Kart reset failed");
        if(kart.driver.getVehicle()!=kart.seat && !kart.seat.addPassenger(kart.driver))throw new IllegalStateException("Kart remount failed");
        kart.driver.sendActionBar(Text.mm("<yellow>Back to your checkpoint"));
    }
    @Override protected void sendBack(Player player) {Kart kart=karts.get(player.getUniqueId());if(kart!=null)reset(kart);}
    private void closeStyleMenu(Player player) {
        var view=player.getOpenInventory();
        if(view!=null && view.getTopInventory().getHolder() instanceof Menu menu && menu.source==this) player.closeInventory();
    }
    private void removeKart(Player player) {
        Kart kart=karts.remove(player.getUniqueId());if(kart==null)return;
        closeStyleMenu(kart.driver);
        try{kart.visual.close();}catch(Exception failure){game.plugin().getLogger().warning("Kart visual cleanup: "+failure.getMessage());}
        finally {kart.seat.eject();kart.seat.remove();}
    }
    @Override public void onQuit(Player player){removeKart(player);super.onQuit(player);}
    @Override public void onEliminated(Player player){removeKart(player);}
    @Override public void onEnd(List<Player> winners){onCleanup();}
    @Override public void onCleanup(){closed=true;for(Kart kart:List.copyOf(karts.values()))removeKart(kart.driver);}
    @Override public int startFreezeSeconds(){return 15;}
    @Override public int timeLimitSeconds(){return game.arena().settings().getInt("time-limit",600);}
}
