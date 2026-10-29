package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotDifficultyProfileTest {
    @Test void legacyAccessorsStillReportTheOriginalFourNumbers() {
        assertEquals(28, BotDifficulty.EASY.meleeDelayTicks());
        assertEquals(45, BotDifficulty.EASY.accuracyPercent());
        assertEquals(20, BotDifficulty.NORMAL.projectileDelayTicks());
        assertEquals(10, BotDifficulty.HARD.pathTicks);
        assertEquals(BotDifficulty.NORMAL, BotDifficulty.parse("garbage"));
    }

    @Test void tiersGetMoreSkilledMonotonically() {
        var e = BotDifficulty.EASY.profile(); var n = BotDifficulty.NORMAL.profile(); var h = BotDifficulty.HARD.profile();
        assertTrue(e.aimErrorDegrees() > n.aimErrorDegrees() && n.aimErrorDegrees() > h.aimErrorDegrees());
        assertTrue(h.aimErrorDegrees() >= 0.5 && e.aimErrorDegrees() <= 6);
        assertTrue(e.strafeBias() < n.strafeBias() && n.strafeBias() < h.strafeBias());
        assertTrue(e.sprintChance() < n.sprintChance() && n.sprintChance() <= h.sprintChance());
        assertEquals(0, e.jumpCritChance());
        assertTrue(n.jumpCritChance() < h.jumpCritChance());
        assertTrue(e.reactionJitter() > h.reactionJitter());
        assertTrue(e.retreatHealth() <= n.retreatHealth());
    }

    @Test void configDefaultsEqualShippedProfilesAndOverridesAreClamped() {
        var defaults = BotCombatSettings.read(new YamlConfiguration());
        assertTrue(defaults.sprint() && defaults.strafe() && defaults.kitUse() && defaults.retreat());
        for (var tier : BotDifficulty.values()) assertEquals(tier.profile(), defaults.profile(tier));
        var yaml = new YamlConfiguration();
        yaml.set("bots.combat.sprint", false);
        yaml.set("bots.combat.tiers.hard.aim-error-degrees", 99);
        yaml.set("bots.combat.tiers.hard.hit-chance", 50);
        yaml.set("bots.combat.tiers.easy.reaction-jitter-ticks", -4);
        var read = BotCombatSettings.read(yaml);
        assertFalse(read.sprint());
        assertEquals(20, read.profile(BotDifficulty.HARD).aimErrorDegrees());
        assertEquals(50, read.profile(BotDifficulty.HARD).hitChance());
        assertEquals(0, read.profile(BotDifficulty.EASY).reactionJitter());
        assertEquals(BotDifficulty.NORMAL.profile(), read.profile(BotDifficulty.NORMAL));
    }

    @Test void shippedConfigYamlParsesToTheSameDefaults() throws Exception {
        var yaml = new YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/config.yml")) { yaml.loadFromString(new String(in.readAllBytes())); }
        var read = BotCombatSettings.read(yaml);
        for (var tier : BotDifficulty.values()) assertEquals(tier.profile(), read.profile(tier), tier.name());
    }
}
