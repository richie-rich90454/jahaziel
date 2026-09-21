/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.sync;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import tools.jackson.databind.ObjectMapper;

/**
 * File-sync server for workspace homes. Guests reach it over the vsock relay as plain TCP on
 * loopback; the relay is the only listener beside this socket, so every connection is either the
 * local node agent tooling or a workspace VM. Authentication is per workspace: the node agent mints
 * a secret at prepare, hands it to the guest on the kernel command line, and the guest presents it
 * in the hello frame.
 *
 * <p>Both directions mirror: files absent from the incoming tree are deleted, so renames and
 * deletions propagate. Pushes are capped at the workspace quota on uncompressed bytes and unpacked
 * atomically per file through a sibling temp file plus an atomic move, so a quota abort never
 * leaves a truncated file behind. Payloads stage through temp files, never RAM. The wire length
 * prefix itself is capped independently of quota, so an empty tree always converges.
 *
 * <p>One workspace transfers at a time: pull and push take a per-workspace lock, so a timer push
 * racing a stop push (or a pull) serializes instead of interleaving on the live home.
 *
 * <p>Durability contract: the host home is the truth at rest. Guests pull at boot, push every
 * minute and at graceful stop. A snapshot therefore lags the live guest by at most the push
 * interval; a killed VM loses at most the same window.
 */
public final class SyncServer {
  static final int PROTOCOL_VERSION = 1;
  static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;
  static final long MAX_WIRE_BYTES = 32L << 30;

  /** Loopback TCP port; guests arrive through the vsock relay. */
  public static final int LOOPBACK_PORT = 19002;

  private final int port;
  private final Path stateDir;
  private final Path registryFile;
  private final Path stagingDir;
  private final ObjectMapper mapper = new ObjectMapper();
  private final ConcurrentMap<UUID, Entry> entries = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Object> locks = new ConcurrentHashMap<>();
  private volatile ServerSocket serverSocket;

  /** A registered workspace home. */
  record Entry(Path home, long quotaBytes, String secret) {}

  /** An authenticated session: the workspace id plus the entry it presented. */
  private record Auth(UUID id, Entry entry) {}

