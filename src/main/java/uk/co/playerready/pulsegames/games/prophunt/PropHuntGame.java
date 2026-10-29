package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Text;

import uk.co.playerready.pulsegames.games.common.RoundController;
import uk.co.playerready.pulsegames.games.common.RoundTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Prop Hunt: PulseContent-owned moving props, stable grid locks and bounded role powers.
 * The flag-off game retains its original invisible helmet disguises.
 * Modes: classic (found = spectate), infection (found = join the seekers).
 */
public final class PropHuntGame extends MiniGame implements uk.co.playerready.pulsegames.core.bot.BotMovement {

    private static final Material[] DISGUISES = {
            Material.HAY_BLOCK, Material.BARREL, Material.CRAFTING_TABLE, Material.BOOKSHELF,
            Material.MELON, Material.PUMPKIN, Material.CACTUS, Material.OAK_LOG, Material.TNT
    };

    private final Set<UUID> seekers = new HashSet<>();
    private final Set<UUID> hiders = new HashSet<>();
    private final Set<UUID> originalHiders = new HashSet<>();
    private RoundController rounds;
    private final Map<UUID, Integer> seekerTurns = new HashMap<>();
    private List<String> propIds = List.of();
    private final Map<UUID, AutoCloseable> visuals = new HashMap<>();
    private final Map<UUID, HiderBody> bodies = new HashMap<>();
    private final Map<UUID, Long> nextLock = new HashMap<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private record Decoy(UUID owner, PropPalette.Option option, Location feet, AutoCloseable visual, int expires) {}
    private final Map<UUID, Decoy> decoys = new java.util.LinkedHashMap<>();
    private boolean powers;
    private int decoyLimit, decoyLifetime, decoyCooldown, sprintCooldown, pulseCooldown, pulseRadius;
    private Set<UUID> visualAudience = Set.of();
    private boolean customProps;
    private boolean blockProps;
    /** Arena setting `real-blocks`: locked hiders and decoys are real world blocks. Default off. */
    private boolean realBlocks;
    PropBlocks blocks = new PropBlocks(Material::createBlockData);
    private int propTick;
    private static final long REVEAL_LOCKOUT_NANOS = 2_500_000_000L;
    /** Marks a decoy that is a tracked world block rather than a model; closing restores the cell. */
    private record BlockDecoy(Runnable undo) implements AutoCloseable { @Override public void close() { undo.run(); } }
    private List<PropPalette.Option> palette = List.of();
    private final Map<UUID, PropPalette.Option> choices = new HashMap<>();
    private record Locked(Location cell, Location standing) {}
    private final Map<UUID, Locked> locked = new HashMap<>();
    private final Set<UUID> routedBlockHits = new HashSet<>();
    private final Map<UUID, Long> nextBlockHit = new HashMap<>();
    private final class PaletteMenu implements org.bukkit.inventory.InventoryHolder {
        final UUID player;
        boolean decoy;
        final PropHuntGame owner = PropHuntGame.this;
        org.bukkit.inventory.Inventory inventory;
        PaletteMenu(UUID player) { this.player = player; }
        @Override public org.bukkit.inventory.Inventory getInventory() { return inventory; }
    }
    private int hidePhase;
    private Location seekerHold;

    public PropHuntGame(GameInstance game) {
        super(game);
    }

    private final Map<UUID, Location> botGoals = new HashMap<>();
    private final Map<UUID, PropBotMemory> botMemories = new HashMap<>();
    private final Map<UUID, Long> botNextAction = new HashMap<>();
    private final Map<UUID, Long> botNextPower = new HashMap<>();
    private final Map<UUID, Long> botLocalSearchUntil = new HashMap<>();
    private final Set<UUID> botPaletteChosen = new HashSet<>();

    @Override public Location botDestination(Player bot) {
        if (seekers.contains(bot.getUniqueId()) && hidePhase > 0 || locked.containsKey(bot.getUniqueId())) return null;
        Location goal = botGoals.get(bot.getUniqueId());
        return goal == null ? null : goal.clone();
    }

