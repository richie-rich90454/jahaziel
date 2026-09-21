/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import dev.jahaziel.JahazielApplication;
import dev.jahaziel.platform.DotEnv;
import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Ultra fat jar dispatcher. Bare {@code java -jar jahaziel.jar} with a jar-adjacent {@code .env}
 * boots the FULL stack (control plane, node agent, and gateway in one process) on 11451; bare
 * without {@code .env} prints help and runs nothing. Portable boot defaults to the jar-adjacent
 * {@code data/} directory and a SQLite file that is created once and never cleared.
 */
public final class JahazielMain {
  /**
   * Boot OAuth2 client auto-configs, old and new locations. Excluded in portable mode so no
   * identity provider is contacted: session auth is bearer JWT only.
   */
  static final String OAUTH2_CLIENT_AUTO_CONFIG =
      "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration,"
          + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration";

  /** Default control-plane port for the portable jar. */
  static final int DEFAULT_PORT = 11451;

  /** Subdirectories owned under the data root. */
  static final List<String> PORTABLE_DIRS =
      List.of(
          "data", "logs", "tmp", "workspaces", "ide", "backups", "dumps", "plugins", "templates");

  private JahazielMain() {}

  static int run(String[] args, PrintStream out, PrintStream err) {
    Objects.requireNonNull(args, "args");
    try {
      String[] normalized = dev.jahaziel.cli.Cli.normalize(args);
      int cli = dev.jahaziel.cli.Cli.dispatch(normalized, out, err);
      if (cli != dev.jahaziel.cli.Cli.PASS_THROUGH) {
        return cli;
      }
      Arguments parsed = Arguments.parse(normalized);
      if (parsed.help()) {
        out.print(help());
        return 0;
      }
      if (parsed.version()) {
        out.println("jahaziel " + version());
        return 0;
      }
      return launch(parsed, out);
    } catch (IllegalArgumentException bad) {
      err.println("jahaziel: " + bad.getMessage());
      err.print(help());
      return 2;
    } catch (InterruptedException done) {
      Thread.currentThread().interrupt();
      return 0;
    } catch (Exception failure) {
      err.println("jahaziel: failed: " + failure.getMessage());
      return 1;
    }
  }

  private static int launch(Arguments parsed, PrintStream out) throws Exception {
    return launch(parsed, out, new CountDownLatch(1));
  }

  static int launch(Arguments parsed, PrintStream out, CountDownLatch stop) throws Exception {
    Platform platform = Platforms.detect(parsed.dataDir());
    Files.createDirectories(platform.tempDir());
    System.setProperty("java.io.tmpdir", platform.tempDir().toString());
    Path envFile = platform.jarDir().resolve(".env");
    if (!Files.isRegularFile(envFile)) {
      out.print(help());
      out.println("jahaziel: no .env yet; run `init` first, then start with no args");
      return 0;
    }
    Map<String, String> fileEnv = DotEnv.parse(envFile);
    if (parsed.demo()) {
      fileEnv = stripBasisVars(fileEnv);
    }
    applyDotEnv(fileEnv);
    reportBasisVars(fileEnv, out, parsed.demo());
    JarDataDir data = JarDataDir.resolve(parsed.dataDir());
    ensurePortableDirs(data.root());
    ensurePortableDirs(platform.dataDir());
    TemplateSeeds.copyIfAbsent(data.root().resolve("templates"));
    DevKeys.Keys keys = DevKeys.ensure(data.keys());
    portableProperties(data, keys, parsed, platform);
    ConfigurableApplicationContext context = SpringApplication.run(JahazielApplication.class);
    try {
      markInitialized(data.root());
      out.println(
          "jahaziel all running: api=:"
              + parsed.port()
              + " gateway=:"
              + parsed.gatewayPort()
              + " data="
              + data.root());
      stop.await();
      return 0;
    } finally {
      context.close();
    }
  }

  static void ensurePortableDirs(Path root) throws java.io.IOException {
    Files.createDirectories(root);
    Files.createDirectories(root.resolve("logs"));
    Files.createDirectories(root.resolve("tmp"));
    Files.createDirectories(root.resolve("workspaces"));
    Files.createDirectories(root.resolve("ide"));
    Files.createDirectories(root.resolve("backups"));
    Files.createDirectories(root.resolve("dumps"));
    Files.createDirectories(root.resolve("plugins"));
    Files.createDirectories(root.resolve("templates"));
  }

  static void markInitialized(Path root) throws java.io.IOException {
    Path marker = root.resolve(".initialized");
    if (!Files.isRegularFile(marker)) {
      Files.createFile(marker);
    }
  }

  static Map<String, String> stripBasisVars(Map<String, String> fileEnv) {
    Map<String, String> kept = new java.util.LinkedHashMap<>(fileEnv);
    kept.keySet().removeAll(DotEnv.BASIS_VARS);
    return java.util.Collections.unmodifiableMap(kept);
  }

