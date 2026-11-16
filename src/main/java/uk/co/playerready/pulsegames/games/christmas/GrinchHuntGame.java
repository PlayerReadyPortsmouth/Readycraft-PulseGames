package uk.co.playerready.pulsegames.games.christmas;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.seasonal.ChristmasMarkers;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.*;

import static uk.co.playerready.pulsegames.games.christmas.GrinchRules.*;

/** Snow footprints are observable evidence. No villager gadget reads an invisible player's location at unlimited range. */
public final class GrinchHuntGame extends MiniGame {
    private record Print(Location at, UUID owner, int made, int expires, ItemDisplay display, boolean decoy) {}
    private record Trap(Location at, int expires, ItemDisplay display) {}
    private record Hold(UUID target, int ticks) {}
    private static final class Tree {
        final Location at;
        final List<ItemDisplay> presents = new ArrayList<>();
        int stock;
        Tree(Location at) { this.at = at; }
    }
    private final ChristmasMarkers markers;
    private final Set<UUID> grinches = new LinkedHashSet<>(), caught = new HashSet<>(), heartUsed = new HashSet<>(), backUsed = new HashSet<>();
    private final Map<UUID, List<String>> carrying = new HashMap<>();
    private final Map<UUID, Integer> hits = new HashMap<>(), lastHit = new HashMap<>(), immuneUntil = new HashMap<>(), revealUntil = new HashMap<>(), quietUntil = new HashMap<>();
    private final Map<UUID, Location> lastFoot = new HashMap<>();
    private final Map<UUID, Float> speeds = new HashMap<>();
    private final Map<UUID, Boolean> invisibleBefore = new HashMap<>();
    private final Map<UUID, VillagerRole> roles = new HashMap<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private final Map<UUID, Hold> holds = new HashMap<>();
    private final Map<String, Tree> trees = new LinkedHashMap<>();
    private final List<Print> prints = new ArrayList<>();
    private final List<Trap> traps = new ArrayList<>();
    private final Map<UUID, Wolf> dogs = new HashMap<>();
    private final Map<UUID, ItemDisplay> dogModels = new HashMap<>();
    private final Map<UUID, Snowball> snowballs = new HashMap<>();
    private final Map<UUID, Integer> stolen = new HashMap<>(), tags = new HashMap<>(), recovered = new HashMap<>();
    private final Map<UUID, ItemDisplay> eyes = new HashMap<>();
    private ChristmasVisuals visuals;
    private BukkitTask loop;
    private int tick, banked;
    private boolean ended;
    private boolean started;
    private boolean originalStorm;
    private long originalTime;

