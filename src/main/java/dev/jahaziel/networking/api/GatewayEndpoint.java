/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.api;

import java.util.Objects;
import java.util.UUID;

/**
 * The public gateway address behind which workspace ports are served. The gateway itself speaks
 * plain HTTP; anything terminating TLS in front of it configures {@code https} here. Port zero is
 * the ephemeral convention used by tests: the OS allocates the port at bind time.
 *
 * <p>Components: scheme ({@code http} or {@code https}), domain the public domain the gateway
 * routes, port the public gateway port or zero for ephemeral.
 */
public final class GatewayEndpoint {
    private final String scheme;
    private final String domain;
    private final int port;

    public GatewayEndpoint(String scheme, String domain, int port) {
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("scheme must be http or https");
        }
        Objects.requireNonNull(domain, "domain");
        if (domain.isBlank()) {
            throw new IllegalArgumentException("domain must not be blank");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        this.scheme = scheme;
        this.domain = domain;
        this.port = port;
    }

    public String scheme() {
        return scheme;
    }

    public String getScheme() {
        return scheme;
    }

    public String domain() {
        return domain;
    }

    public String getDomain() {
        return domain;
    }

    public int port() {
        return port;
    }

    public int getPort() {
        return port;
    }

    /**
     * Builds the tokenized URL of a workspace port.
     *
     * @param userSlug the URL-safe owner slug
     * @param workspacePort the bound workspace port
     * @param token the workspace-scoped session token
     * @return the public URL
     */
    public String url(String userSlug, int workspacePort, String token) {
        Objects.requireNonNull(userSlug, "userSlug");
        Objects.requireNonNull(token, "token");
        String authority = userSlug + "-" + workspacePort + "." + domain + portSuffix();
        return scheme + "://" + authority + "/?token=" + token;
    }

    /**
     * Builds a fixed-host preview URL whose path identifies the workspace and project port.
     *
     * @param workspaceId the workspace serving the project
     * @param workspacePort the registered project port
     * @param token the workspace and port scoped session token
     * @return the public URL on {@code preview.<domain>}
     */
    public String previewUrl(UUID workspaceId, int workspacePort, String token) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(token, "token");
        if (workspacePort < 1 || workspacePort > 65535) {
            throw new IllegalArgumentException("workspacePort must be between 1 and 65535");
        }
        if (!token.matches("[A-Za-z0-9._~-]+")) {
            throw new IllegalArgumentException("token must be URL-safe");
        }
        String authority = "preview." + domain + portSuffix();
        return scheme
                + "://"
                + authority
                + "/"
                + workspaceId
                + "/"
                + workspacePort
                + "/?token="
                + token;
    }

    private String portSuffix() {
        if (scheme.equals("http") && port == 80 || scheme.equals("https") && port == 443) {
            return "";
        }
        return ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GatewayEndpoint)) return false;
        GatewayEndpoint other = (GatewayEndpoint) o;
        return port == other.port && scheme.equals(other.scheme) && domain.equals(other.domain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, domain, port);
    }

    @Override
    public String toString() {
        return "GatewayEndpoint[scheme=" + scheme + ", domain=" + domain + ", port=" + port + "]";
    }
}
