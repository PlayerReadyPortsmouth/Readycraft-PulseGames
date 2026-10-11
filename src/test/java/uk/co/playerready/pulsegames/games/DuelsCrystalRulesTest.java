package uk.co.playerready.pulsegames.games;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.games.duels.DuelsGame;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DuelsCrystalRulesTest {
 @Test void crystalDamageProtectsTeammatesButAllowsSelfDamageAndCreditsEnemies() throws Exception {
  var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);var owner=mock(Player.class);var friend=mock(Player.class);var enemy=mock(Player.class);var crystal=mock(EnderCrystal.class);
  for(var p:List.of(owner,friend,enemy))when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(crystal.getUniqueId()).thenReturn(UUID.randomUUID());
  var team=new uk.co.playerready.pulsegames.core.team.GameTeam(0);team.add(owner);team.add(friend);when(game.teamOf(owner)).thenReturn(team);
  when(game.player(owner.getUniqueId())).thenReturn(owner);when(game.isActive(owner)).thenReturn(true);
  var logic=new DuelsGame(game);var field=DuelsGame.class.getDeclaredField("crystalOwners");field.setAccessible(true);
  @SuppressWarnings("unchecked") var owners=(Map<UUID,UUID>)field.get(logic);owners.put(crystal.getUniqueId(),owner.getUniqueId());
  var event=mock(EntityDamageByEntityEvent.class);when(event.getDamager()).thenReturn(crystal);
  logic.onDamage(friend,event);verify(event).setCancelled(true);clearInvocations(event);
  logic.onDamage(owner,event);verify(event,never()).setCancelled(true);verify(game,never()).recordDamager(owner,owner);
  logic.onDamage(enemy,event);verify(game).recordDamager(enemy,owner);
 }
 @Test void onlyPlacedObsidianInsideCombatAreaCanBeEditedAndItRestores() {
  var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);var world=mock(World.class);var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
  when(game.mode()).thenReturn(GameMode.ffa("modern-crystal","Crystal",2,2));when(game.world()).thenReturn(world);
  when(game.arena().region("combat")).thenReturn(new Cuboid(0,70,0,10,90,10));
  var block=mock(Block.class);when(block.getLocation()).thenReturn(new Location(world,5,79,5));when(block.getType()).thenReturn(Material.OBSIDIAN);
  var logic=new DuelsGame(game);assertTrue(logic.canPlace(player,block));assertFalse(logic.canBreak(player,block));
  var event=mock(BlockPlaceEvent.class);var original=mock(BlockState.class);when(event.getBlock()).thenReturn(block);when(event.getBlockReplacedState()).thenReturn(original);
  logic.onBlockPlaced(event);assertTrue(logic.canBreak(player,block));
  when(block.getLocation()).thenReturn(new Location(world,12,79,5));assertFalse(logic.canPlace(player,block));
  logic.onCleanup();verify(original).update(true,false);
 }
 @Test void onlyModernMaceAllowsSelfWindChargeDamage() {
  var game=mock(GameInstance.class);var event=mock(EntityDamageByEntityEvent.class);when(event.getDamager()).thenReturn(mock(WindCharge.class));
  var logic=new DuelsGame(game);when(game.mode()).thenReturn(GameMode.ffa("modern-mace","Mace",2,2));assertTrue(logic.allowsSelfDamage(event));
  when(game.mode()).thenReturn(GameMode.ffa("modern-classic","Regular",2,2));assertFalse(logic.allowsSelfDamage(event));
  when(game.mode()).thenReturn(GameMode.ffa("modern-mace","Mace",2,2));when(event.getDamager()).thenReturn(mock(Arrow.class));assertFalse(logic.allowsSelfDamage(event));
 }
 @Test void crystalPlacementOutsideRegionOrBetweenRoundsIsRefused() throws Exception {
  var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);var world=mock(World.class);var player=mock(Player.class);var crystal=mock(EnderCrystal.class);
  when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(crystal.getUniqueId()).thenReturn(UUID.randomUUID());
  when(game.world()).thenReturn(world);when(game.mode()).thenReturn(GameMode.ffa("modern-crystal","Crystal",2,2));when(game.isActive(player)).thenReturn(true);
  when(game.arena().region("combat")).thenReturn(new Cuboid(0,70,0,10,90,10));when(crystal.getLocation()).thenReturn(new Location(world,15,80,5));
  var event=mock(EntityPlaceEvent.class);when(event.getPlayer()).thenReturn(player);when(event.getEntity()).thenReturn(crystal);
  var logic=new DuelsGame(game);logic.onEntityPlace(event);verify(event).setCancelled(true);
  clearInvocations(event);when(crystal.getLocation()).thenReturn(new Location(world,5,80,5));var field=DuelsGame.class.getDeclaredField("roundOver");field.setAccessible(true);field.set(logic,true);
  logic.onEntityPlace(event);verify(event).setCancelled(true);
 }
}
