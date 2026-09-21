/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.domain.UserId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer token authentication. Verifies the session token and publishes the user id as the
 * principal; authorization stays in the controllers. Missing tokens pass through anonymous for
 * public endpoints.
 */
public final class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService tokens;
  private final UserLookup users;

  /**
   * Creates the filter.
   *
   * @param tokens the token service
   * @param users the role lookup
   */
  public JwtAuthenticationFilter(JwtService tokens, UserLookup users) {
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.users = Objects.requireNonNull(users, "users");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        var session = tokens.verify(header.substring("Bearer ".length()).trim());
        List<SimpleGrantedAuthority> authorities =
            users.roles(session.user()).stream().map(SimpleGrantedAuthority::new).toList();
        var authentication =
            new UsernamePasswordAuthenticationToken(
                session.user().value().toString(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (RuntimeException rejected) {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response
            .getWriter()
            .write(
                "{\"code\":\"AUTH_INVALID\",\"message\":\"Invalid session token\","
                    + "\"correlationId\":\""
                    + UUID.randomUUID()
                    + "\",\"details\":{}}");
        return;
      }
    }
    chain.doFilter(request, response);
  }

  /** Role lookup for authentication. */
  public interface UserLookup {
    /**
     * Returns role names.
     *
     * @param user the user identifier
     * @return the role names
     */
    List<String> roles(UserId user);
  }

  /**
   * Parses a principal name back to a user id.
   *
   * @param name the principal name
   * @return the user identifier
   */
  public static UserId userId(String name) {
    try {
      return new UserId(UUID.fromString(name));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }
}
