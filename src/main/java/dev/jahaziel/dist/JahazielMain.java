/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import dev.jahaziel.JahazielApplication;
import dev.jahaziel.platform.DotEnv;
import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

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
                    "data",
                    "logs",
                    "tmp",
                    "workspaces",
                    "backups",
                    "dumps",
                    "plugins",
                    "templates");

    private JahazielMain() {}

    static int run(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args");
        try {
            String[] normalized = dev.jahaziel.cli.Cli.normalize(args);
            int cli = dev.jahaziel.cli.Cli.dispatch(normalized, out, err);
            if (cli != dev.jahaziel.cli.Cli.PASS_THROUGH) {
                return cli;
            }
            Arguments parsed = Arguments.parse(normalized, mergedEnv());
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

    /**
     * Returns the effective environment for boot knobs: the jar-adjacent {@code .env} under the
     * real environment. Missing files yield just the real environment.
     */
    static Map<String, String> mergedEnv() {
        Path envFile = Platforms.detect(null).jarDir().resolve(".env");
        return DotEnv.withRealEnvWins(DotEnv.parse(envFile), System.getenv());
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
        FileChannel dataLockChannel =
                FileChannel.open(
                        data.root().resolve(".lock"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
        FileLock dataLock = tryDataLock(dataLockChannel);
        if (dataLock == null) {
            dataLockChannel.close();
            out.println("jahaziel: data directory is locked by another instance: " + data.root());
            return 1;
        }
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
            dataLock.release();
            dataLockChannel.close();
        }
    }

    /**
     * Takes the data-directory lock without blocking. Null when another process holds it (or this
     * JVM already does); the caller refuses to boot so two servers never share one SQLite file.
     *
     * @param channel the open lock channel
     * @return the held lock, or null when it is taken
     */
    static FileLock tryDataLock(FileChannel channel) throws java.io.IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException alreadyHeld) {
            return null;
        }
    }

    static void ensurePortableDirs(Path root) throws java.io.IOException {
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("logs"));
        Files.createDirectories(root.resolve("tmp"));
        Files.createDirectories(root.resolve("workspaces"));
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
        List<String> keys = new ArrayList<>(DotEnv.BASIS_ALIAS_VARS);
        keys.addAll(DotEnv.BASIS_VARS);
        for (String key : keys) {
            String property = basisProperty(key);
            if (property == null) {
                continue;
            }
            String value = basisValue(key, fileEnv);
            if (value != null && !value.isBlank() && System.getProperty(property) == null) {
                System.setProperty(property, value);
            }
        }
        String origin = basisValue("CURRENT_URL_ORIGIN", fileEnv);
        if (origin != null
                && !origin.isBlank()
                && System.getProperty("basis.redirect-uri") == null) {
            String base = origin.trim();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            System.setProperty("basis.redirect-uri", base + "/api/auth/basis/callback");
        }
    }

    private static String basisValue(String key, Map<String, String> fileEnv) {
        String value = System.getenv(key);
        if (value == null) {
            value = System.getProperty(key);
        }
        if (value == null) {
            value = fileEnv.get(key);
        }
        return value;
    }

    static String basisProperty(String envKey) {
        switch (envKey) {
            case "BASIS_AUTH_ISSUER":
                return "basis.auth-issuer";
            case "BASIS_REDIRECT_URI":
                return "basis.redirect-uri";
            case "BASIS_CLIENT_ID":
                return "basis.client-id";
            case "BASIS_CLIENT_SECRET":
                return "basis.client-secret";
            case "BASIS_RESOURCE":
                return "basis.resource";
            case "BASIS_AUTH_CLIENT_ID":
                return "basis.client-id";
            case "BASIS_AUTH_CLIENT_SECRET":
                return "basis.client-secret";
            case "BASIS_AUTH_RESOURCE":
                return "basis.resource";
            case "BASIS_AUTH_RESOURCE_SECRET":
                return "basis.resource-secret";
            case "BASIS_AUTH_INTERNAL_URL":
                return "basis.internal-url";
            case "BASIS_AUTH_COOKIE_SECRET":
                return "basis.cookie-secret";
            case "CURRENT_URL_ORIGIN":
                return "basis.current-url-origin";
            default:
                return null;
        }
    }

    static List<String> missingBasisVars(Map<String, String> fileEnv) {
        Map<String, String> effective = new java.util.LinkedHashMap<>(fileEnv);
        for (int index = 0; index < DotEnv.BASIS_ALIAS_VARS.size(); index++) {
            String alias = DotEnv.BASIS_ALIAS_VARS.get(index);
            String value = basisValue(alias, Map.of());
            if (value != null && !value.isBlank()) {
                effective.put(alias, value);
            }
        }
        for (int index = 0; index < DotEnv.BASIS_VARS.size(); index++) {
            String key = DotEnv.BASIS_VARS.get(index);
            String value = basisValue(key, Map.of());
            if (value != null && !value.isBlank()) {
                effective.put(key, value);
            }
        }
        effective.putAll(System.getenv());
        return DotEnv.missingBasisVars(effective);
    }

    private static void reportBasisVars(
            Map<String, String> fileEnv, PrintStream out, boolean demo) {
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
        for (Map.Entry<String, String> entry :
                portablePropertyMap(data, keys, parsed, platform).entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (parsed.demo() && key.equals("server.address")) {
                // Demo authentication grants a fixed admin identity, so never let an inherited
                // environment or system property expose that mode on a network interface.
                System.setProperty(key, value);
            } else {
                setIfAbsent(key, value);
            }
        }
    }

    /**
     * Builds the exact property set portable boot uses. The fat jar applies it to system
     * properties; tests feed the same map to the application builder, so both paths boot
     * identically.
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
        // The standalone socket gateway is opt-in legacy: workspace traffic is served on the
        // application port by the relay filter, so a second listener stays off unless asked for.
        properties.put("jahaziel.dist.gateway-enabled", "false");
        properties.put("jahaziel.dist.gateway-port", Integer.toString(parsed.gatewayPort()));
        properties.put("jahaziel.dist.domain", parsed.domain());
        properties.put("spring.datasource.url", sqliteUrl(data, platform));
        properties.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
        properties.put(
                "spring.jpa.properties.hibernate.dialect",
                "org.hibernate.community.dialect.SQLiteDialect");
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
            properties.put("server.address", "127.0.0.1");
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
        return versionOf(JahazielMain.class.getPackage());
    }

    /**
     * Renders a package version, falling back when running unpacked. Split out so tests cover both
     * sides without a manifest build.
     *
     * @param pkg the package, null when unknown
     * @return the version or the development fallback
     */
    static String versionOf(Package pkg) {
        String manifest = pkg == null ? null : pkg.getImplementationVersion();
        return manifest == null ? "0.1.0-SNAPSHOT (development)" : manifest;
    }

    static String help() {
        return "Usage: java -jar jahaziel.jar [options]\n"
                + "\n"
                + "No-args with a jar-adjacent .env boots the FULL stack (API + node agent +\n"
                + "gateway) on 11451. No-args without .env prints this help; run `init` first.\n"
                + "\n"
                + "Options:\n"
                + "  --data-dir=PATH       Data root (default: data/ next to the jar)\n"
                + "  --port=N              Control-plane HTTP port (default: 11451, or PORT from .env)\n"
                + "  --gateway-port=N      Legacy standalone gateway socket (default: off;\n"
                + "                        workspace traffic relays on the API port instead;\n"
                + "                        or JAHAZIEL_GATEWAY_PORT from .env)\n"
                + "  --domain=HOST         Public domain and token issuer (default: localhost,\n"
                + "                        or JAHAZIEL_DOMAIN from .env)\n"
                + "  --demo                Stub IdP, seed demo user/workspace, ignore BASIS_*\n"
                + "  --help                Print this help\n"
                + "  --version             Print the version\n"
                + "\n"
                + "Layout: data/ holds workspaces/, language-packs/, logs/, tmp/, backups/,\n"
                + "dumps/, plugins/, templates/, keys/, audit/, sync/, and jahaziel.db\n"
                + "(SQLite WAL, created once, never cleared). Env is read from\n"
                + "jar-adjacent .env (real environment wins); basis-auth login needs\n"
                + "BASIS_AUTH_ISSUER, BASIS_AUTH_CLIENT_ID, BASIS_AUTH_CLIENT_SECRET,\n"
                + "BASIS_AUTH_RESOURCE, and CURRENT_URL_ORIGIN. Legacy BASIS_* names\n"
                + "(BASIS_REDIRECT_URI, BASIS_CLIENT_ID, BASIS_CLIENT_SECRET, BASIS_RESOURCE)\n"
                + "remain accepted aliases.\n";
    }

    /** Parsed dispatcher arguments. */
    static final class Arguments {
        private final String dataDir;
        private final int port;
        private final int gatewayPort;
        private final String domain;
        private final boolean demo;
        private final boolean help;
        private final boolean version;

        Arguments(
                String dataDir,
                int port,
                int gatewayPort,
                String domain,
                boolean demo,
                boolean help,
                boolean version) {
            this.dataDir = dataDir;
            this.port = port;
            this.gatewayPort = gatewayPort;
            this.domain = domain;
            this.demo = demo;
            this.help = help;
            this.version = version;
        }

        String dataDir() {
            return dataDir;
        }

        int port() {
            return port;
        }

        int gatewayPort() {
            return gatewayPort;
        }

        String domain() {
            return domain;
        }

        boolean demo() {
            return demo;
        }

        boolean help() {
            return help;
        }

        boolean version() {
            return version;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Arguments)) {
                return false;
            }
            Arguments that = (Arguments) other;
            return port == that.port
                    && gatewayPort == that.gatewayPort
                    && demo == that.demo
                    && help == that.help
                    && version == that.version
                    && Objects.equals(dataDir, that.dataDir)
                    && Objects.equals(domain, that.domain);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dataDir, port, gatewayPort, domain, demo, help, version);
        }

        @Override
        public String toString() {
            return "Arguments[dataDir="
                    + dataDir
                    + ", port="
                    + port
                    + ", gatewayPort="
                    + gatewayPort
                    + ", domain="
                    + domain
                    + ", demo="
                    + demo
                    + ", help="
                    + help
                    + ", version="
                    + version
                    + "]";
        }

        static Arguments parse(String[] args) {
            return parse(args, Map.of());
        }

        /**
         * Parses flags with environment fallback, so every boot knob is settable from {@code .env}.
         * An explicit flag always wins; otherwise the matching variable (real environment first,
         * then {@code .env}) applies; otherwise the default holds.
         *
         * @param args the command line
         * @param env the effective environment, {@code .env} under the real environment
         * @return the arguments
         */
        static Arguments parse(String[] args, Map<String, String> env) {
            String dataDir = null;
            Integer port = null;
            Integer gatewayPort = null;
            String domain = null;
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
                    gatewayPort =
                            number(arg.substring("--gateway-port=".length()), "--gateway-port");
                } else if (arg.startsWith("--domain=")) {
                    domain = value(arg.substring("--domain=".length()), "--domain");
                } else {
                    unknown.add(arg);
                }
            }
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("unknown argument: " + unknown.get(0));
            }
            return new Arguments(
                    dataDir,
                    port != null ? port : portFrom(env.get("PORT"), DEFAULT_PORT),
                    gatewayPort != null
                            ? gatewayPort
                            : portFrom(env.get("JAHAZIEL_GATEWAY_PORT"), 8081),
                    domain != null ? domain : domainFrom(env.get("JAHAZIEL_DOMAIN")),
                    demo,
                    help,
                    version);
        }

        private static int portFrom(String raw, int fallback) {
            if (raw == null) {
                return fallback;
            }
            try {
                int value = Integer.parseInt(raw.strip());
                if (value >= 1 && value <= 65535) {
                    return value;
                }
            } catch (NumberFormatException notANumber) {
                // Fall through to the default below.
            }
            return fallback;
        }

        private static String domainFrom(String raw) {
            if (raw == null || raw.isBlank()) {
                return "localhost";
            }
            return raw.strip();
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
