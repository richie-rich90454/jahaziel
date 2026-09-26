/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.jahaziel.JahazielApplication;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end workspace access: a port registered for a running workspace becomes reachable through
 * the gateway at its host-form URL, authenticated by the workspace-scoped token the API returned.
 * The token binds one user, one workspace, and one port; anything else is refused.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceAccessTest {
  private static final int API_PORT = freePort();
  private static final int GATEWAY_PORT = freePort();
  private static final Path TMP = tempDir();
  private static final JarDataDir DATA = JarDataDir.resolve(TMP.resolve("data").toString());
  private static final DevKeys.Keys KEYS = DevKeys.ensure(DATA.keys());
  private static final String DOMAIN = "code.example.com";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  private final List<String> touchedProperties = new ArrayList<>();
  private ConfigurableApplicationContext context;
  private JwtService tokens;
  private String ownerToken;
  private UserId owner;
  private HttpServer backend;
  private int backendPort;
  private String workspaceId;

  @BeforeAll
  void boot() throws Exception {
    Map<String, String> properties =
        new LinkedHashMap<>(
            JahazielMain.portablePropertyMap(
                DATA,
                KEYS,
                new JahazielMain.Arguments(
                    DATA.root().toString(), API_PORT, GATEWAY_PORT, DOMAIN, false, false, false)));
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      if (System.getProperty(entry.getKey()) == null) {
        touchedProperties.add(entry.getKey());
      }
      System.setProperty(entry.getKey(), entry.getValue());
    }
    context = new SpringApplicationBuilder(JahazielApplication.class).run();
    tokens = context.getBean(JwtService.class);

    owner = new UserId(UUID.randomUUID());
    AppUserEntity user = new AppUserEntity();
    user.setUserId(owner.value());
    user.setExternalId("access-check");
    user.setEmail("access@example.com");
    user.setDisplayName("Access Check");
    user.setCreatedAt(Instant.now());
    user.setStatus("active");
    user.setMfaEnabled(false);
    context.getBean(AppUserRepository.class).save(user);
    ownerToken = tokens.issue(owner, null, List.of("workspace:write"), Duration.ofHours(1));
  }

  @AfterAll
  void shutdown() throws Exception {
    if (backend != null) {
      backend.stop(0);
    }
    if (context != null) {
      context.close();
    }
    for (String key : touchedProperties) {
      System.clearProperty(key);
    }
    touchedProperties.clear();
    deleteTree(TMP);
  }

  @Test
  void registeredPortIsReachableThroughTheGatewayWithItsToken() throws Exception {
    String slug = context.getBean(UserService.class).slug(owner);

    JsonNode workspace =
        json(post("/api/v1/workspaces", ownerToken, "{\"name\":\"access-check\"}"));
    workspaceId = workspace.get("workspaceId").asString();

    json(post("/api/v1/workspaces/" + workspaceId + "/start", ownerToken, ""));

    backend = dummyServer();
    backendPort = backend.getAddress().getPort();

    JsonNode port =
        json(
            post(
                "/api/v1/workspaces/" + workspaceId + "/ports",
                ownerToken,
                "{\"port\":" + backendPort + ",\"protocol\":\"http\"}"));
    String url = port.get("url").asString();
    assertTrue(
        url.startsWith(
            "http://" + slug + "-" + backendPort + "." + DOMAIN + ":" + GATEWAY_PORT + "/?token="),
        url);

    URI parsed = URI.create(url);
    String token = queryToken(parsed.getRawQuery());

    RawResponse anonymous = get(parsed.getHost(), "/");
    assertEquals(401, anonymous.status(), anonymous.body());

    RawResponse served = get(parsed.getHost(), "/?token=" + token);
    assertEquals(200, served.status(), served.body());
    assertTrue(served.body().contains("jahaziel-workspace-marker"), served.body());

    String foreign = tokens.issue(owner, UUID.randomUUID(), List.of(), Duration.ofMinutes(5));
    assertEquals(403, get(parsed.getHost(), "/?token=" + foreign).status(), "wrong workspace");
  }

  private HttpServer dummyServer() throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = "jahaziel-workspace-marker".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static String post(String path, String token, String body) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(base() + path))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    HttpResponse<String> response =
        HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    assertTrue(response.statusCode() < 400, response.body());
    return response.body();
  }

  private static JsonNode json(String body) {
    return MAPPER.readTree(body);
  }

  /** Sends a request to the gateway with a chosen Host header, which HttpClient forbids. */
  private static RawResponse get(String host, String target) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", GATEWAY_PORT)) {
      socket.setSoTimeout(10_000);
      String request =
          "GET " + target + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n";
      socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
      socket.getOutputStream().flush();
      String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int status = Integer.parseInt(response.split(" ", 3)[1]);
      return new RawResponse(status, response);
    }
  }

  private static String queryToken(String rawQuery) {
    for (String pair : rawQuery.split("&")) {
      if (pair.startsWith("token=")) {
        return pair.substring("token=".length());
      }
    }
    throw new AssertionError("no token in " + rawQuery);
  }

  private static String base() {
    return "http://127.0.0.1:" + API_PORT;
  }

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  private static Path tempDir() {
    try {
      return Files.createTempDirectory("jahaziel-access-test");
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  private static void deleteTree(Path root) {
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                  // Best-effort test cleanup.
                }
              });
    } catch (IOException ignored) {
      // Best-effort test cleanup.
    }
  }

  private record RawResponse(int status, String body) {}
}
