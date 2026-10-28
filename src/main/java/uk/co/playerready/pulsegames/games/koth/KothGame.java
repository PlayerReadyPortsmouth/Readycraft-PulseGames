package uk.co.playerready.pulsegames.games.koth;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.kit.Kit;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.common.AssistTracker;
import uk.co.playerready.pulsegames.games.common.SafeRespawn;
import uk.co.playerready.pulsegames.games.common.SpawnProtection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * King of the Hill: hold the hill to earn points; first to the target wins.
 * A contested hill (two sides on it) scores for nobody. Kills and assists add points.
 * Maps with regions hill1..N rotate the active hill every few seconds.
 * Modes: solo, teams.
 */
public final class KothGame extends MiniGame {
    static final int KILL_POINTS = 5;
    static final int ASSIST_POINTS = 2;
    private static final double SPAWN_PROTECTION_SECONDS = 2;

    private Cuboid hill;
    private List<Cuboid> hills = List.of();
    private int hillIndex;
    private int rotateIn;
    private HillControl.State lastState = HillControl.State.EMPTY;
    private final Map<Integer, Integer> teamScores = new HashMap<>();
    private final SpawnProtection protection = new SpawnProtection();
    private final AssistTracker assists = new AssistTracker(8_000);
    private final BossBar bar = BossBar.bossBar(Text.mm("<gray>Capture the hill"), 0f,
            BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
    private final Set<UUID> barViewers = new HashSet<>();

    public KothGame(GameInstance game) {
        super(game);
    }

    private int target() {
        return game.arena().settings().getInt("target-score", game.mode().isTeams() ? 250 : 120);
    }

    private int rotateSeconds() {
        return Math.max(20, game.arena().settings().getInt("hill-rotate-seconds", 75));
    }

    @Override
    public void onStart() {
        hills = hillRegions();
        hill = hills.isEmpty() ? game.arena().region("hill") : hills.get(0);
        rotateIn = rotateSeconds();
        Kit kit = game.plugin().kits().get(game.arena().settings().getString("kit", "koth"));
        if (kit != null) game.alivePlayers().forEach(kit::apply);
        game.alivePlayers().forEach(p -> protection.protect(p.getUniqueId(), SPAWN_PROTECTION_SECONDS));
        game.broadcast("Hold the hill! First to <yellow>" + target() + "</yellow> points wins."
                + " Two sides on the hill = <red>contested</red>, nobody scores.");
    }

    /** hill1..N in numeric order when the map defines more than one; otherwise nothing (single "hill"). */
    private List<Cuboid> hillRegions() {
        var numbered = new ArrayList<Map.Entry<Integer, Cuboid>>();
        game.arena().regions().forEach((name, region) -> {
            if (name.matches("hill\\d+")) numbered.add(Map.entry(Integer.parseInt(name.substring(4)), region));
        });
        numbered.sort(Map.Entry.comparingByKey());
        return numbered.size() > 1 ? numbered.stream().map(Map.Entry::getValue).toList() : List.of();
    }

    @Override
    public void onRespawn(Player player) {
        Kit kit = game.plugin().kits().get(game.arena().settings().getString("kit", "koth"));
        if (kit != null) kit.apply(player);
        protection.protect(player.getUniqueId(), SPAWN_PROTECTION_SECONDS);
        player.sendActionBar(Text.mm("<aqua>Spawn protected for " + (int) SPAWN_PROTECTION_SECONDS + "s"));
    }

    @Override
    public Location respawnLocation(Player player) {
        return SafeRespawn.forPlayer(game, player, ThreadLocalRandom.current());
    }

    @Override
    public void onDamage(Player victim, org.bukkit.event.entity.EntityDamageEvent event) {
        if (protection.isProtected(victim.getUniqueId())) event.setCancelled(true);
    }

    @Override
    public void onDamageByPlayer(Player victim, Player damager, org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (protection.isProtected(victim.getUniqueId())) { event.setCancelled(true); return; }
        protection.clear(damager.getUniqueId());
        assists.hit(victim.getUniqueId(), damager.getUniqueId(), System.currentTimeMillis());
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        long now = System.currentTimeMillis();
        List<UUID> helpers = assists.assisters(victim.getUniqueId(), killer == null ? null : killer.getUniqueId(), now);
        protection.clear(victim.getUniqueId());
        if (killer != null && !killer.equals(victim)) {
            game.broadcast("<red>" + victim.getName() + "</red> was killed by <red>" + killer.getName());
            game.stats().addKill(killer, game.type().id());
            credit(killer, KILL_POINTS);
            killer.sendActionBar(Text.mm("<green>+" + KILL_POINTS + " <gray>kill: <white>" + victim.getName()));
            for (UUID id : helpers) {
                Player helper = game.player(id);
                if (helper == null || helper.equals(victim)) continue;
                credit(helper, ASSIST_POINTS);
                helper.sendActionBar(Text.mm("<green>+" + ASSIST_POINTS + " <gray>assist"));
            }
        } else {
            game.broadcast("<red>" + victim.getName() + "</red> died");
        }
        game.respawn(victim, respawnDelaySeconds());
        checkWin();
    }

    /** Points go to the team in team games, to the player in solo. */
    private void credit(Player player, int points) {
        GameTeam team = game.teamOf(player);
        if (team != null) teamScores.merge(team.index(), points, Integer::sum);
        else game.addScore(player, points);
    }

    @Override
    public void onSecond(int gameTime) {
        if (hill == null) return;
        rotateHill();
        List<Player> onHill = game.alivePlayers().stream()
                .filter(game::isActive)
                .filter(p -> hill.contains(p.getLocation())).toList();
        // One point per side per second, not per player: stacking the hill would otherwise
        // scale a team's capture rate with its size and win in a fraction of the time.
        var control = HillControl.resolve(onHill.stream().map(this::sideOf).toList());
        if (control.state() == HillControl.State.HELD) {
            if (control.holder() instanceof Integer team) teamScores.merge(team, 1, Integer::sum);
            else onHill.forEach(p -> game.addScore(p, 1));
        }
        if (control.state() == HillControl.State.CONTESTED && lastState != HillControl.State.CONTESTED) {
            onHill.forEach(p -> Text.title(p, "<red><b>CONTESTED", "<gray>Clear the hill to score"));
        }
        lastState = control.state();
        for (Player player : onHill) {
            switch (control.state()) {
                case CONTESTED -> player.sendActionBar(Text.mm("<red><b>CONTESTED</b> <gray>- no points while rivals are here"));
                default -> {
                    GameTeam team = game.teamOf(player);
                    int shown = team != null ? teamScores.getOrDefault(team.index(), 0) : game.score(player);
                    player.sendActionBar(Text.mm("<gold>⚑ Capturing! <yellow>+1</yellow> <gray>(" + shown + "/" + target() + ")"));
                    player.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                            player.getLocation().add(0, 0.2, 0), 5, 0.3, 0.1, 0.3, 0.01);
                }
            }
        }
        updateBar(control);
        checkWin();
    }

