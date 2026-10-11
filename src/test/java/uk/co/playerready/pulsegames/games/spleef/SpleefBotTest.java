package uk.co.playerready.pulsegames.games.spleef;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SpleefBotTest {
    @Test void launcherUsesRealProjectileAndSharedCooldownIncludingOffhand() {
        GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);
        Player bot=mock(Player.class);PlayerInventory inv=mock(PlayerInventory.class);World world=mock(World.class);
        when(game.mode().id()).thenReturn("splegg");when(game.state()).thenReturn(GameState.RUNNING);
        when(game.isActive(bot)).thenReturn(true);when(game.world()).thenReturn(world);when(bot.getWorld()).thenReturn(world);
        when(bot.getInventory()).thenReturn(inv);
        ItemStack air=mock(ItemStack.class),hoe=mock(ItemStack.class);
        when(air.getType()).thenReturn(Material.AIR);when(hoe.getType()).thenReturn(Material.GOLDEN_HOE);
        when(inv.getItemInMainHand()).thenReturn(air);when(inv.getItemInOffHand()).thenReturn(hoe);
        SpleefGame logic=new SpleefGame(game,bound->0);
        assertTrue(logic.throwEgg(bot,new Vector(1,0,0)));
        verify(bot).setCooldown(Material.GOLDEN_HOE,10);verify(bot).launchProjectile(Egg.class,new Vector(2,0,0));
        when(bot.hasCooldown(Material.GOLDEN_HOE)).thenReturn(true);
        assertFalse(logic.throwEgg(bot,new Vector(1,0,0)));
        verify(bot,times(1)).launchProjectile(eq(Egg.class),any(Vector.class));
        when(bot.hasCooldown(Material.GOLDEN_HOE)).thenReturn(false);when(game.isActive(bot)).thenReturn(false);
        assertFalse(logic.throwEgg(bot,new Vector(1,0,0)));
    }
}
