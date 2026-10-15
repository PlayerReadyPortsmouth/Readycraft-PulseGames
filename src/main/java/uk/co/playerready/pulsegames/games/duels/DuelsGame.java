package uk.co.playerready.pulsegames.games.duels;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.kit.Kit;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Duels: 1v1, best of 3 rounds.
 * Modes: classic (iron kit), op (diamond kit), sumo (knock them off, no damage).
 */
public final class DuelsGame extends MiniGame {

    private final Map<org.bukkit.Location, org.bukkit.block.BlockState> placedBlocks = new HashMap<>();
    private final Map<UUID, UUID> crystalOwners = new HashMap<>();
    private final Map<UUID, Integer> roundWins = new HashMap<>();
    private final java.util.Set<UUID> roundOut = new java.util.HashSet<>();
    private final DuelBotCombat botCombat;
    private int round = 1;
    private boolean roundOver;
    private int roundRestartSeconds;

    public DuelsGame(GameInstance game) {
        super(game);
        botCombat = new DuelBotCombat(game, this);
    }

    public boolean botCombat(Player bot, org.bukkit.entity.LivingEntity enemy, long tick,
            uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) { return botCombat.combat(bot,enemy,tick,difficulty); }
    public org.bukkit.Location botObjective(Player bot,org.bukkit.Location proposed) { return botCombat.objective(bot,proposed); }
    public String botStatus(Player bot) { return botCombat.status(bot); }
    java.util.Set<UUID> botCrystals() { return java.util.Set.copyOf(crystalOwners.keySet()); }
    boolean botCombatArea(org.bukkit.Location location) { return inCombatArea(location); }
    void rememberBotBlock(org.bukkit.block.Block block, org.bukkit.block.BlockState previous) { placedBlocks.putIfAbsent(block.getLocation(),previous); }
    void forgetBotCrystal(UUID crystal) { crystalOwners.remove(crystal); }

    @Override
    public boolean acceptsDeath(Player player) { return !roundOver && !roundOut.contains(player.getUniqueId()); }

    private boolean isSumo() { return game.mode().rulesId().equals("sumo"); }

    private int roundsToWin() { return game.arena().settings().getInt("rounds-to-win", 2); }

    private String kitId() {
        return switch (game.mode().rulesId()) {
            case "op" -> "op";
            case "sumo" -> "sumo";
            default -> game.arena().settings().getString("kit", "warrior");
        };
    }

    private Kit selectedKit(Player player) {
        return game.mode().combatStyle() == uk.co.playerready.pulsegames.core.game.GameMode.CombatStyle.DEFAULT
                ? game.plugin().kits().get(kitId()) : game.plugin().duelMenu().kitFor(player, game.mode());
    }

    @Override public String startProblem(List<Player> players) {
        for (Player player : players) if (selectedKit(player) == null)
            return "A selected Duels kit is unavailable. Check the mode's compatible kits before starting.";
        return null;
    }

    @Override
    public void onStart() {
        startRound();
    }

    private void startRound() {
        roundRestartSeconds = 0;
        roundOver = false;
        roundOut.clear();
        resetArena();
        int i = 0;
        for (Player player : game.alivePlayers()) {
            game.plugin().playerState().reset(player);
            player.setGameMode(playGameMode());
            var team = game.teamOf(player);
            player.teleport(game.arena().spawn(team != null ? team.index() : i++, game.world()));
            if (game.plugin().combat() != null) game.plugin().combat().refresh(player);
            Kit kit = selectedKit(player);
            if (kit != null) kit.apply(player);
            Text.title(player, "<yellow><b>ROUND " + round, isSumo() ? "<gray>Knock them off!" : "<gray>Fight!");
        }
        game.broadcast("<yellow><b>Round " + round + "</b></yellow> <gray>- first to " + roundsToWin() + " round wins takes it!");
    }

    @Override
    public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {
        if (roundOver) event.setCancelled(true);
        else if (isSumo()) event.setDamage(0); // knockback only
    }

