package uk.co.playerready.pulsegames.games.partygames;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.common.Fireworks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Party Games: a shuffle-bag rotation of quick-fire micro-games (see {@link Micro}). Each round opens
 * with a title card and 3 s countdown; placements score 5/3/1 plus 1 for taking part (double in an
 * occasional chaos round); a podium reveal closes the match. Knocked-out players watch from the hub.
 * Arena: regions floor (Color Rush, Spleef, TNT Run, Memory), sumo (Sumo, Hot Potato), finish (Sprint);
 * Simon Says needs nothing. Optional settings: rounds, chaos-chance (percent), podium1..3 locations.
 */
public final class PartyGamesGame extends MiniGame {

    private static final Material[] COLORS = {
            Material.RED_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL,
            Material.YELLOW_WOOL, Material.PURPLE_WOOL
    };
    private static final int COUNTDOWN_SECONDS = 3;

    private Micro current = Micro.NONE;
    private int round = 0;
    private int phaseTimer = 5;
    private int microStep;
    private int cardTimer;
    private boolean awaitingGo;
    private boolean chaos;
    private int startCount;
    private int roundSerial;
    private int finaleStep = -1;
    private ShuffleBag<Micro> bag;
    private Material targetColor;
    private final java.util.Map<Block, org.bukkit.block.data.BlockData> floorSnapshot = new java.util.LinkedHashMap<>();
    private final Set<UUID> roundAlive = new HashSet<>();
    private final Set<UUID> roundParticipants = new LinkedHashSet<>();
    private final List<UUID> outOrder = new ArrayList<>();
    private final List<UUID> finishOrder = new ArrayList<>();
    private boolean roundDone;

    // Hot Potato
    private UUID potatoHolder;
    private int potatoTimer;
    private int pops;
    private long noTagBackUntil;
    // Memory Tiles
    private final Set<Integer> safeCells = new HashSet<>();
    // Simon Says
    private enum Command {
        JUMP("Jump!"), SNEAK("Crouch and hold!"), LOOK_UP("Look at the sky!"), STAND_STILL("Freeze!");
        final String label;
        Command(String label) { this.label = label; }
    }
    private Command simonCommand;
    private boolean simonSays;
    private int sprintDeadline = Integer.MAX_VALUE;
    private final Set<UUID> jumped = new HashSet<>();
    private final Set<UUID> moved = new HashSet<>();
    private final Set<UUID> heldAtStart = new HashSet<>();

    public PartyGamesGame(GameInstance game) {
        super(game);
    }

    private int totalRounds() { return game.arena().settings().getInt("rounds", 5); }

    @Override
    public void onStart() {
        game.broadcast("<yellow><b>PARTY TIME!</b></yellow> <gray>" + totalRounds()
                + " quick-fire rounds. Place high to earn points: 5, 3 and 1 - plus 1 for joining in!");
    }

    private boolean running() {
        return current != Micro.NONE && !awaitingGo && cardTimer <= 0 && !roundDone;
    }

    @Override
    public void onSecond(int gameTime) {
        if (finaleStep >= 0) {
            tickFinale();
            return;
        }
        if (current == Micro.NONE) {
            if (--phaseTimer <= 0) nextRound();
            return;
        }
        if (awaitingGo || cardTimer > 0) {
            tickCard();
            return;
        }
        if (roundDone) return;
        updateWatchers();
        switch (current) {
            case COLOR_RUSH -> tickColorRush();
            case SUMO -> tickSumo();
            case SPRINT -> tickSprint();
            case SPLEEF, TNT_RUN -> tickLastStanding(60);
            case HOT_POTATO -> tickPotato();
            case MEMORY -> tickMemory();
            case SIMON_SAYS -> tickSimon();
            default -> { }
        }
    }

    // ---- rounds: pick, card, countdown ---------------------------------------------

    private boolean available(Micro micro) {
        if (micro.region == null) return true;
        Cuboid region = game.arena().region(micro.region);
        if (region == null) return false;
        // Memory Tiles needs room for a 3x3 grid.
        return micro != Micro.MEMORY || (region.maxX() - region.minX() >= 2 && region.maxZ() - region.minZ() >= 2);
    }

