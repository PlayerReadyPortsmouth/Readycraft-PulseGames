package uk.co.playerready.pulsegames.games;

import java.util.Map;

/** One-line length/difficulty tag per game for the game menu (rough guides, not measured). */
public final class GameTags {
    private GameTags() { }

    private static final Map<String, String> TAGS = Map.ofEntries(
            Map.entry("quake", "5 min, medium"),
            Map.entry("dodgeball", "3-4 min, easy"),
            Map.entry("dropper", "2-3 min, easy"),
            Map.entry("spleef", "2-5 min, easy"),
            Map.entry("tntrun", "2-5 min, easy"),
            Map.entry("blockparty", "5-8 min, easy"),
            Map.entry("avalanche", "3 min, medium"),
            Map.entry("volcano", "3-5 min, medium"),
            Map.entry("koth", "4-6 min, medium"),
            Map.entry("duels", "3 min, hard"),
            Map.entry("kitpvp", "5 min, medium"),
            Map.entry("parkour", "3-5 min, medium"),
            Map.entry("elytra", "3 min, medium"),
            Map.entry("karts", "4 min, easy"),
            Map.entry("skywars", "10-15 min, hard"),
            Map.entry("bedwars", "20-30 min, hard"),
            Map.entry("buildbattle", "8 min, easy"),
            Map.entry("deathrun", "5 min, medium"),
            Map.entry("villagerdefense", "10 min, medium"),
            Map.entry("readyplayerz", "15-40 min, hard"),
            Map.entry("luckypillars", "5-7 min, medium"),
            Map.entry("prophunt", "5 min, easy"),
            Map.entry("supermarket", "3-5 min, easy"),
            Map.entry("partygames", "5-8 min, easy"));

    /** Tag for a game id, or null when none is recorded. */
    public static String of(String gameId) { return TAGS.get(gameId); }
}
