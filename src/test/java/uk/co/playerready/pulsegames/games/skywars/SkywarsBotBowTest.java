package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkywarsBotBowTest {
    static final class Fixture {
        final GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);
        final Player bot=mock(Player.class),enemy=mock(Player.class); final World world=mock(World.class);
        final PlayerInventory inventory=mock(PlayerInventory.class); final Arrow arrow=mock(Arrow.class);
        final Map<Integer,ItemStack> slots=new HashMap<>(); final SkywarsBotBow bow=new SkywarsBotBow();
        Fixture() {
            when(game.state()).thenReturn(GameState.RUNNING); when(game.isActive(any())).thenReturn(true); when(game.isBot(bot)).thenReturn(true);
            when(game.teamOf(bot)).thenReturn(null); when(bot.getUniqueId()).thenReturn(UUID.randomUUID()); when(enemy.getUniqueId()).thenReturn(UUID.randomUUID());
            when(bot.getWorld()).thenReturn(world); when(enemy.getWorld()).thenReturn(world); when(bot.hasLineOfSight(enemy)).thenReturn(true); when(bot.canSee(enemy)).thenReturn(true);
            when(bot.getLocation()).thenReturn(new Location(world,0,64,0)); when(bot.getEyeLocation()).thenReturn(new Location(world,0,65.62,0));
            when(enemy.getLocation()).thenReturn(new Location(world,0,64,10)); when(enemy.getEyeLocation()).thenReturn(new Location(world,0,65.62,10));
            when(bot.getInventory()).thenReturn(inventory); when(inventory.getItem(anyInt())).thenAnswer(i->slots.get(i.getArgument(0)));
            doAnswer(i->{slots.put(i.getArgument(0),i.getArgument(1));return null;}).when(inventory).setItem(anyInt(),nullable(ItemStack.class));
            slots.put(0,SkywarsTestItems.stack(Material.BOW)); slots.put(1,SkywarsTestItems.stack(Material.ARROW,3));
            when(bot.launchProjectile(eq(Arrow.class),any(org.bukkit.util.Vector.class))).thenReturn(arrow); when(arrow.isValid()).thenReturn(true);
        }
        boolean tick(long tick) { return bow.combat(game,bot,enemy,tick,BotDifficulty.NORMAL); }
    }
    @Test void waitsFullDrawThenLaunchesRealArrowAndConsumesExactlyOne() {
        var f=new Fixture(); assertTrue(f.tick(0)); assertTrue(f.tick(19));
        verify(f.bot,never()).launchProjectile(eq(Arrow.class),any(org.bukkit.util.Vector.class));
        assertTrue(f.tick(20)); assertEquals(2,f.slots.get(1).getAmount());
        verify(f.bot).launchProjectile(eq(Arrow.class),any(org.bukkit.util.Vector.class)); verify(f.arrow,never()).remove();
    }
    @Test void cancelledShootOrChangedAmmoRemovesProjectileWithoutAdditionalConsumption() {
        var f=new Fixture(); var manager=f.game.plugin().getServer().getPluginManager();
        doAnswer(i->{((EntityShootBowEvent)i.getArgument(0)).setCancelled(true);return null;}).when(manager).callEvent(any(EntityShootBowEvent.class));
        f.tick(0); f.tick(20); verify(f.arrow).remove(); assertEquals(3,f.slots.get(1).getAmount());
        reset(f.arrow); when(f.arrow.isValid()).thenReturn(true);
        doAnswer(i->{f.slots.put(1,SkywarsTestItems.stack(Material.ARROW,1));return null;}).when(manager).callEvent(any(EntityShootBowEvent.class));
        f.tick(40); verify(f.arrow).remove(); assertEquals(1,f.slots.get(1).getAmount());
    }
    @Test void missingAmmoHiddenAndFriendlyOpponentsNeverLaunch() {
        var f=new Fixture(); f.slots.remove(1); assertFalse(f.tick(0));
        f.slots.put(1,SkywarsTestItems.stack(Material.ARROW)); when(f.bot.hasLineOfSight(f.enemy)).thenReturn(false); assertFalse(f.tick(20));
        when(f.bot.hasLineOfSight(f.enemy)).thenReturn(true); var team=mock(uk.co.playerready.pulsegames.core.team.GameTeam.class);
        when(f.game.teamOf(f.bot)).thenReturn(team); when(team.contains(f.enemy)).thenReturn(true); assertFalse(f.tick(40));
        verify(f.bot,never()).launchProjectile(eq(Arrow.class),any(org.bukkit.util.Vector.class));
    }
}
