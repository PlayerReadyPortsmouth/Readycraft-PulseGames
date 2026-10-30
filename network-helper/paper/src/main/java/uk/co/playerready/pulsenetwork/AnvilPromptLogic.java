package uk.co.playerready.pulsenetwork;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The pure state machine behind {@link AnvilPrompt} and the Bedrock text form: validation, re-prompting with an error line, and
 * cancel. It knows nothing about Bukkit, so it is tested with plain calls standing in for inventory events.
 */
final class AnvilPromptLogic {
    /** Trimmed text must be {@code min..max} long and match {@code pattern}; {@code hint} is the error line shown otherwise. */
    record Rule(Pattern pattern, int min, int max, String hint) {
        Optional<String> check(String raw) {
            String t = raw == null ? "" : raw.trim();
            return t.length() >= min && t.length() <= max && pattern.matcher(t).matches() ? Optional.empty() : Optional.of(hint);
        }
        static Rule playerName() { return new Rule(Pattern.compile("[.A-Za-z0-9_]+"), 1, 17, "Use 1 to 17 letters, numbers or _ (Bedrock names start with a dot)."); }
        static Rule text(int max) { return new Rule(Pattern.compile("[^\\p{Cntrl}]+"), 1, max, "Type 1 to " + max + " characters."); }
    }
    enum State { OPEN, REPROMPT, DONE, CANCELLED }
    /** What the caller should do after an event. */
    enum Outcome { NONE, ACCEPT, REPROMPT, CANCEL }

    static final int MAX_ATTEMPTS = 5, RESULT_SLOT = 2;
    private final Rule rule;
    private State state = State.OPEN;
    private int attempts;
    private String value, error;

    AnvilPromptLogic(Rule rule) { this.rule = rule; }
    State state() { return state; }
    String value() { return value; }
    String error() { return error; }

    /** Click on a raw slot. Only the result slot submits; everything else is ignored (the listener cancels the click itself). */
    Outcome click(int rawSlot, String renameText) {
        if (state != State.OPEN || rawSlot != RESULT_SLOT) return Outcome.NONE;
        var problem = rule.check(renameText);
        if (problem.isEmpty()) { state = State.DONE; value = renameText.trim(); error = null; return Outcome.ACCEPT; }
        error = problem.get();
        if (++attempts >= MAX_ATTEMPTS) { state = State.CANCELLED; return Outcome.CANCEL; }
        state = State.REPROMPT;
        return Outcome.REPROMPT;
    }
    /** The caller re-opened the anvil after {@link Outcome#REPROMPT}. */
    void reopened() { if (state == State.REPROMPT) state = State.OPEN; }
    /** The window closed (Escape, walked away, or our own re-open). */
    Outcome close() {
        if (state == State.REPROMPT || state == State.DONE || state == State.CANCELLED) return Outcome.NONE;
        state = State.CANCELLED;
        return Outcome.CANCEL;
    }
}
