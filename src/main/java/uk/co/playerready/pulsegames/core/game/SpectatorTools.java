package uk.co.playerready.pulsegames.core.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.core.util.PlatformDetector;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hotbar for players who are out of the round. Vanilla spectator mode gives no usable
 * hotbar (and Bedrock has no spectator menu at all), so out-of-round players are kept in
 * adventure mode as invisible, invulnerable, flying ghosts with these tools instead.
 * Every action is a plain item tap or chest menu, which works on Java and Bedrock.
 */
public final class SpectatorTools {

    static final long EMOTE_COOLDOWN_MS = 3000L;
    private static final double EMOTE_RANGE = 48;
    private static final int MAX_HEADS = 45;

    public enum Tool {
        WATCH(0, Material.COMPASS, "<aqua><b>Watch a player", "Pick someone to fly to", false),
        QUEUE(1, Material.LIME_DYE, "<green><b>Play next game now", "Join the same game again, even while this one runs", false),
        GAMES(2, Material.NETHER_STAR, "<gold><b>Pick another game", "Open the game menu", false),
        CHEER(4, Material.FIREWORK_STAR, "<yellow>Cheer", "Sparkles and a cheer", true),
        LOVE(5, Material.PINK_DYE, "<light_purple>Love", "Floating hearts", true),
        WOW(6, Material.GLOWSTONE_DUST, "<aqua>Wow!", "A shimmer of wow", true),
        GG(7, Material.NOTE_BLOCK, "<green>Good game", "A friendly chime", true),
        LEAVE(8, Material.RED_BED, "<red>Leave game", "Return to the lobby", false);

        final int slot;
        final Material material;
        final String name;
        final String lore;
        final boolean emote;

        Tool(int slot, Material material, String name, String lore, boolean emote) {
            this.slot = slot; this.material = material; this.name = name; this.lore = lore; this.emote = emote;
        }
    }

    /** Teleport menu holder; targets are indexed by inventory slot. */
    public static final class MenuHolder implements InventoryHolder {
        final List<UUID> targets = new ArrayList<>();
        UUID leader;
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final GameInstance game;
    private final Map<UUID, Long> lastEmote = new HashMap<>();
    private NamespacedKey key;

    SpectatorTools(GameInstance game) { this.game = game; }

    private boolean enabled() { return game.plugin().getConfig().getBoolean("spectator.hotbar", true); }

    private NamespacedKey key() {
        if (key == null) key = new NamespacedKey(game.plugin(), "spectator_tool");
        return key;
    }

    // ---- entering / leaving the out-of-round state -------------------------------------

    /** Put a player into the out-of-round state: ghost with tools, or plain spectator when disabled/bot. */
    void enter(Player player) {
        if (!enabled() || game.isBot(player)) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            return;
        }
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvisible(true);
        player.setCollidable(false);
        player.setInvulnerable(true);
        give(player);
    }

    /** Undo the ghost flags; idempotent and safe for players who never were ghosts. */
    void release(Player player) {
        if (game.isBot(player)) return;
        player.setInvisible(false);
        player.setCollidable(true);
        player.setInvulnerable(false);
    }

