/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.internal;

import dev.jahaziel.debug.api.DebugService;
import dev.jahaziel.debug.api.DebugService.Session;
import dev.jahaziel.debug.api.DebugService.StartRequest;
import dev.jahaziel.workspace.api.WorkspaceDirectoryService;
import dev.jahaziel.workspace.api.WorkspaceProcessLifecycle;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Proxies bounded DAP messages to fixed, server-configured adapters. */
@Service
public final class DebugServiceImpl implements DebugService, WorkspaceProcessLifecycle {
  private static final Logger log = LoggerFactory.getLogger(DebugServiceImpl.class);
  private static final int MAX_SESSIONS = 12;
  private static final int MAX_SESSIONS_PER_WORKSPACE = 3;
  private static final int MAX_DAP_MESSAGE_BYTES = 1024 * 1024;
  private static final int MAX_DAP_HEADER_BYTES = 8192;
  private static final int MAX_REPLAY_BYTES = 1024 * 1024;
  private static final int MAX_RECENT_SESSIONS = 128;
  private static final Set<String> ALLOWED_CLIENT_REQUESTS =
      Set.of(
          "initialize",
          "launch",
          "configurationDone",
          "setBreakpoints",
          "source",
          "threads",
          "stackTrace",
          "scopes",
          "variables",
          "evaluate",
          "continue",
          "pause",
          "next",
          "stepIn",
          "stepOut");

  private final DebugAdapterProperties adapters;
  private final WorkspaceDirectoryService directories;
  private final WorkspaceService workspaces;
  private final WorkspaceProcessRegistry processRegistry;
  private final ObjectMapper json;
  private final Path storageRoot;
  private final ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor();
  private final Map<UUID, AdapterSession> active = new ConcurrentHashMap<>();
  private final Map<UUID, Session> recent = new ConcurrentHashMap<>();
  private final Deque<UUID> recentOrder = new ArrayDeque<>();
  private final java.util.Set<WorkspaceId> stoppingWorkspaces = ConcurrentHashMap.newKeySet();
  private volatile boolean closed;

  public DebugServiceImpl(
      DebugAdapterProperties adapters,
      WorkspaceDirectoryService directories,
      WorkspaceService workspaces,
      WorkspaceProcessRegistry processRegistry,
      ObjectMapper json,
      @Value("${jahaziel.storage.root:data}") String storageRoot) {
    this.adapters = Objects.requireNonNull(adapters, "adapters");
    this.directories = Objects.requireNonNull(directories, "directories");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.processRegistry = Objects.requireNonNull(processRegistry, "processRegistry");
    this.json = Objects.requireNonNull(json, "json");
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  @Override
  public Session start(WorkspaceId workspaceId, StartRequest request) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(request, "request");
    DebugAdapterProperties.Adapter adapter = adapters.adapters().get(request.languageId());
    if (adapter == null) {
      throw new IllegalStateException("debug adapter is not installed for " + request.languageId());
    }
    ensureWorkspaceCanRun(workspaceId);
    synchronized (active) {
      if (closed) throw new IllegalStateException("debug services are shutting down");
      if (stoppingWorkspaces.contains(workspaceId)) {
        throw new IllegalStateException("workspace is stopping");
      }
      if (active.size() >= MAX_SESSIONS
          || active.values().stream().filter(value -> value.workspaceId.equals(workspaceId)).count()
              >= MAX_SESSIONS_PER_WORKSPACE) {
        throw new IllegalStateException("debug session limit reached");
      }
      return launch(workspaceId, request, adapter);
    }
  }

  @Override
  public Session get(WorkspaceId workspaceId, UUID sessionId) {
    AdapterSession live = requireSession(workspaceId, sessionId);
    if (live != null) return live.view();
    Session completed = recent.get(sessionId);
    if (completed == null || !completed.workspaceId().equals(workspaceId.value())) {
      throw new IllegalArgumentException("debug session not found");
    }
    return completed;
  }

  @Override
  public List<Session> list(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    return active.values().stream()
        .filter(session -> session.workspaceId.equals(workspaceId))
        .map(AdapterSession::view)
        .sorted(java.util.Comparator.comparing(Session::startedAt).reversed())
        .toList();
  }

