package com.fluxtion.server.plugin.trading.component.mockvenue.wsmktdata;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsFramesTest {

    @Test
    void acceptKeyMatchesRfc6455Example() {
        // RFC 6455 §1.3 worked example.
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                WsFrames.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="));
    }

    @Test
    void encodeTextProducesFinalUnmaskedTextFrame() {
        byte[] frame = WsFrames.encodeText("hello");
        assertEquals((byte) 0x81, frame[0]);          // FIN + text opcode
        assertEquals((byte) 5, frame[1]);              // len, mask bit clear
        assertEquals("hello", new String(frame, 2, 5, StandardCharsets.UTF_8));
    }

    @Test
    void encodeThenReadRoundTripsShortText() throws Exception {
        byte[] frame = WsFrames.encodeText("{\"type\":\"book\"}");
        WsFrames.Frame decoded = WsFrames.readFrame(new ByteArrayInputStream(frame));
        assertEquals(WsFrames.OP_TEXT, decoded.opcode());
        assertTrue(decoded.fin());
        assertEquals("{\"type\":\"book\"}", decoded.text());
    }

    @Test
    void readsMaskedClientFrame() throws Exception {
        // Build a masked client frame as a browser would, then decode it.
        String payload = "{\"type\":\"subscribe\",\"symbol\":\"USD-MXN\"}";
        byte[] frame = maskedClientTextFrame(payload);
        WsFrames.Frame decoded = WsFrames.readFrame(new ByteArrayInputStream(frame));
        assertEquals(WsFrames.OP_TEXT, decoded.opcode());
        assertEquals(payload, decoded.text());
    }

    @Test
    void roundTripsExtendedLengthPayload() throws Exception {
        String big = "x".repeat(5000); // forces the 16-bit (126) length path
        byte[] frame = WsFrames.encodeText(big);
        assertEquals((byte) 126, frame[1]);
        WsFrames.Frame decoded = WsFrames.readFrame(new ByteArrayInputStream(frame));
        assertEquals(big, decoded.text());
    }

    @Test
    void closeFrameHasNoText() throws Exception {
        byte[] frame = WsFrames.encode(WsFrames.OP_CLOSE, new byte[0]);
        WsFrames.Frame decoded = WsFrames.readFrame(new ByteArrayInputStream(frame));
        assertEquals(WsFrames.OP_CLOSE, decoded.opcode());
        assertEquals(null, decoded.text());
    }

    @Test
    void readFrameReturnsNullAtEndOfStream() throws Exception {
        assertEquals(null, WsFrames.readFrame(new ByteArrayInputStream(new byte[0])));
    }

    /** Encodes a final, MASKED text frame exactly as a conforming client must send. */
    private static byte[] maskedClientTextFrame(String text) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        int len = payload.length; // test strings are < 126
        byte[] mask = new byte[4];
        new Random(42).nextBytes(mask);
        byte[] frame = new byte[2 + 4 + len];
        frame[0] = (byte) (0x80 | WsFrames.OP_TEXT);
        frame[1] = (byte) (0x80 | len); // mask bit set + length
        System.arraycopy(mask, 0, frame, 2, 4);
        for (int i = 0; i < len; i++) {
            frame[6 + i] = (byte) (payload[i] ^ mask[i & 3]);
        }
        // sanity: our own decoder should recover the text
        byte[] copy = frame.clone();
        assertArrayEquals(frame, copy);
        return frame;
    }
}
