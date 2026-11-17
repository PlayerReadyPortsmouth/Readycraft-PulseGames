package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SafeguardingTest {
    @TempDir Path temporary;
    @Test void failClosedEnvironmentAndTestDefaults() {
        Properties p = new Properties();
        for (String env : List.of("", "production", "TEST", "tes")) {
            p.setProperty("safeguarding.environment", env); p.setProperty("safeguarding.filter", "true");
            var cfg = SafeguardingConfig.parse(p); assertFalse(cfg.applies("minigamestest")); assertFalse(cfg.filter()); assertFalse(cfg.logging());
        }
        p.setProperty("safeguarding.environment", "test");
        var cfg = SafeguardingConfig.parse(p); assertTrue(cfg.filter()); assertTrue(cfg.logging()); assertEquals(90, cfg.retentionDays());
        assertTrue(cfg.applies("minigamestest")); assertFalse(cfg.applies("survival"));
        p.setProperty("safeguarding.test-backends", ""); assertFalse(SafeguardingConfig.parse(p).applies("minigamestest"));
        p.setProperty("safeguarding.retention-days", "0"); assertThrows(IllegalArgumentException.class, () -> SafeguardingConfig.parse(p));
    }
    @Test void malformedInactiveSettingsCannotDisableTheProductionBridge() {
        Properties properties = new Properties();
        properties.setProperty("safeguarding.retention-days", "not-a-number");
        properties.setProperty("safeguarding.test-backends", "invalid backend name!");
        properties.setProperty("safeguarding.discord-webhook-env", "not an environment variable");
        properties.setProperty("safeguarding.readyapp-endpoint", "not a URI");
        properties.setProperty("safeguarding.readyapp-token-env", "invalid token reference");
        properties.setProperty("safeguarding.extra-words", "ignored-while-off");
        for (String environment : List.of("", "production", "TEST", "tes")) {
            if (environment.isEmpty()) properties.remove("safeguarding.environment"); else properties.setProperty("safeguarding.environment", environment);
            var config = assertDoesNotThrow(() -> SafeguardingConfig.parse(properties));
            assertFalse(config.testEnvironment()); assertFalse(config.filter()); assertFalse(config.logging());
            assertFalse(config.applies("minigamestest")); assertTrue(config.backends().isEmpty());
            assertEquals("", config.webhookEnv()); assertEquals("", config.readyAppEndpoint()); assertEquals("", config.readyAppTokenEnv()); assertEquals("", config.extraWords());
        }
    }
    @Test void obfuscatedContactAndAgePatterns() {
        var filter = new SafeguardingFilter("");
        assertEquals(SafeguardingFilter.Severity.URGENT, filter.evaluate("I'm 25 and you're 12").severity());
        assertTrue(filter.evaluate("07700.900.123").reasons().contains("phone"));
        assertTrue(filter.evaluate("my d1sc0rd is blue").reasons().contains("off-platform-contact"));
    }
    @Test void syntheticCalibration() {
        var filter = new SafeguardingFilter("");
        // Independent expected labels. Known misses are retained and reported, not silently removed.
        var harmful = List.of("fuck", "f u c k", "f.u.c.k", "fuuuck", "sh1t", "sh!t", "b1tch", "c u n t", "n1gger", "f4gg0t",
                "my email is test@example.org", "test at example dot com", "call 07700 900123", "+44 (0)7700-900123", "12 Example Road", "my address is secret",
                "PO1 1AA", "@my_social", "add me on discord", "my snapchat is blue", "move to whatsapp", "kill myself", "I want to die", "I will stab you",
                "keep this a secret", "don't tell your parents", "send me nudes", "I am 25 and you are 12", "the digits are zero seven seven zero zero nine zero zero one two three",
                "send that p1c privately");
        var benign = List.of("hello friends", "let's build a castle", "class starts soon", "Scunthorpe is a town", "assess the design", "grass blocks", "a spruce street lamp",
                "coordinates x 123 y 64 z 789", "1234567890 is a seed", "the boss will kill me", "my sword has sharpness", "our discordant bells", "I live at spawn",
                "I am 25 and the score is 12", "I don't want my character to die", "this road has 12 lamps", "email staff if you need help", "my social studies class", "I have 3 diamonds", "great teamwork");
        long misses = harmful.stream().filter(t -> !filter.evaluate(t).blocked()).count();
        long falsePositives = benign.stream().filter(t -> filter.evaluate(t).blocked()).count();
        System.out.println("RC3 proxy corpus: harmful=" + harmful.size() + ", benign=" + benign.size() + ", FN=" + misses + ", FP=" + falsePositives);
        assertEquals(0, misses, "All harmful cases in this bounded synthetic corpus should be caught");
        assertEquals(2, falsePositives, "Known conservative triage: ten-digit seed and 'I live at spawn'");
        for (String urgent : List.of("I will kill myself", "I will stab you", "don't tell your parents", "I am 25 and you are 12"))
            assertEquals(SafeguardingFilter.Severity.URGENT, filter.evaluate(urgent).severity());
    }
    @Test void journalProtectsRetentionAndContextWithoutDuplicatingMessages() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);
        Path directory = temporary.resolve("private"); Files.createDirectories(directory);
        Files.writeString(directory.resolve("2026-08-18.tsv"), "expired");
        Files.writeString(directory.resolve("2026-08-20.tsv"), "retained");
        var journal = new SafeguardingJournal(directory, clock, 90);
        assertFalse(Files.exists(directory.resolve("2026-08-18.tsv"))); assertTrue(Files.exists(directory.resolve("2026-08-20.tsv")));
        UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        journal.record(new SafeguardingJournal.Entry("before", 1, one, "minigamestest", "global", "hello\t\nworld", SafeguardingFilter.Severity.NONE, List.of(), "allowed"));
        journal.record(new SafeguardingJournal.Entry("flag", 2, two, "minigamestest", "global", "flag", SafeguardingFilter.Severity.HIGH, List.of("email"), "blocked"));
        journal.record(new SafeguardingJournal.Entry("private", 3, one, "minigamestest", "party:other", "private", SafeguardingFilter.Severity.NONE, List.of(), "allowed"));
        journal.record(new SafeguardingJournal.Entry("after", 4, one, "minigamestest", "global", "help", SafeguardingFilter.Severity.NONE, List.of(), "allowed"));
        assertEquals(3, journal.context("flag").size()); assertEquals(1, journal.read(null, true, 20).size()); assertEquals(3, journal.read(one, false, 20).size());
        assertTrue(journal.read(one, false, 20).getFirst().contains("hello\\t\\nworld"));
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(directory.resolve("2026-11-17.tsv")));
    }
}
