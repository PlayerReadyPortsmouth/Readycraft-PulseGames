package uk.co.playerready.pulsegames.core.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class AdaptiveBotSkillStoreTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private final String mode = "kitpvp:oitc";
    private AdaptiveBotSkillStore store() { return new AdaptiveBotSkillStore(directory.resolve("bot-skill.yml").toFile(), Logger.getAnonymousLogger()); }

    @Test void coldStartAndSmallSamplesStayNormal() {
        var store = store();
        assertEquals(BotDifficulty.NORMAL, store.difficulty(player, mode));
        for (int i = 0; i < 5; i++) store.record(player, mode, true, .5);
        assertEquals(BotDifficulty.NORMAL, store.difficulty(player, mode));
        store.record(player, mode, true, .5);
        assertEquals(BotDifficulty.HARD, store.difficulty(player, mode));
        assertEquals(BotDifficulty.NORMAL, store.difficulty(player, "duels:classic"));
        assertEquals(BotDifficulty.NORMAL, store.difficulty(UUID.randomUUID(), mode));
    }

    @Test void fairFfaWinRateRemainsNormalAndPersistentLossesBecomeEasy() {
        var store = store();
        for (int i = 0; i < 40; i++) store.record(player, mode, i % 4 == 0, .25);
        assertEquals(BotDifficulty.NORMAL, store.difficulty(player, mode));
        for (int i = 0; i < 40; i++) store.record(player, mode, false, .5);
        assertEquals(BotDifficulty.EASY, store.difficulty(player, mode));
    }

    @Test void twelvePlayerFfaCanBecomeEasyWhileFairResultsRemainNormal() {
        var store = store();
        for (int i = 0; i < 40; i++) store.record(player, mode, false, 1.0 / 12);
        assertEquals(BotDifficulty.EASY, store.difficulty(player, mode));
        for (int i = 0; i < 40; i++) store.record(player, mode, i % 12 == 11, 1.0 / 12);
        assertEquals(BotDifficulty.NORMAL, store.difficulty(player, mode));
    }

    @Test void latestFortyOutcomesReplaceOldHistoryAndSurviveReload() throws Exception {
        var store = store();
        for (int i = 0; i < 80; i++) store.record(player, mode, false, .5);
        for (int i = 0; i < 40; i++) store.record(player, mode, true, .5);
        store.save();
        assertEquals(BotDifficulty.HARD, store().difficulty(player, mode));
        assertTrue(Files.readString(directory.resolve("bot-skill.yml")).contains("version: 1"));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void invalidRecordsAreIgnored() {
        var store = store();
        for (int i = 0; i < 10; i++) {
            store.record(player, mode, true, Double.NaN);
            store.record(player, mode, true, 0);
            store.record(player, mode, true, 1.1);
            store.record(player, "../invalid", true, .5);
        }
        assertEquals(BotDifficulty.NORMAL, store.difficulty(player, mode));
    }

    @Test void invalidPersistedRowsDoNotBecomeSamples() throws Exception {
        Files.writeString(directory.resolve("bot-skill.yml"), "players:\n  " + player + ":\n    kitpvp:oitc:\n      - won: yesplease\n        expected: 0.5\n      - won: true\n        expected: .NaN\n  invalid-uuid:\n    kitpvp:oitc: []\n");
        assertEquals(BotDifficulty.NORMAL, store().difficulty(player, mode));
    }
}
