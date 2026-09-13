/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.proxy;

import dev.jahaziel.gateway.auth.GatewaySession;
import dev.jahaziel.gateway.auth.JwtRejectedException;
import dev.jahaziel.gateway.auth.JwtVerifier;
import dev.jahaziel.gateway.routing.HostParser;
import dev.jahaziel.gateway.routing.ParsedHost;
import dev.jahaziel.gateway.routing.WorkspaceDirectory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * HTTP and WebSocket relay to workspaces. Each connection runs on a virtual thread: authenticate by
 * bearer token or query token, route by Host header, forward the request, relay the response.
 * Request bodies use Content-Length framing; WebSocket upgrades relay blindly after the 101. Client
 * errors answer with a status; only a mid-relay backend death closes silently.
 */
public final class GatewayServer {
  private static final int MAX_HEAD_BYTES = 64 * 1024;
  private static final int HEAD_READ_TIMEOUT_MILLIS = 15_000;

  private final HostParser hosts;
  private final JwtVerifier tokens;
  private final WorkspaceDirectory directory;
  private final TargetResolver targets;
  private volatile boolean running;
  private volatile ServerSocket server;

  /**
   * Creates the server.
   *
   * @param hosts the host parser
   * @param tokens the session verifier
   * @param directory the routing table
   * @param targets the backend resolver
   */
  public GatewayServer(
      HostParser hosts, JwtVerifier tokens, WorkspaceDirectory directory, TargetResolver targets) {
    this.hosts = Objects.requireNonNull(hosts, "hosts");
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.directory = Objects.requireNonNull(directory, "directory");
    this.targets = Objects.requireNonNull(targets, "targets");
  }

  /**
   * Serves forever on the given port.
   *
   * @param port the listen port
   */
  public void serve(int port) {
    running = true;
    try (ServerSocket server = new ServerSocket(port)) {
      this.server = server;
      while (running) {
        Socket client = server.accept();
        Thread.ofVirtual().start(() -> handleQuietly(client));
      }
    } catch (IOException failure) {
      if (running) {
        throw new IllegalStateException("gateway failed on port " + port, failure);
      }
    } finally {
      this.server = null;
    }
  }

