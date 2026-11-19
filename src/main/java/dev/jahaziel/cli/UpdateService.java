/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Staged jar updates. A candidate jar is copied under {@code <data>/update/<version>/} and checked
 * against its SHA256SUMS file. Nothing touches the running jar while staging. The swap happens on
 * the next start, when the previous jar is kept under {@code <data>/update/rollback/<version>/} so
 * it can be restored with {@link #rollback(PrintStream)}.
 */
public final class UpdateService {
    /** Root of all update state, inside the jar data directory. */
    public static final String UPDATE_DIR = "update";

    /** Marker naming the version staged for the next start. */
    public static final String PENDING = "pending";

    /** Marker naming the version to restore on rollback. */
    public static final String APPLIED = "applied";

    /** Directory holding the previous running jar. */
    public static final String ROLLBACK = "rollback";

    /** Name of the checksum manifest. */
    public static final String SUMS = "SHA256SUMS";

    /** Name of the staged and running jar. */
    public static final String JAR_NAME = "jahaziel.jar";

    private final Path jarDir;
    private final Path dataDir;
    private final Path updateRoot;

    /** Creates an update service using the jar and data directories. */
    public UpdateService(Path jarDir, Path dataDir) {
        if (jarDir == null || dataDir == null) {
            throw new IllegalArgumentException("jarDir and dataDir are required");
        }
        this.jarDir = jarDir.toAbsolutePath().normalize();
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.updateRoot = this.dataDir.resolve(UPDATE_DIR);
    }

    /** Outcome of a staging operation. */
    public static final class StageResult {
        private final boolean ok;
        private final String message;
        private final String version;

        /** Creates a result. */
        public StageResult(boolean ok, String message, String version) {
            this.ok = ok;
            this.message = message;
            this.version = version;
        }

        /** Returns whether staging succeeded (or what a dry run would do). */
        public boolean ok() {
            return ok;
        }

        /** Returns the human-readable outcome. */
        public String message() {
            return message;
        }

        /** Returns the staged version. */
        public String version() {
            return version;
        }
    }

    /**
     * Stages a candidate jar. With {@code dryRun} no file is created or replaced and the returned
     * message describes the actions that would be taken.
     *
     * @param candidate the jar to stage
     * @param sumsFile an explicit SHA256SUMS file, or null to use the sibling file
     * @param version an explicit version, or null to read the jar manifest
     * @param dryRun when true, only report the plan
     * @return the staging result
     * @throws IOException when the candidate cannot be read or copied
     */
    public StageResult stage(Path candidate, Path sumsFile, String version, boolean dryRun)
            throws IOException {
        if (candidate == null || !Files.isRegularFile(candidate)) {
            throw new IllegalArgumentException("candidate jar must be an existing file");
        }
        String chosen = version;
        if (chosen == null || chosen.trim().isEmpty()) {
            chosen = Cli.jarVersion(candidate);
        }
        chosen = safeVersion(chosen);
        Path sums = sumsFile;
        if (sums == null) {
            Path sibling = candidate.resolveSibling(SUMS);
            if (Files.isRegularFile(sibling)) {
                sums = sibling;
            }
        }
        Path stageDir = updateRoot.resolve(chosen);
        if (dryRun) {
            return new StageResult(
                    true,
                    "dry-run: would stage "
                            + candidate
                            + " as "
                            + chosen
                            + " in "
                            + stageDir
                            + (sums == null
                                    ? " (generating " + SUMS + ")"
                                    : " (verifying " + sums + ")")
                            + "; swap runs on next start",
                    chosen);
        }
        Files.createDirectories(stageDir);
        Path staged = stageDir.resolve(JAR_NAME);
        Files.copy(candidate, staged, StandardCopyOption.REPLACE_EXISTING);
        if (sums != null) {
            if (!verify(staged, sums)) {
                deleteQuietly(staged);
                return new StageResult(
                        false,
                        "checksum verification failed for " + candidate + " against " + sums,
                        chosen);
            }
            Files.copy(sums, stageDir.resolve(SUMS), StandardCopyOption.REPLACE_EXISTING);
        } else {
            writeGeneratedSums(staged, stageDir.resolve(SUMS));
        }
        writeMarker(PENDING, chosen);
        return new StageResult(
                true, "staged " + chosen + " in " + stageDir + "; swap runs on next start", chosen);
    }

    /**
     * Returns true when the jar matches an entry in a SHA256SUMS file. The manifest may name the
     * jar {@code jahaziel.jar} or use the candidate's own file name.
     *
     * @param jar the jar to check
     * @param sums the SHA256SUMS file
     * @return true when a matching digest is found
     */
    public boolean verify(Path jar, Path sums) {
        if (jar == null
                || sums == null
                || !Files.isRegularFile(jar)
                || !Files.isRegularFile(sums)) {
            return false;
        }
        String expected = sha256(jar);
        List<String> lines = readLines(sums);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int split = line.indexOf("  ");
            if (split < 0) {
                split = line.indexOf(' ');
            }
            if (split < 0) {
                continue;
            }
            if (line.substring(0, split).trim().equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies a staged version on start. The running jar is backed up first. A staged jar that
     * fails verification is discarded without touching the running jar.
     *
     * @param out progress output
     * @return true when a swap was performed
     */
    public boolean applyPending(PrintStream out) {
        String version = readMarker(PENDING);
        if (version == null) {
            return false;
        }
        Path stageDir = updateRoot.resolve(version);
        Path staged = stageDir.resolve(JAR_NAME);
        Path sums = stageDir.resolve(SUMS);
        if (!Files.isRegularFile(staged) || !verify(staged, sums)) {
            out.println(
                    "jahaziel: staged update " + version + " failed verification; discarding it");
            deleteQuietly(updateRoot.resolve(PENDING));
            return false;
        }
        Path live = jarDir.resolve(JAR_NAME);
        String oldVersion = "previous";
        if (Files.isRegularFile(live)) {
            oldVersion = safeVersion(Cli.jarVersion(live));
            Path rollback = updateRoot.resolve(ROLLBACK).resolve(oldVersion);
            try {
                Files.createDirectories(rollback);
                Files.copy(live, rollback.resolve(JAR_NAME), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                out.println("jahaziel: cannot back up current jar: " + failure.getMessage());
                return false;
            }
        }
        try {
            Files.copy(staged, live, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            out.println("jahaziel: cannot swap jar: " + failure.getMessage());
            return false;
        }
        writeMarkerQuietly(APPLIED, oldVersion);
        deleteQuietly(updateRoot.resolve(PENDING));
        out.println(
                "jahaziel: applied staged update " + version + " (previous: " + oldVersion + ")");
        return true;
    }

    /**
     * Restores the jar kept before the last applied update.
     *
     * @param out progress output
     * @return true when the previous jar was restored
     */
    public boolean rollback(PrintStream out) {
        String oldVersion = readMarker(APPLIED);
        if (oldVersion == null) {
            out.println("jahaziel: no applied update to roll back");
            return false;
        }
        Path previous = updateRoot.resolve(ROLLBACK).resolve(oldVersion).resolve(JAR_NAME);
        if (!Files.isRegularFile(previous)) {
            out.println("jahaziel: previous jar is missing: " + previous);
            return false;
        }
        try {
            Files.copy(previous, jarDir.resolve(JAR_NAME), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            out.println("jahaziel: rollback failed: " + failure.getMessage());
            return false;
        }
        deleteQuietly(updateRoot.resolve(APPLIED));
        out.println("jahaziel: rolled back to " + oldVersion);
        return true;
    }

    /** Returns a one-line status of staged and applied updates. */
    public String status() {
        StringBuilder out = new StringBuilder("update root: ").append(updateRoot);
        String pending = readMarker(PENDING);
        String applied = readMarker(APPLIED);
        out.append("\npending: ").append(pending == null ? "none" : pending);
        out.append("\napplied rollback target: ").append(applied == null ? "none" : applied);
        List<String> versions = new ArrayList<String>();
        if (Files.isDirectory(updateRoot)) {
            String[] entries = updateRoot.toFile().list();
            if (entries != null) {
                for (int i = 0; i < entries.length; i++) {
                    if (Files.isDirectory(updateRoot.resolve(entries[i]))
                            && !ROLLBACK.equals(entries[i])) {
                        versions.add(entries[i]);
                    }
                }
            }
        }
        java.util.Collections.sort(versions);
        out.append("\nstaged: ").append(versions.isEmpty() ? "none" : String.join(", ", versions));
        return out.toString();
    }

    /** Returns the staged version marker, or null when none is pending. */
    public String pendingVersion() {
        return readMarker(PENDING);
    }

    private static String safeVersion(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty() || !candidate.matches("[A-Za-z0-9._-]+")) {
            return "unknown";
        }
        return candidate;
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            digest.update(bytes, 0, bytes.length);
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot hash " + file, failure);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            String part = Integer.toHexString(bytes[i] & 0xFF);
            if (part.length() == 1) {
                out.append('0');
            }
            out.append(part);
        }
        return out.toString();
    }

    private static List<String> readLines(Path file) {
        List<String> lines = new ArrayList<String>();
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String[] parts = text.split("\\R", -1);
            for (int i = 0; i < parts.length; i++) {
                lines.add(parts[i]);
            }
        } catch (IOException failure) {
            return lines;
        }
        return lines;
    }

    private void writeGeneratedSums(Path staged, Path sumsFile) throws IOException {
        String body = sha256(staged) + "  " + JAR_NAME + "\n";
        Files.write(sumsFile, body.getBytes(StandardCharsets.UTF_8));
    }

    private void writeMarker(String name, String value) {
        try {
            Files.createDirectories(updateRoot);
            Files.write(updateRoot.resolve(name), value.getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("cannot write update marker " + name, failure);
        }
    }

    private void writeMarkerQuietly(String name, String value) {
        try {
            writeMarker(name, value);
        } catch (IllegalStateException ignored) {
            // The swap already happened; a missing rollback marker is reported by status().
        }
    }

    private String readMarker(String name) {
        Path marker = updateRoot.resolve(name);
        if (!Files.isRegularFile(marker)) {
            return null;
        }
        try {
            String value = new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? null : value;
        } catch (IOException failure) {
            return null;
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best effort: stale markers are harmless.
        }
    }
}
