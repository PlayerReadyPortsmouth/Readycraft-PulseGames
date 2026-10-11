package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LocalChatIngressTest {
    @Test void loopbackEndpointAuthenticatesBoundsAndRejectsReplay()throws Exception {
        String key="test-only-not-a-deployment-secret-0123456789";AtomicInteger received=new AtomicInteger();
        try(var endpoint=new LocalChatIngress(0,new Wire(key),m->{received.incrementAndGet();return m.fields().getFirst().equals("aoc");});var client=HttpClient.newHttpClient()) {
            var uri=URI.create("http://127.0.0.1:"+endpoint.port()+"/chat");
            byte[] packet=new Wire(key).encode("CHAT",UUID.randomUUID(),"aoc","hello");
            var request=HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofByteArray(packet)).build();
            assertEquals(204,client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(403,client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(403,client.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofByteArray(new byte[8193])).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(1,received.get());
        }
    }
}
