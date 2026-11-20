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
        if (!(request instanceof HttpServletRequest)
                || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
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
                        .orElseThrow(
                                new java.util.function.Supplier<GatewayServer.HttpFailure>() {
                                    @Override
                                    public GatewayServer.HttpFailure get() {
                                        return new GatewayServer.HttpFailure(404, "no such route");
                                    }
                                });
        if (!session.workspaceId().equals(workspaceId)) {
            throw new GatewayServer.HttpFailure(403, "session workspace mismatch");
        }
        InetSocketAddress backend = targets.resolve(workspaceId, route.port());
        String stripped = stripToken(request.getQueryString());
        String forwardTarget = request.getRequestURI() + (stripped == null ? "" : "?" + stripped);
        // Before any body is written: the entry URL carries the token as a query parameter, but the
        // workbench's asset requests do not, so the session must arrive as a cookie. Setting it
        // after
        // relayHttp would be dropped, because flushing the body already commits the response
        // headers.
        refreshCookie(request, response, session, rawToken);
        if (isUpgrade(headers)) {
            RelayUpgrade.pending()
                    .set(
                            new RelayState(
                                    backend,
                                    rawRequest(request, forwardTarget),
                                    new SocketFactory() {
                                        @Override
                                        public Socket connect(InetSocketAddress address)
                                                throws IOException {
                                            return GatewayRelayFilter.connect(address);
                                        }
                                    }));
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
                if (name.equals("connection")
                        || name.equals("keep-alive")
                        || name.equals("upgrade")) {
                    continue;
                }
                out.write(
                        (name + ": " + header.getValue() + "\r\n")
                                .getBytes(StandardCharsets.US_ASCII));
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
            boolean chunked = isChunked(head.headers());
            for (Map.Entry<String, List<String>> header : head.headers().entrySet()) {
                // Framing headers belong to the hop that produced the body. Forwarding them would
                // make the
                // container frame bytes that are already framed (double chunking), which corrupts
                // every
                // asset; the container re-frames this response itself.
                if (HOP_BY_HOP.contains(header.getKey())) {
                    continue;
                }
                for (String value : header.getValue()) {
                    response.addHeader(header.getKey(), value);
                }
            }
            if (chunked) {
                relayChunked(upstream.getInputStream(), response.getOutputStream());
            } else {
                GatewayServer.relay(upstream.getInputStream(), response.getOutputStream());
            }
            response.flushBuffer();
        }
    }

    /** Framing and connection headers that must not cross a relay hop. */
    private static final java.util.Set<String> HOP_BY_HOP =
            java.util.Set.of("connection", "keep-alive", "transfer-encoding", "content-length");

    private static boolean isChunked(Map<String, List<String>> headers) {
        for (String value : headers.getOrDefault("transfer-encoding", List.of())) {
            if (value.toLowerCase(java.util.Locale.ROOT).contains("chunked")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copies a chunked backend body to the response as plain bytes, dropping the chunk framing. A
     * zero-length chunk ends the body; chunk extensions after a size are ignored.
     */
    static void relayChunked(InputStream in, OutputStream out) throws IOException {
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) {
                return;
            }
            int semicolon = sizeLine.indexOf(';');
            String digits = (semicolon < 0 ? sizeLine : sizeLine.substring(0, semicolon)).strip();
            if (digits.isEmpty()) {
                continue;
            }
            int size;
            try {
                size = Integer.parseInt(digits, 16);
            } catch (NumberFormatException malformed) {
                throw new GatewayServer.HttpFailure(502, "malformed chunk size");
            }
            if (size == 0) {
                return;
            }
            copy(in, out, size);
            // Each chunk is followed by CRLF, which the size read already consumed up to.
            readLine(in);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int current;
        while ((current = in.read()) >= 0) {
            if (current == '\n') {
                return line.toString();
            }
            if (current != '\r') {
                line.append((char) current);
            }
        }
        return line.length() == 0 ? null : line.toString();
    }

    private static void copy(InputStream in, OutputStream out, long count) throws IOException {
        byte[] buffer = new byte[32768];
        long remaining = count;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new IOException("truncated chunked body");
            }
            out.write(buffer, 0, read);
            remaining -= read;
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
                    GatewayTokens.SESSION_COOKIE
                            + "="
                            + cookie
                            + "; Path=/; HttpOnly; SameSite=Lax");
        }
    }

    static boolean needsRefresh(String rawToken) {
        Optional<Instant> expiry = expiresAt(rawToken);
        if (!expiry.isPresent()) {
            return false;
        }
        return !expiry.get().isAfter(Instant.now().plus(REFRESH_WITHIN));
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
        java.util.Enumeration<String> names = request.getHeaderNames();
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
        java.util.Enumeration<String> names = request.getHeaderNames();
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

    /** Parsed backend response head. */
    static final class BackendHead {
        private final int status;
        private final Map<String, List<String>> headers;

        BackendHead(int status, Map<String, List<String>> headers) {
            this.status = status;
            this.headers = headers;
        }

        int status() {
            return status;
        }

        Map<String, List<String>> headers() {
            return headers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BackendHead)) return false;
            BackendHead other = (BackendHead) o;
            return status == other.status && headers.equals(other.headers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, headers);
        }

        @Override
        public String toString() {
            return "BackendHead[status=" + status + ", headers=" + headers + "]";
        }
    }

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
                String headerName = lines.get(i).substring(0, colon).strip().toLowerCase();
                List<String> values = headers.get(headerName);
                if (values == null) {
                    values = new ArrayList<>();
                    headers.put(headerName, values);
                }
                values.add(lines.get(i).substring(colon + 1).strip());
            }
        }
        return new BackendHead(status, headers);
    }

    /** Carries the backend connection across the container upgrade boundary. */
    static final class RelayState {
        private final InetSocketAddress backend;
        private final String requestHead;
        private final SocketFactory sockets;

        RelayState(InetSocketAddress backend, String requestHead, SocketFactory sockets) {
            this.backend = backend;
            this.requestHead = requestHead;
            this.sockets = sockets;
        }

        InetSocketAddress backend() {
            return backend;
        }

        String requestHead() {
            return requestHead;
        }

        SocketFactory sockets() {
            return sockets;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RelayState)) return false;
            RelayState other = (RelayState) o;
            return backend.equals(other.backend)
                    && requestHead.equals(other.requestHead)
                    && sockets.equals(other.sockets);
        }

        @Override
        public int hashCode() {
            return Objects.hash(backend, requestHead, sockets);
        }
    }

    /** Opens backend sockets; substituted in tests for deterministic failures. */
    abstract static class SocketFactory {
        /**
         * Connects to a backend.
         *
         * @param backend the address
         * @return the connected socket
         * @throws IOException when unreachable
         */
        public abstract Socket connect(InetSocketAddress backend) throws IOException;
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
                upstream.getOutputStream()
                        .write(state.requestHead().getBytes(StandardCharsets.US_ASCII));
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
            Thread.ofVirtual()
                    .start(
                            new Runnable() {
                                @Override
                                public void run() {
                                    relayQuietly(backendIn, clientOut, upstream);
                                }
                            });
            Thread.ofVirtual()
                    .start(
                            new Runnable() {
                                @Override
                                public void run() {
                                    relayQuietly(clientIn, backendOut, upstream);
                                }
                            });
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
