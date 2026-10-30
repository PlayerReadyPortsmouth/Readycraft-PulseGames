package uk.co.playerready.pulsenetwork;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

/** Authenticated, bounded plugin messages. Clients must never be trusted as senders. */
public final class Wire {
    public static final String CHANNEL = "pulsenetwork:bridge";
    private static final int LIMIT = 8192;
    private final byte[] key;
    private final Clock clock;
    private final Map<UUID, Long> seen = new HashMap<>();
    public record Message(String kind, UUID player, List<String> fields) {}
    public Wire(String secret) { this(secret, Clock.systemUTC()); }
    public Wire(String secret, Clock clock) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("Bridge secret must have at least 32 characters");
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }
    public byte[] encode(String kind, UUID player, String... fields) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(1); out.writeLong(clock.millis()); out.writeUTF(UUID.randomUUID().toString());
            out.writeUTF(kind); out.writeUTF(player.toString());
            if (fields.length > 12) throw new IllegalArgumentException("Too many fields");
            out.writeInt(fields.length);
            for (String field : fields) {
                if (field.length() > 1024) throw new IllegalArgumentException("Field too long");
                out.writeUTF(field);
            }
            byte[] payload = bytes.toByteArray();
            out.write(sign(payload));
            if (bytes.size() > LIMIT) throw new IllegalArgumentException("Packet too large");
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("Invalid packet", e); }
    }
    public synchronized Optional<Message> decode(byte[] packet) {
        if (packet.length < 40 || packet.length > LIMIT) return Optional.empty();
        try {
            byte[] payload = Arrays.copyOf(packet, packet.length - 32);
            if (!MessageDigest.isEqual(sign(payload), Arrays.copyOfRange(packet, payload.length, packet.length))) return Optional.empty();
            var in = new DataInputStream(new ByteArrayInputStream(payload));
            if (in.readInt() != 1) return Optional.empty();
            long time = in.readLong(), now = clock.millis();
            if (time < now - 30_000 || time > now + 5_000) return Optional.empty();
            UUID nonce = UUID.fromString(in.readUTF());
            String kind = in.readUTF(); UUID player = UUID.fromString(in.readUTF());
            int count = in.readInt(); if (count < 0 || count > 12) return Optional.empty();
            var fields = new ArrayList<String>();
            for (int i = 0; i < count; i++) { String f = in.readUTF(); if (f.length() > 1024) return Optional.empty(); fields.add(f); }
            if (in.available() != 0 || !Set.of("CHAT", "QUEUE", "WITHDRAW", "JOIN", "ACK", "RETURN", "RESTORE", "REMOTECHAT", "GROUPJOIN", "PARTYCMD", "PARTYSTATE", "ROLECMD", "RESUMEVISIT", "RESUMEACK", "STATUS").contains(kind)) return Optional.empty();
            seen.values().removeIf(expiry -> expiry < now);
            if (seen.containsKey(nonce) || seen.size() >= 10000) return Optional.empty();
            seen.put(nonce, time + 30_000);
            return Optional.of(new Message(kind, player, List.copyOf(fields)));
        } catch (IOException | IllegalArgumentException e) { return Optional.empty(); }
    }
    private byte[] sign(byte[] bytes) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256")); return mac.doFinal(bytes);
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public static boolean id(String value) { return value.matches("[a-zA-Z0-9_-]{1,80}"); }
    public static String plain(String value) {
        return value.replaceAll("[\\p{Cntrl}§]", "");
    }
}
