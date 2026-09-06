package me.andromedov.paradisepaints.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaintPayloadTest {
    @Test void payloadInitializesWithExactServerChannel() {
        assertEquals("paradisepaints:paint", PaintPayload.TYPE.id().toString());
        assertSame(PaintPayload.TYPE, new PaintPayload(new byte[0]).type());
        assertNotNull(PaintPayload.CODEC);
    }
}
