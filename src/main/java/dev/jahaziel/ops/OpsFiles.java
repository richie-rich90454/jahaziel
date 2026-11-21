/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Small filesystem and text helpers shared by the operations services. Every path a service writes
 * is checked with {@link #contained} before use, so state stays under the jar data directory.
 */
public final class OpsFiles {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final String[] SECRET_NAMES =
            new String[] {
                "BASIS_AUTH_ISSUER",
                "BASIS_AUTH_CLIENT_ID",
                "BASIS_AUTH_CLIENT_SECRET",
                "BASIS_AUTH_RESOURCE",
                "BASIS_AUTH_RESOURCE_SECRET",
                "BASIS_AUTH_INTERNAL_URL",
                "BASIS_AUTH_COOKIE_SECRET",
                "CURRENT_URL_ORIGIN",
                "MICROSOFT_TENANT_ID",
                "MICROSOFT_CLIENT_ID",
                "MICROSOFT_CLIENT_SECRET",
                "MICROSOFT_WEBHOOK_STATE",
                "MICROSOFT_WEBHOOK_ID"
            };

    private OpsFiles() {}

    /** Creates a directory and its parents, failing fast on I/O errors. */
    public static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot create directory " + dir, failure);
        }
    }

    /** Returns the lower-case hex SHA-256 of a byte array. */
    public static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Returns the lower-case hex SHA-256 of a regular file. */
    public static String sha256(Path file) {
        try {
            return sha256(Files.readAllBytes(file));
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read " + file, failure);
        }
    }

    /** Returns the hex representation of a byte array. */
    public static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            out.append(HEX[value >>> 4]);
            out.append(HEX[value & 0x0f]);
        }
        return out.toString();
    }

    /** Reads a UTF-8 file, returning an empty string when it does not exist. */
    public static String readText(Path file) {
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read " + file, failure);
        }
    }

    /** Writes UTF-8 text, creating parent directories. */
    public static void writeText(Path file, String text) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("cannot write " + file, failure);
        }
    }

    /** Writes UTF-8 text via a sibling temp file, then moves it into place atomically. */
    public static void writeTextAtomic(Path file, String text) {
        Path temp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(temp, text.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(
                        temp,
                        file,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            deleteIfPresent(temp);
            throw new IllegalStateException("cannot write " + file, failure);
        }
    }

    /** Returns true when candidate resolves inside root. */
    public static boolean contained(Path root, Path candidate) {
        Path base = root.toAbsolutePath().normalize();
        Path target = candidate.toAbsolutePath().normalize();
        return target.startsWith(base);
    }

    /** Returns the plain files directly inside a directory, sorted by name. */
    public static List<Path> listFiles(Path dir) {
        List<Path> result = new ArrayList<Path>();
        File folder = dir.toFile();
        File[] children = folder.listFiles();
        if (children == null) {
            return result;
        }
        List<File> files = new ArrayList<File>();
        for (int i = 0; i < children.length; i++) {
            if (children[i].isFile()) {
                files.add(children[i]);
            }
        }
        Comparator<File> byName =
                new Comparator<File>() {
                    @Override
                    public int compare(File left, File right) {
                        return left.getName().compareTo(right.getName());
                    }
                };
        java.util.Collections.sort(files, byName);
        for (int i = 0; i < files.size(); i++) {
            result.add(files.get(i).toPath());
        }
        return result;
    }

    /** Returns true for keys whose values must never reach disk. */
    public static boolean isSecretKey(String key) {
        if (key == null) {
            return false;
        }
        for (int i = 0; i < SECRET_NAMES.length; i++) {
            if (SECRET_NAMES[i].equalsIgnoreCase(key)) {
                return true;
            }
        }
        String lower = key.toLowerCase(Locale.ROOT);
        String[] markers =
                new String[] {
                    "password",
                    "passwd",
                    "secret",
                    "token",
                    "apikey",
                    "api_key",
                    "authorization",
                    "credential",
                    "privatekey",
                    "private_key",
                    "cookie",
                    "session",
                    "jwt"
                };
        for (int i = 0; i < markers.length; i++) {
            if (lower.contains(markers[i])) {
                return true;
            }
        }
        return false;
    }

    /** Escapes a string for embedding in a JSON string literal. */
    public static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') {
                out.append('\\').append(ch);
            } else if (ch == '\n') {
                out.append("\\n");
            } else if (ch == '\r') {
                out.append("\\r");
            } else if (ch == '\t') {
                out.append("\\t");
            } else if (ch < 0x20) {
                out.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(ch)));
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    /**
     * Removes credential material from free text. Keys named like secrets are replaced, bearer
     * tokens and JWTs are masked, and no raw token value survives.
     */
    public static String sanitizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String masked = text;
        masked = masked.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._\\-]+", "$1[REDACTED]");
        masked =
                masked.replaceAll(
                        "(?i)(eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+)",
                        "[REDACTED]");
        String[] lines = masked.split("\\R", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            out.append(sanitizeLine(lines[i]));
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String sanitizeLine(String line) {
        int separator = -1;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '=' || ch == ':') {
                separator = i;
                break;
            }
        }
        if (separator <= 0) {
            return line;
        }
        String key = line.substring(0, separator).trim();
        if (!isSecretKey(key)) {
            return line;
        }
        return line.substring(0, separator + 1) + " [REDACTED]";
    }

    /** Joins a map into deterministic, sanitized text lines. */
    public static String sanitizedMap(java.util.Map<String, String> values) {
        List<String> keys = new ArrayList<String>(values.keySet());
        java.util.Collections.sort(keys);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = values.get(key);
            if (isSecretKey(key)) {
                out.append(key).append("=[REDACTED]\n");
            } else {
                out.append(key).append('=').append(sanitizeText(value)).append('\n');
            }
        }
        return out.toString();
    }

    /** Sorts a string list in place. */
    public static void sortStrings(List<String> values) {
        java.util.Collections.sort(
                values,
                new Comparator<String>() {
                    @Override
                    public int compare(String left, String right) {
                        return left.compareTo(right);
                    }
                });
    }

    /** Returns the sorted keys of a string map. */
    public static List<String> sortedKeys(java.util.Map<String, String> values) {
        List<String> keys = new ArrayList<String>(values.keySet());
        sortStrings(keys);
        return keys;
    }

    /** Deletes a regular file if present. */
    public static void deleteIfPresent(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot delete " + file, failure);
        }
    }

    /** Recursively deletes a directory tree. Refuses paths outside root. */
    public static void deleteTree(Path root, Path target) {
        if (!contained(root, target)) {
            throw new IllegalArgumentException("path is outside the managed root: " + target);
        }
        File folder = target.toFile();
        if (!folder.exists()) {
            return;
        }
        File[] children = folder.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                if (children[i].isDirectory()) {
                    deleteTree(root, children[i].toPath());
                } else {
                    deleteIfPresent(children[i].toPath());
                }
            }
        }
        deleteIfPresent(target);
    }
}
