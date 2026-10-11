package uk.co.playerready.pulsegames.core.lobby;
import org.bukkit.entity.Player;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.kit.Kit;
import uk.co.playerready.pulsegames.core.arena.Arena;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DuelOptionsTest {
 @Test void combatRulesAndTeamSizeAreSeparateFromPersonalKits() {
  var plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);var menu=new DuelMenu(plugin);
  var mode=GameMode.teams("modern-classic-doubles","Modern 2v2",4,4,2);
  assertEquals(GameMode.CombatStyle.MODERN,mode.combatStyle());assertEquals("classic",mode.rulesId());
  var iron=mock(Kit.class);when(iron.id()).thenReturn("warrior");var diamond=mock(Kit.class);when(diamond.id()).thenReturn("op");
  when(plugin.kits().duelChoices(mode)).thenReturn(List.of(iron,diamond));
  Player a=mock(Player.class),b=mock(Player.class);
  when(plugin.economy().duelKit(a,"modern.classic")).thenReturn("op");
  when(plugin.economy().duelKit(b,"modern.classic")).thenReturn("warrior");
  assertSame(diamond,menu.kitFor(a,mode));assertSame(iron,menu.kitFor(b,mode));
  assertTrue(menu.selectKit(a,mode,"warrior"));verify(plugin.economy()).duelKit(a,"modern.classic","warrior");
  assertFalse(menu.selectKit(a,mode,"duel-mace"));verify(a,never()).getInventory();
 }
 @Test void paidKitsStillRequireOwnership() {
  var plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);var menu=new DuelMenu(plugin);var p=mock(Player.class);
  var mode=GameMode.ffa("legacy-classic","Classic",2,2);var tank=mock(Kit.class);when(tank.id()).thenReturn("tank");
  when(plugin.kits().duelChoices(mode)).thenReturn(List.of(tank));
  assertFalse(menu.selectKit(p,mode,"tank"));assertNull(menu.kitFor(p,mode));
  when(plugin.economy().isUnlocked(p,"kit-tank")).thenReturn(true);assertTrue(menu.selectKit(p,mode,"tank"));
 }
 @Test void existingMapRulesCarryAcrossCombatStylesUntilExplicitlyConfigured() {
  var settings=new MemoryConfiguration();settings.set("modes.classic-doubles.max-players",4);
  var arena=new Arena("a","duels","A","a",List.of("classic-doubles"),2,8,"0,80,0",null,List.of("0,80,0","5,80,0"),Map.of(),settings);
  assertTrue(arena.supportsMode("modern-classic-doubles"));assertTrue(arena.supportsMode("legacy-classic-doubles"));
  assertEquals(4,arena.maxPlayers("modern-classic-doubles"));assertFalse(arena.supportsMode("modern-crystal"));
  settings.set("modes.modern-classic-doubles.max-players",3);assertEquals(3,arena.maxPlayers("modern-classic-doubles"));
  settings.set("combat-profiles-explicit",true);assertFalse(arena.supportsMode("modern-classic-doubles"));assertTrue(arena.supportsMode("classic-doubles"));
 }
}
