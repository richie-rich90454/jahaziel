/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.domain.WorkspaceId;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Persists process identities and cleans up only processes that still match their recorded
 * identity.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class WorkspaceProcessRegistryImpl
    implements WorkspaceProcessRegistry, ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(WorkspaceProcessRegistryImpl.class);
  private static final Duration GRACEFUL_STOP = Duration.ofMillis(750);
  private static final Duration FORCE_STOP = Duration.ofMillis(750);
  private static final int MAX_MARKERS = 4096;

  private final Path storageRoot;
  private final ConcurrentMap<UUID, LiveRegistration> live = new ConcurrentHashMap<>();

  public WorkspaceProcessRegistryImpl(String storageRoot) {
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  @Override
  public Registration track(WorkspaceId workspaceId, Kind kind, Process process) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(process, "process");
    ProcessHandle handle = process.toHandle();
    if (!handle.isAlive()) return new CompletedRegistration();
    ProcessIdentity identity;
    try {
      identity = identity(handle);
    } catch (IllegalStateException failure) {
      if (!handle.isAlive()) return new CompletedRegistration();
      throw failure;
    }
    if (!handle.isAlive()) return new CompletedRegistration();
    UUID id = UUID.randomUUID();
    Path marker = markerPath(id);
    try {
      writeMarker(marker, new Marker(id, workspaceId.value(), kind, identity));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot persist workspace process identity", failure);
    }
    LiveRegistration registration = new LiveRegistration(id, marker, handle, identity);
    live.put(id, registration);
    if (!handle.isAlive()) {
      registration.close();
      throw new IllegalStateException("workspace process exited before it could be tracked");
    }
    return registration;
  }

  @Override
  public List<String> dedicatedSessionCommand(List<String> command) {
    Objects.requireNonNull(command, "command");
    if (command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException("managed process command must not be empty");
    }
    if (Platforms.detect(null).os() != Platform.Os.LINUX) {
      return List.copyOf(command);
    }
    Path setsid =
        List.of(Path.of("/usr/bin/setsid"), Path.of("/bin/setsid")).stream()
            .filter(Files::isExecutable)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "workspace processes require the Linux setsid utility"));
    List<String> isolated = new ArrayList<>(command.size() + 2);
    isolated.add(setsid.toString());
    isolated.add("--");
    isolated.addAll(command);
    return List.copyOf(isolated);
  }

  @Override
  public void run(ApplicationArguments arguments) {
    Path root;
    try {
      root = registryDirectory(false);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot read workspace process registry", failure);
    }
    if (root == null) return;
    try (var paths = Files.list(root)) {
      List<Path> markers =
          paths
              .filter(path -> path.getFileName().toString().endsWith(".process"))
              .limit(MAX_MARKERS + 1L)
              .toList();
      if (markers.size() > MAX_MARKERS) {
        throw new IllegalStateException("workspace process registry exceeds its safety limit");
      }
      for (Path path : markers) recover(path);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot scan workspace process registry", failure);
    }
  }

  private void recover(Path markerPath) {
    if (Files.isSymbolicLink(markerPath)
        || !Files.isRegularFile(markerPath, LinkOption.NOFOLLOW_LINKS)) {
      log.warn("skipping non-regular workspace process marker {}", markerPath.getFileName());
      return;
    }
    Marker marker;
    try {
      marker = readMarker(markerPath);
    } catch (IOException | RuntimeException invalid) {
      log.warn(
          "cannot validate workspace process marker {}; leaving it for inspection",
          markerPath.getFileName());
      return;
    }
    ProcessHandle handle = ProcessHandle.of(marker.identity().pid()).orElse(null);
    if (handle == null) {
      if (marker.identity().sessionId() > 0) {
        if (!terminateSession(marker.identity().sessionId())) {
          log.warn("workspace process session {} did not stop; marker retained", marker.id());
          return;
        }
      }
      deleteMarker(markerPath);
      log.info("discarded stale workspace process marker {}", marker.id());
      return;
    }
    if (!matches(handle, marker.identity())) {
      deleteMarker(markerPath);
      log.info("discarded mismatched workspace process marker {}", marker.id());
      return;
    }
    log.info(
        "stopping interrupted {} process for workspace {}", marker.kind(), marker.workspaceId());
    boolean stopped = terminate(handle, marker.identity().sessionId());
    if (!stopped) {
      log.warn("workspace process {} did not stop; marker retained for a later retry", marker.id());
    } else {
      deleteMarker(markerPath);
    }
  }

  private ProcessIdentity identity(ProcessHandle handle) {
    ProcessHandle.Info info = handle.info();
    Instant started =
        info.startInstant()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "host does not expose process start times; refusing to track an unverifiable process"));
    String command =
        info.command()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "host does not expose process executable paths; refusing to track an unverifiable process"));
    if (!handle.isAlive()) {
      throw new IllegalStateException("workspace process has already exited");
    }
    long sessionId = awaitOwnedSession(handle);
    if (isLinux() && sessionId == 0 && handle.isAlive()) {
      throw new IllegalStateException(
          "workspace process did not enter its dedicated Linux session");
    }
    return new ProcessIdentity(handle.pid(), started, command, sessionId);
  }

  private static boolean matches(ProcessHandle handle, ProcessIdentity expected) {
    if (!handle.isAlive() || handle.pid() != expected.pid()) return false;
    ProcessHandle.Info info = handle.info();
    Instant started = info.startInstant().orElse(null);
    if (started == null || !started.equals(expected.startedAt())) return false;
    if (expected.sessionId() > 0) {
      return isLinux()
          && expected.sessionId() == handle.pid()
          && ownLinuxSession(handle.pid()) == expected.sessionId();
    }
    String command = info.command().orElse(null);
    return command != null && command.equals(expected.command());
  }

  private Path markerPath(UUID id) {
    try {
      return registryDirectory(true).resolve(id + ".process");
    } catch (IOException failure) {
      throw new UncheckedIOException("workspace process registry is unavailable", failure);
    }
  }

  private Path registryDirectory(boolean create) throws IOException {
    if (create) Files.createDirectories(storageRoot);
    if (!Files.isDirectory(storageRoot, LinkOption.NOFOLLOW_LINKS)) {
      if (!create && !Files.exists(storageRoot, LinkOption.NOFOLLOW_LINKS)) return null;
      throw new IOException("configured storage root is not a directory");
    }
    Path realRoot = storageRoot.toRealPath();
    Path directory = realRoot.resolve("managed-processes");
    if (create) Files.createDirectories(directory);
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return null;
    if (Files.isSymbolicLink(directory)
        || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("workspace process registry directory is not a plain directory");
    }
    restrictPermissions(directory, true);
    Path realDirectory = directory.toRealPath();
    if (!realDirectory.startsWith(realRoot)) {
      throw new IOException("workspace process registry escapes the configured storage root");
    }
    return realDirectory;
  }

  private void writeMarker(Path destination, Marker marker) throws IOException {
    Path root = registryDirectory(true);
    if (!destination.getParent().equals(root)) {
      throw new IOException("workspace process marker path is outside its registry");
    }
    Path temporary = root.resolve(marker.id() + ".tmp");
    String contents =
        "version=2\n"
            + "id="
            + marker.id()
            + "\n"
            + "workspace="
            + marker.workspaceId()
            + "\n"
            + "kind="
            + marker.kind().name()
            + "\n"
            + "pid="
            + marker.identity().pid()
            + "\n"
            + "started="
            + marker.identity().startedAt()
            + "\n"
            + "session="
            + marker.identity().sessionId()
            + "\n"
            + "command="
            + Base64.getEncoder()
                .encodeToString(marker.identity().command().getBytes(StandardCharsets.UTF_8))
            + "\n";
    Files.writeString(
        temporary,
        contents,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    restrictPermissions(temporary, false);
    try {
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException unsupported) {
      Files.move(temporary, destination);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static Marker readMarker(Path path) throws IOException {
    if (Files.size(path) > 16 * 1024) throw new IOException("process marker is too large");
    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    Map<String, String> values = new HashMap<>();
    for (String line : lines) {
      int separator = line.indexOf('=');
      if (separator <= 0
          || values.putIfAbsent(line.substring(0, separator), line.substring(separator + 1))
              != null) {
        throw new IOException("invalid process marker fields");
      }
    }
    String version = values.get("version");
    if (!"1".equals(version) && !"2".equals(version))
      throw new IOException("unsupported process marker version");
    UUID id = UUID.fromString(required(values, "id"));
    UUID workspace = UUID.fromString(required(values, "workspace"));
    Kind kind = Kind.valueOf(required(values, "kind"));
    long pid = positiveLong(values, "pid");
    Instant started = parseStartTime(required(values, "started"), version);
    long sessionId = "2".equals(version) ? nonNegativeLong(values, "session") : 0;
    String command =
        new String(Base64.getDecoder().decode(required(values, "command")), StandardCharsets.UTF_8);
    if (!path.getFileName().toString().equals(id + ".process") || command.isBlank()) {
      throw new IOException("process marker identity is invalid");
    }
    return new Marker(id, workspace, kind, new ProcessIdentity(pid, started, command, sessionId));
  }

  private static String required(Map<String, String> values, String key) throws IOException {
    String value = values.get(key);
    if (value == null || value.isBlank()) throw new IOException("process marker field is missing");
    return value;
  }

  private static long positiveLong(Map<String, String> values, String key) throws IOException {
    try {
      long value = Long.parseLong(required(values, key));
      if (value <= 0) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException invalid) {
      throw new IOException("process marker numeric field is invalid", invalid);
    }
  }

  private static long nonNegativeLong(Map<String, String> values, String key) throws IOException {
    try {
      long value = Long.parseLong(required(values, key));
      if (value < 0) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException invalid) {
      throw new IOException("process marker numeric field is invalid", invalid);
    }
  }

  private static Instant parseStartTime(String value, String version) throws IOException {
    try {
      return "1".equals(version)
          ? Instant.ofEpochMilli(Long.parseLong(value))
          : Instant.parse(value);
    } catch (RuntimeException invalid) {
      throw new IOException("process marker start time is invalid", invalid);
    }
  }

  private static void restrictPermissions(Path path, boolean directory) {
    try {
      Set<PosixFilePermission> permissions =
          directory
              ? Set.of(
                  PosixFilePermission.OWNER_READ,
                  PosixFilePermission.OWNER_WRITE,
                  PosixFilePermission.OWNER_EXECUTE)
              : Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(path, permissions);
    } catch (UnsupportedOperationException | IOException ignored) {
      // Windows ACLs are inherited from the configured data directory.
    }
  }

  private static boolean terminate(ProcessHandle handle, long sessionId) {
    Map<Long, ProcessHandle> processes = new LinkedHashMap<>();
    handle.descendants().forEach(process -> processes.put(process.pid(), process));
    if (sessionId > 0 && sessionId == handle.pid() && ownLinuxSession(handle.pid()) == sessionId) {
      processesInSession(sessionId).forEach(process -> processes.put(process.pid(), process));
    }
    processes.remove(handle.pid());
    List<ProcessHandle> members = List.copyOf(processes.values());
    members.forEach(ProcessHandle::destroy);
    handle.destroy();
    List<ProcessHandle> all = new ArrayList<>(members);
    all.add(handle);
    awaitExit(all, GRACEFUL_STOP);
    members.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    if (handle.isAlive()) handle.destroyForcibly();
    awaitExit(all, FORCE_STOP);
    return all.stream().noneMatch(ProcessHandle::isAlive);
  }

  private static void awaitExit(List<ProcessHandle> processes, Duration duration) {
    long deadline = System.nanoTime() + duration.toNanos();
    while (processes.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
      LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
    }
  }

  private static long ownLinuxSession(long pid) {
    LinuxProcessIdentity identity = linuxProcessIdentity(pid);
    return identity != null && identity.sessionId() == pid ? pid : 0;
  }

  private static long awaitOwnedSession(ProcessHandle handle) {
    if (!isLinux()) return 0;
    long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (handle.isAlive() && System.nanoTime() < deadline) {
      if (ownLinuxSession(handle.pid()) == handle.pid()) return handle.pid();
      LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
    }
    return 0;
  }

  private static boolean isLinux() {
    return Platforms.detect(null).os() == Platform.Os.LINUX;
  }

  private static List<ProcessHandle> processesInSession(long sessionId) {
    Path proc = Path.of("/proc");
    if (!Files.isDirectory(proc)) return List.of();
    List<ProcessHandle> result = new ArrayList<>();
    try (var entries = Files.list(proc)) {
      for (Path entry : entries.toList()) {
        String name = entry.getFileName().toString();
        if (!name.chars().allMatch(Character::isDigit)) continue;
        long pid;
        try {
          pid = Long.parseLong(name);
        } catch (NumberFormatException invalid) {
          continue;
        }
        LinuxProcessIdentity identity = linuxProcessIdentity(pid);
        if (identity != null && identity.sessionId() == sessionId) {
          ProcessHandle.of(pid).filter(ProcessHandle::isAlive).ifPresent(result::add);
        }
      }
    } catch (IOException failure) {
      log.warn("could not enumerate Linux process session {} during cleanup", sessionId);
    }
    return result;
  }

  private static boolean terminateSession(long sessionId) {
    if (!isLinux() || sessionId <= 0) return true;
    List<ProcessHandle> session = processesInSession(sessionId);
    session.forEach(ProcessHandle::destroy);
    awaitExit(session, GRACEFUL_STOP);
    session.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    awaitExit(session, FORCE_STOP);
    return session.stream().noneMatch(ProcessHandle::isAlive);
  }

  private static LinuxProcessIdentity linuxProcessIdentity(long pid) {
    Path stat = Path.of("/proc", Long.toString(pid), "stat");
    try {
      String value = Files.readString(stat, StandardCharsets.US_ASCII);
      int commandEnd = value.lastIndexOf(')');
      if (commandEnd < 0) return null;
      String[] fields = value.substring(commandEnd + 1).strip().split("\\s+");
      if (fields.length < 4) return null;
      return new LinuxProcessIdentity(Long.parseLong(fields[3]));
    } catch (IOException | NumberFormatException ignored) {
      return null;
    }
  }

  private static void deleteMarker(Path marker) {
    try {
      if (!Files.isSymbolicLink(marker)) Files.deleteIfExists(marker);
    } catch (IOException failure) {
      log.warn("could not remove workspace process marker {}", marker.getFileName());
    }
  }

  @PreDestroy
  public void closeAll() {
    live.values().forEach(LiveRegistration::stop);
  }

  private record ProcessIdentity(long pid, Instant startedAt, String command, long sessionId) {}

  private record LinuxProcessIdentity(long sessionId) {}

  private static final class CompletedRegistration implements Registration {
    private final UUID id = UUID.randomUUID();

    @Override
    public UUID id() {
      return id;
    }

    @Override
    public void close() {}
  }

  private record Marker(UUID id, UUID workspaceId, Kind kind, ProcessIdentity identity) {}

  private final class LiveRegistration implements Registration {
    private final UUID id;
    private final Path marker;
    private final ProcessHandle process;
    private final ProcessIdentity identity;
    private final AtomicBoolean closed = new AtomicBoolean();

    private LiveRegistration(
        UUID id, Path marker, ProcessHandle process, ProcessIdentity identity) {
      this.id = id;
      this.marker = marker;
      this.process = process;
      this.identity = identity;
    }

    @Override
    public UUID id() {
      return id;
    }

    @Override
    public void close() {
      stop();
    }

    private void stop() {
      if (!closed.compareAndSet(false, true)) return;
      live.remove(id, this);
      ProcessHandle current = ProcessHandle.of(identity.pid()).orElse(null);
      boolean stopped;
      if (current != null && matches(current, identity)) {
        stopped = terminate(current, identity.sessionId());
      } else if (current == null && identity.sessionId() > 0) {
        stopped = terminateSession(identity.sessionId());
      } else {
        stopped = true;
      }
      if (stopped) deleteMarker(marker);
      else log.warn("workspace process {} did not stop; marker retained", id);
    }
  }
}
