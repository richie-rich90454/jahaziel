/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agenttools.internal;

import dev.jahaziel.agenttools.api.AgentToolBusyException;
import dev.jahaziel.agenttools.api.AgentToolService;
import dev.jahaziel.identity.domain.UserId;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Installs the pinned OpenCode CLI into one user's application-managed tool directory. */
@Service
public final class AgentToolServiceImpl implements AgentToolService {
  private static final Logger log = LoggerFactory.getLogger(AgentToolServiceImpl.class);
  private static final String TOOL_ID = "opencode";
  private static final String VERSION = "1.19.5";
  private static final long MAX_ARCHIVE_BYTES = 128L * 1024 * 1024;
  private static final long MAX_EXECUTABLE_BYTES = 512L * 1024 * 1024;
  private static final long MAX_EXPANDED_ARCHIVE_BYTES = 768L * 1024 * 1024;
  private static final int MAX_ARCHIVE_ENTRIES = 128;
  private static final int BUFFER_SIZE = 64 * 1024;
  private static final Map<String, Artifact> ARTIFACTS =
      Map.of(
          "x64",
          new Artifact(
              "https://github.com/anomalyco/opencode/releases/download/v1.19.5/opencode-linux-x64.tar.gz",
              "588310a336fa3b9df36b3f0225b26f679d453efaad0cf3d66c3651ef3ec1647a",
              79_600_000L,
              "opencode"),
          "arm64",
          new Artifact(
              "https://github.com/anomalyco/opencode/releases/download/v1.19.5/opencode-linux-arm64.tar.gz",
              "fae5be262d693b7c5e562856cc473e59e741772936db0c0600ac0d5ed5bd088b",
              78_500_000L,
              "opencode"));
  private static final Map<String, Artifact> RIPGREP_ARTIFACTS =
      Map.of(
          "x64",
          new Artifact(
              "https://github.com/BurntSushi/ripgrep/releases/download/15.2.0/ripgrep-15.2.0-x86_64-unknown-linux-musl.tar.gz",
              "33e15bcf1624b25cdd2a55813a47a2f95dbe126268203e76aa6a585d1e7b149c",
              2_300_000L,
              "ripgrep-15.2.0-x86_64-unknown-linux-musl/rg"),
          "arm64",
          new Artifact(
              "https://github.com/BurntSushi/ripgrep/releases/download/15.2.0/ripgrep-15.2.0-aarch64-unknown-linux-musl.tar.gz",
              "800b1e7206afe799dfb5a6901f23147cfaabe0e52210538100f61e86e1740915",
              1_900_000L,
              "ripgrep-15.2.0-aarch64-unknown-linux-musl/rg"));

  private final Path storageRoot;
  private final ObjectMapper json;
  private final HttpClient http;
  private final ConcurrentMap<UUID, ToolStatus> liveStates = new ConcurrentHashMap<>();
  private final Set<UUID> installations = ConcurrentHashMap.newKeySet();