    @Override public boolean allowsTotems() { return isCrystal(); }
    private boolean isCrystal() { return game.mode().rulesId().equals("crystal"); }
    private boolean inCombatArea(org.bukkit.Location location) {
        var area = game.arena().region("combat");
        return area != null && location.getWorld().equals(game.world()) && area.contains(location);
    }
    @Override public boolean canPlace(Player player, org.bukkit.block.Block block) {
        return isCrystal() && !roundOver && !roundOut.contains(player.getUniqueId())
                && block.getType() == org.bukkit.Material.OBSIDIAN && inCombatArea(block.getLocation());
    }
    @Override public void onBlockPlaced(org.bukkit.event.block.BlockPlaceEvent event) {
        placedBlocks.putIfAbsent(event.getBlock().getLocation(), event.getBlockReplacedState());
    }
    @Override public boolean canBreak(Player player, org.bukkit.block.Block block) {
        return isCrystal() && !roundOver && !roundOut.contains(player.getUniqueId())
                && placedBlocks.containsKey(block.getLocation()) && inCombatArea(block.getLocation());
    }
    @Override public void onEntityPlace(org.bukkit.event.entity.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.EnderCrystal)) return;
        crystalOwners.keySet().removeIf(id -> game.world().getEntity(id) == null);
        if (!isCrystal() || roundOver || !game.isActive(event.getPlayer()) || roundOut.contains(event.getPlayer().getUniqueId())
                || !inCombatArea(event.getEntity().getLocation()) || crystalOwners.size() >= 128 && !crystalOwners.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true); return;
        }
        crystalOwners.put(event.getEntity().getUniqueId(), event.getPlayer().getUniqueId());
    }
    @Override public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.EnderCrystal)) return;
        if (!isCrystal() || roundOver || game.frozen() || game.state() != GameState.RUNNING
                || !crystalOwners.containsKey(event.getEntity().getUniqueId())) { event.setCancelled(true); return; }
        if (event instanceof EntityDamageByEntityEvent hit) {
            Player attacker = hit.getDamager() instanceof Player p ? p
                    : hit.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player p ? p : null;
            if (attacker != null) {
                if (!game.isActive(attacker) || roundOut.contains(attacker.getUniqueId())) { event.setCancelled(true); return; }
                crystalOwners.put(event.getEntity().getUniqueId(), attacker.getUniqueId());
            } else if (hit.getDamager() instanceof org.bukkit.entity.EnderCrystal source) {
                var owner = crystalOwners.get(source.getUniqueId());
                if (owner != null) crystalOwners.put(event.getEntity().getUniqueId(), owner);
            }
        }
    }
    @Override public void onDamage(Player victim, org.bukkit.event.entity.EntityDamageEvent event) {
        if (roundOver || roundOut.contains(victim.getUniqueId()) || game.frozen()) { event.setCancelled(true); return; }
        if (event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof org.bukkit.entity.EnderCrystal crystal) {
            var owner = game.player(crystalOwners.get(crystal.getUniqueId()));
            if (owner == null || !game.isActive(owner)) return;
            var team = game.teamOf(owner);
            if (!owner.equals(victim) && team != null && team.contains(victim)) { event.setCancelled(true); return; }
            if (!owner.equals(victim)) game.recordDamager(victim, owner);
        }
    }
    @Override public boolean allowsSelfDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        return game.mode().rulesId().equals("mace") && event instanceof EntityDamageByEntityEvent hit
                && hit.getDamager() instanceof org.bukkit.entity.WindCharge;
    }
    @Override public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (roundOver || game.frozen() || roundOut.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
        if (event.getItem() != null && event.getItem().getType() == org.bukkit.Material.END_CRYSTAL
                && (!isCrystal() || event.getClickedBlock() == null || !inCombatArea(event.getClickedBlock().getLocation()))) event.setCancelled(true);
    }
    private void resetArena() {
        botCombat.clear();
        if (game.world() != null) {
            crystalOwners.keySet().forEach(id -> { var entity = game.world().getEntity(id); if (entity != null) entity.remove(); });
            game.world().getEntitiesByClass(org.bukkit.entity.Projectile.class).forEach(org.bukkit.entity.Entity::remove);
            placedBlocks.values().forEach(state -> state.update(true, false));
        }
        crystalOwners.clear(); placedBlocks.clear();
    }
    @Override public void onCleanup() { roundRestartSeconds = 0; resetArena(); }

    @Override
    public void onDeath(Player victim, Player killer) {
        if (roundOver) return;
        roundOut.add(victim.getUniqueId());
        victim.teleport(game.arena().spectator(game.world()));
        victim.setGameMode(org.bukkit.GameMode.SPECTATOR);
        if (game.plugin().combat() != null) game.plugin().combat().leave(victim);
        List<Player> remaining = game.alivePlayers().stream()
                .filter(p -> !roundOut.contains(p.getUniqueId())).toList();
        if (game.mode().isTeams()) {
            if (remaining.stream().map(game::teamOf).distinct().count() > 1) return;
        } else if (remaining.size() > 1) return;
        roundOver = true;
        if (remaining.isEmpty()) { game.end(List.of(), "draw"); return; }
        Player winner = remaining.getFirst();
        var winningTeam = game.teamOf(winner);
        List<Player> winners = winningTeam == null ? List.of(winner)
                : game.players().stream().filter(winningTeam::contains).toList();
        int wins = winners.stream().mapToInt(p -> roundWins.merge(p.getUniqueId(), 1, Integer::sum)).max().orElse(0);
        game.broadcast("<yellow>" + (winningTeam == null ? winner.getName() : winningTeam.coloredName())
                + "</yellow> wins round <yellow>" + round + "</yellow>! <gray>(" + wins + "/" + roundsToWin() + ")");
        if (wins >= roundsToWin()) { game.end(winners, "rounds"); return; }
        round++;
        int expectedRound = round;
        roundRestartSeconds = 3;
        game.runLater(20L, () -> updateRoundCountdown(expectedRound, 2));
        game.runLater(40L, () -> updateRoundCountdown(expectedRound, 1));
        game.runLater(60L, () -> {
            // The match can be decided in this window (a disconnect ends it), and
            // restarting a round mid-ENDING wipes the winner's inventory.
            if (game.state() != GameState.RUNNING) return;
            startRound();
        });
    }

    @Override
    public void onTimeUp() {
        List<Player> players = game.alivePlayers();
        int best = players.stream().mapToInt(p -> roundWins.getOrDefault(p.getUniqueId(), 0)).max().orElse(0);
        List<Player> leaders = players.stream()
                .filter(p -> roundWins.getOrDefault(p.getUniqueId(), 0) == best).toList();
        boolean oneSide = game.mode().isTeams() ? leaders.stream().map(game::teamOf).distinct().count() == 1 : leaders.size() == 1;
        game.end(best > 0 && oneSide ? leaders : List.of(), "time");
    }

    @Override
    public void onQuit(Player player) {
        roundWins.remove(player.getUniqueId());
        botCombat.forget(player);
    }

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("<gray>Round: <white>" + round);
        for (Player p : game.players()) {
            lines.add("<gray>" + p.getName() + ": <white>" + roundWins.getOrDefault(p.getUniqueId(), 0));
        }
        return lines;
    }

    @Override
    public int maxTeams() { return 2; }

    @Override
    public boolean pvp() { return true; }

    @Override
    public boolean fallDamage() { return !isSumo(); }

    @Override
    public int timeLimitSeconds() { return 600; }
    private void updateRoundCountdown(int expectedRound, int seconds) {
        if (game.state() == GameState.RUNNING && roundOver && round == expectedRound && roundRestartSeconds > 0)
            roundRestartSeconds = seconds;
    }

    @Override
    public String phaseCountdown() {
        return roundRestartSeconds > 0 ? "Next round: " + roundRestartSeconds + "s" : null;
    }

}
