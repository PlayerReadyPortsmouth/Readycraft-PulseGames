package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Everything placed in the lobby, as saved in lobby.yml. Pure data: no world or plugin needed. */
public final class LobbyData {
    public record Npc(String id, String game, String mode, LobbySpot spot) { }

    /** {@code kind} is weekly, alltime or parkour; {@code game} is null for the whole network. */
    public record Board(String id, String kind, String game, String stat, LobbySpot spot) { }

    private final List<Npc> npcs = new ArrayList<>();
    private final List<Board> boards = new ArrayList<>();
    private final List<LobbySpot> checkpoints = new ArrayList<>();
    private final Map<UUID, LocalDate> claims = new HashMap<>();
    private LobbySpot chest, parkourStart, parkourEnd;
    private int nextId = 1;

    public List<Npc> npcs() { return List.copyOf(npcs); }
    public List<Board> boards() { return List.copyOf(boards); }
    public List<LobbySpot> checkpoints() { return List.copyOf(checkpoints); }
    public LobbySpot chest() { return chest; }
    public LobbySpot parkourStart() { return parkourStart; }
    public LobbySpot parkourEnd() { return parkourEnd; }

    public Npc addNpc(String game, String mode, LobbySpot spot) {
        Npc npc = new Npc("n" + nextId++, game, mode, spot);
        npcs.add(npc);
        return npc;
    }

    public Board addBoard(String kind, String game, String stat, LobbySpot spot) {
        Board board = new Board("b" + nextId++, kind, game, stat, spot);
        boards.add(board);
        return board;
    }

    public Optional<Npc> nearestNpc(LobbySpot from, double max) { return nearest(npcs, Npc::spot, from, max); }
    public Optional<Board> nearestBoard(LobbySpot from, double max) { return nearest(boards, Board::spot, from, max); }
    public boolean removeNpc(Npc npc) { return npcs.remove(npc); }
    public boolean removeBoard(Board board) { return boards.remove(board); }

    static <T> Optional<T> nearest(List<T> items, Function<T, LobbySpot> spot, LobbySpot from, double max) {
        T best = null;
        double bestSq = max * max;
        for (T item : items) {
            double d = spot.apply(item).distanceSq(from);
            if (d <= bestSq) { best = item; bestSq = d; }
        }
        return Optional.ofNullable(best);
    }

    public void setChest(LobbySpot spot) { chest = spot; }
    public void setParkourStart(LobbySpot spot) { parkourStart = spot; }
    public void setParkourEnd(LobbySpot spot) { parkourEnd = spot; }
    public void addCheckpoint(LobbySpot spot) { checkpoints.add(spot); }

    public void resetParkour() {
        parkourStart = null;
        parkourEnd = null;
        checkpoints.clear();
        boards.removeIf(b -> b.kind().equals("parkour"));
    }

    // ---- daily chest claims --------------------------------------------------------

    public LocalDate claimedOn(UUID player) { return claims.get(player); }
    public void markClaimed(UUID player, LocalDate day) { claims.put(player, day); }

    // ---- lobby.yml -----------------------------------------------------------------

    /** Drops claims from earlier days first: only today's claim can block anyone. */
    public YamlConfiguration toYaml(LocalDate today) {
        claims.values().removeIf(day -> day.isBefore(today));
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-id", nextId);
        for (Npc n : npcs) {
            y.set("npcs." + n.id() + ".game", n.game());
            if (n.mode() != null) y.set("npcs." + n.id() + ".mode", n.mode());
            y.set("npcs." + n.id() + ".at", n.spot().serialise());
        }
        for (Board b : boards) {
            y.set("boards." + b.id() + ".kind", b.kind());
            if (b.game() != null) y.set("boards." + b.id() + ".game", b.game());
            y.set("boards." + b.id() + ".stat", b.stat());
            y.set("boards." + b.id() + ".at", b.spot().serialise());
        }
        if (chest != null) y.set("chest", chest.serialise());
        if (parkourStart != null) y.set("parkour.start", parkourStart.serialise());
        if (parkourEnd != null) y.set("parkour.end", parkourEnd.serialise());
        y.set("parkour.checkpoints", checkpoints.stream().map(LobbySpot::serialise).toList());
        claims.forEach((id, day) -> y.set("claims." + id, day.toString()));
        return y;
    }

    /** Unreadable entries are skipped rather than failing the whole file. */
    public static LobbyData fromYaml(ConfigurationSection y) {
        LobbyData d = new LobbyData();
        d.nextId = Math.max(1, y.getInt("next-id", 1));
        ConfigurationSection npcs = y.getConfigurationSection("npcs");
        if (npcs != null) for (String id : npcs.getKeys(false)) {
            ConfigurationSection n = npcs.getConfigurationSection(id);
            LobbySpot at = spot(n == null ? null : n.getString("at"));
            if (at != null && n.getString("game") != null) d.npcs.add(new Npc(id, n.getString("game"), n.getString("mode"), at));
        }
        ConfigurationSection boards = y.getConfigurationSection("boards");
        if (boards != null) for (String id : boards.getKeys(false)) {
            ConfigurationSection b = boards.getConfigurationSection(id);
            LobbySpot at = spot(b == null ? null : b.getString("at"));
            if (at != null && b.getString("kind") != null)
                d.boards.add(new Board(id, b.getString("kind"), b.getString("game"), b.getString("stat", "wins"), at));
        }
        d.chest = spot(y.getString("chest"));
        d.parkourStart = spot(y.getString("parkour.start"));
        d.parkourEnd = spot(y.getString("parkour.end"));
        for (String raw : y.getStringList("parkour.checkpoints")) {
            LobbySpot s = spot(raw);
            if (s != null) d.checkpoints.add(s);
        }
        ConfigurationSection claims = y.getConfigurationSection("claims");
        if (claims != null) for (String id : claims.getKeys(false)) {
            try { d.claims.put(UUID.fromString(id), LocalDate.parse(claims.getString(id, ""))); }
            catch (IllegalArgumentException | java.time.format.DateTimeParseException skip) { }
        }
        return d;
    }

    private static LobbySpot spot(String raw) {
        if (raw == null) return null;
        try { return LobbySpot.parse(raw); } catch (IllegalArgumentException bad) { return null; }
    }
}
