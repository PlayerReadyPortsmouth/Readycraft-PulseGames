package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Bots may fight a Decoy Dummy (or a hunting defender or bug) in Bedwars, and nowhere else. */
class BotServiceBedwarsFoesTest {
    private record Rig(BotService bots, BotActor actor, ArmorStand decoy) {}

    private Rig rig(MiniGame logic, boolean bedwars) {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class);
        BotProvider provider = mock(BotProvider.class);
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        World world = mock(World.class);
        Player human = player("Human");
        List<Player> roster = new ArrayList<>(List.of(human));
        YamlConfiguration config = new YamlConfiguration();
        config.set("bots.enabled", true); config.set("bots.wait-seconds", 2);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.canUseNewFeatures(any())).thenReturn(true);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(game.players()).thenAnswer(c -> List.copyOf(roster));
        when(game.humanCount()).thenReturn(1);
        when(game.type().id()).thenReturn("duels");
        when(game.mode().id()).thenReturn("classic");
        when(game.mode().rulesId()).thenReturn("classic");
        when(game.state()).thenReturn(GameState.WAITING);
        when(game.arena().settings().getBoolean("bots-enabled", false)).thenReturn(true);
        when(game.minPlayers()).thenReturn(2); when(game.maxPlayers()).thenReturn(2);
        when(game.world()).thenReturn(world);
        when(game.arena().lobby(world)).thenReturn(new Location(world, 0, 70, 0));
        when(game.add(any())).thenAnswer(c -> { roster.add(c.getArgument(0)); return true; });
        BotService bots = new BotService(plugin, provider, bound -> 0);
        BotActor actor = mock(BotActor.class);
        Player bot = player("Bot");
        when(actor.player()).thenReturn(bot); when(actor.valid()).thenReturn(true);
        when(provider.create(anyString(), any())).thenReturn(actor);
        when(bot.getLocation()).thenReturn(new Location(world, 0, 70, 0));
        when(bot.getEyeLocation()).thenReturn(new Location(world, 0, 71.6, 0));
        when(bot.getWorld()).thenReturn(world);
        when(bot.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
        when(bot.hasLineOfSight(any(org.bukkit.entity.Entity.class))).thenReturn(true);
        when(bot.getAttackCooldown()).thenReturn(1f);
        ArmorStand decoy = mock(ArmorStand.class);
        when(decoy.getUniqueId()).thenReturn(UUID.randomUUID());
        when(decoy.isValid()).thenReturn(true);
        when(decoy.getWorld()).thenReturn(world);
        when(decoy.getLocation()).thenReturn(new Location(world, 2, 70, 0));
        when(decoy.getEyeLocation()).thenReturn(new Location(world, 2, 71, 0));
        when(decoy.getName()).thenReturn("Red Decoy");
        when(logic.acceptsDeath(bot)).thenReturn(true);
        when(logic.voidY()).thenReturn(0.0);
        when(game.logic()).thenReturn(logic);
        if (bedwars) when(((uk.co.playerready.pulsegames.games.bedwars.BedwarsGame) logic).botFoe(eq(bot), anyLong(), any())).thenReturn(decoy);
        bots.reconcile(game); bots.reconcile(game);
        when(game.state()).thenReturn(GameState.RUNNING);
        when(game.isActive(bot)).thenReturn(true);
        when(game.alivePlayers()).thenReturn(List.of(bot, human));
        return new Rig(bots, actor, decoy);
    }

    private static Player player(String name) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getName()).thenReturn(name);
        return p;
    }

    @Test void aBedwarsBotAttacksTheDecoyItHasFallenFor() {
        Rig r = rig(mock(uk.co.playerready.pulsegames.games.bedwars.BedwarsGame.class), true);
        for (int i = 0; i < 6; i++) r.bots().think();
        verify(r.actor().player(), atLeastOnce()).attack(r.decoy());
    }

    @Test void otherGamesNeverTargetAnythingButPlayers() {
        Rig r = rig(mock(uk.co.playerready.pulsegames.games.skywars.SkywarsGame.class), false);
        for (int i = 0; i < 6; i++) r.bots().think();
        verify(r.actor().player(), never()).attack(any(org.bukkit.entity.Entity.class));
    }
}
