/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Fixed-window API rate limiter. Exceeded rates return a 429 decision. */
public final class RateLimiter {
    /** One fixed window of recorded state. */
    private static final class Window {
        private long startMillis;
        private int count;

        /** Creates a window at the given instant. */
        Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }

    private final Map<String, Window> windows = new HashMap<String, Window>();
    private final Clock clock;

    /** Creates a limiter using the system clock. */
    public RateLimiter() {
        this(Clock.systemUTC());
    }

    /** Creates a limiter with an explicit clock. */
    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Attempts one request against a fixed window.
     *
     * @param key the caller key, typically a user or API token id
     * @param limit the maximum requests per window
     * @param window the window length
     * @return an allowed decision or a 429 decision
     */
    public synchronized LimitDecision tryAcquire(String key, int limit, Duration window) {
        if (key == null || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("invalid rate-limit parameters");
        }
        long now = clock.millis();
        Window state = windows.get(key);
        if (state == null || now - state.startMillis >= window.toMillis()) {
            state = new Window(now);
            windows.put(key, state);
        }
        if (state.count >= limit) {
            return LimitDecision.throttled(
                    "rate limit exceeded for "
                            + key
                            + ": "
                            + state.count
                            + "/"
                            + limit
                            + " per "
                            + window);
        }
        state.count++;
        return LimitDecision.allow();
    }

    /** Returns the request count in the current window for a key. */
    public synchronized int count(String key) {
        Window state = windows.get(key);
        return state == null ? 0 : state.count;
    }

    /** Clears all windows. */
    public synchronized void reset() {
        windows.clear();
    }
}
