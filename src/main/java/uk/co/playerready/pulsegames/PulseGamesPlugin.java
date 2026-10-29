package uk.co.playerready.pulsegames;

import org.bukkit.plugin.java.JavaPlugin;
import uk.co.playerready.pulsegames.core.arena.ArenaService;
import uk.co.playerready.pulsegames.core.command.Commands;
import uk.co.playerready.pulsegames.core.economy.CosmeticsService;
import uk.co.playerready.pulsegames.core.economy.EconomyService;
import uk.co.playerready.pulsegames.core.economy.TokenShopMenu;
import uk.co.playerready.pulsegames.core.game.GameListener;
import uk.co.playerready.pulsegames.core.game.GameRegistry;
import uk.co.playerready.pulsegames.core.game.InstanceManager;
import uk.co.playerready.pulsegames.core.game.PlayService;
import uk.co.playerready.pulsegames.core.kit.KitService;
import uk.co.playerready.pulsegames.core.lobby.GameMenu;
import uk.co.playerready.pulsegames.core.lobby.LobbyService;
import uk.co.playerready.pulsegames.core.party.PartyManager;
import uk.co.playerready.pulsegames.core.player.PlayerStateService;
import uk.co.playerready.pulsegames.core.setup.ArenaSetupManager;
import uk.co.playerready.pulsegames.core.setup.MapBuilder;
import uk.co.playerready.pulsegames.core.shapes.ShapeService;
import uk.co.playerready.pulsegames.core.scoreboard.SidebarService;
import uk.co.playerready.pulsegames.core.stats.StatsService;
import uk.co.playerready.pulsegames.core.world.WorldService;
import uk.co.playerready.pulsegames.games.GameCatalog;

import java.io.File;

public final class PulseGamesPlugin extends JavaPlugin {

    private uk.co.playerready.pulsegames.core.bot.BotService bots;
    private uk.co.playerready.pulsegames.core.content.PulseContentBridge content;
    private uk.co.playerready.pulsegames.core.practice.PracticeService practice;
    private uk.co.playerready.pulsegames.core.game.MatchResults results;
    private uk.co.playerready.pulsegames.core.maps.MapReviewService maps;
    private uk.co.playerready.pulsegames.core.progression.ProgressionService progression;
    private GameRegistry registry;
    private ArenaService arenas;
    private WorldService worlds;
    private uk.co.playerready.pulsegames.core.game.MatchDiagnostics diagnostics;
    private uk.co.playerready.pulsegames.core.world.BuildWorldLifecycle buildWorlds;
    private InstanceManager instances;
    private PartyManager parties;
    private LobbyService lobby;
    private GameMenu gameMenu;
    private uk.co.playerready.pulsegames.core.lobby.DuelMenu duelMenu;
    private KitService kits;
    private uk.co.playerready.pulsegames.core.kit.KitSelectionMenu kitSelection;
    private PlayerStateService playerState;
    private SidebarService sidebar;
    private StatsService stats;
    private PlayService playService;
    private ArenaSetupManager setup;
    private uk.co.playerready.pulsegames.core.combat.ClassicCombatService combat;
    private EconomyService economy;
    private CosmeticsService cosmetics;
    private TokenShopMenu tokenShop;
    private ShapeService shapes;
    private MapBuilder mapBuilder;
    private uk.co.playerready.pulsegames.core.lobby.QuickPlayService quickPlay;
    private uk.co.playerready.pulsegames.core.progression.EngagementService engagement;
    private uk.co.playerready.pulsegames.core.lobby.LobbyPlacements lobbyPlacements;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new File(getDataFolder(), "maps").mkdirs();