  @Autowired
  public AgentToolServiceImpl(
      @Value("${jahaziel.storage.root:data}") String storageRoot, ObjectMapper json) {
    this(
        Path.of(storageRoot),
        json,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  AgentToolServiceImpl(Path storageRoot, ObjectMapper json, HttpClient http) {
    this.storageRoot =
        Objects.requireNonNull(storageRoot, "storageRoot").toAbsolutePath().normalize();
    this.json = Objects.requireNonNull(json, "json");
    this.http = Objects.requireNonNull(http, "http");
  }

  @Override
  public java.util.List<ToolStatus> catalog(UserId userId) {
    Objects.requireNonNull(userId, "userId");
    return java.util.List.of(current(userId));
  }

  @Override
  public ToolStatus installOpenCode(UserId userId) {
    return beginInstall(userId, false);
  }

  @Override
  public ToolStatus updateOpenCode(UserId userId) {
    return beginInstall(userId, true);
  }

  private ToolStatus beginInstall(UserId userId, boolean force) {
    Objects.requireNonNull(userId, "userId");
    ToolStatus current = current(userId);
    if ((!force && "INSTALLED".equals(current.status())) || !installations.add(userId.value())) {
      return current(userId);
    }
    String architecture = architecture();
    if (!ARTIFACTS.containsKey(architecture) || !isLinux()) {
      installations.remove(userId.value());
      return remember(
          userId,
          new ToolStatus(
              TOOL_ID,
              "OpenCode",
              VERSION,
              architecture,
              "UNSUPPORTED",
              0,
              0,
              0,
              "OpenCode is available only on Linux x64 and arm64."));
    }
    Artifact artifact = ARTIFACTS.get(architecture);
    long totalDownloadBytes = totalDownloadBytes(architecture);
    ToolStatus starting =
        new ToolStatus(
            TOOL_ID, "OpenCode", VERSION, architecture, "INSTALLING", totalDownloadBytes, 0, 0, "");
    remember(userId, starting);
    try {
      Thread.ofVirtual()
          .name("agent-tool-opencode-" + userId.value())
          .start(() -> install(userId, architecture, artifact));
    } catch (RuntimeException failure) {
      installations.remove(userId.value());
      return remember(
          userId,
          new ToolStatus(
              TOOL_ID,
              "OpenCode",
              VERSION,
              architecture,
              "FAILED",
              totalDownloadBytes(architecture),
              0,
              0,
              "The OpenCode installer could not be started."));
    }
    return starting;
  }

  @Override
  public ToolStatus removeOpenCode(UserId userId) {
    Objects.requireNonNull(userId, "userId");
    if (!installations.add(userId.value())) {
      throw new AgentToolBusyException();
    }
    try {
      Path binary = executablePath(userId);
      Files.deleteIfExists(binary);
      Files.deleteIfExists(ripgrepPath(userId));
      ToolStatus removed =
          new ToolStatus(
              TOOL_ID,
              "OpenCode",
              VERSION,
              architecture(),
              "NOT_INSTALLED",
              totalDownloadBytes(architecture()),
              0,
              0,
              "");
      return remember(userId, removed);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot remove the managed OpenCode executable", failure);
    } finally {
      installations.remove(userId.value());
    }
  }

  @Override
  public Path executableDirectory(UserId userId) {
    Objects.requireNonNull(userId, "userId");
    return userRoot(userId).resolve("bin");
  }

  @Override
  public Path profileDirectory(UserId userId) {
    Objects.requireNonNull(userId, "userId");
    return userRoot(userId).resolve("profile");
  }

  @Override
  public void prepareProfile(UserId userId) {
    Objects.requireNonNull(userId, "userId");
    try {
      Path root = userRoot(userId);
      ensureDirectory(root.getParent());
      ensureDirectory(root);
      ensureDirectory(executableDirectory(userId));
      Path profile = profileDirectory(userId);
      ensureDirectory(profile);
      ensureDirectory(profile.resolve("config"));
      ensureDirectory(profile.resolve("data"));
      ensureDirectory(profile.resolve("cache"));
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot prepare the per-user coding tool profile", failure);
    }
  }

  private void install(UserId userId, String architecture, Artifact artifact) {
    Path root = userRoot(userId);
    Path staging = root.resolve("staging-" + UUID.randomUUID());
    Artifact ripgrepArtifact = RIPGREP_ARTIFACTS.get(architecture);
    long totalDownloadBytes = totalDownloadBytes(architecture);
    try {
      ensureDirectory(root.getParent());
      ensureDirectory(root);
      ensureDirectory(staging);
      Path archive = staging.resolve("opencode.tar.gz");
      download(URI.create(artifact.url()), artifact, archive, userId, 0, totalDownloadBytes);
      Path executable = staging.resolve("opencode");
      extractExecutable(archive, executable, artifact.executableEntry());
      verifyExecutableArchitecture(executable, architecture);
      setExecutable(executable);
      Path ripgrepArchive = staging.resolve("ripgrep.tar.gz");
      download(
          URI.create(ripgrepArtifact.url()),
          ripgrepArtifact,
          ripgrepArchive,
          userId,
          artifact.downloadBytes(),
          totalDownloadBytes);
      Path ripgrep = staging.resolve("rg");
      extractExecutable(ripgrepArchive, ripgrep, ripgrepArtifact.executableEntry());
      verifyExecutableArchitecture(ripgrep, architecture);
      setExecutable(ripgrep);
      Path bin = root.resolve("bin");
      ensureDirectory(bin);
      Path replacement = bin.resolve("opencode.new");
      Path ripgrepReplacement = bin.resolve("rg.new");
      Files.move(executable, replacement, StandardCopyOption.REPLACE_EXISTING);
      Files.move(ripgrep, ripgrepReplacement, StandardCopyOption.REPLACE_EXISTING);
      try {
        Files.move(
            replacement,
            bin.resolve("opencode"),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
        Files.move(replacement, bin.resolve("opencode"), StandardCopyOption.REPLACE_EXISTING);
      }
      try {
        Files.move(
            ripgrepReplacement,
            bin.resolve("rg"),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
        Files.move(ripgrepReplacement, bin.resolve("rg"), StandardCopyOption.REPLACE_EXISTING);
      }
      setExecutable(bin.resolve("opencode"));
      setExecutable(bin.resolve("rg"));
      ToolStatus installed =
          new ToolStatus(
              TOOL_ID,
              "OpenCode",
              VERSION,
              architecture,
              "INSTALLED",
              totalDownloadBytes,
              Files.size(bin.resolve("opencode")) + Files.size(bin.resolve("rg")),
              100,
              "");
      remember(userId, installed);
      log.info(
          "managed coding tool installed userId={} tool={} version={} architecture={} bytes={}",
          userId,
          TOOL_ID,
          VERSION,
          architecture,
          installed.installedSizeBytes());
    } catch (Exception failure) {
      String message = safeMessage(failure);
      remember(
          userId,
          new ToolStatus(
              TOOL_ID,
              "OpenCode",
              VERSION,
              architecture,
              "FAILED",
              totalDownloadBytes,
              0,
              0,
              message));
      log.warn(
          "managed coding tool install failed userId={} tool={} version={} reason={}",
          userId,
          TOOL_ID,
          VERSION,
          message);
    } finally {
      installations.remove(userId.value());
      deleteStaging(staging);
    }
  }

  private void download(
      URI uri,
      Artifact artifact,
      Path destination,
      UserId userId,
      long progressOffset,
      long totalDownloadBytes)
      throws IOException, InterruptedException {
    HttpResponse<InputStream> response = pinnedResponse(uri);
    try (InputStream input = response.body()) {
      URI finalUri = response.uri();
      if (response.statusCode() != 200
          || !"https".equalsIgnoreCase(finalUri.getScheme())
          || !Set.of("github.com", "release-assets.githubusercontent.com")
              .contains(finalUri.getHost())) {
        throw new IOException("OpenCode download was rejected by the pinned source policy");
      }
      long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
      if (declaredLength > MAX_ARCHIVE_BYTES) {
        throw new IOException("OpenCode archive exceeds the catalog size limit");
      }
      MessageDigest digest = sha256();
      long received = 0;
      try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(destination))) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int count;
        while ((count = input.read(buffer)) >= 0) {
          if (count == 0) continue;
          received += count;
          if (received > MAX_ARCHIVE_BYTES) {
            throw new IOException("OpenCode archive exceeds the catalog size limit");
          }
          digest.update(buffer, 0, count);
          output.write(buffer, 0, count);
          int progress =
              (int)
                  Math.min(95, (progressOffset + received) * 95 / Math.max(1, totalDownloadBytes));
          liveStates.put(
              userId.value(),
              new ToolStatus(
                  TOOL_ID,
                  "OpenCode",
                  VERSION,
                  architecture(),
                  "INSTALLING",
                  totalDownloadBytes,
                  0,
                  progress,
                  ""));
        }
      }
      String actual = HexFormat.of().formatHex(digest.digest());
      if (!actual.equals(artifact.sha256())) {
        throw new IOException("OpenCode archive checksum does not match the pinned catalog");
      }
    }
  }

  private HttpResponse<InputStream> pinnedResponse(URI requested)
      throws IOException, InterruptedException {
    URI current = requested;
    for (int redirect = 0; redirect <= 3; redirect++) {
      if (!allowedDownloadUri(current)) {
        throw new IOException("OpenCode download was rejected by the pinned source policy");
      }
      HttpRequest request =
          HttpRequest.newBuilder(current).timeout(Duration.ofMinutes(5)).GET().build();
      HttpResponse<InputStream> response =
          http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      int status = response.statusCode();
      if (status == 200) return response;
      boolean redirectStatus =
          status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
      if (!redirectStatus || redirect == 3) {
        response.body().close();
        throw new IOException("OpenCode download source returned an unexpected response");
      }
      String location = response.headers().firstValue("Location").orElse(null);
      response.body().close();
      if (location == null) {
        throw new IOException("OpenCode download source returned an invalid redirect");
      }
      current = current.resolve(location);
    }
    throw new IOException("OpenCode download source exceeded the redirect limit");
  }

  private static boolean allowedDownloadUri(URI uri) {
    return "https".equalsIgnoreCase(uri.getScheme())
        && uri.getUserInfo() == null
        && (uri.getPort() == -1 || uri.getPort() == 443)
        && Set.of("github.com", "release-assets.githubusercontent.com").contains(uri.getHost());
  }

  static void extractExecutable(Path archive, Path destination) throws IOException {
    extractExecutable(archive, destination, "opencode");
  }

  static void extractExecutable(Path archive, Path destination, String expectedEntry)
      throws IOException {
    boolean found = false;
    int entries = 0;
    try (InputStream input =
        new BoundedInputStream(
            new BufferedInputStream(new GZIPInputStream(Files.newInputStream(archive))),
            MAX_EXPANDED_ARCHIVE_BYTES)) {
      byte[] header = new byte[512];
      while (readBlock(input, header)) {
        if (allZero(header)) break;
        if (++entries > MAX_ARCHIVE_ENTRIES) {
          throw new IOException("OpenCode archive contains too many entries");
        }
        String name = tarString(header, 0, 100);
        long size = tarSize(header, 124, 12);
        if (size < 0 || size > MAX_EXECUTABLE_BYTES) {
          throw new IOException("OpenCode archive contains an oversized entry");
        }
        int type = header[156] & 0xff;
        boolean regularFile = type == 0 || type == '0';
        if (regularFile && name.equals(expectedEntry)) {
          if (found || size < 4 || size > MAX_EXECUTABLE_BYTES) {
            throw new IOException("OpenCode archive contains an invalid executable entry");
          }
          try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(destination))) {
            copyExactly(input, output, size);
          }
          found = true;
        } else {
          if (type == '1' || type == '2') {
            throw new IOException("OpenCode archive contains a link entry");
          }
          skipExactly(input, size);
        }
        long padding = (512 - (size % 512)) % 512;
        skipExactly(input, padding);
      }
    }
    if (!found) throw new IOException("OpenCode archive does not contain its expected executable");
  }

  private static void verifyExecutableArchitecture(Path executable, String architecture)
      throws IOException {
    byte[] header;
    try (InputStream input = Files.newInputStream(executable)) {
      header = input.readNBytes(20);
    }
    if (header.length < 20
        || header[0] != 0x7f
        || header[1] != 'E'
        || header[2] != 'L'
        || header[3] != 'F'
        || header[4] != 2
        || header[5] != 1) {
      throw new IOException("OpenCode executable is not a little-endian 64-bit Linux binary");
    }
    int machine = (header[18] & 0xff) | ((header[19] & 0xff) << 8);
    int expected = "x64".equals(architecture) ? 62 : 183;
    if (machine != expected) throw new IOException("OpenCode executable architecture mismatch");
  }

  private ToolStatus current(UserId userId) {
    ToolStatus live = liveStates.get(userId.value());
    if (live != null) return live;
    Path binary = executablePath(userId);
    Path ripgrep = ripgrepPath(userId);
    if (isManagedBinary(binary) && isManagedBinary(ripgrep)) {
      try {
        ToolStatus installed =
            new ToolStatus(
                TOOL_ID,
                "OpenCode",
                VERSION,
                architecture(),
                "INSTALLED",
                totalDownloadBytes(architecture()),
                Files.size(binary) + Files.size(ripgrep),
                100,
                "");
        return remember(userId, installed);
      } catch (IOException failure) {
        throw new UncheckedIOException("cannot inspect the managed OpenCode executable", failure);
      }
    }
    ToolStatus stored = readStatus(userId);
    if (stored != null && "INSTALLING".equals(stored.status())) {
      return remember(
          userId,
          new ToolStatus(
              TOOL_ID,
              "OpenCode",
              VERSION,
              architecture(),
              "FAILED",
              totalDownloadBytes(architecture()),
              0,
              0,
              "Installation was interrupted by a server restart. Retry to continue."));
    }
    if (stored != null && "FAILED".equals(stored.status())) return stored;
    return new ToolStatus(
        TOOL_ID,
        "OpenCode",
        VERSION,
        architecture(),
        "NOT_INSTALLED",
        totalDownloadBytes(architecture()),
        0,
        0,
        "");
  }

  private ToolStatus remember(UserId userId, ToolStatus status) {
    liveStates.put(userId.value(), status);
    writeStatus(userId, status);
    return status;
  }

  private void writeStatus(UserId userId, ToolStatus status) {
    Path root = userRoot(userId);
    Path target = root.resolve("opencode-status.json");
    Path temporary = root.resolve("opencode-status.tmp");
    try {
      ensureDirectory(root.getParent());
      ensureDirectory(root);
      Files.writeString(temporary, json.writeValueAsString(status), StandardCharsets.UTF_8);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
      setOwnerOnly(root, true);
      setOwnerOnly(target, false);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot persist managed tool state", failure);
    }
  }

  private ToolStatus readStatus(UserId userId) {
    Path status = userRoot(userId).resolve("opencode-status.json");
    if (!Files.isRegularFile(status, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(status)) {
      return null;
    }
    try {
      return json.readValue(Files.readString(status, StandardCharsets.UTF_8), ToolStatus.class);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot read managed tool state", failure);
    }
  }

  private void deleteStaging(Path staging) {
    if (!Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(staging)) {
      return;
    }
    try (var children = Files.list(staging)) {
      for (Path child : children.toList()) {
        if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(child)) {
          Files.deleteIfExists(child);
        }
      }
      Files.deleteIfExists(staging);
    } catch (IOException failure) {
      log.warn("cannot remove OpenCode install staging directory path={}", staging);
    }
  }

  private Path userRoot(UserId userId) {
    Objects.requireNonNull(userId, "userId");
    Path users = storageRoot.resolve("users").normalize();
    Path root = users.resolve(userId.value().toString()).normalize();
    if (!root.startsWith(users)) throw new IllegalArgumentException("invalid user tool path");
    rejectSymbolicLink(users);
    rejectSymbolicLink(root);
    return root;
  }

  private static void rejectSymbolicLink(Path path) {
    if (Files.isSymbolicLink(path)) {
      throw new IllegalStateException("managed user tool directory contains a symbolic link");
    }
  }

  private Path executablePath(UserId userId) {
    Path directory = executableDirectory(userId);
    rejectSymbolicLink(directory);
    Path binary = directory.resolve(TOOL_ID);
    rejectSymbolicLink(binary);
    return binary;
  }

  private Path ripgrepPath(UserId userId) {
    Path directory = executableDirectory(userId);
    rejectSymbolicLink(directory);
    Path binary = directory.resolve("rg");
    rejectSymbolicLink(binary);
    return binary;
  }

  private static boolean isManagedBinary(Path path) {
    return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
  }

  private Artifact artifactForHost() {
    return ARTIFACTS.getOrDefault(architecture(), new Artifact("", "", 0, "opencode"));
  }

  private static long totalDownloadBytes(String architecture) {
    Artifact openCode = ARTIFACTS.get(architecture);
    Artifact ripgrep = RIPGREP_ARTIFACTS.get(architecture);
    return openCode == null || ripgrep == null
        ? 0
        : openCode.downloadBytes() + ripgrep.downloadBytes();
  }

  private static String architecture() {
    String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
    if (arch.equals("amd64") || arch.equals("x86_64")) return "x64";
    if (arch.equals("aarch64") || arch.equals("arm64")) return "arm64";
    return arch.isBlank() ? "unknown" : arch;
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux");
  }

  private static String safeMessage(Exception failure) {
    if (failure instanceof InterruptedException) {
      Thread.currentThread().interrupt();
      return "Installation was interrupted.";
    }
    String message = failure.getMessage();
    if (message == null || message.isBlank()) return "OpenCode could not be installed.";
    if (message.length() > 240) message = message.substring(0, 240);
    return message;
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static void ensureDirectory(Path path) throws IOException {
    Path absolute = path.toAbsolutePath().normalize();
    Path cursor = absolute.getRoot();
    for (Path part : absolute) {
      cursor = cursor.resolve(part);
      if (Files.isSymbolicLink(cursor))
        throw new IOException("managed tool path contains a symbolic link");
      if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(cursor);
      if (!Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("managed tool path is not a directory");
      }
    }
    setOwnerOnly(absolute, true);
  }

  private static void setExecutable(Path path) throws IOException {
    try {
      Files.setPosixFilePermissions(
          path,
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE));
    } catch (UnsupportedOperationException ignored) {
      if (!path.toFile().setExecutable(true, true)) {
        throw new IOException("cannot mark managed OpenCode executable");
      }
    }
  }

  private static void setOwnerOnly(Path path, boolean directory) throws IOException {
    try {
      Files.setPosixFilePermissions(
          path,
          directory
              ? Set.of(
                  PosixFilePermission.OWNER_READ,
                  PosixFilePermission.OWNER_WRITE,
                  PosixFilePermission.OWNER_EXECUTE)
              : Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException ignored) {
      // POSIX permissions apply on supported Linux hosts. Windows tests use the same path logic.
    }
  }

  private static boolean readBlock(InputStream input, byte[] block) throws IOException {
    int offset = 0;
    while (offset < block.length) {
      int count = input.read(block, offset, block.length - offset);
      if (count < 0) {
        if (offset == 0) return false;
        throw new IOException("OpenCode archive has a truncated tar header");
      }
      offset += count;
    }
    return true;
  }

  private static boolean allZero(byte[] bytes) {
    for (byte value : bytes) if (value != 0) return false;
    return true;
  }

  private static String tarString(byte[] header, int offset, int length) {
    int end = offset;
    while (end < offset + length && header[end] != 0) end++;
    return new String(header, offset, end - offset, StandardCharsets.UTF_8);
  }

  private static long tarSize(byte[] header, int offset, int length) throws IOException {
    String value = tarString(header, offset, length).strip();
    if (value.isEmpty()) return 0;
    try {
      return Long.parseLong(value, 8);
    } catch (NumberFormatException invalid) {
      throw new IOException("OpenCode archive has an invalid tar entry size", invalid);
    }
  }

  private static void copyExactly(InputStream input, OutputStream output, long size)
      throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    long remaining = size;
    while (remaining > 0) {
      int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (count < 0) throw new IOException("OpenCode archive has a truncated executable");
      output.write(buffer, 0, count);
      remaining -= count;
    }
  }

  private static void skipExactly(InputStream input, long size) throws IOException {
    long remaining = size;
    while (remaining > 0) {
      long skipped = input.skip(remaining);
      if (skipped == 0) {
        if (input.read() < 0) throw new IOException("OpenCode archive has a truncated tar entry");
        skipped = 1;
      }
      remaining -= skipped;
    }
  }

  private record Artifact(String url, String sha256, long downloadBytes, String executableEntry) {}

  /** Counts decompressed bytes so a small gzip response cannot expand without a bound. */
  private static final class BoundedInputStream extends java.io.FilterInputStream {
    private final long limit;
    private long count;

    private BoundedInputStream(InputStream input, long limit) {
      super(input);
      this.limit = limit;
    }

    @Override
    public int read() throws IOException {
      int value = super.read();
      if (value >= 0) add(1);
      return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      int read = super.read(bytes, offset, length);
      if (read > 0) add(read);
      return read;
    }

    @Override
    public long skip(long requested) throws IOException {
      long remaining = requested;
      byte[] buffer = new byte[8192];
      while (remaining > 0) {
        int read = read(buffer, 0, (int) Math.min(buffer.length, remaining));
        if (read < 0) break;
        remaining -= read;
      }
      return requested - remaining;
    }

    private void add(int amount) throws IOException {
      count += amount;
      if (count > limit) {
        throw new IOException("OpenCode archive exceeds the expanded size limit");
      }
    }
  }
}