  static void applyDotEnv(Map<String, String> fileEnv) {
    for (Map.Entry<String, String> entry : fileEnv.entrySet()) {
      String key = entry.getKey();
      if (System.getenv(key) != null || System.getProperty(key) != null) {
        continue;
      }
      System.setProperty(key, entry.getValue());
      String basisProperty = basisProperty(key);
      if (basisProperty != null && System.getProperty(basisProperty) == null) {
        System.setProperty(basisProperty, entry.getValue());
      }
    }
    for (String key : DotEnv.BASIS_VARS) {
      String property = basisProperty(key);
      if (property == null) {
        continue;
      }
      String value = System.getenv(key);
      if (value == null) {
        value = System.getProperty(key);
      }
      if (value == null) {
        value = fileEnv.get(key);
      }
      if (value != null && !value.isBlank() && System.getProperty(property) == null) {
        System.setProperty(property, value);
      }
    }
  }

  static String basisProperty(String envKey) {
    return switch (envKey) {
      case "BASIS_AUTH_ISSUER" -> "basis.auth-issuer";
      case "BASIS_REDIRECT_URI" -> "basis.redirect-uri";
      case "BASIS_CLIENT_ID" -> "basis.client-id";
      case "BASIS_CLIENT_SECRET" -> "basis.client-secret";
      case "BASIS_RESOURCE" -> "basis.resource";
      default -> null;
    };
  }

  static List<String> missingBasisVars(Map<String, String> fileEnv) {
    List<String> missing = new ArrayList<>();
    for (String key : DotEnv.BASIS_VARS) {
      String value = System.getenv(key);
      if (value == null) {
        value = System.getProperty(key);
      }
      if (value == null) {
        value = fileEnv.get(key);
      }
      if (value == null || value.isBlank()) {
        missing.add(key);
      }
    }
    return List.copyOf(missing);
  }

  private static void reportBasisVars(Map<String, String> fileEnv, PrintStream out, boolean demo) {
    if (demo) {
      out.println("jahaziel: demo mode (stub IdP, BASIS_* ignored)");
      return;
    }
    List<String> missing = missingBasisVars(fileEnv);
    if (missing.isEmpty()) {
      out.println("jahaziel: basis-auth configured");
    } else {
      out.println("jahaziel: login disabled, missing " + String.join(", ", missing));
    }
  }

  static void portableProperties(JarDataDir data, DevKeys.Keys keys, Arguments parsed) {
    portableProperties(data, keys, parsed, Platforms.detect(parsed.dataDir()));
  }

  static void portableProperties(
      JarDataDir data, DevKeys.Keys keys, Arguments parsed, Platform platform) {
    portablePropertyMap(data, keys, parsed, platform).forEach(JahazielMain::setIfAbsent);
  }

  /**
   * Builds the exact property set portable boot uses. The fat jar applies it to system properties;
   * tests feed the same map to the application builder, so both paths boot identically.
   *
   * @param data the data layout
   * @param keys the session keys
   * @param parsed the dispatcher arguments
   * @return the properties, insertion ordered
   */
  static java.util.Map<String, String> portablePropertyMap(
      JarDataDir data, DevKeys.Keys keys, Arguments parsed) {
    return portablePropertyMap(data, keys, parsed, Platforms.detect(parsed.dataDir()));
  }

