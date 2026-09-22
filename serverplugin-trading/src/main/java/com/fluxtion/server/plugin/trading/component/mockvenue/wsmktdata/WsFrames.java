package com.fluxtion.server.plugin.trading.component.mockvenue.wsmktdata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Minimal, dependency-free RFC 6455 (WebSocket) server-side helpers: the opening-handshake
 * accept key, text-frame encoding (server -> client, unmasked) and inbound frame decoding
 * (client -> server, masked). Just enough for a mock venue that speaks text JSON; no
 * extensions, no continuation beyond simple reassembly by the caller.
 */
public final class WsFrames {

    /** RFC 6455 §1.3 magic GUID appended to the client key before hashing. */
    public static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public static final int OP_CONTINUATION = 0x0;
    public static final int OP_TEXT = 0x1;
    public static final int OP_BINARY = 0x2;
    public static final int OP_CLOSE = 0x8;
    public static final int OP_PING = 0x9;
    public static final int OP_PONG = 0xA;

    private WsFrames() {
    }

    /** A single decoded inbound frame. {@code text} is set for text frames, else null. */
    public record Frame(int opcode, boolean fin, String text, byte[] payload) {
    }

    /**
     * Compute the {@code Sec-WebSocket-Accept} response value for a client
     * {@code Sec-WebSocket-Key} (RFC 6455 §4.2.2): base64(SHA-1(key + WS_GUID)).
     */
    public static String acceptKey(String secWebSocketKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((secWebSocketKey + WS_GUID).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }

    /** Encode a text payload as a single final, unmasked server->client frame. */
    public static byte[] encodeText(String text) {
        return encode(OP_TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Encode a control/close/pong frame with the given opcode and (small) payload. */
    public static byte[] encode(int opcode, byte[] payload) {
        int len = payload.length;
        int headerLen = 2 + (len <= 125 ? 0 : len <= 0xFFFF ? 2 : 8);
        byte[] frame = new byte[headerLen + len];
        frame[0] = (byte) (0x80 | (opcode & 0x0F)); // FIN + opcode
        int p;
        if (len <= 125) {
            frame[1] = (byte) len;
            p = 2;
        } else if (len <= 0xFFFF) {
            frame[1] = (byte) 126;
            frame[2] = (byte) ((len >>> 8) & 0xFF);
            frame[3] = (byte) (len & 0xFF);
            p = 4;
        } else {
            frame[1] = (byte) 127;
            for (int i = 0; i < 8; i++) {
                frame[2 + i] = (byte) ((((long) len) >>> (8 * (7 - i))) & 0xFF);
            }
            p = 10;
        }
        System.arraycopy(payload, 0, frame, p, len);
        return frame;
    }

    /**
     * Read and decode one inbound (client->server) frame from the stream. Client frames MUST be
     * masked (RFC 6455 §5.1); this unmasks them. Returns null at end of stream.
     */
    public static Frame readFrame(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 < 0) {
            return null; // stream closed
        }
        boolean fin = (b0 & 0x80) != 0;
        int opcode = b0 & 0x0F;

        int b1 = readByte(in);
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;
        if (len == 126) {
            len = ((long) readByte(in) << 8) | readByte(in);
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) {
                len = (len << 8) | readByte(in);
            }
        }
        if (len > Integer.MAX_VALUE) {
            throw new IOException("frame too large: " + len);
        }

        byte[] mask = null;
        if (masked) {
            mask = readN(in, 4);
        }
        byte[] payload = readN(in, (int) len);
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= mask[i & 3];
            }
        }
        String text = opcode == OP_TEXT ? new String(payload, StandardCharsets.UTF_8) : null;
        return new Frame(opcode, fin, text, payload);
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new IOException("unexpected end of stream");
        }
        return b & 0xFF;
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] buf = in.readNBytes(n);
        if (buf.length != n) {
            throw new IOException("expected " + n + " bytes, got " + buf.length);
        }
        return buf;
    }
}
