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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import uk.co.playerready.pulsegames.core.bot.BotMovement;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.seasonal.ChristmasMarkers;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.*;

import static uk.co.playerready.pulsegames.games.christmas.PresentRules.*;

/** All inventory is round-owned state: display items can never become survival rewards. */
public final class PresentPanicGame extends MiniGame implements BotMovement {
    private static final List<String> TEAMS = List.of("red", "blue", "green", "gold");
    private static final Material[] COLOUR_BLOCKS = {Material.RED_WOOL, Material.GREEN_WOOL, Material.BLUE_WOOL, Material.YELLOW_WOOL};
    private enum Event { NONE, CONVEYOR_RUSH, PAPER_SHORTAGE, NAUGHTY_LIST, SNOW_SQUALL }
    private record Belt(List<Location> nodes) {}
    private static final class Toy {
        final Present present;
        final ItemDisplay display;
        final Belt belt;
        final int born;
        double distance;
        Toy(Present present, ItemDisplay display, Belt belt, int born) {
            this.present = present; this.display = display; this.belt = belt; this.born = born;
        }
    }
    private record Wrap(String station, int ticks) {}
    private final ChristmasMarkers markers;
    private final Random random = new Random();
    private final Map<UUID, Deque<Present>> carrying = new HashMap<>();
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Map<UUID, Float> speeds = new HashMap<>();
    private final Map<UUID, Wrap> wrapping = new HashMap<>();
    private final Set<UUID> wrapAssist = new HashSet<>();
    private final Map<UUID, Integer> throwsAt = new HashMap<>(), roleAt = new HashMap<>();
    private final Map<UUID, Integer> wraps = new HashMap<>(), steals = new HashMap<>(), delivered = new HashMap<>();
    private final Map<UUID, Snowball> snowballs = new HashMap<>();
    private final List<Toy> toys = new ArrayList<>();
    private final List<Belt> belts = new ArrayList<>();
    private final Map<Integer, Integer> points = new HashMap<>();
    private ChristmasVisuals visuals;
    private BukkitTask loop;
    private int tick;
    private Event event = Event.NONE;
    private int eventUntil;
    private int naughtyTeam = -1;
    private boolean ended;

    public PresentPanicGame(GameInstance game) { super(game); markers = new ChristmasMarkers(game); }

    @Override public boolean managesCompletion() { return true; }
    @Override public boolean environmentalDamage() { return false; }
    @Override public boolean pvp() { return true; }
    @Override public boolean respawnable() { return true; }
    @Override public int maxTeams() { return 4; }
    @Override public int timeLimitSeconds() { return Math.max(60, Math.min(1200, game.arena().settings().getInt("time-limit", 300))); }
    @Override public int startFreezeSeconds() { return 0; }

    @Override public String startProblem(List<Player> players) {
        if (game.plugin().seasons() == null || !game.plugin().seasons().gameEnabled("presentpanic")) return "Present Panic is not in the active season.";
        if (players.stream().anyMatch(p -> !p.hasMetadata("NPC") && !game.plugin().seasons().canPlay(p, "presentpanic")))
            return "Present Panic is limited to the current seasonal trial players.";
        var markerProblems = uk.co.playerready.pulsegames.core.seasonal.MarkerContract.savedProblems("presentpanic", game.arena().settings().get("markers"));
        if (!markerProblems.isEmpty()) return markerProblems.getFirst();
        for (String team : TEAMS) if (markers.points("pp.spawn." + team).isEmpty() || markers.point("pp.sleigh." + team) == null)
            return "Present Panic needs the pp.spawn and pp.sleigh markers for every team.";
        if (markers.keys("pp.wrap.").isEmpty() || markers.point("pp.belt.1.feed") == null) return "Import the Present Panic map markers first.";
        return null;
    }

