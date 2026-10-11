package uk.co.playerready.pulsenetwork;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ChatCompatibilityTest {
 public static class Api {
  public boolean channel,all; public Set<String> ignored=new HashSet<>();
  public boolean isPlayerInChannel(UUID id){return channel;}
  public boolean hasAllPlayersIgnored(Player p){return all;}
  public Set<String> getIgnoredPlayers(Player p){return ignored;}
 }
 public static class User {
  public boolean muted,vanished; public List<UUID> ignored=new ArrayList<>();
  public boolean isMuted(){return muted;} public boolean isVanished(){return vanished;}
  public List<UUID> _getIgnoredPlayers(){return ignored;}
  public boolean isIgnoredPlayer(String name){return false;}
 }
 public static class Essentials extends JavaPlugin { public User getUser(UUID id){return null;} }
 public static class PublicEvent extends Event implements Cancellable {
  boolean cancelled; double range; AsyncPlayerChatEvent raw;
  public PublicEvent(AsyncPlayerChatEvent raw){this.raw=raw;}
  public AsyncPlayerChatEvent getChatEvent(){return raw;} public String getMessage(){return "hello";}
  public double getRange(){return range;} public boolean isCancelled(){return cancelled;} public void setCancelled(boolean value){cancelled=value;}
  public HandlerList getHandlers(){return new HandlerList();}
 }
 record Setup(PaperBridge bridge,ChatCompatibility adapter,Api api,User user){}
 Setup setup()throws Exception{
  var bridge=mock(PaperBridge.class);var server=mock(Server.class);var manager=mock(PluginManager.class);
  when(bridge.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(manager);
  var advanced=mock(Plugin.class);when(advanced.isEnabled()).thenReturn(true);when(manager.getPlugin("AdvancedChat")).thenReturn(advanced);
  var essentials=mock(Essentials.class);when(essentials.isEnabled()).thenReturn(true);when(manager.getPlugin("Essentials")).thenReturn(essentials);
  var user=new User();when(essentials.getUser(any(UUID.class))).thenReturn(user);
  var adapter=new ChatCompatibility(bridge);var api=new Api();var field=ChatCompatibility.class.getDeclaredField("advancedApi");field.setAccessible(true);field.set(adapter,api);
  return new Setup(bridge,adapter,api,user);
 }
 @Test void nativeIgnoresAndMutesAreRespected()throws Exception{
  var s=setup();var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());var sender=UUID.randomUUID();
  assertTrue(s.adapter.mayReceive(player,sender,"Alice"));s.api.ignored.add("aLiCe");assertFalse(s.adapter.mayReceive(player,sender,"Alice"));s.api.ignored.clear();
  s.user.ignored.add(sender);assertFalse(s.adapter.mayReceive(player,sender,"Alice"));s.user.ignored.clear();
  s.api.all=true;assertFalse(s.adapter.mayReceive(player,sender,"Alice"));
  assertTrue(s.adapter.maySend(player));s.user.muted=true;assertFalse(s.adapter.maySend(player));s.user.muted=false;s.user.vanished=true;assertFalse(s.adapter.maySend(player));
 }
 @Test void onlyUncancelledPublicUnlimitedChannelEventsRelay()throws Exception{
  var s=setup();var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());var raw=mock(AsyncPlayerChatEvent.class);when(raw.getPlayer()).thenReturn(player);when(raw.isCancelled()).thenReturn(true);
  var event=new PublicEvent(raw);s.adapter.publicAdvanced(event);verify(s.bridge).relayPublic(player,"hello");clearInvocations(s.bridge);
  event.cancelled=true;s.adapter.publicAdvanced(event);event.cancelled=false;event.range=20;s.adapter.publicAdvanced(event);event.range=0;s.api.channel=true;s.adapter.publicAdvanced(event);
  verify(s.bridge,never()).relayPublic(any(),anyString());
 }
 @Test void unavailableNativeApiFailsClosed()throws Exception{
  var s=setup();var f=ChatCompatibility.class.getDeclaredField("advancedApi");f.setAccessible(true);f.set(s.adapter,null);
  assertFalse(s.adapter.mayReceive(mock(Player.class),UUID.randomUUID(),"Alice"));
 }
}
