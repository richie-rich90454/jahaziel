/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import dev.jahaziel.JahazielApplication;
import dev.jahaziel.gateway.GatewayMain;
import dev.jahaziel.nodeagent.audit.AuditSpool;
import dev.jahaziel.nodeagent.service.LocalProcessBackend;
import dev.jahaziel.nodeagent.service.NodeAgentService;
import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.nodeagent.storage.NioCommandRunner;
import dev.jahaziel.nodeagent.sync.SyncServer;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import org.springframework.boot.SpringApplication;

/**
 * Ultra fat jar dispatcher. Bare {@code java -jar jahaziel.jar} prints help; every run goes through
 * {@code --run}. Portable modes default to the jar-adjacent {@code data/} directory and a SQLite
 * file that is created once and never cleared.
 */
public final class JahazielMain {
  /**
   * Boot OAuth2 client auto-configs, old and new locations. Excluded in portable mode so no
   * identity provider is contacted: session auth is bearer JWT only.
   */
  static final String OAUTH2_CLIENT_AUTO_CONFIG =
      "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration,"
          + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration";

  private JahazielMain() {}

  /**
   * Starts the dispatcher.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    Objects.requireNonNull(args, "args");
    try {
      Arguments parsed = Arguments.parse(args);
      if (parsed.help() || args.length == 0) {
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
    JarDataDir data = JarDataDir.resolve(parsed.dataDir());
    DevKeys.Keys keys = DevKeys.ensure(data.keys());
    return switch (parsed.mode()) {
      case NODE_AGENT -> runNodeAgent(data, out);
      case GATEWAY -> {
        GatewayMain.main(
            new String[] {
              "--port",
              Integer.toString(parsed.gatewayPort()),
              "--domain",
              parsed.domain(),
              "--jwt-pub",
              keys.publicKey().toString()
            });
        yield 0;
      }
      case CONTROL_PLANE -> {
        portableProperties(data, keys, parsed, false);
        SpringApplication.run(JahazielApplication.class);
        out.println("jahaziel control-plane running on :" + parsed.port() + " data=" + data.root());
        new CountDownLatch(1).await();
        yield 0;
      }
      case ALL -> {
        portableProperties(data, keys, parsed, true);
        SpringApplication.run(JahazielApplication.class);
        out.println(
            "jahaziel all running: api=:"
                + parsed.port()
                + " gateway=:"
                + parsed.gatewayPort()
                + " data="
                + data.root());
        new CountDownLatch(1).await();
        yield 0;
      }
    };
  }

  private static int runNodeAgent(JarDataDir data, PrintStream out) throws Exception {
    var storage = new FolderStorage(new NioCommandRunner(), data.root().toString());
    var sync = new SyncServer(SyncServer.LOOPBACK_PORT, data.sync());
    sync.start();
    var backend = new LocalProcessBackend(storage);
    var service = new NodeAgentService(backend, storage, new AuditSpool(data.audit()), sync);
    out.println("jahaziel node-agent (local) ready in " + data.root() + ": " + service);
    new CountDownLatch(1).await();
    return 0;
  }

  static void portableProperties(
      JarDataDir data, DevKeys.Keys keys, Arguments parsed, boolean gateway) {
    portablePropertyMap(data, keys, parsed, gateway).forEach(JahazielMain::setIfAbsent);
  }

  /**
   * Builds the exact property set portable modes boot with. The fat jar applies it to system
   * properties; tests feed the same map to the application builder, so both paths boot identically.
   *
   * @param data the data layout
   * @param keys the session keys
   * @param parsed the dispatcher arguments
   * @param gateway whether the gateway runs beside the API
   * @return the properties, insertion ordered
   */
  static java.util.Map<String, String> portablePropertyMap(
      JarDataDir data, DevKeys.Keys keys, Arguments parsed, boolean gateway) {
    java.util.Map<String, String> properties = new java.util.LinkedHashMap<>();
    properties.put("jahaziel.dist.enabled", "true");
    properties.put("jahaziel.dist.gateway-enabled", Boolean.toString(gateway));
    properties.put("jahaziel.dist.gateway-port", Integer.toString(parsed.gatewayPort()));
    properties.put("jahaziel.dist.domain", parsed.domain());
    properties.put("spring.datasource.url", "jdbc:sqlite:" + data.db());
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
    properties.put("jahaziel.jwt.private-key-file", keys.privateKey().toString());
    properties.put("jahaziel.jwt.public-key-file", keys.publicKey().toString());
    properties.put("jahaziel.jwt.issuer", "https://" + parsed.domain());
    properties.put("logging.file.name", data.logs().resolve("control-plane.log").toString());
    properties.put("server.port", Integer.toString(parsed.port()));
    return java.util.Collections.unmodifiableMap(properties);
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
        Usage: java -jar jahaziel.jar --run=MODE [options]

        Bare `java -jar jahaziel.jar` prints this help and runs nothing.

        Modes:
          --run=all             Control plane, node agent, and gateway in one process
          --run=control-plane   API and orchestration only
          --run=node-agent      Local workspace backend and file sync only
          --run=gateway         Host routing and session verification only

        Options:
          --data-dir=PATH       Data root (default: data/ next to the jar)
          --port=N              Control-plane HTTP port (default: 8080)
          --gateway-port=N      Gateway HTTP port (default: 8081)
          --domain=HOST         Public domain and token issuer (default: code.example.com)
          --help                Print this help
          --version             Print the version

        Layout: data/ holds workspaces/, images/, jail/, audit/, sync/, keys/,
        logs/, and jahaziel.db (SQLite, created once, never cleared).
        """;
  }

  /** Parsed dispatcher arguments. */
  record Arguments(
      Mode mode,
      String dataDir,
      int port,
      int gatewayPort,
      String domain,
      boolean help,
      boolean version) {
    static Arguments parse(String[] args) {
      Mode mode = null;
      String dataDir = null;
      int port = 8080;
      int gatewayPort = 8081;
      String domain = "code.example.com";
      boolean help = false;
      boolean version = false;
      List<String> unknown = new ArrayList<>();
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if (arg.equals("--help") || arg.equals("-h")) {
          help = true;
        } else if (arg.equals("--version")) {
          version = true;
        } else if (arg.startsWith("--run=")) {
          mode = mode(arg.substring("--run=".length()));
        } else if (arg.equals("--run")) {
          mode = mode(next(args, ++i, "--run"));
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
      if (help || version || args.length == 0) {
        return new Arguments(mode, dataDir, port, gatewayPort, domain, help, version);
      }
      if (mode == null) {
        throw new IllegalArgumentException("nothing to run: pass --run=MODE");
      }
      return new Arguments(mode, dataDir, port, gatewayPort, domain, false, false);
    }

    private static Mode mode(String raw) {
      try {
        return Mode.valueOf(raw.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
      } catch (IllegalArgumentException bad) {
        throw new IllegalArgumentException(
            "unknown mode: " + raw + " (all, control-plane, node-agent, gateway)");
      }
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

  /** Runnable roles. */
  enum Mode {
    /** Everything in one process. */
    ALL,
    /** API and orchestration. */
    CONTROL_PLANE,
    /** Local workspace backend and file sync. */
    NODE_AGENT,
    /** Host routing and session verification. */
    GATEWAY
  }
}
