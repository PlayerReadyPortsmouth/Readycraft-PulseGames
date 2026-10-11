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
 * Prop Hunt: optional PulseContent block palette and owned native block locks.
 * The flag-off game retains its original invisible helmet disguises.
 * Modes: classic (found = spectate), infection (found = join the seekers).
 */
public final class PropHuntGame extends MiniGame {

    private static final Material[] DISGUISES = {
            Material.HAY_BLOCK, Material.BARREL, Material.CRAFTING_TABLE, Material.BOOKSHELF,
            Material.MELON, Material.PUMPKIN, Material.CACTUS, Material.OAK_LOG, Material.TNT
    };

    private final Set<UUID> seekers = new HashSet<>();
    private final Set<UUID> hiders = new HashSet<>();
    private final Map<UUID, AutoCloseable> visuals = new HashMap<>();
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

    private boolean isInfection() { return game.mode().id().equals("infection"); }

    @Override
    public String startProblem(List<Player> players) {
        if (!game.plugin().getConfig().getBoolean("content.prophunt.enabled", false)) return null;
        if (players.stream().anyMatch(game::isBot)) return "Custom Prop Hunt does not support NPC props yet.";
        List<String> models = game.plugin().getConfig().getStringList("content.prophunt.props");
        if (game.plugin().getConfig().getBoolean("content.prophunt.block-snapping", false)) {
            if (!game.plugin().content().blockPropsReady()) return "Block disguises need the enabled PulseContent block-prop API.";
            try { models = PropPalette.read(game.plugin().getConfig()).stream().map(PropPalette.Option::model).toList(); }
            catch (IllegalArgumentException e) { return e.getMessage(); }
        }
        return game.plugin().content().startProblem(models, players);
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
            visualAudience = game.everyone().stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
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
                String disguiseName;
                if (customProps) {
                    int index = random.nextInt(props.size());
                    String id = props.get(index);
                    visuals.put(player.getUniqueId(), game.plugin().content().attach(id, player, visualAudience));
                    disguiseName = blockProps ? palette.get(index).name() : "custom prop";
                    if (blockProps) {
                        choices.put(player.getUniqueId(), palette.get(index));
                        giveControl(player, 0, Material.BOOK, "Choose disguise");
                        giveControl(player, 1, Material.SLIME_BALL, "Lock / unlock prop");
                        player.sendMessage(Text.msg("<yellow>Choose a disguise during hiding time. Right-click the slime ball to lock to the block grid or move again."));
                        game.runLater(1, () -> openPalette(player));
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
        game.broadcast("<yellow>" + seekerCount + " seeker(s)</yellow> will be released in <red>"
                + hidePhase + "s</red>. Hide!");
    }

    @Override
    public void onSecond(int gameTime) {
        if (customProps) {
            Set<UUID> currentAudience = game.everyone().stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
            boolean valid = currentAudience.equals(visualAudience);
            for (UUID id : hiders) {
                Player hider = game.plugin().getServer().getPlayer(id);
                Locked anchor = locked.get(id);
                valid &= hider != null && (anchor == null ? game.plugin().content().isActive(hider)
                        : game.plugin().content().isBlockPropActive(hider)
                        && hider.getWorld().equals(anchor.standing().getWorld())
                        && hider.getLocation().distanceSquared(anchor.standing()) < 0.25);
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
                Player seeker = game.plugin().getServer().getPlayer(id);
                if (seeker != null && (!seeker.getWorld().equals(seekerHold.getWorld())
                        || seeker.getLocation().distanceSquared(seekerHold) > 4)) {
                    seeker.teleport(seekerHold);
                }
            }
            if (hidePhase == 0) {
                game.broadcast("<red><b>The seekers have been released!");
                for (UUID id : seekers) {
                    Player seeker = game.plugin().getServer().getPlayer(id);
                    if (seeker != null) equipSeeker(seeker);
                }
            }
            return;
        }
        // Heartbeat: every 30s all hiders emit a sound so camping isn't free.
        if (gameTime % 30 == 0) {
            game.broadcast("<dark_gray>The props made a sound...");
            for (UUID id : hiders) {
                Player hider = game.plugin().getServer().getPlayer(id);
                if (hider == null) continue;
                game.world().playSound(hider.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 0.8f);
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

    private void openPalette(Player player) {
        if (!canChoose(player)) {
            if (hiders.contains(player.getUniqueId())) player.sendActionBar(Text.mm("<yellow>Choose while unlocked, before seekers are released."));
            return;
        }
        PaletteMenu holder = new PaletteMenu(player.getUniqueId());
        holder.inventory = org.bukkit.Bukkit.createInventory(holder, 27, Text.mm("<dark_gray>Choose your prop"));
        for (int i = 0; i < palette.size(); i++) {
            PropPalette.Option option = palette.get(i);
            ItemStack item = new ItemStack(option.material());
            item.editMeta(meta -> {
                meta.displayName(Text.mm("<yellow>" + option.name()));
                meta.lore(List.of(Text.mm(option.material() == Material.PLAYER_HEAD
                        ? "<gray>Small head when locked" : "<gray>Full block when locked"),
                        Text.mm("<gray>Click to choose")));
            });
            holder.inventory.setItem(i, item);
        }
        player.openInventory(holder.inventory);
    }

    @Override public void onInventoryClick(Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PaletteMenu holder)) return;
        event.setCancelled(true);
        if (holder.owner != this || !holder.player.equals(player.getUniqueId()) || !canChoose(player)
                || event.getClickedInventory() != event.getView().getTopInventory()
                || event.getSlot() < 0 || event.getSlot() >= palette.size()) return;
        PropPalette.Option option = palette.get(event.getSlot());
        try {
            closeVisual(player.getUniqueId());
            visuals.put(player.getUniqueId(), game.plugin().content().attach(option.model(), player, visualAudience));
            choices.put(player.getUniqueId(), option);
            player.sendActionBar(Text.mm("<green>Disguise: " + option.name()));
            game.runLater(1, player::closeInventory);
        } catch (RuntimeException e) { stopForPropFailure(); }
    }

    @Override public void onInventoryDrag(Player player, org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PaletteMenu) event.setCancelled(true);
    }

    /** Native head/full-block geometry is owned by PulseContent; no client-only block illusion. */
    private void toggleLock(Player player) {
        UUID id = player.getUniqueId();
        if (game.state() != GameState.RUNNING || !game.isActive(player) || game.frozen()
                || player.isInsideVehicle() || !hiders.contains(id)) return;
        PropPalette.Option option = choices.get(id);
        if (option == null) return;
        Locked previous = locked.get(id);
        if (previous != null) {
            try {
                closeVisual(id);
                visuals.put(id, game.plugin().content().attach(option.model(), player, visualAudience));
                Location exit = previous.cell().clone().add(0.5, 0, 0.5);
                exit.setYaw(player.getLocation().getYaw()); exit.setPitch(player.getLocation().getPitch());
                if (!player.teleport(exit)) { stopForPropFailure(); return; }
                player.sendActionBar(Text.mm("<green>Unlocked — you can move."));
            } catch (RuntimeException e) { stopForPropFailure(); }
            return;
        }
        if (!player.isOnGround()) { player.sendActionBar(Text.mm("<yellow>Stand on a solid floor to lock.")); return; }
        Location cell = snapCell(player.getLocation());
        try {
            closeVisual(id);
            AutoCloseable block = game.plugin().content().attachBlockProp(player, option.material(), cell, visualAudience);
            visuals.put(id, block);
            Location standing = cell.clone().add(0.5, option.height(), 0.5);
            standing.setYaw(player.getLocation().getYaw()); standing.setPitch(player.getLocation().getPitch());
            locked.put(id, new Locked(cell, standing));
            // This game owns role potion effects; hide the local third-person body too.
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    PotionEffect.INFINITE_DURATION, 0, false, false));
            if (!player.teleport(standing)) { stopForPropFailure(); return; }
            player.sendActionBar(Text.mm("<green>Locked as " + option.name() + ". <gray>Right-click the slime ball to move."));
        } catch (RuntimeException e) {
            try {
                closeVisual(id);
                visuals.put(id, game.plugin().content().attach(option.model(), player, visualAudience));
                player.sendActionBar(Text.mm("<yellow>Cannot lock here. Choose clear space on a full solid floor."));
            } catch (RuntimeException failedRestore) { stopForPropFailure(); }
        }
    }

