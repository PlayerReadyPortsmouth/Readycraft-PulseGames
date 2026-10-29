package uk.co.playerready.pulsegames.games.kitpvp;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KitPvpBotTest {
    private final GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
    private final Player bot = mock(Player.class), target = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final World world = mock(World.class);
    private final Arrow arrow = mock(Arrow.class);
    private final KitPvpGame logic = new KitPvpGame(game);
    KitPvpBotTest() {
        when(game.mode().rulesId()).thenReturn("oitc");
        when(game.state()).thenReturn(GameState.RUNNING);
        when(game.isBot(bot)).thenReturn(true);
        when(game.isActive(bot)).thenReturn(true); when(game.isActive(target)).thenReturn(true);
        when(game.teamOf(bot)).thenReturn(null);
        when(bot.getUniqueId()).thenReturn(UUID.randomUUID());
        when(bot.getWorld()).thenReturn(world); when(target.getWorld()).thenReturn(world);
        when(bot.getLocation()).thenReturn(new Location(world, 0, 10, 0));
        when(bot.getEyeLocation()).thenReturn(new Location(world, 0, 11.6, 0));
        when(target.getLocation()).thenReturn(new Location(world, 10, 10, 0));
        when(target.getEyeLocation()).thenReturn(new Location(world, 10, 11.6, 0));
        when(bot.hasLineOfSight(target)).thenReturn(true);
        when(bot.getInventory()).thenReturn(inventory);
        when(inventory.first(Material.ARROW)).thenReturn(9);
        ItemStack bow = mock(ItemStack.class), ammo = mock(ItemStack.class);
        when(bow.getType()).thenReturn(Material.BOW); when(ammo.getType()).thenReturn(Material.ARROW);
        when(ammo.getAmount()).thenReturn(1);
        when(inventory.getItem(1)).thenReturn(bow); when(inventory.getItem(9)).thenReturn(ammo);
        when(arrow.isValid()).thenReturn(true);
        when(bot.launchProjectile(eq(Arrow.class), any())).thenReturn(arrow);
    }
    @Test void drawsThenFiresRealArrowAndConsumesOneRound() {
        assertTrue(logic.botCombat(bot, target, 0, BotDifficulty.NORMAL));
        verify(bot, never()).launchProjectile(eq(Arrow.class), any());
        logic.botCombat(bot, target, 20, BotDifficulty.NORMAL);
        verify(bot).launchProjectile(eq(Arrow.class), any());
        verify(inventory).setItem(9, null);
        verify(bot, never()).attack(any());
        logic.botCombat(bot, target, 25, BotDifficulty.NORMAL);
        verify(bot, times(1)).launchProjectile(eq(Arrow.class), any());
    }
    @Test void cancelledBowEventDoesNotSpendAmmunition() {
        var manager = game.plugin().getServer().getPluginManager();
        doAnswer(invocation -> { ((EntityShootBowEvent) invocation.getArgument(0)).setCancelled(true); return null; })
                .when(manager).callEvent(any(EntityShootBowEvent.class));
        logic.botCombat(bot, target, 0, BotDifficulty.NORMAL);
        logic.botCombat(bot, target, 20, BotDifficulty.NORMAL);
        verify(arrow).remove(); verify(inventory, never()).setItem(eq(9), any());
    }
    @Test void missingAmmoCannotProduceFreeArrowsAndHiddenTargetsCannotBeShot() {
        when(inventory.first(Material.ARROW)).thenReturn(-1);
        logic.botCombat(bot, target, 0, BotDifficulty.HARD);
        logic.botCombat(bot, target, 100, BotDifficulty.HARD);
        verify(bot, never()).launchProjectile(eq(Arrow.class), any());
        when(inventory.first(Material.ARROW)).thenReturn(9);
        when(bot.hasLineOfSight(target)).thenReturn(false);
        logic.botCombat(bot, target, 200, BotDifficulty.HARD);
        verify(bot, never()).launchProjectile(eq(Arrow.class), any());
    }
    @Test void cancelledProjectileLaunchDoesNotConsumeAmmunition() {
        when(arrow.isValid()).thenReturn(false);
        logic.botCombat(bot, target, 0, BotDifficulty.NORMAL);
        logic.botCombat(bot, target, 20, BotDifficulty.NORMAL);
        verify(inventory, never()).setItem(eq(9), any());
    }
    @Test void frozenRoundCannotFireAndFfaRemainsGenericCombat() {
        when(game.frozen()).thenReturn(true);
        logic.botCombat(bot, target, 0, BotDifficulty.HARD);
        logic.botCombat(bot, target, 100, BotDifficulty.HARD);
        verify(bot, never()).launchProjectile(eq(Arrow.class), any());
        when(game.mode().rulesId()).thenReturn("ffa");
        assertFalse(logic.botCombat(bot, target, 200, BotDifficulty.HARD));
    }
    @Test void ffaLeavesInventoryAndAmmunitionAloneForTheSharedCore() {
        when(game.mode().rulesId()).thenReturn("ffa");
        assertFalse(logic.botCombat(bot, target, 0, BotDifficulty.NORMAL));
        verifyNoInteractions(inventory);
        verify(bot, never()).launchProjectile(eq(Arrow.class), any());
    }
    @Test void oitcStillClosesToTheWoodenSwordInsideMeleeRange() {
        when(target.getLocation()).thenReturn(new Location(world, 1.5, 10, 0));
        ItemStack sword = mock(ItemStack.class);
        when(sword.getType()).thenReturn(Material.WOODEN_SWORD);
        when(inventory.getItem(0)).thenReturn(sword);
        logic.botCombat(bot, target, 0, BotDifficulty.NORMAL);
        verify(inventory).setHeldItemSlot(0);
        verify(bot, never()).launchProjectile(eq(Arrow.class), any());
    }
}
