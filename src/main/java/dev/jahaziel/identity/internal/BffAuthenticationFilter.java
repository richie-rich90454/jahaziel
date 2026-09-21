/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cookie authentication for DevConnect sessions. The {@code token} cookie is a bearer token, but
 * the account is still re-checked so a suspend takes effect without waiting for the token to
 * expire; the {@code basis_sites_session} cookie resolves to an active account the same way. A
 * stale or disabled cookie is cleared and the request continues anonymously.
 */
public final class BffAuthenticationFilter extends OncePerRequestFilter {
  private final DevConnectService devconnect;
  private final JwtService tokens;
  private final UserService users;

  /**
   * Creates the filter.
   *
   * @param devconnect the DevConnect session lookup
   * @param tokens the API token verifier
   * @param users the account and role lookup
   */
  public BffAuthenticationFilter(
      DevConnectService devconnect, JwtService tokens, UserService users) {
    this.devconnect = Objects.requireNonNull(devconnect, "devconnect");
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.users = Objects.requireNonNull(users, "users");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (devconnect.enabled()
        && request.getHeader(HttpHeaders.AUTHORIZATION) == null
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      authenticateFromCookies(request, response);
    }
    chain.doFilter(request, response);
  }

  private void authenticateFromCookies(HttpServletRequest request, HttpServletResponse response) {
    String apiToken = cookie(request, DevConnectService.TOKEN_COOKIE);
    if (apiToken != null) {
      try {
        UserId user = tokens.verify(apiToken).user();
        if (users.isActive(user)) {
          publish(user);
          return;
        }
      } catch (RuntimeException stale) {
        // Fall through and clear the cookie.
      }
      expire(response, DevConnectService.TOKEN_COOKIE);
    }
    String sessionId = cookie(request, DevConnectService.SESSION_COOKIE);
    if (sessionId == null) {
      return;
    }
    var user = devconnect.activeUser(sessionId);
    if (user.isEmpty()) {
      expire(response, DevConnectService.SESSION_COOKIE);
      return;
    }
    publish(user.get());
  }

  private void publish(UserId user) {
    List<SimpleGrantedAuthority> authorities =
        users.get(user).roles().stream().map(SimpleGrantedAuthority::new).toList();
    var authentication =
        new UsernamePasswordAuthenticationToken(user.value().toString(), null, authorities);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private static String cookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie candidate : cookies) {
      if (name.equals(candidate.getName())) {
        return candidate.getValue();
      }
    }
    return null;
  }

  private static void expire(HttpServletResponse response, String name) {
    ResponseCookie cleared =
        ResponseCookie.from(name, "")
            .httpOnly(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cleared.toString());
  }
}
