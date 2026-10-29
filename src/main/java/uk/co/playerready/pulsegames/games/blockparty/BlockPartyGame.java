package uk.co.playerready.pulsegames.games.blockparty;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Block Party: the floor turns to colors, stand on the called color before the rest vanishes.
 * Modes: classic, hardcore (faster, more colors).
 */
public final class BlockPartyGame extends MiniGame implements uk.co.playerready.pulsegames.core.bot.BotMovement {

    private static final Material[] COLORS = {
            Material.RED_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL, Material.YELLOW_WOOL,
            Material.PURPLE_WOOL, Material.ORANGE_WOOL, Material.PINK_WOOL, Material.CYAN_WOOL,
            Material.WHITE_WOOL, Material.LIGHT_BLUE_WOOL
    };

    /** Same ten colours as distinct glazed-terracotta patterns, for players who cannot tell the wool apart. */
    private static final Material[] PATTERNS = {
            Material.RED_GLAZED_TERRACOTTA, Material.BLUE_GLAZED_TERRACOTTA, Material.LIME_GLAZED_TERRACOTTA,
            Material.YELLOW_GLAZED_TERRACOTTA, Material.PURPLE_GLAZED_TERRACOTTA, Material.ORANGE_GLAZED_TERRACOTTA,
            Material.PINK_GLAZED_TERRACOTTA, Material.CYAN_GLAZED_TERRACOTTA, Material.WHITE_GLAZED_TERRACOTTA,
            Material.LIGHT_BLUE_GLAZED_TERRACOTTA
    };

    /** A shape per colour so the call never depends on telling colours apart. */
    static final String[] SYMBOLS = {"▲", "■", "●", "★", "◆", "♠", "♥", "♣", "♦", "▼"};

    /** Fills the hotbar with the called colour; a field so tests need not build item stacks. */
    private java.util.function.Consumer<Player> hintDelivery = player -> {
        player.getInventory().clear();
        for (int i = 0; i < 9; i++) player.getInventory().setItem(i, new ItemStack(this.target));
    };

    private enum Phase { SHUFFLE, STAND, CLEARED }

    private Cuboid floor;
    private Phase phase = Phase.SHUFFLE;
    private int phaseTimer = 3;
    private int round = 0;
    private Material target;
    private DanceFloor.Pattern lastPattern;
    private int callSeconds;
    private final java.util.Map<java.util.UUID, org.bukkit.Location> botGoals = new java.util.HashMap<>();

    public BlockPartyGame(GameInstance game) {
        super(game);
    }

    private Material[] colors() {
        // Arena setting, or any human in the round who chose colour patterns in their settings.
        boolean patterns = game.arena().settings().getBoolean("colour-patterns", false)
                || uk.co.playerready.pulsegames.core.settings.PlayerSettings.anyEnabled(
                        game.players().stream().filter(p -> !game.isBot(p)).toList(),
                        uk.co.playerready.pulsegames.core.settings.Comfort.COLOUR_PATTERNS);
        return patterns ? PATTERNS : COLORS;
    }

    /** Colour name for the call, the same whichever floor material is in use. */
    static String colourName(Material material) {
        return material.name().replace("_GLAZED_TERRACOTTA", "").replace("_WOOL", "").replace('_', ' ');
    }

    /** The target colour as "▲ RED", the non-colour cue shown on the action bar. */
    static String callLabel(int colourIndex, Material material) {
        return SYMBOLS[Math.floorMod(colourIndex, SYMBOLS.length)] + " " + colourName(material);
    }

    /** Hardcore no longer hides the hotbar hint; it shows it for a shorter time instead. */
    static int hintSeconds(boolean hardcore, int round, int callSeconds) {
        return hardcore && round > 5 ? Math.min(callSeconds, 2) : callSeconds;
    }

    private boolean isHardcore() { return game.mode().id().equals("hardcore"); }

    private int maxRounds() { return game.arena().settings().getInt("rounds", 15); }

    @Override public String startProblem(List<Player> players) {
        Cuboid region = game.arena().region("floor");
        if (region == null || region.minY() != region.maxY() || region.volume() > 4096)
            return "Block Party needs a one-block-high floor of at most 4096 tiles";
        return null;
    }

