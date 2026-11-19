/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import java.util.Locale;
import java.util.Map;

/**
 * Update channel selection. The default channel is {@code stable}, which pins a verified jar. The
 * optional {@code latest} channel tracks the newest published jar and therefore needs network
 * access. Channels come from the jar-adjacent {@code .env} or the real environment.
 */
public final class Channels {
    /** Environment key that selects the update channel. */
    public static final String ENV_KEY = "JAHAZIEL_UPDATE_CHANNEL";

    /** Pinned, verified channel. This is the default. */
    public static final String STABLE = "stable";

    /** Rolling channel that follows the newest published jar. */
    public static final String LATEST = "latest";

    private Channels() {}

    /**
     * Resolves the channel from a merged environment, falling back to the pinned default.
     *
     * @param env configuration values, real environment winning over the file
     * @return {@code stable} or {@code latest}
     */
    public static String resolve(Map<String, String> env) {
        if (env == null) {
            return STABLE;
        }
        String value = env.get(ENV_KEY);
        if (value == null) {
            return STABLE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (LATEST.equals(normalized)) {
            return LATEST;
        }
        return STABLE;
    }

    /**
     * A pinned channel only accepts explicitly staged, checksum-verified jars.
     *
     * @param channel the resolved channel
     * @return true for the stable channel
     */
    public static boolean isPinned(String channel) {
        return STABLE.equals(channel);
    }
}