    static Location snapCell(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Locked anchor = locked.get(event.getPlayer().getUniqueId());
        if (anchor == null || event.getTo() == null) return;
        Location to = anchor.standing().clone();
        to.setYaw(event.getTo().getYaw()); to.setPitch(event.getTo().getPitch());
        event.setTo(to);
    }

    @Override public void onDamage(Player victim, org.bukkit.event.entity.EntityDamageEvent event) {
        if (locked.containsKey(victim.getUniqueId()) && !routedBlockHits.contains(victim.getUniqueId())) event.setCancelled(true);
    }

    private UUID ownerOf(org.bukkit.block.Block block) {
        if (block == null) return null;
        for (var entry : locked.entrySet()) {
            Location cell = entry.getValue().cell();
            if (cell.getWorld().equals(block.getWorld()) && cell.getBlockX() == block.getX()
                    && cell.getBlockY() == block.getY() && cell.getBlockZ() == block.getZ()) return entry.getKey();
        }
        return null;
    }

    @Override public void onSwing(Player player) { if (blockProps) hitAimedProp(player, null); }

    private void hitBlock(Player attacker, UUID owner) { hitAimedProp(attacker, owner); }

    private void hitAimedProp(Player attacker, UUID requiredOwner) {
        if (hidePhase > 0 || game.frozen() || !game.isActive(attacker) || !seekers.contains(attacker.getUniqueId())
                || attacker.getInventory().getItemInMainHand().getType() != Material.IRON_SWORD) return;
        UUID owner = aimedProp(attacker);
        if (owner == null || (requiredOwner != null && !requiredOwner.equals(owner))) return;
        Player victim = game.player(owner);
        if (victim == null || !game.isActive(victim)) return;
        boolean ready = locked.containsKey(owner) ? game.plugin().content().isBlockPropActive(victim)
                : game.plugin().content().isActive(victim);
        if (!ready) return;
        long now = System.nanoTime();
        Long deadline = nextBlockHit.get(attacker.getUniqueId());
        if (deadline != null && now - deadline < 0) return;
        nextBlockHit.put(attacker.getUniqueId(), now + 600_000_000L);
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
        UUID target = blockHit == null ? null : ownerOf(blockHit.getHitBlock());
        for (UUID id : hiders) {
            if (locked.containsKey(id)) continue; // Native raytrace already uses the actual head/block shape.
            Player hider = game.player(id);
            PropPalette.Option option = choices.get(id);
            if (hider == null || option == null || !game.isActive(hider) || hider.getWorld() != eye.getWorld()) continue;
            var hit = movingBounds(hider.getLocation(), option).rayTrace(start, direction, 3.0);
            if (hit == null) continue;
            double distance = start.distanceSquared(hit.getHitPosition());
            if (distance < nearest) { nearest = distance; target = id; }
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
        seeker.playSound(seeker.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.2f);
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (blockProps && event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
            Player player = event.getPlayer();
            UUID target = ownerOf(event.getClickedBlock());
            if (target != null) {
                event.setCancelled(true);
                if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) hitBlock(player, target);
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
            if (locked.remove(id) != null) {
                Player player = game.player(id);
                if (player != null) player.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
        }
        catch (Exception e) { throw new IllegalStateException("Could not remove a Prop Hunt visual", e); }
    }

    @Override public void onEliminated(Player player) {
        try { closeVisual(player.getUniqueId()); }
        catch (RuntimeException e) { stopForPropFailure(); }
    }
    @Override public void onEnd(List<Player> winners) { onCleanup(); }
    @Override public void onCleanup() {
        RuntimeException failure = null;
        for (UUID id : Set.copyOf(visuals.keySet())) {
            try { closeVisual(id); }
            catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        for (Player player : game.everyone()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PaletteMenu) player.closeInventory();
        }
        choices.clear(); nextBlockHit.clear(); routedBlockHits.clear();
        if (failure != null) throw failure;
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
