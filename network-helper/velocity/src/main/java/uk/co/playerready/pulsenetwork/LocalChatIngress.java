package uk.co.playerready.pulsenetwork;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Loopback-only transport for the server-only mod adapter. No untrusted text is logged. */
final class LocalChatIngress implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    LocalChatIngress(int port, Wire wire, Predicate<Wire.Message> receiver) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",port),16);
        executor = new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32), r -> {
            Thread thread=new Thread(r,"PulseNetwork-ingress");thread.setDaemon(true);return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
        server.createContext("/chat", exchange -> {
            try(exchange) {
                int status=403;
                if(exchange.getRequestMethod().equals("POST") && exchange.getRequestURI().getPath().equals("/chat")) {
                    byte[] bytes=exchange.getRequestBody().readNBytes(8193);
                    var decoded=wire.decode(bytes);
                    if(decoded.isPresent() && decoded.get().kind().equals("CHAT") && decoded.get().fields().size()==2 && receiver.test(decoded.get()))status=204;
                }
                exchange.sendResponseHeaders(status,-1);
            }
        });
        server.start();
    }
    int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0);executor.shutdownNow(); }
}