    @Override public void botTick(Player bot, long tick, uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        if (game.frozen() || game.state() != GameState.RUNNING || !game.isActive(bot)) return;
        UUID id = bot.getUniqueId();
        botAbilities(bot, tick);
        if (seekers.contains(id)) {
            if (hidePhase > 0) { botGoals.remove(id); return; }
            PropBotMemory memory = botMemories.computeIfAbsent(id, ignored -> new PropBotMemory());
            for (UUID hiderId : hiders) {
                Player hider = game.player(hiderId);
                if (hider == null || !game.isActive(hider)) continue;
                PropPalette.Option option = choices.get(hiderId);
                Location centre = hider.getLocation().add(0, option == null ? 1.5 : option.height() / 2, 0);
                boolean visible = visibleProp(bot, centre);
                boolean moving = hider.getVelocity().lengthSquared() > .003;
                // A visible moving prop is a legitimate clue. Stationary props are not identified from role metadata.
                memory.observe(hiderId, centre, visible, moving, tick);
            }
            Location clue = memory.destination(tick);
            if (clue != null) {
                if (game.plugin().bots() != null) difficulty = game.plugin().bots().difficultyFor(game, game.player(memory.target()));
                botGoals.put(id, clue.clone().subtract(0, .25, 0));
                if (tick >= botNextAction.getOrDefault(id, 0L) && visibleProp(bot, clue)) {
                    botNextAction.put(id, tick + difficulty.meleeDelayTicks());
                    Location eye = bot.getEyeLocation();
                    eye.setDirection(clue.toVector().subtract(eye.toVector()));
                    bot.setRotation(eye.getYaw(), eye.getPitch());
                    if (eye.distanceSquared(clue) <= 9 && ThreadLocalRandom.current().nextInt(100) < difficulty.accuracyPercent()) {
                        bot.swingMainHand();
                        if (blockProps) hitAimedProp(bot, null);
                        else {
                            Player target = game.player(memory.target());
                            if (target != null && target.getWorld() == bot.getWorld() && hiders.contains(target.getUniqueId())
                                    && bot.getEyeLocation().distanceSquared(target.getLocation().add(0, 1, 0)) <= 9 && bot.hasLineOfSight(target)) bot.attack(target);
                        }
                    }
                }
                return;
            }
        } else if (hiders.contains(id)) {
            if (locked.containsKey(id)) { botGoals.remove(id); return; }
            // Use the same lock validation as humans; navigation never teleports into a hiding space.
            if (hidePhase <= 5 && blockProps && bot.isOnGround()) {
                toggleLock(bot);
                if (locked.containsKey(id)) { botGoals.remove(id); return; }
            }
        } else return;
        if (seekers.contains(id) && tick % 40 == 0) {
            // Inspect along an arbitrary facing, never auto-aim at a stationary hider's coordinates.
            bot.setRotation(ThreadLocalRandom.current().nextFloat() * 360f, 30f);
            bot.swingMainHand();
            if (blockProps) hitAimedProp(bot, null);
            else {
                var hit = bot.getWorld().rayTraceEntities(bot.getEyeLocation(), bot.getEyeLocation().getDirection(), 3,
                        entity -> entity instanceof Player p && !p.equals(bot) && game.isActive(p));
                if (hit != null && hit.getHitEntity() instanceof Player target && bot.hasLineOfSight(target)) bot.attack(target);
            }
        }
        Location goal = botGoals.get(id);
        if (goal != null && goal.getWorld() == bot.getWorld() && goal.distanceSquared(bot.getLocation()) > 2
                && tick < botNextAction.getOrDefault(id, 0L)) return;
        botNextAction.put(id, tick + 100);
        if (hiders.contains(id)) {
            var spots = uk.co.playerready.pulsegames.core.bot.BotNavigationHints.points(game, "bot-hide-points");
            if (!spots.isEmpty()) {
                botGoals.put(id, spots.get(ThreadLocalRandom.current().nextInt(spots.size()))); return;
            }
        } else if (tick >= botLocalSearchUntil.getOrDefault(id, 0L) && game.plugin().bots() != null) {
            Location patrol = game.plugin().bots().patrolDestination(game, bot);
            if (patrol != null) { botGoals.put(id, patrol); return; }
        }
        // Explore loaded, supported cells only. Unreachable choices time out through the navigator.
        var random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 12; attempt++) {
            Location candidate = bot.getLocation().add(random.nextInt(-6, 7), 0, random.nextInt(-6, 7));
            candidate.setX(candidate.getBlockX() + .5); candidate.setZ(candidate.getBlockZ() + .5);
            if (PropSpace.clear(candidate, 1.8) && PropSpace.supported(candidate)) {
                botGoals.put(id, candidate); return;
            }
        }
        botGoals.remove(id);
    }

    /** Uses only public scenery, visible opponents and the same human power handlers. */
    void botAbilities(Player bot, long tick) {
        UUID id = bot.getUniqueId();
        if (game.frozen() || game.state() != GameState.RUNNING || !game.isActive(bot)
                || tick < botNextPower.getOrDefault(id, 0L)) return;
        botNextPower.put(id, tick + 40);
        if (hiders.contains(id)) {
            if (canChoose(bot) && botPaletteChosen.add(id) && !palette.isEmpty()) {
                var fitting = palette.stream().filter(option -> PropSpace.clear(bot.getLocation(), option.height())).toList();
                if (!fitting.isEmpty()) chooseDisguise(bot, fitting.get(ThreadLocalRandom.current().nextInt(fitting.size())));
            }
            if (!canUsePower(bot, true)) return;
            boolean threat = seekers.stream().map(game::player).filter(Objects::nonNull)
                    .anyMatch(seeker -> visibleProp(bot, seeker.getLocation().add(0, 1, 0)));
            if (threat && !locked.containsKey(id) && powerReady(bot, "Sprint")) sprint(bot);
            if (blockProps && !palette.isEmpty() && powerReady(bot, "Decoy")
                    && decoys.values().stream().filter(d -> d.owner().equals(id)).count() < decoyLimit) {
                // Aim at ordinary nearby scenery. The shared ray, support and occupancy checks decide placement.
                bot.setRotation(ThreadLocalRandom.current().nextFloat() * 360f, 55f);
                placeDecoy(bot, palette.get(ThreadLocalRandom.current().nextInt(palette.size())));
            }
        } else if (canUsePower(bot, false)) {
            PropBotMemory memory = botMemories.get(id);
            Location clue = memory == null ? null : memory.destination(tick);
            if (clue != null && visibleProp(bot, clue)) {
                if (powerReady(bot, "Sprint")) sprint(bot);
            } else if (powerReady(bot, "Pulse")) {
                // A strong coarse signal warrants inspecting this area; it supplies no bearing or target identity.
                if (pulse(bot) == 2) {
                    botGoals.remove(id);
                    botLocalSearchUntil.put(id, tick + 100);
                    botNextAction.put(id, tick + 40);
                }
            }
        }
    }

    static boolean visibleProp(Player observer, Location point) {
        Location eye = observer.getEyeLocation();
        if (eye.getWorld() != point.getWorld()) return false;
        var delta = point.toVector().subtract(eye.toVector());
        double distance = delta.length();
        if (distance > 16 || distance < .001) return false;
        if (eye.getDirection().dot(delta.clone().normalize()) < .25) return false;
        var hit = eye.getWorld().rayTraceBlocks(eye, delta.normalize(), distance,
                org.bukkit.FluidCollisionMode.NEVER, true);
        // A real-block prop is a solid cell: reaching the target's own cell means the line of sight is clear.
        var block = hit == null ? null : hit.getHitBlock();
        return hit == null || (block != null && block.getX() == point.getBlockX()
                && block.getY() == point.getBlockY() && block.getZ() == point.getBlockZ());
    }

    /** Skill outcome follows the starting role; conversion must not reward being caught. Call before onEnd cleanup. */
    public boolean botSkillWin(Player player, List<Player> winners) {
        if (!winners.contains(player)) return false;
        if (!isInfection()) return true;
        UUID id = player.getUniqueId();
        if (originalHiders.contains(id)) return hiders.contains(id);
        return hiders.isEmpty() && seekers.contains(id);
    }

    private boolean isInfection() { return game.mode().id().equals("infection"); }
    private boolean isRounds() { return RoundController.isRounds(game); }
    private int roundSecondsFallback() { return Math.max(0, game.arena().settings().getInt("hide-seconds", 30)) + 90; }

    /** Fewest turns as seeker go first, so roles swap every round; ties are shuffled. */
    static Set<UUID> pickSeekers(List<UUID> players, Map<UUID, Integer> turns, int count, java.util.Random random) {
        List<UUID> order = new ArrayList<>(players);
        Collections.shuffle(order, random);
        order.sort(java.util.Comparator.comparingInt(id -> turns.getOrDefault(id, 0)));
        return new java.util.LinkedHashSet<>(order.subList(0, Math.min(count, order.size())));
    }

    @Override
    public String startProblem(List<Player> players) {
        if (players.size() < 2) return "Prop Hunt needs at least one seeker and one hider (2 players).";
        if (isRounds() && game.plugin().getConfig().getBoolean("content.prophunt.enabled", false))
            return "Prop Hunt rounds use the plain disguise mode; custom props are not supported in rounds yet.";
        if (!game.plugin().getConfig().getBoolean("content.prophunt.enabled", false)) return null;
        if (players.stream().anyMatch(game::isBot) && (!game.plugin().getConfig().getBoolean("content.prophunt.block-snapping", false)
                || !game.plugin().content().npcPropVisualsReady())) return "Prop Hunt bots need block disguises and PulseContent NPC prop support.";
        List<String> models = game.plugin().getConfig().getStringList("content.prophunt.props");
        if (game.plugin().getConfig().getBoolean("content.prophunt.block-snapping", false)) {
            if (!game.plugin().content().propVisualsReady()) return "Moving block disguises need PulseContent 0.3.3 or newer (prop visual API).";
            try { models = PropPalette.read(game.plugin().getConfig(), game.arena().settings()).stream().map(PropPalette.Option::model).toList(); }
            catch (IllegalArgumentException e) { return e.getMessage(); }
        }
        return game.plugin().content().startProblem(models, players.stream().filter(p -> !game.isBot(p)).toList());
    }

    @Override
    public void onStart() {
        customProps = game.plugin().getConfig().getBoolean("content.prophunt.enabled", false);
        blockProps = customProps && game.plugin().getConfig().getBoolean("content.prophunt.block-snapping", false);
        if (blockProps) palette = PropPalette.read(game.plugin().getConfig(), game.arena().settings());
        List<String> props = blockProps ? palette.stream().map(PropPalette.Option::model).toList()
                : game.plugin().getConfig().getStringList("content.prophunt.props");
        propIds = props;
        if (customProps) {
            String problem = startProblem(game.everyone());
            if (problem != null) throw new IllegalStateException(problem);
            visualAudience = game.everyone().stream().filter(p -> !game.isBot(p)).map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        realBlocks = blockProps && game.arena().settings().getBoolean("real-blocks", false);
        powers = blockProps && game.arena().settings().getBoolean("prop-powers", true);
        decoyLimit = bounded("decoy-limit", 3, 1, 3);
        decoyLifetime = bounded("decoy-seconds", 60, 10, 120);
        decoyCooldown = bounded("decoy-cooldown", 12, 2, 60);
        sprintCooldown = bounded("sprint-cooldown", 25, 5, 120);
        pulseCooldown = bounded("pulse-cooldown", 25, 5, 120);
        pulseRadius = bounded("pulse-radius", 12, 4, 24);
        hidePhase = Math.max(0, game.arena().settings().getInt("hide-seconds", 30));
        seekerHold = game.arena().spawn(0, game.world());
        if (isRounds()) {
            rounds = new RoundController(game, new RoundController.Host() {
                @Override public void beginRound(int round) { startRoundRoles(); }
                @Override public List<List<UUID>> timeUpTiers(RoundTracker tracker) {
                    // Surviving hiders win the clock; seekers rank above hiders who were found.
                    List<List<UUID>> tiers = new ArrayList<>();
                    List<UUID> survivors = hiders.stream().toList();
                    if (!survivors.isEmpty()) tiers.add(survivors);
                    List<UUID> seekerIds = seekers.stream().toList();
                    if (!seekerIds.isEmpty()) tiers.add(seekerIds);
                    foundTiers(tracker).forEach(tiers::add);
                    return tiers;
                }
            }, null, roundSecondsFallback(), "Hiders stay hidden, seekers find them - roles swap").autoEnd(false);
            rounds.start();
            return;
        }
        List<Player> players = new ArrayList<>(game.alivePlayers());
        Collections.shuffle(players);
        int seekerCount = Math.max(1, players.size() / 5);
        assignRoles(players, players.stream().limit(seekerCount).map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet()));
        if (blockProps) game.runRepeating(1, 1, this::updateProps);
    }

    /** Rounds: forget last round's roles, swap them, hide again. */
    private void startRoundRoles() {
        seekers.clear(); hiders.clear(); originalHiders.clear();
        hidePhase = Math.max(0, game.arena().settings().getInt("hide-seconds", 30));
        List<Player> players = new ArrayList<>(rounds.contestants());
        Set<UUID> chosen = pickSeekers(players.stream().map(Player::getUniqueId).toList(), seekerTurns,
                Math.max(1, players.size() / 5), ThreadLocalRandom.current());
        chosen.forEach(id -> seekerTurns.merge(id, 1, Integer::sum));
        assignRoles(players, chosen);
    }

    /** Hiders found this round, last found first, each a tier of their own. */
    private static List<List<UUID>> foundTiers(RoundTracker tracker) {
        return tracker.eliminationTiers().stream().filter(tier -> !tracker.remaining().containsAll(tier)).toList();
    }

    private void assignRoles(List<Player> players, Set<UUID> seekerIds) {
        List<String> props = propIds;
        int seekerCount = seekerIds.size();
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (seekerIds.contains(player.getUniqueId())) {
                seekers.add(player.getUniqueId());
                player.teleport(seekerHold);
                if (hidePhase > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, hidePhase * 20, 0));
                } else {
                    equipSeeker(player);
                }
                Text.title(player, "<red><b>SEEKER", "<gray>Released in " + hidePhase + "s...");
            } else {
                hiders.add(player.getUniqueId());
                originalHiders.add(player.getUniqueId());
                String disguiseName;
                if (customProps) {
                    int index = random.nextInt(props.size());
                    String id = props.get(index);
                    if (blockProps) {
                        HiderBody body = new HiderBody(game.plugin(), player);
                        bodies.put(player.getUniqueId(), body);
                        body.apply(palette.get(index).height(), game.everyone().stream().filter(p -> !game.isBot(p)).toList());
                        visuals.put(player.getUniqueId(), new PropRig(player, palette.get(index)));
                    } else visuals.put(player.getUniqueId(), game.plugin().content().attach(id, player, visualAudience));
                    disguiseName = blockProps ? palette.get(index).name() : "custom prop";
                    if (blockProps) {
                        choices.put(player.getUniqueId(), palette.get(index));
                        giveControl(player, 0, Material.BOOK, "Choose disguise");
                        giveControl(player, 1, Material.SLIME_BALL, "Lock / unlock prop");
                        if (powers) {
                            giveControl(player, 2, Material.ECHO_SHARD, "Place decoy: choose its disguise");
                            giveControl(player, 3, Material.BARRIER, "Clear your decoys");
                            giveControl(player, 5, Material.FEATHER, "Escape sprint");
                        }
                        player.sendMessage(Text.msg("<yellow>Choose a disguise during hiding time. "+uk.co.playerready.pulsegames.core.util.PlatformDetector.useVerbCapital(player)+" the slime ball to lock to the block grid or move again."));
                        if (!game.isBot(player)) game.runLater(1, () -> openPalette(player));
                    }
                } else {
                    Material disguise = DISGUISES[random.nextInt(DISGUISES.length)];
                    player.getInventory().setHelmet(new ItemStack(disguise));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                            PotionEffect.INFINITE_DURATION, 0, false, false));
                    disguiseName = disguise.name().toLowerCase().replace('_', ' ');
                }
                ItemStack taunt = new ItemStack(Material.NOTE_BLOCK);
                taunt.editMeta(meta -> meta.displayName(Text.mm("<yellow><b>Taunt</b> <gray>("+uk.co.playerready.pulsegames.core.util.PlatformDetector.useVerb(player)+"... if you dare)")));
                player.getInventory().setItem(4, taunt);
                Text.title(player, "<green><b>HIDER", "<gray>Disguise: " + disguiseName);
            }
        }
        game.broadcast("<yellow>" + seekerCount + " seeker(s)</yellow> will be released in <red>"
                + hidePhase + "s</red>. Hide!");
    }

    @Override
    public void onSecond(int gameTime) {
        if (rounds != null) { rounds.tick(); if (!rounds.active()) return; }
        for (UUID id : List.copyOf(decoys.keySet())) if (decoys.get(id).expires() <= gameTime) removeDecoy(id);
        if (customProps) {
            Set<UUID> currentAudience = game.everyone().stream().filter(p -> !game.isBot(p)).map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
            boolean valid = currentAudience.equals(visualAudience);
            for (UUID id : hiders) {
                Player hider = game.player(id);
                if (hider != null) reconcileLock(hider);
                valid &= hider != null && (blockProps ? blocks.hasHider(id) || (visuals.get(id) instanceof PropRig rig && rig.active())
                        : game.plugin().content().isActive(hider));
            }
            if (!valid) {
                game.note("prop-content-invalid", "Audience changed=" + !currentAudience.equals(visualAudience)
                        + "; one or more hider visuals may be inactive");
                game.broadcast("<yellow>Custom Prop Hunt stopped because its content or viewing players changed. Please queue again.");
                game.forceEnd();
                return;
            }
        }
        if (hidePhase > 0) {
            hidePhase--;
            // Hold seekers at spawn during the hide phase.
            for (UUID id : seekers) {
                Player seeker = game.player(id);
                if (seeker != null && (!seeker.getWorld().equals(seekerHold.getWorld())
                        || seeker.getLocation().distanceSquared(seekerHold) > 4)) {
                    seeker.teleport(seekerHold);
                }
            }
            if (hidePhase == 0) {
                game.broadcast("<red><b>The seekers have been released!");
                for (UUID id : seekers) {
                    Player seeker = game.player(id);
                    if (seeker != null) equipSeeker(seeker);
                }
            }
            return;
        }
        // Heartbeat: every 30s all hiders emit a sound so camping isn't free.
        if (gameTime % 30 == 0) {
            game.broadcast("<dark_gray>The props made a sound...");
            for (UUID id : hiders) {
                Player hider = game.player(id);
                if (hider == null) continue;
                game.world().playSound(hider.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 0.8f);
                for (UUID seekerId : seekers) {
                    Player seeker = game.player(seekerId);
                    if (seeker != null && game.isBot(seeker) && seeker.getWorld() == hider.getWorld()
                            && seeker.getLocation().distanceSquared(hider.getLocation()) <= 12 * 12) {
                        // Sound supplies a rough search area, never an identified attack target.
                        Location cue = hider.getLocation();
                        cue.setX(Math.floor(cue.getX() / 4) * 4 + 2);
                        cue.setZ(Math.floor(cue.getZ() / 4) * 4 + 2);
                        botGoals.put(seekerId, cue);
                    }
                }
                game.world().spawnParticle(org.bukkit.Particle.NOTE,
                        hider.getLocation().add(0, 2, 0), 3, 0.2, 0.2, 0.2);
            }
        }
    }

    private void giveControl(Player player, int slot, Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(Text.mm("<yellow>" + name + " <gray>("+uk.co.playerready.pulsegames.core.util.PlatformDetector.useVerb(player)+")")));
        player.getInventory().setItem(slot, item);
    }

    private boolean canChoose(Player player) {
        return blockProps && game.state() == GameState.RUNNING && game.isActive(player)
                && hiders.contains(player.getUniqueId()) && hidePhase > 0 && !locked.containsKey(player.getUniqueId());
    }

    private void openPalette(Player player) { openPalette(player, false); }
    private void openPalette(Player player, boolean decoy) {
        if (decoy ? !canUsePower(player, true) : !canChoose(player)) {
            if (hiders.contains(player.getUniqueId())) player.sendActionBar(Text.mm("<yellow>Choose while unlocked, before seekers are released."));
            return;
        }
        PaletteMenu holder = new PaletteMenu(player.getUniqueId());
        holder.decoy = decoy;
        holder.inventory = org.bukkit.Bukkit.createInventory(holder, 27, Text.mm(decoy ? "<dark_gray>Choose decoy disguise" : "<dark_gray>Choose your prop"));
        for (int i = 0; i < palette.size(); i++) {
            PropPalette.Option option = palette.get(i);
            ItemStack item = new ItemStack(option.material());
            item.editMeta(meta -> {
                meta.displayName(Text.mm("<yellow>" + option.name()));
                meta.lore(List.of(Text.mm(option.material() == Material.PLAYER_HEAD
                        ? "<gray>Half-block head" : "<gray>Full-block disguise"),
                        Text.mm("<gray>Click to choose")));
            });
            holder.inventory.setItem(i, item);
        }
        player.openInventory(holder.inventory);
    }

    @Override public void onInventoryClick(Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PaletteMenu holder)) return;
        event.setCancelled(true);
        if (holder.owner != this || !holder.player.equals(player.getUniqueId()) || (holder.decoy ? !canUsePower(player, true) : !canChoose(player))
                || event.getClickedInventory() != event.getView().getTopInventory()
                || event.getSlot() < 0 || event.getSlot() >= palette.size()) return;
        PropPalette.Option option = palette.get(event.getSlot());
        if (holder.decoy) {
            placeDecoy(player, option);
            game.runLater(1, player::closeInventory);
            return;
        }
        chooseDisguise(player, option);
    }

    private void chooseDisguise(Player player, PropPalette.Option option) {
        if (!canChoose(player)) return;
        if (!PropSpace.clear(player.getLocation(), option.height())) {
            player.sendActionBar(Text.mm("<yellow>That disguise is too large for this space.")); return;
        }
        try {
            PropRig replacement = new PropRig(player, option);
            try { closeVisual(player.getUniqueId()); }
            catch (RuntimeException error) { replacement.close(); throw error; }
            visuals.put(player.getUniqueId(), replacement);
            bodies.get(player.getUniqueId()).scale(option.height());
            choices.put(player.getUniqueId(), option);
            player.sendActionBar(Text.mm("<green>Disguise: " + option.name()));
            game.runLater(1, player::closeInventory);
        } catch (RuntimeException e) { stopForPropFailure(e); }
    }

    @Override public void onInventoryDrag(Player player, org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PaletteMenu) event.setCancelled(true);
    }

    /** Lock changes only the anchor position. No block replacement or upward body teleport. */
    void toggleLock(Player player) {
        UUID id = player.getUniqueId();
        if (game.state() != GameState.RUNNING || !game.isActive(player) || game.frozen()
                || player.isInsideVehicle() || !hiders.contains(id)) return;
        long now = System.nanoTime();
        if (now < nextLock.getOrDefault(id, Long.MIN_VALUE)) return;
        nextLock.put(id, now + 350_000_000L); // Duplicate Java/Bedrock use packets must not toggle twice.
        PropPalette.Option option = choices.get(id);
        if (option == null) return;
        if (locked.remove(id) != null) {
            endBlockLock(player);
            player.sendActionBar(Text.mm("<green>Unlocked - you can move.")); return;
        }
        if (realBlocks && tryBlockLock(player, option)) return;
        if (!player.isOnGround()) { player.sendActionBar(Text.mm("<yellow>Stand on a solid surface to lock.")); return; }
        Location standing = snapCell(player.getLocation()).add(.5, 0, .5);
        standing.setY(player.getLocation().getY()); // Retain slab/shelf height, snap X/Z only.
        standing.setYaw(player.getLocation().getYaw()); standing.setPitch(player.getLocation().getPitch());
        if (!PropSpace.clear(standing, option.height()) || !PropSpace.supported(standing) || occupied(standing, option, id)) {
            player.sendActionBar(Text.mm("<yellow>Cannot lock: the snapped prop needs clear space and support. Try the next cell.")); return;
        }
        if (!player.teleport(standing)) { player.sendActionBar(Text.mm("<yellow>Lock cancelled; your disguise is still moving.")); return; }
        locked.put(id, new Locked(snapCell(standing), standing));
        player.setVelocity(new org.bukkit.util.Vector());
        player.sendActionBar(Text.mm("<green>Locked as " + option.name() + ". <gray>"+uk.co.playerready.pulsegames.core.util.PlatformDetector.useVerbCapital(player)+" again to move."));
    }

    /**
     * Real-blocks lock, valid on the ground or mid-air: snap to the whole cell and write the block.
     * Returns true when the request was handled; false sends a grounded hider to the entity-only lock.
     */
    private boolean tryBlockLock(Player player, PropPalette.Option option) {
        UUID id = player.getUniqueId();
        Location at = player.getLocation();
        Location standing = new Location(at.getWorld(), at.getBlockX() + .5, Math.floor(at.getY() + .001), at.getBlockZ() + .5,
                at.getYaw(), at.getPitch());
        org.bukkit.block.Block cell = standing.getWorld() == null ? null
                : standing.getWorld().getBlockAt(standing.getBlockX(), standing.getBlockY(), standing.getBlockZ());
        boolean viable = cell != null && option.height() == 1.0 && blocks.qualifies(option.material(), standing)
                && !blocks.tracked(cell) && PropBlocks.replaceable(cell)
                && PropSpace.clear(standing, option.height()) && !occupied(standing, option, id);
        if (!viable) {
            if (player.isOnGround()) return false;
            player.sendActionBar(Text.mm("<yellow>Cannot lock mid-air here: that disguise needs a free whole block cell."));
            return true;
        }
        if (!player.teleport(standing)) { player.sendActionBar(Text.mm("<yellow>Lock cancelled; your disguise is still moving.")); return true; }
        if (!blocks.place(cell, id, PropBlocks.Kind.HIDER, option.material(), facingOf(player))) {
            player.sendActionBar(Text.mm("<yellow>Cannot lock: that cell is taken.")); return true;
        }
        locked.put(id, new Locked(snapCell(standing), standing));
        HiderBody body = bodies.get(id);
        if (body != null) body.hold();
        try { AutoCloseable rig = visuals.remove(id); if (rig != null) rig.close(); }
        catch (Exception e) { stopForPropFailure(new IllegalStateException("Could not hide the prop rig", e)); return true; }
        player.sendBlockChange(cell.getLocation(), blocks.air());
        player.sendActionBar(Text.mm("<green>Locked as " + option.name() + ". <gray>"+uk.co.playerready.pulsegames.core.util.PlatformDetector.useVerbCapital(player)+" again to move."));
        return true;
    }

    private static org.bukkit.block.BlockFace facingOf(Player player) {
        var facing = player.getFacing();
        return facing == null ? org.bukkit.block.BlockFace.NORTH : facing.getOppositeFace();
    }

    /** Restores a hider's block, returns gravity/flight, and shows the model again (when they still hold a block). */
    private void endBlockLock(Player player) {
        UUID id = player.getUniqueId();
        boolean had = blocks.hasHider(id);
        releaseBlock(id);
        PropPalette.Option option = choices.get(id);
        if (!had || option == null || visuals.containsKey(id) || !hiders.contains(id)) return;
        try { visuals.put(id, new PropRig(player, option)); }
        catch (RuntimeException e) { stopForPropFailure(e); }
    }

    /** The single restore path for a hider's world block; safe to call when none exists. */
    private void releaseBlock(UUID id) {
        HiderBody body = bodies.get(id);
        if (body != null) body.release();
        org.bukkit.block.Block cell = blocks.releaseHider(id);
        Player owner = cell == null ? null : game.player(id);
        if (owner != null) owner.sendBlockChange(cell.getLocation(), cell.getBlockData());
    }

    @Override public void onToggleFlight(Player player, org.bukkit.event.player.PlayerToggleFlightEvent event) {
        if (!locked.containsKey(player.getUniqueId())) return;
        event.setCancelled(true); player.setFlying(false);
    }

    @Override public void onBlockDamage(org.bukkit.event.block.BlockDamageEvent event) {
        if (!blockProps || !blocks.tracked(event.getBlock())) return;
        event.setCancelled(true);
        if (seekers.contains(event.getPlayer().getUniqueId())) hitAimedProp(event.getPlayer(), null);
    }

    static Location snapCell(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Locked anchor = locked.get(event.getPlayer().getUniqueId());
        if (event.getTo() == null) return;
        // Native movement already resolves the scaled player's body against terrain.
        // A moving disguise is decorative: its larger cube must not cancel valid body
        // movement along walls/steps, camera turns, or attempts to leave an overlap.
        // Full prop clearance still applies when selecting, locking and placing decoys.
        if (anchor == null) return;
        Location to = anchor.standing().clone();
        to.setYaw(event.getTo().getYaw()); to.setPitch(event.getTo().getPitch());
        event.setTo(to);
    }

    @Override public void onDamage(Player victim, org.bukkit.event.entity.EntityDamageEvent event) {
        if (locked.containsKey(victim.getUniqueId()) && !routedBlockHits.contains(victim.getUniqueId())) event.setCancelled(true);
    }

    @Override public void onSwing(Player player) { if (blockProps) hitAimedProp(player, null); }

    private void hitAimedProp(Player attacker, UUID requiredOwner) {
        if (hidePhase > 0 || game.frozen() || !game.isActive(attacker) || !seekers.contains(attacker.getUniqueId())
                || attacker.getInventory().getItemInMainHand().getType() != Material.IRON_SWORD) return;
        UUID owner = aimedProp(attacker);
        if (owner == null || (requiredOwner != null && !requiredOwner.equals(owner))) return;
        Decoy decoy = decoys.get(owner);
        Player victim = game.player(owner);
        if (decoy == null && (victim == null || !game.isActive(victim)
                || !(blocks.hasHider(owner) || (visuals.get(owner) instanceof PropRig rig && rig.active())))) return;
        long now = System.nanoTime();
        Long deadline = nextBlockHit.get(attacker.getUniqueId());
        if (deadline != null && now - deadline < 0) return;
        nextBlockHit.put(attacker.getUniqueId(), now + 600_000_000L);
        if (decoy != null) {
            removeDecoy(owner);
            game.world().playSound(decoy.feet(), "minecraft:block.glass.break", .7f, 1.5f);
            attacker.sendActionBar(Text.mm("<yellow>Decoy broken!")); return;
        }
        boolean wasBlock = blocks.hasHider(owner);
        routedBlockHits.add(owner);
        try { victim.damage(6.0, attacker); }
        finally { routedBlockHits.remove(owner); }
        if (wasBlock && hiders.contains(owner) && locked.remove(owner) != null) {
            // Damage and reveal in the same hit; a short lockout stops an instant re-hide.
            endBlockLock(victim);
            nextLock.put(owner, System.nanoTime() + REVEAL_LOCKOUT_NANOS);
            victim.sendActionBar(Text.mm("<red>You were spotted! <gray>You cannot lock for a few seconds."));
        }
    }

    /** One ray and one nearest target, regardless of Java/Geyser packet order. */
    UUID aimedProp(Player attacker) {
        Location eye = attacker.getEyeLocation();
        org.bukkit.util.Vector start = eye.toVector(), direction = eye.getDirection();
        var blockHit = attacker.rayTraceBlocks(3.0, org.bukkit.FluidCollisionMode.NEVER);
        double nearest = blockHit == null ? 3.0 * 3.0 : start.distanceSquared(blockHit.getHitPosition());
        UUID target = null;
        // A tracked world block is resolved through the cell map first: its own box would only tie the ray.
        var placed = blockHit == null ? null : blocks.at(blockHit.getHitBlock());
        if (placed != null) target = placed.owner();
        for (UUID id : hiders) {
            if (blocks.hasHider(id)) continue;
            Player hider = game.player(id);
            PropPalette.Option option = choices.get(id);
            if (hider == null || option == null || !game.isActive(hider) || hider.getWorld() != eye.getWorld()) continue;
            var hit = movingBounds(locked.containsKey(id) ? locked.get(id).standing() : hider.getLocation(), option).rayTrace(start, direction, 3.0);
            if (hit == null) continue;
            double distance = start.distanceSquared(hit.getHitPosition());
            if (distance < nearest) { nearest = distance; target = id; }
        }
        for (var entry : decoys.entrySet()) {
            var decoy = entry.getValue();
            if (decoy.visual() instanceof BlockDecoy) continue;
            var hit = movingBounds(decoy.feet(), decoy.option()).rayTrace(start, direction, 3.0);
            if (hit != null && start.distanceSquared(hit.getHitPosition()) < nearest) {
                nearest = start.distanceSquared(hit.getHitPosition()); target = entry.getKey();
            }
        }
        return target;
    }

    static org.bukkit.util.BoundingBox movingBounds(Location feet, PropPalette.Option option) {
        double half = option.height() / 2;
        return new org.bukkit.util.BoundingBox(feet.getX() - half, feet.getY(), feet.getZ() - half,
                feet.getX() + half, feet.getY() + option.height(), feet.getZ() + half);
    }

    private void stopForPropFailure(RuntimeException error) {
        game.note("prop-failure", error.getClass().getSimpleName() + ": " + error.getMessage());
        game.plugin().getLogger().log(java.util.logging.Level.WARNING, "Prop Hunt visual failure in " + game.id(), error);
        game.broadcast("<yellow>Prop Hunt stopped because a disguise could not be safely changed. Please queue again.");
        game.forceEnd();
    }

    private void equipSeeker(Player seeker) {
        seeker.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        seeker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
        if (powers) { giveControl(seeker, 1, Material.COMPASS, "Proximity pulse"); giveControl(seeker, 5, Material.FEATHER, "Search sprint"); }
        seeker.playSound(seeker.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.2f);
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (realBlocks && event.getAction().isRightClick() && blocks.tracked(event.getClickedBlock()))
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY); // No barrel/loom/smoker GUIs on a disguise.
        if (blockProps && event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
            Player player = event.getPlayer();
            if (event.getAction().isRightClick() && event.getItem() != null && powers) {
                Material type = event.getItem().getType();
                if (type == Material.ECHO_SHARD && hiders.contains(player.getUniqueId())) {
                    event.setCancelled(true); openPalette(player, true); return;
                }
                if (type == Material.BARRIER && hiders.contains(player.getUniqueId())) {
                    event.setCancelled(true); if (canUsePower(player, true)) clearDecoys(player.getUniqueId()); return;
                }
                if (type == Material.FEATHER) { event.setCancelled(true); sprint(player); return; }
                if (type == Material.COMPASS && seekers.contains(player.getUniqueId())) { event.setCancelled(true); pulse(player); return; }
            }
            if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK && seekers.contains(player.getUniqueId())) {
                event.setCancelled(true); hitAimedProp(player, null);
            }
            if (hiders.contains(player.getUniqueId()) && event.getAction().isRightClick() && event.getItem() != null) {
                if (event.getItem().getType() == Material.BOOK) { event.setCancelled(true); openPalette(player); return; }
                if (event.getItem().getType() == Material.SLIME_BALL) { event.setCancelled(true); toggleLock(player); return; }
            }
        }
        if (event.getItem() != null && event.getItem().getType() == Material.NOTE_BLOCK
                && hiders.contains(event.getPlayer().getUniqueId()) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            game.world().playSound(player.getLocation(), Sound.ENTITY_CHICKEN_AMBIENT, 2f, 1f);
            player.sendActionBar(Text.mm("<yellow>Bok bok... was that wise?"));
        }
    }

    @Override
    public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {
        if (blockProps && !routedBlockHits.contains(victim.getUniqueId())) {
            // Replace the ordinary tall player target with the chosen prop geometry.
            event.setCancelled(true);
            hitAimedProp(damager, victim.getUniqueId());
            return;
        }
        boolean seekerHitsHider = seekers.contains(damager.getUniqueId()) && hiders.contains(victim.getUniqueId());
        if (!seekerHitsHider || hidePhase > 0
                || (locked.containsKey(victim.getUniqueId()) && !routedBlockHits.contains(victim.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        if (seekers.contains(victim.getUniqueId())) {
            // Eliminating a seeker (e.g. the lone seeker falling) would leave a round
            // with nobody able to find the hiders - put them back at the release point.
            victim.setFallDistance(0);
            victim.teleport(seekerHold != null ? seekerHold : game.spawnFor(victim));
            victim.sendActionBar(Text.mm("<red>You died - back to the start."));
            return;
        }
        if (!hiders.contains(victim.getUniqueId())) return;
        try { closeVisual(victim.getUniqueId()); }
        catch (RuntimeException e) { stopForPropFailure(e); return; }
        releaseBody(victim.getUniqueId()); clearDecoys(victim.getUniqueId()); choices.remove(victim.getUniqueId());
        hiders.remove(victim.getUniqueId());
        if (isInfection()) {
            seekers.add(victim.getUniqueId());
            game.plugin().playerState().reset(victim);
            victim.teleport(game.arena().spawn(0, game.world()));
            equipSeeker(victim);
            game.broadcast("<red>" + victim.getName() + "</red> was found and joins the seekers! <gray>("
                    + hiders.size() + " hiders left)");
        } else {
            game.broadcast("<red>" + victim.getName() + "</red> was found! <gray>(" + hiders.size() + " hiders left)");
            if (rounds != null) rounds.knockOut(victim); else game.eliminate(victim);
        }
        checkWin();
    }

    /**
     * Neither role is tracked by the engine's alive set (infection eliminates nobody),
     * so a disconnect has to prune the role sets and re-run the win condition here or
     * the round can never be won.
     */
    @Override
    public void onQuit(Player player) {
        try { closeVisual(player.getUniqueId()); }
        catch (RuntimeException e) { stopForPropFailure(e); return; }
        releaseBody(player.getUniqueId()); clearDecoys(player.getUniqueId()); choices.remove(player.getUniqueId());
        boolean wasSeeker = seekers.remove(player.getUniqueId());
        boolean wasHider = hiders.remove(player.getUniqueId());
        if (rounds != null) rounds.playerLeft(player);
        if (game.state() != GameState.RUNNING) return;
        if (customProps) {
            game.broadcast("<yellow>Custom Prop Hunt stopped because its viewing players changed. Please queue again.");
            game.forceEnd();
            return;
        }
        if (!wasSeeker && !wasHider) return;
        checkWin();
    }

    private void closeVisual(UUID id) {
        releaseBlock(id);
        AutoCloseable visual = visuals.get(id);
        if (visual == null) { locked.remove(id); return; }
        try {
            visual.close(); visuals.remove(id);
            locked.remove(id);
        }
        catch (Exception e) { throw new IllegalStateException("Could not remove a Prop Hunt visual", e); }
    }

    @Override public void onEliminated(Player player) {
        try { closeVisual(player.getUniqueId()); releaseBody(player.getUniqueId()); clearDecoys(player.getUniqueId()); choices.remove(player.getUniqueId()); }
        catch (RuntimeException e) { stopForPropFailure(e); }
    }
    @Override public void onEnd(List<Player> winners) { onCleanup(); }
    @Override public void onCleanup() {
        if (rounds != null) rounds.close();
        RuntimeException failure = null;
        for (UUID id : Set.copyOf(visuals.keySet())) {
            try { closeVisual(id); }
            catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        for (UUID id : Set.copyOf(bodies.keySet())) {
            try { releaseBody(id); }
            catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        for (UUID id : Set.copyOf(decoys.keySet())) {
            try { removeDecoy(id); }
            catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        blocks.releaseAll();
        for (Player player : game.everyone()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PaletteMenu) player.closeInventory();
        }
        originalHiders.clear(); botNextPower.clear(); botLocalSearchUntil.clear(); botPaletteChosen.clear(); botGoals.clear(); botMemories.clear(); botNextAction.clear(); choices.clear(); nextBlockHit.clear(); routedBlockHits.clear(); nextLock.clear(); cooldowns.clear(); locked.clear();
        if (failure != null) throw failure;
    }

    private int bounded(String key, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, game.arena().settings().getInt(key, fallback)));
    }
    private void releaseBody(UUID id) {
        HiderBody body = bodies.get(id);
        if (body != null) { body.close(); bodies.remove(id); }
    }
    private boolean occupied(Location feet, PropPalette.Option option, UUID except) {
        var box = movingBounds(feet, option).expand(-.001);
        for (var entry : choices.entrySet()) {
            if (entry.getKey().equals(except)) continue;
            Player other = game.player(entry.getKey());
            if (other != null && game.isActive(other) && box.overlaps(movingBounds(other.getLocation(), entry.getValue()))) return true;
        }
        for (Player seeker : game.alivePlayers()) {
            if (seekers.contains(seeker.getUniqueId()) && seeker.getWorld() == feet.getWorld() && box.overlaps(seeker.getBoundingBox())) return true;
        }
        return decoys.values().stream().anyMatch(d -> box.overlaps(movingBounds(d.feet(), d.option())));
    }
    /** Citizens movement does not pass through PlayerMoveEvent. Never leave a
     * disguise at a detached anchor, or abort everyone else's match for local drift. */
    Location reconcileLock(Player player) {
        UUID id = player.getUniqueId();
        Locked anchor = locked.get(id);
        Location actual = player.getLocation();
        if (anchor == null) return actual;
        if (actual.getWorld() == anchor.standing().getWorld()
                && actual.distanceSquared(anchor.standing()) < .25) return anchor.standing();
        locked.remove(id);
        endBlockLock(player);
        game.note("prop-unlocked", "Hider moved away from locked anchor; following body again");
        player.sendActionBar(Text.mm("<yellow>Your prop moved away from its spot and was unlocked."));
        return actual;
    }

    private void updateProps() {
        if (game.state() != GameState.RUNNING) return;
        try {
            for (UUID id : List.copyOf(hiders)) {
                Player player = game.player(id);
                if (player != null && visuals.get(id) instanceof PropRig rig) rig.move(reconcileLock(player));
                else if (player != null && blocks.hasHider(id)) holdBlockHider(player, ++propTick);
            }
            for (Decoy decoy : decoys.values()) {
                if (decoy.visual() instanceof BlockDecoy) continue;
                if (!(decoy.visual() instanceof uk.co.playerready.pulsegames.core.content.PulseContentBridge.PropVisual visual) || !visual.active())
                    throw new IllegalStateException("Decoy content is inactive");
            }
        } catch (RuntimeException error) { stopForPropFailure(error); }
    }
    /** Keeps a block-locked hider still, and re-sends the owner's see-through cell (any block update restores it). */
    private void holdBlockHider(Player player, int tick) {
        UUID id = player.getUniqueId();
        reconcileLock(player);
        if (!locked.containsKey(id)) return;
        HiderBody body = bodies.get(id);
        if (body != null) body.settle();
        org.bukkit.block.Block cell = blocks.hiderCell(id);
        if (cell != null && tick % 5 == 0) player.sendBlockChange(cell.getLocation(), blocks.air());
    }
    private final class PropRig implements AutoCloseable {
        private final List<uk.co.playerready.pulsegames.core.content.PulseContentBridge.PropVisual> parts = new ArrayList<>();
        PropRig(Player player, PropPalette.Option option) {
            try {
                Set<UUID> others = new HashSet<>(visualAudience); others.remove(player.getUniqueId());
                Location feet = player.getLocation().clone(); feet.setYaw(0); feet.setPitch(0);
                if (!others.isEmpty()) parts.add(game.isBot(player)
                        ? game.plugin().content().createNpcProp(option.model(), player, feet, others, 1f)
                        : game.plugin().content().createProp(option.model(), player, feet, others, 1f));
                // Owners see the same model size as other viewers, independently of body scale.
                if (!game.isBot(player)) parts.add(game.plugin().content().createProp(option.model(), player, feet, Set.of(player.getUniqueId()), 1f));
            } catch (RuntimeException error) { try { close(); } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); } throw error; }
        }
        boolean active() { return !parts.isEmpty() && parts.stream().allMatch(uk.co.playerready.pulsegames.core.content.PulseContentBridge.PropVisual::active); }
        void move(Location position) {
            Location feet = position.clone(); feet.setYaw(0); feet.setPitch(0);
            for (var part : parts) part.move(feet);
        }
        @Override public void close() {
            RuntimeException failure = null;
            for (var part : List.copyOf(parts)) {
                try { part.close(); parts.remove(part); }
                catch (Exception error) { if (failure == null) failure = new IllegalStateException("Prop cleanup failed", error); else failure.addSuppressed(error); }
            }
            if (failure != null) throw failure;
        }
    }
    private boolean canUsePower(Player player, boolean hider) {
        return powers && game.state() == GameState.RUNNING && !game.frozen() && game.isActive(player)
                && (hider ? hiders : seekers).contains(player.getUniqueId()) && (hider || hidePhase == 0);
    }
    private boolean powerReady(Player player, String power) {
        return cooldowns.getOrDefault(player.getUniqueId() + ":" + power, 0) <= game.gameTime();
    }
    private boolean ready(Player player, String power) {
        int remaining = cooldowns.getOrDefault(player.getUniqueId() + ":" + power, 0) - game.gameTime();
        if (remaining <= 0) return true;
        player.sendActionBar(Text.mm("<yellow>" + power + " ready in " + remaining + "s.")); return false;
    }
    private void used(Player player, String power, int seconds) { cooldowns.put(player.getUniqueId() + ":" + power, game.gameTime() + seconds); }
    private void placeDecoy(Player player, PropPalette.Option option) {
        if (!canUsePower(player, true) || !ready(player, "Decoy")) return;
        if (decoys.values().stream().filter(d -> d.owner().equals(player.getUniqueId())).count() >= decoyLimit) {
            player.sendActionBar(Text.mm("<yellow>Decoy limit reached. Clear your scene or wait for one to expire.")); return;
        }
        var hit = player.rayTraceBlocks(4, org.bukkit.FluidCollisionMode.NEVER);
        if (realBlocks && hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null
                && blocks.qualifies(option.material(), hit.getHitBlock().getLocation())) {
            placeBlockDecoy(player, option, hit.getHitBlock().getRelative(hit.getHitBlockFace())); return;
        }
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() != org.bukkit.block.BlockFace.UP) {
            player.sendActionBar(Text.mm("<yellow>Aim at the top of a nearby floor or shelf.")); return;
        }
        Location feet = hit.getHitBlock().getLocation().add(.5, 0, .5);
        feet.setY(hit.getHitBlock().getBoundingBox().getMaxY());
        if (!PropSpace.clear(feet, option.height()) || !PropSpace.supported(feet) || occupied(feet, option, null)) {
            player.sendActionBar(Text.mm("<yellow>That prop does not fit there. Try a clear spot.")); return;
        }
        try {
            AutoCloseable visual = game.isBot(player)
                    ? game.plugin().content().createNpcProp(option.model(), player, feet, visualAudience, 1f)
                    : game.plugin().content().createProp(option.model(), player, feet, visualAudience, 1f);
            decoys.put(UUID.randomUUID(), new Decoy(player.getUniqueId(), option, feet, visual, game.gameTime() + decoyLifetime));
            used(player, "Decoy", decoyCooldown);
            player.sendActionBar(Text.mm("<green>Placed " + option.name() + " decoy for " + decoyLifetime + "s."));
        } catch (RuntimeException error) { stopForPropFailure(error); }
    }
    /** Real-blocks decoy: the cell next to the aimed face, any face, so decoys can stack. Neither support nor height is checked. */
    private void placeBlockDecoy(Player player, PropPalette.Option option, org.bukkit.block.Block cell) {
        Location feet = new Location(cell.getWorld(), cell.getX() + .5, cell.getY(), cell.getZ() + .5);
        if (blocks.tracked(cell) || !PropBlocks.replaceable(cell) || !PropSpace.clear(feet, option.height()) || occupied(feet, option, null)) {
            player.sendActionBar(Text.mm("<yellow>That prop does not fit there. Try a clear spot.")); return;
        }
        UUID decoyId = UUID.randomUUID();
        if (!blocks.place(cell, decoyId, PropBlocks.Kind.DECOY, option.material(), facingOf(player))) {
            player.sendActionBar(Text.mm("<yellow>That prop does not fit there. Try a clear spot.")); return;
        }
        decoys.put(decoyId, new Decoy(player.getUniqueId(), option, feet, new BlockDecoy(() -> blocks.release(cell)), game.gameTime() + decoyLifetime));
        used(player, "Decoy", decoyCooldown);
        player.sendActionBar(Text.mm("<green>Placed " + option.name() + " decoy for " + decoyLifetime + "s."));
    }
    private void clearDecoys(UUID owner) {
        for (UUID id : List.copyOf(decoys.keySet())) if (decoys.get(id).owner().equals(owner)) removeDecoy(id);
    }
    private void removeDecoy(UUID id) {
        Decoy decoy = decoys.get(id); if (decoy == null) return;
        try { decoy.visual().close(); decoys.remove(id); }
        catch (Exception error) { throw new IllegalStateException("Cannot remove decoy", error); }
    }
    private void sprint(Player player) {
        boolean hider = hiders.contains(player.getUniqueId());
        if (!canUsePower(player, hider) || !ready(player, "Sprint")) return;
        if (locked.containsKey(player.getUniqueId())) { player.sendActionBar(Text.mm("<yellow>Unlock before sprinting.")); return; }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, false, false));
        used(player, "Sprint", sprintCooldown);
        player.sendActionBar(Text.mm("<aqua>Sprint active for 4s!"));
    }
    private int pulse(Player player) {
        if (!canUsePower(player, false) || !ready(player, "Pulse")) return -1;
        double nearest = Double.POSITIVE_INFINITY;
        for (UUID id : hiders) {
            Player hider = game.player(id);
            if (hider != null && hider.getWorld() == player.getWorld()) nearest = Math.min(nearest, hider.getLocation().distance(player.getLocation()));
        }
        for (Decoy decoy : decoys.values()) nearest = Math.min(nearest, decoy.feet().distance(player.getLocation()));
        String signal = nearest <= pulseRadius / 3.0 ? "<red>Strong signal nearby" : nearest <= pulseRadius ? "<yellow>Faint signal nearby" : "<gray>No signal nearby";
        player.sendActionBar(Text.mm(signal + " <dark_gray>(decoys can fool the pulse)"));
        player.playSound(player.getLocation(), "minecraft:block.note_block.pling", .8f, nearest <= pulseRadius ? 1.5f : .6f);
        used(player, "Pulse", pulseCooldown);
        return nearest <= pulseRadius / 3.0 ? 2 : nearest <= pulseRadius ? 1 : 0;
    }

    private void checkWin() {
        if (rounds != null) {
            if (!rounds.active()) return;
            if (hiders.isEmpty()) {
                game.broadcast("<red><b>All hiders found - the seekers take the round!");
                List<List<UUID>> tiers = new ArrayList<>();
                if (!seekers.isEmpty()) tiers.add(seekers.stream().toList());
                tiers.addAll(foundTiers(rounds.tracker()));
                rounds.endRound(tiers);
            } else if (seekers.isEmpty()) {
                game.broadcast("<green><b>No seekers left - the hiders take the round!");
                List<List<UUID>> tiers = new ArrayList<>();
                tiers.add(hiders.stream().toList());
                tiers.addAll(foundTiers(rounds.tracker()));
                rounds.endRound(tiers);
            }
            return;
        }
        if (hiders.isEmpty()) {
            game.broadcast("<red><b>All hiders found - seekers win!");
            game.end(game.alivePlayers().stream().filter(p -> seekers.contains(p.getUniqueId())).toList(), "all-found");
        } else if (seekers.isEmpty()) {
            game.broadcast("<green><b>No seekers left - the hiders win!");
            game.end(game.alivePlayers().stream().filter(p -> hiders.contains(p.getUniqueId())).toList(), "no-seekers");
        }
    }

    /** Hiders survive the clock: hiders win. */
    @Override
    public void onTimeUp() {
        if (rounds != null) { rounds.onTimeUp(); return; }
        game.broadcast("<green><b>Time's up - the hiders win!");
        game.end(game.alivePlayers().stream().filter(p -> hiders.contains(p.getUniqueId())).toList(), "time");
    }

    @Override
    public List<String> sidebar(Player player) {
        if (rounds != null) {
            List<String> lines = new ArrayList<>(rounds.sidebar(player));
            lines.add("<gray>Role: " + (seekers.contains(player.getUniqueId()) ? "<red>Seeker" : "<green>Hider"));
            return lines;
        }
        return List.of(
                "<gray>Role: " + (seekers.contains(player.getUniqueId()) ? "<red>Seeker" : "<green>Hider"),
                "<gray>Hiders left: <green>" + hiders.size());
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public int timeLimitSeconds() {
        if (isRounds()) return RoundController.matchSeconds(game, roundSecondsFallback());
        return game.arena().settings().getInt("time-limit", 300);
    }
    @Override
    public String phaseCountdown() { return hidePhase > 0 ? "Seekers released in: " + hidePhase + "s" : null; }

}
