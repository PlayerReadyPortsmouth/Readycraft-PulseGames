package uk.co.playerready.pulsegames.core.ranked;

/** A disconnect only costs rating if the player is still away when the grace period ends. */
public final class DisconnectGrace {
    private DisconnectGrace() { }

    public enum Verdict { WAITING, VOID, COUNTS }

    /** @param reconnectedMs when the player came back, or a negative number if they have not */
    public static Verdict verdict(long disconnectedMs, long reconnectedMs, long nowMs, long graceMs) {
        if (reconnectedMs >= 0 && reconnectedMs - disconnectedMs <= graceMs) return Verdict.VOID;
        return nowMs - disconnectedMs >= graceMs ? Verdict.COUNTS : Verdict.WAITING;
    }

    public static long remainingMs(long disconnectedMs, long nowMs, long graceMs) {
        return Math.max(0, disconnectedMs + graceMs - nowMs);
    }
}
