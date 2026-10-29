package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Separate, bounded practice calibration; never writes ranked player statistics. */
public final class AdaptiveBotSkillStore {
    private static final int WINDOW = 40, PRIOR = 8, MIN_SAMPLES = 3;
    private record Outcome(boolean won, double expected) {}
    private final File file;
    private final Logger logger;
    private final Map<UUID, Map<String, ArrayDeque<Outcome>>> history = new HashMap<>();

    public AdaptiveBotSkillStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    /** Explainable per-player/mode decision. Practice scenarios never contribute to these samples. */
    public record Calibration(BotDifficulty difficulty, int samples, int wins, double expectedWins,
                              double residual, String reason) {}

    public synchronized BotDifficulty difficulty(UUID player, String mode) {
        return calibration(player, mode).difficulty();
    }

    /** Compare outcomes with the fair chance for each round, not an absolute 50% win rate. */
    public synchronized Calibration calibration(UUID player, String mode) {
        var modes = history.get(player);
        var outcomes = modes == null ? null : modes.get(mode);
        int samples = outcomes == null ? 0 : outcomes.size();
        int wins = outcomes == null ? 0 : (int) outcomes.stream().filter(Outcome::won).count();
        double expected = outcomes == null ? 0 : outcomes.stream().mapToDouble(Outcome::expected).sum();
        double residual = outcomes == null ? 0 : outcomes.stream().mapToDouble(o -> ((o.won ? 1.0 : 0.0) - o.expected)
                        / Math.max(0.1, Math.sqrt(o.expected * (1 - o.expected)))).sum() / (samples + PRIOR);
        // A child's first rounds in a mode get the gentlest bots; three results are enough to place them.
        if (samples < MIN_SAMPLES) return new Calibration(BotDifficulty.EASY, samples, wins, expected, residual,
                "Cold start: " + samples + "/" + MIN_SAMPLES + " eligible rounds; gentle opponents until there is a result to go on.");
        BotDifficulty difficulty = residual <= -0.25 ? BotDifficulty.EASY : residual >= 0.25 ? BotDifficulty.HARD : BotDifficulty.NORMAL;
        String reason = switch (difficulty) {
            case EASY -> "Results below expected chance: allow more reaction time and less accurate attacks.";
            case HARD -> "Results above expected chance: faster reactions and more accurate attacks.";
            case NORMAL -> "Results near expected chance: retain standard reactions and accuracy.";
        };
        return new Calibration(difficulty, samples, wins, expected, residual,
                reason + " Last " + samples + " rounds (maximum " + WINDOW + "); eight-round neutral prior. Normal combat rules remain unchanged.");
    }

    public synchronized void record(UUID player, String mode, boolean won, double expected) {
        if (player == null || !validMode(mode) || !Double.isFinite(expected) || expected <= 0 || expected > 1) return;
        var outcomes = history.computeIfAbsent(player, ignored -> new HashMap<>())
                .computeIfAbsent(mode, ignored -> new ArrayDeque<>());
        outcomes.addLast(new Outcome(won, expected));
        while (outcomes.size() > WINDOW) outcomes.removeFirst();
    }

    private static boolean validMode(String mode) {
        return mode != null && mode.matches("[a-z0-9_-]+:[a-z0-9_-]+") && mode.length() <= 128;
    }

    private void load() {
        if (!file.isFile()) return;
        var yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            if (yaml.getInt("version", 1) != 1) {
                logger.warning("Unsupported bot skill history version; using cold-start difficulty");
                return;
            }
            var players = yaml.getConfigurationSection("players");
            if (players == null) return;
            for (String id : players.getKeys(false)) {
                UUID player;
                try { player = UUID.fromString(id); }
                catch (IllegalArgumentException invalid) { continue; }
                var modes = players.getConfigurationSection(id);
                if (modes == null) continue;
                for (String mode : modes.getKeys(false)) {
                    if (!validMode(mode)) continue;
                    for (Map<?, ?> value : modes.getMapList(mode)) {
                        if (!(value.get("won") instanceof Boolean won) || !(value.get("expected") instanceof Number expected)) continue;
                        record(player, mode, won, expected.doubleValue());
                    }
                }
            }
        } catch (Exception failure) {
            logger.log(Level.WARNING, "Could not load bot skill history; using cold-start difficulty", failure);
        }
    }

    /** Serialised with recording and other saves; replace only a fully written file. */
    public synchronized void save() {
        var yaml = new YamlConfiguration();
        yaml.set("version", 1);
        history.forEach((player, modes) -> modes.forEach((mode, outcomes) -> yaml.set("players." + player + "." + mode,
                outcomes.stream().map(o -> Map.of("won", o.won, "expected", o.expected)).toList())));
        var destination = file.toPath().toAbsolutePath();
        java.nio.file.Path temporary = null;
        try {
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), "bot-skill-", ".tmp");
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unavailable) {
            // Preserve the previous good file rather than risk a partial non-atomic replacement.
            logger.log(Level.WARNING, "Bot skill history requires atomic file replacement; previous file preserved", unavailable);
        } catch (IOException failure) {
            logger.log(Level.WARNING, "Could not save bot skill history; previous file preserved", failure);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException failure) { logger.log(Level.FINE, "Could not remove bot skill temporary file", failure); }
        }
    }
}
