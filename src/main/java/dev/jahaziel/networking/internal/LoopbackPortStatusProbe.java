/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import dev.jahaziel.networking.api.PortStatusProbe;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Tests the same loopback address used by the single-server gateway. */
public final class LoopbackPortStatusProbe implements PortStatusProbe {
  private static final int CONNECT_TIMEOUT_MILLIS = 150;

  @Override
  public boolean isListening(int port) {
    if (port < 1 || port > 65535) return false;
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MILLIS);
      return true;
    } catch (IOException unavailable) {
      return false;
    }
  }
}
