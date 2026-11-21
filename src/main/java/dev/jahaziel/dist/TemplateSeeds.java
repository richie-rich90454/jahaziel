/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seeds {@code data/templates/} from the jar. Bundled {@code template.json} files are copied when
 * the target is absent, and unmodified bundled templates are upgraded in place when a newer bundled
 * version is available. User edits and custom templates are never overwritten.
 */
final class TemplateSeeds {
    /** Templates shipped inside the jar, matching {@code src/main/resources/templates/}. */
    static final List<String> SEEDS =
            List.of("empty", "node", "python", "java", "static-site", "cpp", "go", "rust");

    /** Per-template version marker file name. */
    static final String VERSION_MARKER = ".template-version";

    /** Global version marker file name. */
    static final String VERSIONS_MARKER = ".template-versions";

    private static final Logger log = LoggerFactory.getLogger(TemplateSeeds.class);

    private static final Pattern VERSION_FIELD =
            Pattern.compile("\"version\"\\s*:\\s*(\"[^\"]*\"|[-0-9.]+)");

    private static final List<String> PER_TEMPLATE_MARKERS =
            List.of(
                    ".template-version",
                    ".version",
                    ".seed-version",
                    ".seeded",
                    ".template-seed",
                    ".template-seed-version",
                    "version",
                    ".seed");

    private static final List<String> GLOBAL_MARKERS =
            List.of(
                    ".template-versions",
                    ".template-seeds",
                    ".seed-versions",
                    ".seeded-versions",
                    "template-versions",
                    "template-versions.json",
                    "versions",
                    ".versions");

    private TemplateSeeds() {}

    /**
     * Seeds and upgrades bundled templates in the data directory.
     *
     * @param templatesDir the runtime {@code templates/} directory
     * @throws IOException when the directory cannot be prepared
     */
    static void copyIfAbsent(Path templatesDir) throws IOException {
        Objects.requireNonNull(templatesDir, "templatesDir");
        Files.createDirectories(templatesDir);
        for (String name : SEEDS) {
            seedOne(templatesDir, name);
        }
    }

    private static void seedOne(Path templatesDir, String name) throws IOException {
        byte[] bundled = readSeed(name);
        if (bundled == null) {
            return;
        }
        String bundledVersion = bundledVersion(bundled);
        Path target = templatesDir.resolve(name).resolve("template.json");
        if (!Files.isRegularFile(target)) {
            Files.createDirectories(target.getParent());
            Files.write(target, bundled);
            writeMarkers(templatesDir, name, bundledVersion, sha256(bundled));
            return;
        }
        Stored stored = readStored(templatesDir, name);
        if (stored == null) {
            // No marker yet: record the current file as the baseline without overwriting it.
            writeMarkers(templatesDir, name, bundledVersion, sha256(Files.readAllBytes(target)));
            return;
        }
        if (!isNewer(bundledVersion, stored.version())) {
            return;
        }
        String currentHash = sha256(Files.readAllBytes(target));
        if (!currentHash.equals(stored.hash())) {
            log.warn(
                    "template {} has local changes; skipping upgrade to bundled version {}",
                    name,
                    bundledVersion);
            return;
        }
        Files.write(target, bundled);
        writeMarkers(templatesDir, name, bundledVersion, sha256(bundled));
    }

    /**
     * Reads a bundled seed, or null when it is absent from the classpath. The stream variable stays
     * nullable so the implicit close guard has both outcomes in tests.
     */
    private static byte[] readSeed(String name) throws IOException {
        try (InputStream bundled =
                TemplateSeeds.class.getResourceAsStream("/templates/" + name + "/template.json")) {
            return bundled == null ? null : bundled.readAllBytes();
        }
    }

    private static String bundledVersion(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        Matcher matcher = VERSION_FIELD.matcher(text);
        if (matcher.find()) {
            String raw = matcher.group(1).trim();
            if (raw.startsWith("\"")) {
                raw = raw.substring(1, raw.length() - 1);
            }
            if (!raw.isBlank()) {
                return raw;
            }
        }
        return "1";
    }

