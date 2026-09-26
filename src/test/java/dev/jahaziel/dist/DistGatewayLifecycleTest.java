/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.gateway.routing.WorkspaceDirectory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.SmartLifecycle;

/** Lifecycle transitions of the standalone gateway bean. */
class DistGatewayLifecycleTest {
  @TempDir private Path tmp;

  @Test
  void gatewayStartsServesAndStops() throws Exception {
    Path keys = Files.createDirectory(tmp.resolve("keys"));
    DevKeys.Keys created = DevKeys.ensure(keys);
    int port;
    try (ServerSocket probe = new ServerSocket(0)) {
      port = probe.getLocalPort();
    }
    SmartLifecycle gateway =
        new DistConfig()
            .distGateway(
                port,
                "gateway.test",
                created.publicKey().toString(),
                "https://gateway.test",
                900,
                new WorkspaceDirectory());
    assertFalse(gateway.isRunning());
    gateway.start();
    try {
      assertTrue(serving(port));
      assertTrue(gateway.isRunning());
    } finally {
      gateway.stop();
    }
    long deadline = System.currentTimeMillis() + 5000;
    while (gateway.isRunning() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    assertFalse(gateway.isRunning());
  }

  /** Spins until the gateway accepts connections, so stop() cannot land first. */
  private static boolean serving(int port) throws Exception {
    long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline) {
      try (Socket probe = new Socket()) {
        probe.connect(new InetSocketAddress("127.0.0.1", port), 50);
        return true;
      } catch (IOException notYet) {
        Thread.sleep(10);
      }
    }
    return false;
  }
}