    @Override public void onStart() {
        visuals = new ChristmasVisuals(game);
        for (int n = 1; n <= 2; n++) {
            String prefix = "pp.belt." + n + ".";
            List<Location> path = new ArrayList<>();
            Location feed = markers.point(prefix + "feed");
            if (feed != null) path.add(feed);
            for (String key : markers.keys(prefix)) if (key.substring(prefix.length()).matches("[0-9]+")) path.addAll(markers.points(key));
            if (path.size() > 1) belts.add(new Belt(path));
        }
        int index = 0;
        Map<Integer, Integer> nextSpawn = new HashMap<>();
        for (Player player : game.alivePlayers()) {
            UUID id = player.getUniqueId();
            roles.put(id, Role.values()[index++ % Role.values().length]);
            carrying.put(id, new ArrayDeque<>());
            speeds.put(id, player.getWalkSpeed());
            int team = team(player);
            List<Location> spawns = markers.points("pp.spawn." + TEAMS.get(team));
            int spawn = nextSpawn.merge(team, 1, Integer::sum) - 1;
            player.teleport(spawns.get(spawn % spawns.size()));
            equip(player);
        }
        for (int team = 0; team < game.teams().size(); team++) {
            String colour = TEAMS.get(team);
            visuals.spawn(markers.point("pp.sleigh." + colour).clone().add(0, 0.7, 0), "christmas_sleigh", Material.MINECART, 1.8f);
        }
        for (String station : markers.keys("pp.wrap.")) {
            Colour colour = Colour.valueOf(station.split("\\.")[2].toUpperCase(Locale.ROOT));
            visuals.spawn(markers.point(station).clone().add(0, 0.35, 0), "christmas_wrapping_paper_" + colour.key(), material(colour), 0.75f);
        }
        game.broadcast("<gold>Grab toys from the belt. Sneak by a wrapping bench, or use a carried toy to toggle wrapping. Match the toy colour, add a bow, load your sleigh!");
        game.broadcast("<yellow>Blaze rod: snowball. Iron nugget: change elf role. Snowball hits drop the top present; anyone can steal it.");
        loop = game.runRepeating(1, 2, this::step);
    }

