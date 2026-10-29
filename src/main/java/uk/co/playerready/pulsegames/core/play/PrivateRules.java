package uk.co.playerready.pulsegames.core.play;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/** Pure rules for private matches (no Bukkit types, so they are unit tested). */
public final class PrivateRules {
    private PrivateRules() { }

    public static final String TOKEN_NOTE = "Private matches pay tokens only when most players are people.";

    /** Only the party's own players get in; bots are added by the server, never by a player. */
    public static boolean admits(Set<UUID> allowed, UUID id, boolean bot) {
        return bot || allowed.contains(id);
    }

    /**
     * Players needed to start. With bots to fill the empty seats one human is enough; a game that cannot
     * run with bots needs two humans. Never above what the mode itself asks for.
     */
    public static int minPlayers(int normalMinimum, boolean botsUsable) {
        return Math.min(Math.max(1, normalMinimum), botsUsable ? 1 : 2);
    }

    /** The map's {@code bots-enabled} is respected unless the leader explicitly switched bots on. */
    public static boolean mapAllowsBots(boolean mapOptedIn, boolean explicit) { return mapOptedIn || explicit; }

    public static boolean botsWanted(boolean allowBotsConfig, boolean fillBots) { return allowBotsConfig && fillBots; }

    public enum Status { PICKED, BUSY, NONE }

    /** One candidate arena: its id, and the world template it plays on (arenas sharing a template are one map). */
    public record MapOption(String arenaId, String template) { }

    public record ArenaPick(Status status, String arenaId) {
        static ArenaPick picked(String id) { return new ArenaPick(Status.PICKED, id); }
        static final ArenaPick BUSY = new ArenaPick(Status.BUSY, null);
        static final ArenaPick NONE = new ArenaPick(Status.NONE, null);
    }

    /**
     * A free arena of the wanted map. A busy arena is swapped for a free one on the same template; if
     * there is none the leader is told it is busy. {@code wantedId} null means Random, which only
     * ever lands on a free arena.
     */
    public static ArenaPick pickArena(List<MapOption> options, Set<String> busy, String wantedId, IntUnaryOperator random) {
        if (options.isEmpty()) return ArenaPick.NONE;
        if (wantedId == null) {
            List<MapOption> free = options.stream().filter(o -> !busy.contains(o.arenaId())).toList();
            return free.isEmpty() ? ArenaPick.BUSY : ArenaPick.picked(free.get(random.applyAsInt(free.size())).arenaId());
        }
        MapOption wanted = options.stream().filter(o -> o.arenaId().equalsIgnoreCase(wantedId)).findFirst().orElse(null);
        if (wanted == null) return ArenaPick.NONE;
        if (!busy.contains(wanted.arenaId())) return ArenaPick.picked(wanted.arenaId());
        List<MapOption> same = new ArrayList<>();
        for (MapOption o : options) {
            if (!busy.contains(o.arenaId()) && o.template() != null && o.template().equals(wanted.template())) same.add(o);
        }
        return same.isEmpty() ? ArenaPick.BUSY : ArenaPick.picked(same.get(random.applyAsInt(same.size())).arenaId());
    }
}
