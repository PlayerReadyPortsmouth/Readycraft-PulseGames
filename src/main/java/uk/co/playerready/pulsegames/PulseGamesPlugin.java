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
    private GameRegistry registry;
    private ArenaService arenas;
    private WorldService worlds;
    private InstanceManager instances;
    private PartyManager parties;
    private LobbyService lobby;
    private GameMenu gameMenu;
    private KitService kits;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new File(getDataFolder(), "maps").mkdirs();

        content = new uk.co.playerready.pulsegames.core.content.PulseContentBridge(this);
        registry = new GameRegistry();
        arenas = new ArenaService(this);
        worlds = new WorldService(this);
        instances = new InstanceManager(this);
        bots = new uk.co.playerready.pulsegames.core.bot.BotService(this);
        parties = new PartyManager(this);
        lobby = new LobbyService(this);
        gameMenu = new GameMenu(this);
        kits = new KitService(this);
        playerState = new PlayerStateService();
        sidebar = new SidebarService();
        stats = new StatsService(this);
        playService = new PlayService(this);
        setup = new ArenaSetupManager(this);
        economy = new EconomyService(this);
        cosmetics = new CosmeticsService(economy);
        tokenShop = new TokenShopMenu(this);
        shapes = new ShapeService(this);
        mapBuilder = new MapBuilder(this);

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
        getServer().getPluginManager().registerEvents(new uk.co.playerready.pulsegames.core.lobby.LobbyItemListener(lobby), this);
        getServer().getPluginManager().registerEvents(gameMenu, this);
        getServer().getPluginManager().registerEvents(setup, this);
        getServer().getPluginManager().registerEvents(tokenShop, this);
        new Commands(this);
        bots.start();
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
        try { if (bots != null) bots.close(); }
        catch (RuntimeException ex) { getLogger().log(java.util.logging.Level.SEVERE, "Bot shutdown failed", ex); }
        if (stats != null) stats.flush();
        if (economy != null) economy.flush();
    }

    public uk.co.playerready.pulsegames.core.bot.BotService bots() { return bots; }

    public boolean canUseNewFeatures(org.bukkit.entity.Player player) {
        return getConfig().getBoolean("features.broad", false)
                || getConfig().getStringList("features.canary-players").contains(player.getUniqueId().toString());
    }

    public boolean gameAvailable(uk.co.playerready.pulsegames.core.game.GameType type) {
        return !type.wip() || getConfig().getStringList("games.preview").contains(type.id());
    }

    public uk.co.playerready.pulsegames.core.content.PulseContentBridge content() { return content; }

    public GameRegistry registry() { return registry; }
    public ArenaService arenas() { return arenas; }
    public WorldService worlds() { return worlds; }
    public InstanceManager instances() { return instances; }
    public PartyManager parties() { return parties; }
    public LobbyService lobby() { return lobby; }
    public GameMenu gameMenu() { return gameMenu; }
    public KitService kits() { return kits; }
    public PlayerStateService playerState() { return playerState; }
    public SidebarService sidebar() { return sidebar; }
    public StatsService stats() { return stats; }
    public PlayService playService() { return playService; }
    public uk.co.playerready.pulsegames.core.combat.ClassicCombatService combat() { return combat; }
    public ArenaSetupManager setup() { return setup; }
    public EconomyService economy() { return economy; }
    public CosmeticsService cosmetics() { return cosmetics; }
    public TokenShopMenu tokenShop() { return tokenShop; }
    public ShapeService shapes() { return shapes; }
    public MapBuilder mapBuilder() { return mapBuilder; }
}
