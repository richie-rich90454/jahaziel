/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Mutable clock used to test rotation, windows and backoff without sleeping. */
final class TestClock extends Clock {
    private Instant now;

    /** Creates a clock at a fixed ISO instant. */
    TestClock(String instant) {
        now = Instant.parse(instant);
    }

    /** Advances the clock by a number of seconds. */
    void advanceSeconds(long seconds) {
        now = now.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
