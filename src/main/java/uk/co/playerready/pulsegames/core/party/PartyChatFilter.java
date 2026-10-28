package uk.co.playerready.pulsegames.core.party;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Local safety net for {@code /party chat}. Party chat is private text between up to eight players and does not
 * pass through the server's public chat pipeline, so this refuses the obvious: abuse and anything that looks like
 * a way to contact someone off the server (links, emails, phone numbers). Extra words come from
 * {@code party.chat-blocklist} in config.yml. It is a backstop, not moderation: every message is also logged.
 */
public final class PartyChatFilter {
    private PartyChatFilter() {}

    /** Stored in {@link #normalise} form, where every run of one letter is a single letter. */
    private static final List<String> DEFAULT_WORDS = java.util.stream.Stream.of(
            "fuck", "fucking", "fucker", "shit", "bitch", "cunt", "dick", "pussy", "slut", "whore", "wanker",
            "bastard", "twat", "nigger", "nigga", "faggot", "fag", "retard", "porn", "rape", "kys", "nazi")
            .map(PartyChatFilter::normalise).toList();

    private static final Pattern LINK = Pattern.compile(
            "(?i)(https?://|www\\.|\\b[a-z0-9-]+\\.(com|net|org|gg|io|co\\.uk|tv|me|ly)\\b|discord\\.gg|\\bdiscord\\b)");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)[a-z0-9._%+-]+\\s*@\\s*[a-z0-9-]+\\.[a-z]{2,}|\\bat\\b.{0,20}\\bdot\\b");
    private static final Pattern PHONE = Pattern.compile("(?:\\d[\\s().-]*){7,}");

    /** True when the message must not be delivered. */
    public static boolean blocked(String message, Collection<String> extraWords) {
        if (message == null) return false;
        if (LINK.matcher(message).find() || EMAIL.matcher(message).find() || PHONE.matcher(message).find()) return true;
        for (String token : normalise(message).split("[^a-z]+")) {
            if (token.isEmpty()) continue;
            if (DEFAULT_WORDS.contains(token)) return true;
            for (String extra : extraWords) if (token.equals(normalise(extra))) return true;
        }
        return false;
    }

    /** Lower case, common character swaps undone ("sh1t", "b!tch"), each run of one letter squeezed to one ("fuuuck"). */
    static String normalise(String message) {
        StringBuilder out = new StringBuilder(message.length());
        for (char c : message.toLowerCase(Locale.ROOT).toCharArray()) {
            char mapped = switch (c) {
                case '0' -> 'o'; case '1', '!', '|' -> 'i'; case '3' -> 'e'; case '4', '@' -> 'a';
                case '5', '$' -> 's'; case '7' -> 't'; case '8' -> 'b';
                default -> c;
            };
            int n = out.length();
            if (n >= 1 && out.charAt(n - 1) == mapped) continue;
            out.append(mapped);
        }
        return out.toString();
    }
}
