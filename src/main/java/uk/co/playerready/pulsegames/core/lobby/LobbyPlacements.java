package uk.co.playerready.pulsegames.core.lobby;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lidded;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.stats.WeeklyCounters;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.core.util.YamlFiles;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Admin-placed lobby features: game selector NPCs, hologram leaderboards, the daily reward chest and the lobby
 * parkour. Everything is saved in plugins/PulseGames/lobby.yml (see {@link LobbyData}) and rebuilt from it on
 * boot and on /admin games reload. Holograms are vanilla text displays; NPCs come from Citizens.
 */
public final class LobbyPlacements implements Listener {
    static final String TAG = "pulse-lobby";
    private static final String BOARD_STAT_HELP = "wins, kills or tokens (tokens: network only)";
    private static final double REMOVE_RANGE = 3.0;
    private static final long BOARD_REFRESH_MS = 60_000L;

    /** Skin names for the 24 games; override per game with lobby.npcs.&lt;game&gt;.skin. MHF heads are plain Mojang accounts. */
    static final Map<String, String> DEFAULT_SKINS = Map.ofEntries(
            Map.entry("quake", "MHF_Blaze"), Map.entry("dodgeball", "MHF_Slime"), Map.entry("dropper", "MHF_ArrowDown"),
            Map.entry("spleef", "MHF_Cactus"), Map.entry("tntrun", "MHF_TNT"), Map.entry("blockparty", "MHF_Present1"),
            Map.entry("avalanche", "MHF_Golem"), Map.entry("volcano", "MHF_LavaSlime"), Map.entry("koth", "MHF_Skeleton"),
            Map.entry("duels", "MHF_WSkeleton"), Map.entry("kitpvp", "MHF_Zombie"), Map.entry("parkour", "MHF_ArrowUp"),
            Map.entry("elytra", "MHF_Chicken"), Map.entry("karts", "MHF_Pig"), Map.entry("skywars", "MHF_Ghast"),
            Map.entry("bedwars", "MHF_Sheep"), Map.entry("buildbattle", "MHF_Villager"), Map.entry("deathrun", "MHF_Creeper"),
            Map.entry("villagerdefense", "MHF_Villager"), Map.entry("readyplayerz", "MHF_PigZombie"),
            Map.entry("luckypillars", "MHF_Chest"), Map.entry("prophunt", "MHF_Melon"), Map.entry("supermarket", "MHF_Cake"),
            Map.entry("partygames", "MHF_Present2"));

    private final PulseGamesPlugin plugin;
    private final File file;
    private final Random random = new Random();
    private LobbyData data = new LobbyData();
    private LobbyNpcs citizens;
    private boolean warnedNoCitizens;
    private BukkitTask refresh, ticker;

    private final Map<String, Object> npcs = new HashMap<>();
    private final Map<String, TextDisplay> holograms = new HashMap<>();
    private final Map<String, Long> boardAt = new HashMap<>();
    private final Map<UUID, ParkourRun> runs = new HashMap<>();
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

    public LobbyPlacements(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lobby.yml");
    }