    private int standSeconds() {
        int base = isHardcore() ? 5 : 7;
        return Math.max(isHardcore() ? 1 : 2, base - round / 2);
    }

    @Override
    public void onStart() {
        floor = game.arena().region("floor");
        game.broadcast("Stand on the called color before the floor vanishes!");
        shuffleFloor();
    }

    @Override
    public void onSecond(int gameTime) {
        if (floor == null || --phaseTimer > 0) {
            if (phase == Phase.STAND && phaseTimer > 0) {
                int step = callSeconds - phaseTimer;
                float pitch = uk.co.playerready.pulsegames.games.common.HazardFeedback.risingPitch(step, callSeconds);
                String label = callLabel(java.util.Arrays.asList(colors()).indexOf(target), target);
                for (Player p : game.alivePlayers()) {
                    p.setLevel(phaseTimer);
                    p.playSound(p.getLocation(), "minecraft:block.note_block.hat", 0.8f, pitch);
                    p.sendActionBar(Text.mm("<yellow>Stand on <white>" + label + " <gray>(" + phaseTimer + ")"));
                }
            }
            return;
        }
        switch (phase) {
            case SHUFFLE -> {
                round++;
                if (round > maxRounds()) {
                    game.end(game.alivePlayers(), "survived");
                    return;
                }
                if (!pickTarget()) return;
                phase = Phase.STAND;
                phaseTimer = callSeconds;
            }
            case STAND -> {
                clearFloor();
                phase = Phase.CLEARED;
                phaseTimer = 3;
            }
            case CLEARED -> {
                shuffleFloor();
                phase = Phase.SHUFFLE;
                phaseTimer = 2;
            }
        }
    }

    private void shuffleFloor() {
        botGoals.clear();
        int width=floor.maxX()-floor.minX()+1,depth=floor.maxZ()-floor.minZ()+1;
        var layout=DanceFloor.create(width,depth,round+1,isHardcore(),lastPattern,ThreadLocalRandom.current());
        lastPattern=layout.pattern();
        for(int x=0;x<width;x++) for(int z=0;z<depth;z++)
            game.world().getBlockAt(floor.minX()+x,floor.minY(),floor.minZ()+z)
                    .setType(colors()[layout.colors()[x*depth+z]],false);
    }

    private boolean pickTarget() {
        botGoals.clear();
        int width=floor.maxX()-floor.minX()+1,depth=floor.maxZ()-floor.minZ()+1;
        int[] colors=new int[width*depth]; boolean[] clear=new boolean[colors.length];
        java.util.Arrays.fill(colors,-1);
        for(int x=0;x<width;x++) for(int z=0;z<depth;z++) {
            int i=x*depth+z,wx=floor.minX()+x,wz=floor.minZ()+z,y=floor.maxY();
            Material material=game.world().getBlockAt(wx,y,wz).getType();
            colors[i]=java.util.Arrays.asList(colors()).indexOf(material);
            clear[i]=colors[i]>=0 && game.world().getBlockAt(wx,y+1,wz).isPassable()
                    && game.world().getBlockAt(wx,y+2,wz).isPassable();
        }
        var starts=new java.util.ArrayList<Integer>();
        for(Player player:List.copyOf(game.alivePlayers())) {
            var at=player.getLocation();
            // A contestant already off the dance floor must not abort everybody else's match.
            if(at.getWorld()!=game.world() || !floor.containsColumn(at) || at.getY()<floor.maxY()+0.5
                    || at.getY()>floor.maxY()+3) {
                game.eliminate(player);
                if(game.state()!=uk.co.playerready.pulsegames.core.game.GameState.RUNNING) return false;
                continue;
            }
            starts.add((at.getBlockX()-floor.minX())*depth+at.getBlockZ()-floor.minZ());
        }
        if(starts.isEmpty()) return false;
        var call=DanceFloor.choose(width,depth,colors,clear,starts,standSeconds(),
                java.util.Arrays.asList(colors()).indexOf(target),ThreadLocalRandom.current());
        if(call==null) {
            game.broadcast("<red>No fair reachable colour on this floor. Check spawns, obstacles and floor size.");
            game.end(List.of(),"invalid-floor");return false;
        }
        target=colors()[call.color()];callSeconds=call.seconds();
        String label = callLabel(call.color(), target);
        game.broadcast("<yellow><b>" + label + "!</b></yellow> <gray>(" + callSeconds + "s)");
        int hint = hintSeconds(isHardcore(), round, callSeconds);
        final int calledRound = round;
        if (hint < callSeconds) {
            game.runLater(hint * 20L, () -> {
                if (round != calledRound || phase != Phase.STAND) return;
                game.alivePlayers().forEach(p -> p.getInventory().clear());
            });
        }
        for (Player player : game.alivePlayers()) {
            Text.title(player, "<yellow><b>" + label, "<gray>Round " + round + "/" + maxRounds() + " · "
                    + game.alivePlayers().size() + " left");
            player.playSound(player.getLocation(), "minecraft:block.note_block.pling", 1f, 1.4f);
            hintDelivery.accept(player);
        }
        return true;
    }

