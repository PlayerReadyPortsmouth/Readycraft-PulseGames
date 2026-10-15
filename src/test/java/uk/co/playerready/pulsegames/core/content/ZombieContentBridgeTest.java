package uk.co.playerready.pulsegames.core.content;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ZombieContentBridgeTest {
 public interface Encounters {
  boolean hasVisual(String id); boolean isContentReady(Player p); Entity spawnEntity(String id,Location l);
 }
 record Fixture(PulseContentBridge bridge,Plugin provider,Encounters api,Player player,World world) {}
 Fixture fixture() {
  PulseGamesPlugin game=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);
  Plugin provider=mock(Plugin.class,withSettings().extraInterfaces(Encounters.class));
  when(game.getServer().getPluginManager().getPlugin("PulseContent")).thenReturn(provider);
  when(provider.isEnabled()).thenReturn(true);var api=(Encounters)provider;
  Player player=mock(Player.class);World world=mock(World.class);
  when(api.hasVisual("readycraft:z_boss")).thenReturn(true);when(api.isContentReady(player)).thenReturn(true);
  return new Fixture(new PulseContentBridge(game),provider,api,player,world);
 }
 @Test void missingOrUnreadyContentRefusesAdmission() {
  var f=fixture();assertNull(f.bridge.mobReadiness(List.of(f.player),List.of()));
  assertNotNull(f.bridge.mobReadiness(List.of(f.player),List.of("other:z_boss")));
  assertNotNull(f.bridge.mobReadiness(List.of(f.player),List.of("z_boss")));
  when(f.api.isContentReady(f.player)).thenReturn(false);
  assertNotNull(f.bridge.mobReadiness(List.of(f.player),List.of("readycraft:z_boss")));
  verify(f.api,never()).spawnEntity(anyString(),any());
 }
 @Test void exactAllowedIdIsTranslatedToLocalEncounterId() {
  var f=fixture();Location location=new Location(f.world,1,64,2);Zombie zombie=mock(Zombie.class);
  when(zombie.getWorld()).thenReturn(f.world);when(f.api.spawnEntity("z_boss",location)).thenReturn(zombie);
  assertSame(zombie,f.bridge.spawnMob("readycraft:z_boss",location,List.of(f.player)));
  verify(f.api).hasVisual("readycraft:z_boss");verify(f.api).spawnEntity("z_boss",location);
 }
 @Test void unexpectedEntityIsRemovedInsteadOfLeaking() {
  var f=fixture();Location location=new Location(f.world,1,64,2);Entity wrong=mock(Entity.class);
  when(f.api.spawnEntity("z_boss",location)).thenReturn(wrong);
  assertThrows(IllegalStateException.class,()->f.bridge.spawnMob("readycraft:z_boss",location,List.of(f.player)));
  verify(wrong).remove();
 }
 @Test void readinessIsRecheckedBeforeEachSpawn() {
  var f=fixture();assertNull(f.bridge.mobReadiness(List.of(f.player),List.of("readycraft:z_boss")));
  when(f.api.isContentReady(f.player)).thenReturn(false);
  assertThrows(IllegalStateException.class,()->f.bridge.spawnMob("readycraft:z_boss",new Location(f.world,1,64,2),List.of(f.player)));
  verify(f.api,never()).spawnEntity(anyString(),any());
 }
}
