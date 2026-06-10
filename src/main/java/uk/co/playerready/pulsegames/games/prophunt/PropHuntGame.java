package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Prop Hunt (Hide & Seek): hiders are invisible with a disguise block on their head;
 * seekers hunt them down before time runs out.
 * Modes: classic (found = spectate), infection (found = join the seekers).
 */
public final class PropHuntGame extends MiniGame {

    private static final Material[] DISGUISES = {
            Material.HAY_BLOCK, Material.BARREL, Material.CRAFTING_TABLE, Material.BOOKSHELF,
            Material.MELON, Material.PUMPKIN, Material.CACTUS, Material.OAK_LOG, Material.TNT
    };

    private final Set<UUID> seekers = new HashSet<>();
    private final Set<UUID> hiders = new HashSet<>();
    private int hidePhase;
    private Location seekerHold;

    public PropHuntGame(GameInstance game) {
        super(game);
    }

    private boolean isInfection() { return game.mode().id().equals("infection"); }

    @Override
    public void onStart() {
        hidePhase = game.arena().settings().getInt("hide-seconds", 30);
        List<Player> players = new ArrayList<>(game.alivePlayers());
        Collections.shuffle(players);
        int seekerCount = Math.max(1, players.size() / 5);
        seekerHold = game.arena().spawn(0, game.world());
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (i < seekerCount) {
                seekers.add(player.getUniqueId());
                player.teleport(seekerHold);
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, hidePhase * 20, 0));
                Text.title(player, "<red><b>SEEKER", "<gray>Released in " + hidePhase + "s...");
            } else {
                hiders.add(player.getUniqueId());
                Material disguise = DISGUISES[random.nextInt(DISGUISES.length)];
                player.getInventory().setHelmet(new ItemStack(disguise));
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                        PotionEffect.INFINITE_DURATION, 0, false, false));
                ItemStack taunt = new ItemStack(Material.NOTE_BLOCK);
                taunt.editMeta(meta -> meta.displayName(Text.mm("<yellow><b>Taunt</b> <gray>(right-click... if you dare)")));
                player.getInventory().setItem(4, taunt);
                Text.title(player, "<green><b>HIDER", "<gray>Disguise: " + disguise.name().toLowerCase().replace('_', ' '));
            }
        }
        game.broadcast("<yellow>" + seekerCount + " seeker(s)</yellow> will be released in <red>"
                + hidePhase + "s</red>. Hide!");
    }

    @Override
    public void onSecond(int gameTime) {
        if (hidePhase > 0) {
            hidePhase--;
            // Hold seekers at spawn during the hide phase.
            for (UUID id : seekers) {
                Player seeker = game.plugin().getServer().getPlayer(id);
                if (seeker != null && seeker.getLocation().distanceSquared(seekerHold) > 4) {
                    seeker.teleport(seekerHold);
                }
            }
            if (hidePhase == 0) {
                game.broadcast("<red><b>The seekers have been released!");
                for (UUID id : seekers) {
                    Player seeker = game.plugin().getServer().getPlayer(id);
                    if (seeker != null) equipSeeker(seeker);
                }
            }
        }
    }

    private void equipSeeker(Player seeker) {
        seeker.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        seeker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
        seeker.playSound(seeker.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.2f);
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.NOTE_BLOCK
                && hiders.contains(event.getPlayer().getUniqueId()) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            game.world().playSound(player.getLocation(), Sound.ENTITY_CHICKEN_AMBIENT, 2f, 1f);
            player.sendActionBar(Text.mm("<yellow>Bok bok... was that wise?"));
        }
    }

    @Override
    public void onDamageByPlayer(Player victim, Player damager, EntityDamageByEntityEvent event) {
        boolean seekerHitsHider = seekers.contains(damager.getUniqueId()) && hiders.contains(victim.getUniqueId());
        if (!seekerHitsHider || hidePhase > 0) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        hiders.remove(victim.getUniqueId());
        if (isInfection()) {
            seekers.add(victim.getUniqueId());
            game.plugin().playerState().reset(victim);
            victim.teleport(game.arena().spawn(0, game.world()));
            equipSeeker(victim);
            game.broadcast("<red>" + victim.getName() + "</red> was found and joins the seekers! <gray>("
                    + hiders.size() + " hiders left)");
        } else {
            game.broadcast("<red>" + victim.getName() + "</red> was found! <gray>(" + hiders.size() + " hiders left)");
            game.eliminate(victim);
        }
        if (hiders.isEmpty()) {
            game.broadcast("<red><b>All hiders found - seekers win!");
            game.end(game.alivePlayers().stream().filter(p -> seekers.contains(p.getUniqueId())).toList(), "all-found");
        }
    }

    /** Hiders survive the clock: hiders win. */
    @Override
    public void onTimeUp() {
        game.broadcast("<green><b>Time's up - the hiders win!");
        game.end(game.alivePlayers().stream().filter(p -> hiders.contains(p.getUniqueId())).toList(), "time");
    }

    @Override
    public List<String> sidebar(Player player) {
        return List.of(
                "<gray>Role: " + (seekers.contains(player.getUniqueId()) ? "<red>Seeker" : "<green>Hider"),
                "<gray>Hiders left: <green>" + hiders.size(),
                "<gray>Time left: <white>" + Text.time(Math.max(0, timeLimitSeconds() - game.gameTime())));
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 300);
    }
}