    private void clearFloor() {
        for (Block block : floor.blocks(game.world())) {
            if (block.getType() != target && !block.getType().isAir()) {
                block.setType(Material.AIR);
            }
        }
        game.alivePlayers().forEach(p ->
                {
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.8f);
            p.playSound(p.getLocation(), "minecraft:block.note_block.bass", 1.2f, 0.5f);
        });
    }

    @Override
    public org.bukkit.Location botDestination(Player bot) {
        if (floor == null || target == null || phase == Phase.SHUFFLE) return null;
        var cached = botGoals.get(bot.getUniqueId());
        if (cached != null) return cached.clone();
        var here = bot.getLocation();
        org.bukkit.Location best = null;
        double distance = Double.MAX_VALUE;
        int y = floor.maxY();
        // The called colour is public, including when Hardcore hides the inventory hint.
        // The validated floor is bounded to 4096 cells; don't miss a fair distant patch.
        for (int x = floor.minX(); x <= floor.maxX(); x++) {
            for (int z = floor.minZ(); z <= floor.maxZ(); z++) {
                if (game.world().getBlockAt(x, y, z).getType() != target
                        || !game.world().getBlockAt(x, y + 1, z).isPassable()
                        || !game.world().getBlockAt(x, y + 2, z).isPassable()) continue;
                var point = new org.bukkit.Location(game.world(), x + 0.5, y + 1, z + 0.5);
                // Stable per-bot preference breaks equally good choices without changing the public colour cue.
                double candidate = here.distanceSquared(point)
                        + uk.co.playerready.pulsegames.core.util.BotChoiceVariation.preference(bot.getUniqueId(), x, z, round);
                if (candidate < distance) { best = point; distance = candidate; }
            }
        }
        if (best != null) botGoals.put(bot.getUniqueId(), best.clone());
        return best;
    }

    @Override public void onCleanup() { botGoals.clear(); }
    @Override public MiniGame.Cue eliminationSound() {
        return uk.co.playerready.pulsegames.games.common.HazardFeedback.LOUD_ELIMINATION;
    }
    @Override public void onEliminated(Player player) {
        uk.co.playerready.pulsegames.games.common.HazardFeedback.finalThree(game.players(), game.alivePlayers().size(), game.players().size());
    }
    @Override public void onQuit(Player player) { botGoals.remove(player.getUniqueId()); }

    @Override
    public void onDeath(Player victim, Player killer) {
        game.broadcast("<red>" + victim.getName() + "</red> fell in round <yellow>" + round + "</yellow>!");
        game.eliminate(victim);
    }

    @Override
    public List<String> sidebar(Player player) {
        return List.of(
                "<gray>Round: <white>" + round + "/" + maxRounds(),
                "<gray>Alive: <white>" + game.alivePlayers().size());
    }

    /** Still on the floor when the clock runs out counts as surviving, as it does after the last round. */
    @Override
    public boolean survivorsWinOnTimeout() { return true; }

    @Override
    public int timeLimitSeconds() { return 600; }
    @Override
    public String phaseCountdown() { if (phaseTimer <= 0) return null;
        return (phase == Phase.STAND ? "Floor drops: " : phase == Phase.CLEARED ? "Floor returns: " : "Next colour: ") + phaseTimer + "s"; }

}