  /**
   * Creates the server, loading the persisted registry when present.
   *
   * @param port the loopback port, 0 for an ephemeral test port
   * @param stateDir the registry and staging directory
   */
  public SyncServer(int port, Path stateDir) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port out of range: " + port);
    }
    this.port = port;
    this.stateDir = Objects.requireNonNull(stateDir, "stateDir");
    this.registryFile = stateDir.resolve("registry.properties");
    this.stagingDir = stateDir.resolve("staging");
    load();
  }

  /**
   * Registers a workspace home, minting its secret.
   *
   * @param workspaceId the workspace
   * @param home the home folder
   * @param quotaBytes the sync cap in bytes
   */
  public void register(UUID workspaceId, Path home, long quotaBytes) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(home, "home");
    if (quotaBytes < 0) {
      throw new IllegalArgumentException("quotaBytes must not be negative");
    }
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    entries.put(
        workspaceId,
        new Entry(home.toAbsolutePath().normalize(), quotaBytes, HexFormat.of().formatHex(secret)));
    persist();
  }

  /**
   * Drops a workspace registration.
   *
   * @param workspaceId the workspace
   */
  public void deregister(UUID workspaceId) {
    entries.remove(Objects.requireNonNull(workspaceId, "workspaceId"));
    persist();
  }

  /**
   * Returns the secret for a workspace.
   *
   * @param workspaceId the workspace
   * @return the hex secret
   */
  public String secretFor(UUID workspaceId) {
    Entry entry = entries.get(Objects.requireNonNull(workspaceId, "workspaceId"));
    if (entry == null) {
      throw new IllegalArgumentException("workspace not registered: " + workspaceId);
    }
    return entry.secret();
  }

  /**
   * Binds loopback and serves on virtual threads.
   *
   * @throws IOException when the socket cannot bind
   */
  public synchronized void start() throws IOException {
    if (serverSocket != null) {
      throw new IllegalStateException("server already started");
    }
    sweepStaging();
    ServerSocket socket = new ServerSocket();
    socket.bind(new InetSocketAddress("127.0.0.1", port));
    serverSocket = socket;
    Thread.ofVirtual().name("sync-accept").start(this::acceptLoop);
  }

  /**
   * Returns the bound port.
   *
   * @return the local port
   */
  public int boundPort() {
    ServerSocket socket = serverSocket;
    if (socket == null) {
      throw new IllegalStateException("server not started");
    }
    return socket.getLocalPort();
  }

  /**
   * Stops accepting connections.
   *
   * @throws IOException when the socket cannot close
   */
  public void stop() throws IOException {
    ServerSocket socket = serverSocket;
    serverSocket = null;
    if (socket != null) {
      socket.close();
    }
  }

  private void sweepStaging() throws IOException {
    Files.createDirectories(stagingDir);
    try (Stream<Path> orphans = Files.list(stagingDir)) {
      for (Path orphan :
          orphans
              .filter(path -> !Files.isDirectory(path))
              .filter(
                  path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith("push-") || name.startsWith("pull-");
                  })
              .toList()) {
        Files.deleteIfExists(orphan);
      }
    }
  }

  private void acceptLoop() {
    while (true) {
      ServerSocket socket = serverSocket;
      if (socket == null || socket.isClosed()) {
        return;
      }
      try {
        Socket client = socket.accept();
        Thread.ofVirtual().name("sync-conn").start(() -> handleQuietly(client));
      } catch (IOException closed) {
        return;
      }
    }
  }

  private void handleQuietly(Socket client) {
    try (client) {
      handle(client);
    } catch (IOException | RuntimeException failure) {
      // Per-connection failure; the guest retries on its schedule.
    }
  }

  private void handle(Socket client) throws IOException {
    client.setSoTimeout(30_000);
    DataInputStream in = new DataInputStream(new BufferedInputStream(client.getInputStream()));
    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()));
    Map<String, Object> hello = readFrame(in);
    Auth auth = authenticate(hello);
    if (auth == null) {
      writeFrame(out, Map.of("ok", false, "error", "denied"));
      out.flush();
      return;
    }
    writeFrame(out, Map.of("ok", true));
    out.flush();
    while (true) {
      Map<String, Object> command;
      try {
        command = readFrame(in);
      } catch (EOFException closed) {
        return;
      }
      String op = Objects.toString(command.get("op"), "");
      try {
        switch (op) {
          case "pull" -> pull(auth, in, out);
          case "push" -> push(auth, in, out);
          case "bye" -> {
            writeFrame(out, Map.of("ok", true));
            out.flush();
            return;
          }
          default -> {
            writeFrame(out, Map.of("ok", false, "error", "unknown op"));
            out.flush();
            return;
          }
        }
      } catch (SyncException denied) {
        writeFrame(out, Map.of("ok", false, "error", denied.getMessage()));
        out.flush();
        return;
      }
    }
  }

  private Auth authenticate(Map<String, Object> hello) {
    if (hello == null || !Integer.valueOf(PROTOCOL_VERSION).equals(number(hello.get("v")))) {
      return null;
    }
    UUID workspaceId;
    try {
      workspaceId = UUID.fromString(Objects.toString(hello.get("workspace"), ""));
    } catch (IllegalArgumentException bad) {
      return null;
    }
    Entry entry = entries.get(workspaceId);
    if (entry == null) {
      return null;
    }
    byte[] presented = Objects.toString(hello.get("secret"), "").getBytes(StandardCharsets.UTF_8);
    byte[] expected = entry.secret().getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(presented, expected) ? new Auth(workspaceId, entry) : null;
  }

  private static Integer number(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }

  private void pull(Auth auth, DataInputStream in, DataOutputStream out) throws IOException {
    synchronized (lockFor(auth.id())) {
      writeFrame(out, Map.of("ready", true));
      out.flush();
      Path payload = zipTree(auth.entry().home(), auth.entry().quotaBytes());
      try {
        long size = Files.size(payload);
        out.writeLong(size);
        try (InputStream staged = Files.newInputStream(payload)) {
          staged.transferTo(out);
        }
        writeFrame(out, Map.of("ok", true, "files", countEntries(payload)));
        out.flush();
      } finally {
        Files.deleteIfExists(payload);
      }
    }
  }

  private void push(Auth auth, DataInputStream in, DataOutputStream out) throws IOException {
    synchronized (lockFor(auth.id())) {
      writeFrame(out, Map.of("ready", true));
      out.flush();
      Path payload = stageBlob(in);
      try {
        int files = unzipTree(auth, payload);
        writeFrame(out, Map.of("ok", true, "files", files));
        out.flush();
      } finally {
        Files.deleteIfExists(payload);
      }
    }
  }

  private Object lockFor(UUID workspaceId) {
    return locks.computeIfAbsent(workspaceId, id -> new Object());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readFrame(DataInputStream in) throws IOException {
    int size = in.readInt();
    if (size < 0 || size > MAX_FRAME_BYTES) {
      throw new SyncException("oversize frame: " + size);
    }
    byte[] body = new byte[size];
    in.readFully(body);
    try {
      return mapper.readValue(body, Map.class);
    } catch (tools.jackson.core.JacksonException bad) {
      throw new SyncException("bad frame", bad);
    }
  }

  private void writeFrame(DataOutputStream out, Map<String, Object> message) throws IOException {
    byte[] body = mapper.writeValueAsBytes(message);
    out.writeInt(body.length);
    out.write(body);
  }

  private Path stageBlob(DataInputStream in) throws IOException {
    long size = in.readLong();
    if (size < 0 || size > MAX_WIRE_BYTES) {
      throw new SyncException("payload over wire cap: " + size);
    }
    Files.createDirectories(stagingDir);
    Path staged = Files.createTempFile(stagingDir, "push-", ".zip");
    try (OutputStream file = Files.newOutputStream(staged, StandardOpenOption.WRITE)) {
      byte[] buffer = new byte[32768];
      long remaining = size;
      while (remaining > 0) {
        int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
        if (read < 0) {
          throw new EOFException("truncated payload");
        }
        file.write(buffer, 0, read);
        remaining -= read;
      }
    } catch (IOException | RuntimeException failure) {
      Files.deleteIfExists(staged);
      throw failure;
    }
    return staged;
  }

  private Path zipTree(Path home, long quotaBytes) throws IOException {
    Files.createDirectories(stagingDir);
    Path staged = Files.createTempFile(stagingDir, "pull-", ".zip");
    long bytes = 0;
    try (OutputStream file = Files.newOutputStream(staged, StandardOpenOption.WRITE);
        ZipOutputStream zip = new ZipOutputStream(file)) {
      List<Path> files = regularFiles(home);
      for (Path path : files) {
        String name = posix(home.relativize(path));
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream content = Files.newInputStream(path)) {
          byte[] buffer = new byte[32768];
          int read;
          while ((read = content.read(buffer)) >= 0) {
            zip.write(buffer, 0, read);
            bytes += read;
            if (bytes > quotaBytes) {
              throw new SyncException("tree over quota: " + home);
            }
          }
        }
        zip.closeEntry();
      }
    } catch (IOException | RuntimeException failure) {
      Files.deleteIfExists(staged);
      throw failure;
    }
    return staged;
  }

  private int unzipTree(Auth auth, Path payload) throws IOException {
    Path home = auth.entry().home();
    long quotaBytes = auth.entry().quotaBytes();
    Files.createDirectories(home);
    Path realHome = home.toRealPath();
    Set<String> kept = new HashSet<>();
    long bytes = 0;
    try (InputStream file = Files.newInputStream(payload);
        ZipInputStream zip = new ZipInputStream(file)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        Path target = resolve(home, entry.getName());
        if (target == null) {
          continue;
        }
        if (entry.isDirectory()) {
          Files.createDirectories(target);
          continue;
        }
        Files.createDirectories(target.getParent());
        if (!target.getParent().toRealPath().startsWith(realHome)) {
          continue;
        }
        Path staged =
            Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try (OutputStream content = Files.newOutputStream(staged, StandardOpenOption.WRITE)) {
          byte[] buffer = new byte[32768];
          int read;
          while ((read = zip.read(buffer)) >= 0) {
            content.write(buffer, 0, read);
            bytes += read;
            if (bytes > quotaBytes) {
              throw new SyncException("payload over quota: " + home);
            }
          }
        } catch (IOException | RuntimeException failure) {
          Files.deleteIfExists(staged);
          throw failure;
        }
        try {
          moveAtomically(staged, target);
        } catch (IOException | RuntimeException failure) {
          Files.deleteIfExists(staged);
          throw failure;
        }
        kept.add(posix(home.relativize(target)));
        zip.closeEntry();
      }
    }
    if (entries.get(auth.id()) != auth.entry()) {
      throw new SyncException("stale session");
    }
    mirror(home, kept);
    return kept.size();
  }

  private static void moveAtomically(Path staged, Path target) throws IOException {
    try {
      Files.move(
          staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException fallback) {
      Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private int countEntries(Path payload) throws IOException {
    int count = 0;
    try (InputStream file = Files.newInputStream(payload);
        ZipInputStream zip = new ZipInputStream(file)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          count++;
        }
        zip.closeEntry();
      }
    }
    return count;
  }

  private void mirror(Path home, Set<String> kept) throws IOException {
    for (Path path : regularFiles(home)) {
      if (!kept.contains(posix(home.relativize(path)))) {
        Files.deleteIfExists(path);
      }
    }
    try (Stream<Path> walk = Files.walk(home)) {
      for (Path path :
          walk.filter(Files::isSymbolicLink).sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
    try (Stream<Path> dirs = Files.walk(home)) {
      dirs.filter(Files::isDirectory)
          .filter(dir -> !dir.equals(home))
          .sorted(Comparator.reverseOrder())
          .forEach(
              dir -> {
                try {
                  Files.deleteIfExists(dir);
                } catch (IOException notEmpty) {
                  // Still has kept files; nothing to do.
                }
              });
    }
  }

  private static List<Path> regularFiles(Path home) throws IOException {
    if (!Files.isDirectory(home)) {
      return List.of();
    }
    try (Stream<Path> walk = Files.walk(home)) {
      return walk.filter(Files::isRegularFile)
          .filter(path -> !Files.isSymbolicLink(path))
          .sorted()
          .toList();
    }
  }

  private static Path resolve(Path home, String name) {
    if (name == null || name.isEmpty() || name.contains("\\")) {
      return null;
    }
    if (name.startsWith("/")) {
      return null;
    }
    Path target = home.resolve(name).normalize();
    return target.startsWith(home) ? target : null;
  }

  private static String posix(Path relative) {
    return relative.toString().replace('\\', '/');
  }

  private synchronized void persist() {
    try {
      Files.createDirectories(stateDir);
      Properties properties = new Properties();
      entries.forEach(
          (id, entry) ->
              properties.setProperty(
                  id.toString(),
                  entry.home().toString() + "|" + entry.quotaBytes() + "|" + entry.secret()));
      Path staged = stateDir.resolve("registry.properties.new");
      try (OutputStream file =
          Files.newOutputStream(
              staged,
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE,
              StandardOpenOption.TRUNCATE_EXISTING)) {
        properties.store(file, null);
      }
      restrict(staged);
      try {
        Files.move(
            staged,
            registryFile,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException fallback) {
        Files.move(staged, registryFile, StandardCopyOption.REPLACE_EXISTING);
      }
      restrict(registryFile);
    } catch (IOException failure) {
      throw new SyncException("cannot persist registry", failure);
    }
  }

  private static void restrict(Path file) {
    try {
      Files.setPosixFilePermissions(
          file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException onWindows) {
      // No POSIX modes; the data dir ACLs are the operator's job.
    } catch (IOException ignored) {
      // Best effort; the registry stays readable only by accident, not by promise.
    }
  }

  private void load() {
    if (!Files.isRegularFile(registryFile)) {
      return;
    }
    restrict(registryFile);
    Properties properties = new Properties();
    try (InputStream file = Files.newInputStream(registryFile)) {
      properties.load(file);
    } catch (IOException failure) {
      throw new SyncException("cannot load registry", failure);
    }
    properties.forEach(
        (key, value) -> {
          String[] parts = value.toString().split("\\|", 3);
          if (parts.length == 3) {
            try {
              entries.put(
                  UUID.fromString(key.toString()),
                  new Entry(Path.of(parts[0]), Long.parseLong(parts[1]), parts[2]));
            } catch (IllegalArgumentException bad) {
              // Corrupt row; a fresh prepare rewrites it.
            }
          }
        });
  }
}
