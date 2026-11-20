/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A registered port binding. Components: userId the owning user, port the bound port, workspaceId
 * the serving workspace, protocol one of http, https, tcp, registeredAt when registered.
 */
public final class PortBinding {
    private static final Set<String> PROTOCOLS = Set.of("http", "https", "tcp");

    private final UUID userId;
    private final int port;
    private final UUID workspaceId;
    private final String protocol;
    private final Instant registeredAt;

    public PortBinding(
            UUID userId, int port, UUID workspaceId, String protocol, Instant registeredAt) {
        Objects.requireNonNull(userId, "userId");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (protocol == null || !PROTOCOLS.contains(protocol)) {
            throw new IllegalArgumentException("protocol must be one of http, https, tcp");
        }
        Objects.requireNonNull(registeredAt, "registeredAt");
        this.userId = userId;
        this.port = port;
        this.workspaceId = workspaceId;
        this.protocol = protocol;
        this.registeredAt = registeredAt;
    }

    public UUID userId() {
        return userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int port() {
        return port;
    }

    public int getPort() {
        return port;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String protocol() {
        return protocol;
    }

    public String getProtocol() {
        return protocol;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PortBinding)) return false;
        PortBinding other = (PortBinding) o;
        return port == other.port
                && userId.equals(other.userId)
                && workspaceId.equals(other.workspaceId)
                && protocol.equals(other.protocol)
                && registeredAt.equals(other.registeredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, port, workspaceId, protocol, registeredAt);
    }

    @Override
    public String toString() {
        return "PortBinding[userId="
                + userId
                + ", port="
                + port
                + ", workspaceId="
                + workspaceId
                + ", protocol="
                + protocol
                + ", registeredAt="
                + registeredAt
                + "]";
    }
}
