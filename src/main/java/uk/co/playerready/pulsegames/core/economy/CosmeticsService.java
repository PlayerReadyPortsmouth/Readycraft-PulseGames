package uk.co.playerready.pulsegames.core.economy;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** The cosmetic catalog and the effect implementations behind each unlock. */
public final class CosmeticsService {

    public static final List<Cosmetic> CATALOG = List.of(
            // Kill effects (played where your victim fell)
            new Cosmetic("ke-lightning", Cosmetic.Category.KILL_EFFECT, "Lightning Strike",
                    Material.LIGHTNING_ROD, 400, "A bolt from the blue marks your kill"),
            new Cosmetic("ke-hearts", Cosmetic.Category.KILL_EFFECT, "Heartbreaker",
                    Material.POPPY, 250, "Defeated with love"),
            new Cosmetic("ke-explosion", Cosmetic.Category.KILL_EFFECT, "Boom",
                    Material.TNT, 350, "An explosive finish"),
            new Cosmetic("ke-ink", Cosmetic.Category.KILL_EFFECT, "Ink Splat",
                    Material.INK_SAC, 200, "Splat!"),
            // Victory effects (during your win celebration)
            new Cosmetic("we-totem", Cosmetic.Category.WIN_EFFECT, "Totem Shower",
                    Material.TOTEM_OF_UNDYING, 450, "Golden sparkles rain over you"),
            new Cosmetic("we-dragon", Cosmetic.Category.WIN_EFFECT, "Dragon's Roar",
                    Material.DRAGON_HEAD, 600, "Roar like the Ender Dragon"),
            new Cosmetic("we-notes", Cosmetic.Category.WIN_EFFECT, "Victory Tune",
                    Material.NOTE_BLOCK, 300, "Musical celebration"),
            // Lobby trails
            new Cosmetic("tr-flame", Cosmetic.Category.TRAIL, "Flame Trail",
                    Material.BLAZE_POWDER, 350, "Leave fire in your footsteps"),
            new Cosmetic("tr-hearts", Cosmetic.Category.TRAIL, "Heart Trail",
                    Material.PINK_DYE, 350, "Spread the love"),
            new Cosmetic("tr-rainbow", Cosmetic.Category.TRAIL, "Rainbow Trail",
                    Material.RED_STAINED_GLASS, 600, "Taste the rainbow"),
            new Cosmetic("tr-notes", Cosmetic.Category.TRAIL, "Music Trail",
                    Material.NOTE_BLOCK, 300, "Dance through the lobby"),
            // In-match flair: this trail and the two victory sounds are visible during play / the celebration
            new Cosmetic("tr-sparkle", Cosmetic.Category.TRAIL, "Sparkle Trail",
                    Material.END_ROD, 300, "Sparkles that follow you in Party Games and Parkour too"),
            new Cosmetic("we-fanfare", Cosmetic.Category.WIN_EFFECT, "Victory Fanfare",
                    Material.GOLD_INGOT, 250, "A triumphant fanfare when you win"),
            new Cosmetic("we-bells", Cosmetic.Category.WIN_EFFECT, "Victory Bells",
                    Material.BELL, 250, "Bright bells ring out when you win"),
            // Kit unlocks (selectable in Kit PvP FFA)
            new Cosmetic("kit-tank", Cosmetic.Category.KIT, "Tank Kit",
                    Material.NETHERITE_CHESTPLATE, 800, "Heavy armor, slow but unstoppable"),
            new Cosmetic("kit-archer", Cosmetic.Category.KIT, "Archer Kit",
                    Material.BOW, 800, "Power bow and mobility"));

    /** Games where an equipped trail also shows during the match, not just in the lobby. */
    public static boolean trailInMatch(String gameId) {
        return gameId.equals("partygames") || gameId.equals("parkour");
    }

