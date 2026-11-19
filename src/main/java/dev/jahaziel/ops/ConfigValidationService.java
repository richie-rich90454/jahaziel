/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fail-fast startup validation. Checks the host platform, the writability of the jar data
 * directory, catalog checksums and identity-provider settings, and returns actionable messages for
 * every failure.
 */
public final class ConfigValidationService {
    private final Path dataDir;

    /** Creates the validator rooted at the jar data directory. */
    public ConfigValidationService(Path dataDir) {
        if (dataDir == null) {
            throw new IllegalArgumentException("dataDir must not be null");
        }
        this.dataDir = dataDir.toAbsolutePath().normalize();
    }

    /** Returns the validated data directory. */
    public Path dataDir() {
        return dataDir;
    }

    /** One failed check with a message and a concrete fix. */
    public static final class Issue {
        private final String name;
        private final String message;
        private final String fix;

        /** Creates an issue. */
        public Issue(String name, String message, String fix) {
            this.name = name;
            this.message = message;
            this.fix = fix;
        }

        /** Returns the check name. */
        public String name() {
            return name;
        }

        /** Returns what failed. */
        public String message() {
            return message;
        }

        /** Returns the actionable fix. */
        public String fix() {
            return fix;
        }

        /** Returns a single actionable line. */
        public String describe() {
            return name + ": " + message + " (fix: " + fix + ")";
        }
    }

    /** Thrown at boot when one or more checks fail. */
    public static final class ConfigValidationException extends RuntimeException {
        private final List<Issue> issues;

        /** Creates the exception from the failed checks. */
        public ConfigValidationException(List<Issue> issues) {
            super(firstMessage(issues));
            this.issues = new ArrayList<Issue>(issues);
        }

        /** Returns all failed checks. */
        public List<Issue> issues() {
            return new ArrayList<Issue>(issues);
        }

        private static String firstMessage(List<Issue> issues) {
            if (issues.isEmpty()) {
                return "configuration validation failed";
            }
            return issues.get(0).describe();
        }
    }

    /** Identity-provider settings validated at startup. */
    public static final class IdpConfig {
        private final String issuer;
        private final String clientId;
        private final String clientSecret;

        /** Creates an IdP configuration. */
        public IdpConfig(String issuer, String clientId, String clientSecret) {
            this.issuer = issuer;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        /** Returns the issuer. */
        public String issuer() {
            return issuer;
        }

        /** Returns the client id. */
        public String clientId() {
            return clientId;
        }

        /** Returns the client secret, never logged. */
        public String clientSecret() {
            return clientSecret;
        }
    }

    /**
     * Runs every check and returns all failures. An empty list means startup may continue.
     *
     * @param osName the operating system name
     * @param osArch the processor architecture
     * @param catalog expected digests keyed by artifact file name
     * @param catalogDir the directory holding the catalog artifacts
     * @param idp the identity-provider settings
     * @return the failed checks, in stable order
     */
    public List<Issue> check(
            String osName,
            String osArch,
            Map<String, String> catalog,
            Path catalogDir,
            IdpConfig idp) {
        List<Issue> issues = new ArrayList<Issue>();
        checkPlatform(osName, osArch, issues);
        checkDataDir(issues);
        checkCatalog(catalog, catalogDir, issues);
        checkIdp(idp, issues);
        return issues;
    }

    /** Runs every check, throwing on the first actionable failure. */
    public void validate(
            String osName,
            String osArch,
            Map<String, String> catalog,
            Path catalogDir,
            IdpConfig idp) {
        List<Issue> issues = check(osName, osArch, catalog, catalogDir, idp);
        if (!issues.isEmpty()) {
            throw new ConfigValidationException(issues);
        }
    }

    private static void checkPlatform(String osName, String osArch, List<Issue> issues) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
        boolean linux = os.contains("linux");
        boolean x64 = arch.contains("x86_64") || arch.contains("amd64") || arch.contains("x64");
        boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
        if (!linux) {
            issues.add(
                    new Issue(
                            "platform.os",
                            "unsupported operating system '" + osName + "' for a server deployment",
                            "run on Linux x64 or arm64"));
            return;
        }
        if (!x64 && !arm64) {
            issues.add(
                    new Issue(
                            "platform.arch",
                            "unsupported architecture '" + osArch + "' on Linux",
                            "use a Linux x64 or arm64 host"));
        }
    }

    private void checkDataDir(List<Issue> issues) {
        Path probe = dataDir.resolve(".ops-write-probe");
        try {
            Files.createDirectories(dataDir);
            Files.write(probe, "probe".getBytes(StandardCharsets.UTF_8));
            Files.deleteIfExists(probe);
        } catch (IOException failure) {
            issues.add(
                    new Issue(
                            "dataDir",
                            "data directory "
                                    + dataDir
                                    + " is not writable: "
                                    + failure.getMessage(),
                            "fix ownership and permissions on " + dataDir));
        }
    }

    private static void checkCatalog(
            Map<String, String> catalog, Path catalogDir, List<Issue> issues) {
        if (catalog == null || catalog.isEmpty()) {
            return;
        }
        java.util.Set<String> names = catalog.keySet();
        List<String> sorted = new ArrayList<String>(names);
        OpsFiles.sortStrings(sorted);
        for (int i = 0; i < sorted.size(); i++) {
            String name = sorted.get(i);
            Path artifact = catalogDir.resolve(name).normalize();
            if (!OpsFiles.contained(catalogDir, artifact)
                    || !java.nio.file.Files.isRegularFile(artifact)) {
                issues.add(
                        new Issue(
                                "catalog." + name,
                                "catalog artifact " + name + " is missing from " + catalogDir,
                                "restore " + name + " into " + catalogDir));
                continue;
            }
            String expected = catalog.get(name);
            String actual = OpsFiles.sha256(artifact);
            if (expected == null || !actual.equalsIgnoreCase(expected.trim())) {
                issues.add(
                        new Issue(
                                "catalog." + name,
                                "catalog artifact " + name + " checksum mismatch",
                                "reinstall "
                                        + name
                                        + "; expected "
                                        + expected
                                        + ", found "
                                        + actual));
            }
        }
    }

    private static void checkIdp(IdpConfig idp, List<Issue> issues) {
        if (idp == null) {
            issues.add(
                    new Issue(
                            "idp",
                            "identity provider is not configured",
                            "set the OIDC issuer, client id and client secret"));
            return;
        }
        String issuer = idp.issuer() == null ? "" : idp.issuer().trim();
        if (!issuer.startsWith("https://") || issuer.length() <= "https://".length()) {
            issues.add(
                    new Issue(
                            "idp.issuer",
                            "identity-provider issuer must be an https URL",
                            "set JAHAZIEL_IDP_ISSUER to an https URL"));
        }
        if (idp.clientId() == null || idp.clientId().trim().isEmpty()) {
            issues.add(
                    new Issue(
                            "idp.clientId",
                            "identity-provider client id is blank",
                            "set JAHAZIEL_IDP_CLIENT_ID"));
        }
        String secret = idp.clientSecret() == null ? "" : idp.clientSecret().trim();
        if (secret.isEmpty() || secret.contains("change") || secret.contains("example")) {
            issues.add(
                    new Issue(
                            "idp.clientSecret",
                            "identity-provider client secret is missing or a placeholder",
                            "set JAHAZIEL_IDP_CLIENT_SECRET to a real secret"));
        }
    }
}
