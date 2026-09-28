/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.proxy;

import dev.jahaziel.gateway.auth.GatewaySession;
import dev.jahaziel.gateway.auth.GatewayTokens;
import dev.jahaziel.gateway.auth.JwtRejectedException;
import dev.jahaziel.gateway.auth.JwtVerifier;
import dev.jahaziel.gateway.routing.HostParser;
import dev.jahaziel.gateway.routing.ParsedHost;
import dev.jahaziel.gateway.routing.WorkspaceDirectory;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.domain.UserId;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.WebConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves workspace traffic on the application port. Requests whose {@code Host} parses as a
 * workspace route ({@code <user-slug>-<port>.<domain>}) are authenticated by session token and
 * relayed to the workspace backend on loopback; every other request passes down the filter chain
 * untouched. Plain HTTP is proxied inline; WebSocket upgrades hand a live backend relay to the
 * container. This is what makes a single port serve the API, the UI, and every workspace IDE.
 */
public final class GatewayRelayFilter implements Filter {
  /** Refresh the session cookie when it expires within this window. */
  static final Duration REFRESH_WITHIN = Duration.ofMinutes(5);

  private static final Pattern EXPIRY = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");
  private static final int MAX_HEAD_BYTES = 64 * 1024;
  private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

  private final HostParser hosts;
  private final JwtVerifier tokens;
  private final WorkspaceDirectory directory;
  private final TargetResolver targets;
  private final JwtService sessions;
  private final Duration sessionTtl;

