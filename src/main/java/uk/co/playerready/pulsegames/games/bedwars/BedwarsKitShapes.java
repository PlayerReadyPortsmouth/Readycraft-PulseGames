package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure block shapes for the shop's building items: bridge egg path, pop-up tower, shield wall, ice road, honey patch.
 * Nothing here touches the world; BedwarsCustomItems asks {@link #canReplace} and the bed-region test per cell.
 * Author: AurorasChaos (on behalf of Player Ready).
 */
final class BedwarsKitShapes {
    private BedwarsKitShapes() {}

    record Pos(int x, int y, int z) {
        Pos add(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    }
    enum Kind { WOOL, LADDER }
    record Piece(Pos pos, Kind kind) {}

    static final int BRIDGE_MAX_CELLS = 40;
    static final int TOWER_HEIGHT = 5;
    static final int ICE_ROAD_LENGTH = 8;

    /** Only empty space is ever built into: beds, chests, the map and other players' blocks stay as they are. */
    static boolean canReplace(Material existing) {
        return existing == Material.AIR || existing == Material.CAVE_AIR || existing == Material.VOID_AIR;
    }

    /** Horizontal facing as a unit step (dx, dz) from a look vector; the larger axis wins, ties go to z. */
    static int[] cardinal(double lookX, double lookZ) {
        if (Math.abs(lookX) > Math.abs(lookZ)) return new int[]{lookX > 0 ? 1 : -1, 0};
        return new int[]{0, lookZ >= 0 ? 1 : -1};
    }

    /** Wool cells under the egg's flight from one tick to the next: one block below the egg, no repeats. */
    static List<Pos> bridgeCells(double fx, double fy, double fz, double tx, double ty, double tz) {
        Set<Pos> cells = new LinkedHashSet<>();
        double length = Math.sqrt((tx - fx) * (tx - fx) + (ty - fy) * (ty - fy) + (tz - fz) * (tz - fz));
        int steps = Math.max(1, (int) Math.ceil(length / 0.4));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            cells.add(new Pos((int) Math.floor(fx + (tx - fx) * t), (int) Math.floor(fy + (ty - fy) * t) - 1,
                    (int) Math.floor(fz + (tz - fz) * t)));
        }
        return new ArrayList<>(cells);
    }

    /**
     * A hollow 3x3 wool tower 5 high whose centre is two blocks in front of {@code feet}. The wall nearest the
     * player has a two-high doorway; a ladder runs up the centre cell against the far wall.
     */
    static List<Piece> tower(Pos feet, int dx, int dz) {
        List<Piece> pieces = new ArrayList<>();
        int rx = -dz, rz = dx;
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            for (int y = 0; y < TOWER_HEIGHT; y++) {
                boolean doorway = a == 0 && b == -1 && y < 2;
                Pos at = feet.add(dx * (2 + b) + rx * a, y, dz * (2 + b) + rz * a);
                if (a == 0 && b == 0) pieces.add(new Piece(at, Kind.LADDER));
                else if (!doorway) pieces.add(new Piece(at, Kind.WOOL));
            }
        }
        return pieces;
    }

    /** A 5 wide, 3 high wall two blocks in front of the player, across their facing. */
    static List<Pos> wall(Pos feet, int dx, int dz) {
        List<Pos> cells = new ArrayList<>();
        int rx = -dz, rz = dx;
        for (int a = -2; a <= 2; a++) for (int y = 0; y < 3; y++) cells.add(feet.add(dx * 2 + rx * a, y, dz * 2 + rz * a));
        return cells;
    }

    /** Eight blocks of path starting one step ahead of the block under the player's feet. */
    static List<Pos> iceRoad(Pos feet, int dx, int dz) {
        List<Pos> cells = new ArrayList<>();
        for (int i = 1; i <= ICE_ROAD_LENGTH; i++) cells.add(feet.add(dx * i, -1, dz * i));
        return cells;
    }

    /** The 3x3 patch centred on a clicked ground block. */
    static List<Pos> honeyPatch(Pos centre) {
        List<Pos> cells = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) cells.add(centre.add(x, 0, z));
        return cells;
    }

    /** Cells that may actually be built into: empty, and not for the bed-region test to refuse. */
    static List<Pos> buildable(List<Pos> cells, Predicate<Pos> empty, Predicate<Pos> protectedCell) {
        return cells.stream().filter(p -> empty.test(p) && !protectedCell.test(p)).toList();
    }
}
