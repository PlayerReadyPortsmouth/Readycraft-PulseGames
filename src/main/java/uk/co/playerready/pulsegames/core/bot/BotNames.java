package uk.co.playerready.pulsegames.core.bot;

/** Playful names remain clearly labelled NPC and fit the 16-character player name limit. */
final class BotNames {
    private static final String[] FIRST = {"Waffle", "Mango", "Pixel", "Toast", "Noodle", "Biscuit", "Pickle", "Bouncy",
            "Sleepy", "Fuzzy", "Cosmic", "Jelly", "Turbo", "Wobbly", "Minty", "Disco"};
    private static final String[] LAST = {"Fox", "Duck", "Moth", "Cat", "Owl", "Bee", "Yak", "Pug",
            "Panda", "Otter", "Gecko", "Toad", "Crab", "Mole", "Koala", "Llama"};
    private BotNames() {}
    static String at(int sequence) {
        long value = Integer.toUnsignedLong(sequence);
        String suffix = value < 256 ? "" : "_" + Long.toString(value, 36);
        String base = FIRST[(int)(value % 16)] + LAST[(int)((value / 16) % 16)];
        return base.substring(0, Math.min(base.length(), 12 - suffix.length())) + suffix + "_NPC";
    }
}
