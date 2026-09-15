/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import dev.jahaziel.identity.api.DevConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The basis-auth OIDC calls: discovery, authorization URL, code exchange, UserInfo, and ID token
 * verification. Discovery is cached for five minutes and retried three times; every call aborts at
 * the request factory's timeout. JWKS handling lives in one decoder per {@code jwks_uri}.
 */
public final class DevConnectClient {
  private static final Duration DISCOVERY_TTL = Duration.ofMinutes(5);
  private static final int DISCOVERY_ATTEMPTS = 3;
  private static final String SCOPE = "openid profile email";

  private final RestClient http;
  private final RestTemplate rest;
  private final DevConnectProperties props;
  private final Clock clock;
  private final ObjectMapper mapper = new ObjectMapper();

  private volatile DiscoveryDocument discovery;
  private volatile Instant discoveredAt = Instant.EPOCH;
  private volatile JwtDecoder decoder;
  private volatile String decoderJwksUri;

  /**
   * Creates the client.
   *
   * @param http the REST client, with bounded timeouts
   * @param rest the JWKS-fetching template, with bounded timeouts
   * @param props the DevConnect settings
   * @param clock the clock
   */
  public DevConnectClient(
      RestClient http, RestTemplate rest, DevConnectProperties props, Clock clock) {
    this.http = http;
    this.rest = rest;
    this.props = props;
    this.clock = clock;
  }

  /**
   * Returns the discovery document, cached for five minutes.
   *
   * @return the discovery document
   */
  public DiscoveryDocument discovery() {
    if (fresh()) {
      return discovery;
    }
    synchronized (this) {
      if (!fresh()) {
        String url = props.issuerBase() + "/.well-known/openid-configuration";
        discovery = parseDiscovery(fetch(url), url);
        discoveredAt = clock.instant();
      }
      return discovery;
    }
  }

  /**
   * Builds the IdP authorization URL: minimal scope, PKCE S256, state, nonce, and the resource
   * audience.
   *
   * @param state the opaque login state
   * @param nonce the ID token nonce
   * @param challenge the PKCE S256 challenge
   * @return the URL the browser is sent to
   */
  public String authorizationUrl(String state, String nonce, String challenge) {
    String endpoint = discovery().authorizationEndpoint();
    StringBuilder url = new StringBuilder(endpoint);
    url.append(endpoint.contains("?") ? '&' : '?');
    appendQuery(url, "response_type", "code");
    appendQuery(url, "client_id", props.getClientId());
    appendQuery(url, "redirect_uri", props.getRedirectUri());
    appendQuery(url, "scope", SCOPE);
    appendQuery(url, "state", state);
    appendQuery(url, "nonce", nonce);
    appendQuery(url, "code_challenge", challenge);
    appendQuery(url, "code_challenge_method", "S256");
    appendQuery(url, "resource", props.getResource());
    return url.toString();
  }

  /**
   * Exchanges the authorization code, authenticating the confidential client with Basic auth.
   *
   * @param code the authorization code
   * @param verifier the PKCE verifier
   * @param redirectUri the redirect URI used to start login
   * @return the id and access tokens
   */
  public Tokens exchange(String code, String verifier, String redirectUri) {
    DiscoveryDocument document = discovery();
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("code", code);
    form.add("redirect_uri", redirectUri);
    form.add("code_verifier", verifier);
    form.add("resource", props.getResource());
    String body;
    try {
      body =
          http.post()
              .uri(document.tokenEndpoint())
              .header(
                  HttpHeaders.AUTHORIZATION, basic(props.getClientId(), props.getClientSecret()))
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(String.class);
    } catch (RuntimeException failure) {
      throw new DevConnectException(
          "DevConnect token exchange failed at " + document.tokenEndpoint() + ": " + cause(failure),
          failure);
    }
    JsonNode node = read(body, document.tokenEndpoint());
    String idToken = textOrNull(node, "id_token");
    String accessToken = textOrNull(node, "access_token");
    if (idToken == null || accessToken == null) {
      throw new DevConnectException(
          "DevConnect token response from "
              + document.tokenEndpoint()
              + " lacks id_token or"
              + " access_token");
    }
    return new Tokens(idToken, accessToken);
  }

  /**
   * Reads identity from UserInfo, which is authoritative once the ID token proves login.
   *
   * @param accessToken the access token
   * @return the identity
   */
  public UserInfo userinfo(String accessToken) {
    DiscoveryDocument document = discovery();
    String body;
    try {
      body =
          http.get()
              .uri(document.userinfoEndpoint())
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .retrieve()
              .body(String.class);
    } catch (RuntimeException failure) {
      throw new DevConnectException(
          "DevConnect UserInfo failed at " + document.userinfoEndpoint() + ": " + cause(failure),
          failure);
    }
    JsonNode node = read(body, document.userinfoEndpoint());
    String sub = textOrNull(node, "sub");
    String email = textOrNull(node, "email");
    if (sub == null || email == null) {
      throw new DevConnectException(
          "DevConnect UserInfo from " + document.userinfoEndpoint() + " lacks sub or email");
    }
    String name = textOrNull(node, "name");
    return new UserInfo(sub, email, name == null || name.isBlank() ? email : name);
  }

