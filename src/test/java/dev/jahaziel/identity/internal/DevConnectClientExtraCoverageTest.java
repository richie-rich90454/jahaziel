/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import dev.jahaziel.identity.api.DevConnectException;
import dev.jahaziel.identity.internal.devconnect.DevConnectClient;
import dev.jahaziel.identity.internal.devconnect.DevConnectProperties;
import dev.jahaziel.identity.internal.devconnect.DiscoveryDocument;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

class DevConnectClientExtraCoverageTest {
  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  private DevConnectProperties props(String base) {
    DevConnectProperties p = new DevConnectProperties();
    p.setAuthIssuer(base);
    p.setRedirectUri("https://app/cb");
    p.setClientId("jahaziel");
    p.setClientSecret("s3cret");
    p.setResource("urn:basis:api:jahaziel");
    return p;
  }

  private DevConnectClient client(String base, AtomicReference<String> discoveryBody)
      throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int port = server.getAddress().getPort();
    String url = "http://127.0.0.1:" + port;
    server.createContext(
        "/.well-known/openid-configuration",
        ex -> {
          byte[] body = discoveryBody.get().getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "application/json");
          ex.sendResponseHeaders(200, body.length);
          ex.getResponseBody().write(body);
          ex.close();
        });
    server.start();
    DevConnectProperties p = props(url);
    RestClient http = RestClient.builder().build();
    return new DevConnectClient(http, new RestTemplate(), p, Clock.systemUTC());
  }

  private String discoveryJson(String base) {
    return "{\"issuer\":\""
        + base
        + "\",\"authorization_endpoint\":\""
        + base
        + "/authorize\",\"token_endpoint\":\""
        + base
        + "/token\",\"userinfo_endpoint\":\""
        + base
        + "/userinfo\",\"jwks_uri\":\""
        + base
        + "/jwks\"}";
  }

  @Test
  void discoveryCachesAndRefreshes() throws Exception {
    AtomicReference<String> body = new AtomicReference<>("{}");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int port = server.getAddress().getPort();
    String base = "http://127.0.0.1:" + port;
    body.set(discoveryJson(base));
    AtomicInteger hits = new AtomicInteger();
    server.createContext(
        "/.well-known/openid-configuration",
        ex -> {
          hits.incrementAndGet();
          byte[] b = body.get().getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "application/json");
          ex.sendResponseHeaders(200, b.length);
          ex.getResponseBody().write(b);
          ex.close();
        });
    server.start();
    DevConnectProperties p = props(base);
    Clock fixed = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    DevConnectClient c =
        new DevConnectClient(RestClient.builder().build(), new RestTemplate(), p, fixed);
    c.discovery();
    c.discovery();
    assertEquals(1, hits.get());
    Clock later = Clock.offset(fixed, java.time.Duration.ofMinutes(6));
    Field clockField = DevConnectClient.class.getDeclaredField("clock");
    clockField.setAccessible(true);
    clockField.set(c, later);
    c.discovery();
    assertEquals(2, hits.get());
    String withQuery = base + "/authorize?existing=1";
    String endpointDoc = discoveryJson(base).replace(base + "/authorize", withQuery);
    body.set(endpointDoc);
    clockField.set(c, Clock.offset(fixed, java.time.Duration.ofMinutes(12)));
    assertTrue(c.authorizationUrl("s", "n", "ch").contains("existing=1"));
    assertTrue(c.authorizationUrl("s", "n", "ch").contains("code_challenge=ch"));
  }

  @Test
  void discoveryRejectsMalformedAndMissing() throws Exception {
    AtomicReference<String> body = new AtomicReference<>("not-json");
    client("http://127.0.0.1:1", body);
    server.stop(0);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int port = server.getAddress().getPort();
    String base = "http://127.0.0.1:" + port;
    server.createContext(
        "/.well-known/openid-configuration",
        ex -> {
          byte[] b = body.get().getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(200, b.length);
          ex.getResponseBody().write(b);
          ex.close();
        });
    server.start();
    DevConnectProperties p = props(base);
    DevConnectClient c2 =
        new DevConnectClient(
            RestClient.builder().build(), new RestTemplate(), p, Clock.systemUTC());
    assertThrows(DevConnectException.class, c2::discovery);
    body.set("{}");
    DevConnectClient c3 =
        new DevConnectClient(
            RestClient.builder().build(), new RestTemplate(), p, Clock.systemUTC());
    assertThrows(DevConnectException.class, c3::discovery);
    body.set(
        "{\"issuer\":\"  \",\"authorization_endpoint\":\"a\",\"token_endpoint\":\"t\","
            + "\"userinfo_endpoint\":\"u\",\"jwks_uri\":\"j\"}");
    DevConnectClient c4 =
        new DevConnectClient(
            RestClient.builder().build(), new RestTemplate(), p, Clock.systemUTC());
    assertThrows(DevConnectException.class, c4::discovery);
    body.set("");
    DevConnectClient c5 =
        new DevConnectClient(
            RestClient.builder().build(), new RestTemplate(), p, Clock.systemUTC());
    assertThrows(DevConnectException.class, c5::discovery);
  }

  @Test
  void fetchRetriesThenFailsAndHandles4xx() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int port = server.getAddress().getPort();
    String base = "http://127.0.0.1:" + port;
    AtomicInteger calls = new AtomicInteger();
    server.createContext(
        "/.well-known/openid-configuration",
        ex -> {
          calls.incrementAndGet();
          ex.sendResponseHeaders(500, 0);
          ex.close();
        });
    server.start();
    DevConnectClient c =
        new DevConnectClient(
            RestClient.builder().build(), new RestTemplate(), props(base), Clock.systemUTC());
    long start = System.currentTimeMillis();
    assertThrows(DevConnectException.class, c::discovery);
    assertTrue(System.currentTimeMillis() - start >= 500);
    assertEquals(3, calls.get());
    server.stop(0);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int port2 = server.getAddress().getPort();
    String base2 = "http://127.0.0.1:" + port2;
    server.createContext(
        "/.well-known/openid-configuration",
        ex -> {
          ex.sendResponseHeaders(404, 0);
          ex.close();
        });
    server.start();
    DevConnectClient c2 =
        new DevConnectClient(
            RestClient.builder().build(), new RestTemplate(), props(base2), Clock.systemUTC());
    DevConnectException ex = assertThrows(DevConnectException.class, c2::discovery);
    assertTrue(ex.getMessage().contains("404"));
  }

  @Test
  void exchangeAndUserinfoBranches() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int port = server.getAddress().getPort();
    String base = "http://127.0.0.1:" + port;
    AtomicReference<String> tokenBody =
        new AtomicReference<>("{\"id_token\":\"i\",\"access_token\":\"a\"}");
    AtomicReference<String> userBody =
        new AtomicReference<>("{\"sub\":\"s\",\"email\":\"e@x.y\",\"name\":\"N\"}");
    AtomicReference<Integer> tokenStatus = new AtomicReference<>(200);
    server.createContext(
        "/.well-known/openid-configuration",
        ex -> {
          byte[] b = discoveryJson(base).getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "application/json");
          ex.sendResponseHeaders(200, b.length);
          ex.getResponseBody().write(b);
          ex.close();
        });
    server.createContext(
        "/token",
        ex -> {
          if (tokenStatus.get() != 200) {
            ex.sendResponseHeaders(tokenStatus.get(), 0);
            ex.close();
            return;
          }
          byte[] b = tokenBody.get().getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "application/json");
          ex.sendResponseHeaders(200, b.length);
          ex.getResponseBody().write(b);
          ex.close();
        });
    server.createContext(
        "/userinfo",
        ex -> {
          byte[] b = userBody.get().getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "application/json");
          ex.sendResponseHeaders(200, b.length);
          ex.getResponseBody().write(b);
          ex.close();
        });
    server.start();
    DevConnectClient c =
        new DevConnectClient(
            RestClient.builder().build(), new RestTemplate(), props(base), Clock.systemUTC());
    assertEquals("i", c.exchange("code", "ver", "https://app/cb").idToken());
    tokenBody.set("{\"id_token\":\"only\"}");
    assertThrows(DevConnectException.class, () -> c.exchange("code", "ver", "https://app/cb"));
    tokenBody.set("not-json");
    assertThrows(DevConnectException.class, () -> c.exchange("code", "ver", "https://app/cb"));
    tokenStatus.set(500);
    assertThrows(DevConnectException.class, () -> c.exchange("code", "ver", "https://app/cb"));
    tokenStatus.set(200);
    tokenBody.set("{\"id_token\":\"i\",\"access_token\":\"a\"}");
    assertEquals("s", c.userinfo("a").sub());
    userBody.set("{\"sub\":\"only\"}");
    assertThrows(DevConnectException.class, () -> c.userinfo("a"));
    userBody.set("{\"sub\":\"s\",\"email\":\"e@x.y\",\"name\":\"  \"}");
    assertEquals("e@x.y", c.userinfo("a").name());
    userBody.set("{\"sub\":\"s\",\"email\":\"e@x.y\"}");
    assertEquals("e@x.y", c.userinfo("a").name());
  }

  @Test
  void verifyIdTokenBranchesWithMockDecoder() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int port = server.getAddress().getPort();
    String base = "http://127.0.0.1:" + port;
    server.createContext(
        "/.well-known/openid-configuration",
        ex -> {
          byte[] b = discoveryJson(base).getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(200, b.length);
          ex.getResponseBody().write(b);
          ex.close();
        });
    server.start();
    DevConnectClient c =
        new DevConnectClient(
            RestClient.builder().build(), new RestTemplate(), props(base), Clock.systemUTC());
    c.discovery();
    JwtDecoder decoder = mock(JwtDecoder.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getClaimAsString("nonce")).thenReturn("n");
    when(decoder.decode("good")).thenReturn(jwt);
    Field df = DevConnectClient.class.getDeclaredField("decoder");
    df.setAccessible(true);
    df.set(c, decoder);
    Field jf = DevConnectClient.class.getDeclaredField("decoderJwksUri");
    jf.setAccessible(true);
    jf.set(c, base + "/jwks");
    assertEquals(jwt, c.verifyIdToken("good", "n"));
    assertEquals(jwt, c.verifyIdToken("good", null));
    when(jwt.getClaimAsString("nonce")).thenReturn("other");
    assertThrows(DevConnectException.class, () -> c.verifyIdToken("good", "n"));
    when(decoder.decode("bad")).thenThrow(new JwtException("nope"));
    assertThrows(DevConnectException.class, () -> c.verifyIdToken("bad", "n"));
    when(decoder.decode(any())).thenThrow(new IllegalArgumentException("bad"));
    assertThrows(DevConnectException.class, () -> c.verifyIdToken("x", "n"));
  }

  @Test
  void privateCauseAndSleepBranches() throws Exception {
    Method cause = DevConnectClient.class.getDeclaredMethod("cause", Throwable.class);
    cause.setAccessible(true);
    assertEquals("unknown cause", cause.invoke(null, (Object) null));
    assertEquals("RuntimeException", cause.invoke(null, new RuntimeException()));
    assertEquals(
        "   ".isBlank() ? "RuntimeException" : "x",
        cause.invoke(null, new RuntimeException("   ")));
    assertTrue(((String) cause.invoke(null, new RuntimeException("boom"))).contains("boom"));
    RuntimeException chained = new RuntimeException("outer", new IllegalStateException("inner"));
    assertTrue(((String) cause.invoke(null, chained)).contains("outer"));
    Method sleep = DevConnectClient.class.getDeclaredMethod("sleep", java.time.Duration.class);
    sleep.setAccessible(true);
    Thread.currentThread().interrupt();
    try {
      assertThrows(
          java.lang.reflect.InvocationTargetException.class,
          () -> sleep.invoke(null, java.time.Duration.ofMillis(1)));
    } finally {
      Thread.interrupted();
    }
    DiscoveryDocument doc = new DiscoveryDocument("i", "a", "t", "u", "j");
    assertEquals("i", doc.issuer());
  }
}
