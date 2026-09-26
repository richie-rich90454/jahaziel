/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Port bindings over HTTP; each registration mints a workspace-scoped gateway URL. */
class PortE2eTest extends E2eBase {
  @Test
  void registerListUnregisterRoundTrip() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("ports"));

    E2eResponse registered =
        post(
            "/api/v1/workspaces/" + id + "/ports",
            userToken(),
            "{\"port\":18080,\"protocol\":\"http\"}");
    assertEquals(201, registered.status(), registered.body());
    assertTrue(registered.body().contains("\"port\":18080"), registered.body());
    assertTrue(
        registered.body().contains("-18080." + DOMAIN + ":" + GATEWAY_PORT + "/?token="),
        registered.body());

    E2eResponse listed = get("/api/v1/workspaces/" + id + "/ports", userToken());
    assertEquals(200, listed.status(), listed.body());
    assertTrue(listed.body().contains("\"port\":18080"), listed.body());

    E2eResponse removed = delete("/api/v1/workspaces/" + id + "/ports/18080", userToken());
    assertEquals(204, removed.status(), removed.body());

    E2eResponse relisted = get("/api/v1/workspaces/" + id + "/ports", userToken());
    assertTrue(!relisted.body().contains("\"port\":18080"), relisted.body());
  }

  @Test
  void tcpProtocolRegisters() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("tcp"));

    E2eResponse registered =
        post(
            "/api/v1/workspaces/" + id + "/ports",
            userToken(),
            "{\"port\":19001,\"protocol\":\"tcp\"}");
    assertEquals(201, registered.status(), registered.body());
    assertTrue(registered.body().contains("\"protocol\":\"tcp\""), registered.body());
  }

  @Test
  void duplicateRegistrationIsRejected() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("pdup"));
    post(
        "/api/v1/workspaces/" + id + "/ports",
        userToken(),
        "{\"port\":18081,\"protocol\":\"http\"}");

    E2eResponse again =
        post(
            "/api/v1/workspaces/" + id + "/ports",
            userToken(),
            "{\"port\":18081,\"protocol\":\"http\"}");
    assertEquals(400, again.status(), again.body());
    assertTrue(again.body().contains("VALIDATION_FAILED"), again.body());
  }

  @Test
  void workspaceHostRelaysOnTheApplicationPort() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("relay"));
    assertEquals(200, post("/api/v1/workspaces/" + id + "/start", userToken(), null).status());
    HttpServer backend = dummyBackend("relay-marker");
    int backendPort = backend.getAddress().getPort();
    try {
      E2eResponse registered =
          post(
              "/api/v1/workspaces/" + id + "/ports",
              userToken(),
              "{\"port\":" + backendPort + ",\"protocol\":\"http\"}");
      assertEquals(201, registered.status(), registered.body());
      String url = bodyJson(registered).get("url").asString();
      URI parsed = URI.create(url);
      String token = parsed.getRawQuery().replace("token=", "");
      String raw =
          rawGet(
              parsed.getHost(),
              "/?token=" + token,
              "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
      assertTrue(raw.contains(" 200 "), raw);
      assertTrue(raw.contains("relay-marker"), raw);
    } finally {
      backend.stop(0);
    }
  }

  @Test
  void reservedPortIsRejected() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("reserved"));

    E2eResponse registered =
        post(
            "/api/v1/workspaces/" + id + "/ports",
            userToken(),
            "{\"port\":22,\"protocol\":\"tcp\"}");
    assertEquals(400, registered.status(), registered.body());
  }

  @Test
  void unknownProtocolIsRejected() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("proto"));

    E2eResponse registered =
        post(
            "/api/v1/workspaces/" + id + "/ports",
            userToken(),
            "{\"port\":18082,\"protocol\":\"gopher\"}");
    assertEquals(400, registered.status(), registered.body());
  }

  @Test
  void outOfRangePortsAreRejected() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("range"));

    assertEquals(
        400,
        post(
                "/api/v1/workspaces/" + id + "/ports",
                userToken(),
                "{\"port\":0,\"protocol\":\"http\"}")
            .status());
    assertEquals(
        400,
        post(
                "/api/v1/workspaces/" + id + "/ports",
                userToken(),
                "{\"port\":99999,\"protocol\":\"http\"}")
            .status());
  }

  @Test
  void unregisterUnknownIsRejected() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("unreg"));

    E2eResponse removed = delete("/api/v1/workspaces/" + id + "/ports/18083", userToken());
    assertEquals(400, removed.status(), removed.body());
  }

  @Test
  void unknownWorkspacePortsAreNotFound() throws Exception {
    String missing = UUID.randomUUID().toString();
    assertEquals(404, get("/api/v1/workspaces/" + missing + "/ports", userToken()).status());
    assertEquals(
        404,
        post(
                "/api/v1/workspaces/" + missing + "/ports",
                userToken(),
                "{\"port\":18084,\"protocol\":\"http\"}")
            .status());
  }

  private static HttpServer dummyBackend(String marker) throws Exception {
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

  /** Raw socket GET to the application port with a chosen Host header. */
  private String rawGet(String host, String target, String userAgent) throws Exception {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(10_000);
      String request =
          "GET "
              + target
              + " HTTP/1.1\r\nHost: "
              + host
              + "\r\nUser-Agent: "
              + userAgent
              + "\r\nConnection: close\r\n\r\n";
      socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
      socket.getOutputStream().flush();
      return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
