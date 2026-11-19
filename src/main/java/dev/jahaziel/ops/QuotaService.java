/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-user and per-workspace quotas for storage, processes, sessions and snapshots. Violations
 * return a 403 decision; API call rates are handled separately by {@link RateLimiter} with 429.
 */
public final class QuotaService {
    /** Resource name for storage bytes. */
    public static final String STORAGE = "storage";

    /** Resource name for concurrent processes. */
    public static final String PROCESS = "process";

    /** Resource name for terminal and language sessions. */
    public static final String SESSION = "session";

    /** Resource name for retained snapshots. */
    public static final String SNAPSHOT = "snapshot";

    /** Typical defaults applied by callers that do not supply their own limits. */
    public static final class LimitSet {
        /** Maximum storage bytes per workspace. */
        public final long storageBytes = 1024L * 1024L * 1024L;

        /** Maximum concurrent processes per user. */
        public final long processes = 20L;

        /** Maximum simultaneous sessions per user. */
        public final long sessions = 50L;

        /** Maximum snapshots per workspace. */
        public final long snapshots = 30L;

        /** Creates a default limit set. */
        public LimitSet() {}
    }

    private final Map<String, Long> usage = new HashMap<String, Long>();

    /**
     * Reserves capacity, returning a 403 decision when the limit would be exceeded. Nothing is
     * consumed on a rejected reservation.
     */
    public LimitDecision reserve(
            String scope, String id, String resource, long amount, long limit) {
        if (scope == null || id == null || resource == null || amount < 0 || limit < 0) {
            throw new IllegalArgumentException("invalid quota reservation");
        }
        String key = key(scope, id, resource);
        long used = usage.containsKey(key) ? usage.get(key).longValue() : 0L;
        if (used + amount > limit) {
            return LimitDecision.forbidden(
                    resource
                            + " quota exceeded for "
                            + scope
                            + " "
                            + id
                            + ": "
                            + used
                            + "+"
                            + amount
                            + " > "
                            + limit);
        }
        usage.put(key, Long.valueOf(used + amount));
        return LimitDecision.allow();
    }

    /** Releases previously reserved capacity, never below zero. */
    public void release(String scope, String id, String resource, long amount) {
        String key = key(scope, id, resource);
        long used = usage.containsKey(key) ? usage.get(key).longValue() : 0L;
        long left = Math.max(0L, used - Math.max(0L, amount));
        usage.put(key, Long.valueOf(left));
    }

    /** Returns the currently used amount for a scope, id and resource. */
    public long usage(String scope, String id, String resource) {
        Long used = usage.get(key(scope, id, resource));
        return used == null ? 0L : used.longValue();
    }

    /** Clears all recorded usage. */
    public void reset() {
        usage.clear();
    }

    private static String key(String scope, String id, String resource) {
        return scope + "|" + id + "|" + resource;
    }
}
