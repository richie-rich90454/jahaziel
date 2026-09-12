/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.proxy;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.UUID;

/** Resolves workspace ports to loopback addresses. Single-node stand-in for vsock dialing. */
public final class LoopbackTargetResolver implements TargetResolver {
  @Override
  public InetSocketAddress resolve(UUID workspaceId, int port) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    return new InetSocketAddress("127.0.0.1", port);
  }
}
