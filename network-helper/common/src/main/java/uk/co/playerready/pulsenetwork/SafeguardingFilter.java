package uk.co.playerready.pulsenetwork;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Deterministic triage only. A hit asks a trained person to review; it is not a finding. */
public final class SafeguardingFilter {
    public enum Severity { NONE, LOW, HIGH, URGENT }
    public record Result(Severity severity, List<String> reasons) { public boolean blocked() { return severity != Severity.NONE; } }
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+\\s*@\\s*[a-z0-9.-]+\\s*\\.\\s*[a-z]{2,}|\\b\\w+\\s+(?:at|@)\\s+\\w+\\s+(?:dot|\\.)\\s+(?:com|net|org|co\\s+dot\\s+uk)\\b");
    private static final Pattern PHONE = Pattern.compile("(?<![\\p{L}\\d])(?:\\+?\\d[ ().-]*){10,15}(?![\\p{L}\\d])");
    private static final Pattern ADDRESS = Pattern.compile("(?i)\\b(?:i live at|my address is)\\b|\\b\\d{1,5}\\s+[a-z][a-z ]{1,35}\\s+(?:road|street|avenue|lane|drive|close|crescent|postcode)\\b|\\b[a-z]{1,2}\\d[a-z\\d]?\\s*\\d[a-z]{2}\\b");
    private static final Pattern SOCIAL = Pattern.compile("(?i)(?<!\\w)@[a-z0-9_][a-z0-9_.]{2,31}\\b|\\b(?:add|follow|dm|message|contact) me (?:on|at)\\b|\\b(?:my|your) (?:snapchat|snap|discord|instagram|insta|tiktok|whatsapp|telegram|phone number)\\b|\\b(?:move|switch|talk|chat) (?:to|on) (?:discord|snapchat|whatsapp|telegram|instagram)\\b");
    private static final Pattern WELFARE = Pattern.compile("\\b(?:kill myself|end my life|want to die|suicid(?:e|al)|self harm|hurt myself|cut myself)\\b");
    private static final Pattern THREAT = Pattern.compile("\\b(?:i(?: will| ll|m going to)|gonna) (?:kill|stab|shoot|hurt) (?:you|him|her|them)\\b|\\b(?:kill|shoot|stab) you\\b");
    private static final Pattern GROOMING = Pattern.compile("\\b(?:keep (?:this|it|us) (?:a )?secret|don t tell (?:your )?(?:parents|mum|dad)|send (?:me )?(?:nudes|naked (?:pics|pictures)|private (?:pics|photos)))\\b|\\b(?:i am|i m|im) (?:1[89]|[2-9]\\d).{0,80}\\b(?:you(?: re| are)?|ur) (?:[6-9]|1[0-7])\\b");
    private final List<Pattern> words;
    public SafeguardingFilter(String extraWords) {
        var entries = new LinkedHashSet<>(List.of("fuck", "shit", "bitch", "cunt", "nigger", "faggot"));
        for (String word : extraWords.split(",")) if (!word.isBlank()) {
            String clean = normalized(word).replaceAll("[^a-z]", "");
            if (clean.length() >= 3 && clean.length() <= 40) entries.add(clean);
        }
        words = entries.stream().map(word -> {
            StringJoiner letters = new StringJoiner("[\\W_]*");
            word.chars().forEach(c -> letters.add(Pattern.quote(Character.toString(c)) + "+"));
            return Pattern.compile("(?<![a-z])" + letters + "(?![a-z])");
        }).toList();
    }
    public Result evaluate(String input) {
        String text = normalized(input);
        String contact = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\p{Cf}", "").replaceAll("\\s+", " ");
        var reasons = new ArrayList<String>();
        if (WELFARE.matcher(text).find()) reasons.add("welfare");
        if (THREAT.matcher(text).find()) reasons.add("threat");
        if ((GROOMING.matcher(text).find() || GROOMING.matcher(contact.replaceAll("[’']", " ")).find())) reasons.add("grooming-pattern");
        boolean urgent = !reasons.isEmpty();
        if (EMAIL.matcher(contact).find()) reasons.add("email");
        String spoken = contact;
        String[] digits = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
        for (int i = 0; i < digits.length; i++) spoken = spoken.replaceAll("\\b" + digits[i] + "\\b", Integer.toString(i));
        if (PHONE.matcher(contact).find() || PHONE.matcher(spoken).find()) reasons.add("phone");
        if (ADDRESS.matcher(contact).find()) reasons.add("address");
        if (SOCIAL.matcher(contact).find() || SOCIAL.matcher(text).find() || Pattern.compile("\\bsend (?:me |that |your )?(?:pic|picture|photo)s? privately\\b").matcher(text).find()) reasons.add("off-platform-contact");
        if (!reasons.isEmpty()) return new Result(urgent ? Severity.URGENT : Severity.HIGH, List.copyOf(reasons));
        if (words.stream().anyMatch(word -> word.matcher(text).find())) return new Result(Severity.LOW, List.of("language"));
        return new Result(Severity.NONE, List.of());
    }
    static String normalized(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}|[\\p{Cf}]", "").replace('0', 'o').replace('1', 'i').replace('3', 'e')
                .replace('!', 'i').replace('|', 'i').replace('4', 'a').replace('5', 's').replace('7', 't').replace('@', 'a').replace('$', 's')
                .replaceAll("[’']", " ").replaceAll("\\s+", " ");
    }
}
