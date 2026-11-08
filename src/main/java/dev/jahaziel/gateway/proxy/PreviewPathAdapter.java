/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.proxy;

import dev.jahaziel.gateway.auth.GatewaySession;
import dev.jahaziel.gateway.auth.GatewayTokens;
import dev.jahaziel.gateway.auth.JwtRejectedException;
import dev.jahaziel.gateway.auth.JwtVerifier;
import dev.jahaziel.gateway.routing.WorkspaceDirectory;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adapts fixed-host preview paths to the existing authenticated workspace relay. */
public final class PreviewPathAdapter implements Filter {
  private static final Pattern ROUTE =
      Pattern.compile(
          "^/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/([0-9]{1,5})(/.*)?$");
  private static final String PORT_SCOPE = "workspace:port:";
  private final String previewHost;
  private final WorkspaceDirectory routes;
  private final JwtVerifier tokens;

  /**
   * Creates the adapter.
   *
   * @param previewHost the exact fixed preview hostname
   * @param routes the live workspace port routes
   * @param tokens the verifier for port-scoped preview tokens
   */
  public PreviewPathAdapter(String previewHost, WorkspaceDirectory routes, JwtVerifier tokens) {
    if (previewHost == null || previewHost.isBlank()) {
      throw new IllegalArgumentException("previewHost must not be blank");
    }
    this.previewHost = normalizeHost(previewHost);
    if (!this.previewHost.startsWith("preview.")
        || this.previewHost.length() == "preview.".length()) {
      throw new IllegalArgumentException("previewHost must use the fixed preview.<domain> form");
    }
    this.routes = Objects.requireNonNull(routes, "routes");
    this.tokens = Objects.requireNonNull(tokens, "tokens");
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest httpRequest
        && response instanceof HttpServletResponse httpResponse)) {
      chain.doFilter(request, response);
      return;
    }
    if (!previewHost.equals(normalizeHost(httpRequest.getServerName()))) {
      chain.doFilter(request, response);
      return;
    }

    PreviewRoute route = parseRoute(httpRequest.getRequestURI());
    if (route == null) {
      httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "no such preview route");
      return;
    }
    String slug = routes.lookupSlug(route.workspaceId(), route.port()).orElse(null);
    if (slug == null) {
      httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "no such preview route");
      return;
    }
    if (!authorizedForRoute(httpRequest, httpResponse, route)) {
      return;
    }

    String relayHost = slug + "-" + route.port() + "." + previewHost.substring("preview.".length());
    String prefix = "/" + route.workspaceId() + "/" + route.port();
    String upstreamPath = route.upstreamPath();
    HttpServletRequest adaptedRequest =
        new PreviewRequest(httpRequest, relayHost, upstreamPath, prefix);
    HttpServletResponse adaptedResponse =
        new PreviewResponse(httpResponse, httpRequest, prefix, previewHost);
    chain.doFilter(adaptedRequest, adaptedResponse);
  }

  private boolean authorizedForRoute(
      HttpServletRequest request, HttpServletResponse response, PreviewRoute route)
      throws IOException {
    String token =
        GatewayTokens.firstToken(headers(request), request.getRequestURI() + query(request));
    if (token == null) {
      return true;
    }
    GatewaySession session;
    try {
      session = tokens.verify(token);
    } catch (JwtRejectedException rejected) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "rejected session token");
      return false;
    }
    if (!session.workspaceId().equals(route.workspaceId())) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "session workspace mismatch");
      return false;
    }
    if (!session.scopes().contains(PORT_SCOPE + route.port())) {
      response.sendError(
          HttpServletResponse.SC_FORBIDDEN, "session does not allow this preview port");
      return false;
    }
    return true;
  }

  private static Map<String, String> headers(HttpServletRequest request) {
    var headers = new java.util.LinkedHashMap<String, String>();
    var names = request.getHeaderNames();
    if (names == null) return Map.of();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      String value = request.getHeader(name);
      if (value != null) headers.put(name.toLowerCase(Locale.ROOT), value);
    }
    return headers;
  }

  private static String query(HttpServletRequest request) {
    String query = request.getQueryString();
    return query == null || query.isEmpty() ? "" : "?" + query;
  }

  private static PreviewRoute parseRoute(String path) {
    if (path == null) return null;
    Matcher matcher = ROUTE.matcher(path);
    if (!matcher.matches()) return null;
    try {
      UUID workspaceId = UUID.fromString(matcher.group(1));
      if (!workspaceId.toString().equals(matcher.group(1))) return null;
      int port = Integer.parseInt(matcher.group(2));
      if (port < 1 || port > 65535 || !Integer.toString(port).equals(matcher.group(2))) return null;
      String suffix = matcher.group(3);
      String upstreamPath = suffix == null || suffix.isEmpty() ? "/" : suffix;
      if (upstreamPath.equals("/")) return new PreviewRoute(workspaceId, port, "/");
      return new PreviewRoute(workspaceId, port, upstreamPath);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private static String normalizeHost(String host) {
    if (host == null) return "";
    String normalized = host.strip().toLowerCase(Locale.ROOT);
    if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
    return normalized;
  }

  private record PreviewRoute(UUID workspaceId, int port, String upstreamPath) {}

  private static final class PreviewRequest extends HttpServletRequestWrapper {
    private final String relayHost;
    private final String upstreamPath;
    private final String prefix;

    private PreviewRequest(
        HttpServletRequest request, String relayHost, String upstreamPath, String prefix) {
      super(request);
      this.relayHost = relayHost;
      this.upstreamPath = upstreamPath;
      this.prefix = prefix;
    }

    @Override
    public String getServerName() {
      return relayHost;
    }

    @Override
    public String getRequestURI() {
      return upstreamPath;
    }

    @Override
    public String getHeader(String name) {
      if (name != null && name.equalsIgnoreCase("X-Forwarded-Prefix")) return prefix;
      return super.getHeader(name);
    }

    @Override
    public java.util.Enumeration<String> getHeaderNames() {
      List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
      if (names.stream().noneMatch(name -> name.equalsIgnoreCase("X-Forwarded-Prefix"))) {
        names.add("X-Forwarded-Prefix");
      }
      return Collections.enumeration(names);
    }
  }

  private static final class PreviewResponse extends HttpServletResponseWrapper {
    private final String prefix;
    private final String previewHost;
    private final HttpServletRequest request;

    private PreviewResponse(
        HttpServletResponse response,
        HttpServletRequest request,
        String prefix,
        String previewHost) {
      super(response);
      this.prefix = prefix;
      this.previewHost = previewHost;
      this.request = request;
    }

    @Override
    public void addHeader(String name, String value) {
      super.addHeader(name, rewriteHeader(name, value));
    }

    @Override
    public void setHeader(String name, String value) {
      super.setHeader(name, rewriteHeader(name, value));
    }

    @Override
    public void sendRedirect(String location) throws IOException {
      super.sendRedirect(rewriteLocation(location, prefix, previewHost, request));
    }

    private String rewriteHeader(String name, String value) {
      if (value == null) return null;
      if (name.equalsIgnoreCase("Set-Cookie")) return scopeCookie(value, prefix);
      if (name.equalsIgnoreCase("Location"))
        return rewriteLocation(value, prefix, previewHost, request);
      return value;
    }
  }

  static String scopeCookie(String cookie, String prefix) {
    String[] attributes = cookie.split(";");
    StringBuilder result = new StringBuilder(attributes[0].strip());
    boolean hasPath = false;
    for (int index = 1; index < attributes.length; index++) {
      String attribute = attributes[index].strip();
      if (attribute.regionMatches(true, 0, "Path=", 0, 5)) {
        hasPath = true;
        String path = attribute.substring(5).strip();
        result.append("; Path=").append(scopedPath(path, prefix));
      } else {
        result.append("; ").append(attribute);
      }
    }
    if (!hasPath) result.append("; Path=").append(prefix).append("/");
    return result.toString();
  }

  private static String scopedPath(String path, String prefix) {
    if (path.equals(prefix) || path.startsWith(prefix + "/")) return path;
    String suffix = path.startsWith("/") ? path : "/" + path;
    return prefix + suffix;
  }

  private static String rewriteLocation(
      String location, String prefix, String previewHost, HttpServletRequest request) {
    if (location == null || location.isBlank()) return location;
    try {
      URI uri = URI.create(location);
      boolean absolute = uri.isAbsolute() || uri.getRawAuthority() != null;
      if (absolute && !previewHost.equals(normalizeHost(uri.getHost()))) return location;
      String rawPath = uri.getRawPath();
      if (rawPath == null || rawPath.isEmpty()) return location;
      if (rawPath.equals(prefix) || rawPath.startsWith(prefix + "/")) return location;
      String rewritten = normalizeRedirectPath(rawPath, request, prefix);
      StringBuilder result = new StringBuilder();
      if (uri.isAbsolute()) result.append(uri.getScheme()).append(':');
      if (uri.getRawAuthority() != null) result.append("//").append(uri.getRawAuthority());
      result.append(rewritten);
      if (uri.getRawQuery() != null) result.append('?').append(uri.getRawQuery());
      if (uri.getRawFragment() != null) result.append('#').append(uri.getRawFragment());
      return result.toString();
    } catch (IllegalArgumentException malformed) {
      return location;
    }
  }

  private static String normalizeRedirectPath(
      String rawPath, HttpServletRequest request, String prefix) {
    List<String> segments = new ArrayList<>(List.of(prefix.substring(1).split("/")));
    boolean rootRelative = rawPath.startsWith("/");
    if (!rootRelative && request != null) {
      String current = request.getRequestURI();
      List<String> currentSegments = new ArrayList<>(List.of(current.substring(1).split("/")));
      if (!current.endsWith("/") && !currentSegments.isEmpty()) {
        currentSegments.remove(currentSegments.size() - 1);
      }
      if (currentSegments.size() >= 2
          && currentSegments.get(0).equals(segments.get(0))
          && currentSegments.get(1).equals(segments.get(1))) {
        segments = currentSegments;
      }
    }
    String path = rootRelative ? rawPath.substring(1) : rawPath;
    for (String segment : path.split("/")) {
      if (segment.isEmpty()) continue;
      String decoded = decodeSegment(segment);
      if (decoded.equals(".")) continue;
      if (decoded.equals("..")) {
        if (segments.size() > 2) segments.remove(segments.size() - 1);
      } else {
        segments.add(segment);
      }
    }
    return "/" + String.join("/", segments);
  }

  private static String decodeSegment(String segment) {
    try {
      return URI.create("/" + segment).getPath().substring(1);
    } catch (IllegalArgumentException malformed) {
      return segment;
    }
  }
}
