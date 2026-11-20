/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.routing;

import java.util.Objects;

/** A parsed wildcard host. Components: userSlug the URL-safe user slug, port the workspace port. */
public final class ParsedHost {
    private final String userSlug;
    private final int port;

    public ParsedHost(String userSlug, int port) {
        if (userSlug == null || userSlug.isBlank()) {
            throw new IllegalArgumentException("userSlug must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        Objects.requireNonNull(userSlug, "userSlug");
        this.userSlug = userSlug;
        this.port = port;
    }

    public String userSlug() {
        return userSlug;
    }

    public String getUserSlug() {
        return userSlug;
    }

    public int port() {
        return port;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParsedHost)) return false;
        ParsedHost other = (ParsedHost) o;
        return port == other.port && userSlug.equals(other.userSlug);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userSlug, port);
    }

    @Override
    public String toString() {
        return "ParsedHost[userSlug=" + userSlug + ", port=" + port + "]";
    }
}
