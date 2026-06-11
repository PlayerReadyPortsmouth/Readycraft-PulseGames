package uk.co.playerready.pulsegames.core.trivia;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spectator trivia: eliminated players get kid-friendly Minecraft questions in
 * chat while they wait, with token rewards for the first correct answer -
 * being knocked out stays fun instead of boring.
 */
public final class TriviaService implements Listener {

    private record Question(String question, String... answers) {}

    private static final List<Question> QUESTIONS = List.of(
            new Question("What do you need to tame a wolf?", "bone", "bones"),
            new Question("What mob explodes when it gets close to you?", "creeper"),
            new Question("What material is the strongest for tools?", "netherite"),
            new Question("What do you use to open a Nether portal?", "flint and steel", "flint"),
            new Question("What animal drops wool?", "sheep"),
            new Question("What do bees make?", "honey"),
            new Question("What block do you mine to get diamonds?", "diamond ore", "ore"),
            new Question("What mob shoots arrows at you?", "skeleton"),
            new Question("What do you ride with a saddle and a carrot on a stick?", "pig"),
            new Question("What is the boss of the End called?", "ender dragon", "dragon"),
            new Question("What fruit can you carve into a helmet?", "pumpkin"),
            new Question("What do chickens drop every few minutes?", "egg", "eggs"),
            new Question("What hostile mob can pick up blocks?", "enderman"),
            new Question("What do you give a horse to tame it faster?", "apple", "golden apple", "sugar"),
            new Question("What tool do you need to collect wool from sheep?", "shears"),
            new Question("What mob turns villagers into zombie villagers?", "zombie"),
            new Question("What is needed to brew potions?", "brewing stand"),
            new Question("Which ore gives you redstone?", "redstone ore", "redstone"),
            new Question("What do you call baby slimes' bigger relatives in the Nether?", "magma cube", "magma cubes"),
            new Question("What block lets you respawn in the Nether?", "respawn anchor"));

    private final PulseGamesPlugin plugin;
    private Question current;
    private long questionExpires;

    public TriviaService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::ask, 20L * 45, 20L * 45);
    }

    private List<Player> spectators() {
        List<Player> result = new ArrayList<>();
        for (GameInstance instance : plugin.instances().all()) {
            for (Player p : instance.everyone()) {
                if (instance.isSpectator(p)) result.add(p);
            }
        }
        return result;
    }

    private void ask() {
        current = null;
        List<Player> spectators = spectators();
        if (spectators.size() < 1) return;
        current = QUESTIONS.get(ThreadLocalRandom.current().nextInt(QUESTIONS.size()));
        questionExpires = System.currentTimeMillis() + 25_000;
        for (Player p : spectators) {
            p.sendMessage(Text.msg("<light_purple><b>TRIVIA!</b></light_purple> <white>" + current.question()
                    + " <gray>(answer in chat - first correct wins tokens!)"));
            if (!Text.calm(p)) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Question question = current;
        if (question == null || System.currentTimeMillis() > questionExpires) return;
        Player player = event.getPlayer();
        GameInstance instance = plugin.instances().byPlayer(player);
        if (instance == null || !instance.isSpectator(player)) return;
        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).toLowerCase(Locale.ROOT).trim();
        for (String answer : question.answers()) {
            if (message.contains(answer)) {
                current = null;
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.economy().addTokens(player, 50, "trivia");
                    for (Player p : spectators()) {
                        p.sendMessage(Text.msg("<light_purple>Trivia:</light_purple> <yellow>" + player.getName()
                                + "</yellow> <gray>got it - <white>" + question.answers()[0] + "</white>!"));
                    }
                });
                return;
            }
        }
    }
}