  static java.util.Map<String, String> portablePropertyMap(
      JarDataDir data, DevKeys.Keys keys, Arguments parsed, Platform platform) {
    java.util.Map<String, String> properties = new java.util.LinkedHashMap<>();
    properties.put("jahaziel.dist.enabled", "true");
    properties.put("jahaziel.dist.gateway-enabled", "true");
    properties.put("jahaziel.dist.gateway-port", Integer.toString(parsed.gatewayPort()));
    properties.put("jahaziel.dist.domain", parsed.domain());
    properties.put("spring.datasource.url", sqliteUrl(data, platform));
    properties.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
    properties.put(
        "spring.jpa.properties.hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
    properties.put("spring.jpa.hibernate.ddl-auto", "update");
    properties.put("spring.flyway.enabled", "false");
    properties.put("spring.datasource.hikari.maximum-pool-size", "1");
    properties.put("spring.datasource.hikari.minimum-idle", "1");
    properties.put("spring.autoconfigure.exclude", OAUTH2_CLIENT_AUTO_CONFIG);
    properties.put("DB_USER", "");
    properties.put("DB_PASSWORD", "");
    properties.put("jahaziel.storage.root", data.root().toString());
    properties.put("jahaziel.data-dir", data.root().toString());
    properties.put("jahaziel.templates.dir", data.root().resolve("templates").toString());
    if (parsed.demo()) {
      properties.put("jahaziel.auth.provider", "stub");
      properties.put("jahaziel.demo", "true");
    }
    properties.put("jahaziel.domain", parsed.domain());
    properties.put("jahaziel.jwt.private-key-file", keys.privateKey().toString());
    properties.put("jahaziel.jwt.public-key-file", keys.publicKey().toString());
    properties.put("jahaziel.jwt.issuer", "https://" + parsed.domain());
    properties.put("logging.file.name", data.logs().resolve("control-plane.log").toString());
    properties.put("server.port", Integer.toString(parsed.port()));
    properties.put("server.shutdown", "graceful");
    properties.put("spring.lifecycle.timeout-per-shutdown-phase", "20s");
    return java.util.Collections.unmodifiableMap(properties);
  }

  static String sqliteUrl(JarDataDir data, Platform platform) {
    String path = platform.toPosix(data.db().toAbsolutePath().normalize());
    return "jdbc:sqlite:" + path + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=ON";
  }

  private static void setIfAbsent(String key, String value) {
    if (System.getProperty(key) == null && System.getenv(toEnv(key)) == null) {
      System.setProperty(key, value);
    }
  }

  private static String toEnv(String key) {
    return key.toUpperCase(java.util.Locale.ROOT).replace('.', '_').replace('-', '_');
  }

  static String version() {
    String manifest =
        JahazielMain.class.getPackage() == null
            ? null
            : JahazielMain.class.getPackage().getImplementationVersion();
    return manifest == null ? "0.1.0-SNAPSHOT (development)" : manifest;
  }

  static String help() {
    return """
        Usage: java -jar jahaziel.jar [options]

        No-args with a jar-adjacent .env boots the FULL stack (API + node agent +
        gateway) on 11451. No-args without .env prints this help; run `init` first.

        Options:
          --data-dir=PATH       Data root (default: data/ next to the jar)
          --port=N              Control-plane HTTP port (default: 11451)
          --gateway-port=N      Gateway HTTP port (default: 8081)
          --domain=HOST         Public domain and token issuer (default: code.example.com)
          --demo                Stub IdP, seed demo user/workspace, ignore BASIS_*
          --help                Print this help
          --version             Print the version

        Layout: data/ holds workspaces/, ide/, logs/, tmp/, backups/, dumps/,
        plugins/, templates/, keys/, audit/, sync/, and jahaziel.db
        (SQLite WAL, created once, never cleared). Env is read from
        jar-adjacent .env (real environment wins); login needs all five
        BASIS_* vars (BASIS_AUTH_ISSUER, BASIS_REDIRECT_URI, BASIS_CLIENT_ID,
        BASIS_CLIENT_SECRET, BASIS_RESOURCE).
        """;
  }

  /** Parsed dispatcher arguments. */
  record Arguments(
      String dataDir,
      int port,
      int gatewayPort,
      String domain,
      boolean demo,
      boolean help,
      boolean version) {
    static Arguments parse(String[] args) {
      String dataDir = null;
      int port = DEFAULT_PORT;
      int gatewayPort = 8081;
      String domain = "code.example.com";
      boolean demo = false;
      boolean help = false;
      boolean version = false;
      List<String> unknown = new ArrayList<>();
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if (arg.equals("--help") || arg.equals("-h")) {
          help = true;
        } else if (arg.equals("--version")) {
          version = true;
        } else if (arg.equals("--demo")) {
          demo = true;
        } else if (arg.startsWith("--data-dir=")) {
          dataDir = arg.substring("--data-dir=".length());
        } else if (arg.equals("--data-dir")) {
          dataDir = next(args, ++i, "--data-dir");
        } else if (arg.startsWith("--port=")) {
          port = number(arg.substring("--port=".length()), "--port");
        } else if (arg.startsWith("--gateway-port=")) {
          gatewayPort = number(arg.substring("--gateway-port=".length()), "--gateway-port");
        } else if (arg.startsWith("--domain=")) {
          domain = value(arg.substring("--domain=".length()), "--domain");
        } else {
          unknown.add(arg);
        }
      }
      if (!unknown.isEmpty()) {
        throw new IllegalArgumentException("unknown argument: " + unknown.get(0));
      }
      return new Arguments(dataDir, port, gatewayPort, domain, demo, help, version);
    }

    private static String next(String[] args, int i, String flag) {
      if (i >= args.length) {
        throw new IllegalArgumentException("missing value: " + flag);
      }
      return args[i];
    }

    private static int number(String raw, String flag) {
      try {
        int value = Integer.parseInt(raw);
        if (value < 1 || value > 65535) {
          throw new IllegalArgumentException("out of range: " + flag);
        }
        return value;
      } catch (NumberFormatException bad) {
        throw new IllegalArgumentException("not a port: " + flag);
      }
    }

    private static String value(String raw, String flag) {
      if (raw.isBlank()) {
        throw new IllegalArgumentException("missing value: " + flag);
      }
      return raw;
    }
  }
}
