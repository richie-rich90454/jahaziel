/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.JahazielApplication;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * End-to-end DevConnect login against a hermetic basis-auth stand-in: the browser is sent to the
 * IdP, the callback sets the session and token cookies, and the API then answers as the resolved
 * user. Also proves the account rules: first login creates an active user, a disabled account loses
 * access, and a second subject on a linked email is refused.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DevConnectLoginTest {
  private static final int PORT = freePort();
  private static final Path TMP = tempDir();
  private static final JarDataDir DATA = JarDataDir.resolve(TMP.resolve("data").toString());
  private static final DevKeys.Keys KEYS = DevKeys.ensure(DATA.keys());
  private static final MockBasisAuth IDP = startIdp();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  private final List<String> touchedProperties = new ArrayList<>();
  private ConfigurableApplicationContext context;

  @BeforeAll
  void boot() throws Exception {
    Map<String, String> properties =
        new LinkedHashMap<>(
            JahazielMain.portablePropertyMap(
                DATA,
                KEYS,
                new JahazielMain.Arguments(
                    DATA.root().toString(), PORT, PORT + 1, "code.example.com", false, false,
                    false)));
    properties.put("basis.auth-issuer", IDP.issuer());
    properties.put("basis.redirect-uri", base() + "/api/oauth/callback");
    properties.put("basis.client-id", "jahaziel");
    properties.put("basis.client-secret", "s3cret");
    properties.put("basis.resource", "urn:basis:api:jahaziel");
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      if (System.getProperty(entry.getKey()) == null) {
        touchedProperties.add(entry.getKey());
      }
      System.setProperty(entry.getKey(), entry.getValue());
    }
    context = new SpringApplicationBuilder(JahazielApplication.class).run();
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
    IDP.close();
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
  void loginSetsCookiesAndAuthenticatesEveryApiCall() throws Exception {
    IDP.identity("login-sub", "login@example.com", "Login User");
    String cookie = login();

    Response me = get(base() + "/api/v1/me", cookie);
    assertEquals(200, me.status(), me.body());
    assertTrue(me.body().contains("login@example.com"), me.body());

    Response authMe = get(base() + "/api/auth/me", cookie);
    assertEquals(200, authMe.status(), authMe.body());
    assertTrue(authMe.body().contains("Login User"), authMe.body());

    Response mine = get(base() + "/api/v1/me/workspaces", cookie);
    assertEquals(200, mine.status(), mine.body());

    Response created =
        post(base() + "/api/v1/workspaces", cookie, "{\"name\":\"devconnect-check\"}");
    assertEquals(201, created.status(), created.body());
    assertTrue(created.body().contains("devconnect-check"), created.body());
  }

  @Test
  void firstLoginCreatesAnActiveUserWithTheUserRole() throws Exception {
    IDP.identity("fresh-sub", "fresh@example.com", "Fresh User");
    login();

    AppUserEntity user =
        context.getBean(AppUserRepository.class).findByExternalId("fresh-sub").orElseThrow();
    assertEquals("active", user.getStatus());
    assertEquals("fresh@example.com", user.getEmail());
    assertTrue(
        context.getBean(UserRoleRepository.class).findByIdUserId(user.getUserId()).stream()
            .anyMatch(role -> role.getId().getRole().equals("ROLE_USER")));
  }

  @Test
  void anonymousRequestsAreRejected() throws Exception {
    assertEquals(401, get(base() + "/api/v1/me", null).status());
  }

  @Test
  void theAliasCallbackCompletesLoginToo() throws Exception {
    IDP.identity("alias-sub", "alias@example.com", "Alias User");
    Response start = get(base() + "/api/login", null);
    Response idp = get(start.headers().firstValue("location").orElseThrow(), null);
    String callback = idp.headers().firstValue("location").orElseThrow();
    Response done = get(callback.replace("/api/oauth/callback", "/api/oauth2/bcallback"), null);
    assertEquals(302, done.status(), done.body());
    assertTrue(cookies(done).contains("basis_sites_session="));
  }

  @Test
  void logoutInvalidatesTheSession() throws Exception {
    IDP.identity("logout-sub", "logout@example.com", "Logout User");
    Response done = loginFlow();
    assertEquals(302, done.status(), done.body());
    // The session cookie alone authenticates: it is the browser's credential.
    String session = cookie(done, "basis_sites_session");
    assertEquals(200, get(base() + "/api/v1/me", session).status());
    assertEquals(204, post(base() + "/api/auth/logout", session, "").status());
    assertEquals(401, get(base() + "/api/v1/me", session).status());
  }

  @Test
  void aSuspendedAccountLosesAccessAndCannotLogInAgain() throws Exception {
    IDP.identity("suspended-sub", "suspended@example.com", "Suspended User");
    String cookie = login();
    assertEquals(200, get(base() + "/api/v1/me", cookie).status());

    AppUserRepository users = context.getBean(AppUserRepository.class);
    AppUserEntity user = users.findByExternalId("suspended-sub").orElseThrow();
    user.setStatus("suspended");
    users.save(user);

    assertEquals(401, get(base() + "/api/v1/me", cookie).status(), "kill switch is re-read");
    Response rejected = loginFlow();
    assertEquals(403, rejected.status(), rejected.body());
  }

  @Test
  void aDifferentSubjectOnALinkedEmailIsRefused() throws Exception {
    IDP.identity("join-sub-one", "join@example.com", "Join One");
    String cookie = login();
    assertEquals(200, get(base() + "/api/v1/me", cookie).status());

    IDP.identity("join-sub-two", "join@example.com", "Join Two");
    Response rejected = loginFlow();
    assertEquals(403, rejected.status(), rejected.body());
    assertTrue(rejected.body().contains("LOGIN_REJECTED"), rejected.body());
  }

  private static String login() throws Exception {
    Response done = loginFlow();
    assertEquals(302, done.status(), done.body());
    String cookie = cookies(done);
    assertTrue(cookie.contains("basis_sites_session="), cookie);
    assertTrue(cookie.contains("token="), cookie);
    return cookie;
  }

  private static Response loginFlow() throws Exception {
    Response start = get(base() + "/api/login", null);
    assertEquals(302, start.status(), start.body());
    String authorize = start.headers().firstValue("location").orElseThrow();
    assertTrue(authorize.contains("/authorize?"), authorize);
    Response idp = get(authorize, null);
    assertEquals(302, idp.status());
    String callback = idp.headers().firstValue("location").orElseThrow();
    return get(callback, null);
  }

  private static String base() {
    return "http://127.0.0.1:" + PORT;
  }

  private static String cookies(Response response) {
    return response.headers().allValues("set-cookie").stream()
        .map(header -> header.split(";", 2)[0])
        .collect(Collectors.joining("; "));
  }

  private static String cookie(Response response, String name) {
    return response.headers().allValues("set-cookie").stream()
        .map(header -> header.split(";", 2)[0])
        .filter(pair -> pair.startsWith(name + "="))
        .findFirst()
        .orElseThrow(() -> new AssertionError(name + " cookie missing"));
  }

  private static Response get(String url, String cookie) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
    if (cookie != null) {
      request.header("Cookie", cookie);
    }
    return send(request.build());
  }

  private static Response post(String url, String cookie, String body) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");
    if (cookie != null) {
      request.header("Cookie", cookie);
    }
    return send(request.build());
  }

  private static Response send(HttpRequest request) throws Exception {
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    return new Response(response.statusCode(), response.headers(), response.body());
  }

  private static MockBasisAuth startIdp() {
    try {
      return MockBasisAuth.start("jahaziel", "s3cret");
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
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
      return Files.createTempDirectory("jahaziel-devconnect-test");
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  private record Response(int status, java.net.http.HttpHeaders headers, String body) {}
}