  /**
   * Verifies the ID token signature, issuer, audience, timestamps, and nonce.
   *
   * @param idToken the compact ID token
   * @param expectedNonce the nonce stashed when login started
   * @return the verified claims
   */
  public Jwt verifyIdToken(String idToken, String expectedNonce) {
    DiscoveryDocument document = discovery();
    try {
      Jwt jwt = decoderFor(document).decode(idToken);
      if (expectedNonce != null && !expectedNonce.equals(jwt.getClaimAsString("nonce"))) {
        throw new DevConnectException("Login nonce mismatch");
      }
      return jwt;
    } catch (JwtException | IllegalArgumentException rejected) {
      throw new DevConnectException("DevConnect ID token rejected: " + cause(rejected), rejected);
    }
  }

  private boolean fresh() {
    return discovery != null
        && Duration.between(discoveredAt, clock.instant()).compareTo(DISCOVERY_TTL) < 0;
  }

  private String fetch(String url) {
    RuntimeException last = null;
    for (int attempt = 1; attempt <= DISCOVERY_ATTEMPTS; attempt++) {
      try {
        return http.get().uri(url).retrieve().body(String.class);
      } catch (RestClientResponseException rejected) {
        if (rejected.getStatusCode().is4xxClientError()) {
          throw new DevConnectException(
              "DevConnect discovery rejected (HTTP "
                  + rejected.getStatusCode().value()
                  + ") for "
                  + url,
              rejected);
        }
        last = rejected;
      } catch (RuntimeException unreachable) {
        last = unreachable;
      }
      if (attempt < DISCOVERY_ATTEMPTS) {
        sleep(Duration.ofMillis(200L * attempt));
      }
    }
    throw new DevConnectException(
        "DevConnect discovery failed for " + url + ": " + cause(last), last);
  }

  private JwtDecoder decoderFor(DiscoveryDocument document) {
    if (decoder != null && document.jwksUri().equals(decoderJwksUri)) {
      return decoder;
    }
    synchronized (this) {
      if (decoder == null || !document.jwksUri().equals(decoderJwksUri)) {
        NimbusJwtDecoder built =
            NimbusJwtDecoder.withJwkSetUri(document.jwksUri()).restOperations(rest).build();
        built.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(document.issuer()),
                new JwtClaimValidator<List<String>>(
                    JwtClaimNames.AUD,
                    audiences -> audiences != null && audiences.contains(props.getClientId()))));
        decoder = built;
        decoderJwksUri = document.jwksUri();
      }
      return decoder;
    }
  }

  private DiscoveryDocument parseDiscovery(String body, String url) {
    JsonNode node = read(body, url);
    return new DiscoveryDocument(
        required(node, "issuer", url),
        required(node, "authorization_endpoint", url),
        required(node, "token_endpoint", url),
        required(node, "userinfo_endpoint", url),
        required(node, "jwks_uri", url));
  }

  private JsonNode read(String body, String url) {
    try {
      if (body == null || body.isBlank()) {
        throw new DevConnectException("DevConnect response from " + url + " was empty");
      }
      return mapper.readTree(body);
    } catch (JacksonException malformed) {
      throw new DevConnectException(
          "DevConnect response from " + url + " is not JSON: " + cause(malformed), malformed);
    }
  }

  private static String required(JsonNode node, String field, String url) {
    String value = textOrNull(node, field);
    if (value == null || value.isBlank()) {
      throw new DevConnectException(
          "DevConnect discovery document from " + url + " lacks " + field);
    }
    return value;
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asString();
  }

  private static String basic(String user, String secret) {
    String pair = user + ":" + secret;
    return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
  }

  private static void appendQuery(StringBuilder url, String name, String value) {
    url.append(name)
        .append('=')
        .append(java.net.URLEncoder.encode(value, StandardCharsets.UTF_8))
        .append('&');
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new DevConnectException("DevConnect discovery interrupted");
    }
  }

  private static String cause(Throwable failure) {
    if (failure == null) {
      return "unknown cause";
    }
    Throwable root = failure;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String message = failure.getMessage();
    String label = root.getClass().getSimpleName();
    if (message == null || message.isBlank()) {
      return label;
    }
    return message + " (" + label + ")";
  }

  /**
   * The id and access tokens of a completed code exchange.
   *
   * @param idToken the login proof
   * @param accessToken the token used for UserInfo
   */
  public record Tokens(String idToken, String accessToken) {}

  /**
   * The identity read from UserInfo.
   *
   * @param sub the subject, the key from now on
   * @param email the email, used once to join a legacy account
   * @param name the display name
   */
  public record UserInfo(String sub, String email, String name) {}
}
