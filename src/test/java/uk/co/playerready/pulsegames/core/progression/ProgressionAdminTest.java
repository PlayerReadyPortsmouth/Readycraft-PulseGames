package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.economy.Cosmetic;
import uk.co.playerready.pulsegames.core.economy.CosmeticCatalogue;
import uk.co.playerready.pulsegames.core.economy.CosmeticsService;

import java.io.InputStreamReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProgressionAdminTest {
    private static final UUID ID = UUID.randomUUID();

    private static List<String> shippedStyleIds() {
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                ProgressionAdminTest.class.getResourceAsStream("/cosmetics.yml")));
        var parsed = CosmeticCatalogue.parse(yaml.getConfigurationSection("cosmetics"),
                CosmeticsService.BUILTIN.stream().map(Cosmetic::id).collect(java.util.stream.Collectors.toSet()));
        return parsed.items().stream().map(Cosmetic::nameStyleId).filter(Objects::nonNull).toList();
    }

    private static final class Fake implements ProgressionAdmin.Backend {
        final List<String> styles = shippedStyleIds();
        final Set<String> held = new HashSet<>();
        final List<String> log = new ArrayList<>();
        long xp;
        @Override public long xp(UUID p) { return xp; }
        @Override public void setXp(UUID p, long v) { xp = v; }
        @Override public List<String> unlock(UUID p, int level) { log.add("unlock " + level); return List.of("Red Name (name style)"); }
        @Override public void refresh(UUID p) { log.add("refresh"); }
        @Override public int clearUnlocks(UUID p) { log.add("clear"); return 4; }
        @Override public int unlockedCount(UUID p) { return 7; }
        @Override public List<String> styleIds() { return styles; }
        @Override public boolean holds(UUID p, String id) { return held.contains(id); }
        @Override public boolean grant(UUID p, String id) { held.add(id); return true; }
        @Override public boolean revoke(UUID p, String id) { held.remove(id); return true; }
    }

    private final Fake fake = new Fake();
    private final List<String> audit = new ArrayList<>();
    private final ProgressionAdmin admin = new ProgressionAdmin(fake, audit::add);

    private List<String> run(String... args) {
        var parsed = ProgressionAdmin.parse(args);
        assertNull(parsed.error(), parsed.error());
        return admin.run("Aurora", ID, "Kid", parsed);
    }

    @Test void levelSetUsesTheExactThresholdAndRunsTheUnlockPathOnce() {
        run("level", "Kid", "set", "10");
        assertEquals(XpRules.xpForLevel(10), fake.xp);
        assertEquals(10, XpRules.levelFor(fake.xp));
        assertEquals(List.of("unlock 10", "refresh"), fake.log);
        assertEquals(List.of("PROGRESSION Aurora level-set Kid 10"), audit);
    }

    @Test void xpAddUnlocksOnlyWhenALevelIsCrossed() {
        run("xp", "Kid", "add", "50");
        assertEquals(List.of("refresh"), fake.log);
        run("xp", "Kid", "add", "60");
        assertEquals(110, fake.xp);
        assertEquals(List.of("refresh", "unlock 2", "refresh"), fake.log);
    }

    @Test void resetClearsXpAndUnlocksAndSaysLuckPermsIsUntouched() {
        fake.xp = 999;
        var out = run("level", "Kid", "reset");
        assertEquals(0, fake.xp);
        assertTrue(fake.log.contains("clear"));
        assertTrue(String.join(" ", out).contains("LuckPerms"));
    }

    @Test void parsingRejectsBadInput() {
        assertNotNull(ProgressionAdmin.parse(new String[]{"xp", "Kid", "add", "-1"}).error());
        assertNotNull(ProgressionAdmin.parse(new String[]{"level", "Kid", "set", "101"}).error());
        assertNotNull(ProgressionAdmin.parse(new String[]{"level", "Kid", "set", "0"}).error());
        assertNotNull(ProgressionAdmin.parse(new String[]{"xp", "Kid", "give", "5"}).error());
        assertNotNull(ProgressionAdmin.parse(new String[]{"namestyle", "Kid"}).error());
        assertEquals(ProgressionAdmin.Verb.XP_SET, ProgressionAdmin.parse(new String[]{"XP", "Kid", "SET", "5"}).verb());
        assertEquals(ProgressionAdmin.Verb.SHOW, ProgressionAdmin.parse(new String[]{"progression", "Kid"}).verb());
    }

    @Test void grantAndRevokeAllExpandToSixteenIds() {
        assertEquals(16, fake.styles.size());
        run("namestyle", "Kid", "grant", "all");
        assertEquals(16, fake.held.size());
        run("namestyle", "Kid", "revoke", "all");
        assertTrue(fake.held.isEmpty());
        assertEquals(List.of("PROGRESSION Aurora namestyle-grant Kid all", "PROGRESSION Aurora namestyle-revoke Kid all"), audit);
        assertTrue(run("namestyle", "Kid", "grant", "nonsense").get(0).contains("Unknown"));
    }

    @Test void progressionShowsHeldStyles() {
        fake.xp = XpRules.xpForLevel(3);
        fake.held.add(fake.styles.get(0));
        var out = run("progression", "Kid");
        assertTrue(out.get(0).contains("level 3") && out.get(0).contains("7 cosmetics"));
        assertTrue(out.get(1).contains("(1/16)"));
    }
}
