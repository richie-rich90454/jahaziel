/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * Black-box smoke test. Drives {@code java -jar jahaziel.jar} in isolated temp workdirs: usage,
 * missing-vars failure, init, doctor, then a full demo boot with HTTP assertions and process
 * cleanup. Run with {@code -Psmoke} after {@code package}.
 */
@Tag("smoke")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SmokeTest {
  private static final Duration BOOT_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration KILL_TIMEOUT = Duration.ofSeconds(20);

  @Test
  @Order(0)
  void jarContainsOnlyMaintainedWorkbenchAssets(@TempDir Path tmp) throws Exception {
    Path jarPath = copyJarTo(tmp);
    try (JarFile jar = new JarFile(jarPath.toFile())) {
      Set<String> entries = jar.stream().map(entry -> entry.getName()).collect(Collectors.toSet());
      assertTrue(entries.contains("BOOT-INF/classes/static/index.html"));
      assertTrue(
          entries.stream()
              .anyMatch(name -> name.matches("BOOT-INF/classes/static/assets/editor-[^/]+\\.js")));
      assertFalse(entries.contains("BOOT-INF/classes/static/app.js"));
      assertFalse(entries.contains("BOOT-INF/classes/static/styles.css"));
      assertEquals(
          1,
          entries.stream()
              .filter(name -> name.matches("BOOT-INF/classes/static/assets/index-[^/]+\\.js"))
              .count());
      assertFalse(
          entries.stream()
              .anyMatch(
                  name ->
                      name.contains("code-server")
                          || name.contains("clangd")
                          || name.contains("rust-analyzer")
                          || name.contains("gopls")
                          || name.contains("jdtls")
                          || name.contains("node_modules")));
    }
  }

  @Test
  @Order(1)
  void bareRunPrintsUsage(@TempDir Path tmp) throws Exception {
    Path jar = copyJarTo(tmp);
    Run run = exec(tmp, List.of("java", "-jar", jar.toString()), Map.of(), 60);
    assertEquals(0, run.code(), "bare run must exit 0, stderr: " + run.err());
    assertTrue(run.out().contains("Usage"), "bare run must print Usage, got: " + run.out());
  }

  @Test
  @Order(3)
  void initCreatesEnvAndData(@TempDir Path tmp) throws Exception {
    Path jar = copyJarTo(tmp);
    Run first = exec(tmp, List.of("java", "-jar", jar.toString(), "init"), Map.of(), 60);
    assertEquals(0, first.code(), "init must exit 0: " + first.err());
    Path env = tmp.resolve(".env");
    assertTrue(Files.isRegularFile(env), "init must create .env");
    String body = Files.readString(env, StandardCharsets.UTF_8);
    for (String key :
        List.of(
            "BASIS_AUTH_ISSUER",
            "BASIS_REDIRECT_URI",
            "BASIS_CLIENT_ID",
            "BASIS_CLIENT_SECRET",
            "BASIS_RESOURCE")) {
      assertTrue(body.contains(key + "="), ".env must define " + key);
    }
    String secret = value(body, "BASIS_CLIENT_SECRET");
    assertTrue(secret.matches("[0-9a-f]{64}"), "secret must be 64 hex chars, got: " + secret);
    assertTrue(Files.isDirectory(tmp.resolve("data")), "init must mkdir data/");
    Run second = exec(tmp, List.of("java", "-jar", jar.toString(), "init"), Map.of(), 60);
    assertTrue(second.code() != 0, "second init must refuse to overwrite .env");
  }

  @Test
  @Order(4)
  void doctorPassesOnFreshInstall(@TempDir Path tmp) throws Exception {
    Path jar = copyJarTo(tmp);
    exec(tmp, List.of("java", "-jar", jar.toString(), "init"), Map.of(), 60);
    int free = freePort();
    Map<String, String> env = Map.of("PORT", String.valueOf(free));
    Run doctor = exec(tmp, List.of("java", "-jar", jar.toString(), "doctor"), env, 120);
    assertEquals(0, doctor.code(), "doctor must pass on a fresh install: " + doctor.out());
    Matcher checks = Pattern.compile("doctor: (\\d+)/(\\d+) checks passed").matcher(doctor.out());
    assertTrue(checks.find(), "doctor must report its check totals, got: " + doctor.out());
    assertEquals(checks.group(2), checks.group(1), "doctor must pass every reported check");
    assertTrue(
        doctor.out().contains("[ok] git -"),
        "doctor must report Git availability: " + doctor.out());
  }

  @Test
  @Order(5)
  void fullRunServesHealthWorkbenchAndAdmin(@TempDir Path work) throws Exception {
    Path jar = copyJarTo(work);
    exec(work, List.of("java", "-jar", jar.toString(), "init"), Map.of(), 60);
    Path dataTmp = work.resolve("data").resolve("tmp");
    Files.createDirectories(dataTmp);
    int port = freePort();
    List<String> serverCommand =
        List.of(
            "java",
            "-Djava.io.tmpdir=" + dataTmp,
            "-jar",
            jar.toString(),
            "--demo",
            "--port=" + port);
    ProcessBuilder serverBuilder = new ProcessBuilder(serverCommand).directory(work.toFile());
    serverBuilder.environment().keySet().removeIf(key -> key.startsWith("BASIS_"));
    serverBuilder.environment().put("PORT", String.valueOf(port));
    serverBuilder.redirectOutput(work.resolve("server.log").toFile());
    serverBuilder.redirectErrorStream(true);
    Process server = serverBuilder.start();
    try {
      HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
      String health = pollHealth(http, port, server, work.resolve("server.log"));
      assertTrue(health.contains("\"status\":\"UP\""), "health must be UP, got: " + health);
      assertTrue(
          Files.isRegularFile(work.resolve("data").resolve("jahaziel.db")),
          "boot must create data/jahaziel.db");

      try (Stream<Path> entries = Files.list(work)) {
        Set<String> names =
            entries
                .map(path -> path.getFileName().toString())
                .filter(name -> !name.equals("server.log"))
                .collect(Collectors.toSet());
        assertEquals(
            Set.of("jahaziel.jar", ".env", "data"), names, "workdir must hold only jar+.env+data/");
      }

      HttpResponse<String> root =
          get(http, "http://127.0.0.1:" + port + "/", Map.of("Accept", "text/html"));
      assertEquals(200, root.statusCode(), "/ must be 200, body: " + root.body());
      assertTrue(
          root.body().toLowerCase(Locale.ROOT).contains("workbench"),
          "/ must contain the workbench");
      assertTrue(
          root.body().contains("terminal-session-select"),
          "workbench must expose reconnectable terminals");
      assertTrue(
          root.body().contains("terminal-reconnect"), "workbench must expose a reconnect action");

      Matcher script = Pattern.compile("<script[^>]+src=\"([^\"]+\\.js)\"").matcher(root.body());
      assertTrue(script.find(), "workbench page must reference a built JavaScript asset");
      HttpResponse<String> scriptAsset =
          get(http, "http://127.0.0.1:" + port + script.group(1), Map.of());
      assertEquals(200, scriptAsset.statusCode(), "packaged workbench JavaScript must be served");
      Matcher stylesheet =
          Pattern.compile("<link[^>]+href=\"([^\"]+\\.css)\"").matcher(root.body());
      assertTrue(stylesheet.find(), "workbench page must reference a built stylesheet");
      HttpResponse<String> cssAsset =
          get(http, "http://127.0.0.1:" + port + stylesheet.group(1), Map.of());
      assertEquals(200, cssAsset.statusCode(), "packaged workbench CSS must be served");

      HttpResponse<String> admin = get(http, "http://127.0.0.1:" + port + "/_admin", Map.of());
      assertEquals(200, admin.statusCode(), "/_admin must be 200, body: " + admin.body());

      HttpResponse<String> identity =
          get(http, "http://127.0.0.1:" + port + "/api/v1/me", Map.of());
      assertEquals(200, identity.statusCode(), "demo identity must be available without a login");
      assertTrue(identity.body().contains("demo@example.com"), identity.body());
      HttpResponse<String> workspaces =
          get(http, "http://127.0.0.1:" + port + "/api/v1/me/workspaces", Map.of());
      assertEquals(200, workspaces.statusCode(), "demo workspaces must be available anonymously");
      assertTrue(workspaces.body().contains("\"name\":\"demo\""), workspaces.body());
      HttpResponse<String> templates =
          get(http, "http://127.0.0.1:" + port + "/api/v1/templates", Map.of());
      assertEquals(200, templates.statusCode(), "demo templates must load in the workbench");
      HttpResponse<String> signIn = get(http, "http://127.0.0.1:" + port + "/api/login", Map.of());
      assertEquals(302, signIn.statusCode(), "demo sign-in link should return to the workbench");
      assertEquals("/", signIn.headers().firstValue("location").orElseThrow());

    } finally {
      server.destroy();
      boolean gone = server.waitFor(KILL_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
      if (!gone) {
        server.destroyForcibly();
        fail("server did not stop within 20s");
      }
    }
    assertNoOrphan(work.resolve("jahaziel.jar"));
  }

  private static String pollHealth(HttpClient http, int port, Process server, Path serverLog)
      throws Exception {
    long deadline = System.currentTimeMillis() + BOOT_TIMEOUT.toMillis();
    String last = "";
    while (System.currentTimeMillis() < deadline) {
      if (!server.isAlive()) {
        fail("server exited with " + server.exitValue() + "; log: " + Files.readString(serverLog));
      }
      try {
        HttpResponse<String> response =
            get(http, "http://127.0.0.1:" + port + "/actuator/health", Map.of());
        last = response.body() == null ? "" : response.body().replace(" ", "");
        if (response.statusCode() == 200 && last.contains("\"status\":\"UP\"")) {
          return response.body();
        }
      } catch (IOException retry) {
        last = retry.getMessage() == null ? "unreachable" : retry.getMessage();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw interrupted;
      }
      Thread.sleep(1000);
    }
    fail("health was not UP within 60s, last: " + last + "; log: " + Files.readString(serverLog));
    throw new IllegalStateException("unreachable");
  }

  private static HttpResponse<String> get(HttpClient http, String url, Map<String, String> headers)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET();
    headers.forEach(request::header);
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static void assertNoOrphan(Path jar) {
    long self = ProcessHandle.current().pid();
    String needle = jar.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    List<Long> strays = new ArrayList<>();
    ProcessHandle.allProcesses()
        .filter(process -> process.pid() != self)
        .forEach(
            process ->
                process
                    .info()
                    .commandLine()
                    .ifPresent(
                        cmd -> {
                          if (cmd.toLowerCase(Locale.ROOT).contains(needle)) {
                            strays.add(process.pid());
                          }
                        }));
    assertTrue(strays.isEmpty(), "orphan java processes hold " + jar + ": " + strays);
  }

  private static String value(String body, String key) {
    for (String line : body.split("\r?\n")) {
      if (line.startsWith(key + "=")) {
        return line.substring(key.length() + 1).strip();
      }
    }
    return "";
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static Path jar() {
    String configured = System.getProperty("jahaziel.jar", "");
    if (!configured.isBlank() && Files.isRegularFile(Path.of(configured))) {
      return Path.of(configured).toAbsolutePath().normalize();
    }
    for (String candidate : List.of("target/jahaziel.jar")) {
      Path path = Path.of(candidate).toAbsolutePath().normalize();
      if (Files.isRegularFile(path)) {
        return path;
      }
    }
    fail("jahaziel.jar not found: build first (package) or set -Djahaziel.jar=<path>");
    throw new IllegalStateException("unreachable");
  }

  private static Path copyJarTo(Path work) throws IOException {
    Path dest = work.resolve("jahaziel.jar");
    Files.copy(jar(), dest, StandardCopyOption.COPY_ATTRIBUTES);
    return dest;
  }

  private record Run(int code, String out, String err) {}

  private static Run exec(
      Path work, List<String> command, Map<String, String> env, long timeoutSeconds)
      throws Exception {
    ProcessBuilder builder = new ProcessBuilder(command).directory(work.toFile());
    if (!env.isEmpty()) {
      builder.environment().putAll(env);
    }
    Process process = builder.start();
    String out;
    String err;
    try (var outThread = stream(process.getInputStream());
        var errThread = stream(process.getErrorStream())) {
      boolean done = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
      if (!done) {
        process.destroyForcibly();
        fail("timed out: " + command);
      }
      out = outThread.read();
      err = errThread.read();
    }
    return new Run(process.exitValue(), out, err);
  }

  private static Reader stream(java.io.InputStream in) {
    ByteCollector collector = new ByteCollector(in);
    Thread thread = Thread.ofVirtual().start(collector);
    return new Reader(collector, thread);
  }

  private static final class ByteCollector implements Runnable {
    private final java.io.InputStream in;
    private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

    ByteCollector(java.io.InputStream in) {
      this.in = in;
    }

    @Override
    public void run() {
      try {
        in.transferTo(buffer);
      } catch (IOException ignored) {
        // Process ended; whatever was captured is enough.
      }
    }

    String text() {
      return buffer.toString(StandardCharsets.UTF_8);
    }
  }

  private record Reader(ByteCollector collector, Thread thread) implements AutoCloseable {
    String read() throws InterruptedException {
      thread.join(10_000);
      return collector.text();
    }

    @Override
    public void close() {
      thread.interrupt();
    }
  }
}
