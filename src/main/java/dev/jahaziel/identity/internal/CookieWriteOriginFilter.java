/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Requires a same-origin browser proof for writes authenticated by cookies or demo identity. */
final class CookieWriteOriginFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (safeMethod(request.getMethod()) || isBearerRequest(request)) {
      chain.doFilter(request, response);
      return;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      chain.doFilter(request, response);
      return;
    }
    if (sameOrigin(request)) {
      chain.doFilter(request, response);
      return;
    }
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json");
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response
        .getWriter()
        .write(
            "{\"code\":\"CSRF_REJECTED\",\"message\":\"Request origin rejected\","
                + "\"correlationId\":\""
                + UUID.randomUUID()
                + "\",\"details\":{}}");
  }

  private static boolean safeMethod(String method) {
    return "GET".equalsIgnoreCase(method)
        || "HEAD".equalsIgnoreCase(method)
        || "OPTIONS".equalsIgnoreCase(method)
        || "TRACE".equalsIgnoreCase(method);
  }

  private static boolean isBearerRequest(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    return authorization != null && authorization.startsWith("Bearer ");
  }

  private static boolean sameOrigin(HttpServletRequest request) {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    if (origin == null || origin.isBlank()) return false;
    String fetchSite = request.getHeader("Sec-Fetch-Site");
    if (fetchSite != null && !"same-origin".equalsIgnoreCase(fetchSite.strip())) return false;
    try {
      URI source = URI.create(origin);
      URI target = requestOrigin(request);
      return validOrigin(source)
          && validOrigin(target)
          && source.getScheme().equalsIgnoreCase(target.getScheme())
          && source.getHost().equalsIgnoreCase(target.getHost())
          && effectivePort(source) == effectivePort(target);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  private static URI requestOrigin(HttpServletRequest request) {
    String scheme = first(request.getHeader("X-Forwarded-Proto"));
    if (scheme == null) scheme = request.getScheme();
    String authority = first(request.getHeader("X-Forwarded-Host"));
    if (authority == null) {
      String host = request.getServerName();
      if (host.indexOf(':') >= 0 && !host.startsWith("[")) host = "[" + host + "]";
      authority = host + ":" + request.getServerPort();
    }
    return URI.create(scheme.toLowerCase(Locale.ROOT) + "://" + authority);
  }

  private static String first(String header) {
    if (header == null || header.isBlank()) return null;
    String value = header.split(",", 2)[0].strip();
    return value.isEmpty() ? null : value;
  }

  private static boolean validOrigin(URI uri) {
    return uri.getHost() != null
        && uri.getUserInfo() == null
        && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
        && (uri.getRawPath() == null || uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
        && uri.getQuery() == null
        && uri.getFragment() == null;
  }

  private static int effectivePort(URI uri) {
    if (uri.getPort() >= 0) return uri.getPort();
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }
}
