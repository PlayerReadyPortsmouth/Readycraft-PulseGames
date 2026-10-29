package uk.co.playerready.pulsegames.core.ranked;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Division names, thresholds and badge tags, from {@code ranked.divisions} in config.yml. */
public final class Divisions {
    public record Division(String name, int min, String tag, String color) { }

    /** What a badge shows before placement is finished. */
    public static final String UNRANKED = "Unranked";

    private final List<Division> list;

    public Divisions(List<Division> divisions) {
        List<Division> sorted = new ArrayList<>(divisions);
        sorted.sort(Comparator.comparingInt(Division::min));
        if (sorted.isEmpty()) sorted = new ArrayList<>(defaults());
        list = List.copyOf(sorted);
    }

    public static List<Division> defaults() {
        return List.of(new Division("Wood", 0, "WOD", "#a97142"), new Division("Stone", 900, "STN", "#9a9a9a"),
                new Division("Iron", 1100, "IRN", "#d8d8d8"), new Division("Gold", 1300, "GLD", "#ffc835"),
                new Division("Diamond", 1500, "DIA", "#4be6e6"));
    }

    public static Divisions fromConfig(ConfigurationSection config) {
        List<Division> out = new ArrayList<>();
        for (Map<?, ?> row : config == null ? List.<Map<?, ?>>of() : config.getMapList("ranked.divisions")) {
            Object name = row.get("name"), min = row.get("min"), tag = row.get("tag"), color = row.get("color");
            if (name == null || !(min instanceof Number n)) continue;
            out.add(new Division(name.toString(), n.intValue(), tag == null ? name.toString() : tag.toString(),
                    color == null ? "#ffffff" : color.toString()));
        }
        return new Divisions(out);
    }

    public List<Division> all() { return list; }

    public Division of(int rating) {
        Division found = list.getFirst();
        for (Division d : list) if (rating >= d.min()) found = d;
        return found;
    }

    /** The next division up, or null at the top. */
    public Division next(int rating) {
        for (Division d : list) if (d.min() > rating) return d;
        return null;
    }

    /** Rating points to the next division, or 0 at the top. */
    public int distanceToNext(int rating) {
        Division next = next(rating);
        return next == null ? 0 : next.min() - rating;
    }

    /** Division name, or {@link #UNRANKED} until the placement games are done. */
    public String label(int rating, int games) { return Elo.placed(games) ? of(rating).name() : UNRANKED; }

    /** MiniMessage badge such as {@code <#ffc835>[GLD]</#ffc835> }; empty while unranked. */
    public String badge(int rating, int games) {
        if (!Elo.placed(games)) return "";
        Division d = of(rating);
        return "<" + d.color() + ">[" + d.tag() + "]</" + d.color() + "> ";
    }
}