        content = new uk.co.playerready.pulsegames.core.content.PulseContentBridge(this);
        registry = new GameRegistry();
        arenas = new ArenaService(this);
        worlds = new WorldService(this);
        diagnostics = new uk.co.playerready.pulsegames.core.game.MatchDiagnostics(getDataFolder().toPath().resolve("match-reports"), getLogger());
        buildWorlds = new uk.co.playerready.pulsegames.core.world.BuildWorldLifecycle(this);
        instances = new InstanceManager(this);
        bots = new uk.co.playerready.pulsegames.core.bot.BotService(this);
        parties = new PartyManager(this);
        lobby = new LobbyService(this);
        gameMenu = new GameMenu(this);
        kits = new KitService(this);
        duelMenu = new uk.co.playerready.pulsegames.core.lobby.DuelMenu(this);
        kitSelection = new uk.co.playerready.pulsegames.core.kit.KitSelectionMenu(this);
        playerState = new PlayerStateService();
        sidebar = new SidebarService();
        if (getConfig().getBoolean("debug.sidebar", false)) {
            getServer().getScheduler().runTaskTimer(this, () -> {
                String line = sidebar.drainStats();
                if (line != null) getLogger().info(line);
            }, 20L, 20L);
        }
        stats = new StatsService(this);
        playService = new PlayService(this);
        results = new uk.co.playerready.pulsegames.core.game.MatchResults(this);
        practice = new uk.co.playerready.pulsegames.core.practice.PracticeService(this);
        maps = new uk.co.playerready.pulsegames.core.maps.MapReviewService(this);
        setup = new ArenaSetupManager(this);
        economy = new EconomyService(this);
        progression = new uk.co.playerready.pulsegames.core.progression.ProgressionService(this);
        cosmetics = new CosmeticsService(economy);
        tokenShop = new TokenShopMenu(this);
        shapes = new ShapeService(this);
        mapBuilder = new MapBuilder(this);
        quickPlay = new uk.co.playerready.pulsegames.core.lobby.QuickPlayService(this);
        engagement = new uk.co.playerready.pulsegames.core.progression.EngagementService(this);

