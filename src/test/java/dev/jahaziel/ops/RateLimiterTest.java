/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Behavior of {@link RateLimiter}: fixed windows and 429 decisions. */
class RateLimiterTest {
    @Test
    void throttlesAfterLimitAndResetsOnNewWindow() {
        TestClock clock = new TestClock("2026-01-01T00:00:00Z");
        RateLimiter limiter = new RateLimiter(clock);
        Duration window = Duration.ofMinutes(1);
        assertTrue(limiter.tryAcquire("alice", 2, window).allowed());
        assertTrue(limiter.tryAcquire("alice", 2, window).allowed());
        LimitDecision denied = limiter.tryAcquire("alice", 2, window);
        assertFalse(denied.allowed());
        assertEquals(LimitDecision.TOO_MANY, denied.statusCode());
        assertEquals(2, limiter.count("alice"));
        clock.advanceSeconds(61L);
        assertTrue(limiter.tryAcquire("alice", 2, window).allowed());
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(new TestClock("2026-01-01T00:00:00Z"));
        Duration window = Duration.ofMinutes(1);
        assertTrue(limiter.tryAcquire("alice", 1, window).allowed());
        assertFalse(limiter.tryAcquire("alice", 1, window).allowed());
        assertTrue(limiter.tryAcquire("bob", 1, window).allowed());
    }
}
