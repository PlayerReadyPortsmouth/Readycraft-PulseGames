package uk.co.playerready.pulsegames.games;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.kit.Kit;
import uk.co.playerready.pulsegames.games.duels.DuelsGame;
import uk.co.playerready.pulsegames.games.kitpvp.KitPvpGame;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class LoadoutPreflightTest {
 @Test void duelsRejectMissingKitBeforeStarting() {
  var g=mock(GameInstance.class,RETURNS_DEEP_STUBS);var p=mock(Player.class);
  when(g.mode()).thenReturn(GameMode.ffa("classic","Classic",2,2));
  when(g.arena().settings().getString("kit","warrior")).thenReturn("warrior");
  when(g.plugin().kits().get("warrior")).thenReturn(null);
  var logic=new DuelsGame(g);assertNotNull(logic.startProblem(List.of(p)));
  var kit=mock(Kit.class);when(g.plugin().kits().get("warrior")).thenReturn(kit);
  assertNull(logic.startProblem(List.of(p)));verify(kit,never()).apply(any());
 }
 @Test void kitPvpKeepsMissingSelectionFallbackButRejectsNoAvailableKit() {
  var g=mock(GameInstance.class,RETURNS_DEEP_STUBS);var p=mock(Player.class);
  when(g.mode()).thenReturn(GameMode.ffa("ffa","FFA",2,8));
  when(g.plugin().cosmetics().equippedKitId(p)).thenReturn("missing");
  when(g.plugin().kits().get("missing")).thenReturn(null);
  when(g.arena().settings().getString("kit","warrior")).thenReturn("warrior");
  when(g.plugin().kits().get("warrior")).thenReturn(null);
  var logic=new KitPvpGame(g);assertNotNull(logic.startProblem(List.of(p)));
  when(g.plugin().kits().get("warrior")).thenReturn(mock(Kit.class));
  assertNull(logic.startProblem(List.of(p)));
 }
 @Test void builtinOitcLoadoutDoesNotDependOnKitCatalogue() {
  var g=mock(GameInstance.class,RETURNS_DEEP_STUBS);
  when(g.mode()).thenReturn(GameMode.ffa("oitc","OITC",2,8));
  assertNull(new KitPvpGame(g).startProblem(List.of(mock(Player.class))));
 }
}
