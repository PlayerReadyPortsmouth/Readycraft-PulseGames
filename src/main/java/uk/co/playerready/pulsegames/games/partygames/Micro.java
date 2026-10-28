package uk.co.playerready.pulsegames.games.partygames;

/** The micro-game set. {@code region} is the arena region it needs (null = works anywhere on the hub spawns). */
enum Micro {
    COLOR_RUSH("Color Rush", "Stand on the colour that is called!", "floor"),
    SUMO("Sumo Brawl", "Knock everyone off the platform!", "sumo"),
    SPRINT("Sprint", "Race to the finish line!", "finish"),
    SPLEEF("Mini Spleef", "Dig the floor out from under your rivals!", "floor"),
    TNT_RUN("TNT Run", "Keep moving - the floor falls away behind you!", "floor"),
    HOT_POTATO("Hot Potato", "Don't hold the potato when it pops - hit someone to pass it on!", "sumo"),
    MEMORY("Memory Tiles", "Remember the green tiles, then stand on one!", "floor"),
    SIMON_SAYS("Simon Says", "Only do it when Simon says!", null),
    NONE("", "", null);

    final String title;
    final String instruction;
    final String region;

    Micro(String title, String instruction, String region) {
        this.title = title;
        this.instruction = instruction;
        this.region = region;
    }

    /** Games where players are knocked out and the last ones standing place highest. */
    boolean survival() {
        return this != SPRINT && this != NONE;
    }

    /** Games decided by falling off or through the floor region. */
    boolean fallsThroughFloor() {
        return this == SPLEEF || this == TNT_RUN || this == MEMORY;
    }
}