    private void nextRound() {
        round++;
        if (round > totalRounds()) {
            startFinale();
            return;
        }
        roundDone = false;
        roundSerial++;
        microStep = 0;
        outOrder.clear();
        finishOrder.clear();
        jumped.clear();
        moved.clear();
        sprintDeadline = Integer.MAX_VALUE;
        roundAlive.clear();
        game.alivePlayers().forEach(p -> roundAlive.add(p.getUniqueId()));
        roundParticipants.clear();
        roundParticipants.addAll(roundAlive);
        startCount = roundAlive.size();
        if (bag == null) {
            bag = new ShuffleBag<>(Arrays.stream(Micro.values()).filter(m -> m != Micro.NONE && available(m)).toList(),
                    ThreadLocalRandom.current());
        }
        current = bag.draw();
        int chance = game.arena().settings().getInt("chaos-chance", 15);
        chaos = round > 1 && ThreadLocalRandom.current().nextInt(100) < chance;
        cardTimer = COUNTDOWN_SECONDS;
        awaitingGo = true;
        int i = 0;
        for (Player p : game.alivePlayers()) {
            p.teleport(startLocation(i++));
            // Built here, not as constants: potion types need Paper's registry, absent in unit tests.
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 5, 6, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 5, 128, false, false, false));
            Text.title(p, "<yellow><b>" + current.title, cardSubtitle());
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        }
        game.broadcast("<yellow><b>Round " + round + "/" + totalRounds() + ":</b> " + current.title
                + (chaos ? " <gold><b>CHAOS ROUND - double points!</b>" : ""));
    }

    private String cardSubtitle() {
        return (chaos ? "<gold>DOUBLE POINTS! " : "<gray>") + current.instruction;
    }

    /** 3, 2, 1 with the instruction still on screen, then GO. */
    private void tickCard() {
        if (cardTimer > 0) {
            for (Player p : game.alivePlayers()) {
                Text.title(p, "<yellow><b>" + cardTimer, cardSubtitle());
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }
            cardTimer--;
            return;
        }
        awaitingGo = false;
        for (Player p : game.alivePlayers()) {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            Text.title(p, "<green><b>GO!", "<gray>" + current.instruction);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        }
        startMicro();
    }

    private void startMicro() {
        if (current == Micro.SPLEEF) {
            for (Player p : survivors()) {
                ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
                shovel.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.EFFICIENCY, 10);
                p.getInventory().setItem(0, shovel);
            }
        } else if (current == Micro.HOT_POTATO) {
            pops = 0;
            passPotato(null);
        } else if (current == Micro.SIMON_SAYS) {
            simonCommand = null;
        } else if (current == Micro.MEMORY) {
            safeCells.clear();
        }
    }

    private Location startLocation(int index) {
        if (current == Micro.SUMO || current == Micro.HOT_POTATO) {
            Cuboid sumo = game.arena().region("sumo");
            if (sumo != null) {
                Location center = sumo.center(game.world());
                center.setY(sumo.maxY() + 1);
                // Integer division on the row, or the whole grid collapses onto one line.
                return center.add(index % 3 - 1, 0, index / 3 - 1);
            }
        }
        return game.arena().spawn(index, game.world());
    }

    // ---- Color Rush -------------------------------------------------------------

    private void tickColorRush() {
        Cuboid floor = game.arena().region("floor");
        if (floor == null) {
            endSurvival();
            return;
        }
        microStep++;
        var random = ThreadLocalRandom.current();
        // 3 color calls per round, each: shuffle -> call -> clear -> restore
        int cycle = microStep % 9;
        if (cycle == 1) {
            for (Block block : floor.blocks(game.world())) {
                floorSnapshot.putIfAbsent(block, block.getBlockData().clone());
                block.setType(COLORS[random.nextInt(COLORS.length)]);
            }
        } else if (cycle == 2) {
            List<Material> present = floor.blocks(game.world()).stream().map(Block::getType)
                    .filter(material -> java.util.Arrays.asList(COLORS).contains(material)).distinct().toList();
            if (present.isEmpty()) { endSurvival(); return; }
            targetColor = present.get(random.nextInt(present.size()));
            String name = targetColor.name().replace("_WOOL", "");
            game.broadcastRaw(Text.PREFIX + "<yellow><b>" + name + "!");
            game.alivePlayers().forEach(p -> Text.title(p, "<yellow><b>" + name, ""));
        } else if (cycle == 6) {
            for (Block block : floor.blocks(game.world())) {
                if (block.getType() != targetColor && !block.getType().isAir()) block.setType(Material.AIR);
            }
        } else if (cycle == 8 && microStep >= 26) {
            // Survivors of all three calls score.
            endSurvival();
            return;
        } else if (cycle == 8) {
            for (Block block : floor.blocks(game.world())) {
                if (block.getType().isAir()) block.setType(COLORS[random.nextInt(COLORS.length)]);
            }
        }
    }

    // ---- Sumo / last-standing games ---------------------------------------------

    private void tickSumo() {
        microStep++;
        if (microStep > 60) endSurvival();
    }

    /** Spleef and TNT Run: fall through the floor to be out; timeout leaves survivors tied. */
    private void tickLastStanding(int limit) {
        microStep++;
        if (current == Micro.TNT_RUN) limit = 45;
        if (microStep > limit) endSurvival();
    }

    // ---- Sprint -----------------------------------------------------------------

    private void tickSprint() {
        microStep++;
        if (microStep > 60 || microStep > sprintDeadline) endSprint();
    }

    private void endSprint() {
        List<List<Player>> groups = new ArrayList<>();
        for (UUID id : finishOrder) {
            Player p = game.player(id);
            if (p != null) groups.add(List.of(p));
        }
        endRound(groups);
    }

    // ---- Hot Potato ---------------------------------------------------------------

    private void passPotato(Player to) {
        Player old = potatoHolder == null ? null : game.player(potatoHolder);
        if (old != null) old.setGlowing(false);
        Player next = to;
        if (next == null) {
            List<Player> in = survivors();
            if (in.isEmpty()) { potatoHolder = null; return; }
            next = in.get(ThreadLocalRandom.current().nextInt(in.size()));
            potatoTimer = Math.max(4, 8 - pops);
        }
        potatoHolder = next.getUniqueId();
        next.setGlowing(true);
        Text.title(next, "<red><b>YOU HAVE THE POTATO", "<gray>Hit someone to pass it on!");
        next.playSound(next.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f);
    }

    private void tickPotato() {
        microStep++;
        if (microStep > 90) { endSurvival(); return; }
        Player holder = potatoHolder == null ? null : game.player(potatoHolder);
        if (holder == null || !roundAlive.contains(potatoHolder)) { passPotato(null); return; }
        if (--potatoTimer > 0) {
            String msg = "<red>🥔 " + holder.getName() + " <gray>pops in <yellow>" + potatoTimer;
            survivors().forEach(p -> p.sendActionBar(Text.mm(msg)));
            if (potatoTimer <= 3) holder.playSound(holder.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f);
            return;
        }
        pops++;
        holder.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, holder.getLocation().add(0, 1, 0), 1);
        holder.getWorld().playSound(holder.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
        holder.setGlowing(false);
        potatoHolder = null;
        knockOut(holder, "<red>Popped!");
        if (running()) passPotato(null);
    }

    private void clearPotato() {
        if (potatoHolder != null) {
            Player holder = game.player(potatoHolder);
            if (holder != null) holder.setGlowing(false);
            potatoHolder = null;
        }
    }

    // ---- TNT Run ------------------------------------------------------------------

    private void tntFloorStep(Location to) {
        Cuboid floor = game.arena().region("floor");
        if (floor == null) return;
        Block under = to.getWorld().getBlockAt(to.getBlockX(), (int) Math.floor(to.getY() - 0.1), to.getBlockZ());
        if (under.getType().isAir() || !floor.contains(under.getLocation())) return;
        if (floorSnapshot.putIfAbsent(under, under.getBlockData().clone()) != null) return;
        int serial = roundSerial;
        game.runLater(8, () -> {
            if (serial == roundSerial && !roundDone) under.setType(Material.AIR);
        });
    }

    // ---- Memory Tiles ---------------------------------------------------------------

    private int cellOf(Cuboid floor, Block block) {
        int col = Math.min(2, (block.getX() - floor.minX()) * 3 / (floor.maxX() - floor.minX() + 1));
        int row = Math.min(2, (block.getZ() - floor.minZ()) * 3 / (floor.maxZ() - floor.minZ() + 1));
        return row * 3 + col;
    }

    private void paintCells(Cuboid floor, java.util.function.IntFunction<Material> material) {
        for (Block block : floor.blocks(game.world())) {
            floorSnapshot.putIfAbsent(block, block.getBlockData().clone());
            block.setType(material.apply(cellOf(floor, block)));
        }
    }

    /** Ten-second cycle, three times: show green tiles, hide them, drop the rest. */
    private void tickMemory() {
        Cuboid floor = game.arena().region("floor");
        if (floor == null) { endSurvival(); return; }
        microStep++;
        int sequence = (microStep - 1) / 10;
        int phase = (microStep - 1) % 10 + 1;
        if (phase == 1) {
            safeCells.clear();
            List<Integer> cells = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));
            java.util.Collections.shuffle(cells);
            safeCells.addAll(cells.subList(0, 5 - sequence));
            paintCells(floor, c -> safeCells.contains(c) ? Material.LIME_WOOL : Material.BLACK_WOOL);
            game.alivePlayers().forEach(p -> Text.title(p, "<green><b>REMEMBER!", "<gray>The bright green tiles are safe"));
        } else if (phase == 4) {
            paintCells(floor, c -> Material.WHITE_WOOL);
            game.alivePlayers().forEach(p -> Text.title(p, "<yellow><b>HIDDEN!", "<gray>Stand on a tile you remember"));
        } else if (phase == 8) {
            for (Block block : floor.blocks(game.world())) {
                block.setType(safeCells.contains(cellOf(floor, block)) ? Material.LIME_WOOL : Material.AIR);
            }
        } else if (phase == 9) {
            paintCells(floor, c -> Material.WHITE_WOOL);
        } else if (phase == 10 && sequence >= 2) {
            endSurvival();
        }
    }

    // ---- Simon Says -----------------------------------------------------------------

    private void tickSimon() {
        microStep++;
        int phase = microStep % 4;
        if (microStep > 24) { endSurvival(); return; }
        if (phase == 1) {
            Command[] all = Command.values();
            simonCommand = all[ThreadLocalRandom.current().nextInt(all.length)];
            // "Freeze" is always called; the others are sometimes a trap with no "Simon says".
            simonSays = simonCommand == Command.STAND_STILL || ThreadLocalRandom.current().nextInt(100) < 70;
            jumped.clear();
            moved.clear();
            heldAtStart.clear();
            for (Player p : survivors()) {
                if (p.isSneaking() || p.getLocation().getPitch() < -45) heldAtStart.add(p.getUniqueId());
                Text.title(p, simonSays ? "<green><b>Simon says" : "<yellow><b>" + simonCommand.label,
                        simonSays ? "<white>" + simonCommand.label : "<gray>...");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, simonSays ? 1.6f : 0.8f);
            }
        } else if (phase == 3 && simonCommand != null) {
            for (Player p : survivors()) {
                if (did(p, simonCommand) != simonSays) knockOut(p, simonSays ? "<red>Simon said it!" : "<red>Simon didn't say it!");
            }
            simonCommand = null;
        }
    }

    private boolean did(Player p, Command command) {
        // A pose still held from the previous call must not trip a trap.
        if (!simonSays && heldAtStart.contains(p.getUniqueId())
                && (command == Command.SNEAK || command == Command.LOOK_UP)) return false;
        return switch (command) {
            case JUMP -> jumped.contains(p.getUniqueId());
            case SNEAK -> p.isSneaking();
            case LOOK_UP -> p.getLocation().getPitch() < -45;
            case STAND_STILL -> !moved.contains(p.getUniqueId());
        };
    }

    // ---- movement -------------------------------------------------------------------

    @Override
    public void onMove(Player player, Location from, Location to) {
        if (!running() || !roundAlive.contains(player.getUniqueId())) return;
        switch (current) {
            case SPRINT -> {
                Cuboid finish = game.arena().region("finish");
                if (finish != null && finish.contains(to) && !finishOrder.contains(player.getUniqueId())) {
                    finishOrder.add(player.getUniqueId());
                    Text.title(player, "<green><b>FINISHED!", "<gray>Place: " + ordinal(finishOrder.size()));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    if (finishOrder.size() == 1 && startCount > 1) {
                        sprintDeadline = microStep + 10;
                        game.broadcastRaw(Text.PREFIX + "<yellow>" + player.getName() + " finished! 10 seconds left.");
                    }
                    if (finishOrder.size() >= 3 || survivors().stream().allMatch(p -> finishOrder.contains(p.getUniqueId()))) {
                        endSprint();
                    }
                }
            }
            case TNT_RUN -> {
                tntFloorStep(to);
                checkFell(player, to);
            }
            case SPLEEF, MEMORY -> checkFell(player, to);
            case SIMON_SAYS -> {
                if (to.getY() - from.getY() > 0.2) jumped.add(player.getUniqueId());
                double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
                if (dx * dx + dz * dz > 0.0025) moved.add(player.getUniqueId());
            }
            default -> { }
        }
    }

    /** Floor games have no guaranteed void: dropping below the floor layer is out. */
    private void checkFell(Player player, Location to) {
        Cuboid floor = game.arena().region("floor");
        if (floor != null && to.getY() < floor.minY() - 1) knockOut(player, "<red>You fell!");
    }

    private static String ordinal(int n) {
        return switch (n) { case 1 -> "1st"; case 2 -> "2nd"; case 3 -> "3rd"; default -> n + "th"; };
    }

    @Override
    public boolean canBreak(Player player, Block block) {
        if (current != Micro.SPLEEF || !running() || !roundAlive.contains(player.getUniqueId())) return false;
        Cuboid floor = game.arena().region("floor");
        if (floor == null || !floor.contains(block.getLocation())) return false;
        floorSnapshot.putIfAbsent(block, block.getBlockData().clone());
        return true;
    }

    // ---- shared round plumbing -----------------------------------------------------

    private List<Player> survivors() {
        return game.alivePlayers().stream().filter(p -> roundAlive.contains(p.getUniqueId())).toList();
    }

    /** Out of this round (not the game): note the order, send them to watch from the hub. */
    private void knockOut(Player player, String reason) {
        if (roundDone || !roundAlive.remove(player.getUniqueId())) return;
        outOrder.add(player.getUniqueId());
        player.setFallDistance(0);
        player.setGlowing(false);
        player.teleport(game.arena().lobby(game.world()));
        Text.title(player, reason, "<gray>You're out - watch the rest from the hub!");
        player.sendActionBar(Text.mm("<red>Out of this round!"));
        if (current.survival() && running() && startCount >= 2 && survivors().size() <= 1) endSurvival();
    }

    /** Hub spectators: who is still in, refreshed every second. */
    private void updateWatchers() {
        List<Player> in = survivors();
        String names = String.join(", ", in.stream().limit(4).map(Player::getName).toList());
        String line = "<gray>Watching <white>" + current.title + "<gray> - " + in.size() + " still in: <yellow>" + names;
        for (Player p : game.alivePlayers()) {
            if (!roundAlive.contains(p.getUniqueId()) && roundParticipants.contains(p.getUniqueId())) p.sendActionBar(Text.mm(line));
        }
    }

    /** Falling out of a micro-game knocks you out of the round, not the game. */
    @Override
    public void onDeath(Player victim, Player killer) {
        if (roundDone || !roundAlive.contains(victim.getUniqueId())) {
            victim.setFallDistance(0);
            victim.teleport(game.arena().lobby(game.world()));
            return;
        }
        knockOut(victim, "<red>Out!");
    }

    /** A leaver still counted as "in" would stall a round waiting for a knock-off. */
    @Override
    public void onQuit(Player player) {
        if (!roundAlive.remove(player.getUniqueId())) return;
        player.setGlowing(false);
        if (current == Micro.HOT_POTATO && player.getUniqueId().equals(potatoHolder)) potatoHolder = null;
        if (current.survival() && running() && startCount >= 2 && survivors().size() <= 1) endSurvival();
    }

    /** Survivors tie for first; everyone else places in reverse knock-out order. */
    private void endSurvival() {
        List<List<Player>> groups = new ArrayList<>();
        List<Player> alive = survivors();
        if (!alive.isEmpty()) groups.add(alive);
        for (int i = outOrder.size() - 1; i >= 0; i--) {
            Player p = game.player(outOrder.get(i));
            if (p != null) groups.add(List.of(p));
        }
        endRound(groups);
    }

    private void endRound(List<List<Player>> groups) {
        if (roundDone) return;
        roundDone = true;
        roundSerial++;
        restoreFloor();
        clearPotato();
        current = Micro.NONE;
        phaseTimer = 5;
        int multiplier = chaos ? 2 : 1;
        Map<UUID, Integer> points = PartyScoring.award(
                groups.stream().map(g -> g.stream().map(Player::getUniqueId).toList()).toList(),
                roundParticipants.stream().filter(id -> game.player(id) != null).toList(), multiplier);
        points.forEach((id, value) -> {
            Player p = game.player(id);
            if (p == null) return;
            game.addScore(p, value);
            p.sendActionBar(Text.mm("<green>+" + value + " points" + (chaos ? " <gold>(double!)" : "") + " <gray>- total " + game.score(p)));
        });
        if (groups.isEmpty()) {
            game.broadcast("<gray>Nobody placed that round - just the participation point!");
        } else {
            List<String> names = groups.get(0).stream().map(Player::getName).toList();
            game.broadcast("<green>Round winner(s): <yellow>" + String.join(", ", names) + "</yellow> <gray>(+"
                    + PartyScoring.PLACE_POINTS[0] * multiplier + " points)");
        }
        chaos = false;
        int i = 0;
        for (Player p : game.alivePlayers()) {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            p.getInventory().remove(Material.DIAMOND_SHOVEL);
            p.setGlowing(false);
            p.teleport(game.arena().spawn(i++, game.world()));
        }
    }

    // ---- finale: podium ---------------------------------------------------------------

    private List<Player> ranking() {
        List<Player> ranked = new ArrayList<>();
        for (var entry : game.topScores()) {
            Player p = game.player(entry.getKey());
            if (p != null && game.isParticipant(p)) ranked.add(p);
        }
        return ranked;
    }

    private void startFinale() {
        current = Micro.NONE;
        phaseTimer = 0;
        finaleStep = 0;
        int i = 0;
        for (Player p : game.alivePlayers()) p.teleport(game.arena().spawn(i++, game.world()));
    }

    /** Step 0 intro, then 3rd, 2nd, 1st every two seconds (fewer podium places for small games), then results. */
    private void tickFinale() {
        List<Player> ranked = ranking();
        int places = Math.min(3, ranked.size());
        int step = finaleStep++;
        if (step == 0) {
            for (Player p : game.alivePlayers()) {
                Text.title(p, "<gold><b>FINAL SCORES", "<gray>Who made the podium?");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            return;
        }
        int reveal = step / 2 - 1; // 0 = lowest podium place shown
        if (step % 2 == 0 && reveal >= 0 && reveal < places) {
            int rank = places - reveal; // 1-based place being revealed
            Player p = ranked.get(rank - 1);
            String[] colours = {"<gold>", "<white>", "<#cd7f32>"};
            String label = colours[rank - 1] + "<b>" + ordinal(rank) + " PLACE";
            for (Player viewer : game.alivePlayers()) {
                Text.title(viewer, label, "<yellow>" + p.getName() + " <gray>- " + game.score(p) + " points");
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1f);
            }
            game.broadcast(label + "</b> <yellow>" + p.getName() + "</yellow> <gray>(" + game.score(p) + " points)");
            Location podium = game.arena().settingLocation("podium" + rank, game.world());
            if (podium != null) p.teleport(podium);
            Fireworks.burst(p.getLocation());
            if (rank == 1) Fireworks.burst(p.getLocation().add(1.5, 0, 0));
            return;
        }
        if (step > 2 * places + 2 || places == 0) {
            int best = ranked.isEmpty() ? 0 : game.score(ranked.get(0));
            game.end(ranked.stream().filter(p -> game.score(p) == best).toList(), "rounds");
        }
    }

    /** Restore the authored floor before any phase exit teleports players back onto it. */
    private void restoreFloor() {
        floorSnapshot.forEach((block, data) -> block.setBlockData(data, false));
        floorSnapshot.clear();
    }

    @Override public void onEnd(List<Player> winners) { restoreFloor(); clearPotato(); }
    @Override public void onCleanup() { restoreFloor(); clearPotato(); }

    @Override
    public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {
        boolean fighting = (current == Micro.SUMO || current == Micro.HOT_POTATO) && running();
        if (!fighting
                || !roundAlive.contains(victim.getUniqueId())
                || !roundAlive.contains(damager.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (current == Micro.HOT_POTATO && damager.getUniqueId().equals(potatoHolder)
                && System.currentTimeMillis() >= noTagBackUntil) {
            noTagBackUntil = System.currentTimeMillis() + 1500;
            passPotato(victim);
        }
        event.setDamage(0); // knockback only
    }

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Round: <white>" + Math.min(round, totalRounds()) + "/" + totalRounds());
        if (current != Micro.NONE) lines.add("<gray>Game: <white>" + current.title + (chaos ? " <gold>x2" : ""));
        lines.add("<gray>Your points: <yellow>" + game.score(player));
        if (current != Micro.NONE && roundParticipants.contains(player.getUniqueId()) && !roundAlive.contains(player.getUniqueId())) {
            lines.add("<red>Out - watching");
        }
        game.topScores().stream().limit(3).forEach(e -> {
            Player p = game.plugin().getServer().getPlayer(e.getKey());
            if (p != null) lines.add("<gray>" + p.getName() + ": <white>" + e.getValue());
        });
        return lines;
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public int timeLimitSeconds() { return 900; }
    @Override
    public String phaseCountdown() { return current == Micro.NONE && phaseTimer > 0 ? "Next game: " + phaseTimer + "s" : null; }

}
