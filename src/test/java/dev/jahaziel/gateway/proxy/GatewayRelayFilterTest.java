/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import dev.jahaziel.gateway.auth.GatewaySession;
import dev.jahaziel.gateway.auth.JwtRejectedException;
import dev.jahaziel.gateway.auth.JwtVerifier;
import dev.jahaziel.gateway.routing.HostParser;
import dev.jahaziel.gateway.routing.WorkspaceDirectory;
import dev.jahaziel.identity.api.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.WebConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Behavior of {@link GatewayRelayFilter} against stub backends and mock requests. */
class GatewayRelayFilterTest {
  private static final Duration TTL = Duration.ofSeconds(900);

  private GatewayRelayFilter filter(
      JwtVerifier tokens, WorkspaceDirectory directory, TargetResolver targets) {
    return new GatewayRelayFilter(
        new HostParser("code.example.com"),
        tokens,
        directory,
        targets,
        mock(JwtService.class),
        TTL);
  }

  private GatewaySession session(UUID user, UUID workspace) {
    return new GatewaySession(user, workspace, List.of("workspace:port:8080"));
  }

  private MockHttpServletRequest workspaceRequest(String target) {
    int query = target.indexOf('?');
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", query < 0 ? target : target.substring(0, query));
    request.setServerName("richard-8080.code.example.com");
    if (query >= 0) {
      request.setQueryString(target.substring(query + 1));
    }
    return request;
  }

  @Test
  void passesThroughNonWorkspaceHosts() throws Exception {
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
    request.setServerName("localhost");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter(mock(JwtVerifier.class), mock(WorkspaceDirectory.class), mock(TargetResolver.class))
        .doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
    assertEquals(200, response.getStatus());
  }

