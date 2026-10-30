package uk.co.playerready.pulsegames.core.ui;

import java.util.function.Function;

/**
 * Pure state machine behind {@link AnvilPrompt}: one prompt, open until it is accepted or cancelled, with
 * validation that keeps it open (the caller shows the error and lets the player try again). Once finished,
 * every further input is ignored, so a double-click or a late Bedrock reply can never fire twice.
 */
public final class AnvilPromptState {
    public enum Phase { OPEN, DONE, CANCELLED }

    public enum Kind { ACCEPTED, REJECTED, IGNORED }

    public record Outcome(Kind kind, String text, String error) { }

    public static final int MAX_LENGTH = 50;

    private final String prompt;
    private final Function<String, String> validator;
    private Phase phase = Phase.OPEN;
    private int rejections;

    /** @param validator returns an error to show, or null when the text is fine */
    public AnvilPromptState(String prompt, Function<String, String> validator) {
        this.prompt = prompt;
        this.validator = validator;
    }

    public Phase phase() { return phase; }
    public int rejections() { return rejections; }
    public boolean open() { return phase == Phase.OPEN; }

    /** Trims, drops control characters and collapses runs of spaces. */
    public static String clean(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\p{Cntrl}", "").replaceAll("\\s+", " ").trim();
    }

    public Outcome submit(String raw) {
        if (phase != Phase.OPEN) return new Outcome(Kind.IGNORED, "", null);
        String text = clean(raw);
        String error = null;
        if (text.isEmpty() || text.equals(prompt)) error = "Type your answer first.";
        else if (text.length() > MAX_LENGTH) error = "That is too long (most " + MAX_LENGTH + " characters).";
        else if (validator != null) error = validator.apply(text);
        if (error != null) {
            rejections++;
            return new Outcome(Kind.REJECTED, text, error);
        }
        phase = Phase.DONE;
        return new Outcome(Kind.ACCEPTED, text, null);
    }

    /** True the first time only; false when the prompt had already finished. */
    public boolean cancel() {
        if (phase != Phase.OPEN) return false;
        phase = Phase.CANCELLED;
        return true;
    }
}
