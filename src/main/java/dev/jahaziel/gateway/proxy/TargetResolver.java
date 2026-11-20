/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.proxy;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Resolves a workspace port to a backend address. The loopback implementation serves single-node
 * development; vsock dialing replaces it once the node-agent channel lands.
 */
public abstract class TargetResolver {
    /**
     * Resolves the backend serving a workspace port.
     *
     * @param workspaceId the workspace identifier
     * @param port the workspace port
     * @return the backend address
     */
    public abstract InetSocketAddress resolve(UUID workspaceId, int port);
}