  @Override
  public Session stop(WorkspaceId workspaceId, UUID sessionId) {
    AdapterSession session = requireSession(workspaceId, sessionId);
    if (session != null) session.close("STOPPED", "");
    return get(workspaceId, sessionId);
  }

  @Override
  public AutoCloseable attach(WorkspaceId workspaceId, UUID sessionId, Consumer<String> output) {
    Objects.requireNonNull(output, "output");
    AdapterSession session = requireSession(workspaceId, sessionId);
    if (session == null) throw new IllegalStateException("debug session is no longer running");
    session.attach(output);
    return () -> session.detach(output);
  }

  @Override
  public void send(WorkspaceId workspaceId, UUID sessionId, String message) {
    AdapterSession session = requireSession(workspaceId, sessionId);
    if (session == null) throw new IllegalStateException("debug session is no longer running");
    if (message == null
        || message.getBytes(StandardCharsets.UTF_8).length > MAX_DAP_MESSAGE_BYTES) {
      throw new IllegalArgumentException("DAP message exceeds the supported limit");
    }
    try {
      JsonNode parsed = json.readTree(message);
      if (!(parsed instanceof ObjectNode object)) {
        throw new IllegalArgumentException("DAP message must be a JSON object");
      }
      JsonNode prepared = prepareClientMessage(session, object);
      writeDapMessage(session.output, json.writeValueAsBytes(prepared));
    } catch (JacksonException | IOException failure) {
      throw new IllegalArgumentException("invalid DAP message", failure);
    }
  }

  @Override
  public void closeByWorkspace(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    stoppingWorkspaces.add(workspaceId);
    synchronized (active) {
      active.values().stream()
          .filter(session -> session.workspaceId.equals(workspaceId))
          .toList()
          .forEach(session -> session.close("STOPPED", "workspace stopped"));
    }
  }

  @Override
  public void allowByWorkspace(WorkspaceId workspaceId) {
    if (!closed) stoppingWorkspaces.remove(Objects.requireNonNull(workspaceId, "workspaceId"));
  }

  @Override
  public void deleteWorkspace(WorkspaceId workspaceId) {
    closeByWorkspace(workspaceId);
  }

  @PreDestroy
  public void closeAll() {
    closed = true;
    synchronized (active) {
      active.values().stream()
          .toList()
          .forEach(session -> session.close("STOPPED", "server stopped"));
    }
    readers.shutdownNow();
  }

  private Session launch(
      WorkspaceId workspaceId, StartRequest request, DebugAdapterProperties.Adapter adapter) {
    Path home;
    Path cwd;
    Path program = null;
    try {
      home = directories.home(workspaceId).toRealPath();
      if (!Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("workspace home is unavailable");
      }
      cwd = resolveWorkspacePath(home, request.workingDirectory(), true, true);
      if (request.program() != null) {
        program = resolveWorkspacePath(home, request.program(), true, false);
      }
    } catch (IOException failure) {
      throw new IllegalArgumentException("debug launch path is unavailable", failure);
    }

    UUID id = UUID.randomUUID();
    Path sessionHome = sessionDirectory(id);
    Process process = null;
    WorkspaceProcessRegistry.Registration registration = null;
    Socket socket = null;
    try {
      ensurePlainDirectory(sessionHome.resolve("tmp"));
      int port = adapter.transport() == DebugAdapterProperties.Transport.TCP ? availablePort() : -1;
      ProcessBuilder builder =
          new ProcessBuilder(processRegistry.dedicatedSessionCommand(adapterCommand(adapter, port)))
              .directory(home.toFile());
      configureEnvironment(builder, sessionHome);
      process = builder.start();
      registration =
          processRegistry.track(workspaceId, WorkspaceProcessRegistry.Kind.DEBUG_ADAPTER, process);
      Process adapterProcess = process;
      InputStream input;
      OutputStream output;
      if (adapter.transport() == DebugAdapterProperties.Transport.TCP) {
        socket = connectAdapter(adapterProcess, port);
        input = socket.getInputStream();
        output = socket.getOutputStream();
        readers.submit(() -> discard(adapterProcess.getInputStream()));
        readers.submit(() -> discard(adapterProcess.getErrorStream()));
      } else {
        input = adapterProcess.getInputStream();
        output = adapterProcess.getOutputStream();
        readers.submit(() -> discard(adapterProcess.getErrorStream()));
      }
      AdapterSession session =
          new AdapterSession(
              id,
              workspaceId,
              request,
              adapter.version(),
              home,
              program,
              cwd,
              sessionHome,
              adapterProcess,
              registration,
              socket,
              input,
              output);
      active.put(id, session);
      remember(session.view());
      readers.submit(() -> readOutput(session));
      adapterProcess.onExit().thenRun(() -> processExited(session));
      return session.view();
    } catch (IOException | RuntimeException failure) {
      if (socket != null) closeQuietly(socket);
      if (process != null) stopProcess(process);
      if (registration != null) registration.close();
      deleteSessionDirectory(sessionHome);
      if (failure instanceof IllegalStateException state) throw state;
      throw new IllegalStateException("debug adapter could not be started", failure);
    }
  }