    private void give(Player player) {
        for (Tool tool : Tool.values()) {
            ItemStack item = new ItemStack(tool.material);
            item.editMeta(meta -> {
                meta.displayName(Text.mm(tool.name));
                meta.lore(List.of(Text.mm("<gray>" + tool.lore)));
                meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, tool.name());
            });
            player.getInventory().setItem(tool.slot, item);
        }
        player.getInventory().setHeldItemSlot(0);
    }

    public Tool toolOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.STRING);
        if (raw == null) return null;
        try { return Tool.valueOf(raw); } catch (IllegalArgumentException unknown) { return null; }
    }

    /** True when the item was one of ours (the caller then cancels the interaction). */
    public boolean use(Player player, ItemStack item) {
        Tool tool = toolOf(item);
        if (tool == null) return false;
        act(player, tool);
        return true;
    }

    // ---- actions -------------------------------------------------------------------------

    void act(Player player, Tool tool) {
        if (game.state() == GameState.RESETTING || !game.isSpectator(player)) return;
        switch (tool) {
            case WATCH -> openWatchMenu(player);
            case QUEUE -> queueSame(player);
            case GAMES -> game.plugin().gameMenu().openGames(player);
            case LEAVE -> game.plugin().playService().leave(player);
            default -> emote(player, tool, System.currentTimeMillis());
        }
    }

    /**
     * Re-queue the mode just played without waiting for this match to end. Joining another
     * queue removes the player from this instance, so the results screen is still published
     * to them when the match ends (see {@link GameInstance#end}).
     */
    void queueSame(Player player) {
        PulseGamesPlugin plugin = game.plugin();
        if (plugin.playService().hasPending(player)) {
            player.sendMessage(Text.msg("<yellow>Your next game is already loading."));
            return;
        }
        if (game.practice()) {
            player.sendMessage(Text.msg("<yellow>Practice rounds are repeated from the results screen. Opening the game menu."));
            plugin.gameMenu().openGames(player);
            return;
        }
        var party = plugin.parties().partyOf(player);
        if (party != null && party.onlineMembers().stream().anyMatch(m -> !m.equals(player) && game.isAlive(m))) {
            player.sendMessage(Text.msg("<yellow>Your party is still playing. Use the game menu to queue on your own, or wait for the round to end."));
            return;
        }
        player.sendMessage(Text.msg("<green>Queueing <yellow>" + game.type().displayName()
                + "</yellow> <gray>(" + game.mode().displayName() + ")</gray>..."));
        plugin.playService().join(player, game.type(), game.mode());
    }

    void openWatchMenu(Player player) {
        List<WatchList.Candidate> targets = candidates(player);
        if (targets.isEmpty()) {
            player.sendMessage(Text.msg("<gray>Nobody is left to watch."));
            return;
        }
        MenuHolder holder = new MenuHolder();
        // Fixed size for this opening so refreshes can redraw in place; the list only ever shrinks.
        int rows = Math.min(6, (Math.min(targets.size(), MAX_HEADS) + 8) / 9 + 1);
        holder.inventory = game.plugin().getServer().createInventory(holder, rows * 9, Text.mm("<dark_gray>Watch a player"));
        render(holder, player);
        player.openInventory(holder.inventory);
    }

    /** Players still in the round, with the viewer's party and teammates marked as close. */
    private List<WatchList.Candidate> candidates(Player viewer) {
        var party = game.plugin().parties().partyOf(viewer);
        GameTeam team = game.teamOf(viewer);
        List<WatchList.Candidate> out = new ArrayList<>();
        for (Player p : game.alivePlayers()) {
            if (p.equals(viewer)) continue;
            boolean close = (party != null && party.contains(p.getUniqueId())) || (team != null && team.contains(p));
            double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 0
                    : p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            out.add(new WatchList.Candidate(p.getUniqueId(), p.getName(), close, game.score(p), p.getHealth(), max));
        }
        return out;
    }

    /** Redraws the whole menu in place, so a refresh never closes or re-opens it under a child's finger. */
    private void render(MenuHolder holder, Player viewer) {
        List<WatchList.Candidate> all = candidates(viewer);
        List<WatchList.Candidate> shown = WatchList.order(all, Math.min(MAX_HEADS, holder.inventory.getSize() - 9));
        holder.inventory.clear();
        holder.targets.clear();
        for (WatchList.Candidate c : shown) {
            Player target = game.player(c.id());
            if (target == null) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setOwningPlayer(target);
                meta.displayName(Component.text(c.name()).decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                if (c.close()) lore.add(Text.mm("<green>On your team").decoration(TextDecoration.ITALIC, false));
                String stats = WatchList.stats(c);
                if (!stats.isEmpty()) lore.add(Text.mm("<gray>" + stats).decoration(TextDecoration.ITALIC, false));
                lore.add(Text.mm("<gray>" + PlatformDetector.useVerbCapital(viewer) + " to fly to them").decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });
            holder.inventory.setItem(holder.targets.size(), head);
            holder.targets.add(c.id());
        }
        WatchList.Candidate leader = WatchList.leader(all);
        holder.leader = leader == null ? null : leader.id();
        if (leader != null) {
            ItemStack star = new ItemStack(Material.NETHER_STAR);
            star.editMeta(meta -> {
                meta.displayName(Text.mm("<gold><b>Follow the leader").decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(Text.mm("<gray>" + leader.name() + " is in front with " + leader.score())
                        .decoration(TextDecoration.ITALIC, false)));
            });
            holder.inventory.setItem(holder.inventory.getSize() - 5, star);
        }
    }

    /** Someone went out: redraw every open watch menu so nobody can pick a player who has gone. */
    void refreshMenus() {
        for (Player viewer : game.everyone()) {
            if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder
                    && holder.inventory != null) render(holder, viewer);
        }
    }

    void menuClick(Player player, MenuHolder holder, int slot) {
        if (!game.isSpectator(player)) return;
        UUID wanted;
        if (slot >= 0 && slot < holder.targets.size()) wanted = holder.targets.get(slot);
        else if (slot == holder.inventory.getSize() - 5 && holder.leader != null) wanted = holder.leader;
        else return;
        // Next tick: closing or teleporting inside the click event can drop the item on Bedrock.
        game.plugin().getServer().getScheduler().runTask(game.plugin(), () -> {
            if (!player.isOnline() || !game.isSpectator(player)) return;
            Player target = game.player(wanted);
            player.closeInventory();
            if (target == null || !game.isAlive(target) || target.getWorld() != player.getWorld()) {
                player.sendMessage(Text.msg("<gray>That player is out of the round."));
                return;
            }
            player.teleport(target.getLocation().add(0, 2, 0));
            player.sendActionBar(Text.mm("<aqua>Watching <white>" + target.getName()));
        });
    }

    /** Rate limit shared by all emotes: one every {@link #EMOTE_COOLDOWN_MS} per player. */
    boolean tryEmote(UUID player, long nowMs) {
        Long last = lastEmote.get(player);
        if (last != null && nowMs - last < EMOTE_COOLDOWN_MS) return false;
        lastEmote.put(player, nowMs);
        return true;
    }

    /** However many ghosts react at once, a viewer sees at most two reactions a second. */
    private final uk.co.playerready.pulsegames.core.util.RateCap visibleEmotes = new uk.co.playerready.pulsegames.core.util.RateCap(2, 1000);

    void emote(Player player, Tool tool, long nowMs) {
        if (!tool.emote) return;
        if (!tryEmote(player.getUniqueId(), nowMs) || !visibleEmotes.allow(nowMs)) {
            player.sendActionBar(Text.mm("<gray>Wait a moment before the next reaction"));
            return;
        }
        Player viewed = null;
        double best = EMOTE_RANGE * EMOTE_RANGE;
        for (Player candidate : game.alivePlayers()) {
            if (candidate.getWorld() != player.getWorld()) continue;
            double d = candidate.getLocation().distanceSquared(player.getLocation());
            if (d < best) { best = d; viewed = candidate; }
        }
        var at = (viewed != null ? viewed.getLocation() : player.getLocation()).add(0, 1.2, 0);
        String sound;
        float pitch;
        Particle particle;
        int count;
        switch (tool) {
            case CHEER -> { sound = "minecraft:entity.firework_rocket.twinkle"; pitch = 1f; particle = Particle.FIREWORK; count = 20; }
            case LOVE -> { sound = "minecraft:entity.cat.purr"; pitch = 1.2f; particle = Particle.HEART; count = 6; }
            case WOW -> { sound = "minecraft:entity.experience_orb.pickup"; pitch = 1.6f; particle = Particle.END_ROD; count = 20; }
            default -> { sound = "minecraft:block.note_block.chime"; pitch = 1.4f; particle = Particle.NOTE; count = 8; }
        }
        at.getWorld().spawnParticle(particle, at, count, 0.4, 0.5, 0.4, 0.05);
        for (Player p : game.everyone()) if (!game.isBot(p)) p.playSound(at, sound, 0.7f, pitch);
        player.sendActionBar(Text.mm("<green>Sent!"));
    }
}
