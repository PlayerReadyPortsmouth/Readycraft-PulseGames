package uk.co.playerready.pulsegames.core.command;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CommandStandardTest {
    private static YamlConfiguration pluginYml() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                CommandStandardTest.class.getResourceAsStream("/plugin.yml")));
    }

    private static Set<String> registered() {
        ConfigurationSection commands = pluginYml().getConfigurationSection("commands");
        Set<String> names = new HashSet<>();
        for (String name : commands.getKeys(false)) {
            names.add(name);
            names.addAll(commands.getStringList(name + ".aliases"));
        }
        return names;
    }

    /** Owned by PulseNetwork (network-helper), dispatched here by name. */
    private static final Set<String> EXTERNAL = Set.of("namestyle");

    @Test void everyDispatchedStringResolvesToARegisteredCommand() {
        Set<String> names = registered();
        for (String dispatched : Routes.all()) {
            String[] words = dispatched.replaceFirst("^/", "").split(" +");
            assertTrue(names.contains(words[0]) || EXTERNAL.contains(words[0]), dispatched + " is not a registered command or alias");
            if (words[0].equals(Routes.ADMIN_ROUTE)) {
                assertTrue(Set.of(Routes.AREA_GAMES, Routes.AREA_SERVER).contains(words[1]), dispatched);
                assertTrue(AdminRoutes.VERBS.contains(words[2]), dispatched + " is not a route verb");
            }
            if (words[0].equals("party") && words.length > 1) {
                String usage = pluginYml().getString("commands.party.usage");
                assertTrue(usage.contains(words[1]) || words[1].equals("invite"), dispatched);
            }
        }
    }

    @Test void routeCommandIsDeclaredAndGated() {
        assertEquals("pulsegames.admin", pluginYml().getString("commands.admin-route.permission"));
        assertEquals("pulsegames.admin", pluginYml().getString("commands.pulse.permission"));
    }

    @Test void adminNodeTreeIsDeclared() {
        String yml = readResource();
        for (String node : List.of("games", "matches", "players", "events", "bots", "server", "maps", "setup")) {
            assertTrue(yml.contains("pulsegames.admin." + node + ":"), node);
        }
        assertTrue(yml.contains("pulsegames.maps.approve:"));
    }

    private static String readResource() {
        try (var in = CommandStandardTest.class.getResourceAsStream("/plugin.yml")) {
            return new String(in.readAllBytes());
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    @Test void lobbyNoLongerAnswersToLeave() {
        List<String> aliases = pluginYml().getStringList("commands.lobby.aliases");
        assertFalse(aliases.contains("leave"));
        assertTrue(aliases.contains("hub"));
    }

    @Test void legacyPulseVerbsMapToStandardNames() {
        assertArrayEquals(new String[]{"types"}, AdminRoutes.fromLegacy(new String[]{"games"}));
        assertArrayEquals(new String[]{"matches", "x"}, AdminRoutes.fromLegacy(new String[]{"LIST", "x"}));
        assertArrayEquals(new String[]{"setup", "start"}, AdminRoutes.fromLegacy(new String[]{"setup", "start"}));
        assertArrayEquals(new String[0], AdminRoutes.fromLegacy(new String[0]));
    }

    @Test void consoleVerbsAreTheOnesThatDoNotNeedAPlayer() {
        assertTrue(AdminRoutes.consoleVerb(new String[]{"maps", "board"}));
        assertTrue(AdminRoutes.consoleVerb(new String[]{"build", "bp"}));
        assertFalse(AdminRoutes.consoleVerb(new String[]{"setup", "start"}));
        assertFalse(AdminRoutes.consoleVerb(new String[0]));
    }

    private static final Pattern DISPATCH = Pattern.compile(
            "(performCommand|dispatchCommand|runCommand)\\([^;]*\"|run_command:'?/");

    @Test void noCommandIsDispatchedOutsideRoutes() throws IOException {
        Path root = Path.of("src/main/java");
        List<String> offenders;
        try (Stream<Path> files = Files.walk(root)) {
            offenders = files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.getFileName().toString().equals("Routes.java"))
                    .filter(f -> {
                        try { return DISPATCH.matcher(Files.readString(f)).find(); }
                        catch (IOException e) { throw new RuntimeException(e); }
                    })
                    .map(Path::toString).collect(Collectors.toList());
        }
        assertEquals(List.of(), offenders, "dispatch command strings through Routes");
    }

    @Test void noStalePulseUsageTextRemains() throws IOException {
        Path root = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (f.getFileName().toString().equals("Commands.java")) continue;
                String text = Files.readString(f);
                assertFalse(text.toLowerCase(Locale.ROOT).contains("/pulse "), f + " still shows /pulse");
            }
        }
    }
}
