/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.gateway.auth.GatewaySession;
import dev.jahaziel.gateway.auth.JwtVerifier;
import dev.jahaziel.gateway.routing.HostParser;
import dev.jahaziel.gateway.routing.WorkspaceDirectory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GatewayServerCoverageGapTest {
  private GatewayServer server(JwtVerifier tokens, WorkspaceDirectory dir) {
    return new GatewayServer(
        new HostParser("code.test"), tokens, dir, new LoopbackTargetResolver());
  }

  private String send(int port, String raw) throws Exception {
    try (Socket s = new Socket("127.0.0.1", port)) {
      s.setSoTimeout(5000);
      s.getOutputStream().write(raw.getBytes(StandardCharsets.US_ASCII));
      s.getOutputStream().flush();
      s.shutdownOutput();
      byte[] all = s.getInputStream().readAllBytes();
      String text = new String(all, StandardCharsets.US_ASCII);
      int idx = text.indexOf("\r\n\r\n");
      return idx < 0 ? text : text.substring(idx + 4);
    }
  }

  private int live(GatewayServer target) throws Exception {
    int port;
    try (ServerSocket probe = new ServerSocket(0)) {
      port = probe.getLocalPort();
    }
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                target.serve(port);
              } catch (RuntimeException ignored) {
                // Server stopped.
              }
            });
    for (int i = 0; i < 50; i++) {
      try (Socket probe = new Socket("127.0.0.1", port)) {
        return port;
      } catch (Exception retry) {
        Thread.sleep(100);
      }
    }
    throw new IllegalStateException("gateway did not start");
  }

  @Test
  void stopIgnoresCloseFailureAndNull() throws Exception {
    GatewayServer target = server(mock(JwtVerifier.class), new WorkspaceDirectory());
    target.stop();
    ServerSocket socket = mock(ServerSocket.class);
    doThrow(new java.io.IOException("gone")).when(socket).close();
    Field field = GatewayServer.class.getDeclaredField("server");
    field.setAccessible(true);
    field.set(target, socket);
    target.stop();
  }

  @Test
  void handleQuietlyIgnoresCloseFailure() throws Exception {
    GatewayServer target = server(mock(JwtVerifier.class), new WorkspaceDirectory());
    Socket client = mock(Socket.class);
    when(client.getInputStream()).thenThrow(new java.io.IOException("dead"));
    doThrow(new java.io.IOException("gone")).when(client).close();
    Method handle = GatewayServer.class.getDeclaredMethod("handleQuietly", Socket.class);
    handle.setAccessible(true);
    handle.invoke(target, client);
  }

  @Test
  void parseRouteStripsPort() throws Exception {
    GatewayServer target = server(mock(JwtVerifier.class), new WorkspaceDirectory());
    Method parse =
        GatewayServer.class.getDeclaredMethod("parseRoute", GatewayServer.RequestHead.class);
    parse.setAccessible(true);
    GatewayServer.RequestHead head =
        GatewayServer.readHead(
            new ByteArrayInputStream(
                "GET / HTTP/1.1\r\nHost: richard-8080.code.test:1234\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII)));
    Object route = parse.invoke(target, head);
    assertTrue(route.toString().contains("richard"));
  }

  @Test
  void queryTokenAndBearerVariants() throws Exception {
    JwtVerifier tokens = mock(JwtVerifier.class);
    WorkspaceDirectory dir = new WorkspaceDirectory();
    UUID wid = UUID.randomUUID();
    UUID sub = UUID.randomUUID();
    when(tokens.verify("good")).thenReturn(new GatewaySession(sub, wid, List.of()));
    int dead;
    try (ServerSocket reserved = new ServerSocket(0)) {
      dead = reserved.getLocalPort();
    }
    dir.register("richard", dead, wid);
    String host = "richard-" + dead + ".code.test";
    GatewayServer target = server(tokens, dir);
    int port = live(target);
    try {
      assertEquals(
          "backend unreachable\n",
          send(port, "GET /?token=good HTTP/1.1\r\nHost: " + host + "\r\n\r\n"));
      assertEquals(
          "missing session token\n", send(port, "GET / HTTP/1.1\r\nHost: " + host + "\r\n\r\n"));
      assertEquals(
          "missing session token\n",
          send(port, "GET /?other=1 HTTP/1.1\r\nHost: " + host + "\r\n\r\n"));
      assertEquals(
          "missing session token\n",
          send(port, "GET /?flag HTTP/1.1\r\nHost: " + host + "\r\n\r\n"));
      assertEquals(
          "missing session token\n", send(port, "GET /=x HTTP/1.1\r\nHost: " + host + "\r\n\r\n"));
      assertEquals(
          "missing session token\n",
          send(
              port,
              "GET / HTTP/1.1\r\nHost: " + host + "\r\n" + "Authorization: Basic abc\r\n\r\n"));
      assertEquals(
          "backend unreachable\n",
          send(port, "GET /?flag&token=good HTTP/1.1\r\nHost: " + host + "\r\n\r\n"));
    } finally {
      target.stop();
    }
  }

  @Test
  void identityTransferEncodingPassesThrough() throws Exception {
    JwtVerifier tokens = mock(JwtVerifier.class);
    WorkspaceDirectory dir = new WorkspaceDirectory();
    UUID wid = UUID.randomUUID();
    UUID sub = UUID.randomUUID();
    when(tokens.verify("good")).thenReturn(new GatewaySession(sub, wid, List.of()));
    int dead;
    try (ServerSocket reserved = new ServerSocket(0)) {
      dead = reserved.getLocalPort();
    }
    dir.register("richard", dead, wid);
    GatewayServer target = server(tokens, dir);
    int port = live(target);
    try {
      assertEquals(
          "backend unreachable\n",
          send(
              port,
              "GET / HTTP/1.1\r\nHost: richard-"
                  + dead
                  + ".code.test\r\nAuthorization: Bearer good\r\n"
                  + "Transfer-Encoding: identity\r\n\r\n"));
    } finally {
      target.stop();
    }
  }

  @Test
  void forwardRequestWritesBodyTruncatesAndSkips() throws Exception {
    Method forward =
        GatewayServer.class.getDeclaredMethod(
            "forwardRequest", GatewayServer.RequestHead.class, InputStream.class, Socket.class);
    forward.setAccessible(true);
    GatewayServer.RequestHead withBody =
        GatewayServer.readHead(
            new ByteArrayInputStream(
                "POST / HTTP/1.1\r\nHost: h\r\nContent-Length: 3\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII)));
    Socket upstream = mock(Socket.class);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    when(upstream.getOutputStream()).thenReturn(out);
    forward.invoke(
        null,
        withBody,
        new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII)),
        upstream);
    assertTrue(new String(out.toByteArray(), StandardCharsets.US_ASCII).endsWith("abc"));
    Socket truncatedUp = mock(Socket.class);
    when(truncatedUp.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    try {
      forward.invoke(
          null,
          withBody,
          new ByteArrayInputStream("ab".getBytes(StandardCharsets.US_ASCII)),
          truncatedUp);
    } catch (java.lang.reflect.InvocationTargetException expected) {
      assertTrue(expected.getCause() instanceof java.io.IOException);
    }
    GatewayServer.RequestHead noBody =
        GatewayServer.readHead(
            new ByteArrayInputStream(
                "GET / HTTP/1.1\r\nHost: h\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));
    Socket idle = mock(Socket.class);
    ByteArrayOutputStream idleOut = new ByteArrayOutputStream();
    when(idle.getOutputStream()).thenReturn(idleOut);
    forward.invoke(null, noBody, new ByteArrayInputStream(new byte[0]), idle);
    assertTrue(idleOut.size() > 0);
  }

  @Test
  void writeErrorIgnoresGoneClient() throws Exception {
    Method write =
        GatewayServer.class.getDeclaredMethod(
            "writeError", Socket.class, GatewayServer.HttpFailure.class);
    write.setAccessible(true);
    Socket gone = mock(Socket.class);
    when(gone.getOutputStream()).thenThrow(new java.io.IOException("gone"));
    write.invoke(null, gone, new GatewayServer.HttpFailure(400, "oops"));
  }

  @Test
  void contentLengthVariants() throws Exception {
    GatewayServer.RequestHead five =
        GatewayServer.readHead(
            new ByteArrayInputStream(
                "POST / HTTP/1.1\r\nHost: h\r\nContent-Length: 5\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII)));
    assertEquals(5, five.contentLength());
    GatewayServer.RequestHead negative =
        GatewayServer.readHead(
            new ByteArrayInputStream(
                "POST / HTTP/1.1\r\nHost: h\r\nContent-Length: -1\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII)));
    assertThrows(GatewayServer.HttpFailure.class, negative::contentLength);
    GatewayServer.RequestHead absent =
        GatewayServer.readHead(
            new ByteArrayInputStream(
                "GET / HTTP/1.1\r\nHost: h\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));
    assertEquals(0, absent.contentLength());
  }

  @Test
  void readHeadToleratesLoneCrAndColonlessHeader() throws Exception {
    GatewayServer.RequestHead lone =
        GatewayServer.readHead(
            new ByteArrayInputStream(
                "GET /\rX HTTP/1.1\r\nHost: h\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));
    assertEquals("/X", lone.target());
    GatewayServer.RequestHead bare =
        GatewayServer.readHead(
            new ByteArrayInputStream(
                "GET / HTTP/1.1\r\nNoColon\r\nHost: h\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII)));
    assertEquals("h", bare.headers().get("host"));
    assertTrue(!bare.headers().containsKey("nocolon"));
  }

  @Test
  void serveExitsWhenStoppedBetweenAccepts() throws Exception {
    GatewayServer target = server(mock(JwtVerifier.class), new WorkspaceDirectory());
    int port;
    try (ServerSocket probe = new ServerSocket(0)) {
      port = probe.getLocalPort();
    }
    Thread worker =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    target.serve(port);
                  } catch (RuntimeException ignored) {
                    // Server stopped.
                  }
                });
    Field serverField = GatewayServer.class.getDeclaredField("server");
    serverField.setAccessible(true);
    long end = System.currentTimeMillis() + 10_000;
    while (serverField.get(target) == null && System.currentTimeMillis() < end) {
      Thread.sleep(50);
    }
    assertTrue(serverField.get(target) != null, "gateway never bound");
    Field running = GatewayServer.class.getDeclaredField("running");
    running.setAccessible(true);
    running.setBoolean(target, false);
    Socket last = new Socket("127.0.0.1", port);
    try {
      worker.join(15_000);
    } finally {
      last.close();
    }
    assertFalse(worker.isAlive());
    target.stop();
  }

  @Test
  void serveOccupiedPortThrows() throws Exception {
    GatewayServer target = server(mock(JwtVerifier.class), new WorkspaceDirectory());
    try (ServerSocket occupied = new ServerSocket(0)) {
      int port = occupied.getLocalPort();
      IllegalStateException failure =
          assertThrows(IllegalStateException.class, () -> target.serve(port));
      assertTrue(failure.getMessage().contains("gateway failed"));
    } finally {
      target.stop();
    }
  }
}
