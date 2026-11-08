/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security.api;

import java.net.URI;
import java.util.Locale;

/** Compares a browser origin with the public request origin, including trusted proxy headers. */
public final class BrowserOrigin {
  private BrowserOrigin() {}

  /**
   * Checks an optional Origin header against the request origin visible to the browser.
   *
   * <p>When present, the first values of {@code X-Forwarded-Proto} and {@code X-Forwarded-Host}
   * describe the public request origin. These headers must be set by a trusted reverse proxy.
   *
   * @param origin the browser Origin header, or {@code null} when absent
   * @param forwardedProto the X-Forwarded-Proto header
   * @param forwardedHost the X-Forwarded-Host header
   * @param host the Host header
   * @param requestUri the request URI seen by the application
   * @return whether the optional browser origin matches the public request origin
   */
  public static boolean matchesIfPresent(
      String origin, String forwardedProto, String forwardedHost, String host, URI requestUri) {
    if (origin == null || origin.isBlank()) return true;
    try {
      URI source = URI.create(origin);
      String scheme = first(forwardedProto);
      if (scheme == null) scheme = requestUri.getScheme();
      String authority = first(forwardedHost);
      if (authority == null) authority = first(host);
      if (authority == null) authority = requestUri.getRawAuthority();
      if (scheme == null || authority == null) return false;

      URI target = URI.create(scheme.toLowerCase(Locale.ROOT) + "://" + authority);
      return validOrigin(source)
          && validOrigin(target)
          && source.getScheme().equalsIgnoreCase(target.getScheme())
          && source.getHost().equalsIgnoreCase(target.getHost())
          && effectivePort(source) == effectivePort(target);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
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
