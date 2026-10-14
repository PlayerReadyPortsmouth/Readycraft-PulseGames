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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    @Override public Location botDestination(Player bot) {
        if (seekers.contains(bot.getUniqueId()) && hidePhase > 0 || locked.containsKey(bot.getUniqueId())) return null;
        Location goal = botGoals.get(bot.getUniqueId());
        return goal == null ? null : goal.clone();
    }

    @Override public void botTick(Player bot, long tick, uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        if (game.frozen() || game.state() != GameState.RUNNING || !game.isActive(bot)) return;
        UUID id = bot.getUniqueId();
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
        } else if (game.plugin().bots() != null) {
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

    static boolean visibleProp(Player observer, Location point) {
        Location eye = observer.getEyeLocation();
        if (eye.getWorld() != point.getWorld()) return false;
        var delta = point.toVector().subtract(eye.toVector());
        double distance = delta.length();
        if (distance > 16 || distance < .001) return false;
        if (eye.getDirection().dot(delta.clone().normalize()) < .25) return false;
        return eye.getWorld().rayTraceBlocks(eye, delta.normalize(), distance,
                org.bukkit.FluidCollisionMode.NEVER, true) == null;
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

    @Override
    public String startProblem(List<Player> players) {
        if (players.size() < 2) return "Prop Hunt needs at least one seeker and one hider (2 players).";
        if (!game.plugin().getConfig().getBoolean("content.prophunt.enabled", false)) return null;
        if (players.stream().anyMatch(game::isBot) && (!game.plugin().getConfig().getBoolean("content.prophunt.block-snapping", false)
                || !game.plugin().content().npcPropVisualsReady())) return "Prop Hunt bots need block disguises and PulseContent NPC prop support.";
        List<String> models = game.plugin().getConfig().getStringList("content.prophunt.props");
        if (game.plugin().getConfig().getBoolean("content.prophunt.block-snapping", false)) {
            if (!game.plugin().content().propVisualsReady()) return "Moving block disguises need PulseContent 0.3.3 or newer (prop visual API).";
            try { models = PropPalette.read(game.plugin().getConfig()).stream().map(PropPalette.Option::model).toList(); }
            catch (IllegalArgumentException e) { return e.getMessage(); }
        }
        return game.plugin().content().startProblem(models, players.stream().filter(p -> !game.isBot(p)).toList());
    }

    @Override
    public void onStart() {
        customProps = game.plugin().getConfig().getBoolean("content.prophunt.enabled", false);
        blockProps = customProps && game.plugin().getConfig().getBoolean("content.prophunt.block-snapping", false);
        if (blockProps) palette = PropPalette.read(game.plugin().getConfig());
        List<String> props = blockProps ? palette.stream().map(PropPalette.Option::model).toList()
                : game.plugin().getConfig().getStringList("content.prophunt.props");
        if (customProps) {
            String problem = startProblem(game.everyone());
            if (problem != null) throw new IllegalStateException(problem);
            visualAudience = game.everyone().stream().filter(p -> !game.isBot(p)).map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        powers = blockProps && game.arena().settings().getBoolean("prop-powers", true);
        decoyLimit = bounded("decoy-limit", 3, 1, 3);
        decoyLifetime = bounded("decoy-seconds", 60, 10, 120);
        decoyCooldown = bounded("decoy-cooldown", 12, 2, 60);
        sprintCooldown = bounded("sprint-cooldown", 25, 5, 120);
        pulseCooldown = bounded("pulse-cooldown", 25, 5, 120);
        pulseRadius = bounded("pulse-radius", 12, 4, 24);
        hidePhase = Math.max(0, game.arena().settings().getInt("hide-seconds", 30));
        List<Player> players = new ArrayList<>(game.alivePlayers());
        Collections.shuffle(players);
        int seekerCount = Math.max(1, players.size() / 5);
        seekerHold = game.arena().spawn(0, game.world());
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (i < seekerCount) {
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
                        player.sendMessage(Text.msg("<yellow>Choose a disguise during hiding time. Right-click the slime ball to lock to the block grid or move again."));
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
                taunt.editMeta(meta -> meta.displayName(Text.mm("<yellow><b>Taunt</b> <gray>(right-click... if you dare)")));
                player.getInventory().setItem(4, taunt);
                Text.title(player, "<green><b>HIDER", "<gray>Disguise: " + disguiseName);
            }
        }
        if (blockProps) game.runRepeating(1, 1, this::updateProps);
        game.broadcast("<yellow>" + seekerCount + " seeker(s)</yellow> will be released in <red>"
                + hidePhase + "s</red>. Hide!");
    }

    @Override
    public void onSecond(int gameTime) {
        for (UUID id : List.copyOf(decoys.keySet())) if (decoys.get(id).expires() <= gameTime) removeDecoy(id);
        if (customProps) {
            Set<UUID> currentAudience = game.everyone().stream().filter(p -> !game.isBot(p)).map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
            boolean valid = currentAudience.equals(visualAudience);
            for (UUID id : hiders) {
                Player hider = game.player(id);
                Locked anchor = locked.get(id);
                valid &= hider != null && (blockProps ? visuals.get(id) instanceof PropRig rig && rig.active()
                        : game.plugin().content().isActive(hider));
                if (anchor != null && hider != null) valid &= hider.getWorld().equals(anchor.standing().getWorld())
                        && hider.getLocation().distanceSquared(anchor.standing()) < 0.25;
            }
            if (!valid) {
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
        item.editMeta(meta -> meta.displayName(Text.mm("<yellow>" + name + " <gray>(right-click)")));
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
        } catch (RuntimeException e) { stopForPropFailure(); }
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
            player.sendActionBar(Text.mm("<green>Unlocked - you can move.")); return;
        }
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
        player.sendActionBar(Text.mm("<green>Locked as " + option.name() + ". <gray>Right-click again to move."));
    }

    static Location snapCell(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Locked anchor = locked.get(event.getPlayer().getUniqueId());
        if (event.getTo() == null) return;
        if (anchor == null) {
            PropPalette.Option option = choices.get(event.getPlayer().getUniqueId());
            if (blockProps && option != null && !PropSpace.clear(event.getTo(), option.height())) event.setCancelled(true);
            return;
        }
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
                || !(visuals.get(owner) instanceof PropRig rig) || !rig.active())) return;
        long now = System.nanoTime();
        Long deadline = nextBlockHit.get(attacker.getUniqueId());
        if (deadline != null && now - deadline < 0) return;
        nextBlockHit.put(attacker.getUniqueId(), now + 600_000_000L);
        if (decoy != null) {
            removeDecoy(owner);
            game.world().playSound(decoy.feet(), Sound.BLOCK_GLASS_BREAK, .7f, 1.5f);
            attacker.sendActionBar(Text.mm("<yellow>Decoy broken!")); return;
        }
        routedBlockHits.add(owner);
        try { victim.damage(6.0, attacker); }
        finally { routedBlockHits.remove(owner); }
    }

    /** One ray and one nearest target, regardless of Java/Geyser packet order. */
    UUID aimedProp(Player attacker) {
        Location eye = attacker.getEyeLocation();
        org.bukkit.util.Vector start = eye.toVector(), direction = eye.getDirection();
        var blockHit = attacker.rayTraceBlocks(3.0, org.bukkit.FluidCollisionMode.NEVER);
        double nearest = blockHit == null ? 3.0 * 3.0 : start.distanceSquared(blockHit.getHitPosition());
        UUID target = null;
        for (UUID id : hiders) {
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

    private void stopForPropFailure() {
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
        catch (RuntimeException e) { stopForPropFailure(); return; }
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
            game.eliminate(victim);
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
        catch (RuntimeException e) { stopForPropFailure(); return; }
        releaseBody(player.getUniqueId()); clearDecoys(player.getUniqueId()); choices.remove(player.getUniqueId());
        boolean wasSeeker = seekers.remove(player.getUniqueId());
        boolean wasHider = hiders.remove(player.getUniqueId());
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
        AutoCloseable visual = visuals.get(id);
        if (visual == null) return;
        try {
            visual.close(); visuals.remove(id);
            locked.remove(id);
        }
        catch (Exception e) { throw new IllegalStateException("Could not remove a Prop Hunt visual", e); }
    }

    @Override public void onEliminated(Player player) {
        try { closeVisual(player.getUniqueId()); releaseBody(player.getUniqueId()); clearDecoys(player.getUniqueId()); choices.remove(player.getUniqueId()); }
        catch (RuntimeException e) { stopForPropFailure(); }
    }
    @Override public void onEnd(List<Player> winners) { onCleanup(); }
    @Override public void onCleanup() {
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
        for (Player player : game.everyone()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PaletteMenu) player.closeInventory();
        }
        originalHiders.clear(); botGoals.clear(); botMemories.clear(); botNextAction.clear(); choices.clear(); nextBlockHit.clear(); routedBlockHits.clear(); nextLock.clear(); cooldowns.clear(); locked.clear();
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
    private void updateProps() {
        if (game.state() != GameState.RUNNING) return;
        try {
            for (UUID id : List.copyOf(hiders)) {
                Player player = game.player(id);
                if (player != null && visuals.get(id) instanceof PropRig rig) rig.move(locked.containsKey(id) ? locked.get(id).standing() : player.getLocation());
            }
            for (Decoy decoy : decoys.values()) if (!(decoy.visual() instanceof uk.co.playerready.pulsegames.core.content.PulseContentBridge.PropVisual visual) || !visual.active())
                throw new IllegalStateException("Decoy content is inactive");
        } catch (RuntimeException error) { stopForPropFailure(); }
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
                // Small local preview avoids placing the reduced first-person camera inside a full cube.
                if (!game.isBot(player)) parts.add(game.plugin().content().createProp(option.model(), player, feet, Set.of(player.getUniqueId()), .4f));
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
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() != org.bukkit.block.BlockFace.UP) {
            player.sendActionBar(Text.mm("<yellow>Aim at the top of a nearby floor or shelf.")); return;
        }
        Location feet = hit.getHitBlock().getLocation().add(.5, 0, .5);
        feet.setY(hit.getHitBlock().getBoundingBox().getMaxY());
        if (!PropSpace.clear(feet, option.height()) || !PropSpace.supported(feet) || occupied(feet, option, null)) {
            player.sendActionBar(Text.mm("<yellow>That prop does not fit there. Try a clear spot.")); return;
        }
        try {
            AutoCloseable visual = game.plugin().content().createProp(option.model(), player, feet, visualAudience, 1f);
            decoys.put(UUID.randomUUID(), new Decoy(player.getUniqueId(), option, feet, visual, game.gameTime() + decoyLifetime));
            used(player, "Decoy", decoyCooldown);
            player.sendActionBar(Text.mm("<green>Placed " + option.name() + " decoy for " + decoyLifetime + "s."));
        } catch (RuntimeException error) { stopForPropFailure(); }
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
    private void pulse(Player player) {
        if (!canUsePower(player, false) || !ready(player, "Pulse")) return;
        double nearest = Double.POSITIVE_INFINITY;
        for (UUID id : hiders) {
            Player hider = game.player(id);
            if (hider != null && hider.getWorld() == player.getWorld()) nearest = Math.min(nearest, hider.getLocation().distance(player.getLocation()));
        }
        for (Decoy decoy : decoys.values()) nearest = Math.min(nearest, decoy.feet().distance(player.getLocation()));
        String signal = nearest <= pulseRadius / 3.0 ? "<red>Strong signal nearby" : nearest <= pulseRadius ? "<yellow>Faint signal nearby" : "<gray>No signal nearby";
        player.sendActionBar(Text.mm(signal + " <dark_gray>(decoys can fool the pulse)"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .8f, nearest <= pulseRadius ? 1.5f : .6f);
        used(player, "Pulse", pulseCooldown);
    }

    private void checkWin() {
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
        game.broadcast("<green><b>Time's up - the hiders win!");
        game.end(game.alivePlayers().stream().filter(p -> hiders.contains(p.getUniqueId())).toList(), "time");
    }

    @Override
    public List<String> sidebar(Player player) {
        return List.of(
                "<gray>Role: " + (seekers.contains(player.getUniqueId()) ? "<red>Seeker" : "<green>Hider"),
                "<gray>Hiders left: <green>" + hiders.size(),
                "<gray>Time left: <white>" + Text.time(Math.max(0, timeLimitSeconds() - game.gameTime())));
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 300);
    }
}
