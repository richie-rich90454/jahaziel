/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.jahaziel.gateway.auth.JwtVerifier;
import dev.jahaziel.gateway.routing.WorkspaceDirectory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PreviewPathAdapterTest {
  private static final String ISSUER = "https://jahaziel.test";
  private static final String AUDIENCE = "jahaziel-code-server";
  private static final String HOST = "preview.code.test";

  private WorkspaceDirectory routes;
  private KeyPair keys;
  private PreviewPathAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    routes = new WorkspaceDirectory();
    keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    adapter =
        new PreviewPathAdapter(
            HOST,
            routes,
            new JwtVerifier(
                (java.security.interfaces.RSAPublicKey) keys.getPublic(),
                ISSUER,
                AUDIENCE,
                Duration.ofMinutes(20)));
  }

  @Test
  void adaptsARegisteredPathAndScopesCookieAndRedirects() throws Exception {
    UUID workspace = UUID.randomUUID();
    routes.register("richard", 8080, workspace);
    MockHttpServletRequest request = request("/" + workspace + "/8080/docs/page");
    request.setQueryString("token=" + token(workspace, 8080));
    MockHttpServletResponse response = new MockHttpServletResponse();

    adapter.doFilter(
        request,
        response,
        (adapted, wrapped) -> {
          var relayRequest = (jakarta.servlet.http.HttpServletRequest) adapted;
          var relayResponse = (jakarta.servlet.http.HttpServletResponse) wrapped;
          assertEquals("richard-8080.code.test", relayRequest.getServerName());
          assertEquals("/docs/page", relayRequest.getRequestURI());
          assertEquals("/" + workspace + "/8080", relayRequest.getHeader("X-Forwarded-Prefix"));
          relayResponse.addHeader("Set-Cookie", "project=one; Path=/; HttpOnly; SameSite=Lax");
          relayResponse.addHeader("Location", "../../login?next=%2F");
        });

    assertEquals(
        List.of("project=one; Path=/" + workspace + "/8080/; HttpOnly; SameSite=Lax"),
        response.getHeaders("Set-Cookie"));
    assertEquals("/" + workspace + "/8080/login?next=%2F", response.getHeader("Location"));
  }

  @Test
  void rejectsTokensForAnotherWorkspaceOrPort() throws Exception {
    UUID workspace = UUID.randomUUID();
    routes.register("richard", 8080, workspace);
    MockHttpServletResponse wrongWorkspace = new MockHttpServletResponse();
    MockHttpServletRequest first = request("/" + workspace + "/8080/");
    first.setQueryString("token=" + token(UUID.randomUUID(), 8080));
    adapter.doFilter(first, wrongWorkspace, (request, response) -> fail("must reject token"));
    assertEquals(403, wrongWorkspace.getStatus());

    MockHttpServletResponse wrongPort = new MockHttpServletResponse();
    MockHttpServletRequest second = request("/" + workspace + "/8080/");
    second.setQueryString("token=" + token(workspace, 9090));
    adapter.doFilter(second, wrongPort, (request, response) -> fail("must reject token"));
    assertEquals(403, wrongPort.getStatus());
  }

  @Test
  void rejectsInvalidAndUnregisteredPreviewPaths() throws Exception {
    UUID workspace = UUID.randomUUID();
    MockHttpServletResponse invalid = new MockHttpServletResponse();
    adapter.doFilter(
        request("/" + workspace + "/0/"),
        invalid,
        (request, response) -> fail("must reject route"));
    assertEquals(404, invalid.getStatus());

    MockHttpServletResponse nonCanonical = new MockHttpServletResponse();
    adapter.doFilter(
        request("/" + workspace.toString().toUpperCase(Locale.ROOT) + "/8080/"),
        nonCanonical,
        (request, response) -> fail("must reject route"));
    assertEquals(404, nonCanonical.getStatus());

    MockHttpServletResponse unknown = new MockHttpServletResponse();
    adapter.doFilter(
        request("/" + workspace + "/8080/"),
        unknown,
        (request, response) -> fail("must reject route"));
    assertEquals(404, unknown.getStatus());
  }

  @Test
  void leavesTheIdeHostUntouched() throws Exception {
    MockHttpServletRequest request = request("/api/v1/me");
    request.setServerName("code.test");
    MockHttpServletResponse response = new MockHttpServletResponse();
    boolean[] reached = {false};

    adapter.doFilter(request, response, (adapted, wrapped) -> reached[0] = true);

    assertTrue(reached[0]);
    assertFalse(response.isCommitted());
  }

  private MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setServerName(HOST);
    request.setScheme("https");
    return request;
  }

  private String token(UUID workspace, int port) throws Exception {
    Date now = new Date();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(UUID.randomUUID().toString())
            .audience(AUDIENCE)
            .claim("wid", workspace.toString())
            .claim("scope", "workspace:port:" + port)
            .issueTime(now)
            .expirationTime(new Date(now.getTime() + Duration.ofMinutes(10).toMillis()))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(keys.getPrivate()));
    return jwt.serialize();
  }

  private static void fail(String message) {
    throw new AssertionError(message);
  }
}
