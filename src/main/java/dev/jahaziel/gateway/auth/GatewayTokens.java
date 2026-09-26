/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.auth;

import java.util.Map;
import java.util.Objects;

/**
 * Extracts session tokens from HTTP requests. Three carriers, first win: the {@code Authorization}
 * bearer token, the {@code ?token=} query parameter browsers add to entry URLs, and the session
 * cookie the relay sets on those entries so later same-host requests (sub-resources, WebSockets)
 * stay authenticated without re-attaching the token.
 */
public final class GatewayTokens {
  /** Cookie carrying the session token for same-host follow-up requests. */
  public static final String SESSION_COOKIE = "jahaziel-session";

  private GatewayTokens() {}

  /**
   * Returns the bearer token, or null.
   *
   * @param headers the lowercase request headers
   * @return the token, or null when no bearer authorization is present
   */
  public static String bearer(Map<String, String> headers) {
    Objects.requireNonNull(headers, "headers");
    String authorization = headers.get("authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      return authorization.substring("Bearer ".length()).trim();
    }
    return null;
  }

  /**
   * Returns the {@code ?token=} query token, or null.
   *
   * @param target the request target
   * @return the token, or null when absent
   */
  public static String queryToken(String target) {
    Objects.requireNonNull(target, "target");
    int query = target.indexOf('?');
    if (query < 0) {
      return null;
    }
    for (String pair : target.substring(query + 1).split("&")) {
      int equals = pair.indexOf('=');
      if (equals > 0 && pair.substring(0, equals).equals("token")) {
        return pair.substring(equals + 1);
      }
    }
    return null;
  }

  /**
   * Returns the session cookie token, or null.
   *
   * @param cookieHeader the raw {@code Cookie} header value, or null
   * @return the token, or null when absent
   */
  public static String cookieToken(String cookieHeader) {
    if (cookieHeader == null) {
      return null;
    }
    for (String pair : cookieHeader.split(";")) {
      int equals = pair.indexOf('=');
      if (equals > 0 && pair.substring(0, equals).strip().equals(SESSION_COOKIE)) {
        return pair.substring(equals + 1).strip();
      }
    }
    return null;
  }

  /**
   * Returns the first token found across all carriers.
   *
   * @param headers the lowercase request headers
   * @param target the request target
   * @return the token, or null when no carrier has one
   */
  public static String firstToken(Map<String, String> headers, String target) {
    String token = bearer(headers);
    if (token != null) {
      return token;
    }
    token = queryToken(target);
    if (token != null) {
      return token;
    }
    return cookieToken(headers.get("cookie"));
  }
}