    public static Cosmetic byId(String id) {
        return CATALOG.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    public static List<Cosmetic> byCategory(Cosmetic.Category category) {
        return CATALOG.stream().filter(c -> c.category() == category).toList();
    }

    private final EconomyService economy;
    /** One lightning flash per world per five seconds, however many equipped killers there are. */
    private final java.util.Map<java.util.UUID, uk.co.playerready.pulsegames.core.util.RateCap> lightning = new java.util.HashMap<>();

    public CosmeticsService(EconomyService economy) {
        this.economy = economy;
    }

    public void playKillEffect(Player killer, Location victimLocation) {
        String id = economy.equipped(killer, Cosmetic.Category.KILL_EFFECT);
        if (id == null) return;
        var world = victimLocation.getWorld();
        switch (id) {
            case "ke-lightning" -> {
                if (lightning.computeIfAbsent(world.getUID(), k -> new uk.co.playerready.pulsegames.core.util.RateCap(1, 5000))
                        .allow(System.currentTimeMillis())) world.strikeLightningEffect(victimLocation);
                else world.spawnParticle(Particle.CRIT, victimLocation.clone().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0.05);
            }
            case "ke-hearts" -> world.spawnParticle(Particle.HEART, victimLocation.clone().add(0, 1, 0),
                    15, 0.5, 0.7, 0.5);
            case "ke-explosion" -> {
                world.spawnParticle(Particle.EXPLOSION, victimLocation, 2);
                world.playSound(victimLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
            }
            case "ke-ink" -> world.spawnParticle(Particle.SQUID_INK, victimLocation.clone().add(0, 1, 0),
                    25, 0.4, 0.6, 0.4, 0.05);
            default -> { }
        }
    }

    /** Called repeatedly during the win celebration. */
    public void playWinEffect(Player winner) {
        String id = economy.equipped(winner, Cosmetic.Category.WIN_EFFECT);
        if (id == null) return;
        var world = winner.getWorld();
        Location loc = winner.getLocation();
        switch (id) {
            case "we-totem" -> world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                    loc.clone().add(0, 2, 0), 40, 0.6, 1, 0.6, 0.15);
            case "we-dragon" -> {
                world.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(0, 1, 0), 30, 0.8, 0.8, 0.8, 0.02);
                world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.3f);
            }
            case "we-notes" -> {
                world.spawnParticle(Particle.NOTE, loc.clone().add(0, 2, 0), 12, 0.8, 0.5, 0.8);
                world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f,
                        0.6f + ThreadLocalRandom.current().nextFloat());
            }
            case "we-fanfare" -> {
                world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1.2f);
            }
            case "we-bells" -> {
                world.playSound(loc, Sound.BLOCK_BELL_USE, 1f, 1.2f);
                world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
            }
            default -> { }
        }
    }

    /** Called on lobby movement. */
    public void playTrail(Player player) {
        String id = economy.equipped(player, Cosmetic.Category.TRAIL);
        if (id == null) return;
        var world = player.getWorld();
        Location loc = player.getLocation().add(0, 0.2, 0);
        switch (id) {
            case "tr-flame" -> world.spawnParticle(Particle.FLAME, loc, 3, 0.1, 0.05, 0.1, 0.01);
            case "tr-hearts" -> world.spawnParticle(Particle.HEART, loc, 1, 0.2, 0.1, 0.2);
            case "tr-sparkle" -> world.spawnParticle(Particle.END_ROD, loc, 2, 0.15, 0.1, 0.15, 0.01);
            case "tr-notes" -> world.spawnParticle(Particle.NOTE, loc, 2, 0.2, 0.1, 0.2);
            case "tr-rainbow" -> {
                var random = ThreadLocalRandom.current();
                Color color = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
                world.spawnParticle(Particle.DUST, loc, 5, 0.2, 0.1, 0.2,
                        new Particle.DustOptions(color, 1.2f));
            }
            default -> { }
        }
    }

    /** Equipped, unlocked Kit PvP kit id (e.g. "tank"), or null for the default. */
    public String equippedKitId(Player player) {
        String id = economy.equipped(player, Cosmetic.Category.KIT);
        if (id == null || !economy.isUnlocked(player, id)) return null;
        return id.replace("kit-", "");
    }
}
