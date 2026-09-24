/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.firecracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The shutdown and status-code branches of {@link FirecrackerClient} over a fake socket. */
class FirecrackerClientCoverageGapTest {
  @Test
  void shutdownAcceptsNoContent(@TempDir Path root) throws Exception {
    Path socket = root.resolve("ok204.sock");
    FakeApi api = new FakeApi(socket, "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n");
    try {
      new FirecrackerClient().shutdown(socket.toString());
    } finally {
      api.close();
    }
    assertTrue(api.seen.get().contains("PUT /actions"));
  }

  @Test
  void shutdownAcceptsOk(@TempDir Path root) throws Exception {
    Path socket = root.resolve("ok200.sock");
    FakeApi api = new FakeApi(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
    try {
      new FirecrackerClient().shutdown(socket.toString());
    } finally {
      api.close();
    }
    assertTrue(api.seen.get().contains("PUT /actions"));
  }

  @Test
  void shutdownRefusesError(@TempDir Path root) throws Exception {
    Path socket = root.resolve("err.sock");
    FakeApi api = new FakeApi(socket, "HTTP/1.1 500 Internal Error\r\nContent-Length: 2\r\n\r\n{}");
    try {
      FirecrackerException failure =
          assertThrows(
              FirecrackerException.class,
              () -> new FirecrackerClient().shutdown(socket.toString()));
      assertEquals("/actions", failure.path());
      assertEquals(500, failure.status());
    } finally {
      api.close();
    }
  }

  /** Single-connection fake Firecracker API over a Unix socket. */
  private static final class FakeApi {
    private final Path socketPath;
    private final String response;
    private final AtomicReference<String> seen = new AtomicReference<>("");
    private final Thread server;

    FakeApi(Path socketPath, String response) throws InterruptedException {
      this.socketPath = socketPath;
      this.response = response;
      server = Thread.ofVirtual().start(this::serve);
      for (int i = 0; i < 200 && !Files.exists(socketPath); i++) {
        Thread.sleep(10);
      }
    }

    void close() throws InterruptedException {
      server.join(10000);
    }

    private void serve() {
      try (ServerSocketChannel serverCh = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
        serverCh.bind(UnixDomainSocketAddress.of(socketPath));
        try (SocketChannel accepted = serverCh.accept()) {
          accepted.configureBlocking(true);
          seen.set(new String(readAll(accepted), StandardCharsets.UTF_8));
          accepted.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.US_ASCII)));
        }
      } catch (IOException ignored) {
        // Test server; client assertions carry the outcome.
      }
    }
  }

  private static byte[] readAll(SocketChannel channel) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteBuffer buffer = ByteBuffer.allocate(8192);
    int read;
    while ((read = channel.read(buffer)) > 0) {
      out.write(buffer.array(), 0, read);
      buffer.clear();
    }
    return out.toByteArray();
  }
}
