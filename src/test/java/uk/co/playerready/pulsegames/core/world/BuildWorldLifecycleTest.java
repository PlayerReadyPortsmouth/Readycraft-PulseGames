package uk.co.playerready.pulsegames.core.world;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.InstanceManager;
import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import static org.mockito.Mockito.*;

class BuildWorldLifecycleTest {
    PulseGamesPlugin plugin; World lobby, build; BuildWorldLifecycle lifecycle;
    YamlConfiguration config; MockedStatic<Bukkit> bukkit;
    @BeforeEach void setup() {
        plugin=mock(PulseGamesPlugin.class); config=new YamlConfiguration(); config.set("lobby.world","lobby");
        when(plugin.getConfig()).thenReturn(config);when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.instances()).thenReturn(mock(InstanceManager.class));
        lobby=world("lobby");build=world("rc_build_courtyard");
        bukkit=mockStatic(Bukkit.class);bukkit.when(Bukkit::getWorlds).thenReturn(List.of(lobby,build));
        bukkit.when(()->Bukkit.unloadWorld(build,true)).thenReturn(true);
        lifecycle=new BuildWorldLifecycle(plugin);
    }
    World world(String name) { World w=mock(World.class);when(w.getName()).thenReturn(name);when(w.getUID()).thenReturn(UUID.randomUUID());when(w.getWorldFolder()).thenReturn(new File("/tmp/nonexistent-"+UUID.randomUUID()));return w; }
    @AfterEach void close(){bukkit.close();}
    @Test void emptyEditingWorldIsSavedAndUnloadedAfterGracePeriod() {
        lifecycle.tick(1000);lifecycle.tick(15999);bukkit.verify(()->Bukkit.unloadWorld(build,true),never());
        lifecycle.tick(16000);bukkit.verify(()->Bukkit.unloadWorld(build,true));bukkit.verify(()->Bukkit.unloadWorld(lobby,true),never());
    }
    @Test void returningPlayerResetsIdleClock() {
        lifecycle.tick(1000);when(build.getPlayers()).thenReturn(List.of(mock(Player.class)));lifecycle.tick(16000);
        when(build.getPlayers()).thenReturn(List.of());lifecycle.tick(17000);lifecycle.tick(31999);
        bukkit.verify(()->Bukkit.unloadWorld(build,true),never());lifecycle.tick(32000);bukkit.verify(()->Bukkit.unloadWorld(build,true));
    }
    @Test void captureKeepsWorldLoadedUntilAllCopiesFinish() {
        lifecycle.retain(build);lifecycle.retain(build);lifecycle.tick(1000);lifecycle.release(build);lifecycle.tick(20000);
        bukkit.verify(()->Bukkit.unloadWorld(build,true),never());lifecycle.release(build);lifecycle.tick(21000);lifecycle.tick(36000);
        bukkit.verify(()->Bukkit.unloadWorld(build,true));
    }
    @Test void cancellationRetriesWithoutDeletingOrSavingWithoutUnload() {
        bukkit.when(()->Bukkit.unloadWorld(build,true)).thenReturn(false);lifecycle.tick(1000);lifecycle.tick(16000);lifecycle.tick(17000);
        bukkit.verify(()->Bukkit.unloadWorld(build,true),times(1));lifecycle.tick(31000);bukkit.verify(()->Bukkit.unloadWorld(build,true),times(2));
    }
    @Test void keepLoadedAndDisabledSettingAreRespected() {
        config.set("build-worlds.keep-loaded",List.of(build.getName()));lifecycle.tick(1000);lifecycle.tick(20000);
        config.set("build-worlds.keep-loaded",List.of());config.set("build-worlds.idle-unload-seconds",0);lifecycle.tick(40000);
        bukkit.verify(()->Bukkit.unloadWorld(build,true),never());
    }
    @Test void unrelatedWorldIsNotManagedButExplicitlyOpenedWorldIs() {
        when(build.getName()).thenReturn("other_world");lifecycle.tick(1000);lifecycle.tick(20000);
        bukkit.verify(()->Bukkit.unloadWorld(build,true),never());lifecycle.manage(build);lifecycle.tick(21000);lifecycle.tick(36000);
        bukkit.verify(()->Bukkit.unloadWorld(build,true));
    }
    @Test void configuredLobbyIsNeverUnloadedEvenWithBuildName() {
        config.set("lobby.world",build.getName());lifecycle.tick(1000);lifecycle.tick(20000);
        bukkit.verify(()->Bukkit.unloadWorld(build,true),never());
    }
}