    private Object sideOf(Player player) {
        GameTeam team = game.teamOf(player);
        return team != null ? (Object) team.index() : player.getUniqueId();
    }

    private void rotateHill() {
        if (hills.size() < 2) return;
        if (--rotateIn > 0) {
            if (rotateIn <= 5) game.alivePlayers().forEach(p -> p.sendActionBar(Text.mm("<yellow>The hill moves in " + rotateIn + "s")));
            return;
        }
        rotateIn = rotateSeconds();
        hillIndex = HillControl.next(hillIndex, hills.size());
        hill = hills.get(hillIndex);
        for (Player p : game.alivePlayers()) {
            Text.title(p, "<gold><b>NEW HILL", "<gray>Hill " + (hillIndex + 1) + " of " + hills.size() + " - go go go!");
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
        }
    }

    /** Capture bar in the holder's team colour; red CONTESTED when rivals share the hill. */
    private void updateBar(HillControl.Control<Object> control) {
        for (Player p : game.alivePlayers()) if (barViewers.add(p.getUniqueId())) p.showBossBar(bar);
        switch (control.state()) {
            case CONTESTED -> {
                bar.name(Text.mm("<red><b>CONTESTED"));
                bar.color(BossBar.Color.RED);
            }
            case HELD -> {
                int score;
                if (control.holder() instanceof Integer index) {
                    GameTeam team = game.teams().stream().filter(t -> t.index() == index).findFirst().orElse(null);
                    score = teamScores.getOrDefault(index, 0);
                    bar.name(Text.mm((team == null ? "Team" : team.coloredName()) + "<gray> holds the hill"));
                    bar.color(team == null ? BossBar.Color.GREEN : barColor(team));
                } else {
                    Player holder = game.player((UUID) control.holder());
                    score = holder == null ? 0 : game.score(holder);
                    bar.name(Text.mm("<green>" + (holder == null ? "Someone" : holder.getName()) + "<gray> holds the hill"));
                    bar.color(BossBar.Color.GREEN);
                }
                bar.progress(Math.min(1f, score / (float) target()));
            }
            default -> {
                bar.name(Text.mm("<gray>Capture the hill"));
                bar.color(BossBar.Color.WHITE);
            }
        }
    }