  /** Stops accepting new connections. */
  public void stop() {
    running = false;
    ServerSocket socket = server;
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException ignored) {
        // Already closed by serve(); the accept loop is ending.
      }
    }
  }

  private void handleQuietly(Socket client) {
    try (client) {
      handle(client);
    } catch (HttpFailure failure) {
      writeError(client, failure);
    } catch (IOException | RuntimeException failure) {
      // Backend died mid-relay or the client went away; nothing useful to send.
    }
  }

  private void handle(Socket client) throws IOException {
    client.setSoTimeout(HEAD_READ_TIMEOUT_MILLIS);
    InputStream in = client.getInputStream();
    RequestHead head = readHead(in);
    client.setSoTimeout(0);
    String transferEncoding = head.headers().getOrDefault("transfer-encoding", "");
    if (!transferEncoding.isBlank() && !transferEncoding.equalsIgnoreCase("identity")) {
      throw new HttpFailure(400, "chunked transfer-encoding unsupported");
    }
    ParsedHost route = parseRoute(head);
    GatewaySession session = authenticate(head);
    UUID workspaceId = lookup(route);
    checkScope(session, workspaceId);
    InetSocketAddress backend = targets.resolve(workspaceId, route.port());
    Socket upstream;
    try {
      upstream = new Socket(backend.getAddress(), backend.getPort());
    } catch (IOException failure) {
      throw new HttpFailure(502, "backend unreachable");
    }
    try (upstream) {
      forwardRequest(head, in, upstream);
      relay(upstream.getInputStream(), client.getOutputStream());
    }
  }

  private ParsedHost parseRoute(RequestHead head) throws HttpFailure {
    String host = head.headers().get("host");
    if (host == null) {
      throw new HttpFailure(400, "missing Host header");
    }
    int colon = host.indexOf(':');
    String bare = colon < 0 ? host : host.substring(0, colon);
    try {
      return hosts.parse(bare);
    } catch (IllegalArgumentException failure) {
      throw new HttpFailure(404, "unroutable host");
    }
  }

  private GatewaySession authenticate(RequestHead head) throws HttpFailure {
    String token = bearer(head);
    if (token == null) {
      token = queryToken(head);
    }
    if (token == null) {
      throw new HttpFailure(401, "missing session token");
    }
    try {
      return tokens.verify(token);
    } catch (JwtRejectedException failure) {
      throw new HttpFailure(401, "rejected session token");
    }
  }

  private UUID lookup(ParsedHost route) throws HttpFailure {
    return directory
        .lookup(route.userSlug(), route.port())
        .orElseThrow(() -> new HttpFailure(404, "no such route"));
  }

  private static void checkScope(GatewaySession session, UUID workspaceId) throws HttpFailure {
    if (!session.workspaceId().equals(workspaceId)) {
      throw new HttpFailure(403, "session workspace mismatch");
    }
  }

  private static String bearer(RequestHead head) {
    String authorization = head.headers().get("authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      return authorization.substring("Bearer ".length()).trim();
    }
    return null;
  }

  private static String queryToken(RequestHead head) {
    String target = head.target();
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

  private static void forwardRequest(RequestHead head, InputStream in, Socket upstream)
      throws IOException {
    OutputStream out = upstream.getOutputStream();
    out.write(head.raw());
    long remaining = head.contentLength();
    byte[] buffer = new byte[32768];
    while (remaining > 0) {
      int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (read < 0) {
        throw new IOException("truncated request body");
      }
      out.write(buffer, 0, read);
      remaining -= read;
    }
    out.flush();
  }

  private static void relay(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[32768];
    int read;
    while ((read = in.read(buffer)) >= 0) {
      out.write(buffer, 0, read);
    }
    out.flush();
  }

  private static void writeError(Socket client, HttpFailure failure) {
    String body = failure.getMessage() + "\n";
    String response =
        "HTTP/1.1 "
            + failure.status()
            + " Error\r\nContent-Type: text/plain\r\nContent-Length: "
            + body.length()
            + "\r\nConnection: close\r\n\r\n"
            + body;
    try {
      client.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
    } catch (IOException ignored) {
      // Client already gone.
    }
  }

  /**
   * Reads the request line and headers.
   *
   * @param in the client stream
   * @return the parsed head with raw bytes
   */
  static RequestHead readHead(InputStream in) throws IOException {
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    StringBuilder current = new StringBuilder();
    List<String> lines = new ArrayList<>();
    int previous = -1;
    int currentByte;
    while ((currentByte = in.read()) >= 0) {
      raw.write(currentByte);
      if (raw.size() > MAX_HEAD_BYTES) {
        throw new HttpFailure(431, "request head too large");
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
      throw new IOException("empty request");
    }
    String[] requestLine = lines.get(0).split(" ");
    if (requestLine.length != 3) {
      throw new IOException("malformed request line");
    }
    Map<String, String> headers = new LinkedHashMap<>();
    for (int i = 1; i < lines.size(); i++) {
      int colon = lines.get(i).indexOf(':');
      if (colon > 0) {
        headers.put(
            lines.get(i).substring(0, colon).strip().toLowerCase(),
            lines.get(i).substring(colon + 1).strip());
      }
    }
    return new RequestHead(requestLine[0], requestLine[1], headers, raw.toByteArray());
  }

  /** Parsed request head. */
  record RequestHead(String method, String target, Map<String, String> headers, byte[] raw) {
    /** Returns the declared body length, or zero. */
    long contentLength() throws HttpFailure {
      String value = headers.get("content-length");
      if (value == null) {
        return 0;
      }
      try {
        long parsed = Long.parseLong(value.strip());
        if (parsed < 0) {
          throw new HttpFailure(400, "negative content length");
        }
        return parsed;
      } catch (NumberFormatException failure) {
        throw new HttpFailure(400, "invalid content length");
      }
    }
  }

  /** Client error with a status code. */
  static final class HttpFailure extends IOException {
    private final int status;

    HttpFailure(int status, String message) {
      super(message);
      this.status = status;
    }

    /** Returns the HTTP status code. */
    int status() {
      return status;
    }
  }
}
