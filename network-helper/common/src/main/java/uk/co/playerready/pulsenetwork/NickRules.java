package uk.co.playerready.pulsenetwork;

import java.util.*;

/**
 * Pure nickname rules: shape, anti-impersonation and a blocked-words filter. Messages are written for a children's network:
 * they never repeat the offending word. Nothing here does I/O.
 */
public final class NickRules {
    public static final int MIN = 3, MAX = 16;
    public static final String RESET = "reset";
    public record Result(boolean ok, String message) {
        static final Result OK = new Result(true, null);
        static Result no(String message) { return new Result(false, message); }
    }
    /** Small built-in list; the network adds its own through {@code nick.blocked-words}. Matched after leetspeak and underscores are folded away. */
    static final List<String> BUILT_IN = List.of("fuck", "shit", "bitch", "cunt", "dick", "cock", "penis", "vagina", "nigg", "fag", "retard", "whore",
            "slut", "rape", "nazi", "hitler", "porn", "sex", "piss", "twat", "wank", "bastard", "paedo", "pedo", "kkk", "dildo", "boob", "nude");
    private NickRules() {}

    /** 3 to 16 letters, digits or underscores, at least two letters. No spaces, no formatting codes. Does not look at other names. */
    public static boolean shapeOk(String nick) {
        return nick != null && nick.matches("[A-Za-z0-9_]{" + MIN + "," + MAX + "}") && nick.chars().filter(c -> Character.isLetter(c)).count() >= 2
                && !nick.equalsIgnoreCase(RESET);
    }

    /** Comma separated extra words (the {@code nick.blocked-words} value). */
    public static Set<String> words(String csv) {
        var set = new HashSet<String>();
        if (csv != null) for (String s : csv.split(",")) if (!s.isBlank()) set.add(fold(s.trim()));
        set.remove("");
        return Set.copyOf(set);
    }

    /**
     * @param others names and nicknames of other players that must not be copied (online players)
     * @param protectedNames dev, admin and staff usernames
     * @param extraBlocked folded words from {@link #words}
     */
    public static Result check(String nick, Collection<String> others, Collection<String> protectedNames, Set<String> extraBlocked) {
        if (nick == null || nick.length() < MIN || nick.length() > MAX) return Result.no("A nickname needs 3 to 16 characters.");
        if (!nick.matches("[A-Za-z0-9_]+")) return Result.no("A nickname can only use letters, numbers and underscores. No spaces or colours.");
        if (nick.chars().filter(Character::isLetter).count() < 2) return Result.no("A nickname needs at least 2 letters.");
        if (nick.equalsIgnoreCase(RESET)) return Result.no("That nickname cannot be used. Please pick another.");
        String folded = fold(nick);
        for (String w : BUILT_IN) if (folded.contains(w)) return Result.no("That nickname is not allowed on this network. Please pick another.");
        for (String w : extraBlocked) if (folded.contains(w)) return Result.no("That nickname is not allowed on this network. Please pick another.");
        for (String other : others) if (close(nick, other)) return Result.no("That is too close to another player's name. Please pick something different.");
        for (String other : protectedNames) if (close(nick, other)) return Result.no("That is too close to a staff member's name. Please pick something different.");
        return Result.OK;
    }

    private static boolean close(String a, String b) { return b != null && !b.isEmpty() && withinOne(a.toLowerCase(Locale.ROOT), b.toLowerCase(Locale.ROOT)); }

    /** Edit distance of at most one (insert, delete or change a character). Equal strings count. */
    static boolean withinOne(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) return false;
        int i = 0, j = 0, edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) { i++; j++; continue; }
            if (++edits > 1) return false;
            if (a.length() > b.length()) i++; else if (a.length() < b.length()) j++; else { i++; j++; }
        }
        return edits + (a.length() - i) + (b.length() - j) <= 1;
    }

    /** Lower case, common digit lookalikes turned into letters, underscores removed. */
    static String fold(String s) {
        var out = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            switch (c) {
                case '0' -> out.append('o'); case '1' -> out.append('i'); case '3' -> out.append('e'); case '4' -> out.append('a');
                case '5' -> out.append('s'); case '7' -> out.append('t'); case '_' -> { }
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
