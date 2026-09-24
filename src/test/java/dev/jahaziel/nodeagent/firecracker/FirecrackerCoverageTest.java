/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.firecracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** Extra branches of {@link FirecrackerClient} plus {@link FirecrackerException}. */
class FirecrackerCoverageTest {
  @TempDir private Path root;

  @Test
  void statusRejectsMalformed() {
    assertThrows(
        FirecrackerException.class,
        () -> FirecrackerClient.statusOf("HELLO\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));
    assertThrows(
        FirecrackerException.class,
        () ->
            FirecrackerClient.statusOf(
                "HTTP/1.1 XYZ oops\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));
    FirecrackerException failure =
        assertThrows(FirecrackerException.class, () -> FirecrackerClient.statusOf(new byte[0]));
    assertEquals("<response>", failure.path());
    assertEquals(-1, failure.status());
  }

  @Test
  void exceptionCarriesFields() {
    FirecrackerException viaStatus = new FirecrackerException("/drives/rootfs", 500, "boom");
    assertEquals("/drives/rootfs", viaStatus.path());
    assertEquals(500, viaStatus.status());
    assertEquals("boom", viaStatus.body());
    assertTrue(viaStatus.getMessage().contains("/drives/rootfs"));
    java.io.IOException cause = new java.io.IOException("down");
    FirecrackerException viaCause = new FirecrackerException("/actions", cause);
    assertEquals("/actions", viaCause.path());
    assertEquals(-1, viaCause.status());
    assertEquals("", viaCause.body());
    assertEquals(cause, viaCause.getCause());
  }

  @Test
  void bootFailsWithoutSocket() {
    FirecrackerClient client = new FirecrackerClient();
    Path missing = root.resolve("missing.sock");
    assertThrows(
        FirecrackerException.class,
        () ->
            client.boot(
                missing.toString(),
                "/rootfs.img",
                "tap-1",
                "AA:FC:00:00:00:01",
                1,
                512,
                "/kernel",
                List.of()));
    assertThrows(FirecrackerException.class, () -> client.shutdown(missing.toString()));
  }

  @Test
  void bootRoundTripOverUnixSocket() throws Exception {
    Path socketPath = root.resolve("api.sock");
    AtomicReference<String> lastRequest = new AtomicReference<>("");
    Thread server =
        Thread.ofVirtual()
            .start(
                () -> {
                  try (ServerSocketChannel serverCh =
                      ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                    serverCh.bind(UnixDomainSocketAddress.of(socketPath));
                    for (int i = 0; i < 6; i++) {
                      try (SocketChannel accepted = serverCh.accept()) {
                        byte[] request = readAll(accepted);
                        lastRequest.set(new String(request, StandardCharsets.UTF_8));
                        String response = "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n";
                        accepted.write(
                            ByteBuffer.wrap(response.getBytes(StandardCharsets.US_ASCII)));
                      }
                    }
                  } catch (Exception ignored) {
                    // Test server; client assertions carry the outcome.
                  }
                });
    try {
      // ponytail: poll for bind, no extra helper.
      for (int i = 0; i < 100 && !java.nio.file.Files.exists(socketPath); i++) {
        Thread.sleep(20);
      }
      FirecrackerClient client = new FirecrackerClient();
      client.boot(
          socketPath.toString(),
          "/rootfs.img",
          "tap-1",
          "AA:FC:00:00:00:01",
          2,
          1024,
          "/kernel",
          List.of("jahaziel.workspace=x"));
      assertTrue(lastRequest.get().contains("PUT /actions"));
    } finally {
      server.interrupt();
      server.join(2000);
    }
  }

  @Test
  void bootRefusesErrorStatus() throws Exception {
    Path socketPath = root.resolve("err.sock");
    Thread server =
        Thread.ofVirtual()
            .start(
                () -> {
                  try (ServerSocketChannel serverCh =
                      ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                    serverCh.bind(UnixDomainSocketAddress.of(socketPath));
                    try (SocketChannel accepted = serverCh.accept()) {
                      readAll(accepted);
                      String response = "HTTP/1.1 400 Bad Request\r\nContent-Length: 2\r\n\r\n{}";
                      accepted.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.US_ASCII)));
                    }
                  } catch (Exception ignored) {
                    // Best effort.
                  }
                });
    try {
      for (int i = 0; i < 100 && !java.nio.file.Files.exists(socketPath); i++) {
        Thread.sleep(20);
      }
      FirecrackerClient client = new FirecrackerClient();
      assertThrows(FirecrackerException.class, () -> client.shutdown(socketPath.toString()));
    } finally {
      server.interrupt();
      server.join(2000);
    }
  }

  @Test
  void writeJsonFailureMapsToIllegalArgument() throws Exception {
    FirecrackerClient client = new FirecrackerClient();
    Field mapperField = FirecrackerClient.class.getDeclaredField("mapper");
    mapperField.setAccessible(true);
    ObjectMapper failing =
        new ObjectMapper() {
          @Override
          public String writeValueAsString(Object value)
              throws tools.jackson.core.JacksonException {
            throw new tools.jackson.core.JacksonException("boom") {};
          }
        };
    ObjectMapper original = (ObjectMapper) mapperField.get(client);
    mapperField.set(client, failing);
    try {
      Path missing = root.resolve("never.sock");
      assertThrows(IllegalArgumentException.class, () -> client.shutdown(missing.toString()));
    } finally {
      mapperField.set(client, original);
    }
    // ponytail: direct writeJson check via boot with failing mapper hits same branch.
    mapperField.set(client, failing);
    try {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              client.boot(
                  root.resolve("x.sock").toString(), "/r", "t", "m", 1, 128, "/k", List.of()));
    } finally {
      mapperField.set(client, original);
    }
  }

  private static byte[] readAll(SocketChannel channel) throws Exception {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    ByteBuffer buffer = ByteBuffer.allocate(8192);
    channel.configureBlocking(true);
    int read;
    while ((read = channel.read(buffer)) > 0) {
      out.write(buffer.array(), 0, read);
      buffer.clear();
      if (out.size() > 65536) {
        break;
      }
    }
    try {
      channel.shutdownInput();
    } catch (Exception ignored) {
      // Best effort.
    }
    return out.toByteArray();
  }
}
