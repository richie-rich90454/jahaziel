/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.JahazielApplication;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.UserRoleEntity;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * Every user-facing and admin feature against the portable single-process stack: workspaces and
 * their lifecycle, ports, snapshots and restore, terminals, metrics, audit, policy, nodes, and
 * users. Each endpoint is a promise; this asserts it on the platform the jar runs on.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortableFeaturesTest {
  private static final int API_PORT = freePort();
  private static final int GATEWAY_PORT = freePort();
  private static final Path TMP = tempDir();
  private static final JarDataDir DATA = JarDataDir.resolve(TMP.resolve("data").toString());
  private static final DevKeys.Keys KEYS = DevKeys.ensure(DATA.keys());
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  private final List<String> touchedProperties = new ArrayList<>();
  private ConfigurableApplicationContext context;
  private String ownerToken;
  private String adminToken;

  @BeforeAll
  void boot() throws Exception {
    Map<String, String> properties =
        new LinkedHashMap<>(
            JahazielMain.portablePropertyMap(
                DATA,
                KEYS,
                new JahazielMain.Arguments(
                    DATA.root().toString(), API_PORT, GATEWAY_PORT, "code.example.com", false,
                    false, false)));
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      if (System.getProperty(entry.getKey()) == null) {
        touchedProperties.add(entry.getKey());
      }
      System.setProperty(entry.getKey(), entry.getValue());
    }
    context = new SpringApplicationBuilder(JahazielApplication.class).run();
    JwtService tokens = context.getBean(JwtService.class);

    UserId owner = new UserId(UUID.randomUUID());
    saveUser(owner, "feature-owner@example.com", false);
    ownerToken = tokens.issue(owner, null, List.of("workspace:write"), Duration.ofHours(1));

    UserId admin = new UserId(UUID.randomUUID());
    saveUser(admin, "feature-admin@example.com", true);
    adminToken = tokens.issue(admin, null, List.of("admin"), Duration.ofHours(1));
  }

  @AfterAll
  void shutdown() throws Exception {
    if (context != null) {
      context.close();
    }
    for (String key : touchedProperties) {
      System.clearProperty(key);
    }
    touchedProperties.clear();
    try (Stream<Path> walk = Files.walk(TMP)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                  // Best-effort test cleanup.
                }
              });
    }
  }

  @Test
  void workspaceLifecycleAndEntryPointsWork() throws Exception {
    String id = createWorkspace();
    assertEquals(200, get("/api/v1/workspaces/" + id, ownerToken).status());
    assertEquals(200, post("/api/v1/workspaces/" + id + "/start", ownerToken, "").status());
    assertEquals(200, post("/api/v1/workspaces/" + id + "/restart", ownerToken, "").status());
    assertEquals(200, post("/api/v1/workspaces/" + id + "/stop", ownerToken, "").status());
    assertEquals(200, get("/api/v1/workspaces/" + id + "/metrics", ownerToken).status());
    assertEquals(200, get("/api/v1/workspaces/" + id + "/ports", ownerToken).status());
    assertEquals(200, get("/api/v1/me/workspaces", ownerToken).status());
    assertEquals(200, get("/api/v1/me", ownerToken).status());
    assertEquals(204, delete("/api/v1/workspaces/" + id + "?confirm=true", ownerToken).status());
  }

  @Test
  void snapshotsCanBeTakenListedAndRestored() throws Exception {
    String id = createWorkspace();
    assertEquals(200, post("/api/v1/workspaces/" + id + "/start", ownerToken, "").status());

    Response created =
        post("/api/v1/workspaces/" + id + "/snapshots", ownerToken, "{\"tag\":\"daily\"}");
    assertEquals(201, created.status(), created.body());
    String snapshotId = json(created.body()).get("snapshotId").asString();

    Response list = get("/api/v1/workspaces/" + id + "/snapshots", ownerToken);
    assertEquals(200, list.status(), list.body());
    assertTrue(list.body().contains("daily"), list.body());

    Response restored =
        post("/api/v1/workspaces/" + id + "/snapshots/" + snapshotId + "/restore", ownerToken, "");
    assertEquals(200, restored.status(), restored.body());

    assertEquals(204, delete("/api/v1/workspaces/" + id + "?confirm=true", ownerToken).status());
  }

  @Test
  void terminalsCanBeOpenedListedAndClosed() throws Exception {
    String id = createWorkspace();
    Response opened = post("/api/v1/workspaces/" + id + "/terminals", ownerToken, "");
    assertEquals(201, opened.status(), opened.body());
    String sessionId = json(opened.body()).get("sessionId").asString();

    Response list = get("/api/v1/workspaces/" + id + "/terminals", ownerToken);
    assertEquals(200, list.status(), list.body());
    assertTrue(list.body().contains(sessionId), list.body());

    Response closed = delete("/api/v1/terminals/" + sessionId, ownerToken);
    assertEquals(204, closed.status(), closed.body());
    assertEquals(204, delete("/api/v1/workspaces/" + id + "?confirm=true", ownerToken).status());
  }

  @Test
  void ownerCannotReachAdministration() throws Exception {
    assertEquals(403, get("/admin/v1/users", ownerToken).status());
  }

  @Test
  void administrationWorks() throws Exception {
    assertEquals(200, get("/admin/v1/health", adminToken).status());
    assertEquals(200, get("/admin/v1/users", adminToken).status());
    assertEquals(200, get("/admin/v1/nodes", adminToken).status());
    assertEquals(200, get("/admin/v1/workspaces", adminToken).status());
    assertEquals(200, get("/admin/v1/audit/query?limit=10", adminToken).status());

    Response stored =
        post("/admin/v1/policy", adminToken, "{\"scope\":\"global\",\"policyJson\":\"{}\"}");
    assertEquals(201, stored.status(), stored.body());
    String policyId = json(stored.body()).get("id").asString();
    assertEquals(200, get("/admin/v1/policy?scope=global", adminToken).status());
    assertEquals(204, delete("/admin/v1/policy/" + policyId, adminToken).status());
  }

  @Test
  void actuatorHealthIsPublic() throws Exception {
    assertEquals(200, get("/actuator/health", null).status());
  }

  @Test
  void unknownPathsAreNotFoundNotServerErrors() throws Exception {
    assertEquals(404, get("/api/v1/no-such-endpoint", ownerToken).status());
  }

  private void saveUser(UserId id, String email, boolean roleAdmin) {
    AppUserEntity user = new AppUserEntity();
    user.setUserId(id.value());
    user.setExternalId("features:" + id.value());
    user.setEmail(email);
    user.setDisplayName(email);
    user.setCreatedAt(Instant.now());
    user.setStatus("active");
    user.setMfaEnabled(roleAdmin);
    context.getBean(AppUserRepository.class).save(user);
    UserRoleEntity role = new UserRoleEntity();
    role.setId(new UserRoleEntity.Id(id.value(), roleAdmin ? "ROLE_ADMIN" : "ROLE_USER"));
    role.setGrantedAt(Instant.now());
    role.setGrantedBy("test");
    context.getBean(UserRoleRepository.class).save(role);
  }

  private String createWorkspace() throws Exception {
    String name = "features-" + UUID.randomUUID().toString().substring(0, 8);
    Response created = post("/api/v1/workspaces", ownerToken, "{\"name\":\"" + name + "\"}");
    assertEquals(201, created.status(), created.body());
    return json(created.body()).get("workspaceId").asString();
  }

  private static Response get(String path, String token) throws Exception {
    return send(HttpRequest.newBuilder(URI.create(base() + path)).GET(), token, "GET", null);
  }

  private static Response post(String path, String token, String body) throws Exception {
    return send(
        HttpRequest.newBuilder(URI.create(base() + path))
            .POST(HttpRequest.BodyPublishers.ofString(body)),
        token,
        "POST",
        body);
  }

  private static Response delete(String path, String token) throws Exception {
    return send(HttpRequest.newBuilder(URI.create(base() + path)).DELETE(), token, "DELETE", null);
  }

  private static Response send(
      HttpRequest.Builder request, String token, String method, String body) throws Exception {
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if ("POST".equals(method)) {
      request.header("Content-Type", "application/json");
    }
    HttpResponse<String> response =
        HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    return new Response(response.statusCode(), response.body());
  }

  private static JsonNode json(String body) {
    return MAPPER.readTree(body);
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
      return Files.createTempDirectory("jahaziel-features-test");
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  private record Response(int status, String body) {}
}