  @Test
  void passesThroughNonHttpRequests() throws Exception {
    FilterChain chain = mock(FilterChain.class);
    ServletRequest request = mock(ServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    filter(mock(JwtVerifier.class), mock(WorkspaceDirectory.class), mock(TargetResolver.class))
        .doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
  }

  @Test
  void rejectsMissingToken() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter(mock(JwtVerifier.class), mock(WorkspaceDirectory.class), mock(TargetResolver.class))
        .doFilter(workspaceRequest("/"), response, mock(FilterChain.class));
    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("missing session token"));
  }

  @Test
  void rejectsBadToken() throws Exception {
    JwtVerifier tokens = mock(JwtVerifier.class);
    when(tokens.verify(any())).thenThrow(new JwtRejectedException("nope"));
    MockHttpServletRequest request = workspaceRequest("/?token=bad");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter(tokens, mock(WorkspaceDirectory.class), mock(TargetResolver.class))
        .doFilter(request, response, mock(FilterChain.class));
    assertEquals(401, response.getStatus());
  }

  @Test
  void rejectsUnknownRoute() throws Exception {
    JwtVerifier tokens = mock(JwtVerifier.class);
    UUID user = UUID.randomUUID();
    UUID workspace = UUID.randomUUID();
    when(tokens.verify("tok")).thenReturn(session(user, workspace));
    WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
    when(directory.lookup("richard", 8080)).thenReturn(Optional.empty());
    MockHttpServletRequest request = workspaceRequest("/?token=tok");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter(tokens, directory, mock(TargetResolver.class))
        .doFilter(request, response, mock(FilterChain.class));
    assertEquals(404, response.getStatus());
  }

  @Test
  void rejectsScopeMismatch() throws Exception {
    JwtVerifier tokens = mock(JwtVerifier.class);
    when(tokens.verify("tok")).thenReturn(session(UUID.randomUUID(), UUID.randomUUID()));
    WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
    UUID other = UUID.randomUUID();
    when(directory.lookup("richard", 8080)).thenReturn(Optional.of(other));
    MockHttpServletRequest request = workspaceRequest("/?token=tok");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter(tokens, directory, mock(TargetResolver.class))
        .doFilter(request, response, mock(FilterChain.class));
    assertEquals(403, response.getStatus());
  }

  @Test
  void rejectsChunkedUploads() throws Exception {
    JwtVerifier tokens = mock(JwtVerifier.class);
    UUID user = UUID.randomUUID();
    UUID workspace = UUID.randomUUID();
    when(tokens.verify("tok")).thenReturn(session(user, workspace));
    WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
    when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
    MockHttpServletRequest request = workspaceRequest("/?token=tok");
    request.addHeader("Transfer-Encoding", "chunked");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter(tokens, directory, mock(TargetResolver.class))
        .doFilter(request, response, mock(FilterChain.class));
    assertEquals(400, response.getStatus());
  }

  @Test
  void acceptsIdentityTransferEncoding() throws Exception {
    String response = "HTTP/1.1 200 OK\r\nContent-Length: 15\r\n\r\nidentity-marker";
    try (ServerSocket backend = new ServerSocket(0)) {
      Thread acceptor =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try (Socket peer = backend.accept()) {
                      InputStream in = peer.getInputStream();
                      ByteArrayOutputStream head = new ByteArrayOutputStream();
                      int current;
                      while ((current = in.read()) >= 0) {
                        head.write(current);
                        byte[] bytes = head.toByteArray();
                        int size = bytes.length;
                        if (size >= 4
                            && bytes[size - 4] == '\r'
                            && bytes[size - 3] == '\n'
                            && bytes[size - 2] == '\r'
                            && bytes[size - 1] == '\n') {
                          break;
                        }
                      }
                      OutputStream peerOut = peer.getOutputStream();
                      peerOut.write(response.getBytes(StandardCharsets.US_ASCII));
                      peerOut.flush();
                    } catch (IOException done) {
                    }
                  });
      int backendPort = backend.getLocalPort();
      UUID user = UUID.randomUUID();
      UUID workspace = UUID.randomUUID();
      JwtVerifier tokens = mock(JwtVerifier.class);
      when(tokens.verify("tok")).thenReturn(session(user, workspace));
      WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
      when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
      TargetResolver targets = mock(TargetResolver.class);
      when(targets.resolve(workspace, 8080))
          .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), backendPort));
      MockHttpServletRequest request = workspaceRequest("/?token=tok");
      request.addHeader("Transfer-Encoding", "identity");
      MockHttpServletResponse response2 = new MockHttpServletResponse();
      filter(tokens, directory, targets).doFilter(request, response2, mock(FilterChain.class));
      acceptor.join(TimeUnit.SECONDS.toMillis(10));
      assertEquals(200, response2.getStatus());
      assertTrue(response2.getContentAsString().contains("identity-marker"));
    }
  }

  @Test
  void failsOnTruncatedBody() throws Exception {
    HttpServer backend = dummyBackend("unused");
    int backendPort = backend.getAddress().getPort();
    try {
      UUID user = UUID.randomUUID();
      UUID workspace = UUID.randomUUID();
      JwtVerifier tokens = mock(JwtVerifier.class);
      when(tokens.verify("tok")).thenReturn(session(user, workspace));
      WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
      when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
      TargetResolver targets = mock(TargetResolver.class);
      when(targets.resolve(workspace, 8080))
          .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), backendPort));
      MockHttpServletRequest request =
          new MockHttpServletRequest("POST", "/upload") {
            @Override
            public long getContentLengthLong() {
              return 10;
            }
          };
      request.setServerName("richard-8080.code.example.com");
      request.setContent("abc".getBytes(StandardCharsets.UTF_8));
      request.addHeader("Authorization", "Bearer tok");
      MockHttpServletResponse response = new MockHttpServletResponse();
      GatewayRelayFilter relay = filter(tokens, directory, targets);
      assertThrows(
          IOException.class, () -> relay.doFilter(request, response, mock(FilterChain.class)));
    } finally {
      backend.stop(0);
    }
  }

  @Test
  void relaysHttpAndSetsSessionCookie() throws Exception {
    HttpServer backend = dummyBackend("ide-marker");
    int backendPort = backend.getAddress().getPort();
    try {
      UUID user = UUID.randomUUID();
      UUID workspace = UUID.randomUUID();
      JwtVerifier tokens = mock(JwtVerifier.class);
      when(tokens.verify("tok")).thenReturn(session(user, workspace));
      WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
      when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
      TargetResolver targets = mock(TargetResolver.class);
      when(targets.resolve(workspace, 8080))
          .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), backendPort));
      MockHttpServletRequest request = workspaceRequest("/workbench?token=tok&x=1");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter(tokens, directory, targets).doFilter(request, response, mock(FilterChain.class));
      assertEquals(200, response.getStatus());
      assertTrue(response.getContentAsString().contains("ide-marker"));
      String cookie = response.getHeader("Set-Cookie");
      assertTrue(cookie != null && cookie.startsWith("jahaziel-session=tok"), cookie);
    } finally {
      backend.stop(0);
    }
  }

  @Test
  void proxiesPostBodies() throws Exception {
    AtomicBody backend = new AtomicBody();
    HttpServer server = backendServer(backend);
    int backendPort = server.getAddress().getPort();
    try {
      UUID user = UUID.randomUUID();
      UUID workspace = UUID.randomUUID();
      JwtVerifier tokens = mock(JwtVerifier.class);
      when(tokens.verify("tok")).thenReturn(session(user, workspace));
      WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
      when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
      TargetResolver targets = mock(TargetResolver.class);
      when(targets.resolve(workspace, 8080))
          .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), backendPort));
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload");
      request.setServerName("richard-8080.code.example.com");
      request.setContent("payload-bytes".getBytes(StandardCharsets.UTF_8));
      request.addHeader("Content-Length", "13");
      request.addHeader("Authorization", "Bearer tok");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter(tokens, directory, targets).doFilter(request, response, mock(FilterChain.class));
      assertEquals(200, response.getStatus());
      assertEquals("payload-bytes", backend.received());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void answers502WhenBackendDown() throws Exception {
    int dead;
    try (ServerSocket socket = new ServerSocket(0)) {
      dead = socket.getLocalPort();
    }
    UUID user = UUID.randomUUID();
    UUID workspace = UUID.randomUUID();
    JwtVerifier tokens = mock(JwtVerifier.class);
    when(tokens.verify("tok")).thenReturn(session(user, workspace));
    WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
    when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
    TargetResolver targets = mock(TargetResolver.class);
    when(targets.resolve(workspace, 8080))
        .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), dead));
    MockHttpServletRequest request = workspaceRequest("/?token=tok");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter(tokens, directory, targets).doFilter(request, response, mock(FilterChain.class));
    assertEquals(502, response.getStatus());
  }

  @Test
  void upgradeDelegatesToRelayHandler() throws Exception {
    AtomicReference<Class<?>> captured = new AtomicReference<>();
    GatewayRelayFilter.RelayUpgrade handler = new GatewayRelayFilter.RelayUpgrade();
    MockHttpServletRequest spied =
        new MockHttpServletRequest() {
          @Override
          @SuppressWarnings("unchecked")
          public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
            captured.set(handlerClass);
            return (T) handler;
          }
        };
    spied.setMethod("GET");
    spied.setRequestURI("/");
    spied.setServerName("richard-8080.code.example.com");
    spied.addHeader("Upgrade", "websocket");
    spied.addHeader("Connection", "Upgrade");
    spied.addHeader("Keep-Alive", "timeout=5");
    spied.addHeader("Authorization", "Bearer tok");
    UUID user = UUID.randomUUID();
    UUID workspace = UUID.randomUUID();
    JwtVerifier tokens = mock(JwtVerifier.class);
    when(tokens.verify("tok")).thenReturn(session(user, workspace));
    WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
    when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter(tokens, directory, mock(TargetResolver.class))
        .doFilter(spied, response, mock(FilterChain.class));
    assertEquals(GatewayRelayFilter.RelayUpgrade.class, captured.get());
  }

  @Test
  void refreshesNearExpiryCookie() throws Exception {
    HttpServer backend = dummyBackend("ide-marker");
    int backendPort = backend.getAddress().getPort();
    try {
      UUID user = UUID.randomUUID();
      UUID workspace = UUID.randomUUID();
      String expiring = unsignedToken(Instant.now().plusSeconds(60).getEpochSecond());
      JwtVerifier tokens = mock(JwtVerifier.class);
      when(tokens.verify(expiring)).thenReturn(session(user, workspace));
      JwtService sessions = mock(JwtService.class);
      when(sessions.issue(any(), any(), any(), any())).thenReturn("fresh");
      WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
      when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
      TargetResolver targets = mock(TargetResolver.class);
      when(targets.resolve(workspace, 8080))
          .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), backendPort));
      GatewayRelayFilter relay =
          new GatewayRelayFilter(
              new HostParser("code.example.com"), tokens, directory, targets, sessions, TTL);
      MockHttpServletRequest request = workspaceRequest("/?token=" + expiring);
      MockHttpServletResponse response = new MockHttpServletResponse();
      relay.doFilter(request, response, mock(FilterChain.class));
      assertEquals(200, response.getStatus());
      assertTrue(response.getHeader("Set-Cookie").startsWith("jahaziel-session=fresh"));
    } finally {
      backend.stop(0);
    }
  }

  @Test
  void skipsRefreshWhenFarFromExpiry() throws Exception {
    HttpServer backend = dummyBackend("ide-marker");
    int backendPort = backend.getAddress().getPort();
    try {
      UUID user = UUID.randomUUID();
      UUID workspace = UUID.randomUUID();
      String lasting = unsignedToken(Instant.now().plusSeconds(3600).getEpochSecond());
      JwtVerifier tokens = mock(JwtVerifier.class);
      when(tokens.verify(lasting)).thenReturn(session(user, workspace));
      JwtService sessions = mock(JwtService.class);
      WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
      when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
      TargetResolver targets = mock(TargetResolver.class);
      when(targets.resolve(workspace, 8080))
          .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), backendPort));
      GatewayRelayFilter relay =
          new GatewayRelayFilter(
              new HostParser("code.example.com"), tokens, directory, targets, sessions, TTL);
      MockHttpServletRequest request = workspaceRequest("/?token=" + lasting);
      MockHttpServletResponse response = new MockHttpServletResponse();
      relay.doFilter(request, response, mock(FilterChain.class));
      assertEquals(200, response.getStatus());
      assertTrue(response.getHeader("Set-Cookie").startsWith("jahaziel-session=" + lasting));
      verify(sessions, never()).issue(any(), any(), any(), any());
    } finally {
      backend.stop(0);
    }
  }

  @Test
  void expiresAtParses() {
    assertTrue(GatewayRelayFilter.expiresAt("abc").isEmpty());
    assertTrue(GatewayRelayFilter.expiresAt(".abc").isEmpty());
    assertTrue(GatewayRelayFilter.expiresAt("a.").isEmpty());
    assertTrue(GatewayRelayFilter.expiresAt("a.!!!.c").isEmpty());
    assertTrue(GatewayRelayFilter.expiresAt(unsignedTokenPayload("{}")).isEmpty());
    assertTrue(
        GatewayRelayFilter.expiresAt(unsignedTokenPayload("{\"exp\":99999999999999999999}"))
            .isEmpty());
    assertEquals(
        1700000000L,
        GatewayRelayFilter.expiresAt(unsignedTokenPayload("{\"exp\":1700000000}"))
            .orElseThrow()
            .getEpochSecond());
    assertTrue(GatewayRelayFilter.needsRefresh(unsignedTokenPayload("{\"exp\":1700000000}")));
    assertTrue(!GatewayRelayFilter.needsRefresh("garbage"));
  }

  @Test
  void helpersBehave() {
    assertEquals("", GatewayRelayFilter.querySuffix(null));
    assertEquals("?a=1", GatewayRelayFilter.querySuffix("a=1"));
    assertNull(GatewayRelayFilter.stripToken(null));
    assertNull(GatewayRelayFilter.stripToken("  "));
    assertEquals("a=1", GatewayRelayFilter.stripToken("a=1"));
    assertNull(GatewayRelayFilter.stripToken("token=tok"));
    assertEquals("not-a-pair", GatewayRelayFilter.stripToken("not-a-pair"));
    assertEquals("a=1&b=2", GatewayRelayFilter.stripToken("a=1&token=tok&b=2"));
    assertTrue(GatewayRelayFilter.isUpgrade(Map.of("upgrade", "WebSocket")));
    assertTrue(!GatewayRelayFilter.isUpgrade(Map.of()));
  }

  @Test
  void readBackendHeadParses() throws Exception {
    String head = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nX-Multi: a\r\nX-Multi: b\r\n\r\n";
    GatewayRelayFilter.BackendHead parsed =
        GatewayRelayFilter.readBackendHead(
            new ByteArrayInputStream(head.getBytes(StandardCharsets.US_ASCII)));
    assertEquals(200, parsed.status());
    assertEquals(List.of("a", "b"), parsed.headers().get("x-multi"));
    assertThrows(
        GatewayServer.HttpFailure.class,
        () -> GatewayRelayFilter.readBackendHead(new ByteArrayInputStream(new byte[0])));
    assertThrows(
        GatewayServer.HttpFailure.class,
        () ->
            GatewayRelayFilter.readBackendHead(
                new ByteArrayInputStream(
                    "GARBAGE\r\nNoColonHere\r\n\r\n".getBytes(StandardCharsets.US_ASCII))));
    byte[] huge = new byte[70 * 1024];
    java.util.Arrays.fill(huge, (byte) 'x');
    assertThrows(
        GatewayServer.HttpFailure.class,
        () -> GatewayRelayFilter.readBackendHead(new ByteArrayInputStream(huge)));
  }

  @Test
  void relayUpgradeInitRelaysBothDirections() throws Exception {
    String rawRequest = "GET /chat HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\n\r\n";
    java.util.concurrent.CountDownLatch headReceived = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch abcReceived = new java.util.concurrent.CountDownLatch(1);
    try (ServerSocket backend = new ServerSocket(0)) {
      Thread acceptor =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try (Socket peer = backend.accept()) {
                      InputStream in = peer.getInputStream();
                      ByteArrayOutputStream head = new ByteArrayOutputStream();
                      int current;
                      while ((current = in.read()) >= 0) {
                        head.write(current);
                        byte[] bytes = head.toByteArray();
                        int size = bytes.length;
                        if (size >= 4
                            && bytes[size - 4] == '\r'
                            && bytes[size - 3] == '\n'
                            && bytes[size - 2] == '\r'
                            && bytes[size - 1] == '\n') {
                          break;
                        }
                      }
                      headReceived.countDown();
                      OutputStream peerOut = peer.getOutputStream();
                      peerOut.write(
                          "HTTP/1.1 101 Switching\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                      peerOut.flush();
                      byte[] three = in.readNBytes(3);
                      if (new String(three, StandardCharsets.US_ASCII).equals("ABC")) {
                        abcReceived.countDown();
                      }
                    } catch (IOException done) {
                    }
                  });
      int backendPort = backend.getLocalPort();
      GatewayRelayFilter.RelayState state =
          new GatewayRelayFilter.RelayState(
              new InetSocketAddress(InetAddress.getLoopbackAddress(), backendPort),
              rawRequest,
              GatewayRelayFilter::connect);
      GatewayRelayFilter.RelayUpgrade.pending().set(state);
      try {
        WebConnection connection = mock(WebConnection.class);
        ByteArrayOutputStream clientOut = new ByteArrayOutputStream();
        byte[] abc = "ABC".getBytes(StandardCharsets.US_ASCII);
        jakarta.servlet.ServletInputStream clientIn =
            new jakarta.servlet.ServletInputStream() {
              private final ByteArrayInputStream delegate = new ByteArrayInputStream(abc);

              @Override
              public int read() {
                return delegate.read();
              }

              @Override
              public boolean isFinished() {
                return delegate.available() == 0;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(jakarta.servlet.ReadListener listener) {}
            };
        jakarta.servlet.ServletOutputStream clientStream =
            new jakarta.servlet.ServletOutputStream() {
              @Override
              public void write(int b) {
                clientOut.write(b);
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setWriteListener(jakarta.servlet.WriteListener listener) {}
            };
        when(connection.getInputStream()).thenReturn(clientIn);
        when(connection.getOutputStream()).thenReturn(clientStream);
        new GatewayRelayFilter.RelayUpgrade().init(connection);
        assertTrue(headReceived.await(10, TimeUnit.SECONDS), "backend never saw the request");
        assertTrue(abcReceived.await(10, TimeUnit.SECONDS), "backend never saw ABC");
        String seen = "";
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
          seen = clientOut.toString(StandardCharsets.UTF_8);
          if (seen.contains("101 Switching")) {
            break;
          }
          Thread.sleep(10);
        }
        assertTrue(seen.contains("101 Switching"), seen);
        acceptor.join(TimeUnit.SECONDS.toMillis(10));
      } finally {
        GatewayRelayFilter.RelayUpgrade.pending().remove();
      }
    }
  }

  @Test
  void relayUpgradeInitRequiresState() {
    GatewayRelayFilter.RelayUpgrade.pending().remove();
    assertThrows(
        IllegalStateException.class,
        () -> new GatewayRelayFilter.RelayUpgrade().init(mock(WebConnection.class)));
  }

  @Test
  void relayUpgradeInitFailsOnDeadBackend() throws Exception {
    int dead;
    try (ServerSocket socket = new ServerSocket(0)) {
      dead = socket.getLocalPort();
    }
    GatewayRelayFilter.RelayState state =
        new GatewayRelayFilter.RelayState(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), dead),
            "GET / HTTP/1.1\r\n\r\n",
            GatewayRelayFilter::connect);
    GatewayRelayFilter.RelayUpgrade.pending().set(state);
    try {
      assertThrows(
          java.io.UncheckedIOException.class,
          () -> new GatewayRelayFilter.RelayUpgrade().init(mock(WebConnection.class)));
    } finally {
      GatewayRelayFilter.RelayUpgrade.pending().remove();
    }
  }

  @Test
  void relayUpgradeInitFailsOnBrokenConnection() throws Exception {
    try (ServerSocket backend = new ServerSocket(0)) {
      // Accepted socket stays open so the backend connect succeeds; the client side breaks.
      Thread acceptor =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try (Socket peer = backend.accept()) {
                      peer.getInputStream().read();
                    } catch (IOException ignored) {
                    }
                  });
      GatewayRelayFilter.RelayState state =
          new GatewayRelayFilter.RelayState(
              new InetSocketAddress(InetAddress.getLoopbackAddress(), backend.getLocalPort()),
              "GET / HTTP/1.1\r\n\r\n",
              GatewayRelayFilter::connect);
      GatewayRelayFilter.RelayUpgrade.pending().set(state);
      try {
        WebConnection connection = mock(WebConnection.class);
        when(connection.getInputStream()).thenThrow(new IOException("broken"));
        assertThrows(
            IllegalStateException.class,
            () -> new GatewayRelayFilter.RelayUpgrade().init(connection));
      } finally {
        GatewayRelayFilter.RelayUpgrade.pending().remove();
        acceptor.join(TimeUnit.SECONDS.toMillis(10));
      }
    }
  }

  @Test
  void relayUpgradeInitFailsOnFactoryFailure() {
    GatewayRelayFilter.RelayState state =
        new GatewayRelayFilter.RelayState(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 9),
            "GET / HTTP/1.1\r\n\r\n",
            backend -> {
              throw new IOException("refused");
            });
    GatewayRelayFilter.RelayUpgrade.pending().set(state);
    try {
      assertThrows(
          java.io.UncheckedIOException.class,
          () -> new GatewayRelayFilter.RelayUpgrade().init(mock(WebConnection.class)));
    } finally {
      GatewayRelayFilter.RelayUpgrade.pending().remove();
    }
  }

  @Test
  void relayUpgradeInitFailsOnStreamFailure() {
    Socket fake =
        new Socket() {
          private final ByteArrayOutputStream sink = new ByteArrayOutputStream();

          @Override
          public OutputStream getOutputStream() {
            return sink;
          }

          @Override
          public InputStream getInputStream() throws IOException {
            throw new IOException("boom");
          }
        };
    GatewayRelayFilter.RelayState state =
        new GatewayRelayFilter.RelayState(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 9),
            "GET / HTTP/1.1\r\n\r\n",
            backend -> fake);
    GatewayRelayFilter.RelayUpgrade.pending().set(state);
    try {
      assertThrows(
          java.io.UncheckedIOException.class,
          () -> new GatewayRelayFilter.RelayUpgrade().init(mock(WebConnection.class)));
    } finally {
      GatewayRelayFilter.RelayUpgrade.pending().remove();
    }
  }

  @Test
  void relayQuietlyCopiesAndSwallowsCloseFailure() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Socket socket = mock(Socket.class);
    doThrow(new IOException("closed")).when(socket).close();
    GatewayRelayFilter.RelayUpgrade.relayQuietly(
        new ByteArrayInputStream("x".getBytes(StandardCharsets.US_ASCII)), out, socket);
    assertEquals("x", out.toString(StandardCharsets.UTF_8));
    verify(socket).close();
  }

  @Test
  void relaySkipsHopHeadersAndToleratesOddBackendLines() throws Exception {
    String response =
        "HTTP/1.1 200 OK\r\n"
            + "Connection: keep-alive\r\n"
            + "Keep-Alive: timeout=5\r\n"
            + "X-Ok: yes\r\n"
            + "NoColonHere\r\n"
            + "X-Weird\rBroken: yes\r\n"
            + "Content-Length: 15\r\n"
            + "\r\n"
            + "marker-"
            + "\rX\r\n"
            + "tail";
    try (ServerSocket backend = new ServerSocket(0)) {
      Thread acceptor =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try (Socket peer = backend.accept()) {
                      InputStream in = peer.getInputStream();
                      ByteArrayOutputStream head = new ByteArrayOutputStream();
                      int current;
                      while ((current = in.read()) >= 0) {
                        head.write(current);
                        byte[] bytes = head.toByteArray();
                        int size = bytes.length;
                        if (size >= 4
                            && bytes[size - 4] == '\r'
                            && bytes[size - 3] == '\n'
                            && bytes[size - 2] == '\r'
                            && bytes[size - 1] == '\n') {
                          break;
                        }
                      }
                      OutputStream peerOut = peer.getOutputStream();
                      peerOut.write(response.getBytes(StandardCharsets.US_ASCII));
                      peerOut.flush();
                    } catch (IOException done) {
                    }
                  });
      int backendPort = backend.getLocalPort();
      UUID user = UUID.randomUUID();
      UUID workspace = UUID.randomUUID();
      JwtVerifier tokens = mock(JwtVerifier.class);
      when(tokens.verify("tok")).thenReturn(session(user, workspace));
      WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
      when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
      TargetResolver targets = mock(TargetResolver.class);
      when(targets.resolve(workspace, 8080))
          .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), backendPort));
      MockHttpServletRequest request = workspaceRequest("/?token=tok");
      MockHttpServletResponse servletResponse = new MockHttpServletResponse();
      filter(tokens, directory, targets)
          .doFilter(request, servletResponse, mock(FilterChain.class));
      acceptor.join(TimeUnit.SECONDS.toMillis(10));
      assertEquals(200, servletResponse.getStatus());
      assertTrue(servletResponse.getContentAsString().contains("marker-"));
      assertNull(servletResponse.getHeader("Connection"));
      assertEquals("yes", servletResponse.getHeader("X-Ok"));
    }
  }

  @Test
  void skipsHopHeadersOnForward() throws Exception {
    HttpServer backend = dummyBackend("hop-marker");
    int backendPort = backend.getAddress().getPort();
    try {
      UUID user = UUID.randomUUID();
      UUID workspace = UUID.randomUUID();
      JwtVerifier tokens = mock(JwtVerifier.class);
      when(tokens.verify("tok")).thenReturn(session(user, workspace));
      WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
      when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
      TargetResolver targets = mock(TargetResolver.class);
      when(targets.resolve(workspace, 8080))
          .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), backendPort));
      MockHttpServletRequest request = workspaceRequest("/?token=tok");
      request.addHeader("Connection", "keep-alive");
      request.addHeader("Keep-Alive", "timeout=5");
      request.addHeader("Upgrade", "h2c");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter(tokens, directory, targets).doFilter(request, response, mock(FilterChain.class));
      assertEquals(200, response.getStatus());
      assertTrue(response.getContentAsString().contains("hop-marker"));
    } finally {
      backend.stop(0);
    }
  }

  @Test
  void passesThroughNonHttpResponse() throws Exception {
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
    request.setServerName("localhost");
    ServletResponse response = mock(ServletResponse.class);
    filter(mock(JwtVerifier.class), mock(WorkspaceDirectory.class), mock(TargetResolver.class))
        .doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
  }

  @Test
  void refreshesBearerSessionNearExpiry() throws Exception {
    HttpServer backend = dummyBackend("bearer-marker");
    int backendPort = backend.getAddress().getPort();
    try {
      UUID user = UUID.randomUUID();
      UUID workspace = UUID.randomUUID();
      String expiring = unsignedToken(Instant.now().plusSeconds(61).getEpochSecond());
      JwtVerifier tokens = mock(JwtVerifier.class);
      when(tokens.verify(expiring)).thenReturn(session(user, workspace));
      JwtService sessions = mock(JwtService.class);
      when(sessions.issue(any(), any(), any(), any())).thenReturn("fresh-bearer");
      WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
      when(directory.lookup("richard", 8080)).thenReturn(Optional.of(workspace));
      TargetResolver targets = mock(TargetResolver.class);
      when(targets.resolve(workspace, 8080))
          .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), backendPort));
      GatewayRelayFilter relay =
          new GatewayRelayFilter(
              new HostParser("code.example.com"), tokens, directory, targets, sessions, TTL);
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
      request.setServerName("richard-8080.code.example.com");
      request.addHeader("Authorization", "Bearer " + expiring);
      MockHttpServletResponse response = new MockHttpServletResponse();
      relay.doFilter(request, response, mock(FilterChain.class));
      assertEquals(200, response.getStatus());
      assertTrue(response.getHeader("Set-Cookie").startsWith("jahaziel-session=fresh-bearer"));
    } finally {
      backend.stop(0);
    }
  }

  @Test
  void relayUpgradeDestroyIsNoop() {
    new GatewayRelayFilter.RelayUpgrade().destroy();
  }

  private static String unsignedToken(long epochSecond) {
    return unsignedTokenPayload("{\"exp\":" + epochSecond + "}");
  }

  private static String unsignedTokenPayload(String json) {
    return "h."
        + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8))
        + ".s";
  }

  private static HttpServer dummyBackend(String marker) throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = marker.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static final class AtomicBody {
    private volatile String received;

    String received() {
      return received;
    }

    void receive(String body) {
      received = body;
    }
  }

  private static HttpServer backendServer(AtomicBody holder) throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/upload",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          holder.receive(body);
          byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, ok.length);
          exchange.getResponseBody().write(ok);
          exchange.close();
        });
    server.start();
    return server;
  }
}
