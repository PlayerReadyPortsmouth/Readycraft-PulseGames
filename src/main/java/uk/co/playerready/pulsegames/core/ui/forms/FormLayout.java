package uk.co.playerready.pulsegames.core.ui.forms;

import java.util.ArrayList;
import java.util.List;

/** Pure button ordering for the Bedrock forms (no Floodgate or Bukkit types, so it is unit tested). */
public final class FormLayout {
    private FormLayout() { }

    public static final String QUICK_PLAY = "quickplay";
    public static final String PARTY = "party";
    public static final String CHALLENGES = "challenges";
    public static final String TOP = "top";
    public static final String KIT = "kit";
    public static final String LEVEL = "level";
    public static final String BACK = "back";
    public static final String GAME = "game:";
    public static final String MODE = "mode:";
    public static final String SOON = "soon:";

    /** A form button: what the child reads, and what it does. */
    public record Button(String label, String target) { }

    public record GameRow(String id, String name, int waiting, int playing, boolean available) { }

    public record ModeRow(String id, String name, int waiting, int playing) { }

    /** "3 waiting" first, because it is the reason to tap; quiet games say so plainly. */
    public static String activity(int waiting, int playing) {
        if (waiting > 0) return waiting + " waiting, " + playing + " playing";
        if (playing > 0) return playing + " playing";
        return "Quiet right now";
    }

    /** Quick Play first, then games in the given order, then the helper buttons the chest menu also has. */
    public static List<Button> gameList(List<GameRow> games) {
        List<Button> out = new ArrayList<>();
        out.add(new Button("Quick Play\nJump into the busiest game that is starting now", QUICK_PLAY));
        for (GameRow g : games) {
            out.add(g.available()
                    ? new Button(g.name() + "\n" + activity(g.waiting(), g.playing()), GAME + g.id())
                    : new Button(g.name() + "\nComing soon!", SOON + g.id()));
        }
        out.add(new Button("Play with a party", PARTY));
        out.add(new Button("Daily challenges", CHALLENGES));
        out.add(new Button("Weekly top 3", TOP));
        out.add(new Button("KitPvP loadout", KIT));
        out.add(new Button("My level", LEVEL));
        return out;
    }

    /** Modes in order, then Back last. */
    public static List<Button> modeList(List<ModeRow> modes) {
        List<Button> out = new ArrayList<>();
        for (ModeRow m : modes) out.add(new Button(m.name() + "\n" + activity(m.waiting(), m.playing()), MODE + m.id()));
        out.add(new Button("Back", BACK));
        return out;
    }

    /** The target for a tapped button index, or null when the index is out of range. */
    public static String targetAt(List<Button> buttons, int index) {
        return index >= 0 && index < buttons.size() ? buttons.get(index).target() : null;
    }

    /** Forms for Bedrock players only, and only when the switch is on and Floodgate is really there. */
    public static boolean useForm(boolean configEnabled, boolean floodgatePresent, boolean bedrock) {
        return configEnabled && floodgatePresent && bedrock;
    }

    /** Label above a text box; a rejected answer's reason goes first so it is the thing they read. */
    public static String inputLabel(String label, String error) {
        return error == null || error.isBlank() ? label : error + "\n" + label;
    }
}