    private int team(Player player) { return game.teamOf(player) == null ? 0 : Math.min(3, game.teamOf(player).index()); }
    private Role role(Player player) { return roles.getOrDefault(player.getUniqueId(), Role.SPEEDY); }
    private Deque<Present> stack(Player player) { return carrying.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>()); }
    private static Material material(Colour colour) { return COLOUR_BLOCKS[colour.ordinal()]; }
    private static boolean near(Location a, Location b, double radius) {
        return a != null && b != null && a.getWorld() == b.getWorld() && a.distanceSquared(b) <= radius * radius;
    }
    private void equip(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(0, tool(Material.BLAZE_ROD, "Snowball thrower", "Use to knock loose a rival's top present"));
        player.getInventory().setItem(8, tool(Material.IRON_NUGGET, "Elf role: " + role(player), "Use to switch role (15 second cooldown)"));
        updateCarry(player);
    }
    private static ItemStack tool(Material material, String title, String lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> { meta.displayName(Component.text(title)); meta.lore(List.of(Component.text(lore))); });
        return item;
    }
    private void updateCarry(Player player) {
        player.setWalkSpeed(walkSpeed(role(player), stack(player).size()));
        for (int slot = 1; slot <= 4; slot++) player.getInventory().setItem(slot, null);
        int slot = 1;
        for (Present present : stack(player)) player.getInventory().setItem(slot++, tool(material(present.toy()),
                present.toy().key() + " toy" + (present.paper() == null ? " · unwrapped" : " · " + present.paper().key() + " paper" + (present.bow() ? " + bow" : "")), "Use to toggle wrapping at a bench; sneak also works"));
    }
    private void step() {
        if (game.state() != GameState.RUNNING || ended) return;
        tick += 2;
        if (tick % 20 == 0) visuals.refreshViewers();
        if (event != Event.NONE && tick >= eventUntil) { event = Event.NONE; naughtyTeam = -1; game.broadcast("<green>The workshop is back to normal!"); }
        if (tick % (event == Event.CONVEYOR_RUSH ? 20 : 40) == 0) for (Belt belt : belts) spawnToy(belt);
        Iterator<Toy> iterator = toys.iterator();
        while (iterator.hasNext()) {
            Toy toy = iterator.next();
            if (!toy.display.isValid() || tick - toy.born > 600) { visuals.remove(toy.display); iterator.remove(); continue; }
            if (toy.belt != null) moveToy(toy);
            for (Player player : game.alivePlayers()) if (game.isActive(player) && !(toy.belt == null && toy.present.lastTeam() == team(player) && tick - toy.born < 20)
                    && stack(player).size() < capacity(role(player)) && near(player.getLocation(), toy.display.getLocation(), 1.6)) {
                Present picked = toy.present.pickedUp(team(player));
                stack(player).addLast(picked);
                if (toy.present.lastTeam() >= 0 && toy.present.lastTeam() != team(player)) steals.merge(player.getUniqueId(), 1, Integer::sum);
                visuals.remove(toy.display); iterator.remove(); updateCarry(player);
                player.playSound(player.getLocation(), "minecraft:entity.item.pickup", 0.5f, 1.4f);
                break;
            }
        }
        for (Player player : game.alivePlayers()) {
            if (!game.isActive(player)) continue;
            processWrap(player, player.hasMetadata("NPC") ? botWrapping(player) : player.isSneaking() || wrapAssist.contains(player.getUniqueId()));
            Location sleigh = markers.point("pp.sleigh." + TEAMS.get(team(player)));
            if (near(player.getLocation(), sleigh, 2.2)) deposit(player);
            if (event == Event.SNOW_SQUALL && tick % 10 == 0) {
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 8, 2, 1.5, 2, 0.01);
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 14, 0, false, false));
            }
        }
        if (event == Event.NAUGHTY_LIST && tick % 10 == 0) {
            Location board = markers.point("pp.event.naughty");
            if (board != null) board.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, board, 12, 1, 1, 0.5, 0);
        }
    }
    private void spawnToy(Belt belt) {
        if (toys.size() >= 60 || visuals.size() >= 100) return;
        Colour colour = Colour.values()[random.nextInt(4)];
        ItemDisplay display = visuals.spawn(belt.nodes.getFirst().clone().add(0, 0.35, 0), "christmas_present_" + colour.key(), material(colour), 0.65f);
        if (display != null) toys.add(new Toy(new Present(colour, null, false, -1, false), display, belt, tick));
    }
    private void moveToy(Toy toy) {
        toy.distance += event == Event.CONVEYOR_RUSH ? 0.32 : 0.16;
        double remaining = toy.distance;
        List<Location> nodes = toy.belt.nodes;
        for (int i = 1; i < nodes.size(); i++) {
            Location from = nodes.get(i - 1), to = nodes.get(i);
            double length = from.distance(to);
            if (remaining <= length && length > 0) {
                Location at = from.clone().add(to.toVector().subtract(from.toVector()).multiply(remaining / length)).add(0, 0.35, 0);
                toy.display.teleport(at); return;
            }
            remaining -= length;
        }
        toy.distance = 0;
        toy.display.teleport(nodes.getFirst().clone().add(0, 0.35, 0));
    }
    private String wrappingStation(Player player) {
        Location at = player.getLocation();
        return markers.keys("pp.wrap.").stream().filter(key -> near(at, markers.point(key), 2.6))
                .min(Comparator.comparingDouble(key -> at.distanceSquared(markers.point(key)))).orElse(null);
    }
    private boolean botWrapping(Player bot) {
        Present next = nextUnwrapped(stack(bot));
        if (next == null) return true; // Bow processing still requires the team's own station.
        String station = wrappingStation(bot);
        return station != null && station.startsWith("pp.wrap." + next.toy().key() + ".");
    }
    private void processWrap(Player player, boolean holding) {
        UUID id = player.getUniqueId();
        if (!holding || stack(player).isEmpty()) { wrapping.remove(id); return; }
        Present top = nextUnwrapped(stack(player));
        if (top == null) {
            if (stack(player).stream().anyMatch(p -> !p.bow()) && near(player.getLocation(), markers.point("pp.bow." + TEAMS.get(team(player))), 2.6)) {
                bowAll(stack(player)); updateCarry(player);
                player.sendActionBar(Text.mm("<gold>Bow added! Correctly matched presents are worth 6."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, 1.5f);
            }
            return;
        }
        String station = wrappingStation(player);
        if (station == null) { wrapping.remove(id); return; }
        Wrap current = wrapping.get(id);
        int progress = current != null && station.equals(current.station) ? current.ticks + 2 : 2;
        int required = wrapTicks(role(player), event == Event.PAPER_SHORTAGE);
        wrapping.put(id, new Wrap(station, progress));
        Colour paper = Colour.valueOf(station.split("\\.")[2].toUpperCase(Locale.ROOT));
        player.sendActionBar(Text.mm((paper == top.toy() ? "<gold>" : "<yellow>Wrong colour! ") + "Wrapping " + Math.min(100, progress * 100 / required) + "% <gray>" + paper.key() + " paper"));
        if (progress >= required) {
            Colour colour = Colour.valueOf(station.split("\\.")[2].toUpperCase(Locale.ROOT));
            wrapNext(stack(player), colour);
            wraps.merge(id, 1, Integer::sum); wrapping.remove(id); updateCarry(player);
            visuals.sound(player.getLocation(), "wrap", Sound.BLOCK_WOOL_PLACE, 1, 1.4f);
        }
    }
    private void deposit(Player player) {
        if (stack(player).isEmpty()) return;
        int earned = 0, amount = 0;
        for (Iterator<Present> iterator = stack(player).iterator(); iterator.hasNext();) {
            Present present = iterator.next();
            if (present.paper() == null) continue;
            earned += score(present, timeLimitSeconds() * 20 - tick <= 600);
            amount++; iterator.remove();
        }
        if (amount == 0) {
            if (tick % 20 == 0) player.sendActionBar(Text.mm("<yellow>Wrap your toys first. Unfinished toys stay in your stack."));
            return;
        }
        if (event == Event.NAUGHTY_LIST && team(player) == naughtyTeam) earned++;
        points.merge(team(player), earned, Integer::sum);
        game.addScore(player, earned);
        delivered.merge(player.getUniqueId(), amount, Integer::sum);
        wrapping.remove(player.getUniqueId()); updateCarry(player);
        player.sendActionBar(Text.mm("<green>Loaded! +" + earned + " team points"));
        visuals.sound(player.getLocation(), "delivery", Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.3f);
    }
    private void dropTop(Player player) {
        if (stack(player).isEmpty()) return;
        Present top = stack(player).removeLast();
        ItemDisplay display = visuals.spawn(player.getLocation().clone().add(0, 0.3, 0),
                "christmas_present_" + top.toy().key(), material(top.paper() == null ? top.toy() : top.paper()), 0.6f);
        if (display != null) toys.add(new Toy(top, display, null, tick));
        updateCarry(player); wrapping.remove(player.getUniqueId());
    }
    public boolean throwSnowball(Player player) {
        UUID id = player.getUniqueId();
        if (game.state() != GameState.RUNNING || game.frozen() || !game.isActive(player) || snowballs.size() >= 48 || tick < throwsAt.getOrDefault(id, 0)) return false;
        int cooldown = role(player) == Role.SNOWBALLER ? 12 : 30;
        throwsAt.put(id, tick + cooldown);
        Snowball ball = player.launchProjectile(Snowball.class, player.getEyeLocation().getDirection().multiply(1.4));
        snowballs.put(ball.getUniqueId(), ball);
        game.runLater(80, () -> { snowballs.remove(ball.getUniqueId()); ball.remove(); });
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.6f, 1.3f);
        return true;
    }
    @Override public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick() || event.getItem() == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!game.isActive(player)) return;
        if (java.util.Arrays.asList(COLOUR_BLOCKS).contains(event.getItem().getType())) {
            if (!wrapAssist.add(player.getUniqueId())) wrapAssist.remove(player.getUniqueId());
            wrapping.remove(player.getUniqueId());
            player.sendActionBar(Text.mm(wrapAssist.contains(player.getUniqueId()) ? "<green>Wrapping ON: stand by a bench or bow station" : "<gray>Wrapping OFF: sneak to wrap manually"));
        }
        if (event.getItem().getType() == Material.BLAZE_ROD) throwSnowball(player);
        if (event.getItem().getType() == Material.IRON_NUGGET && tick >= roleAt.getOrDefault(player.getUniqueId(), 0)) {
            Role next = Role.values()[(role(player).ordinal() + 1) % Role.values().length];
            if (stack(player).size() > capacity(next)) { player.sendActionBar(Text.mm("<yellow>Deliver before changing out of Packer.")); return; }
            roles.put(player.getUniqueId(), next); roleAt.put(player.getUniqueId(), tick + 300); equip(player);
            player.sendMessage(Text.msg("<green>You are now " + next + "."));
        }
    }
    @Override public void onProjectileHit(ProjectileHitEvent event) {
        if (snowballs.remove(event.getEntity().getUniqueId()) == null) return;
        event.setCancelled(true); event.getEntity().remove();
        if (game.state() != GameState.RUNNING || !(event.getEntity().getShooter() instanceof Player shooter)
                || !(event.getHitEntity() instanceof Player victim) || !game.isActive(victim) || !game.isActive(shooter)
                || team(shooter) == team(victim)) return;
        Vector away = victim.getLocation().toVector().subtract(shooter.getLocation().toVector()).setY(0);
        if (away.lengthSquared() > 0.01) victim.setVelocity(away.normalize().multiply(0.65).setY(0.22));
        dropTop(victim);
        victim.sendActionBar(Text.mm("<yellow>Snowball hit! Check your stack for a dropped present."));
        victim.playSound(victim.getLocation(), Sound.BLOCK_SNOW_BREAK, 0.45f, 1.3f);
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0);
    }
    @Override public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) { event.setCancelled(true); }
    @Override public void onDamage(Player victim, EntityDamageEvent event) { event.setCancelled(true); }
    @Override public void onInventoryClick(Player player, InventoryClickEvent event) { event.setCancelled(true); }
    @Override public void onInventoryDrag(Player player, org.bukkit.event.inventory.InventoryDragEvent event) { event.setCancelled(true); }

    @Override public void onSecond(int elapsed) {
        for (Player player : game.alivePlayers()) if (!player.hasMetadata("NPC") && !wrapping.containsKey(player.getUniqueId())) {
            Present next = nextUnwrapped(stack(player));
            String hint = stack(player).isEmpty() ? "Grab a toy from a conveyor"
                    : next != null ? "Find " + next.toy().key() + " paper; sneak or use toy"
                    : stack(player).stream().anyMatch(p -> !p.bow()) ? "Add bows, then load your " + TEAMS.get(team(player)) + " sleigh"
                    : "Load your " + TEAMS.get(team(player)) + " sleigh!";
            player.sendActionBar(Text.mm("<yellow>" + hint));
        }
        if (elapsed > 0 && elapsed % 45 == 0 && timeLimitSeconds() - elapsed > 30) {
            event = Event.values()[1 + random.nextInt(4)]; eventUntil = tick + 300;
            naughtyTeam = java.util.stream.IntStream.range(0, Math.max(1, game.teams().size())).boxed()
                    .min(Comparator.comparingInt(team -> points.getOrDefault(team, 0))).orElse(0);
            game.broadcast("<gold><b>" + event.name().replace('_', ' ') + "!</b> <yellow>" + switch (event) {
                case CONVEYOR_RUSH -> "Double toy supply and belt speed for 15 seconds!";
                case PAPER_SHORTAGE -> "Wrapping takes twice as long for 15 seconds!";
                case NAUGHTY_LIST -> TEAMS.get(naughtyTeam) + " needs help: +1 per sleigh visit for 15 seconds!";
                case SNOW_SQUALL -> "Snow slows every elf for 15 seconds!";
                default -> "";
            });
        }
        if (timeLimitSeconds() - elapsed == 30) game.broadcast("<gold><b>Final 30 seconds! DOUBLE POINTS! Sleighs leave soon!");
        if (game.alivePlayers().isEmpty()) game.end(List.of(), "empty-workshop");
    }
    @Override public String phaseCountdown() { return "Sleigh departs in " + Math.max(0, timeLimitSeconds() - tick / 20) + "s"; }
    @Override public void onTimeUp() {
        int best = points.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Player> winners = best <= 0 ? List.of() : game.alivePlayers().stream().filter(p -> points.getOrDefault(team(p), 0) == best).toList();
        game.end(winners, "sleigh-launch");
    }
    @Override public EndSummary endSummary(List<Player> winners) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < game.teams().size(); i++) lines.add("<gold>" + TEAMS.get(i) + " sleigh: " + points.getOrDefault(i, 0));
        lines.add(mvp("Master Wrapper", wraps)); lines.add(mvp("Present Packer", delivered)); lines.add(mvp("Sneaky Elf", steals));
        int best = points.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> leaders = java.util.stream.IntStream.range(0, game.teams().size()).filter(i -> best > 0 && points.getOrDefault(i,0) == best).mapToObj(TEAMS::get).toList();
        String result = winners.isEmpty() ? "No winning sleigh this time" : String.join(" / ", leaders) + (leaders.size() == 1 ? " sleigh wins!" : " sleighs share the win!");
        return new EndSummary("Sleighs away!", result, lines);
    }
    private String mvp(String name, Map<UUID, Integer> values) {
        return values.entrySet().stream().max(Map.Entry.comparingByValue()).map(entry -> "<green>" + name + ": " + game.players().stream()
                .filter(p -> p.getUniqueId().equals(entry.getKey())).map(Player::getName).findFirst().orElse("an elf") + " (" + entry.getValue() + ")").orElse("<gray>" + name + ": next time!");
    }
    @Override public void onEnd(List<Player> winners) {
        ended = true;
        if (loop != null) loop.cancel();
        for (Player player : game.players()) restore(player);
        snowballs.values().forEach(Entity::remove); snowballs.clear();
        // The shared ending lasts ten seconds: fly decorative sleighs along the builders' launch markers.
        if (visuals != null) visuals.close();
        for (int i = 0; i < game.teams().size(); i++) {
            String colour = TEAMS.get(i); Location from = markers.point("pp.sleigh." + colour), to = markers.point("pp.sleigh." + colour + ".launch");
            if (from == null || to == null || visuals == null) continue;
            ItemDisplay sleigh = visuals.spawn(from.clone().add(0, 1, 0), "christmas_sleigh", Material.MINECART, 2);
            if (sleigh == null) continue;
            visuals.sound(from, "launch", Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.2f);
            for (int step = 1; step <= 40; step++) {
                final double part = step / 40.0;
                game.runLater(step * 2L, () -> {
                    if (!sleigh.isValid()) return;
                    Location at = from.clone().add(to.toVector().subtract(from.toVector()).multiply(part)).add(0, 1, 0);
                    sleigh.teleport(at); at.getWorld().spawnParticle(Particle.FIREWORK, at, 3, 0.3, 0.2, 0.3, 0.01);
                });
            }
        }
    }
    private void restore(Player player) {
        Float original = speeds.remove(player.getUniqueId()); if (original != null) player.setWalkSpeed(original);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
    }
    @Override public void onQuit(Player player) {
        if (!ended && visuals != null) while (!stack(player).isEmpty()) dropTop(player);
        carrying.remove(player.getUniqueId()); wrapping.remove(player.getUniqueId()); wrapAssist.remove(player.getUniqueId()); restore(player);
    }
    @Override public void onDeath(Player victim, Player killer) {
        while (!stack(victim).isEmpty()) dropTop(victim);
        game.respawn(victim, 2);
    }
    @Override public void onRespawn(Player player) {
        List<Location> spawns = markers.points("pp.spawn." + TEAMS.get(team(player)));
        if (!spawns.isEmpty()) player.teleport(spawns.getFirst());
        equip(player);
    }
    @Override public void onCleanup() {
        ended = true; if (loop != null) loop.cancel();
        for (Player player : game.players()) restore(player);
        if (visuals != null) visuals.close(); snowballs.values().forEach(Entity::remove);
        toys.clear(); snowballs.clear(); carrying.clear(); wrapping.clear(); wrapAssist.clear();
    }
    @Override public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>(List.of("<gold>" + role(player) + " <yellow>Carry: " + stack(player).size() + "/" + capacity(role(player)), phaseCountdown()));
        for (int i = 0; i < game.teams().size(); i++) lines.add((i == team(player) ? "<gold>Your " : "<gray>") + TEAMS.get(i) + ": <white>" + points.getOrDefault(i, 0));
        lines.add("<aqua>Sneak at a bench to wrap");
        if (event != Event.NONE) lines.add("<gold>" + event.name().replace('_', ' '));
        return lines;
    }

    @Override public Location botDestination(Player bot) {
        Deque<Present> stack = stack(bot);
        if (!stack.isEmpty()) {
            Present top = nextUnwrapped(stack);
            if (top != null) return markers.keys("pp.wrap." + top.toy().key() + ".").stream().map(markers::point)
                    .min(Comparator.comparingDouble(p -> p.distanceSquared(bot.getLocation()))).orElse(bot.getLocation());
            if (stack.stream().anyMatch(p -> !p.bow())) return markers.point("pp.bow." + TEAMS.get(team(bot)));
            return markers.point("pp.sleigh." + TEAMS.get(team(bot)));
        }
        return toys.stream().filter(t -> t.belt != null && t.display.isValid()).map(t -> t.display.getLocation())
                .min(Comparator.comparingDouble(p -> p.distanceSquared(bot.getLocation()))).orElse(markers.point("pp.belt.1.feed"));
    }
}