  private void ensureWorkspaceCanRun(WorkspaceId workspaceId) {
    WorkspaceState state = workspaces.get(workspaceId).state();
    if (!(state instanceof WorkspaceState.Ready
        || state instanceof WorkspaceState.Active
        || state instanceof WorkspaceState.Idle)) {
      throw new IllegalStateException("workspace must be running before debugging");
    }
  }

  private AdapterSession requireSession(WorkspaceId workspaceId, UUID sessionId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(sessionId, "sessionId");
    AdapterSession session = active.get(sessionId);
    if (session == null) {
      Session completed = recent.get(sessionId);
      if (completed != null && !completed.workspaceId().equals(workspaceId.value())) {
        throw new IllegalArgumentException("debug session not found");
      }
      return null;
    }
    if (!session.workspaceId.equals(workspaceId)) {
      throw new IllegalArgumentException("debug session not found");
    }
    return session;
  }

  private JsonNode prepareClientMessage(AdapterSession session, ObjectNode message)
      throws IOException {
    JsonNode type = message.get("type");
    if (type == null || !type.isString()) {
      throw new IllegalArgumentException("DAP message type is required");
    }
    if (type.asString().equals("response")) {
      if (!message.path("request_seq").canConvertToInt()) {
        throw new IllegalArgumentException("DAP response request_seq is invalid");
      }
      return message;
    }
    if (!type.asString().equals("request")) {
      throw new IllegalArgumentException(
          "only DAP requests and responses may be sent by the client");
    }
    String command = message.path("command").asString("");
    if (command.isBlank() || command.length() > 128) {
      throw new IllegalArgumentException("DAP request command is invalid");
    }
    if (!ALLOWED_CLIENT_REQUESTS.contains(command))
      throw new IllegalArgumentException("DAP request is not supported by the workbench");
    if (command.equals("launch")) {
      if (session.launchSent)
        throw new IllegalArgumentException("debug launch was already requested");
      message.set("arguments", launchArguments(session));
      session.launchSent = true;
    } else if (command.equals("setBreakpoints") || command.equals("source")) {
      constrainSourcePath(session, message);
    }
    return message;
  }

  private ObjectNode launchArguments(AdapterSession session) {
    ObjectNode args = json.createObjectNode();
    args.put("type", DebugService.DAP_ADAPTER_IDS.get(session.request.languageId()));
    args.put("request", "launch");
    args.put("name", "Jahaziel workspace launch");
    if (session.request.languageId().equals("java")) {
      args.put("mainClass", session.request.mainClass());
    } else {
      args.put("program", session.program.toString());
    }
    args.put("cwd", session.cwd.toString());
    ArrayNode values = args.putArray("args");
    session.request.arguments().forEach(values::add);
    args.put("stopOnEntry", session.request.stopOnEntry());
    if (session.request.languageId().equals("go")) args.put("mode", "debug");
    if (session.request.languageId().equals("node")
        || session.request.languageId().equals("python")) {
      args.put("console", "internalConsole");
    }
    return args;
  }

  private void constrainSourcePath(AdapterSession session, ObjectNode message) throws IOException {
    JsonNode arguments = message.get("arguments");
    if (!(arguments instanceof ObjectNode argumentObject)) {
      throw new IllegalArgumentException("DAP source request arguments are invalid");
    }
    JsonNode source = argumentObject.get("source");
    if (!(source instanceof ObjectNode sourceObject)) {
      throw new IllegalArgumentException("DAP source path is required");
    }
    JsonNode sourcePath = source.get("path");
    if (sourcePath == null || !sourcePath.isString()) return;
    Path confined = resolveWorkspacePath(session.home, sourcePath.asString(), true, false);
    sourceObject.put("path", confined.toString());
  }