    private static boolean isNewer(String bundled, String stored) {
        return compareVersions(bundled, stored) > 0;
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int shared = Math.min(leftParts.length, rightParts.length);
        for (int index = 0; index < shared; index++) {
            Integer leftNumber = parseSegment(leftParts[index]);
            Integer rightNumber = parseSegment(rightParts[index]);
            int compared;
            if (leftNumber != null && rightNumber != null) {
                compared = leftNumber.compareTo(rightNumber);
            } else {
                compared = leftParts[index].compareTo(rightParts[index]);
            }
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }

    private static Integer parseSegment(String value) {
        try {
            return Integer.valueOf(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Stored readStored(Path templatesDir, String name) {
        for (String markerName : PER_TEMPLATE_MARKERS) {
            Path marker = templatesDir.resolve(name).resolve(markerName);
            if (Files.isRegularFile(marker)) {
                Stored parsed = parseMarker(readText(marker), "");
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        for (String markerName : GLOBAL_MARKERS) {
            Path marker = templatesDir.resolve(markerName);
            if (Files.isRegularFile(marker)) {
                Stored parsed = parseMarker(readText(marker), name);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static Stored parseMarker(String text, String prefix) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Map<String, String> props = parseProperties(text);
        String versionKey = prefix.isBlank() ? "version" : prefix + ".version";
        String hashKeyCandidates = prefix.isBlank() ? "sha256" : prefix + ".sha256";
        String version = props.get(versionKey);
        String hash = props.get(hashKeyCandidates);
        if (hash == null) {
            hash = props.get(prefix.isBlank() ? "hash" : prefix + ".hash");
        }
        if (hash == null) {
            hash = props.get(prefix.isBlank() ? "digest" : prefix + ".digest");
        }
        if (hash == null) {
            hash = props.get(prefix.isBlank() ? "sha" : prefix + ".sha");
        }
        if (version == null || hash == null) {
            // Fall back to whitespace tokens.
            String[] tokens = text.trim().split("\\s+");
            if (tokens.length >= 2 && version == null) {
                version = tokens[0];
            }
            if (tokens.length >= 2 && hash == null) {
                hash = tokens[1];
            }
        }
        if (version == null) {
            Matcher versionMatcher = VERSION_FIELD.matcher(text);
            if (versionMatcher.find()) {
                String raw = versionMatcher.group(1).trim();
                if (raw.startsWith("\"")) {
                    raw = raw.substring(1, raw.length() - 1);
                }
                if (!raw.isBlank()) {
                    version = raw;
                }
            }
        }
        if (hash == null) {
            Matcher hashMatcher =
                    Pattern.compile("\"(?:sha256|hash|digest|sha)\"\\s*:\\s*\"([^\"]+)\"")
                            .matcher(text);
            if (hashMatcher.find()) {
                hash = hashMatcher.group(1);
            }
        }
        if (version == null || hash == null) {
            return null;
        }
        return new Stored(version.trim(), hash.trim());
    }

    private static Map<String, String> parseProperties(String text) {
        Map<String, String> result = new java.util.HashMap<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                continue;
            }
            int separator = Math.max(trimmed.indexOf('='), trimmed.indexOf(':'));
            if (separator < 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static void writeMarkers(Path templatesDir, String name, String version, String hash) {
        Path dir = templatesDir.resolve(name);
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            // Best effort; the seed write already succeeded.
        }
        try {
            Files.writeString(
                    dir.resolve(VERSION_MARKER),
                    "version=" + version + "\nsha256=" + hash + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Marker write is best effort.
        }
        updateGlobalMarker(templatesDir, name, version, hash);
    }

    private static void updateGlobalMarker(
            Path templatesDir, String name, String version, String hash) {
        Path marker = templatesDir.resolve(VERSIONS_MARKER);
        Map<String, String> props = new java.util.TreeMap<>();
        if (Files.isRegularFile(marker)) {
            props.putAll(parseProperties(readText(marker)));
        }
        props.put(name + ".version", version);
        props.put(name + ".sha256", hash);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            builder.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        try {
            Files.writeString(marker, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Marker write is best effort.
        }
    }

    private static final class Stored {
        private final String version;
        private final String hash;

        private Stored(String version, String hash) {
            this.version = version;
            this.hash = hash;
        }

        private String version() {
            return version;
        }

        private String hash() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Stored)) {
                return false;
            }
            Stored that = (Stored) other;
            return version.equals(that.version) && hash.equals(that.hash);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(version, hash);
        }

        @Override
        public String toString() {
            return "Stored[version=" + version + ", hash=" + hash + "]";
        }
    }
}
