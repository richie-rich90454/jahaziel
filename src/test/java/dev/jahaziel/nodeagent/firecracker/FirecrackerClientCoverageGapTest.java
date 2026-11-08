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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The shutdown and status-code branches of {@link FirecrackerClient} over a fake socket. */
class FirecrackerClientCoverageGapTest {
  @Test
  void shutdownAcceptsNoContent() throws Exception {
    Path socket = shortSocketPath();
    FakeApi api = new FakeApi(socket, "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n");
    try {
      new FirecrackerClient().shutdown(socket.toString());
    } finally {
      api.close();
    }
    assertTrue(api.seen.get().contains("PUT /actions"));
  }

  @Test
  void shutdownAcceptsOk() throws Exception {
    Path socket = shortSocketPath();
    FakeApi api = new FakeApi(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
    try {
      new FirecrackerClient().shutdown(socket.toString());
    } finally {
      api.close();
    }
    assertTrue(api.seen.get().contains("PUT /actions"));
  }

  @Test
  void shutdownRefusesError() throws Exception {
    Path socket = shortSocketPath();
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

  private static Path shortSocketPath() {
    Path directory =
        java.io.File.separatorChar == '\\'
            ? Path.of(System.getProperty("user.dir"), ".cache")
            : Path.of("/tmp");
    if (java.io.File.separatorChar == '\\') {
      try {
        Files.createDirectories(directory);
      } catch (IOException failure) {
        throw new IllegalStateException("Could not create the short socket directory", failure);
      }
    }
    String suffix = UUID.randomUUID().toString().replace("-", "");
    return directory.resolve("j" + suffix + ".sock");
  }

  /** Single-connection fake Firecracker API over a Unix socket. */
  private static final class FakeApi {
    private final Path socketPath;
    private final String response;
    private final AtomicReference<String> seen = new AtomicReference<>("");
    private final ServerSocketChannel listener;
    private final Thread server;

    FakeApi(Path socketPath, String response) throws IOException {
      this.socketPath = socketPath;
      this.response = response;
      listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      listener.bind(UnixDomainSocketAddress.of(socketPath));
      server = Thread.ofPlatform().daemon().start(this::serve);
    }

    void close() throws Exception {
      listener.close();
      server.join(10000);
      Files.deleteIfExists(socketPath);
    }

    private void serve() {
      try (listener) {
        try (SocketChannel accepted = listener.accept()) {
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