  private static Path resolveWorkspacePath(
      Path home, String relative, boolean mustExist, boolean directory) throws IOException {
    Path requested = Path.of(relative.replace('\\', '/'));
    Path candidate =
        requested.isAbsolute() ? requested.normalize() : home.resolve(requested).normalize();
    if (!candidate.startsWith(home))
      throw new IllegalArgumentException("debug path escapes workspace");
    if (mustExist) {
      Path real = candidate.toRealPath();
      if (!real.startsWith(home))
        throw new IllegalArgumentException("debug symlink escapes workspace");
      if (directory ? !Files.isDirectory(real) : !Files.isRegularFile(real)) {
        throw new IllegalArgumentException("debug path has the wrong file type");
      }
      return real;
    }
    return candidate;
  }

  private static List<String> adapterCommand(DebugAdapterProperties.Adapter adapter, int port) {
    return adapter.command().stream()
        .map(value -> value.replace("{port}", Integer.toString(port)))
        .toList();
  }

  private static int availablePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      return socket.getLocalPort();
    }
  }

  private static Socket connectAdapter(Process process, int port) throws IOException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    IOException lastFailure = null;
    while (System.nanoTime() < deadline && process.isAlive()) {
      Socket socket = new Socket();
      try {
        socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 150);
        socket.setTcpNoDelay(true);
        return socket;
      } catch (IOException refused) {
        lastFailure = refused;
        closeQuietly(socket);
        try {
          Thread.sleep(40);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IOException("debug adapter connection was interrupted", interrupted);
        }
      }
    }
    throw new IOException("debug adapter did not open its loopback DAP endpoint", lastFailure);
  }

  private void readOutput(AdapterSession session) {
    try {
      String payload;
      while ((payload = readDapMessage(session.input)) != null) {
        session.publish(normalizeAdapterMessage(session, payload));
      }
      if (!session.closed) session.close("EXITED", "debug adapter closed its DAP stream");
    } catch (IOException failure) {
      if (!session.closed) session.close("FAILED", "debug adapter DAP stream failed");
    }
  }

  private String normalizeAdapterMessage(AdapterSession session, String payload)
      throws IOException {
    JsonNode message;
    try {
      message = json.readTree(payload);
    } catch (JacksonException invalid) {
      throw new IOException("debug adapter returned invalid DAP JSON", invalid);
    }
    if (!(message instanceof ObjectNode)) {
      throw new IOException("debug adapter returned an invalid DAP message");
    }
    JsonNode type = message.get("type");
    if (type != null && type.isString() && type.asString().equals("request")) {
      String command = message.path("command").asString("");
      if (command.equals("runInTerminal") || command.equals("startDebugging")) {
        throw new IOException("debug adapter requested a server-side process");
      }
    }
    normalizeSourcePaths(session.home, message);
    try {
      return json.writeValueAsString(message);
    } catch (JacksonException invalid) {
      throw new IOException("debug adapter returned invalid DAP JSON", invalid);
    }
  }

  private void normalizeSourcePaths(Path home, JsonNode current) throws IOException {
    if (current instanceof ObjectNode object) {
      JsonNode source = object.get("source");
      if (source instanceof ObjectNode sourceObject) {
        JsonNode path = sourceObject.get("path");
        if (path != null && path.isString()) {
          String relative = workspaceRelativeSourcePath(home, path.asString());
          if (relative == null) sourceObject.remove("path");
          else sourceObject.put("path", relative);
        }
      }
      for (JsonNode value : object.values()) normalizeSourcePaths(home, value);
    } else if (current instanceof ArrayNode values) {
      for (JsonNode value : values) normalizeSourcePaths(home, value);
    }
  }

  static String workspaceRelativeSourcePath(Path home, String source) {
    try {
      Path normalizedHome = home.toAbsolutePath().normalize();
      Path requested = Path.of(source.replace('\\', '/'));
      Path candidate =
          requested.isAbsolute()
              ? requested.normalize()
              : normalizedHome.resolve(requested).normalize();
      if (!candidate.startsWith(normalizedHome)) return null;
      if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
        Path real = candidate.toRealPath();
        if (!real.startsWith(normalizedHome)) return null;
        candidate = real;
      }
      return normalizedHome.relativize(candidate).toString().replace('\\', '/');
    } catch (IOException | RuntimeException invalid) {
      return null;
    }
  }

  static String readDapMessage(InputStream input) throws IOException {
    int contentLength = -1;
    int headerBytes = 0;
    boolean foundContentLength = false;
    while (true) {
      String line = readHeaderLine(input, headerBytes);
      if (line == null) {
        if (headerBytes == 0 && contentLength < 0) return null;
        throw new IOException("DAP header ended unexpectedly");
      }
      headerBytes += line.length() + 2;
      if (headerBytes > MAX_DAP_HEADER_BYTES) throw new IOException("DAP header is too large");
      if (line.isEmpty()) break;
      int colon = line.indexOf(':');
      if (colon <= 0) throw new IOException("DAP header is malformed");
      if (line.substring(0, colon).strip().equalsIgnoreCase("Content-Length")) {
        if (foundContentLength) throw new IOException("DAP content length is duplicated");
        foundContentLength = true;
        try {
          contentLength = Integer.parseInt(line.substring(colon + 1).strip());
        } catch (NumberFormatException invalid) {
          throw new IOException("DAP content length is invalid", invalid);
        }
      }
    }
    if (contentLength < 1 || contentLength > MAX_DAP_MESSAGE_BYTES) {
      throw new IOException("DAP content length is outside the supported limit");
    }
    byte[] body = input.readNBytes(contentLength);
    if (body.length != contentLength) throw new IOException("DAP body ended unexpectedly");
    return new String(body, StandardCharsets.UTF_8);
  }

  private static String readHeaderLine(InputStream input, int consumed) throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    while (consumed + line.size() <= MAX_DAP_HEADER_BYTES) {
      int value = input.read();
      if (value < 0) return line.size() == 0 ? null : throwIncompleteHeader();
      if (value == '\n') {
        byte[] bytes = line.toByteArray();
        int length =
            bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
      }
      line.write(value);
    }
    throw new IOException("DAP header line is too large");
  }

  private static String throwIncompleteHeader() throws IOException {
    throw new IOException("DAP header ended unexpectedly");
  }

  private static void writeDapMessage(OutputStream output, byte[] payload) throws IOException {
    output.write(
        ("Content-Length: " + payload.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    output.write(payload);
    output.flush();
  }

  private void discard(InputStream input) {
    try (input) {
      input.transferTo(OutputStream.nullOutputStream());
    } catch (IOException ignored) {
      // Adapter diagnostics are never copied into browser-visible DAP messages.
    }
  }

  private void processExited(AdapterSession session) {
    if (!session.closed) session.close("EXITED", "debug adapter process exited");
  }

  private Path sessionDirectory(UUID id) {
    Path root = storageRoot.resolve("debug").resolve("sessions").normalize();
    Path session = root.resolve(id.toString()).normalize();
    if (!session.startsWith(root)) throw new IllegalArgumentException("invalid debug session path");
    return session;
  }

  private static void ensurePlainDirectory(Path path) throws IOException {
    Path absolute = path.toAbsolutePath().normalize();
    Path current = absolute.getRoot();
    if (current == null) throw new IOException("debug state path must be absolute");
    for (Path component : absolute) {
      current = current.resolve(component);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(current)
            || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
          throw new IOException("debug state path is unsafe");
        }
      } else {
        Files.createDirectory(current);
      }
    }
  }

  private void configureEnvironment(ProcessBuilder builder, Path home) {
    Map<String, String> environment = builder.environment();
    environment.clear();
    environment.put("HOME", home.toString());
    environment.put("TMPDIR", home.resolve("tmp").toString());
    environment.put("LANG", "C.UTF-8");
    String path = System.getenv("PATH");
    if (path != null && !path.isBlank()) environment.put("PATH", path);
    for (String key : List.of("JAVA_HOME", "SYSTEMROOT", "WINDIR")) {
      String value = System.getenv(key);
      if (value != null && !value.isBlank()) environment.put(key, value);
    }
  }

  private void remember(Session status) {
    recent.put(status.id(), status);
    synchronized (recentOrder) {
      recentOrder.remove(status.id());
      recentOrder.addLast(status.id());
      while (recentOrder.size() > MAX_RECENT_SESSIONS) {
        UUID expired = recentOrder.removeFirst();
        recent.remove(expired);
      }
    }
  }

  private void deleteSessionDirectory(Path path) {
    Path sessionsRoot =
        storageRoot.resolve("debug").resolve("sessions").toAbsolutePath().normalize();
    Path absolute = path.toAbsolutePath().normalize();
    if (!absolute.startsWith(sessionsRoot) || Files.isSymbolicLink(absolute)) return;
    try {
      Files.walkFileTree(
          absolute,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Files.deleteIfExists(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                throws IOException {
              if (failure != null) throw failure;
              Files.deleteIfExists(directory);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException failure) {
      log.warn("could not remove generated debugger session directory id={}", path.getFileName());
    }
  }

  private static void stopProcess(Process process) {
    process.destroy();
    try {
      if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }

  private static void closeQuietly(AutoCloseable closeable) {
    if (closeable == null) return;
    try {
      closeable.close();
    } catch (Exception ignored) {
      // Best-effort cleanup of adapter channels.
    }
  }

  private final class AdapterSession {
    private final UUID id;
    private final WorkspaceId workspaceId;
    private final StartRequest request;
    private final String adapterVersion;
    private final java.time.Instant startedAt = java.time.Instant.now();
    private final Path home;
    private final Path program;
    private final Path cwd;
    private final Path sessionHome;
    private final Process process;
    private final WorkspaceProcessRegistry.Registration registration;
    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final Deque<String> replay = new ArrayDeque<>();
    private int replayBytes;
    private volatile String state = "RUNNING";
    private volatile String error = "";
    private volatile boolean launchSent;
    private volatile boolean closed;

    private AdapterSession(
        UUID id,
        WorkspaceId workspaceId,
        StartRequest request,
        String adapterVersion,
        Path home,
        Path program,
        Path cwd,
        Path sessionHome,
        Process process,
        WorkspaceProcessRegistry.Registration registration,
        Socket socket,
        InputStream input,
        OutputStream output) {
      this.id = id;
      this.workspaceId = workspaceId;
      this.request = request;
      this.adapterVersion = adapterVersion;
      this.home = home;
      this.program = program;
      this.cwd = cwd;
      this.sessionHome = sessionHome;
      this.process = process;
      this.registration = registration;
      this.socket = socket;
      this.input = input;
      this.output = output;
    }

    private Session view() {
      return new Session(
          id,
          workspaceId.value(),
          request.languageId(),
          DebugService.DAP_ADAPTER_IDS.get(request.languageId()),
          state,
          adapterVersion,
          startedAt,
          error);
    }

    private void attach(Consumer<String> listener) {
      List<String> pending;
      synchronized (replay) {
        if (closed) throw new IllegalStateException("debug session is no longer running");
        listeners.add(listener);
        pending = new ArrayList<>(replay);
      }
      pending.forEach(listener);
    }

    private void detach(Consumer<String> listener) {
      listeners.remove(listener);
    }

    private void publish(String payload) {
      List<Consumer<String>> targets;
      synchronized (replay) {
        replay.addLast(payload);
        replayBytes += payload.getBytes(StandardCharsets.UTF_8).length;
        while (replayBytes > MAX_REPLAY_BYTES && !replay.isEmpty()) {
          replayBytes -= replay.removeFirst().getBytes(StandardCharsets.UTF_8).length;
        }
        targets = List.copyOf(listeners);
      }
      targets.forEach(
          listener -> {
            try {
              listener.accept(payload);
            } catch (RuntimeException disconnect) {
              listeners.remove(listener);
            }
          });
    }

    private synchronized void close(String finalState, String safeError) {
      if (closed) return;
      closed = true;
      state = finalState;
      error = safeError;
      stopProcess(process);
      registration.close();
      closeQuietly(socket);
      closeQuietly(input);
      closeQuietly(output);
      deleteSessionDirectory(sessionHome);
      active.remove(id, this);
      remember(view());
      listeners.clear();
    }
  }
}
