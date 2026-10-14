package uk.co.playerready.pulsegames.games.duels;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DuelBotCombatTest {
    static ItemStack stack(Material material,int amount) {
        ItemStack item=mock(ItemStack.class);AtomicInteger count=new AtomicInteger(amount);
        when(item.getType()).thenReturn(material);when(item.getAmount()).thenAnswer(i->count.get());
        when(item.clone()).thenAnswer(i->stack(material,count.get()));
        when(item.isSimilar(any())).thenAnswer(i->i.getArgument(0) instanceof ItemStack other && other.getType()==material);
        doAnswer(i->{count.set(i.getArgument(0));return null;}).when(item).setAmount(anyInt());return item;
    }
    static final class Fixture {
        final GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);
        final World world=mock(World.class);final Player bot=mock(Player.class),target=mock(Player.class);
        final PlayerInventory inventory=mock(PlayerInventory.class);
        final Map<Integer,ItemStack> contents=new HashMap<>();final Map<String,Block> blocks=new HashMap<>();
        final AtomicInteger selected=new AtomicInteger();final AtomicReference<ItemStack> offhand=new AtomicReference<>();
        final DuelsGame duels=new DuelsGame(game);final DuelBotCombat control=new DuelBotCombat(game,duels,bound->0);
        final WindCharge wind=mock(WindCharge.class);final EnderCrystal crystal=mock(EnderCrystal.class);
        Fixture(String mode) {
            when(game.mode().rulesId()).thenReturn(mode);when(game.state()).thenReturn(GameState.RUNNING);
            when(game.world()).thenReturn(world);when(game.isBot(bot)).thenReturn(true);
            when(game.isActive(bot)).thenReturn(true);when(game.isActive(target)).thenReturn(true);
            when(game.teamOf(bot)).thenReturn(null);when(game.alivePlayers()).thenReturn(List.of(bot,target));
            when(game.arena().region("combat")).thenReturn(new Cuboid(-20,60,-20,20,90,20));
            when(bot.getUniqueId()).thenReturn(UUID.randomUUID());when(target.getUniqueId()).thenReturn(UUID.randomUUID());
            when(bot.getWorld()).thenReturn(world);when(target.getWorld()).thenReturn(world);
            when(bot.getLocation()).thenAnswer(i->new Location(world,.5,64,.5));
            when(bot.getEyeLocation()).thenAnswer(i->new Location(world,.5,65.62,.5));
            when(target.getLocation()).thenAnswer(i->new Location(world,3.5,64,.5));
            when(bot.hasLineOfSight(any(Entity.class))).thenReturn(true);
            when(bot.getHealth()).thenReturn(20.0);when(bot.getAttackCooldown()).thenReturn(1f);
            when(bot.isOnGround()).thenReturn(true);when(bot.getVelocity()).thenReturn(new Vector());
            when(bot.getInventory()).thenReturn(inventory);
            when(inventory.getItem(anyInt())).thenAnswer(i->contents.get(i.getArgument(0)));
            doAnswer(i->{contents.put(i.getArgument(0),i.getArgument(1));return null;}).when(inventory).setItem(anyInt(),nullable(ItemStack.class));
            when(inventory.getHeldItemSlot()).thenAnswer(i->selected.get());
            doAnswer(i->{selected.set(i.getArgument(0));return null;}).when(inventory).setHeldItemSlot(anyInt());
            when(inventory.getItemInOffHand()).thenAnswer(i->offhand.get());
            doAnswer(i->{offhand.set(i.getArgument(0));return null;}).when(inventory).setItemInOffHand(nullable(ItemStack.class));
            when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            WorldBorder border=mock(WorldBorder.class);when(world.getWorldBorder()).thenReturn(border);when(border.isInside(any())).thenReturn(true);
            when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->block(i.getArgument(0),i.getArgument(1),i.getArgument(2)));
            when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.of());
            when(bot.launchProjectile(eq(WindCharge.class),any(Vector.class))).thenReturn(wind);when(wind.isValid()).thenReturn(true);
            when(world.spawn(any(Location.class),eq(EnderCrystal.class))).thenReturn(crystal);
            when(crystal.getUniqueId()).thenReturn(UUID.randomUUID());when(crystal.isValid()).thenReturn(true);
            when(crystal.getLocation()).thenAnswer(i->new Location(world,2.5,64,.5));
            when(world.getEntity(crystal.getUniqueId())).thenReturn(crystal);
        }
        Block block(int x,int y,int z) {
            String key=x+":"+y+":"+z; if(blocks.containsKey(key))return blocks.get(key);
            Block block=mock(Block.class);blocks.put(key,block);
            when(block.getX()).thenReturn(x);when(block.getY()).thenReturn(y);when(block.getZ()).thenReturn(z);
            when(block.getWorld()).thenReturn(world);when(block.getLocation()).thenAnswer(i->new Location(world,x,y,z));
            when(block.getType()).thenReturn(y==63?Material.STONE:Material.AIR);when(block.isPassable()).thenReturn(y!=63);
            when(block.getBoundingBox()).thenReturn(new BoundingBox(x,y,z,x+1,y+1,z+1));
            when(block.getRelative(any(BlockFace.class))).thenAnswer(i->{BlockFace f=i.getArgument(0);return block(x+f.getModX(),y+f.getModY(),z+f.getModZ());});
            when(block.getRelative(any(BlockFace.class),anyInt())).thenAnswer(i->{BlockFace f=i.getArgument(0);int n=i.getArgument(1);return block(x+f.getModX()*n,y+f.getModY()*n,z+f.getModZ()*n);});
            return block;
        }
        Block crystalBase() {
            Block base=block(2,63,0);when(base.getType()).thenReturn(Material.OBSIDIAN);
            RayTraceResult hit=new RayTraceResult(new Vector(2.5,63.9,.5),base,BlockFace.UP);
            when(world.rayTraceBlocks(any(Location.class),any(Vector.class),anyDouble(),eq(FluidCollisionMode.NEVER),eq(true))).thenReturn(hit);
            return base;
        }
    }
    @Test void windChargeUsesOneActualItemAndNeverInjectsVelocity() {
        var f=new Fixture("mace");f.contents.put(0,stack(Material.WIND_CHARGE,2));f.contents.put(1,stack(Material.MACE,1));
        assertTrue(f.control.launchWind(f.bot));assertEquals(1,f.contents.get(0).getAmount());
        verify(f.bot).launchProjectile(eq(WindCharge.class),eq(new Vector(0,-1.5,0)));
        verify(f.bot,never()).setVelocity(any());verify(f.bot).setCooldown(Material.WIND_CHARGE,10);
        assertEquals(1,f.selected.get());
    }
    @Test void cancelledWindUseAndCancelledProjectileNeverSpendAmmo() {
        var f=new Fixture("mace");f.contents.put(0,stack(Material.WIND_CHARGE,1));
        var manager=f.game.plugin().getServer().getPluginManager();
        doAnswer(i->{((PlayerInteractEvent)i.getArgument(0)).setCancelled(true);return null;}).when(manager).callEvent(any(PlayerInteractEvent.class));
        assertFalse(f.control.launchWind(f.bot));assertEquals(1,f.contents.get(0).getAmount());
        verify(f.bot,never()).launchProjectile(eq(WindCharge.class),any(Vector.class));
        reset(manager);when(f.wind.isValid()).thenReturn(false);
        assertFalse(f.control.launchWind(f.bot));assertEquals(1,f.contents.get(0).getAmount());
    }
    @Test void maceOnlySmashesWhileActuallyFallingAndKeepsOrdinaryReach() {
        var f=new Fixture("mace");f.contents.put(0,stack(Material.MACE,1));
        when(f.bot.isOnGround()).thenReturn(false);when(f.bot.getVelocity()).thenReturn(new Vector(0,-.5,0));when(f.bot.getFallDistance()).thenReturn(3f);
        when(f.target.getLocation()).thenAnswer(i->new Location(f.world,2.5,64,.5));
        f.control.combat(f.bot,f.target,0,BotDifficulty.HARD);f.control.combat(f.bot,f.target,10,BotDifficulty.HARD);
        verify(f.bot).attack(f.target);assertNull(f.control.objective(f.bot,f.target.getLocation()));
        when(f.bot.getVelocity()).thenReturn(new Vector(0,.5,0));f.control.combat(f.bot,f.target,20,BotDifficulty.HARD);
        when(f.bot.getVelocity()).thenReturn(new Vector(0,-.5,0));when(f.target.getLocation()).thenReturn(new Location(f.world,8,64,0));
        f.control.combat(f.bot,f.target,30,BotDifficulty.HARD);verify(f.bot,times(1)).attack(f.target);
    }
    @Test void unsafeFloorCeilingAndUnloadedChunkPreventChargedJump() {
        var f=new Fixture("mace");assertTrue(f.control.safeLaunch(f.bot.getLocation()));
        when(f.block(0,68,0).getType()).thenReturn(Material.STONE);assertFalse(f.control.safeLaunch(f.bot.getLocation()));
        when(f.block(0,68,0).getType()).thenReturn(Material.AIR);when(f.block(1,63,1).isPassable()).thenReturn(true);
        assertFalse(f.control.safeLaunch(f.bot.getLocation()));when(f.block(1,63,1).isPassable()).thenReturn(false);
        when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);assertFalse(f.control.safeLaunch(f.bot.getLocation()));
    }
    @Test void crystalPlacementConsumesExactlyOneAndRegistersRoundOwnership() {
        var f=new Fixture("crystal");var base=f.crystalBase();f.contents.put(0,stack(Material.END_CRYSTAL,2));
        assertTrue(f.control.placeCrystal(f.bot,base));assertEquals(1,f.contents.get(0).getAmount());
        assertTrue(f.duels.botCrystals().contains(f.crystal.getUniqueId()));verify(f.crystal,never()).remove();
    }
    @Test void cancelledCrystalPlacementRemovesEntityWithoutSpendingOrOwnership() {
        var f=new Fixture("crystal");var base=f.crystalBase();f.contents.put(0,stack(Material.END_CRYSTAL,2));
        var manager=f.game.plugin().getServer().getPluginManager();
        doAnswer(i->{((EntityPlaceEvent)i.getArgument(0)).setCancelled(true);return null;}).when(manager).callEvent(any(EntityPlaceEvent.class));
        assertFalse(f.control.placeCrystal(f.bot,base));assertEquals(2,f.contents.get(0).getAmount());
        assertTrue(f.duels.botCrystals().isEmpty());verify(f.crystal).remove();
    }
    @Test void noAmmoAndBlockedCrystalSpaceCannotSpawnCrystals() {
        var f=new Fixture("crystal");var base=f.crystalBase();assertFalse(f.control.placeCrystal(f.bot,base));
        f.contents.put(0,stack(Material.END_CRYSTAL,2));when(f.block(2,64,0).getType()).thenReturn(Material.STONE);
        assertFalse(f.control.placeCrystal(f.bot,base));verify(f.world,never()).spawn(any(Location.class),eq(EnderCrystal.class));
    }
    @Test void totemSwapIsFiniteAndCancellationPreservesBothHands() {
        var f=new Fixture("crystal");f.contents.put(0,stack(Material.TOTEM_OF_UNDYING,1));
        f.offhand.set(stack(Material.SHIELD,1));assertTrue(f.control.equipTotem(f.bot));
        assertEquals(Material.TOTEM_OF_UNDYING,f.offhand.get().getType());assertEquals(Material.SHIELD,f.contents.get(0).getType());
        var denied=new Fixture("crystal");denied.contents.put(0,stack(Material.TOTEM_OF_UNDYING,1));
        var manager=denied.game.plugin().getServer().getPluginManager();
        doAnswer(i->{((PlayerSwapHandItemsEvent)i.getArgument(0)).setCancelled(true);return null;}).when(manager).callEvent(any(PlayerSwapHandItemsEvent.class));
        assertFalse(denied.control.equipTotem(denied.bot));assertNull(denied.offhand.get());assertEquals(Material.TOTEM_OF_UNDYING,denied.contents.get(0).getType());
    }
    @Test void crystalDetonationUsesOrdinaryPlayerAttackAndAvoidsNearbyTeamMates() {
        var f=new Fixture("crystal");var base=f.crystalBase();f.contents.put(0,stack(Material.DIAMOND_SWORD,1));
        f.duels.onEntityPlace(new EntityPlaceEvent(f.crystal,f.bot,base,BlockFace.UP,EquipmentSlot.HAND));
        f.control.combat(f.bot,f.target,0,BotDifficulty.HARD);f.control.combat(f.bot,f.target,10,BotDifficulty.HARD);
        verify(f.bot).attack(f.crystal);
        Player mate=mock(Player.class);when(mate.getWorld()).thenReturn(f.world);when(mate.getLocation()).thenReturn(new Location(f.world,3,64,0));
        GameTeam team=mock(GameTeam.class);when(team.contains(mate)).thenReturn(true);when(f.game.teamOf(f.bot)).thenReturn(team);
        when(f.game.alivePlayers()).thenReturn(List.of(f.bot,f.target,mate));
        f.control.combat(f.bot,f.target,20,BotDifficulty.HARD);verify(f.bot,times(1)).attack(f.crystal);
    }
    @Test void crystalEventInventoryMutationCannotConsumeAnUnrelatedStack() {
        var f=new Fixture("crystal");var base=f.crystalBase();f.contents.put(0,stack(Material.END_CRYSTAL,2));
        var manager=f.game.plugin().getServer().getPluginManager();
        doAnswer(i->{f.contents.put(0,stack(Material.DIAMOND_SWORD,1));return null;}).when(manager).callEvent(any(EntityPlaceEvent.class));
        assertFalse(f.control.placeCrystal(f.bot,base));assertEquals(Material.DIAMOND_SWORD,f.contents.get(0).getType());
        verify(f.crystal).remove();assertTrue(f.duels.botCrystals().isEmpty());
    }
    @Test void difficultyChangesReactionTimeWithoutChangingDamageOrReach() {
        var easy=new Fixture("mace");easy.contents.put(0,stack(Material.MACE,1));
        when(easy.target.getLocation()).thenReturn(new Location(easy.world,2,64,0));
        easy.control.combat(easy.bot,easy.target,0,BotDifficulty.EASY);easy.control.combat(easy.bot,easy.target,10,BotDifficulty.EASY);
        verify(easy.bot,never()).attack(any());easy.control.combat(easy.bot,easy.target,28,BotDifficulty.EASY);
        verify(easy.bot).attack(easy.target);
    }
    @Test void friendlyInvisibleAndFrozenTargetsCannotTriggerAttackActions() {
        var f=new Fixture("mace");f.contents.put(0,stack(Material.MACE,1));when(f.target.getLocation()).thenReturn(new Location(f.world,1,64,0));
        when(f.bot.hasLineOfSight(f.target)).thenReturn(false);f.control.combat(f.bot,f.target,0,BotDifficulty.HARD);f.control.combat(f.bot,f.target,100,BotDifficulty.HARD);
        when(f.bot.hasLineOfSight(f.target)).thenReturn(true);when(f.game.frozen()).thenReturn(true);f.control.combat(f.bot,f.target,200,BotDifficulty.HARD);
        when(f.game.frozen()).thenReturn(false);GameTeam team=mock(GameTeam.class);when(team.contains(f.target)).thenReturn(true);when(f.game.teamOf(f.bot)).thenReturn(team);
        f.control.combat(f.bot,f.target,300,BotDifficulty.HARD);verify(f.bot,never()).attack(any());
    }
}
