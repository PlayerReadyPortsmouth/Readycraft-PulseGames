package uk.co.playerready.pulsegames.core.game;

public enum GameState {
    /** World loading / instance being prepared. */
    LOADING,
    /** Waiting lobby, accepting players. */
    WAITING,
    /** Countdown running, still accepting players. */
    COUNTDOWN,
    /** Game in progress. */
    RUNNING,
    /** Winners decided, celebration period. */
    ENDING,
    /** Players evacuated, world being deleted. */
    RESETTING
}