    public GrinchHuntGame(GameInstance game) { super(game); markers = new ChristmasMarkers(game); }
    @Override public boolean managesCompletion() { return true; }
    @Override public boolean environmentalDamage() { return false; }
    @Override public boolean pvp() { return true; }
    @Override public boolean respawnable() { return true; }
    @Override public int startFreezeSeconds() { return 0; }
    @Override public int timeLimitSeconds() { return Math.max(60, Math.min(1200, game.arena().settings().getInt("time-limit", 300))); }
    @Override public String startProblem(List<Player> players) {
        if (game.plugin().seasons() == null || !game.plugin().seasons().gameEnabled("grinchhunt")) return "Grinch Hunt is not in the active season.";
        if (players.stream().anyMatch(p -> !p.hasMetadata("NPC") && !game.plugin().seasons().canPlay(p, "grinchhunt")))
            return "Grinch Hunt is limited to the current seasonal trial players.";
        if (players.size() < 2) return "Grinch Hunt needs a Grinch and at least one villager.";
        if (markers.points("gh.grinch.spawn").isEmpty() || markers.points("gh.villager.spawn").isEmpty()
                || markers.point("gh.cave.pile") == null || markers.keys("gh.tree.").isEmpty()) return "Import the Grinch Hunt map markers first.";
        return null;
    }
    @Override public void onStart() {
        started = true;
        visuals = new ChristmasVisuals(game);
        originalStorm = game.world().hasStorm(); originalTime = game.world().getTime();
        game.world().setTime(18000); game.world().setStorm(false);
        for (String key : markers.keys("gh.tree.")) {
            Tree tree = new Tree(markers.point(key)); trees.put(key, tree);
            tree.stock = 3; renderTree(tree);
        }
        List<Player> players = new ArrayList<>(game.alivePlayers());
        Collections.shuffle(players);
        int count = grinchCount(players.size()), villager = 0;
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index); UUID id = player.getUniqueId();
            speeds.put(id, player.getWalkSpeed()); invisibleBefore.put(id, player.isInvisible());
            carrying.put(id, new ArrayList<>());
            if (index < count) {
                grinches.add(id); player.setInvisible(true);
                teleportSpawn(player, "gh.grinch.spawn", index);
                player.sendMessage(Text.msg("<green>You are the Grinch! Sneak at trees to steal, return to the cave pile. Bank 15, or be carrying a present when time runs out!"));
                player.sendMessage(Text.msg("<yellow>Five hotbar abilities. Your eyes appear when revealed. Sneak for faint prints. Heart escape and cave back exit can each save you once."));
            } else {
                roles.put(id, VillagerRole.values()[villager++ % VillagerRole.values().length]);
                teleportSpawn(player, "gh.villager.spawn", index - count);
                player.sendMessage(Text.msg("<gold>Track green footprints! Hit a Grinch three times, or keep sneaking within two blocks for three seconds to tag. Use your gadget and switch roles with the iron nugget."));
            }
            equip(player);
        }
        game.broadcast("<gold>Grinch Hunt! " + count + (count == 1 ? " invisible Grinch" : " invisible Grinches") + " among the snowy rooftops. Listen for tree alarms!");
        loop = game.runRepeating(1, 2, this::step);
    }
    private void teleportSpawn(Player player, String key, int index) {
        List<Location> spawns = markers.points(key); if (!spawns.isEmpty()) player.teleport(spawns.get(Math.floorMod(index, spawns.size())));
    }
    private boolean isGrinch(Player player) { return grinches.contains(player.getUniqueId()); }
    private boolean activeGrinch(Player player) { return isGrinch(player) && !caught.contains(player.getUniqueId()) && game.isActive(player); }
    private static boolean near(Location a, Location b, double radius) {
        return a != null && b != null && a.getWorld() == b.getWorld() && a.distanceSquared(b) <= radius * radius;
    }
    private List<String> cargo(Player player) { return carrying.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()); }
    private void equip(Player player) {
        player.getInventory().clear();
        if (isGrinch(player)) {
            player.getInventory().setItem(0, item(Material.LEATHER_BOOTS, "Sneaky Shoes", "Quiet, faint footprints for 8 seconds"));
            player.getInventory().setItem(1, item(Material.LIME_DYE, "Decoy Prints", "Send a false trail ahead"));
            player.getInventory().setItem(2, item(Material.SNOWBALL, "Snow Flurry", "Nearby villagers get a snowy distraction"));
            player.getInventory().setItem(3, item(Material.SWEET_BERRIES, "Heart Grows Three Sizes", "One escape per round; also saves you from one tag"));
            player.getInventory().setItem(4, item(Material.BRICK, "Chimney Dive", "Use near a chimney to jump to the next"));
            player.getInventory().setItem(8, item(Material.RABBIT_FOOT, "Cave back exit", "One escape through the cave back marker"));
            player.getInventory().setHeldItemSlot(6);
        } else {
            VillagerRole role = roles.getOrDefault(player.getUniqueId(), VillagerRole.TRACKER);
            Material material = switch (role) {
                case TRACKER -> Material.COMPASS; case LAMPLIGHTER -> Material.LANTERN; case SNOWBALLER -> Material.BLAZE_ROD;
                case BELL_RINGER -> Material.BELL; case ELF_INVENTOR -> Material.CHEST; case NIGHT_WATCH -> Material.FIREWORK_ROCKET;
            };
            player.getInventory().setItem(0, item(material, role.name().replace('_', ' '), "Use gadget; each role has its own cooldown"));
            player.getInventory().setItem(8, item(Material.IRON_NUGGET, "Change villager role", "Use to switch role, once every 15 seconds"));
        }
    }
    private static ItemStack item(Material type, String name, String hint) {
        ItemStack stack = new ItemStack(type); stack.editMeta(meta -> { meta.displayName(Component.text(name)); meta.lore(List.of(Component.text(hint))); }); return stack;
    }
    private void renderTree(Tree tree) {
        for (ItemDisplay display : tree.presents) visuals.remove(display);
        tree.presents.clear();
        for (int i = 0; i < tree.stock; i++) {
            ItemDisplay display = visuals.spawn(tree.at.clone().add((i - 1) * 0.5, 0.35, 0), "christmas_present_red", Material.RED_WOOL, 0.55f);
            if (display != null) tree.presents.add(display);
        }
    }
    private void step() {
        if (ended || game.state() != GameState.RUNNING) return;
        tick += 2;
        for (Iterator<Print> iterator = prints.iterator(); iterator.hasNext();) {
            Print print = iterator.next();
            if (tick >= print.expires || !print.display.isValid()) { visuals.remove(print.display); iterator.remove(); }
        }
        for (Iterator<Trap> iterator = traps.iterator(); iterator.hasNext();) {
            Trap trap = iterator.next();
            if (tick >= trap.expires) { visuals.remove(trap.display); iterator.remove(); }
        }
        for (Player player : game.alivePlayers()) {
            if (!game.isActive(player) || ended) continue;
            if (activeGrinch(player)) stepGrinch(player);
            else if (!isGrinch(player)) stepVillager(player);
        }
        for (Map.Entry<UUID, Wolf> entry : dogs.entrySet()) {
            Wolf dog = entry.getValue();
            ItemDisplay model = dogModels.get(entry.getKey());
            if (model != null && dog.isValid()) model.teleport(dog.getLocation());
            if (!dog.isValid() || tick % 20 != 0) continue;
            Print freshest = freshestPrint();
            if (freshest != null) dog.getPathfinder().moveTo(freshest.at, 1.1);
        }
    }
    private void stepGrinch(Player player) {
        UUID id = player.getUniqueId(); Location at = player.getLocation();
        boolean revealed = tick < revealUntil.getOrDefault(id, 0);
        player.setInvisible(!revealed);
        if (revealed && !eyes.containsKey(id)) {
            ItemDisplay glow = visuals.spawn(at.clone().add(0, 1.65, 0), "christmas_grinch_eyes", Material.LIME_DYE, 0.5f);
            if (glow != null) eyes.put(id, glow);
        }
        ItemDisplay eye = eyes.get(id);
        if (eye != null) { if (revealed) eye.teleport(at.clone().add(0, 1.65, 0)); else { visuals.remove(eye); eyes.remove(id); } }
        if (!revealed && tick % 20 == 0) for (Player viewer : game.alivePlayers()) if (!isGrinch(viewer)) {
            Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
                equipment.put(slot, new ItemStack(Material.AIR));
            viewer.sendEquipmentChange(player, equipment);
        }
        Location previous = lastFoot.get(id);
        double spacing = Math.max(1, Math.min(6, game.arena().settings().getDouble("footprint-spacing", 2.5)));
        boolean grounded = player.isOnGround() || !at.clone().subtract(0, 0.15, 0).getBlock().isPassable();
        if (grounded && (previous == null || previous.distanceSquared(at) >= spacing * spacing)) {
            Location printAt = at.clone();
            if (previous != null) {
                org.bukkit.util.Vector travel = at.toVector().subtract(previous.toVector()).setY(0);
                if (travel.lengthSquared() > 0.01) printAt.setDirection(travel);
            }
            addPrint(player, printAt, false); lastFoot.put(id, at.clone());
        }
        if (!cargo(player).isEmpty() && tick % 6 == 0) at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.8, 0), 3, 0.2, 0.3, 0.2, 0);
        if (near(at, markers.point("gh.cave.pile"), 2.3) && !cargo(player).isEmpty()) {
            int amount = cargo(player).size(); banked += amount; cargo(player).clear(); game.addScore(player, amount);
            game.broadcast("<green>The cave pile now holds " + banked + "/15 presents!");
            for (int i = banked - amount; i < banked && i < 15; i++) visuals.spawn(markers.point("gh.cave.pile").clone().add((i % 3 - 1) * 0.6, 0.35 + i / 9 * 0.5, (i / 3 % 3 - 1) * 0.6),
                    "christmas_present_green", Material.GREEN_WOOL, 0.5f);
            if (banked >= 15) { finish(true, "fifteen-presents"); return; }
        }
        if (player.isSneaking() && tick % 20 == 0 && cargo(player).size() < 3) steal(player);
        for (Trap trap : new ArrayList<>(traps)) if (near(at, trap.at, 1.5)) {
            reveal(player, 120); player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 2, false, false));
            visuals.remove(trap.display); traps.remove(trap);
            game.broadcast("<gold>A present trap has caught a trail!");
        }
        if (tick > timeLimitSeconds() * 10 && banked < 4) player.setWalkSpeed(0.23f);
    }
    private void steal(Player player) {
        for (Map.Entry<String, Tree> entry : trees.entrySet()) {
            Tree tree = entry.getValue();
            if (tree.stock > 0 && near(player.getLocation(), tree.at, 2.2)) {
                tree.stock--; cargo(player).add(entry.getKey()); stolen.merge(player.getUniqueId(), 1, Integer::sum); renderTree(tree);
                visuals.sound(tree.at, "alarm", Sound.BLOCK_BELL_USE, 1.5f, 1.4f);
                tree.at.getWorld().spawnParticle(Particle.FIREWORK, tree.at, 10, 1, 1, 1, 0);
                game.broadcast("<gold>Tree alarm! A present was taken near " + entry.getKey().substring("gh.tree.".length()) + ".");
                reveal(player, banked >= 10 ? 50 : 20);
                return;
            }
        }
    }
    private void stepVillager(Player player) {
        if (!player.isSneaking()) { holds.remove(player.getUniqueId()); return; }
        Player target = game.alivePlayers().stream().filter(this::activeGrinch)
                .filter(g -> near(player.getLocation(), g.getLocation(), 2.2) && player.hasLineOfSight(g))
                .min(Comparator.comparingDouble(g -> g.getLocation().distanceSquared(player.getLocation()))).orElse(null);
        if (target == null) { holds.remove(player.getUniqueId()); return; }
        Hold hold = holds.get(player.getUniqueId());
        int progress = hold != null && hold.target.equals(target.getUniqueId()) ? hold.ticks + 2 : 2;
        holds.put(player.getUniqueId(), new Hold(target.getUniqueId(), progress));
        player.sendActionBar(Text.mm("<gold>Tag hold: " + Math.min(100, progress * 100 / 60) + "%"));
        if (tagged(0, progress)) { holds.remove(player.getUniqueId()); catchGrinch(target, player); }
    }
    private Ground ground(Location at) {
        if (insideMarkers(at, "gh.path.cleared.")) return Ground.CLEARED;
        if (at.getBlock().getLightFromSky() < 10) return Ground.INDOORS;
        Material below = at.clone().subtract(0, 0.15, 0).getBlock().getType();
        return insideMarkers(at, "gh.snow.fresh.") || below == Material.SNOW || below == Material.SNOW_BLOCK ? Ground.FRESH : Ground.INDOORS;
    }
    private boolean insideMarkers(Location at, String prefix) {
        for (String key : markers.keys(prefix)) {
            if (!key.endsWith(".min")) continue;
            Location min = markers.point(key), max = markers.point(key.substring(0, key.length() - 4) + ".max");
            if (min != null && max != null && at.getX() >= Math.min(min.getX(), max.getX()) - 0.5 && at.getX() <= Math.max(min.getX(), max.getX()) + 0.5
                    && at.getY() >= Math.min(min.getY(), max.getY()) - 1 && at.getY() <= Math.max(min.getY(), max.getY()) + 2
                    && at.getZ() >= Math.min(min.getZ(), max.getZ()) - 0.5 && at.getZ() <= Math.max(min.getZ(), max.getZ()) + 0.5) return true;
        }
        return false;
    }
    private void addPrint(Player player, Location at, boolean decoy) {
        while (prints.size() >= 155 || visuals.size() >= ChristmasVisuals.LIMIT - 10 || at.getWorld().getEntities().size() >= ChristmasVisuals.WORLD_LIMIT) {
            if (prints.isEmpty()) return;
            visuals.remove(prints.removeFirst().display);
        }
        Ground ground = ground(at);
        boolean sneaking = player.isSneaking() || tick < quietUntil.getOrDefault(player.getUniqueId(), 0);
        Location floor = at.clone().add(0, 0.025, 0); floor.setPitch(0);
        ItemDisplay display = visuals.spawn(floor, "christmas_footprint", Material.LIME_CARPET, printScale(ground, sneaking));
        if (display == null) return;
        int brightness = sneaking ? 3 : ground == Ground.FRESH ? 15 : ground == Ground.CLEARED ? 8 : 5;
        display.setBrightness(new Display.Brightness(brightness, brightness));
        int lifetime = printLifetime(ground, sneaking);
        prints.add(new Print(floor, player.getUniqueId(), tick, tick + lifetime, display, decoy));
        if (!sneaking) at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, floor, ground == Ground.FRESH ? 3 : 1, 0.05, 0, 0.05, 0);
    }
    private Print freshestPrint() { return prints.isEmpty() ? null : prints.getLast(); }
    private void reveal(Player grinch, int ticks) {
        revealUntil.merge(grinch.getUniqueId(), tick + ticks, Math::max); grinch.setInvisible(false);
        grinch.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, false, false));
        if (visuals != null) visuals.sound(grinch.getLocation(), "reveal", Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.3f);
    }
    private boolean ready(Player player, String ability, int cooldown) {
        String key = player.getUniqueId() + ":" + ability;
        if (tick < cooldowns.getOrDefault(key, 0)) {
            player.sendActionBar(Text.mm("<gray>Ready in " + ((cooldowns.get(key) - tick + 19) / 20) + "s")); return false;
        }
        cooldowns.put(key, tick + cooldown); return true;
    }
    @Override public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick() || event.getItem() == null || !game.isActive(event.getPlayer())) return;
        event.setCancelled(true); Player player = event.getPlayer();
        if (isGrinch(player)) grinchAbility(player, event.getItem().getType());
        else if (event.getItem().getType() == Material.IRON_NUGGET) {
            if (!ready(player, "role", 300)) return;
            VillagerRole role = roles.getOrDefault(player.getUniqueId(), VillagerRole.TRACKER);
            roles.put(player.getUniqueId(), VillagerRole.values()[(role.ordinal() + 1) % VillagerRole.values().length]);
            Wolf dog = dogs.remove(player.getUniqueId()); if (dog != null) dog.remove(); equip(player);
            ItemDisplay model = dogModels.remove(player.getUniqueId()); if (model != null) visuals.remove(model);
        } else villagerAbility(player);
    }
    private void grinchAbility(Player player, Material item) {
        if (!activeGrinch(player)) return;
        switch (item) {
            case LEATHER_BOOTS -> {
                if (!ready(player, "shoes", 500)) return;
                quietUntil.put(player.getUniqueId(), tick + 160);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 0, false, false));
                player.sendActionBar(Text.mm("<green>Sneaky Shoes! Fainter prints for eight seconds."));
            }
            case LIME_DYE -> {
                if (!ready(player, "decoy", 500)) return;
                Location origin = player.getLocation(); org.bukkit.util.Vector forward = origin.getDirection().setY(0);
                if (forward.lengthSquared() < 0.01) return;
                forward.normalize();
                for (int i = 1; i <= 8; i++) {
                    Location fake = origin.clone().add(forward.clone().multiply(i * 1.5));
                    // A decoy never crosses solid blocks or leaves a walkable surface.
                    if (!fake.getBlock().isPassable() || !fake.clone().add(0, 1, 0).getBlock().isPassable()
                            || fake.clone().subtract(0, 1, 0).getBlock().isPassable()) break;
                    addPrint(player, fake, true);
                }
            }
            case SNOWBALL -> {
                if (!ready(player, "flurry", 600)) return;
                Location at = player.getLocation(); at.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1, 0), 70, 5, 2, 5, 0.02);
                for (Player villager : game.alivePlayers()) if (!isGrinch(villager) && near(villager.getLocation(), at, 8))
                    villager.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
            }
            case SWEET_BERRIES -> escapeHeart(player);
            case BRICK -> {
                List<Location> chimneys = markers.keys("gh.chimney.").stream().map(markers::point).toList();
                int nearest = -1;
                for (int i = 0; i < chimneys.size(); i++) if (near(player.getLocation(), chimneys.get(i), 3)) { nearest = i; break; }
                if (nearest < 0 || chimneys.size() < 2) { player.sendActionBar(Text.mm("<yellow>Stand inside a chimney first.")); return; }
                if (ready(player, "chimney", 400)) {
                    Location target = chimneys.get((nearest + 1) % chimneys.size());
                    player.teleport(target); lastFoot.remove(player.getUniqueId());
                    target.getWorld().playSound(target, Sound.BLOCK_FIRE_EXTINGUISH, 1, 1.2f);
                }
            }
            case RABBIT_FOOT -> {
                if (backUsed.contains(player.getUniqueId()) || !near(player.getLocation(), markers.point("gh.cave.door"), 5)
                        && !near(player.getLocation(), markers.point("gh.cave.pile"), 5)) { player.sendActionBar(Text.mm("<yellow>The back exit is a one-use cave escape.")); return; }
                Location exit = markers.point("gh.cave.back");
                if (exit != null) { backUsed.add(player.getUniqueId()); player.teleport(exit); lastFoot.remove(player.getUniqueId()); immuneUntil.put(player.getUniqueId(), tick + 60); }
            }
            default -> {}
        }
    }
    private boolean escapeHeart(Player player) {
        if (!heartUsed.add(player.getUniqueId())) return false;
        immuneUntil.put(player.getUniqueId(), tick + 100); hits.put(player.getUniqueId(), 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false));
        game.broadcast("<light_purple>A Grinch's heart grew three sizes! One last burst of Christmas cheer!");
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 12, 1, 1, 1, 0);
        for (Player villager : game.alivePlayers()) if (!isGrinch(villager) && near(villager.getLocation(), player.getLocation(), 4)) {
            org.bukkit.util.Vector away = villager.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 0.1) villager.setVelocity(away.normalize().multiply(0.7).setY(0.25));
        }
        return true;
    }
    private void villagerAbility(Player player) {
        VillagerRole role = roles.getOrDefault(player.getUniqueId(), VillagerRole.TRACKER);
        if (role == VillagerRole.BELL_RINGER && markers.keys("gh.bell.").stream().map(markers::point).noneMatch(at -> near(player.getLocation(), at, 4))) {
            player.sendActionBar(Text.mm("<yellow>Climb a bell tower to ring its bell.")); return;
        }
        int cooldown = switch (role) { case SNOWBALLER -> 25; case TRACKER -> 300; case LAMPLIGHTER -> 400; case BELL_RINGER -> 500; case ELF_INVENTOR, NIGHT_WATCH -> 600; };
        if (!ready(player, role.name(), cooldown)) return;
        switch (role) {
            case TRACKER -> {
                Print freshest = freshestPrint();
                if (freshest != null) { player.setCompassTarget(freshest.at); player.sendActionBar(Text.mm("<green>Compass follows the freshest footprints, including decoys!")); }
                Wolf existing = dogs.get(player.getUniqueId());
                if (existing == null || !existing.isValid()) {
                    if (dogs.size() >= 6) return;
                    Location kennel = markers.point("gh.kennel");
                    if (kennel == null) kennel = player.getLocation();
                    Wolf dog = kennel.getWorld().spawn(kennel, Wolf.class, entity -> {
                        entity.setTamed(true); entity.setOwner(player); entity.setInvulnerable(true); entity.setPersistent(false);
                        entity.setCollidable(false); entity.customName(Component.text("Sniffer Dog")); entity.setCustomNameVisible(true);
                    }); dogs.put(player.getUniqueId(), dog);
                    if (visuals.custom()) {
                        ItemDisplay model = visuals.spawn(kennel, "christmas_dog", Material.BONE, 1);
                        if (model != null) { dog.setInvisible(true); dogModels.put(player.getUniqueId(), model); }
                    }
                }
            }
            case LAMPLIGHTER -> {
                Location at = player.getLocation();
                at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 2, 0), 45, 6, 2, 6, 0);
                at.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);
                revealNearby(player, 10, 100);
                // Change only existing decorative light blocks in the cloned arena; no new collision blocks.
                for (int x = -6; x <= 6; x++) for (int y = -1; y <= 5; y++) for (int z = -6; z <= 6; z++) {
                    org.bukkit.block.Block block = at.getBlock().getRelative(x, y, z);
                    if (block.getType() == Material.LIGHT && block.getBlockData() instanceof org.bukkit.block.data.type.Light light) {
                        org.bukkit.block.data.BlockData original = block.getBlockData().clone(); light.setLevel(15); block.setBlockData(light, false);
                        game.runLater(120, () -> block.setBlockData(original, false));
                    }
                }
            }
            case SNOWBALLER -> {
                if (snowballs.size() >= 24) return;
                Snowball snowball = player.launchProjectile(Snowball.class, player.getEyeLocation().getDirection().multiply(1.5));
                snowballs.put(snowball.getUniqueId(), snowball);
                game.runLater(80, () -> { snowballs.remove(snowball.getUniqueId()); snowball.remove(); });
            }
            case BELL_RINGER -> {
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 2, 1.2f);
                for (Player grinch : game.alivePlayers()) if (activeGrinch(grinch) && near(grinch.getLocation(), player.getLocation(), 28)) {
                    grinch.getWorld().playSound(grinch.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.8f, 1.7f);
                    grinch.getWorld().spawnParticle(Particle.NOTE, grinch.getLocation().add(0, 2, 0), 5, 0.3, 0.1, 0.3, 0);
                }
            }
            case ELF_INVENTOR -> {
                if (traps.size() >= 8) return;
                Location at = player.getLocation();
                ItemDisplay present = visuals.spawn(at.clone().add(0, 0.35, 0), "christmas_present_gold", Material.YELLOW_WOOL, 0.65f);
                if (present != null) traps.add(new Trap(at, tick + 600, present));
            }
            case NIGHT_WATCH -> {
                player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 5, 0), 50, 8, 1, 8, 0.03);
                revealNearby(player, 18, 120);
            }
        }
    }
    private void revealNearby(Player viewer, double radius, int duration) {
        for (Player grinch : game.alivePlayers()) if (activeGrinch(grinch) && near(viewer.getLocation(), grinch.getLocation(), radius)
                && viewer.hasLineOfSight(grinch)) reveal(grinch, duration);
    }
    @Override public void onProjectileHit(ProjectileHitEvent event) {
        if (snowballs.remove(event.getEntity().getUniqueId()) == null) return;
        event.setCancelled(true); event.getEntity().remove();
        if (game.state() != GameState.RUNNING || !(event.getEntity().getShooter() instanceof Player shooter) || !game.isActive(shooter)) return;
        Location at = event.getHitEntity() == null ? event.getEntity().getLocation() : event.getHitEntity().getLocation();
        at.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1, 0), 24, 2, 1, 2, 0.02);
        for (Player grinch : game.alivePlayers()) if (activeGrinch(grinch) && near(at, grinch.getLocation(), 2.5)) {
            reveal(grinch, 100);
            if (event.getHitEntity() == grinch) hit(grinch, shooter);
        }
    }
    @Override public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {
        event.setCancelled(true);
        if (event.getDamager() instanceof Player && activeGrinch(victim) && !isGrinch(damager)) hit(victim, damager);
    }
    @Override public void onDamage(Player victim, EntityDamageEvent event) { event.setCancelled(true); }
    private void hit(Player grinch, Player villager) {
        UUID id = grinch.getUniqueId();
        if (!activeGrinch(grinch) || tick < immuneUntil.getOrDefault(id, 0) || tick - lastHit.getOrDefault(id, -100) < 10) return;
        lastHit.put(id, tick); int count = hits.merge(id, 1, Integer::sum); tags.merge(villager.getUniqueId(), 1, Integer::sum);
        reveal(grinch, 80); game.addScore(villager, 1);
        villager.sendActionBar(Text.mm("<gold>Grinch tag: " + count + "/3"));
        if (tagged(count, 0)) catchGrinch(grinch, villager);
    }
    private void catchGrinch(Player grinch, Player villager) {
        UUID id = grinch.getUniqueId();
        if (!activeGrinch(grinch) || tick < immuneUntil.getOrDefault(id, 0)) return;
        if (escapeHeart(grinch)) return;
        int saved = cargo(grinch).size(); returnCargo(grinch);
        recovered.merge(villager.getUniqueId(), saved, Integer::sum); caught.add(id); restore(grinch);
        grinch.setGameMode(org.bukkit.GameMode.SPECTATOR);
        Location viewpoint = markers.point("gh.spectator"); if (viewpoint != null) grinch.teleport(viewpoint);
        game.broadcast("<gold>" + villager.getName() + " caught a Grinch and saved " + saved + " presents!");
        if (game.alivePlayers().stream().noneMatch(this::activeGrinch)) finish(false, "grinches-caught");
    }
    private void returnCargo(Player player) {
        for (String key : cargo(player)) {
            Tree tree = trees.get(key); if (tree != null) { tree.stock = Math.min(3, tree.stock + 1); renderTree(tree); }
        }
        cargo(player).clear();
    }
    private void finish(boolean grinchWin, String reason) {
        if (ended) return;
        List<Player> winners = game.alivePlayers().stream().filter(p -> grinchWin ? activeGrinch(p) : !isGrinch(p)).toList();
        game.end(winners, reason);
    }
    @Override public void onSecond(int elapsed) {
        if (game.alivePlayers().isEmpty()) { game.end(List.of(), "empty-village"); return; }
        if (game.alivePlayers().stream().noneMatch(this::activeGrinch)) { finish(false, "no-grinch"); return; }
        if (game.alivePlayers().stream().noneMatch(p -> !isGrinch(p))) { finish(true, "no-villagers"); return; }
        if (elapsed == timeLimitSeconds() / 2 && banked < 4) game.broadcast("<green>Catch-up cheer: the Grinch gets a little speed to help fill the cave!");
        if (banked >= 10 && elapsed % 15 == 0) {
            for (Player grinch : game.alivePlayers()) if (activeGrinch(grinch)) {
                grinch.getWorld().playSound(grinch.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 1.8f);
                reveal(grinch, 30);
            }
            game.broadcast("<gold>Catch-up bells! The village hears the Grinch's trail.");
        }
    }
    @Override public void onTimeUp() {
        int carried = game.alivePlayers().stream().filter(this::activeGrinch).mapToInt(p -> cargo(p).size()).sum();
        int heldMinimum = Math.max(1, Math.min(15, game.arena().settings().getInt("timeout-held-min", 1)));
        finish(grinchWins(banked, carried, heldMinimum), "christmas-morning");
    }
    @Override public String phaseCountdown() { return "Christmas morning in " + Math.max(0, timeLimitSeconds() - tick / 20) + "s"; }
    @Override public EndSummary endSummary(List<Player> winners) {
        boolean grinchWin = winners.stream().anyMatch(this::isGrinch);
        return new EndSummary(grinchWin ? "A very Grinchy Christmas!" : "Christmas is saved!",
                grinchWin ? "The cave is full of Christmas cheer" : "The village celebrates together",
                List.of("<gold>Cave presents: " + banked + "/15", mvp("Present Pilferer", stolen), mvp("Village Tracker", tags), mvp("Christmas Saver", recovered)));
    }
    private String mvp(String title, Map<UUID, Integer> scores) {
        return scores.entrySet().stream().max(Map.Entry.comparingByValue()).map(entry -> "<green>" + title + ": " + game.players().stream()
                .filter(player -> player.getUniqueId().equals(entry.getKey())).map(Player::getName).findFirst().orElse("a festive friend") + " (" + entry.getValue() + ")").orElse("<gray>" + title + ": next time!");
    }
    @Override public void onEnd(List<Player> winners) {
        ended = true; if (loop != null) loop.cancel();
        for (Player player : game.players()) {
            restore(player);
            player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 2, 0), 15, 2, 1, 2, 0.01);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }
        cleanupEntities();
    }
    private void restore(Player player) {
        Boolean invisible = invisibleBefore.remove(player.getUniqueId()); if (invisible != null) player.setInvisible(invisible);
        Float speed = speeds.remove(player.getUniqueId()); if (speed != null) player.setWalkSpeed(speed);
        for (PotionEffectType type : List.of(PotionEffectType.SPEED, PotionEffectType.SLOWNESS, PotionEffectType.GLOWING, PotionEffectType.BLINDNESS)) player.removePotionEffect(type);
        Map<EquipmentSlot, ItemStack> actual = new EnumMap<>(EquipmentSlot.class);
        if (player.getEquipment() != null) for (EquipmentSlot slot : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
            actual.put(slot, player.getEquipment().getItem(slot));
        for (Player viewer : game.players()) if (viewer != player && !actual.isEmpty()) viewer.sendEquipmentChange(player, actual);
    }
    @Override public void onQuit(Player player) {
        if (!ended && visuals != null && isGrinch(player)) { returnCargo(player); caught.add(player.getUniqueId()); }
        Wolf dog = dogs.remove(player.getUniqueId()); if (dog != null) dog.remove(); restore(player); holds.remove(player.getUniqueId());
        ItemDisplay model = dogModels.remove(player.getUniqueId()); if (model != null && visuals != null) visuals.remove(model);
    }
    @Override public void onDeath(Player victim, Player killer) {
        if (isGrinch(victim)) returnCargo(victim);
        game.respawn(victim, 2);
    }
    @Override public void onRespawn(Player player) {
        teleportSpawn(player, isGrinch(player) ? "gh.grinch.spawn" : "gh.villager.spawn", 0);
        if (isGrinch(player) && !caught.contains(player.getUniqueId())) player.setInvisible(true);
        equip(player); immuneUntil.put(player.getUniqueId(), tick + 60);
    }
    private void cleanupEntities() {
        if (visuals != null) visuals.close(); dogs.values().forEach(Entity::remove); dogs.clear();
        snowballs.values().forEach(Entity::remove); snowballs.clear(); prints.clear(); traps.clear(); eyes.clear(); dogModels.clear();
    }
    @Override public void onCleanup() {
        ended = true; if (loop != null) loop.cancel();
        for (Player player : game.players()) restore(player); cleanupEntities();
        if (started && game.world() != null) { game.world().setTime(originalTime); game.world().setStorm(originalStorm); }
    }
    @Override public void onInventoryClick(Player player, InventoryClickEvent event) { event.setCancelled(true); }
    @Override public void onInventoryDrag(Player player, org.bukkit.event.inventory.InventoryDragEvent event) { event.setCancelled(true); }
    @Override public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(isGrinch(player) ? "<green>Role: Grinch" : "<gold>Role: " + roles.getOrDefault(player.getUniqueId(), VillagerRole.TRACKER).name().replace('_', ' '));
        lines.add("<gold>Cave presents: " + banked + "/15"); lines.add(phaseCountdown());
        if (isGrinch(player)) { lines.add("<green>Carrying: " + cargo(player).size() + "/3"); lines.add("<gray>Tag hits: " + hits.getOrDefault(player.getUniqueId(), 0) + "/3"); }
        else { lines.add("<aqua>Follow fresh green footprints"); lines.add("<gray>3 hits or sneak-hold 3s"); }
        return lines;
    }
}
