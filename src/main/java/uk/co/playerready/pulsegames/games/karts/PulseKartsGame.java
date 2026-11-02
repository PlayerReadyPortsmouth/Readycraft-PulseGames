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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
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
    static final String PICKUP_MODEL = "readycraft:kart_pickup_box";
    enum Item {
        SHELL("Shell"), TURBO("Turbo"), LIGHTNING("Lightning"), BANANA("Banana"), TRIPLE_SHELL("Triple shell"),
        STAR("Star"), BULLET("Bullet"), SHIELD("Shield"),
        REPAIR_KIT("Repair kit"), PULSE_WAVE("Pulse wave"), MAGNET("Magnet");
        final String label;
        Item(String label) { this.label = label; }
    }
    private static Material icon(Item item) {
        return switch (item) {
            case SHELL, TRIPLE_SHELL -> Material.SNOWBALL;
            case TURBO -> Material.SUGAR;
            case LIGHTNING -> Material.NETHER_STAR;
            case BANANA -> Material.YELLOW_DYE;
            case STAR -> Material.FIREWORK_STAR;
            case BULLET -> Material.ARROW;
            case SHIELD -> Material.SHIELD;
            case REPAIR_KIT -> Material.IRON_INGOT;
            case PULSE_WAVE -> Material.HEART_OF_THE_SEA;
            case MAGNET -> Material.COMPASS;
        };
    }
    static final int STAR_TICKS = 200, SHIELD_TICKS = 300;
    static final class Kart {
        Player driver; final ArmorStand seat; AutoCloseable visual;
        Set<UUID> viewers=Set.of();
        KartPhysics.State motion;
        KartSurface surface;
        KartSurfacePhysics.State surfaceMotion;
        KartPose pose;
        long surfaceReady;
        boolean detachedFlight;
        KartPhysics.Input input = new KartPhysics.Input(0,0);
        final KartBoost boost = new KartBoost();
        long slowUntil, boxReady, resetReady;
        long magnetUntil, pulseAt;
        Location pulseOrigin;
        final KartDrift drift = new KartDrift();
        boolean driftPressed, driftNeedsRelease, jumpDown, trickRequested, inAntiGravity;
        final KartAir air = new KartAir();
        Item item;
        int uses = 1;
        long rollStart = -1, shieldUntil, starUntil, bulletUntil, nextShellTick, noticeUntil;
        int lastLap = 1;
        final KartPlaceTracker placeTracker = new KartPlaceTracker();
        Item rollResult;
        KartBotDriver autopilot;
        boolean insideBox;
        boolean selectionClosed = true;
        KartBotDriver bot;
        int waypoint;
        long nextItemTick, itemSince, paceTick = -100;
        double pace = 1;
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
    private final Map<Cuboid,uk.co.playerready.pulsegames.core.content.PulseContentBridge.WorldVisual> boxVisuals = new LinkedHashMap<>();
    private Set<UUID> boxViewers = Set.of();
    private KartArcadeConfig arcade = KartArcadeConfig.NONE;
    private final KartPads pads = new KartPads();
    private KartPadConfig padProfiles = KartPadConfig.load(Map.of(), Map.of());
    private KartSurfaceConfig surfaces = KartSurfaceConfig.load(Map.of());
    private boolean advancedTrack;
    private boolean showcaseEnabled;
    private KartTrackProps trackProps;
    private List<UUID> trackHumanRacers = List.of();
    /** Wall-clock cap, so a laggy server cannot stretch the gap between lightning flashes. */
    private final uk.co.playerready.pulsegames.core.util.RateCap flashes = new uk.co.playerready.pulsegames.core.util.RateCap(1, 5000);
    private List<List<KartArcadeConfig.Pt>> ribbons = List.of();
    private KartItemBoxes boxes;
    private KartBoxDisplays displays;
    private boolean bridgeBoxes;
    private final KartBananas bananas = new KartBananas();
    private final Map<Integer,ItemDisplay> bananaDisplays = new HashMap<>();
    private KartPhysics.CollisionSpace space;
    private KartBotRoute botRoute;
    private List<Location> botPoints = List.of();
    private KartRaceResults raceResults;
    private final Map<UUID,String> racerNames = new HashMap<>();
    private final Set<UUID> announcedFinishers = new HashSet<>();
    private double finishFraction;
    private boolean resultsPublished;
    private long tick;
    private boolean closed;

    public PulseKartsGame(GameInstance game) { super(game); }
    @Override public boolean managesCompletion() { return true; }
    @Override protected boolean sweptCheckpoints() { return true; }
    @Override protected int laps() { return "cup".equals(game.mode().id()) ? Math.max(1,game.arena().settings().getInt("laps",3)) : 1; }
    @Override public String startProblem(List<Player> players) {
        String trackProblem = KartTrackSafety.validationError(game.arena());
        if (trackProblem != null) return trackProblem;
        if (players.stream().anyMatch(game::isBot)) {
            String routeProblem = KartBotRoute.validationError(game.arena());
            if (routeProblem != null) return "Kart bot route: " + routeProblem;
        }
        if(game.plugin().content()==null)return "PulseContent is required for kart models.";
        String kartProblem = game.plugin().content().kartStartProblem(MODEL,players);
        if (kartProblem != null) return kartProblem;
        KartArcadeConfig config;
        try { config = KartArcadeConfig.load(game.arena().settings().getValues(false), game.arena().regions()); }
        catch (IllegalArgumentException problem) { return problem.getMessage(); }
        try {
            var profiles = KartPadConfig.load(game.arena().settings().getValues(false), game.arena().regions());
            var surfaceConfig = KartSurfaceConfig.load(game.arena().settings().getValues(false));
            if ((profiles.configured() || surfaceConfig.configured()) && !trackAccess(players))
                return "This track requires the track-physics trial and every human racer on its allowlist.";
            if (surfaceConfig.configured()) {
                String poseProblem = game.plugin().content().kartPoseStartProblem(MODEL, players);
                if (poseProblem != null) return poseProblem;
            }
        } catch (IllegalArgumentException problem) { return problem.getMessage(); }
        if (showcaseAccess(players)) {
            try { KartPresentationConfig.load(game.arena().settings().getValues(false),game.arena().regions()); }
            catch (IllegalArgumentException problem) { return problem.getMessage(); }
        }
        var notBlock = config.boostBlocks().stream().filter(material -> !material.isBlock()).findFirst();
        if (notBlock.isPresent()) return "boost-block " + notBlock.get() + " is not a block material.";
        // Item-box locations fall back to a plain display without the PulseContent pickup model;
        // legacy item-box regions keep requiring it, exactly as before.
        int legacyBoxes = config.itemBoxes().isEmpty() ? game.arena().regionsByPrefix("itembox").size() : 0;
        if (legacyBoxes > 32) return "PulseKarts supports at most 32 item-box regions per map.";
        return legacyBoxes == 0 ? null : game.plugin().content().worldVisualStartProblem(PICKUP_MODEL,players);
    }
    @Override public void onStart() {
        super.onStart();
        initialiseResults(game.alivePlayers());
        space=KartPhysics.world(game.world());
        try { tuning = new KartBotTuning(game.plugin().getConfig().getConfigurationSection("karts.bots")); }
        catch (RuntimeException ignored) { tuning = KartBotTuning.DEFAULT; }
        arcade=KartArcadeConfig.load(game.arena().settings().getValues(false),game.arena().regions());
        padProfiles=KartPadConfig.load(game.arena().settings().getValues(false),game.arena().regions());
        surfaces=KartSurfaceConfig.load(game.arena().settings().getValues(false));
        advancedTrack=padProfiles.configured() || surfaces.configured();
        trackHumanRacers=game.alivePlayers().stream().filter(p -> !game.isBot(p)).map(Player::getUniqueId).toList();
        if (advancedTrack && !trackAccess(game.alivePlayers())) throw new IllegalStateException("Kart track-physics trial access changed");
        itemBoxes=arcade.itemBoxes().isEmpty() ? game.arena().regionsByPrefix("itembox") : List.of();
        showcaseEnabled=KartShowcaseAccess.allowed(game.plugin().getConfig(),trackHumanRacers);
        if (showcaseEnabled) {
            var presentation=KartPresentationConfig.load(game.arena().settings().getValues(false),game.arena().regions());
            if(presentation.configured()) trackProps=new KartTrackProps(game.world(),game.plugin().content(),presentation,
                    message->game.plugin().getLogger().warning(message),itemBoxes.size());
        }
        if (!arcade.itemBoxes().isEmpty()) {
            boxes=new KartItemBoxes(arcade.itemBoxes());
            displays=new KartBoxDisplays(game.world(),boxes);
            bridgeBoxes=game.plugin().content().worldVisualStartProblem(PICKUP_MODEL,game.alivePlayers())==null;
        }
        ribbons=arcade.antiGravityRails().stream().map(rail->KartAntiGravity.ribbon(rail,1.5)).toList();
        // The bullet item follows the bot route. In an all-human race it is only used when the map's route is valid
        // and marked reviewed with bots-enabled: validation alone does not prove the kart can drive the line.
        if (game.alivePlayers().stream().anyMatch(game::isBot) || (game.arena().settings().getBoolean("bots-enabled", false)
                && KartBotRoute.validationError(game.arena()) == null)) {
            botRoute = KartBotRoute.load(game.arena(), game.world());
            botPoints = botRoute.points();
        }
        refreshBoxVisuals();
        game.alivePlayers().forEach(this::giveKart);
        game.alivePlayers().stream().filter(p -> !game.isBot(p)).forEach(this::openStyleMenu);
        game.broadcast("<aqua>Choose your kart style during the 15-second grid countdown. Balanced is selected by default.");
        game.broadcast("<yellow>Accelerate forwards, brake/reverse backwards, steer left/right. Hold jump while steering to drift; release for a charged boost. Use your item to fire it; compass resets your kart.");
        game.runRepeating(1,1,()->{
            try { drive(); }
            catch(RuntimeException | LinkageError failure) {
                game.plugin().getLogger().log(java.util.logging.Level.SEVERE,"Kart controller failed",failure);
                try { onCleanup(); }
                catch (RuntimeException | LinkageError cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                    game.plugin().getLogger().log(java.util.logging.Level.WARNING,"Kart controller cleanup failed",cleanupFailure);
                } finally {
                    // closed stops simulation; it must never prevent the terminal game transition.
                    game.end(List.of(),advancedTrack || showcaseEnabled ? "forced" : "kart-controller-error");
                }
            }
        });
    }
    private boolean showcaseAccess(List<Player> players) {
        return KartShowcaseAccess.allowed(game.plugin().getConfig(),players.stream()
                .filter(p->!game.isBot(p)).map(Player::getUniqueId).toList());
    }
    private void pulseProp(String binding) { if(trackProps!=null)trackProps.pulse(binding,tick); }
    private boolean trackAccess(List<Player> players) {
        return KartTrackFeatures.allowed(game.plugin().getConfig(), players.stream()
                .filter(p -> !game.isBot(p)).map(Player::getUniqueId).toList());
    }
    static KartPhysics.State at(Location at) { return new KartPhysics.State(at.getX(),at.getY(),at.getZ(),at.getYaw(),0,0,false); }
    private Location location(KartPhysics.State state) { return new Location(game.world(),state.x(),state.y(),state.z(),(float)state.yaw(),0); }
    private Set<UUID> viewers() {
        return game.everyone().stream().filter(p->!game.isBot(p) && p.isOnline() && p.getWorld().equals(game.world()))
                .map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
    }
    private void closeBoxVisuals() {
        RuntimeException error = null;
        for (var entry : List.copyOf(boxVisuals.entrySet())) {
            try { entry.getValue().close(); boxVisuals.remove(entry.getKey()); }
            catch (Exception | LinkageError failure) {
                if (error == null) error = new IllegalStateException("Kart pickup cleanup failed", failure);
                else error.addSuppressed(failure);
            }
        }
        boxViewers = Set.of();
        // Keep failed handles for retry; never create replacements over orphan visuals.
        if (error != null) throw error;
    }
    private void refreshBoxVisuals() {
        Set<UUID> audience = game.everyone().stream().filter(p -> !game.isBot(p) && p.isOnline()
                && p.getWorld().equals(game.world()) && game.plugin().content().worldVisualViewerReady(p))
                .map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
        List<Cuboid> loaded = itemBoxes.stream().filter(box -> {
            Location at = box.center(game.world());
            return game.world().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
        }).toList();
        if (!audience.equals(boxViewers)) closeBoxVisuals();
        if (audience.isEmpty()) return;
        try {
            for (var entry : List.copyOf(boxVisuals.entrySet())) {
                if (!loaded.contains(entry.getKey()) || !entry.getValue().active()) {
                    entry.getValue().close(); boxVisuals.remove(entry.getKey());
                }
            }
            for (Cuboid box : loaded) if (!boxVisuals.containsKey(box)) boxVisuals.put(box,
                    game.plugin().content().createWorldVisual(PICKUP_MODEL,box.center(game.world()).add(0,.75,0),audience));
            boxViewers = Set.copyOf(audience);
        } catch (Exception failure) {
            try { closeBoxVisuals(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw new IllegalStateException("Kart pickup refresh failed", failure);
        } catch (LinkageError failure) {
            try { closeBoxVisuals(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw new IllegalStateException("Kart pickup refresh failed", failure);
        }
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
            ItemStack reset=new ItemStack(Material.COMPASS);reset.editMeta(m->m.displayName(Text.mm("<yellow>Reset kart <gray>("+uk.co.playerready.pulsegames.core.util.PlatformDetector.useVerb(player)+")")));
            player.getInventory().setItem(8,reset);
            if (!game.isBot(player)) player.getInventory().setItem(4,menuItem(Material.MINECART,"<aqua>Choose kart style",List.of("<gray>Choose before the race starts")));
        } catch(RuntimeException | LinkageError failure) {
            if(visual!=null)try{visual.close();}catch(Exception ignored){} seat.remove();throw failure;
        }
    }
    @Override public void onInput(Player player,Input input) {
        Kart kart=karts.get(player.getUniqueId());if(kart==null || kart.bot!=null)return;
        if (input.isJump() && !kart.jumpDown) kart.trickRequested=true; // rising edge: a trick if airborne
        kart.jumpDown=input.isJump();
        if (!input.isJump()) kart.driftNeedsRelease=false;
        kart.driftPressed=input.isJump() && !kart.driftNeedsRelease;
        kart.input=new KartPhysics.Input((input.isForward()?1:0)-(input.isBackward()?1:0),
                (input.isRight()?1:0)-(input.isLeft()?1:0));
    }
    private void drive() {
        if(closed || game.state()!=GameState.RUNNING)return;tick++;
        if (advancedTrack && !KartTrackFeatures.allowed(game.plugin().getConfig(), trackHumanRacers)) {
            game.broadcast("<yellow>This track trial has been disabled. The race has been cancelled.");
            try { onCleanup(); } finally { game.end(List.of(), "forced"); }
            return;
        }
        if (showcaseEnabled && !KartShowcaseAccess.allowed(game.plugin().getConfig(),trackHumanRacers)) {
            game.broadcast("<yellow>The kart showcase trial has been disabled. The race has been cancelled.");
            try { onCleanup(); } finally { game.end(List.of(), "forced"); }
            return;
        }
        if(trackProps!=null)trackProps.update(tick,game.everyone().stream().filter(p->!game.isBot(p)).toList());
        if (surfaces.configured() && tick % 20 == 0
                && game.plugin().content().kartPoseStartProblem(MODEL,game.alivePlayers()) != null) {
            game.broadcast("<yellow>The surface renderer is unavailable. The race has been cancelled.");
            try { onCleanup(); } finally { game.end(List.of(), "forced"); }
            return;
        }
        if (raceResults != null && raceResults.shouldEnd(tick)) { finishRace("race-grace-ended"); return; }
        if (tick % 20 == 0 && !itemBoxes.isEmpty()) refreshBoxVisuals();
        if (displays != null) { if (tick == 1 || tick % 20 == 0) configureBoxDisplays(); displays.update(tick); }
        if (tick % 20 == 0) expireBananas();
        if (tick % 10 == 0) bananaMarkers();
        Map<UUID,Integer> places = tick % KartHud.INTERVAL_TICKS == 0 ? places() : Map.of();
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
            if(game.frozen()) { kart.drift.reset(); continue; }
            if (kart.bot == null && places.containsKey(player.getUniqueId())) hud(kart, places.get(player.getUniqueId()), places.size());
            if (!kart.selectionClosed && kart.bot == null) {
                kart.selectionClosed = true;
                closeStyleMenu(player);
                player.getInventory().setItem(4,null);
            }
            if (kart.bulletUntil != 0 && tick >= kart.bulletUntil) endBullet(kart);
            if (showcaseEnabled && kart.bot != null && tick < kart.spinUntil && kart.item == Item.REPAIR_KIT) useItem(kart);
            if (tick >= kart.spinUntil && (kart.bot != null || tick < kart.bulletUntil) && controlBot(kart)) continue;
            KartPhysics.State before=kart.motion;
            Location from=location(kart.motion);
            double multiplier=speedMultiplier(kart.boost,tick,kart.slowUntil);
            // Karts may pass side by side; rear impacts are resolved after every chassis moves.
            KartPhysics.Input input = tick < kart.spinUntil ? new KartPhysics.Input(0,0) : kart.input;
            if (tick < kart.spinUntil && kart.surface == null) {
                float yaw = kart.motion.yaw();
                for (int degree=0; degree<36; degree++) {
                    if (!space.clearAt(kart.motion.x(),kart.motion.y(),kart.motion.z(),yaw+1)) break;
                    yaw++;
                }
                kart.motion = new KartPhysics.State(kart.motion.x(),kart.motion.y(),kart.motion.z(),yaw,
                        kart.motion.speed(),kart.motion.verticalSpeed(),kart.motion.grounded());
                input = new KartPhysics.Input(0,0);
            }
            int previousDriftTier = kart.drift.tier();
            var drift = kart.drift.update(kart.driftPressed && kart.surface == null, input, kart.motion, tick < kart.spinUntil || tick < kart.slowUntil);
            if (kart.drift.tier() > previousDriftTier && kart.bot == null)
                player.playSound(player.getLocation(), "minecraft:block.note_block.pling", .6f, DRIFT_PITCH[kart.drift.tier() - 1]);
            if (kart.drift.tier() > 0 && tick % 2 == 0) driftSparks(kart);
            if (drift.boostTicks() > 0) {
                if (kart.bot == null) player.playSound(player.getLocation(), "minecraft:entity.firework_rocket.launch", .6f, 1.2f);
                kart.boost.apply(KartBoost.Source.DRIFT, drift.boostFactor(), tick, drift.boostTicks());
                multiplier = speedMultiplier(kart.boost, tick, kart.slowUntil);
            }
            KartPhysics.State next=moveChassis(kart,drift.input(),multiplier,drift.slipDegrees());
            if (next == null) continue;
            if (next.speed() < 4 || Math.hypot(next.x()-kart.motion.x(),next.z()-kart.motion.z()) < .08) kart.drift.cancelCharge();
            boolean trickEdge=kart.trickRequested;kart.trickRequested=false;
            KartAir.Step flight=kart.air.tick(next.grounded(),trickEdge && tick>=kart.spinUntil);
            if (flight.capped()) next=new KartPhysics.State(next.x(),next.y(),next.z(),next.yaw(),next.speed(),
                    Math.min(next.verticalSpeed(),KartAir.FORCED_DESCENT),false);
            Location to=location(next);
            if(to.getY()<voidY()) { reset(kart);continue; }
            // The trick spin turns the seat (and so the kart model) only; the physics heading is untouched.
            Location seatAt=to.clone();seatAt.setYaw(to.getYaw()+(float)flight.spinDegrees());
            if(!kart.seat.teleport(seatAt,TeleportFlag.EntityState.RETAIN_PASSENGERS)) {reset(kart);continue;}
            kart.motion=next;
            syncPose(kart,flight.spinDegrees());
            flightEffects(kart,flight);
            finishFraction = finish == null ? 0 : RaceGateCrossing.entry(finish,from,to);
            int gate=progressValue(player.getUniqueId());
            super.onMove(player,from,to);
            // The base class announces gates on the action bar; keep that message up before the HUD returns.
            int passed=progressValue(player.getUniqueId());
            if (passed!=gate) {
                kart.noticeUntil=tick+KartHud.NOTICE_HOLD_TICKS;
                for(int step=gate;step<passed;step++) {
                    int index=step%(checkpoints.size()+1);
                    pulseProp(index<checkpoints.size()?"checkpoint"+(index+1):"finish");
                }
            }
            if(closed || game.state()!=GameState.RUNNING)return;
            if (karts.containsKey(player.getUniqueId())) {
                collect(kart,from,to);collectBox(kart,before,next);touchPads(kart,before,next);antiGravity(kart,next);
                touchBananas(kart,before,next);tickRoulette(kart);itemEffects(kart);
            }
            if (kart.boost.active(tick)) boostTrail(kart);
        }
        if (!game.frozen()) { tickPulses(); applyBumps(previous); antiGravityContacts(); }
        if (tick % 5 == 0 && !ribbons.isEmpty()) antiGravityRibbon();
        completeRaceTick();
    }
    private KartPhysics.State moveChassis(Kart kart, KartPhysics.Input input, double multiplier, double slip) {
        if (kart.surface == null && !kart.air.flying() && tick >= kart.surfaceReady) {
            for (KartSurface candidate : surfaces.surfaces()) {
                var attached = candidate.attach(kart.motion);
                if (attached == null) continue;
                var pose = candidate.pose(attached.distance(), attached.lateral(), attached.headingDegrees());
                if (!space.clearPose(pose)) continue;
                kart.surface = candidate;
                kart.detachedFlight = false;
                kart.surfaceMotion = attached;
                kart.drift.reset();
                break;
            }
        }
        if (kart.surface != null) {
            var current = kart.surfaceMotion;
            // Item hits may alter speed after the previous surface step.
            current = new KartSurfacePhysics.State(current.distance(), current.lateral(), current.headingDegrees(), kart.motion.speed());
            var step = KartSurfacePhysics.tick(kart.surface, current, input, multiplier, space, kart.style);
            kart.surfaceMotion = step.state();
            kart.pose = step.pose();
            if (step.detached()) {
                kart.surface = null; kart.surfaceMotion = null; kart.surfaceReady = tick + 20;
                kart.detachedFlight = true;
                if (!space.clearAt(step.chassis().x(), step.chassis().y(), step.chassis().z(), step.chassis().yaw())) {
                    reset(kart); return null;
                }
                kart.pose = KartPose.upright(step.chassis());
            }
            return step.chassis();
        }
        boolean flight = kart.air.configuredFlight() || kart.detachedFlight;
        if (flight) input = new KartPhysics.Input(input.throttle(), input.steering() * (kart.detachedFlight ? .25 : kart.air.airSteering()));
        var result = KartPhysics.tick(kart.motion, input, multiplier, space, kart.style, slip, kart.air.gravity(), flight);
        if (result.grounded()) kart.detachedFlight = false;
        kart.pose = null;
        return result;
    }
    private void syncPose(Kart kart) { syncPose(kart,0); }
    private void syncPose(Kart kart, double spin) {
        if (!surfaces.configured()) return;
        var rotation = (kart.pose == null ? KartPose.upright(kart.motion) : kart.pose).rotation();
        if (kart.surface == null) rotation.rotateY((float)Math.toRadians(-spin));
        game.plugin().content().setKartPose(kart.seat, rotation.x(), rotation.y(), rotation.z(), rotation.w());
    }
    private void initialiseResults(List<Player> racers) {
        raceResults = new KartRaceResults(racers.stream().map(Player::getUniqueId).toList(),
                game.arena().settings().getInt("finish-grace-seconds",30));
        racers.forEach(p -> racerNames.put(p.getUniqueId(),p.getName()));
    }
    @Override protected void onFinish(Player player) {
        if (raceResults == null) initialiseResults(karts.values().stream().map(k -> k.driver).toList());
        if (!raceResults.finish(player.getUniqueId(),tick,Double.isFinite(finishFraction) ? finishFraction : 0)) return;
        pulseProp("finish");
        removeKart(player);
        game.recordFinish(player); // engine order only; PulseKarts' own places stay authoritative
        game.finishParticipant(player);
        player.sendMessage(Text.mm("<green>Course complete! <gray>You can watch the remaining racers."));
    }
    private void completeRaceTick() {
        if (raceResults == null || resultsPublished || closed) return;
        for (var result : raceResults.results()) if (announcedFinishers.add(result.player()))
            game.broadcast("<gold>#" + result.place() + (result.tied() ? " (tie) " : " ")
                    + racerNames.getOrDefault(result.player(),"Racer") + " <green>finished!");
        if (raceResults.shouldEnd(tick) || karts.isEmpty()) finishRace("race-complete");
    }
    private void finishRace(String reason) {
        if (resultsPublished || closed || game.state()!=GameState.RUNNING) return;
        resultsPublished = true;
        List<UUID> winnerIds = raceResults == null ? List.of() : raceResults.winners();
        List<Player> winners = game.players().stream().filter(p -> winnerIds.contains(p.getUniqueId())).toList();
        if (!winnerIds.isEmpty() && winners.isEmpty())
            game.broadcast("<gray>The first-place finisher left; their placing stands, but no victory rewards are reassigned.");
        if (raceResults != null && raceResults.remainingRacers()>0)
            game.broadcast("<gray>Race closed with " + raceResults.remainingRacers() + " unfinished racer(s).");
        game.end(winners,reason);
    }
    @Override public void onTimeUp() { finishRace("time-race"); }

    /** Mercy: a human has finished, no human is still racing and only bots are left; end after a short grace. */
    @Override public List<Player> mercyWinners() {
        if (raceResults == null || resultsPublished || closed) return null;
        int humans = 0, bots = 0;
        for (Kart kart : karts.values()) { if (game.isBot(kart.driver)) bots++; else humans++; }
        boolean humanFinished = raceResults.results().stream().anyMatch(r -> {
            Player p = game.player(r.player());
            return p != null && !game.isBot(p);
        });
        if (!Mercy.botsOnly(humanFinished, humans, bots)) return null;
        List<UUID> winnerIds = raceResults.winners();
        return game.players().stream().filter(p -> winnerIds.contains(p.getUniqueId())).toList();
    }
    /** With the 10 second countdown this is the 15 second grace. */
    @Override public int mercyHoldSeconds() { return 5; }

    private void applyBumps(Map<UUID,KartPhysics.State> previous) {
        for (Kart victim : karts.values()) {
            if (victim.surface != null || tick < victim.bumpImmuneUntil || victim.resetTick == tick || !game.isActive(victim.driver)) continue;
            KartPhysics.State beforeVictim = previous.get(victim.driver.getUniqueId());
            if (beforeVictim == null) continue;
            for (Kart attacker : karts.values()) {
                if (attacker.surface != null || attacker == victim || attacker.resetTick == tick || tick < attacker.spinUntil || !game.isActive(attacker.driver)) continue;
                KartPhysics.State beforeAttacker = previous.get(attacker.driver.getUniqueId());
                if (beforeAttacker != null && KartBumps.rearImpact(beforeAttacker,attacker.motion,beforeVictim,victim.motion)) {
                    if (absorbs(victim)) break;
                    int duration = (int)Math.round(20 * attacker.style.bumpPower() * victim.style.incomingDurationMultiplier());
                    victim.slowUntil = Math.max(victim.slowUntil,tick+duration);
                    victim.spinUntil = tick+11;
                    victim.bumpImmuneUntil = tick+60;
                    var m=victim.motion;
                    victim.motion = new KartPhysics.State(m.x(),m.y(),m.z(),m.yaw(),m.speed()*.45,m.verticalSpeed(),m.grounded());
                    notice(victim, "<red>Bumped! <gold>Protected 3s");
                    victim.driver.playSound(location(m),"minecraft:entity.player.hurt",1,.8f);
                    notice(attacker, "<green>Bumper hit!");
                    break;
                }
            }
        }
    }
    /** Bots submit the same bounded throttle/steering input as human drivers. */
    private KartBotTuning tuning = KartBotTuning.DEFAULT;
    private double gateSpacing;
    /** Mean distance between consecutive gates: turns checkpoint progress into blocks for the pace gap. */
    private double gateSpacing() {
        if (gateSpacing > 0) return gateSpacing;
        var centres = new ArrayList<Location>();
        checkpoints.forEach(c -> centres.add(c.center(game.world())));
        centres.add(finish.center(game.world()));
        double sum = 0;
        for (int i = 1; i < centres.size(); i++) sum += Math.hypot(centres.get(i).getX()-centres.get(i-1).getX(), centres.get(i).getZ()-centres.get(i-1).getZ());
        return gateSpacing = centres.size() < 2 || sum < 10 ? 60 : sum / (centres.size() - 1);
    }
    /** Rubber-band pace for a bot against the nearest human by race position; refreshed twice a second. */
    private double paceFor(Kart kart, BotDifficulty difficulty) {
        if (tick - kart.paceTick < 10) return kart.pace;
        kart.paceTick = tick;
        var own = placingEntry(kart);
        double best = Double.NaN;
        for (Kart other : karts.values()) {
            if (other.bot != null || !game.isActive(other.driver)) continue;
            var theirs = placingEntry(other);
            double gap = (own.progress() - theirs.progress()) * gateSpacing()
                    + (finite(theirs.distanceToGate()) - finite(own.distanceToGate()));
            if (Double.isNaN(best) || Math.abs(gap) < Math.abs(best)) best = gap;
        }
        return kart.pace = tuning.pace(best, !Double.isNaN(best), difficulty);
    }
    private static double finite(double distance) { return distance == Double.MAX_VALUE ? 0 : distance; }
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
        boolean bullet = tick < kart.bulletUntil;
        if (kart.surface != null) {
            var state = kart.surfaceMotion;
            double correction = state.headingDegrees() - state.lateral() * 8;
            kart.input = new KartPhysics.Input(1, Math.clamp(-correction / 25, -1, 1));
            kart.driftPressed = false;
            if (bullet) kart.boost.apply(KartBoost.Source.BULLET, KartBullet.FACTOR, tick, 2);
            return false;
        }
        // A human's bullet is the same driver steering their kart; real bots keep their own.
        KartBotDriver pilot = kart.bot != null ? kart.bot : kart.autopilot;
        BotDifficulty difficulty = kart.bot != null ? game.plugin().bots().difficultyFor(game, null) : BotDifficulty.HARD;
        double scale = bullet ? KartBullet.SPEED_SCALE : kart.boost.multiplier(tick);
        var decision = pilot.update(kart.motion, point(target), point(following), difficulty, space,
                karts.values().stream().filter(other -> other != kart).map(other -> other.motion).toList(), kart.style,
                new KartBotDriver.Context(scale, nearJump(kart), kart.air.flying(),
                        kart.bot != null ? paceFor(kart, difficulty) : 1, lapOf(kart), tuning));
        if (decision.reset()) { reset(kart); return true; }
        kart.input = decision.input();
        kart.driftPressed = decision.drift() && !bullet;
        if (bullet) kart.boost.apply(KartBoost.Source.BULLET, KartBullet.FACTOR, tick, 2);
        if (kart.bot != null && kart.item != null && tick >= kart.nextItemTick) {
            boolean use = switch (kart.item) {
                case TURBO -> decision.useTurboSafe();
                case SHELL, TRIPLE_SHELL -> tick >= kart.nextShellTick && shellTargetAhead(kart);
                case LIGHTNING -> lightningTarget(kart) != null && humanAhead(kart);
                // Hold a banana for a corner, where followers cut through; spend it anyway if it has waited long.
                case BANANA -> opponentBehind(kart) && (decision.cornerAhead() || tick - kart.itemSince > 300);
                case SHIELD -> tick >= kart.shieldUntil;
                case STAR, BULLET -> true;
                case REPAIR_KIT -> tick < kart.slowUntil || tick < kart.spinUntil;
                case PULSE_WAVE -> karts.values().stream().anyMatch(k -> k != kart && game.isActive(k.driver)
                        && itemCentre(k).distanceSquared(itemCentre(kart)) <= 36);
                case MAGNET -> boxes != null && tick >= kart.magnetUntil;
            };
            if (use) useItem(kart);
            kart.nextItemTick = tick + Math.round(kart.bot.personality().itemTempo()
                    * switch (difficulty) { case EASY -> 40; case NORMAL -> 20; case HARD -> 10; });
        }
        return false;
    }
    private int lapOf(Kart kart) { Progress p = progress.get(kart.driver.getUniqueId()); return p == null ? 1 : p.lap; }
    /** Lightning is a catch-up tool against people: only worth firing when a human is placed ahead. */
    private boolean humanAhead(Kart kart) {
        int own = progressValue(kart.driver.getUniqueId());
        return karts.values().stream().anyMatch(o -> o.bot == null && game.isActive(o.driver) && progressValue(o.driver.getUniqueId()) > own);
    }
    private boolean nearJump(Kart kart) {
        for (Cuboid jump : arcade.jumps()) if (KartPads.near(jump, kart.motion.x(), kart.motion.y(), kart.motion.z(), 8)) return true;
        return false;
    }
    /** A banana is worth dropping when an unprotected racer is close behind, roughly in line. */
    private boolean opponentBehind(Kart kart) {
        Location origin = location(kart.motion);
        Vector back = origin.getDirection().setY(0).normalize().multiply(-1);
        return karts.values().stream().filter(other -> other != kart && game.isActive(other.driver) && tick >= other.bumpImmuneUntil).anyMatch(other -> {
            Vector relative = location(other.motion).toVector().subtract(origin.toVector()).setY(0);
            double along = relative.dot(back);
            return along > 2 && along < 14 && relative.subtract(back.clone().multiply(along)).lengthSquared() <= 6;
        });
    }
    private static KartBotDriver.Point point(Location at) { return new KartBotDriver.Point(at.getX(),at.getY(),at.getZ()); }
    private boolean shellTargetAhead(Kart kart) {
        Location origin = location(kart.motion).add(0,.45,0);
        Vector direction = origin.getDirection().setY(0).normalize();
        var wall = game.world().rayTraceBlocks(origin,direction,24,FluidCollisionMode.ALWAYS,true);
        double range = wall == null ? 24 : wall.getHitPosition().distance(origin.toVector());
        return karts.values().stream().filter(other -> other != kart && game.isActive(other.driver) && tick >= other.bumpImmuneUntil).anyMatch(other -> {
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
    static double speedMultiplier(KartBoost boost,long tick,long slow) {return boost.multiplier(tick)*(tick<slow?.4:1);}
    private static final float[] DRIFT_PITCH = {1.0f, 1.4f, 1.9f};
    private static final Color[] DRIFT_COLOUR = {Color.fromRGB(0x3b82f6), Color.fromRGB(0xf97316), Color.fromRGB(0xa855f7)};
    /** Colour-coded sparks at both rear corners: blue (mini), orange (super), purple (ultra). */
    private void touchPads(Kart kart, KartPhysics.State before, KartPhysics.State after) {
        var panels = arcade.boostPanels();
        for (int i = 0; i < panels.size(); i++) {
            var profile = padProfiles.boost(i);
            if (profile == null) {
                if (KartPads.enters(panels.get(i), before, after)) boostPad(kart, "boost" + (i + 1));
            } else if (before.grounded() || after.grounded()) {
                boolean entered;
                if (kart.surface != null && kart.pose != null && profile.yaw() == null) {
                    var forward = kart.pose.forward();
                    entered = pads.entry(kart.driver.getUniqueId(), "boost" + (i + 1), panels.get(i), before, after,
                            forward.x(), forward.y(), forward.z(), tick, profile.cooldown());
                } else entered = pads.entry(kart.driver.getUniqueId(), "boost" + (i + 1), panels.get(i), before, after,
                        profile.yaw(), tick, profile.cooldown());
                if (entered) {
                    kart.boost.apply(KartBoost.Source.PANEL, profile.factor(), tick, profile.ticks());
                    pulseProp("boost"+(i+1));
                    padEffects(kart);
                }
            }
        }
        var jumps = arcade.jumps();
        for (int i = 0; i < jumps.size(); i++) {
            if (kart.air.flying() || kart.surface != null) continue;
            var profile = padProfiles.jump(i);
            if (profile == null) {
                if (!after.grounded() || after.speed() < KartAir.MIN_LAUNCH_SPEED
                        || !KartPads.enters(jumps.get(i), before, after)
                        || !pads.trigger(kart.driver.getUniqueId(), "jump" + (i + 1), tick, KartAir.PAD_COOLDOWN)) continue;
                kart.motion = kart.air.launch(kart.motion, arcade.jumpPower());
            } else {
                if ((!before.grounded() && !after.grounded()) || after.speed() < profile.minSpeed()
                        || !pads.entry(kart.driver.getUniqueId(), "jump" + (i + 1), jumps.get(i), before, after,
                        profile.yaw(), tick, profile.cooldown())) continue;
                kart.motion = kart.air.launch(kart.motion, profile, before.grounded());
            }
            pulseProp("jump"+(i+1));
            kart.drift.cancelCharge();
            Location at = location(kart.motion);
            game.world().spawnParticle(Particle.CLOUD, at.clone().add(0, .2, 0), 10, .5, .1, .5, .04);
            if (kart.bot == null) kart.driver.playSound(at, "minecraft:entity.slime.jump", .8f, 1.3f);
            break;
        }
        if (!arcade.boostBlocks().isEmpty() && after.grounded() && kart.surface == null) {
            var under = game.world().getBlockAt((int) Math.floor(after.x()), (int) Math.floor(after.y() - .1), (int) Math.floor(after.z()));
            boolean touching = under != null && arcade.boostBlocks().contains(under.getType());
            if (advancedTrack) {
                if (pads.contactEntry(kart.driver.getUniqueId(), "block", touching, tick, KartPads.BOOST_COOLDOWN)) {
                    kart.boost.apply(KartBoost.Source.PANEL, PANEL_FACTOR, tick, PANEL_TICKS);
                    padEffects(kart);
                }
            } else if (touching) boostPad(kart, "block");
        } else if (advancedTrack) pads.contactEntry(kart.driver.getUniqueId(), "block", false, tick, KartPads.BOOST_COOLDOWN);
    }
    /** Inside a section: +10% speed (a non-stacking boost source) and a soft glow around the kart. */
    private void antiGravity(Kart kart, KartPhysics.State at) {
        boolean inside = kart.surface != null || arcade.inAntiGravity(at.x(), at.y(), at.z());
        if (inside) {
            kart.boost.apply(KartBoost.Source.ANTIGRAV, KartAntiGravity.SPEED_FACTOR, tick, 2);
            if (tick % 4 == 0) game.world().spawnParticle(Particle.GLOW, location(at).add(0, .8, 0), 2, .5, .3, .5, .02);
            if (!kart.inAntiGravity && kart.bot == null) {
                notice(kart, "<light_purple>Anti-gravity!");
                kart.driver.playSound(location(at), "minecraft:block.beacon.power_select", .6f, 1.6f);
            }
        }
        kart.inAntiGravity = inside;
    }
    /** MK8-style: two karts touching inside a section each get a short boost. */
    private void antiGravityContacts() {
        List<Kart> inside = karts.values().stream().filter(k -> k.inAntiGravity && game.isActive(k.driver)).toList();
        for (int i = 0; i < inside.size(); i++) for (int j = i + 1; j < inside.size(); j++) {
            Kart a = inside.get(i), b = inside.get(j);
            if (!KartPhysics.overlaps(a.pose == null ? KartPose.upright(a.motion) : a.pose,
                    b.pose == null ? KartPose.upright(b.motion) : b.pose)
                    || !pads.triggerBoth(a.driver.getUniqueId(), b.driver.getUniqueId(), "antigrav-bump", tick, KartAntiGravity.BUMP_COOLDOWN)) continue;
            for (Kart kart : List.of(a, b)) {
                kart.boost.apply(KartBoost.Source.BUMP, KartAntiGravity.BUMP_FACTOR, tick, KartAntiGravity.BUMP_TICKS);
                if (kart.bot == null) kart.driver.playSound(location(kart.motion), "minecraft:block.amethyst_cluster.hit", .9f, 1.5f);
            }
            game.world().spawnParticle(Particle.END_ROD, location(a.motion).add(location(b.motion)).multiply(.5).add(0, 1, 0), 10, .4, .3, .4, .05);
        }
    }
    /** Glowing ribbon along each rail, only near human karts and capped per emission. */
    private void antiGravityRibbon() {
        List<Location> humans = karts.values().stream().filter(k -> k.bot == null).map(k -> location(k.motion)).toList();
        if (humans.isEmpty()) return;
        var dust = new Particle.DustOptions(Color.fromRGB(0x22d3ee), 1.2f);
        int budget = 60;
        for (var ribbon : ribbons) for (var p : ribbon) {
            if (budget <= 0) return;
            if (humans.stream().noneMatch(h -> Math.abs(h.getX() - p.x()) < 24 && Math.abs(h.getZ() - p.z()) < 24)) continue;
            game.world().spawnParticle(Particle.DUST, p.x(), p.y() + .4, p.z(), 1, 0, 0, 0, 0, dust);
            budget--;
        }
    }
    private void flightEffects(Kart kart, KartAir.Step flight) {
        Location at = location(kart.motion);
        if (flight.trickStarted()) {
            game.world().spawnParticle(Particle.FIREWORK, at.clone().add(0, 1, 0), 10, .6, .4, .6, .03);
            if (kart.bot == null) kart.driver.playSound(at, "minecraft:entity.player.attack.sweep", .8f, 1.5f);
        }
        if (flight.landedWithTrick()) {
            kart.boost.apply(KartBoost.Source.TRICK, KartAir.TRICK_FACTOR, tick, KartAir.TRICK_BOOST_TICKS);
            game.world().spawnParticle(Particle.END_ROD, at.clone().add(0, .3, 0), 14, .6, .1, .6, .06);
            if (kart.bot == null) kart.driver.playSound(at, "minecraft:block.note_block.chime", .9f, 1.6f);
        }
    }
    private void boostPad(Kart kart, String pad) {
        if (!pads.trigger(kart.driver.getUniqueId(), pad, tick, KartPads.BOOST_COOLDOWN)) return;
        kart.boost.apply(KartBoost.Source.PANEL, PANEL_FACTOR, tick, PANEL_TICKS);
        pulseProp(pad);
        padEffects(kart);
    }
    private void padEffects(Kart kart) {
        Location at = location(kart.motion);
        game.world().spawnParticle(Particle.CLOUD, at.clone().add(0, .3, 0), 12, .5, .1, .5, .05);
        if (kart.bot == null) kart.driver.playSound(at, "minecraft:entity.breeze.wind_burst", .8f, 1.2f);
    }
    /** One continuous cloud puff behind any boosted kart; a trail, not a flashing burst. */
    private void boostTrail(Kart kart) {
        Location at = location(kart.motion);
        double yaw = Math.toRadians(at.getYaw());
        game.world().spawnParticle(Particle.CLOUD, at.getX() + Math.sin(yaw) * 1.1, at.getY() + .35, at.getZ() - Math.cos(yaw) * 1.1, 1, .12, .05, .12, .01);
    }
    static final double PANEL_FACTOR = 1.5;
    static final int PANEL_TICKS = 30;
    private void driftSparks(Kart kart) {
        Location at = location(kart.motion);
        double yaw = Math.toRadians(at.getYaw()), fx = -Math.sin(yaw), fz = Math.cos(yaw);
        var dust = new Particle.DustOptions(DRIFT_COLOUR[kart.drift.tier() - 1], 1.1f);
        for (int side = -1; side <= 1; side += 2)
            game.world().spawnParticle(Particle.DUST, at.getX() - fx * .85 - fz * .55 * side, at.getY() + .2,
                    at.getZ() - fz * .85 + fx * .55 * side, 1, 0.05, 0.05, 0.05, 0, dust);
    }
    // Only controller-authoritative mounted motion advances the race; walking cannot count.
    @Override public void onMove(Player player,Location from,Location to) {}
    private void collect(Kart kart,Location from,Location to) {
        boolean inside=itemBoxes.stream().anyMatch(b->b.contains(to));
        boolean crossed=itemBoxes.stream().anyMatch(b->!Double.isNaN(RaceGateCrossing.entry(b,from,to)));
        if(!kart.insideBox && crossed && tick>=kart.boxReady && canTake(kart)) {
            kart.boxReady=tick+100;pickUp(kart);
            kart.driver.playSound(to,"minecraft:entity.item.pickup",1,.8f);
        }
        kart.insideBox=inside;
    }
    private boolean canTake(Kart kart) { return kart.item==null && kart.rollStart<0; }
    /** Collectable boxes: swept proximity, global respawn. A kart that cannot take an item leaves the box alone. */
    private void collectBox(Kart kart,KartPhysics.State before,KartPhysics.State after) {
        if (boxes == null || !canTake(kart)) return;
        int hit = showcaseEnabled && tick < kart.magnetUntil
                ? boxes.collect(before, after, tick, 3.5, i -> {
                    var p = boxes.position(i);
                    return itemLineOfSight(itemCentre(kart), new Location(game.world(), p.x(), p.y() + KartItemBoxes.BOX_LIFT, p.z()));
                }) : boxes.collect(before, after, tick);
        if (hit < 0) return;
        displays.burst(hit);
        pickUp(kart);
    }
    private KartPlacing.Entry placingEntry(Kart kart) {
        UUID id = kart.driver.getUniqueId();
        Progress p = progress.get(id);
        Cuboid gate = p == null ? null : p.nextCheckpoint < checkpoints.size() ? checkpoints.get(p.nextCheckpoint) : finish;
        double distance = Double.MAX_VALUE;
        if (gate != null) { Location c = gate.center(game.world()); distance = Math.hypot(c.getX() - kart.motion.x(), c.getZ() - kart.motion.z()); }
        return new KartPlacing.Entry(id, progressValue(id), distance);
    }
    private Item chooseItem(Kart kart) {
        var entries = karts.values().stream().filter(k -> game.isActive(k.driver)).map(this::placingEntry).toList();
        var tier = KartItemBalance.tier(KartPlacing.place(kart.driver.getUniqueId(), entries), Math.max(1, entries.size()));
        Set<Item> unavailable = EnumSet.noneOf(Item.class);
        if (botPoints.isEmpty()) unavailable.add(Item.BULLET);
        if (boxes == null) unavailable.add(Item.MAGNET);
        // Balance flag: with karts.bots.strong-items-when-behind, bots in the back tier may roll Star and Bullet.
        boolean catchUp = tuning.strongItemsWhenBehind && tier == KartItemBalance.Tier.BACK;
        if (kart.bot != null && !catchUp) for (Item strong : List.of(Item.STAR, Item.BULLET))
            if (karts.values().stream().noneMatch(k -> k.bot == null && k.item == strong)) unavailable.add(strong);
        return KartItemBalance.choose(kart.style, ThreadLocalRandom.current().nextInt(), tier, unavailable, showcaseEnabled);
    }
    /** Humans spin the roulette; bots take the item straight away. */
    private void pickUp(Kart kart) {
        Item chosen = chooseItem(kart);
        if (kart.bot != null) { giveItem(kart, chosen); return; }
        kart.rollStart = tick; kart.rollResult = chosen;
        showRouletteIcon(kart, 0);
    }
    private void giveItem(Kart kart, Item item) {
        kart.itemSince = tick; kart.item = item; kart.uses = item == Item.TRIPLE_SHELL ? 3 : 1;
        showItem(kart);
    }
    private void showRouletteIcon(Kart kart, int elapsed) {
        Item[] pool = KartItemBalance.roulettePool(showcaseEnabled);
        ItemStack stack = itemIcon(kart, pool[KartRoulette.iconIndex(elapsed, pool.length)]);
        stack.editMeta(m -> m.displayName(Text.mm("<gray>Rolling...")));
        kart.driver.getInventory().setItem(0, stack);
    }
    private void tickRoulette(Kart kart) {
        if (kart.rollStart < 0) return;
        int elapsed = (int) (tick - kart.rollStart);
        if (KartRoulette.done(elapsed)) {
            Item result = kart.rollResult; kart.rollStart = -1; kart.rollResult = null;
            giveItem(kart, result);
            kart.driver.playSound(location(kart.motion), "minecraft:entity.experience_orb.pickup", .8f, 1.3f);
        } else if (KartRoulette.changesAt(elapsed)) {
            showRouletteIcon(kart, elapsed);
            kart.driver.playSound(location(kart.motion), "minecraft:block.note_block.hat", .5f, 0.8f + elapsed / 60f);
        }
    }
    private void configureBoxDisplays() {
        Set<UUID> audience = game.everyone().stream().filter(p -> !game.isBot(p) && p.isOnline()
                && p.getWorld().equals(game.world()) && game.plugin().content().worldVisualViewerReady(p))
                .map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
        displays.configure(game.plugin().content(), bridgeBoxes, audience);
    }
    private Kart lightningTarget(Kart kart) {
        int own = progressValue(kart.driver.getUniqueId());
        return standings().stream().map(p -> karts.get(p.getUniqueId())).filter(Objects::nonNull)
                .filter(other -> other != kart && game.isActive(other.driver) && tick >= other.bumpImmuneUntil
                        && tick >= other.starUntil && tick >= other.bulletUntil
                        && other.resetTick != tick && progressValue(other.driver.getUniqueId()) > own)
                .findFirst().orElse(null);
    }
    /** At most one lightning flash per 5 s for photosensitive players; any extra strike shows quiet sparks. */
    private void lightningEffect(Location at) {
        if (!uk.co.playerready.pulsegames.core.settings.PlayerSettings.anyInWorld(game.world(), uk.co.playerready.pulsegames.core.settings.Comfort.REDUCE_FLASHES)
                && flashes.allow(System.currentTimeMillis())) game.world().strikeLightningEffect(at);
        else game.world().spawnParticle(Particle.ELECTRIC_SPARK, at.add(0, 1, 0), 20, .5, .8, .5, .1);
    }
    /** An action-bar message the HUD must not overwrite for a moment. */
    private void notice(Kart kart, String miniMessage) {
        kart.driver.sendActionBar(Text.mm(miniMessage));
        kart.noticeUntil = tick + KartHud.NOTICE_HOLD_TICKS;
    }
    private Map<UUID,Integer> places() {
        var order = KartPlacing.order(karts.values().stream().filter(k -> game.isActive(k.driver)).map(this::placingEntry).toList());
        Map<UUID,Integer> places = new HashMap<>();
        for (int i = 0; i < order.size(); i++) places.put(order.get(i), i + 1);
        return places;
    }
    /** Every 10 ticks: final-lap title, place-change sound, and lap / place / item / boost on the action bar. */
    private void hud(Kart kart, int place, int field) {
        Progress p = progress.get(kart.driver.getUniqueId());
        int lap = p == null ? 1 : p.lap;
        if (KartHud.enteredFinalLap(kart.lastLap, lap, laps()))
            kart.driver.showTitle(Title.title(Text.mm("<gold>FINAL LAP!"), Component.empty(),
                    Title.Times.times(java.time.Duration.ofMillis(250), java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(500))));
        kart.lastLap = lap;
        switch (kart.placeTracker.update(place, tick)) {
            case GAINED -> kart.driver.playSound(location(kart.motion), "minecraft:block.note_block.chime", .5f, 1.5f);
            case LOST -> kart.driver.playSound(location(kart.motion), "minecraft:block.note_block.bass", .4f, .7f);
            case NONE -> { }
        }
        if (tick < kart.noticeUntil) return;
        String item = kart.rollStart >= 0 ? "Rolling" : kart.item == null ? null
                : kart.item == Item.TRIPLE_SHELL ? kart.item.label + " x" + kart.uses : kart.item.label;
        kart.driver.sendActionBar(Text.mm(KartHud.line(lap, laps(), place, field, item, kart.boost.active(tick), kart.drift.tier())));
    }
    private ItemStack itemIcon(Kart kart, Item item) {
        if (showcaseEnabled && game.plugin().content() != null) {
            ItemStack custom = game.plugin().content().optionalItem("readycraft:kart_" + item.name().toLowerCase(Locale.ROOT), kart.driver);
            if (custom != null) return custom;
        }
        return new ItemStack(icon(item));
    }
    private void showItem(Kart kart) {
        if(kart.item==null){kart.driver.getInventory().setItem(0,null);return;}
        ItemStack item=itemIcon(kart,kart.item);
        item.setAmount(kart.item==Item.TRIPLE_SHELL ? Math.max(1,kart.uses) : 1);
        item.editMeta(m->m.displayName(Text.mm("<aqua>"+kart.item.label+" <gray>("+uk.co.playerready.pulsegames.core.util.PlatformDetector.useVerb(kart.driver)+")")));
        kart.driver.getInventory().setItem(0,item);
        notice(kart, "<aqua>Power-up: "+kart.item.label);
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
    @Override protected List<Player> standings() {
        List<Player> ordered = new ArrayList<>(super.standings());
        if (raceResults == null) return ordered;
        Map<UUID,Integer> places = new HashMap<>();
        raceResults.results().forEach(r -> places.put(r.player(),r.place()));
        game.players().stream().filter(p -> places.containsKey(p.getUniqueId()) && !ordered.contains(p)).forEach(ordered::add);
        ordered.sort(Comparator.comparingInt((Player p) -> places.getOrDefault(p.getUniqueId(),Integer.MAX_VALUE))
                .thenComparing(Comparator.comparingInt((Player p) -> progressValue(p.getUniqueId())).reversed()));
        return ordered;
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
        if(game.frozen() || event.getPlayer().getVehicle()!=kart.seat || (tick<kart.spinUntil && kart.item!=Item.REPAIR_KIT))return;
        if(event.getItem().getType()==Material.COMPASS && event.getPlayer().getInventory().getHeldItemSlot()!=0){if(tick>=kart.resetReady){kart.resetReady=tick+60;reset(kart);}return;}
        if(event.getPlayer().getInventory().getHeldItemSlot()!=0 || kart.item==null)return;
        useItem(kart);
    }
    private void useItem(Kart kart) {
        Item used=kart.item;if(used==null)return;
        if ((used==Item.REPAIR_KIT || used==Item.PULSE_WAVE || used==Item.MAGNET) && !showcaseEnabled) return;
        if (used==Item.MAGNET && (boxes==null || tick<kart.magnetUntil)) return;
        if (used==Item.PULSE_WAVE && kart.pulseOrigin!=null) return;
        Kart lightning = used == Item.LIGHTNING ? lightningTarget(kart) : null;
        if (used == Item.LIGHTNING && lightning == null) {
            if (kart.bot == null) notice(kart, "<gray>Nobody to zap yet. Kept.");
            return;
        }
        if (used == Item.BULLET && !startBullet(kart)) {
            if (kart.bot == null) notice(kart, "<gray>Get on the track first.");
            return;
        }
        if (used == Item.TRIPLE_SHELL) {
            if (tick < kart.nextShellTick) return;
            if (--kart.uses > 0) { kart.nextShellTick = tick + 10; showItem(kart); shell(kart); return; }
        }
        kart.item=null;kart.uses=1;showItem(kart);
        switch(used) {
            case REPAIR_KIT -> {
                kart.spinUntil=0; kart.slowUntil=0; kart.bumpImmuneUntil=Math.max(kart.bumpImmuneUntil,tick+40);
                game.world().spawnParticle(Particle.HAPPY_VILLAGER,itemCentre(kart),8,.5,.4,.5,0);
                kart.driver.playSound(itemCentre(kart),"minecraft:block.anvil.use",.35f,1.7f);
                notice(kart,"<green>Repaired! <gold>Brief protection");
            }
            case PULSE_WAVE -> {
                kart.pulseOrigin=itemCentre(kart); kart.pulseAt=tick+10;
                game.world().playSound(kart.pulseOrigin,"minecraft:block.beacon.activate",.6f,1.6f);
                itemRing(kart.pulseOrigin,6,Color.AQUA);
            }
            case MAGNET -> {
                kart.magnetUntil=tick+120;
                kart.driver.playSound(itemCentre(kart),"minecraft:block.amethyst_block.chime",.7f,1.2f);
                notice(kart,"<aqua>Magnet · wider box pickups for 6 seconds");
            }
            case BANANA -> dropBanana(kart);
            case STAR -> {
                kart.starUntil=tick+STAR_TICKS;kart.boost.apply(KartBoost.Source.STAR,1.2,tick,STAR_TICKS);
                kart.driver.playSound(location(kart.motion),"minecraft:entity.player.levelup",.8f,1.2f);
            }
            case SHIELD -> {
                kart.shieldUntil=tick+SHIELD_TICKS;
                kart.driver.playSound(location(kart.motion),"minecraft:block.beacon.activate",.7f,1.4f);
            }
            case BULLET -> { /* already running: startBullet armed it */ }
            case TURBO -> {kart.boost.apply(KartBoost.Source.TURBO,KartBoost.TURBO_FACTOR,tick,40);kart.driver.playSound(location(kart.motion),"minecraft:entity.firework_rocket.launch",1,1.2f);}
            case SHELL, TRIPLE_SHELL -> shell(kart);
            case LIGHTNING -> {
                if(slow(lightning,60))lightningEffect(location(lightning.motion));
            }
        }
    }
    /** Star and bullet karts shrug hits off; a shield takes exactly one and pops. */
    private boolean absorbs(Kart kart) {
        if (tick < kart.starUntil || tick < kart.bulletUntil) return true;
        if (tick >= kart.shieldUntil) return false;
        kart.shieldUntil = 0;
        kart.bumpImmuneUntil = Math.max(kart.bumpImmuneUntil, tick + 20);
        Location at = location(kart.motion).add(0, .8, 0);
        game.world().spawnParticle(Particle.BUBBLE_POP, at, 16, .8, .5, .8, .05);
        if (kart.bot == null) {
            kart.driver.playSound(at, "minecraft:item.shield.break", .9f, 1.1f);
            notice(kart, "<aqua>Shield blocked it!");
        }
        return true;
    }
    private boolean startBullet(Kart kart) {
        if (botPoints.isEmpty() || botRoute == null) return false;
        var route = botPoints.stream().map(PulseKartsGame::point).toList();
        if (KartBullet.distanceToRoute(route, kart.motion.x(), kart.motion.z()) > KartBullet.MAX_OFF_ROUTE) return false;
        kart.waypoint = KartBullet.nextWaypoint(route, kart.motion.x(), kart.motion.z());
        if (kart.bot == null) kart.autopilot = new KartBotDriver(kart.driver.getUniqueId());
        kart.bulletUntil = tick + KartBullet.TICKS;
        kart.drift.reset(); kart.driftPressed = false;
        kart.driver.playSound(location(kart.motion), "minecraft:entity.firework_rocket.launch", 1f, .8f);
        return true;
    }
    private void endBullet(Kart kart) {
        kart.bulletUntil = 0; kart.autopilot = null;
        if (kart.bot != null) return;
        // Hand the controls back with whatever the player is holding right now.
        Input held = kart.driver.getCurrentInput();
        kart.input = held == null ? new KartPhysics.Input(0,0) : new KartPhysics.Input(
                (held.isForward()?1:0)-(held.isBackward()?1:0), (held.isRight()?1:0)-(held.isLeft()?1:0));
        kart.driftNeedsRelease = held != null && held.isJump();
    }
    private void dropBanana(Kart kart) {
        KartPhysics.State s = kart.motion;
        double behind = 0, yaw = Math.toRadians(s.yaw());
        for (double candidate : new double[]{KartBananas.DROP_BEHIND, 1.0}) {
            if (space.clearAt(s.x() + Math.sin(yaw) * candidate, s.y(), s.z() - Math.cos(yaw) * candidate, s.yaw())) { behind = candidate; break; }
        }
        List<KartBananas.Banana> evicted = new ArrayList<>();
        var banana = bananas.drop(kart.driver.getUniqueId(), s, behind, tick, evicted);
        evicted.forEach(this::removeBananaDisplay);
        ItemDisplay display = game.world().spawn(new Location(game.world(), banana.x(), banana.y() + .35, banana.z()), ItemDisplay.class, e -> {
            e.setItemStack(new ItemStack(Material.YELLOW_DYE));
            e.setBillboard(Display.Billboard.CENTER);
            e.setGlowing(true); e.setGlowColorOverride(Color.YELLOW); e.setPersistent(false);
            e.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(.9f, .9f, .9f), new org.joml.AxisAngle4f()));
        });
        if (display != null) bananaDisplays.put(banana.id(), display);
        kart.driver.playSound(location(s), "minecraft:entity.slime.squish", .8f, 1.4f);
    }
    private void removeBananaDisplay(KartBananas.Banana banana) {
        ItemDisplay display = bananaDisplays.remove(banana.id());
        if (display != null) display.remove();
    }
    private void expireBananas() { bananas.expire(tick).forEach(this::removeBananaDisplay); }
    /** Yellow dust marks each banana for clients that cannot render display entities. */
    private void bananaMarkers() {
        var dust = new Particle.DustOptions(Color.YELLOW, 1.0f);
        for (var banana : bananas.live()) game.world().spawnParticle(Particle.DUST, banana.x(), banana.y() + .6, banana.z(), 1, .1, .1, .1, 0, dust);
    }
    private void touchBananas(Kart kart, KartPhysics.State before, KartPhysics.State after) {
        var banana = bananas.touched(before, after, kart.driver.getUniqueId(), tick);
        // A protected kart rolls over it and leaves it armed for the next racer.
        if (banana == null || tick < kart.bumpImmuneUntil || kart.resetTick == tick) return;
        bananas.remove(banana); removeBananaDisplay(banana);
        if (!slow(kart, 30)) return; // a star, bullet or shield took the hit
        kart.spinUntil = tick + 11;
        var m = kart.motion;
        kart.motion = new KartPhysics.State(m.x(), m.y(), m.z(), m.yaw(), m.speed() * .45, m.verticalSpeed(), m.grounded());
    }
    private void itemEffects(Kart kart) {
        Location at = location(kart.motion);
        if (showcaseEnabled && tick < kart.magnetUntil && tick % 10 == 0) itemRing(itemCentre(kart),1.5,Color.FUCHSIA);
        if (tick >= kart.magnetUntil) kart.magnetUntil=0;
        if (tick < kart.starUntil && tick % 2 == 0) game.world().spawnParticle(Particle.NOTE, at.clone().add(0, 1.8, 0), 2, .6, .3, .6, 1);
        if (tick < kart.shieldUntil && tick % 10 == 0)
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI / 4 * i;
                game.world().spawnParticle(Particle.BUBBLE_POP, at.getX() + Math.cos(angle) * 1.3, at.getY() + .9, at.getZ() + Math.sin(angle) * 1.3, 1, 0, 0, 0, 0);
            }
        if (kart.shieldUntil != 0 && tick >= kart.shieldUntil) kart.shieldUntil = 0;
    }
    private Location itemCentre(Kart kart) {
        KartPose pose=kart.pose==null ? KartPose.upright(kart.motion) : kart.pose;
        var p=pose.position().add(pose.up().multiply(.6));
        return new Location(game.world(),p.x(),p.y(),p.z());
    }
    private boolean itemLineOfSight(Location from,Location to) {
        Vector direction=to.toVector().subtract(from.toVector()); double length=direction.length();
        return length<1e-6 || game.world().rayTraceBlocks(from,direction.multiply(1/length),length,FluidCollisionMode.ALWAYS,true)==null;
    }
    private void itemRing(Location at,double radius,Color colour) {
        var dust=new Particle.DustOptions(colour,1);
        for(int i=0;i<24;i++) {
            double a=i*Math.PI/12;
            game.world().spawnParticle(Particle.DUST,at.getX()+Math.cos(a)*radius,at.getY(),at.getZ()+Math.sin(a)*radius,1,0,0,0,0,dust);
        }
    }
    /** Resolve only after every kart has moved; a reset or departure cancels its armed wave. */
    private void tickPulses() {
        for(Kart owner:karts.values()) {
            if(owner.pulseOrigin==null) continue;
            if(!showcaseEnabled || !game.isActive(owner.driver)) { owner.pulseOrigin=null; continue; }
            Location origin=owner.pulseOrigin;
            if(tick<owner.pulseAt) {
                if(tick%5==0) itemRing(origin,6,Color.AQUA);
                continue;
            }
            owner.pulseOrigin=null;
            for(Kart victim:karts.values()) {
                if(victim==owner || !game.isActive(victim.driver)) continue;
                Location target=itemCentre(victim);
                if(origin.distanceSquared(target)<=36 && itemLineOfSight(origin,target)) slow(victim,30);
            }
            itemRing(origin,6,Color.AQUA);
            game.world().playSound(origin,"minecraft:block.amethyst_block.resonate",.7f,.8f);
        }
    }
    private boolean slow(Kart kart,int duration) {
        if (tick < kart.bumpImmuneUntil || kart.resetTick == tick) return false;
        if (absorbs(kart)) return false;
        kart.slowUntil=Math.max(kart.slowUntil,tick+duration);
        kart.bumpImmuneUntil = kart.slowUntil + 40;
        kart.drift.cancelCharge();
        kart.driver.playSound(location(kart.motion),"minecraft:entity.player.hurt",1,1);
        notice(kart, "<red>Hit! <gold>Brief protection");
        return true;
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
        if(target!=null){boolean hit=slow(target,50);notice(shooter, hit ? "<green>Shell hit!" : "<gray>Already hit.");}
    }
    private boolean safeReset(Location at) {
        return space.clearAt(at.getX(),at.getY(),at.getZ(),at.getYaw())
                && !space.clearAt(at.getX(),at.getY()-.1,at.getZ(),at.getYaw());
    }
    private void reset(Kart kart) {
        kart.pulseOrigin=null; kart.pulseAt=0; kart.magnetUntil=0;
        kart.drift.reset();kart.driftPressed=false;kart.air.cancel();kart.trickRequested=false;
        kart.surface=null;kart.surfaceMotion=null;kart.pose=null;kart.surfaceReady=tick+20;kart.detachedFlight=false;
        if (kart.bot == null) { kart.bulletUntil=0;kart.autopilot=null; }
        kart.resetTick=tick;kart.spinUntil=0;kart.bumpImmuneUntil=Math.max(kart.bumpImmuneUntil,tick+40);
        Location target=resetLocation(kart.driver);kart.input=new KartPhysics.Input(0,0);kart.boost.clear();kart.slowUntil=0;
        Input held = kart.bot == null ? kart.driver.getCurrentInput() : null;
        kart.driftNeedsRelease = held != null && held.isJump();
        if (held != null) kart.input = new KartPhysics.Input(
                (held.isForward()?1:0)-(held.isBackward()?1:0),
                (held.isRight()?1:0)-(held.isLeft()?1:0));
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
        syncPose(kart);
        if(kart.driver.getVehicle()!=kart.seat && !kart.seat.addPassenger(kart.driver))throw new IllegalStateException("Kart remount failed");
        notice(kart, "<yellow>Back on track");
    }
    @Override protected void sendBack(Player player) {Kart kart=karts.get(player.getUniqueId());if(kart!=null)reset(kart);}
    private void closeStyleMenu(Player player) {
        var view=player.getOpenInventory();
        if(view!=null && view.getTopInventory().getHolder() instanceof Menu menu && menu.source==this) player.closeInventory();
    }
    private void removeKart(Player player) {
        Kart kart=karts.remove(player.getUniqueId());if(kart==null)return;
        pads.forget(player.getUniqueId());
        closeStyleMenu(kart.driver);
        try{kart.visual.close();}catch(Exception failure){game.plugin().getLogger().warning("Kart visual cleanup: "+failure.getMessage());}
        finally {kart.seat.eject();kart.seat.remove();}
    }
    @Override public void onQuit(Player player){removeKart(player);super.onQuit(player);if(raceResults!=null){raceResults.depart(player.getUniqueId());completeRaceTick();}}
    @Override public void onEliminated(Player player){removeKart(player);if(raceResults!=null){raceResults.depart(player.getUniqueId());completeRaceTick();}}
    @Override public void onEnd(List<Player> winners){onCleanup();}
    @Override public void onCleanup(){
        closed=true;
        try { try { if(trackProps!=null)trackProps.close(); } finally {closeBoxVisuals();} }
        finally {
            try { if(displays!=null)displays.close(); }
            finally {
                try { bananas.clear().forEach(this::removeBananaDisplay); }
                finally { for(Kart kart:List.copyOf(karts.values()))removeKart(kart.driver); }
            }
        }
    }
    @Override public int startFreezeSeconds(){return 15;}
    @Override public int timeLimitSeconds(){return game.arena().settings().getInt("time-limit",600);}
    @Override
    public String phaseCountdown() { if(raceResults!=null && raceResults.hasFinishers() && !resultsPublished) return "Finish race: " + raceResults.remainingSeconds(tick) + "s"; int seconds = game.startCountdownSeconds(); return seconds > 0 ? "Choose kart: " + seconds + "s" : null; }

}
