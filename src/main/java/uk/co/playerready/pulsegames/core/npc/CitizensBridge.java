package uk.co.playerready.pulsegames.core.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The only class that touches the Citizens API - never load it unless the
 * Citizens plugin is installed. NPCs live in our own in-memory registry so
 * Citizens doesn't persist them itself; npcs.yml is the source of truth and
 * everything is respawned fresh each boot.
 */
final class CitizensBridge implements Listener {

    private record Spawned(NPC npc, NpcDefinition def) {}

    private final PulseGamesPlugin plugin;
    private final NPCRegistry registry;
    private final List<Spawned> spawned = new ArrayList<>();
    /** "<playerUuid>|<npcId>" -> epoch millis a greeter may next speak to that player. */
    private final Map<String, Long> chatCooldowns = new HashMap<>();
    private final BukkitTask greeterTask;

    CitizensBridge(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.registry = CitizensAPI.createNamedNPCRegistry("pulsegames", new MemoryNPCDataStore());
        this.greeterTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickGreeters, 20L, 20L);
    }

    void spawn(NpcDefinition def, World world) {
        NPC npc = registry.createNPC(EntityType.PLAYER, Text.legacy(Text.mm(def.name())));
        npc.setProtected(true);
        if (def.skin() != null) {
            npc.getOrAddTrait(SkinTrait.class).setSkinName(def.skin());
        }
        npc.getOrAddTrait(LookClose.class).lookClose(true);
        npc.spawn(def.locationIn(world));
        spawned.add(new Spawned(npc, def));
    }

    /** Removes every live copy of this definition (lobby, build worlds and instances). */
    void despawnDefinition(NpcDefinition def) {
        for (Iterator<Spawned> it = spawned.iterator(); it.hasNext(); ) {
            Spawned entry = it.next();
            if (entry.def() == def) {
                entry.npc().destroy();
                it.remove();
            }
        }
    }

    /** Removes all NPCs in a world about to be unloaded/deleted. */
    void despawnWorld(World world) {
        for (Iterator<Spawned> it = spawned.iterator(); it.hasNext(); ) {
            Spawned entry = it.next();
            if (entry.npc().isSpawned() && world.equals(entry.npc().getEntity().getWorld())) {
                entry.npc().destroy();
                it.remove();
            }
        }
    }

    /** Re-creates live copies after a definition change (e.g. a new skin). */
    void respawn(NpcDefinition def) {
        List<World> worlds = new ArrayList<>();
        for (Spawned entry : spawned) {
            if (entry.def() == def && entry.npc().isSpawned()) {
                worlds.add(entry.npc().getEntity().getWorld());
            }
        }
        despawnDefinition(def);
        worlds.forEach(world -> spawn(def, world));
    }

    void removeAll() {
        greeterTask.cancel();
        spawned.forEach(entry -> entry.npc().destroy());
        spawned.clear();
        registry.deregisterAll();
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        NpcDefinition def = definitionOf(event.getNPC());
        if (def == null) return;
        Player player = event.getClicker();
        if (def.kind() == NpcDefinition.Kind.GREETER) {
            say(def, player);
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.3f);
        GameType game = def.gameId() == null ? null : plugin.registry().get(def.gameId());
        if (game == null) {
            plugin.gameMenu().openGames(player);
        } else {
            plugin.gameMenu().openGame(player, game);
        }
    }

    private NpcDefinition definitionOf(NPC npc) {
        for (Spawned entry : spawned) {
            if (entry.npc().getId() == npc.getId()
                    && entry.npc().getOwningRegistry() == npc.getOwningRegistry()) {
                return entry.def();
            }
        }
        return null;
    }

    // ---- greeter chatter ---------------------------------------------------------

    private void tickGreeters() {
        for (Spawned entry : spawned) {
            NpcDefinition def = entry.def();
            if (def.kind() != NpcDefinition.Kind.GREETER || def.lines().isEmpty()
                    || !entry.npc().isSpawned()) {
                continue;
            }
            var loc = entry.npc().getEntity().getLocation();
            for (Player player : loc.getWorld().getNearbyPlayers(loc, def.radius())) {
                say(def, player);
            }
        }
    }

    /** Sends one of the greeter's lines, rate-limited per player per NPC. */
    private void say(NpcDefinition def, Player player) {
        if (def.lines().isEmpty()) return;
        String key = player.getUniqueId() + "|" + def.id();
        long now = System.currentTimeMillis();
        Long next = chatCooldowns.get(key);
        if (next != null && now < next) return;
        long cooldown = plugin.getConfig().getLong("npc.chat-cooldown-seconds", 25) * 1000L;
        chatCooldowns.put(key, now + cooldown);

        String line = def.lines().get(ThreadLocalRandom.current().nextInt(def.lines().size()));
        player.sendMessage(Text.mm(def.name() + " <dark_gray>» <gray>" + line));
        if (!Text.calm(player)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.6f, 1.2f);
        }
    }
}
