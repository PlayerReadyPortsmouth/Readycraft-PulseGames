package uk.co.playerready.pulsegames.games.christmas;

/** Server-independent scoring and carrying rules, shared by human and bot actions. */
public final class PresentRules {
    private PresentRules() {}
    public enum Colour { RED, GREEN, BLUE, GOLD;
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public enum Role { SPEEDY, WRAPPER, SNOWBALLER, PACKER }
    public record Present(Colour toy, Colour paper, boolean bow, int lastTeam, boolean stolen) {
        public Present wrap(Colour colour) { return new Present(toy, colour, false, lastTeam, stolen); }
        public Present withBow() { return paper == null ? this : new Present(toy, paper, true, lastTeam, stolen); }
        public Present pickedUp(int team) {
            return new Present(toy, paper, bow, team, stolen || lastTeam >= 0 && lastTeam != team);
        }
    }
    public static int score(Present present, boolean doublePoints) {
        if (present.paper == null) return 0;
        int base = present.paper == null ? 0 : present.toy == present.paper ? present.bow ? 6 : 3 : 1;
        return (base + (present.stolen ? 1 : 0)) * (doublePoints ? 2 : 1);
    }
    public static int capacity(Role role) { return role == Role.PACKER ? 4 : 3; }
    public static int wrapTicks(Role role, boolean shortage) {
        return (role == Role.WRAPPER ? 30 : 60) * (shortage ? 2 : 1);
    }
    public static float walkSpeed(Role role, int count) {
        return Math.max(0.09f, (role == Role.SPEEDY ? 0.24f : 0.20f) - Math.max(0, count) * 0.025f);
    }
    /** The topmost unfinished toy is worked on, so a full stack can all be wrapped before delivery. */
    public static Present nextUnwrapped(java.util.Deque<Present> stack) {
        for (var iterator = stack.descendingIterator(); iterator.hasNext();) {
            Present present = iterator.next(); if (present.paper == null) return present;
        }
        return null;
    }
    public static boolean wrapNext(java.util.Deque<Present> stack, Colour colour) {
        java.util.List<Present> list = new java.util.ArrayList<>(stack);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).paper == null) {
            list.set(i, list.get(i).wrap(colour)); stack.clear(); stack.addAll(list); return true;
        }
        return false;
    }
    public static void bowAll(java.util.Deque<Present> stack) {
        java.util.List<Present> list = stack.stream().map(Present::withBow).toList();
        stack.clear(); stack.addAll(list);
    }
}
