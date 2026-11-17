package uk.co.playerready.pulsegames.core.seasonal;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.configuration.ConfigurationSection;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.util.PlatformDetector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Optional additive Java pack, acknowledged by exact offer UUID; never replaces a player's other packs. */
public final class SeasonOverlay implements Listener,AutoCloseable {
    record Pack(UUID id,String url,String hash) {
        static Pack parse(ConfigurationSection section) {
            if(section==null)return null;
            String url=section.getString("url",""),hash=section.getString("sha1","");
            if(url.isBlank()&&hash.isBlank())return null;
            URI uri=URI.create(url);
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||!hash.matches("[0-9a-fA-F]{40}"))
                throw new IllegalArgumentException("Season overlay needs an HTTPS URL and 40-character SHA-1");
            hash=hash.toLowerCase(Locale.ROOT);
            return new Pack(UUID.nameUUIDFromBytes(("pulse-xm:"+hash).getBytes(StandardCharsets.UTF_8)),url,hash);
        }
    }
    private final PulseGamesPlugin plugin;
    private final Map<UUID,Pack> offered=new HashMap<>();
    private final Set<UUID> ready=new HashSet<>();
    private Pack pack;
    private boolean active;
    public SeasonOverlay(PulseGamesPlugin plugin) {this.plugin=plugin;plugin.getServer().getPluginManager().registerEvents(this,plugin);}
    public void configure(ConfigurationSection config,String season) {
        try {pack=Pack.parse(config==null?null:config.getConfigurationSection("resource-packs."+season));}
        catch(IllegalArgumentException invalid) {pack=null;plugin.getLogger().warning("Season overlay disabled: "+invalid.getMessage());}
        active=season!=null;
    }
    public boolean ready(Player player) {return !PlatformDetector.isBedrock(player)&&ready.contains(player.getUniqueId());}
    public void tick() {
        for(Player player:plugin.getServer().getOnlinePlayers()) {
            UUID id=player.getUniqueId();Pack previous=offered.get(id);
            // A running round keeps its additive overlay until its models have been removed.
            if(previous!=null&&plugin.instances().byPlayer(player)!=null)continue;
            if(previous!=null&&(!active||!Objects.equals(previous,pack)||!plugin.seasons().canView(player))) {
                player.removeResourcePack(previous.id());offered.remove(id);ready.remove(id);
            }
            if(active&&pack!=null&&!offered.containsKey(id)&&!PlatformDetector.isBedrock(player)
                    &&plugin.seasons().canView(player)) {
                offered.put(id,pack);
                player.addResourcePack(pack.id(),pack.url(),HexFormat.of().parseHex(pack.hash()),"Optional seasonal Christmas models",false);
            }
        }
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        Pack expected=offered.get(event.getPlayer().getUniqueId());
        if(expected==null||!expected.id().equals(event.getID()))return;
        if(event.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)ready.add(event.getPlayer().getUniqueId());
        else if(event.getStatus()!=PlayerResourcePackStatusEvent.Status.ACCEPTED&&event.getStatus()!=PlayerResourcePackStatusEvent.Status.DOWNLOADED)ready.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler public void quit(PlayerQuitEvent event) {offered.remove(event.getPlayer().getUniqueId());ready.remove(event.getPlayer().getUniqueId());}
    @Override public void close() {
        for(Player player:plugin.getServer().getOnlinePlayers()) {Pack p=offered.get(player.getUniqueId());if(p!=null)player.removeResourcePack(p.id());}
        offered.clear();ready.clear();HandlerList.unregisterAll(this);
    }
}
