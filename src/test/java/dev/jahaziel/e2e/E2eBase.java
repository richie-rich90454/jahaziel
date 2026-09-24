/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

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
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared full-stack fixture. Every subclass inherits these annotations unchanged, so Spring caches
 * exactly one application context for the whole package: one portable boot, one SQLite file, one
 * gateway port.
 *
 * <p>Properties mirror {@code JahazielMain.portablePropertyMap} (dist enabled with gateway, SQLite
 * WAL file, temp storage root and templates, generated JWT keys, fixed domain) so boot matches
 * production. Seeding is by upsert in {@code @BeforeEach}, so tests stay independent while sharing
 * the database. HTTP goes over JDK HttpClient: Spring Boot 4 removed TestRestTemplate.
 */
@SpringBootTest(
    classes = JahazielApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class E2eBase {
  static final String DOMAIN = "e2e.example.test";
  static final Path E2E_ROOT = initRoot();
  static final String ROOT_POSIX = posix(E2E_ROOT);
  static final int GATEWAY_PORT = freePort();
  static final ObjectMapper JSON = new ObjectMapper();
  static final HttpClient HTTP =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  static final HttpClient NO_REDIRECT =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  static final UUID ADMIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  static final UUID SUSPENDED_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  static final UUID NOMFA_ADMIN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

  static {
    ensureKeys();
    seedTemplates();
  }

  @LocalServerPort int port;

  @Autowired JwtService tokens;

  @Autowired AppUserRepository userRows;

  @Autowired UserRoleRepository roleRows;

  @DynamicPropertySource
  static void e2eProperties(DynamicPropertyRegistry registry) {
    registry.add("jahaziel.dist.enabled", () -> "true");
    registry.add("jahaziel.dist.gateway-enabled", () -> "true");
    registry.add("jahaziel.dist.gateway-port", () -> String.valueOf(GATEWAY_PORT));
    registry.add("jahaziel.dist.domain", () -> DOMAIN);
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:sqlite:"
                + ROOT_POSIX
                + "/jahaziel.db?journal_mode=WAL&busy_timeout=5000&foreign_keys=ON");
    registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
    registry.add(
        "spring.jpa.properties.hibernate.dialect",
        () -> "org.hibernate.community.dialect.SQLiteDialect");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    registry.add("spring.flyway.enabled", () -> "false");
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
    registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
    registry.add(
        "spring.autoconfigure.exclude",
        () ->
            "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration,"
                + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration");
    registry.add("DB_USER", () -> "");
    registry.add("DB_PASSWORD", () -> "");
    registry.add("jahaziel.storage.root", () -> ROOT_POSIX);
    registry.add("jahaziel.data-dir", () -> ROOT_POSIX);
    registry.add("jahaziel.templates.dir", () -> ROOT_POSIX + "/templates");
    registry.add("jahaziel.plugins.dir", () -> ROOT_POSIX + "/plugins");
    registry.add("jahaziel.ide.dir", () -> ROOT_POSIX + "/ide");
    registry.add("jahaziel.domain", () -> DOMAIN);
    registry.add("jahaziel.jwt.private-key-file", () -> ROOT_POSIX + "/keys/jwt.key");
    registry.add("jahaziel.jwt.public-key-file", () -> ROOT_POSIX + "/keys/jwt.pub");
    registry.add("jahaziel.jwt.issuer", () -> "https://" + DOMAIN);
    registry.add("logging.file.name", () -> ROOT_POSIX + "/logs/control-plane.log");
    registry.add("server.shutdown", () -> "graceful");
    registry.add("spring.lifecycle.timeout-per-shutdown-phase", () -> "20s");
  }

  @BeforeEach
  void seedUsers() {
    upsertUser(
        ADMIN_ID,
        "e2e-admin-sub",
        "e2e-admin@example.test",
        "E2E Admin",
        "active",
        true,
        List.of("ROLE_ADMIN"));
    upsertUser(
        USER_ID,
        "e2e-user-sub",
        "e2e-user@example.test",
        "E2E User",
        "active",
        false,
        List.of("ROLE_USER"));
    upsertUser(
        SUSPENDED_ID,
        "e2e-suspended-sub",
        "e2e-suspended@example.test",
        "E2E Suspended",
        "suspended",
        false,
        List.of("ROLE_USER"));
    upsertUser(
        NOMFA_ADMIN_ID,
        "e2e-nomfa-sub",
        "e2e-nomfa@example.test",
        "E2E NoMFA",
        "active",
        false,
        List.of("ROLE_ADMIN"));
  }

  String adminToken() {
    return tokens.issue(new UserId(ADMIN_ID), null, List.of("e2e"), Duration.ofMinutes(30));
  }

  String userToken() {
    return tokens.issue(new UserId(USER_ID), null, List.of("e2e"), Duration.ofMinutes(30));
  }

  String suspendedToken() {
    return tokens.issue(new UserId(SUSPENDED_ID), null, List.of("e2e"), Duration.ofMinutes(30));
  }

  String nomfaAdminToken() {
    return tokens.issue(new UserId(NOMFA_ADMIN_ID), null, List.of("e2e"), Duration.ofMinutes(30));
  }

  String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  E2eResponse get(String path, String token) throws Exception {
    return send(HTTP, "GET", path, token, null);
  }

  E2eResponse getAnonymous(String path) throws Exception {
    return get(path, null);
  }

  E2eResponse getHtml(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl() + path))
            .GET()
            .header("Accept", "text/html")
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    return new E2eResponse(response.statusCode(), response.headers(), response.body());
  }

  E2eResponse getWithCookie(String path, String cookie) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl() + path))
            .GET()
            .header("Accept", "application/json")
            .header("Cookie", cookie)
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    return new E2eResponse(response.statusCode(), response.headers(), response.body());
  }

  E2eResponse getWithAccept(String path, String token, String accept) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET().header("Accept", accept);
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    HttpResponse<String> response =
        HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    return new E2eResponse(response.statusCode(), response.headers(), response.body());
  }

  E2eResponse post(String path, String token, String body) throws Exception {
    return send(HTTP, "POST", path, token, body);
  }

  E2eResponse delete(String path, String token) throws Exception {
    return send(HTTP, "DELETE", path, token, null);
  }

  E2eResponse getNoRedirect(String path) throws Exception {
    return send(NO_REDIRECT, "GET", path, null, null);
  }

  JsonNode bodyJson(E2eResponse response) {
    return JSON.readTree(response.body());
  }

  String uniqueName(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  String uniqueTag() {
    return "tag-" + UUID.randomUUID().toString().substring(0, 8);
  }

  String createWorkspace(String token, String name) throws Exception {
    E2eResponse created = post("/api/v1/workspaces", token, "{\"name\":\"" + name + "\"}");
    if (created.status() != 201) {
      throw new AssertionError("create workspace failed: " + created.body());
    }
    return bodyJson(created).get("workspaceId").asString();
  }

  private E2eResponse send(HttpClient client, String method, String path, String token, String body)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(baseUrl() + path)).header("Accept", "application/json");
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      builder
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(body));
    } else if (method.equals("POST")) {
      builder.POST(HttpRequest.BodyPublishers.noBody());
    } else if (method.equals("DELETE")) {
      builder.DELETE();
    } else {
      builder.GET();
    }
    HttpResponse<String> response =
        client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    return new E2eResponse(response.statusCode(), response.headers(), response.body());
  }

  private void upsertUser(
      UUID id,
      String externalId,
      String email,
      String name,
      String status,
      boolean mfa,
      List<String> roles) {
    AppUserEntity row = userRows.findById(id).orElseGet(AppUserEntity::new);
    row.setUserId(id);
    row.setExternalId(externalId);
    row.setEmail(email);
    row.setDisplayName(name);
    if (row.getCreatedAt() == null) {
      row.setCreatedAt(Instant.now());
    }
    row.setStatus(status);
    row.setMfaEnabled(mfa);
    userRows.save(row);
    roleRows.findByIdUserId(id).forEach(roleRows::delete);
    for (String role : roles) {
      UserRoleEntity grant = new UserRoleEntity();
      grant.setId(new UserRoleEntity.Id(id, role));
      grant.setGrantedAt(Instant.now());
      grant.setGrantedBy("e2e");
      roleRows.save(grant);
    }
  }

  private static Path initRoot() {
    try {
      return Files.createTempDirectory("jahaziel-e2e");
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  private static String posix(Path path) {
    return path.toAbsolutePath().normalize().toString().replace('\\', '/');
  }

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  private static void ensureKeys() {
    try {
      Path keys = E2E_ROOT.resolve("keys");
      Files.createDirectories(keys);
      Path privateKey = keys.resolve("jwt.key");
      Path publicKey = keys.resolve("jwt.pub");
      if (Files.isRegularFile(privateKey) && Files.isRegularFile(publicKey)) {
        return;
      }
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      writePem(privateKey, "PRIVATE KEY", pair.getPrivate().getEncoded());
      writePem(publicKey, "PUBLIC KEY", pair.getPublic().getEncoded());
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  private static void writePem(Path path, String kind, byte[] encoded) throws Exception {
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded) + "\n";
    Files.writeString(
        path,
        "-----BEGIN " + kind + "-----\n" + body + "-----END " + kind + "-----\n",
        StandardCharsets.UTF_8);
  }

  private static void seedTemplates() {
    try {
      // ponytail: minimal seeds; portable boot copies richer ones only via JahazielMain.launch.
      for (String name : List.of("empty", "node")) {
        Path dir = E2E_ROOT.resolve("templates").resolve(name);
        Files.createDirectories(dir);
        Path file = dir.resolve("template.json");
        if (!Files.isRegularFile(file)) {
          Files.writeString(
              file,
              "{\"name\":\"" + name + "\",\"vcpuCount\":2,\"memoryMib\":2048}",
              StandardCharsets.UTF_8);
        }
      }
      Files.createDirectories(E2E_ROOT.resolve("plugins"));
      Files.createDirectories(E2E_ROOT.resolve("ide"));
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  /** Minimal HTTP result: status, headers, body. */
  record E2eResponse(int status, HttpHeaders headers, String body) {
    String header(String name) {
      return headers.firstValue(name).orElse("");
    }

    String allHeaders(String name) {
      return String.join(";", headers.allValues(name));
    }
  }
}
