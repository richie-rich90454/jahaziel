/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A hermetic stand-in for basis-auth: discovery, authorize, token, userinfo, and JWKS over
 * loopback. The authorization code is single-use; PKCE S256 is verified on exchange. The subject
 * and email are mutable so a test can drive the email-join and disabled-account paths.
 */
final class MockBasisAuth implements AutoCloseable {
  private final HttpServer server;
  private final int port;
  private final RSAKey signingKey;
  private final String clientId;
  private final String clientSecret;
  private final Map<String, Pending> codes = new ConcurrentHashMap<>();
  private final Map<String, Identity> accessTokens = new ConcurrentHashMap<>();

  private volatile Identity identity =
      new Identity("devconnect-sub-1", "user@example.com", "Dev User");

  private MockBasisAuth(
      HttpServer server, RSAKey signingKey, String clientId, String clientSecret) {
    this.server = server;
    this.port = server.getAddress().getPort();
    this.signingKey = signingKey;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  static MockBasisAuth start(String clientId, String clientSecret) throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    RSAKey signingKey;
    try {
      signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    } catch (Exception failure) {
      throw new IOException("cannot generate the test signing key", failure);
    }
    MockBasisAuth idp = new MockBasisAuth(server, signingKey, clientId, clientSecret);
    server.createContext("/.well-known/openid-configuration", idp::discovery);
    server.createContext("/authorize", idp::authorize);
    server.createContext("/token", idp::token);
    server.createContext("/userinfo", idp::userinfo);
    server.createContext("/jwks", idp::jwks);
    server.start();
    return idp;
  }

  String issuer() {
    return "http://127.0.0.1:" + port;
  }

  void identity(String sub, String email, String name) {
    this.identity = new Identity(sub, email, name);
  }

  Identity identity() {
    return identity;
  }

  private void discovery(HttpExchange exchange) throws IOException {
    json(
        exchange,
        200,
        "{\"issuer\":\""
            + issuer()
            + "\",\"authorization_endpoint\":\""
            + issuer()
            + "/authorize\",\"token_endpoint\":\""
            + issuer()
            + "/token\",\"userinfo_endpoint\":\""
            + issuer()
            + "/userinfo\",\"jwks_uri\":\""
            + issuer()
            + "/jwks\"}");
  }

  private void authorize(HttpExchange exchange) throws IOException {
    Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
    String redirectUri = query.get("redirect_uri");
    String state = query.get("state");
    if (redirectUri == null || state == null) {
      json(exchange, 400, "{\"error\":\"invalid_request\"}");
      return;
    }
    String code = UUID.randomUUID().toString();
    codes.put(
        code, new Pending(query.get("code_challenge"), redirectUri, identity, query.get("nonce")));
    String location =
        redirectUri
            + (redirectUri.contains("?") ? '&' : '?')
            + "code="
            + code
            + "&state="
            + urlEncode(state);
    exchange.getResponseHeaders().add("Location", location);
    exchange.sendResponseHeaders(302, -1);
    exchange.close();
  }

  private void token(HttpExchange exchange) throws IOException {
    if (!basicAuthorized(exchange)) {
      json(exchange, 401, "{\"error\":\"invalid_client\"}");
      return;
    }
    Map<String, String> form = parseQuery(readBody(exchange));
    Pending pending = codes.remove(form.get("code"));
    if (pending == null) {
      json(exchange, 400, "{\"error\":\"invalid_grant\"}");
      return;
    }
    if (!challenge(form.get("code_verifier")).equals(pending.challenge())) {
      json(exchange, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"pkce\"}");
      return;
    }
    String accessToken = UUID.randomUUID().toString();
    accessTokens.put(accessToken, pending.identity());
    json(
        exchange,
        200,
        "{\"id_token\":\""
            + idToken(pending.identity().sub(), pending.nonce())
            + "\",\"access_token\":\""
            + accessToken
            + "\",\"token_type\":\"Bearer\"}");
  }

  private void userinfo(HttpExchange exchange) throws IOException {
    String header = exchange.getRequestHeaders().getFirst("Authorization");
    Identity found = header == null ? null : accessTokens.get(header.replace("Bearer ", ""));
    if (found == null) {
      json(exchange, 401, "{\"error\":\"invalid_token\"}");
      return;
    }
    json(
        exchange,
        200,
        "{\"sub\":\""
            + found.sub()
            + "\",\"email\":\""
            + found.email()
            + "\",\"name\":\""
            + found.name()
            + "\"}");
  }

  private void jwks(HttpExchange exchange) throws IOException {
    json(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString());
  }

  private String idToken(String sub, String nonce) {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer())
            .subject(sub)
            .audience(clientId)
            .issueTime(new Date())
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .claim("nonce", nonce)
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
    try {
      jwt.sign(new RSASSASigner(signingKey));
    } catch (Exception failure) {
      throw new IllegalStateException("cannot sign test ID token", failure);
    }
    return jwt.serialize();
  }

  private boolean basicAuthorized(HttpExchange exchange) {
    String header = exchange.getRequestHeaders().getFirst("Authorization");
    if (header == null || !header.startsWith("Basic ")) {
      return false;
    }
    String decoded =
        new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
    return decoded.equals(clientId + ":" + clientSecret);
  }

  private static String readBody(HttpExchange exchange) throws IOException {
    return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
  }

  private static void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private static Map<String, String> parseQuery(String raw) {
    Map<String, String> values = new HashMap<>();
    if (raw == null || raw.isBlank()) {
      return values;
    }
    for (String pair : raw.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0) {
        values.put(urlDecode(pair.substring(0, eq)), urlDecode(pair.substring(eq + 1)));
      }
    }
    return values;
  }

  private static String challenge(String verifier) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((verifier == null ? "" : verifier).getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String urlDecode(String value) {
    return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private record Pending(String challenge, String redirectUri, Identity identity, String nonce) {}

  record Identity(String sub, String email, String name) {}
}
