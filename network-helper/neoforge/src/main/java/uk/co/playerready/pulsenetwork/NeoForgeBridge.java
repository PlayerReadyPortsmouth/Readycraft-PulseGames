package uk.co.playerready.pulsenetwork;

import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** No client mod or custom-payload handshake required. Transport is authenticated loopback HTTP. */
@Mod(value="pulsenetwork", dist=Dist.DEDICATED_SERVER)
public final class NeoForgeBridge {
    private Wire wire;
    private HttpClient http;
    private String backend;
    private URI endpoint;
    private boolean byDefault, localGames;
    private String answer;
    private long deadline, nextGame = System.currentTimeMillis() + 60_000;
    private Set<String> externalGames = Set.of();
    private final Map<UUID, Boolean> global = new HashMap<>();
    private final Map<UUID, Long> last = new HashMap<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final java.util.Queue<Runnable> chatTasks = new java.util.concurrent.ArrayBlockingQueue<>(256);
    public NeoForgeBridge() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("pulsenetwork.properties");
            if (!Files.exists(path)) Files.writeString(path,"enabled=false\nbridge-secret=\nbackend=aoc\nproxy-port=25581\nglobal-by-default=false\nchat-games=false\nexternal-chat-game-mods=\n");
            Properties config = new Properties();try(var reader=Files.newBufferedReader(path)){config.load(reader);}
            if (!Boolean.parseBoolean(config.getProperty("enabled"))) return;
            wire = new Wire(config.getProperty("bridge-secret")); backend = config.getProperty("backend", "aoc");
            if (!Wire.id(backend)) throw new IllegalArgumentException("Invalid backend name");
            int port = Integer.parseInt(config.getProperty("proxy-port", "25581"));
            if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid proxy bridge port");
            endpoint = URI.create("http://127.0.0.1:"+port+"/chat");byDefault=Boolean.parseBoolean(config.getProperty("global-by-default"));
            localGames=Boolean.parseBoolean(config.getProperty("chat-games", "false"));
            externalGames=new HashSet<>(Arrays.asList(config.getProperty("external-chat-game-mods", "").split(",")));
            http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        } catch (Exception e) { System.getLogger("PulseNetwork").log(System.Logger.Level.ERROR,"Invalid PulseNetwork configuration: "+e.getClass().getSimpleName());wire=null;return; }
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::chat);
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::quit);
        NeoForge.EVENT_BUS.addListener(this::stop);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("globalchat").executes(context -> toggle(context.getSource().getPlayerOrException(), false)));
        event.getDispatcher().register(Commands.literal("g").executes(context -> toggle(context.getSource().getPlayerOrException(), false)));
        event.getDispatcher().register(Commands.literal("localchat").executes(context -> toggle(context.getSource().getPlayerOrException(), true)));
    }
    private int toggle(net.minecraft.server.level.ServerPlayer player, boolean local) {
        boolean enabled=!local && !global.getOrDefault(player.getUUID(),byDefault);global.put(player.getUUID(),enabled);
        player.sendSystemMessage(Component.literal(enabled ? "Global chat on. Type normally." : "Local chat on."));return 1;
    }
    private void chat(ServerChatEvent event) {
        var player=event.getPlayer();var server=player.getServer();if(server==null)return;
        chatTasks.offer(() -> {
            UUID id=player.getUUID();
            if (event.isCanceled() || player.hasDisconnected()) return;
            String body=Wire.plain(event.getMessage().getString());long now=System.currentTimeMillis();
            if (answer != null && now < deadline && answer.equalsIgnoreCase(body.trim())) {
                server.getPlayerList().broadcastSystemMessage(Component.literal("[Chat game] "+player.getGameProfile().getName()+" got it! The answer was "+answer+"."),false);
                answer=null;return;
            }
            if (!global.getOrDefault(id,byDefault)) return;
            if(body.isBlank() || body.length()>512 || now-last.getOrDefault(id,0L)<1000 || requests.get()>=16)return;
            last.put(id,now);requests.incrementAndGet();
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3)).POST(HttpRequest.BodyPublishers.ofByteArray(wire.encode("CHAT",id,backend,body))).build();
            http.sendAsync(request,HttpResponse.BodyHandlers.discarding()).whenComplete((response,error)->requests.decrementAndGet());
        });
    }
    private void quit(PlayerEvent.PlayerLoggedOutEvent event) {global.remove(event.getEntity().getUUID());last.remove(event.getEntity().getUUID());}
    private void stop(ServerStoppedEvent event) {if(http!=null)http.close();}
    private void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        // Queue rather than server.execute(): execute may run inline before later chat handlers.
        for (int i=0;i<256;i++) {Runnable task=chatTasks.poll();if(task==null)break;task.run();}
        if (!localGames || externalGames.stream().anyMatch(id -> net.neoforged.fml.ModList.get().isLoaded(id.trim()))) {answer=null;return;}
        var players=event.getServer().getPlayerList();long now=System.currentTimeMillis();
        if(answer!=null) {
            if(now>=deadline){players.broadcastSystemMessage(Component.literal("[Chat game] Time's up! The answer was "+answer+"."),false);answer=null;}
            return;
        }
        if(now<nextGame || players.getPlayerCount()<2)return;
        var challenge=ChatChallenge.random();answer=challenge.answer();deadline=now+30_000;nextGame=now+300_000;
        players.broadcastSystemMessage(Component.literal("[Chat game] "+challenge.question()+" Type your answer in local chat. You have 30 seconds."),false);
    }
}
