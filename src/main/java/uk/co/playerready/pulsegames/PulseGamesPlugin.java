package uk.co.playerready.pulsegames;

import org.bukkit.plugin.java.JavaPlugin;
import uk.co.playerready.pulsegames.core.achieve.AchievementService;
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
import uk.co.playerready.pulsegames.core.leaderboard.LeaderboardService;
import uk.co.playerready.pulsegames.core.level.LevelService;
import uk.co.playerready.pulsegames.core.lobby.GameMenu;
import uk.co.playerready.pulsegames.core.lobby.LobbyService;
import uk.co.playerready.pulsegames.core.npc.NpcService;
import uk.co.playerready.pulsegames.core.party.PartyManager;
import uk.co.playerready.pulsegames.core.player.PlayerStateService;
import uk.co.playerready.pulsegames.core.player.PrefsService;
import uk.co.playerready.pulsegames.core.quest.QuestService;
import uk.co.playerready.pulsegames.core.setup.ArenaSetupManager;
import uk.co.playerready.pulsegames.core.shapes.ShapeService;
import uk.co.playerready.pulsegames.core.scoreboard.SidebarService;
import uk.co.playerready.pulsegames.core.stats.StatsService;
import uk.co.playerready.pulsegames.core.trivia.TriviaService;
import uk.co.playerready.pulsegames.core.util.MenuFx;
import uk.co.playerready.pulsegames.core.world.WorldService;
import uk.co.playerready.pulsegames.games.GameCatalog;

import java.io.File;

public final class PulseGamesPlugin extends JavaPlugin {

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
    private EconomyService economy;
    private CosmeticsService cosmetics;
    private TokenShopMenu tokenShop;
    private ShapeService shapes;
    private PrefsService prefs;
    private LevelService levels;
    private QuestService quests;
    private AchievementService achievements;
    private TriviaService trivia;
    private LeaderboardService leaderboards;
    private NpcService npcs;
    private MenuFx menuFx;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new File(getDataFolder(), "maps").mkdirs();

        registry = new GameRegistry();
        menuFx = new MenuFx(this);
        arenas = new ArenaService(this);
        worlds = new WorldService(this);
        instances = new InstanceManager(this);
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
        prefs = new PrefsService(this);
        uk.co.playerready.pulsegames.core.util.Text.init(prefs);
        levels = new LevelService(this);
        quests = new QuestService(this);
        achievements = new AchievementService(this);
        trivia = new TriviaService(this);
        leaderboards = new LeaderboardService(this);
        npcs = new NpcService(this);

        worlds.purgeLeftovers();
        arenas.load();
        kits.load();
        GameCatalog.registerAll(registry);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(gameMenu, this);
        getServer().getPluginManager().registerEvents(setup, this);
        getServer().getPluginManager().registerEvents(tokenShop, this);
        getServer().getPluginManager().registerEvents(levels, this);
        getServer().getPluginManager().registerEvents(trivia, this);
        getServer().getPluginManager().registerEvents(menuFx, this);
        getServer().getPluginManager().registerEvents(npcs, this);
        npcs.enable();
        new Commands(this);

        getLogger().info("PulseGames enabled with " + registry.all().size() + " games.");
    }

    @Override
    public void onDisable() {
        if (instances != null) instances.shutdownAll();
        if (npcs != null) npcs.shutdown();
        if (stats != null) stats.flush();
        if (economy != null) economy.flush();
        if (levels != null) levels.flush();
        if (quests != null) quests.flush();
        if (achievements != null) achievements.flush();
    }

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
    public ArenaSetupManager setup() { return setup; }
    public EconomyService economy() { return economy; }
    public CosmeticsService cosmetics() { return cosmetics; }
    public TokenShopMenu tokenShop() { return tokenShop; }
    public ShapeService shapes() { return shapes; }
    public PrefsService prefs() { return prefs; }
    public LevelService levels() { return levels; }
    public QuestService quests() { return quests; }
    public AchievementService achievements() { return achievements; }
    public TriviaService trivia() { return trivia; }
    public LeaderboardService leaderboards() { return leaderboards; }
    public NpcService npcs() { return npcs; }
    public MenuFx menuFx() { return menuFx; }
}