    static BossBar.Color barColor(GameTeam team) {
        return switch (team.palette().name()) {
            case "Red" -> BossBar.Color.RED;
            case "Blue", "Aqua" -> BossBar.Color.BLUE;
            case "Green" -> BossBar.Color.GREEN;
            case "Yellow" -> BossBar.Color.YELLOW;
            case "Pink" -> BossBar.Color.PINK;
            default -> BossBar.Color.WHITE;
        };
    }

    private void hideBar() {
        for (UUID id : barViewers) {
            Player p = game.player(id);
            if (p != null) p.hideBossBar(bar);
        }
        barViewers.clear();
    }

    @Override public void onQuit(Player player) {
        if (barViewers.remove(player.getUniqueId())) player.hideBossBar(bar);
        protection.clear(player.getUniqueId());
    }
    @Override public void onEnd(List<Player> winners) { hideBar(); }
    @Override public void onCleanup() { hideBar(); protection.clear(); assists.clear(); }

    private void checkWin() {
        if (game.mode().isTeams()) {
            teamScores.entrySet().stream()
                    .filter(e -> e.getValue() >= target())
                    .findFirst()
                    .ifPresent(e -> game.end(membersOfTeam(e.getKey()), "score"));
        } else {
            game.topScores().stream()
                    .filter(e -> e.getValue() >= target())
                    .findFirst()
                    .ifPresent(e -> game.end(playerList(e.getKey()), "score"));
        }
    }

    private List<Player> membersOfTeam(int index) {
        return game.teams().stream()
                .filter(t -> t.index() == index)
                .findFirst()
                .map(t -> game.alivePlayers().stream().filter(t::contains).toList())
                .orElse(List.of());
    }

    private List<Player> playerList(UUID uuid) {
        return game.players().stream().filter(p -> p.getUniqueId().equals(uuid)).toList();
    }

    @Override
    public void onTimeUp() {
        List<List<Player>> leaders = new ArrayList<>();
        int highest = Integer.MIN_VALUE;
        if (game.mode().isTeams()) {
            for (GameTeam team : game.teams()) {
                List<Player> members = membersOfTeam(team.index());
                if (members.isEmpty()) continue;
                int score = teamScores.getOrDefault(team.index(), 0);
                if (score > highest) { leaders.clear(); highest = score; }
                if (score == highest) leaders.add(members);
            }
        } else {
            for (Player player : game.alivePlayers()) {
                int score = game.score(player);
                if (score > highest) { leaders.clear(); highest = score; }
                if (score == highest) leaders.add(List.of(player));
            }
        }
        game.end(highest > 0 && leaders.size() == 1 ? leaders.get(0) : List.of(), leaders.size() > 1 ? "time-draw" : "time");
    }

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Target: <yellow>" + target());
        if (game.mode().isTeams()) {
            game.teams().stream()
                    .sorted(Comparator.comparingInt((GameTeam t) -> teamScores.getOrDefault(t.index(), 0)).reversed())
                    .limit(4)
                    .forEach(t -> lines.add(t.coloredName() + "<gray>: <white>" + teamScores.getOrDefault(t.index(), 0)));
        } else {
            lines.add("<gray>You: <white>" + game.score(player));
            game.topScores().stream().limit(3).forEach(e -> {
                Player p = game.player(e.getKey());
                if (p != null) lines.add("<gray>" + p.getName() + ": <white>" + e.getValue());
            });
        }
        return lines;
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public boolean respawnable() { return true; }

    @Override
    public int respawnDelaySeconds() { return 5; }

    @Override
    public boolean fallDamage() { return true; }

    @Override
    public int timeLimitSeconds() { return 600; }
}
