/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import dev.jahaziel.gateway.auth.JwtVerifier;
import dev.jahaziel.gateway.routing.HostParser;
import dev.jahaziel.gateway.routing.WorkspaceDirectory;
import java.io.ByteArrayInputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Behavior of {@link GatewayServer} request parsing and relay. */
class GatewayServerTest {
  private static final byte[] REQUEST =
      "GET /app HTTP/1.1\r\nHost: richard-8080.code.test\r\n\r\n"
          .getBytes(StandardCharsets.US_ASCII);
  private static final byte[] REPLY =
      "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nhi"
          .getBytes(StandardCharsets.US_ASCII);

  @Test
  void readsHeadAndHeaders() throws Exception {
    GatewayServer.RequestHead head = GatewayServer.readHead(new ByteArrayInputStream(REQUEST));
    assertEquals("GET", head.method());
    assertEquals("/app", head.target());
    assertEquals("richard-8080.code.test", head.headers().get("host"));
    assertEquals(0, head.contentLength());
  }

  @Test
  void rejectsOversizedHead() {
    StringBuilder head = new StringBuilder("GET /app HTTP/1.1\r\nHost: x\r\nX-Pad: ");
    while (head.length() < 70 * 1024) {
      head.append('a');
    }
    head.append("\r\n\r\n");
    byte[] bytes = head.toString().getBytes(StandardCharsets.US_ASCII);
    GatewayServer.HttpFailure failure =
        assertThrows(
            GatewayServer.HttpFailure.class,
            () -> GatewayServer.readHead(new ByteArrayInputStream(bytes)));
    assertEquals(431, failure.status());
  }

  @Test
  void rejectsInvalidContentLength() throws Exception {
    GatewayServer.RequestHead head =
        GatewayServer.readHead(
            new ByteArrayInputStream(
                "POST /app HTTP/1.1\r\nHost: x\r\nContent-Length: abc\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII)));
    GatewayServer.HttpFailure failure =
        assertThrows(GatewayServer.HttpFailure.class, head::contentLength);
    assertEquals(400, failure.status());
  }

  @Test
  void stopReleasesPort() throws Exception {
    int port;
    try (ServerSocket probe = new ServerSocket(0)) {
      port = probe.getLocalPort();
    }
    GatewayServer server =
        new GatewayServer(
            new HostParser("code.test"),
            mock(JwtVerifier.class),
            mock(WorkspaceDirectory.class),
            mock(TargetResolver.class));
    Thread.ofVirtual().start(() -> server.serve(port));
    int connected = 0;
    for (int attempt = 0; attempt < 50 && connected == 0; attempt++) {
      try (Socket probe = new Socket("127.0.0.1", port)) {
        assertTrue(probe.isConnected());
        connected = 1;
      } catch (Exception ignored) {
        Thread.sleep(100);
      }
    }
    assertEquals(1, connected);
    server.stop();
    boolean rebound = false;
    for (int attempt = 0; attempt < 50 && !rebound; attempt++) {
      try (ServerSocket socket = new ServerSocket(port)) {
        rebound = socket.getLocalPort() == port;
      } catch (BindException busy) {
        Thread.sleep(100);
      }
    }
    assertTrue(rebound);
  }

  @Test
  void rejectsEmptyAndMalformed() {
    assertThrows(
        Exception.class, () -> GatewayServer.readHead(new ByteArrayInputStream(new byte[0])));
    assertThrows(
        Exception.class,
        () ->
            GatewayServer.readHead(
                new ByteArrayInputStream("BOGUS\r\n\r\n".getBytes(StandardCharsets.US_ASCII))));
  }

  @Test
  void proxiesOneExchange() throws Exception {
    CountDownLatch served = new CountDownLatch(1);
    byte[][] seen = new byte[1][];
    try (ServerSocket backend = new ServerSocket(0)) {
      Thread.ofVirtual()
          .start(
              () -> {
                try (Socket peer = backend.accept()) {
                  seen[0] = GatewayServer.readHead(peer.getInputStream()).raw();
                  peer.getOutputStream().write(REPLY);
                  served.countDown();
                } catch (Exception failure) {
                  throw new IllegalStateException(failure);
                }
              });
      try (Socket client = new Socket("127.0.0.1", backend.getLocalPort())) {
        client.getOutputStream().write(REQUEST);
        client.shutdownOutput();
        byte[] response = client.getInputStream().readAllBytes();
        assertTrue(new String(response, StandardCharsets.US_ASCII).endsWith("hi"));
      }
      assertTrue(served.await(5, TimeUnit.SECONDS));
    }
    assertEquals(
        new String(REQUEST, StandardCharsets.US_ASCII),
        new String(seen[0], StandardCharsets.US_ASCII));
  }
}