  /**
   * Creates the filter.
   *
   * @param hosts the workspace host parser
   * @param tokens the session verifier
   * @param directory the routing table
   * @param targets the backend resolver
   * @param sessions the session minter, for cookie refresh
   * @param sessionTtl the lifetime of refreshed sessions
   */
  public GatewayRelayFilter(
      HostParser hosts,
      JwtVerifier tokens,
      WorkspaceDirectory directory,
      TargetResolver targets,
      JwtService sessions,
      Duration sessionTtl) {
    this.hosts = Objects.requireNonNull(hosts, "hosts");
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.directory = Objects.requireNonNull(directory, "directory");
    this.targets = Objects.requireNonNull(targets, "targets");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.sessionTtl = Objects.requireNonNull(sessionTtl, "sessionTtl");
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest httpRequest
        && response instanceof HttpServletResponse httpResponse)) {
      chain.doFilter(request, response);
      return;
    }
    ParsedHost route;
    try {
      route = hosts.parse(httpRequest.getServerName());
    } catch (IllegalArgumentException | NullPointerException notWorkspaceHost) {
      chain.doFilter(request, response);
      return;
    }
    try {
      serveWorkspace(httpRequest, httpResponse, route);
    } catch (GatewayServer.HttpFailure failure) {
      httpResponse.setStatus(failure.status());
      httpResponse.setContentType("text/plain;charset=US-ASCII");
      httpResponse.getWriter().write(failure.getMessage() + "\n");
    }
  }

  private void serveWorkspace(
      HttpServletRequest request, HttpServletResponse response, ParsedHost route)
      throws IOException, ServletException {
    Map<String, String> headers = lowercaseHeaders(request);
    String target = request.getRequestURI() + querySuffix(request.getQueryString());
    String rawToken = GatewayTokens.firstToken(headers, target);
    if (rawToken == null) {
      throw new GatewayServer.HttpFailure(401, "missing session token");
    }
    GatewaySession session;
    try {
      session = tokens.verify(rawToken);
    } catch (JwtRejectedException rejected) {
      throw new GatewayServer.HttpFailure(401, "rejected session token");
    }
    UUID workspaceId =
        directory
            .lookup(route.userSlug(), route.port())
            .orElseThrow(() -> new GatewayServer.HttpFailure(404, "no such route"));
    if (!session.workspaceId().equals(workspaceId)) {
      throw new GatewayServer.HttpFailure(403, "session workspace mismatch");
    }
    InetSocketAddress backend = targets.resolve(workspaceId, route.port());
    String stripped = stripToken(request.getQueryString());
    String forwardTarget = request.getRequestURI() + (stripped == null ? "" : "?" + stripped);
    // Before any body is written: the entry URL carries the token as a query parameter, but the
    // workbench's asset requests do not, so the session must arrive as a cookie. Setting it after
    // relayHttp would be dropped, because flushing the body already commits the response headers.
    refreshCookie(request, response, session, rawToken);
    if (isUpgrade(headers)) {
      RelayUpgrade.pending()
          .set(
              new RelayState(
                  backend, rawRequest(request, forwardTarget), GatewayRelayFilter::connect));
      try {
        request.upgrade(RelayUpgrade.class);
      } finally {
        RelayUpgrade.pending().remove();
      }
      return;
    }
    relayHttp(request, response, headers, forwardTarget, backend);
  }

  private void relayHttp(
      HttpServletRequest request,
      HttpServletResponse response,
      Map<String, String> headers,
      String target,
      InetSocketAddress backend)
      throws IOException {
    String transferEncoding = headers.getOrDefault("transfer-encoding", "");
    if (!transferEncoding.isBlank() && !transferEncoding.equalsIgnoreCase("identity")) {
      throw new GatewayServer.HttpFailure(400, "chunked transfer-encoding unsupported");
    }
    long contentLength = request.getContentLengthLong();
    long remaining = Math.max(contentLength, 0);
    Socket upstream;
    try {
      upstream = connect(backend);
    } catch (IOException unreachable) {
      throw new GatewayServer.HttpFailure(502, "backend unreachable");
    }
    try (upstream) {
      OutputStream out = upstream.getOutputStream();
      out.write(
          (request.getMethod() + " " + target + " " + request.getProtocol() + "\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      for (Map.Entry<String, String> header : headers.entrySet()) {
        String name = header.getKey();
        if (name.equals("connection") || name.equals("keep-alive") || name.equals("upgrade")) {
          continue;
        }
        out.write((name + ": " + header.getValue() + "\r\n").getBytes(StandardCharsets.US_ASCII));
      }
      out.write("connection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
      InputStream body = request.getInputStream();
      byte[] buffer = new byte[32768];
      while (remaining > 0) {
        int read = body.read(buffer, 0, (int) Math.min(buffer.length, remaining));
        if (read < 0) {
          throw new IOException("truncated request body");
        }
        out.write(buffer, 0, read);
        remaining -= read;
      }
      out.flush();
      BackendHead head = readBackendHead(upstream.getInputStream());
      response.setStatus(head.status());
      for (Map.Entry<String, List<String>> header : head.headers().entrySet()) {
        if (header.getKey().equals("connection") || header.getKey().equals("keep-alive")) {
          continue;
        }
        for (String value : header.getValue()) {
          response.addHeader(header.getKey(), value);
        }
      }
      GatewayServer.relay(upstream.getInputStream(), response.getOutputStream());
      response.flushBuffer();
    }
  }

  private void refreshCookie(
      HttpServletRequest request,
      HttpServletResponse response,
      GatewaySession session,
      String rawToken) {
    boolean entry = GatewayTokens.queryToken(querySuffix(request.getQueryString())) != null;
    String cookie = rawToken;
    if (needsRefresh(rawToken)) {
      cookie =
          sessions.issue(
              new UserId(session.userId()),
              session.workspaceId(),
              List.copyOf(session.scopes()),
              sessionTtl);
    }
    if (entry || !cookie.equals(rawToken)) {
      response.addHeader(
          "Set-Cookie",
          GatewayTokens.SESSION_COOKIE + "=" + cookie + "; Path=/; HttpOnly; SameSite=Lax");
    }
  }

  static boolean needsRefresh(String rawToken) {
    return expiresAt(rawToken)
        .map(expiry -> !expiry.isAfter(Instant.now().plus(REFRESH_WITHIN)))
        .orElse(false);
  }

  static Optional<Instant> expiresAt(String token) {
    int first = token.indexOf('.');
    int second = first < 0 ? -1 : token.indexOf('.', first + 1);
    if (first <= 0 || second <= first + 1) {
      return Optional.empty();
    }
    byte[] payload;
    try {
      payload = Base64.getUrlDecoder().decode(token.substring(first + 1, second));
    } catch (IllegalArgumentException badEncoding) {
      return Optional.empty();
    }
    Matcher match = EXPIRY.matcher(new String(payload, StandardCharsets.UTF_8));
    if (!match.find()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Instant.ofEpochSecond(Long.parseLong(match.group(1))));
    } catch (NumberFormatException | java.time.DateTimeException badExpiry) {
      return Optional.empty();
    }
  }

  static boolean isUpgrade(Map<String, String> headers) {
    return "websocket".equalsIgnoreCase(headers.getOrDefault("upgrade", ""));
  }

  static String querySuffix(String query) {
    return query == null ? "" : "?" + query;
  }

  static String stripToken(String query) {
    if (query == null || query.isBlank()) {
      return null;
    }
    List<String> kept = new ArrayList<>();
    for (String pair : query.split("&")) {
      int equals = pair.indexOf('=');
      if (equals > 0 && pair.substring(0, equals).equals("token")) {
        continue;
      }
      kept.add(pair);
    }
    return kept.isEmpty() ? null : String.join("&", kept);
  }

  static Map<String, String> lowercaseHeaders(HttpServletRequest request) {
    Map<String, String> headers = new LinkedHashMap<>();
    var names = request.getHeaderNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      headers.put(name.toLowerCase(java.util.Locale.ROOT), request.getHeader(name));
    }
    return headers;
  }

  static String rawRequest(HttpServletRequest request, String target) {
    StringBuilder head = new StringBuilder();
    head.append(request.getMethod())
        .append(' ')
        .append(target)
        .append(' ')
        .append(request.getProtocol())
        .append("\r\n");
    var names = request.getHeaderNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (name.equalsIgnoreCase("connection")
          || name.equalsIgnoreCase("keep-alive")
          || name.equalsIgnoreCase("upgrade")) {
        continue;
      }
      head.append(name).append(": ").append(request.getHeader(name)).append("\r\n");
    }
    return head + "\r\n";
  }

  record BackendHead(int status, Map<String, List<String>> headers) {}

  static BackendHead readBackendHead(InputStream in) throws IOException {
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    StringBuilder current = new StringBuilder();
    List<String> lines = new ArrayList<>();
    int previous = -1;
    int currentByte;
    while ((currentByte = in.read()) >= 0) {
      raw.write(currentByte);
      if (raw.size() > MAX_HEAD_BYTES) {
        throw new GatewayServer.HttpFailure(431, "response head too large");
      }
      if (previous == '\r' && currentByte == '\n') {
        String line = current.toString();
        if (line.isEmpty()) {
          break;
        }
        lines.add(line);
        current.setLength(0);
      } else if (currentByte != '\r') {
        current.append((char) currentByte);
      }
      previous = currentByte;
    }
    if (lines.isEmpty()) {
      throw new GatewayServer.HttpFailure(502, "empty backend response");
    }
    String[] statusLine = lines.get(0).split(" ", 3);
    int status;
    try {
      status = Integer.parseInt(statusLine[1]);
    } catch (ArrayIndexOutOfBoundsException | NumberFormatException malformed) {
      throw new GatewayServer.HttpFailure(502, "malformed backend status");
    }
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (int i = 1; i < lines.size(); i++) {
      int colon = lines.get(i).indexOf(':');
      if (colon > 0) {
        headers
            .computeIfAbsent(
                lines.get(i).substring(0, colon).strip().toLowerCase(), key -> new ArrayList<>())
            .add(lines.get(i).substring(colon + 1).strip());
      }
    }
    return new BackendHead(status, headers);
  }

  /** Carries the backend connection across the container upgrade boundary. */
  record RelayState(InetSocketAddress backend, String requestHead, SocketFactory sockets) {}

  /** Opens backend sockets; substituted in tests for deterministic failures. */
  interface SocketFactory {
    /**
     * Connects to a backend.
     *
     * @param backend the address
     * @return the connected socket
     * @throws IOException when unreachable
     */
    Socket connect(InetSocketAddress backend) throws IOException;
  }

  /**
   * WebSocket relay. The container instantiates this itself, so the backend connection arrives
   * through a thread-local handoff set just before {@code upgrade()} on the same thread.
   */
  public static final class RelayUpgrade implements HttpUpgradeHandler {
    private static final ThreadLocal<RelayState> PENDING = new ThreadLocal<>();

    static ThreadLocal<RelayState> pending() {
      return PENDING;
    }

    @Override
    public void init(WebConnection connection) {
      RelayState state = PENDING.get();
      if (state == null) {
        throw new IllegalStateException("no relay state for upgrade");
      }
      Socket upstream;
      InputStream backendIn;
      OutputStream backendOut;
      try {
        upstream = state.sockets().connect(state.backend());
        upstream.getOutputStream().write(state.requestHead().getBytes(StandardCharsets.US_ASCII));
        upstream.getOutputStream().flush();
        backendIn = upstream.getInputStream();
        backendOut = upstream.getOutputStream();
      } catch (IOException unreachable) {
        throw new UncheckedIOException("backend unreachable for upgrade", unreachable);
      }
      InputStream clientIn;
      OutputStream clientOut;
      try {
        clientIn = connection.getInputStream();
        clientOut = connection.getOutputStream();
      } catch (IOException broken) {
        closeQuietly(upstream);
        throw new IllegalStateException("upgrade connection broken", broken);
      }
      Thread.ofVirtual().start(() -> relayQuietly(backendIn, clientOut, upstream));
      Thread.ofVirtual().start(() -> relayQuietly(clientIn, backendOut, upstream));
    }

    @Override
    public void destroy() {}

    static void relayQuietly(InputStream in, OutputStream out, Socket owned) {
      try {
        GatewayServer.relay(in, out);
      } catch (IOException | RuntimeException closed) {
        // Either side went away; the relay is over.
      } finally {
        closeQuietly(owned);
      }
    }

    private static void closeQuietly(Socket socket) {
      try {
        socket.close();
      } catch (IOException ignored) {
        // Already closed by the other direction.
      }
    }
  }

  static Socket connect(InetSocketAddress backend) throws IOException {
    Socket socket = new Socket();
    socket.connect(backend, CONNECT_TIMEOUT_MILLIS);
    return socket;
  }
}
