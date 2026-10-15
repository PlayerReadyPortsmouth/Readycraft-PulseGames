package uk.co.playerready.pulsegames.core.setup;

import org.bukkit.World;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Adapts saved map definitions into the canonical setup validator without opening or changing a world. */
public final class SavedArenaValidation {
    private SavedArenaValidation() {}
    public enum Status { RECORDED, MISSING, OPTIONAL, UNVERIFIED }
    public record Check(String key, String label, Status status, String detail) {}
    public record Report(String gameId, String arenaId, List<Check> checklist, ArenaValidation.Report validation) {
        public Report { checklist = List.copyOf(checklist); }
        /** Configuration only. This never asserts visual approval, successful navigation or client acceptance. */
        public boolean configurationValid() { return validation.valid(); }
    }

    public static Report check(GameType type, Arena arena, World loadedTemplateOrNull) {
        Objects.requireNonNull(type, "game type");
        Objects.requireNonNull(arena, "arena");
        if (!type.id().equals(arena.gameId())) throw new IllegalArgumentException("Arena belongs to a different game");
        var session = new ArenaSetupManager.Session(type, arena.id(), loadedTemplateOrNull);
        session.displayName = arena.displayName();
        session.minPlayers = arena.minPlayers();
        session.maxPlayers = arena.maxPlayers();
        session.modes = arena.modes();
        session.lobby = arena.rawLobby();
        session.spectator = arena.rawSpectator();
        session.spawns.addAll(arena.rawSpawns());
        session.regions.putAll(arena.regions());
        session.settings.putAll(arena.settings().getValues(true));
        var validation = ArenaValidation.checkConfiguration(session);
        List<Check> checks = new ArrayList<>();
        checks.add(new Check("lobby", "Waiting lobby", present(arena.rawLobby()) ? Status.RECORDED : Status.MISSING,
                "A recorded coordinate still requires physical clearance checks."));
        checks.add(new Check("spectator", "Spectator location", present(arena.rawSpectator()) ? Status.RECORDED : Status.MISSING,
                "Spectator location is checked by the same setup rules."));
        for (SetupRequirement requirement : type.setup()) {
            int count = switch (requirement.kind()) {
                case SPAWNS -> arena.spawnCount();
                case REGION -> arena.regions().containsKey(requirement.key()) ? 1 : 0;
                case REGION_PREFIX -> (int) arena.regions().keySet().stream()
                        .filter(key -> key.startsWith(requirement.key()) && key.substring(requirement.key().length()).matches("[1-9][0-9]*")).count();
                case LOCATION_SETTING -> present(arena.settings().getString(requirement.key())) ? 1 : 0;
                case VALUE_SETTING -> arena.settings().contains(requirement.key()) ? 1 : 0;
            };
            boolean recorded = count >= requirement.min();
            checks.add(new Check(requirement.key(), requirement.description(), recorded ? Status.RECORDED
                    : requirement.required() ? Status.MISSING : Status.OPTIONAL,
                    count + " recorded; " + requirement.min() + (requirement.required() ? " required" : " optional")
                            + ". Canonical validation below checks numbering, values and mode compatibility."));
        }
        for (var mode : type.modes()) {
            if (!arena.supportsMode(mode.id())) continue;
            String capacity = arena.minPlayers(mode.id()) + "–" + arena.maxPlayers(mode.id()) + " configured players";
            if (mode.isTeams()) capacity += "; " + arena.teamCount(mode.id()) + " teams of " + mode.teamSize()
                    + "; " + arena.spawnCount() + " recorded team spawns";
            checks.add(new Check("mode:" + mode.id(), mode.displayName(), Status.RECORDED,
                    capacity + ". Canonical validation checks required bases/objectives and allowed limits."));
        }
        Set<String> hints = Set.of("bot-patrol-points", "bot-hide-points", "bot-recovery-points");
        boolean anyHints = false;
        for (String key : arena.settings().getKeys(true).stream().sorted().toList()) {
            if (!hints.contains(key) && !key.startsWith("bot-route-")) continue;
            anyHints = true;
            Object raw = arena.settings().get(key);
            checks.add(new Check(key, "Bot navigation hint: " + key, Status.RECORDED,
                    raw instanceof List<?> points ? points.size() + " recorded waypoints; traversability is not proven."
                            : "Recorded value is not a location list; see validation errors."));
        }
        if (type.id().equals("karts")) {
            String routeError=uk.co.playerready.pulsegames.games.karts.KartBotRoute.validationError(arena);
            checks.add(new Check("kart-bot-route", "Kart driving line", routeError==null?Status.RECORDED:Status.MISSING,
                    routeError==null?"Ordered route and recovery points recorded; physical driving still needs a playtest.":routeError));
            anyHints=true;
        }
        if (!anyHints) checks.add(new Check("bot-hints", "Bot navigation hints", Status.OPTIONAL,
                "No authored route, patrol, hiding or recovery points. Bots use their existing fallback behaviour."));
        checks.add(new Check("physical", "Physical clearance", Status.UNVERIFIED, loadedTemplateOrNull == null
                ? "Template not loaded. Coordinate syntax is checked; world height, border and floor/head clearance are unknown."
                : "Only already-loaded chunks were inspected; review validation warnings. No chunks were loaded by this check."));
        checks.add(new Check("runtime-paths", "Live bot navigation and objectives", Status.UNVERIFIED,
                "Run an isolated practice round to check routes, bridges, hazards, role balance and match completion."));
        checks.add(new Check("visual", "Visual map review", Status.UNVERIFIED,
                "A person must inspect and approve the finished build."));
        checks.add(new Check("java", "Java client playtest", Status.UNVERIFIED,
                "Human playtesting must confirm gameplay, presentation and interactions."));
        checks.add(new Check("bedrock", "Bedrock client playtest", Status.UNVERIFIED,
                "Human playtesting must confirm gameplay, presentation and interactions."));
        return new Report(type.id(), arena.id(), checks, validation);
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