        worlds.purgeLeftovers();
        // A broken arenas/ or kits.yml must not take the rest of the wiring down with
        // it - without commands and listeners the plugin is inert on the whole network.
        try {
            arenas.load();
        } catch (Exception ex) {
            getLogger().severe("Arena load failed; continuing with no arenas: " + ex);
        }
        try {
            kits.load();
        } catch (Exception ex) {
            getLogger().severe("Kit load failed; continuing with no kits: " + ex);
        }
        GameCatalog.registerAll(registry);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new uk.co.playerready.pulsegames.core.game.FenceListener(this), this);
        getServer().getPluginManager().registerEvents(new uk.co.playerready.pulsegames.core.lobby.LobbyItemListener(lobby), this);
        getServer().getPluginManager().registerEvents(new uk.co.playerready.pulsegames.core.ui.MenuListener(), this);
        getServer().getPluginManager().registerEvents(setup, this);
        new Commands(this);
        lobbyPlacements = new uk.co.playerready.pulsegames.core.lobby.LobbyPlacements(this);
        lobbyPlacements.start();
        bots.start();
        practice.start();
        buildWorlds.start();
        combat = new uk.co.playerready.pulsegames.core.combat.ClassicCombatService(this);
        combat.start();

        getLogger().info("PulseGames enabled with " + registry.all().size() + " games.");
    }

    @Override
    public void onDisable() {
        // The flushes below are the only persistence for stats and Pulse Tokens since
        // the last 60s autosave, so nothing above them may abort onDisable.
        try {
            if (instances != null) instances.shutdownAll();
        } catch (Throwable ex) {
            getLogger().severe("Instance shutdown failed: " + ex);
        }
        try { if (content != null) content.close(); }
        catch (RuntimeException ex) { getLogger().log(java.util.logging.Level.SEVERE, "Content shutdown failed", ex); }
        try { if (lobbyPlacements != null) lobbyPlacements.close(); }
        catch (RuntimeException ex) { getLogger().log(java.util.logging.Level.SEVERE, "Lobby placements shutdown failed", ex); }
        try { if (bots != null) bots.close(); }
        catch (RuntimeException ex) { getLogger().log(java.util.logging.Level.SEVERE, "Bot shutdown failed", ex); }
        if (diagnostics != null) diagnostics.close();
        if (stats != null) stats.flush();
        if (economy != null) economy.flush();
        if (engagement != null) engagement.flush();
    }

    public uk.co.playerready.pulsegames.core.bot.BotService bots() { return bots; }

    public boolean canUseNewFeatures(org.bukkit.entity.Player player) {
        return getConfig().getBoolean("features.broad", false)
                || getConfig().getStringList("features.canary-players").contains(player.getUniqueId().toString());
    }

    public boolean gameAvailable(uk.co.playerready.pulsegames.core.game.GameType type) {
        return !type.wip() || gamePublic(type) || getConfig().getStringList("games.preview").contains(type.id());
    }

    public boolean gamePublic(uk.co.playerready.pulsegames.core.game.GameType type) {
        return getConfig().getStringList("games.public").contains(type.id());
    }

    public uk.co.playerready.pulsegames.core.content.PulseContentBridge content() { return content; }

    public uk.co.playerready.pulsegames.core.practice.PracticeService practice() { return practice; }

    public uk.co.playerready.pulsegames.core.game.MatchResults results() { return results; }

    public GameRegistry registry() { return registry; }
    public ArenaService arenas() { return arenas; }
    public uk.co.playerready.pulsegames.core.world.BuildWorldLifecycle buildWorlds() { return buildWorlds; }

    /** Network helper return hook: existing permanent build worlds only. */
    public org.bukkit.World loadReturnWorld(String name) {
        return worlds.loadReturnWorld(name);
    }

    public uk.co.playerready.pulsegames.core.game.MatchDiagnostics diagnostics() { return diagnostics; }

    public WorldService worlds() { return worlds; }
    public InstanceManager instances() { return instances; }
    public PartyManager parties() { return parties; }
    public LobbyService lobby() { return lobby; }
    public GameMenu gameMenu() { return gameMenu; }
    public uk.co.playerready.pulsegames.core.kit.KitSelectionMenu kitSelection() { return kitSelection; }
    public uk.co.playerready.pulsegames.core.lobby.DuelMenu duelMenu() { return duelMenu; }
    public KitService kits() { return kits; }
    public PlayerStateService playerState() { return playerState; }
    public SidebarService sidebar() { return sidebar; }
    public StatsService stats() { return stats; }
    public uk.co.playerready.pulsegames.core.progression.ProgressionService progression() { return progression; }
    public PlayService playService() { return playService; }
    public uk.co.playerready.pulsegames.core.combat.ClassicCombatService combat() { return combat; }
    public ArenaSetupManager setup() { return setup; }
    public uk.co.playerready.pulsegames.core.maps.MapReviewService maps() { return maps; }
    public EconomyService economy() { return economy; }
    public CosmeticsService cosmetics() { return cosmetics; }
    public TokenShopMenu tokenShop() { return tokenShop; }
    public ShapeService shapes() { return shapes; }
    public MapBuilder mapBuilder() { return mapBuilder; }
    public uk.co.playerready.pulsegames.core.lobby.QuickPlayService quickPlay() { return quickPlay; }
    public uk.co.playerready.pulsegames.core.progression.EngagementService engagement() { return engagement; }
    public uk.co.playerready.pulsegames.core.lobby.LobbyPlacements lobbyPlacements() { return lobbyPlacements; }
    /** Public optional bridge API; called on the server thread by PulseNetwork. */
    public java.util.List<java.util.Map<String, String>> networkQueues() {
        return instances().all().stream()
                .filter(g -> (g.state() == uk.co.playerready.pulsegames.core.game.GameState.WAITING
                        || g.state() == uk.co.playerready.pulsegames.core.game.GameState.COUNTDOWN)
                        && !g.practice() && g.humanCount() > 0 && g.joinable(1) && !g.type().wip() && gameAvailable(g.type()))
                .map(g -> java.util.Map.of("id", g.id(), "title", g.type().displayName() + " / " + g.mode().displayName(),
                        "players", Integer.toString(g.players().size()), "maximum", Integer.toString(g.maxPlayers())))
                .toList();
    }

    public boolean networkPlayerBusy(org.bukkit.entity.Player player) {
        return instances().byPlayer(player) != null || playService().hasPending(player)
                || results != null && results.holdsReturn(player);
    }

    public void networkPartyState(org.bukkit.entity.Player player, java.util.UUID leader, java.util.List<java.util.UUID> members) {
        parties.networkState(player, leader, members);
    }

    public boolean joinNetworkParty(org.bukkit.entity.Player leader, String instanceId, java.util.List<java.util.UUID> members) {
        if (!parties.importNetworkParty(leader,members)) return false;
        var players=members.stream().map(getServer()::getPlayer).toList();
        return playService.joinNetworkPartyQueue(leader,instanceId,players);
    }

    public boolean networkPartyCommand(org.bukkit.entity.Player player, String[] arguments) {
        var helper=getServer().getPluginManager().getPlugin("PulseNetwork");
        if(helper==null || !helper.isEnabled()) return false;
        try { return Boolean.TRUE.equals(helper.getClass().getMethod("networkPartyCommand",org.bukkit.entity.Player.class,String[].class).invoke(helper,player,arguments)); }
        catch(NoSuchMethodException absent) { return false; }
        catch(ReflectiveOperationException failure) { player.sendMessage(net.kyori.adventure.text.Component.text("Network party service is unavailable. Please try again.")); return true; }
    }

    public void joinNetworkQueue(org.bukkit.entity.Player player, String instanceId) {
        playService().joinNetworkQueue(player, instanceId);
    }
}