    // ---- lifecycle -----------------------------------------------------------------

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        citizens = LobbyNpcs.connect(plugin);
        load();
        refresh = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, 40L);
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickParkour, 10L, 4L);
    }

    /** /admin games reload: forget the live NPCs and holograms and rebuild them from lobby.yml. */
    public void reload() {
        despawnAll();
        runs.clear();
        load();
        refreshAll();
    }

    public void close() {
        if (refresh != null) refresh.cancel();
        if (ticker != null) ticker.cancel();
        despawnAll();
    }

    private void load() {
        data = LobbyData.fromYaml(YamlConfiguration.loadConfiguration(file));
    }

    private void save() {
        try { YamlFiles.writeAtomically(file, data.toYaml(LocalDate.now()).saveToString()); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save lobby.yml: " + ex.getMessage()); }
    }

    private void despawnAll() {
        npcs.values().forEach(n -> { if (citizens != null) citizens.destroy(n); });
        npcs.clear();
        holograms.values().forEach(Entity::remove);
        holograms.clear();
        boardAt.clear();
        // Strays from a plugin reload or a crash: ours are tagged and never saved, but loaded ones may linger.
        for (World world : Bukkit.getWorlds())
            for (TextDisplay d : world.getEntitiesByClass(TextDisplay.class)) if (d.getScoreboardTags().contains(TAG)) d.remove();
        if (citizens != null) citizens.close();
    }

    // ---- refresh: keep NPCs and holograms alive, update their text -----------------------

    private void refreshAll() {
        var counts = plugin.quickPlay().counts();
        var modeCounts = plugin.quickPlay().modeCounts();
        long now = System.currentTimeMillis();
        for (LobbyData.Npc n : data.npcs()) {
            World world = Bukkit.getWorld(n.spot().world());
            if (world == null || !world.isChunkLoaded(n.spot().blockX() >> 4, n.spot().blockZ() >> 4)) continue;
            Location at = n.spot().toLocation(world);
            GameType type = plugin.registry().get(n.game());
            if (type == null) continue;
            ensureNpc(n, type, at);
            var c = (n.mode() == null ? counts.get(n.game()) : modeCounts.get(n.game() + "/" + n.mode()));
            int waiting = c == null ? 0 : c.waiting(), playing = c == null ? 0 : c.playing();
            hologram(n.id(), at.clone().add(0, 2.35, 0), List.of(LobbyBoardText.npcName(type.displayName()),
                    LobbyBoardText.npcCounts(waiting, playing)));
        }
        for (LobbyData.Board b : data.boards()) {
            World world = Bukkit.getWorld(b.spot().world());
            if (world == null || !world.isChunkLoaded(b.spot().blockX() >> 4, b.spot().blockZ() >> 4)) continue;
            TextDisplay existing = holograms.get(b.id());
            if (existing != null && existing.isValid() && now - boardAt.getOrDefault(b.id(), 0L) < BOARD_REFRESH_MS) continue;
            boardAt.put(b.id(), now);
            hologram(b.id(), b.spot().toLocation(world), boardLines(b));
        }
    }

    private void ensureNpc(LobbyData.Npc n, GameType type, Location at) {
        if (citizens == null) {
            if (!warnedNoCitizens) {
                warnedNoCitizens = true;
                plugin.getLogger().warning("Citizens is not enabled: lobby game NPCs are saved but cannot spawn.");
            }
            return;
        }
        try {
            Object npc = npcs.get(n.id());
            if (npc == null) npcs.put(n.id(), citizens.spawn(type.displayName(), skin(n.game()), at));
            else if (!citizens.isSpawned(npc)) citizens.respawn(npc, at);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not spawn lobby NPC " + n.id() + ": " + ex.getMessage());
        }
    }

    String skin(String game) {
        return plugin.getConfig().getString("lobby.npcs." + game + ".skin", DEFAULT_SKINS.getOrDefault(game, "MHF_Question"));
    }

    private List<String> boardLines(LobbyData.Board b) {
        if (b.kind().equals("parkour")) {
            var rows = plugin.stats().topTimes("lobby", "parkour", 5).stream()
                    .map(r -> new LobbyBoardText.Line(r.name(), ParkourRun.format(r.value()))).toList();
            return LobbyBoardText.lines("<gold><b>Lobby Parkour</b> <yellow>top 5", rows, 5);
        }
        GameType type = b.game() == null ? null : plugin.registry().get(b.game());
        var rows = plugin.stats().topStat(b.kind().equals("weekly"), b.game(), b.stat(), 3).stream()
                .map(r -> new LobbyBoardText.Line(r.name(), String.valueOf(r.value()))).toList();
        String title = LobbyBoardText.title(b.kind(), type == null ? "Network" : type.displayName(), b.stat());
        return LobbyBoardText.lines(title, rows, 3);
    }

    private void hologram(String id, Location at, List<String> lines) {
        Component text = Component.empty();
        for (int i = 0; i < lines.size(); i++) text = text.append(i == 0 ? Text.mm(lines.get(i)) : Component.newline().append(Text.mm(lines.get(i))));
        TextDisplay d = holograms.get(id);
        if (d == null || !d.isValid()) {
            d = at.getWorld().spawn(at, TextDisplay.class, t -> {
                t.setBillboard(Display.Billboard.VERTICAL);
                t.setPersistent(false);
                t.addScoreboardTag(TAG);
                t.setShadowed(true);
                t.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            });
            holograms.put(id, d);
        }
        d.text(text);
    }

    // ---- commands --------------------------------------------------------------------

    /** {@code /admin games lobby ...}; args exclude the word "lobby". */
    public void handle(Player p, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        String[] rest = a.length == 0 ? a : java.util.Arrays.copyOfRange(a, 1, a.length);
        switch (sub) {
            case "npc" -> npcCommand(p, rest);
            case "board" -> boardCommand(p, rest);
            case "chest" -> chestCommand(p, rest);
            case "parkour" -> parkourCommand(p, rest);
            default -> help(p);
        }
    }

    private void help(Player p) {
        p.sendMessage(Text.msg("<gold><b>Lobby placements</b> <gray>(stand where you want it, then run the command)"));
        for (String line : List.of(
                "/admin games lobby npc <game> [mode]</yellow> <gray>- a game selector NPC where you stand",
                "/admin games lobby npc remove</yellow> <gray>- remove the nearest NPC (within 3 blocks)",
                "/admin games lobby npc list</yellow> <gray>- every NPC placed",
                "/admin games lobby board <weekly|alltime> <game|network> [wins|kills|tokens]</yellow> <gray>- a leaderboard",
                "/admin games lobby board remove</yellow> <gray>- remove the nearest board; <yellow>board list</yellow> <gray>lists them",
                "/admin games lobby chest</yellow> <gray>- make the chest or barrel you look at the daily reward chest",
                "/admin games lobby chest remove</yellow> <gray>- stop it being the reward chest",
                "/admin games lobby parkour <start|checkpoint|end></yellow> <gray>- plate at the block you look at",
                "/admin games lobby parkour <board|reset|status></yellow> <gray>- top-5 board, clear the course, or check it",
                "/admin games reload</yellow> <gray>- reload lobby.yml along with the config"))
            p.sendMessage(Text.mm("<yellow>" + line));
    }

    private void npcCommand(Player p, String[] a) {
        if (a.length == 0) { p.sendMessage(Text.msg("<red>Usage: <yellow>/admin games lobby npc <game> [mode]</yellow>, <yellow>remove</yellow> or <yellow>list</yellow>.")); return; }
        LobbySpot here = LobbySpot.of(p.getLocation());
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "remove" -> data.nearestNpc(here, REMOVE_RANGE).ifPresentOrElse(n -> {
                data.removeNpc(n);
                dropNpc(n.id());
                save();
                p.sendMessage(Text.msg("<green>Removed the " + n.game() + " NPC (" + n.id() + ")."));
            }, () -> p.sendMessage(Text.msg("<red>No NPC within 3 blocks. Stand next to it, or use <yellow>/admin games lobby npc list</yellow>.")));
            case "list" -> {
                if (data.npcs().isEmpty()) p.sendMessage(Text.msg("<gray>No game NPCs placed yet."));
                for (var n : data.npcs()) p.sendMessage(Text.msg("<yellow>" + n.id() + "</yellow> " + n.game()
                        + (n.mode() == null ? "" : "/" + n.mode()) + " <gray>at " + where(n.spot())));
            }
            default -> {
                GameType type = plugin.registry().get(a[0]);
                if (type == null) { p.sendMessage(Text.msg("<red>Unknown game. Try <yellow>" + String.join(", ", plugin.registry().all().stream().map(GameType::id).toList()))); return; }
                GameMode mode = a.length > 1 ? type.mode(a[1]) : null;
                if (a.length > 1 && mode == null) {
                    p.sendMessage(Text.msg("<red>Unknown mode. Modes: <yellow>" + String.join(", ", type.modes().stream().map(GameMode::id).toList()))); return;
                }
                Location l = p.getLocation().clone();
                l.setYaw(l.getYaw() + 180f); // turned to face where you were looking from
                l.setPitch(0f);
                var n = data.addNpc(type.id(), mode == null ? null : mode.id(), LobbySpot.of(l));
                save();
                refreshAll();
                p.sendMessage(Text.msg("<green>Placed the " + type.displayName() + " NPC (" + n.id() + ") here. Right-click it to try it!"
                        + (citizens == null ? " <yellow>Citizens is not running, so it will appear once Citizens is enabled." : "")));
            }
        }
    }

    private void boardCommand(Player p, String[] a) {
        if (a.length == 0) { p.sendMessage(Text.msg("<red>Usage: <yellow>/admin games lobby board <weekly|alltime> <game|network> [wins|kills|tokens]</yellow>, <yellow>remove</yellow> or <yellow>list</yellow>.")); return; }
        LobbySpot here = LobbySpot.of(p.getLocation());
        String first = a[0].toLowerCase(Locale.ROOT);
        if (first.equals("remove")) {
            data.nearestBoard(here, REMOVE_RANGE).ifPresentOrElse(b -> {
                data.removeBoard(b);
                dropHologram(b.id());
                save();
                p.sendMessage(Text.msg("<green>Removed the board (" + b.id() + ")."));
            }, () -> p.sendMessage(Text.msg("<red>No board within 3 blocks. Stand next to it, or use <yellow>/admin games lobby board list</yellow>.")));
            return;
        }
        if (first.equals("list")) {
            if (data.boards().isEmpty()) p.sendMessage(Text.msg("<gray>No boards placed yet."));
            for (var b : data.boards()) p.sendMessage(Text.msg("<yellow>" + b.id() + "</yellow> " + b.kind()
                    + (b.kind().equals("parkour") ? "" : " " + (b.game() == null ? "network" : b.game()) + " " + b.stat()) + " <gray>at " + where(b.spot())));
            return;
        }
        if ((!first.equals("weekly") && !first.equals("alltime")) || a.length < 2) {
            p.sendMessage(Text.msg("<red>Usage: <yellow>/admin games lobby board <weekly|alltime> <game|network> [wins|kills|tokens]")); return;
        }
        String scope = a[1].toLowerCase(Locale.ROOT);
        String game = scope.equals("network") ? null : scope;
        if (game != null && plugin.registry().get(game) == null) { p.sendMessage(Text.msg("<red>Unknown game. Use <yellow>network</yellow> or a game id such as <yellow>spleef</yellow>.")); return; }
        String stat = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "wins";
        if (!List.of("wins", "kills", "tokens").contains(stat)) { p.sendMessage(Text.msg("<red>Stat must be " + BOARD_STAT_HELP + ".")); return; }
        if (stat.equals("tokens") && game != null) { p.sendMessage(Text.msg("<red>Token boards are network-wide. Use <yellow>network tokens</yellow>.")); return; }
        Location at = p.getLocation().clone().add(0, 1.6, 0);
        var b = data.addBoard(first, game, stat, LobbySpot.of(at));
        save();
        refreshAll();
        p.sendMessage(Text.msg("<green>Placed the " + first + " " + (game == null ? "network" : game) + " " + stat + " board (" + b.id() + ") here. It updates every minute."));
    }

    private void chestCommand(Player p, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("remove")) {
            data.setChest(null);
            save();
            p.sendMessage(Text.msg("<green>The daily reward chest is no longer set."));
            return;
        }
        Block target = p.getTargetBlockExact(6);
        if (target == null || !(target.getType() == Material.CHEST || target.getType() == Material.BARREL || target.getType() == Material.TRAPPED_CHEST)) {
            p.sendMessage(Text.msg("<red>Look at a chest or barrel (within 6 blocks) and try again.")); return;
        }
        data.setChest(LobbySpot.of(target.getLocation()));
        save();
        p.sendMessage(Text.msg("<green>That is now the daily reward chest. Players can open it once a day."));
    }

    private void parkourCommand(Player p, String[] a) {
        String sub = a.length == 0 ? "status" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reset" -> {
                data.resetParkour();
                runs.clear();
                boardAt.clear();
                holograms.keySet().removeIf(id -> {
                    boolean gone = data.boards().stream().noneMatch(b -> b.id().equals(id)) && id.startsWith("b");
                    if (gone) holograms.get(id).remove();
                    return gone;
                });
                save();
                p.sendMessage(Text.msg("<green>Parkour cleared. Plates already placed stay in the world; break them if you want them gone."));
            }
            case "status" -> p.sendMessage(Text.msg("<gold>Parkour: <white>start " + (data.parkourStart() == null ? "<red>not set" : "<green>set")
                    + "<white>, checkpoints <yellow>" + data.checkpoints().size() + "<white>, end "
                    + (data.parkourEnd() == null ? "<red>not set" : "<green>set") + "<white>. Fall height: <yellow>"
                    + String.format(Locale.ROOT, "%.1f", fallLimit())));
            case "board" -> {
                var b = data.addBoard("parkour", null, "time", LobbySpot.of(p.getLocation().clone().add(0, 1.6, 0)));
                save();
                refreshAll();
                p.sendMessage(Text.msg("<green>Placed the parkour top-5 board (" + b.id() + ") here."));
            }
            case "start", "checkpoint", "end" -> placePlate(p, sub);
            default -> p.sendMessage(Text.msg("<red>Usage: <yellow>/admin games lobby parkour <start|checkpoint|end|board|reset|status>"));
        }
    }

    private void placePlate(Player p, String kind) {
        if (!plugin.lobby().isLobbyWorld(p.getWorld())) { p.sendMessage(Text.msg("<red>The parkour has to be in the lobby world.")); return; }
        Block target = p.getTargetBlockExact(6);
        if (target == null) { p.sendMessage(Text.msg("<red>Look at the block where the plate should go (within 6 blocks).")); return; }
        Block plate = Tag.PRESSURE_PLATES.isTagged(target.getType()) ? target : target.getRelative(0, 1, 0);
        if (!Tag.PRESSURE_PLATES.isTagged(plate.getType())) {
            if (!plate.getType().isAir()) { p.sendMessage(Text.msg("<red>There is no room above that block for a plate.")); return; }
            plate.setType(kind.equals("start") ? Material.STONE_PRESSURE_PLATE
                    : kind.equals("end") ? Material.HEAVY_WEIGHTED_PRESSURE_PLATE : Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        }
        LobbySpot spot = new LobbySpot(plate.getWorld().getName(), plate.getX() + 0.5, plate.getY(), plate.getZ() + 0.5, 0f, 0f);
        switch (kind) {
            case "start" -> data.setParkourStart(spot);
            case "end" -> data.setParkourEnd(spot);
            default -> data.addCheckpoint(spot);
        }
        runs.clear();
        save();
        p.sendMessage(Text.msg("<green>" + (kind.equals("checkpoint") ? "Checkpoint " + data.checkpoints().size() : "The " + kind + " plate")
                + " is set. Next: " + (data.parkourStart() == null ? "<yellow>parkour start" : data.parkourEnd() == null ? "<yellow>parkour checkpoint</yellow> or <yellow>parkour end" : "<yellow>parkour board")));
    }

    private static String where(LobbySpot s) {
        return String.format(Locale.ROOT, "%s %.0f, %.0f, %.0f", s.world(), s.x(), s.y(), s.z());
    }

    private void dropNpc(String id) {
        Object npc = npcs.remove(id);
        if (npc != null && citizens != null) citizens.destroy(npc);
        dropHologram(id);
    }

    private void dropHologram(String id) {
        TextDisplay d = holograms.remove(id);
        if (d != null) d.remove();
        boardAt.remove(id);
    }

    public List<String> tab(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pulsegames.admin")) return List.of();
        List<String> games = plugin.registry().all().stream().map(GameType::id).toList();
        List<String> out = List.of();
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 2) out = List.of("npc", "board", "chest", "parkour");
        else if (sub.equals("npc") && args.length == 3) { out = new ArrayList<>(games); out.addAll(List.of("remove", "list")); }
        else if (sub.equals("npc") && args.length == 4 && plugin.registry().get(args[2]) != null)
            out = plugin.registry().get(args[2]).modes().stream().map(GameMode::id).toList();
        else if (sub.equals("board") && args.length == 3) out = List.of("weekly", "alltime", "remove", "list");
        else if (sub.equals("board") && args.length == 4) { out = new ArrayList<>(games); out.add("network"); }
        else if (sub.equals("board") && args.length == 5) out = List.of("wins", "kills", "tokens");
        else if (sub.equals("chest") && args.length == 3) out = List.of("remove");
        else if (sub.equals("parkour") && args.length == 3) out = List.of("start", "checkpoint", "end", "board", "reset", "status");
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(s -> s.startsWith(prefix)).toList();
    }

    // ---- NPC right-click ---------------------------------------------------------------

    @EventHandler
    public void onNpcClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !e.getRightClicked().hasMetadata("NPC") || citizens == null) return;
        for (LobbyData.Npc n : data.npcs()) {
            Object npc = npcs.get(n.id());
            if (npc == null || citizens.entityOf(npc) == null || !citizens.entityOf(npc).getUniqueId().equals(e.getRightClicked().getUniqueId())) continue;
            e.setCancelled(true);
            Player p = e.getPlayer();
            long now = System.currentTimeMillis();
            if (now - clickCooldown.getOrDefault(p.getUniqueId(), 0L) < 800) return;
            clickCooldown.put(p.getUniqueId(), now);
            GameType type = plugin.registry().get(n.game());
            if (type == null) return;
            if (n.mode() != null && type.mode(n.mode()) != null) {
                if (!plugin.gameAvailable(type)) { p.sendMessage(Text.msg("<red>" + type.displayName() + " is coming soon!")); return; }
                plugin.playService().join(p, type, type.mode(n.mode()));
            } else {
                plugin.gameMenu().pick(p, type);
            }
            return;
        }
    }

    // ---- daily chest -------------------------------------------------------------------

    @EventHandler
    public void onChestClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null || data.chest() == null) return;
        if (!LobbySpot.of(e.getClickedBlock().getLocation()).sameBlock(data.chest())) return;
        e.setCancelled(true);
        claimChest(e.getPlayer(), e.getClickedBlock());
    }

    private void claimChest(Player p, Block chest) {
        LocalDate today = LocalDate.now();
        if (!DailyChestRules.canClaim(data.claimedOn(p.getUniqueId()), today)) {
            Duration wait = DailyChestRules.untilNext(LocalDateTime.now());
            p.sendMessage(Text.msg("<yellow>You have already opened today's chest. Come back in <white>" + DailyChestRules.describe(wait) + "</white> for more!"));
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 1f);
            return;
        }
        data.markClaimed(p.getUniqueId(), today);
        save();
        int bonus = plugin.getConfig().getInt("economy.daily-bonus", 100);
        int extra = DailyChestRules.extra(random, plugin.getConfig().getInt("lobby.chest.extra-min", 5), plugin.getConfig().getInt("lobby.chest.extra-max", 25));
        plugin.economy().addTokens(p, bonus + extra, "daily chest");
        p.sendMessage(Text.msg("<gold><b>Daily chest!</b></gold> <gray>You found <yellow>" + bonus + "</yellow> tokens and a surprise <yellow>" + extra + "</yellow> more!"));
        Location top = chest.getLocation().add(0.5, 1.1, 0.5);
        if (chest.getState() instanceof Lidded lid) {
            lid.open();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (chest.getState() instanceof Lidded l) l.close(); }, 50L);
        }
        chest.getWorld().playSound(top, Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        chest.getWorld().playSound(top, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        chest.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, top, 30, 0.3, 0.4, 0.3, 0.3);
        chest.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, top, 12, 0.4, 0.3, 0.4, 0);
    }

    // ---- parkour -----------------------------------------------------------------------

    private double fallLimit() {
        Double configured = null;
        try { configured = Double.valueOf(plugin.getConfig().getString("lobby.parkour.fall-y", "auto")); } catch (NumberFormatException auto) { }
        List<Double> ys = new ArrayList<>();
        if (data.parkourStart() != null) ys.add(data.parkourStart().y());
        data.checkpoints().forEach(c -> ys.add(c.y()));
        return ParkourRun.fallLimit(configured, ys);
    }

    @EventHandler
    public void onPlate(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL || e.getClickedBlock() == null || !Tag.PRESSURE_PLATES.isTagged(e.getClickedBlock().getType())) return;
        Player p = e.getPlayer();
        if (data.parkourStart() == null || data.parkourEnd() == null || !plugin.lobby().isLobbyWorld(p.getWorld())) return;
        LobbySpot at = LobbySpot.of(e.getClickedBlock().getLocation());
        long now = System.currentTimeMillis();
        if (at.sameBlock(data.parkourStart())) {
            runs.put(p.getUniqueId(), new ParkourRun(now, data.checkpoints().size()));
            p.sendMessage(Text.msg("<green>Parkour started! Reach every checkpoint, then the end plate."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
            return;
        }
        ParkourRun run = runs.get(p.getUniqueId());
        if (run == null) return;
        if (at.sameBlock(data.parkourEnd())) {
            if (!run.canFinish()) { p.sendMessage(Text.msg("<yellow>You missed a checkpoint! Go back for checkpoint " + (run.reached() + 1) + ".")); return; }
            finishRun(p, run.elapsed(now));
            return;
        }
        for (int i = 0; i < data.checkpoints().size(); i++) {
            if (at.sameBlock(data.checkpoints().get(i)) && run.reachCheckpoint(i)) {
                p.sendMessage(Text.msg("<aqua>Checkpoint " + run.reached() + "/" + run.total() + "!"));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
            }
        }
    }

    private void finishRun(Player p, long ms) {
        runs.remove(p.getUniqueId());
        long old = plugin.stats().bestTime(p, "lobby", "parkour");
        boolean best = plugin.stats().submitBestTime(p, "lobby", "parkour", ms);
        p.sendMessage(Text.msg("<gold><b>Finished!</b></gold> <gray>Your time: <white>" + ParkourRun.format(ms)
                + (best ? (old > 0 ? " <green>New personal best!" : " <green>First time round!")
                        : " <gray>(best " + ParkourRun.format(old) + ")")));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0.1);
        data.boards().stream().filter(b -> b.kind().equals("parkour")).forEach(b -> boardAt.remove(b.id()));
    }

    private void tickParkour() {
        if (runs.isEmpty()) return;
        long now = System.currentTimeMillis();
        double limit = fallLimit();
        for (var it = runs.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !plugin.lobby().isLobbyWorld(p.getWorld()) || data.parkourStart() == null) { it.remove(); continue; }
            ParkourRun run = entry.getValue();
            if (ParkourRun.fell(p.getLocation().getY(), limit)) {
                int idx = run.respawnIndex();
                LobbySpot back = idx < 0 ? data.parkourStart() : data.checkpoints().get(Math.min(idx, data.checkpoints().size() - 1));
                Location to = back.toLocation(p.getWorld()).add(0, 0.2, 0);
                to.setYaw(p.getLocation().getYaw());
                to.setPitch(p.getLocation().getPitch());
                p.teleport(to);
                p.setFallDistance(0f);
                p.setVelocity(new org.bukkit.util.Vector());
                p.sendMessage(Text.msg("<yellow>Whoops! Back to " + (idx < 0 ? "the start" : "checkpoint " + (idx + 1)) + ". Your timer keeps running."));
            }
            p.sendActionBar(Text.mm("<gold>Parkour <white>" + ParkourRun.format(run.elapsed(now))
                    + " <gray>· checkpoint <aqua>" + run.reached() + "/" + run.total()));
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { runs.remove(e.getPlayer().getUniqueId()); clickCooldown.remove(e.getPlayer().getUniqueId()); }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e) { runs.remove(e.getPlayer().getUniqueId()); }

    List<String> npcIds() { return data.npcs().stream().map(LobbyData.Npc::id).toList(); }
}
