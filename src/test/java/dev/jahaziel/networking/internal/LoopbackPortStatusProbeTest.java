/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

class LoopbackPortStatusProbeTest {
  private final LoopbackPortStatusProbe probe = new LoopbackPortStatusProbe();

  @Test
  void reportsListeningLoopbackPort() throws IOException {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      assertTrue(probe.isListening(server.getLocalPort()));
    }
  }

  @Test
  void reportsClosedAndInvalidPortsAsNotListening() throws IOException {
    int port;
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      port = server.getLocalPort();
    }
    assertFalse(probe.isListening(port));
    assertFalse(probe.isListening(0));
    assertFalse(probe.isListening(65536));
  }
}
