package de.maxhenkel.voicechat.net;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PacketRateLimiterTest {

    @Test
    void testAcceptsBurstWithinLimit() {
        int maxPerSecond = 10;
        PacketRateLimiter limiter = new PacketRateLimiter(maxPerSecond);
        UUID player = UUID.randomUUID();
        // The window is 5 seconds, so a burst of up to 10 * 5 = 50 packets is allowed
        for (int i = 0; i < maxPerSecond * 5; i++) {
            assertTrue(limiter.allow(player));
        }
        assertFalse(limiter.allow(player));
    }

    @Test
    void testDisabledRateLimitAlwaysAllows() {
        PacketRateLimiter limiter = new PacketRateLimiter(-1);
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(limiter.allow(player));
        }
    }

    @Test
    void testPlayersAreLimitedIndividually() {
        PacketRateLimiter limiter = new PacketRateLimiter(10);
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        for (int i = 0; i < 50; i++) {
            assertTrue(limiter.allow(playerA));
        }
        assertFalse(limiter.allow(playerA));
        assertTrue(limiter.allow(playerB));
    }

    @Test
    void testRateLimiterRecoversOverTime() throws Exception {
        int maxPerSecond = 100;
        PacketRateLimiter limiter = new PacketRateLimiter(maxPerSecond);
        UUID player = UUID.randomUUID();
        for (int i = 0; i < maxPerSecond * 5; i++) {
            assertTrue(limiter.allow(player));
        }
        assertFalse(limiter.allow(player));

        // Each token refills every (5000 ms / 500) = 10 ms, so sleeping 150 ms
        // guarantees the limiter has recovered enough to accept a new packet.
        Thread.sleep(150);
        assertTrue(limiter.allow(player));
    }
}