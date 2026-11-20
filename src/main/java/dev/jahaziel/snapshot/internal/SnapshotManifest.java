/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Measures a snapshot tree and creates its deterministic SHA-256 manifest digest. */
final class SnapshotManifest {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long FILESYSTEM_BLOCK_BYTES = 512;

    private SnapshotManifest() {}

    /**
     * Measures regular-file and symbolic-link content, allocated blocks, and a stable tree digest.
     * Symlinks are recorded as links and never followed. When the file system does not expose Unix
     * allocation blocks, file content size is used as the physical-size estimate. Unix block counts
     * multiplied by 512 are a conservative upper bound: sparse files can be reported larger than
     * the space they actually consume.
     *
     * @param root the snapshot directory
     * @return measured sizes and the manifest digest
     */
    static Measurement measure(Path root) {
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("snapshot directory is unavailable");
        }
        Accumulator totals = new Accumulator();
        try {
            visit(root, root, totals.manifest, totals);
            return new Measurement(
                    totals.logicalBytes,
                    totals.physicalBytes,
                    HexFormat.of().formatHex(totals.manifest.digest()));
        } catch (IOException failure) {
            throw new IllegalStateException("cannot measure snapshot contents", failure);
        }
    }

    private static void visit(Path root, Path path, MessageDigest manifest, Accumulator totals)
            throws IOException {
        BasicFileAttributes attributes =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        String relative = normalize(root.relativize(path));
        byte type = typeOf(attributes);
        long logicalSize = 0;
        byte[] contentHash = digest().digest();

        if (attributes.isRegularFile()) {
            FileMeasurement file = digestFile(path);
            logicalSize = file.size();
            contentHash = file.sha256();
            totals.logicalBytes = Math.addExact(totals.logicalBytes, logicalSize);
        } else if (attributes.isSymbolicLink()) {
            byte[] target =
                    Files.readSymbolicLink(path).toString().getBytes(StandardCharsets.UTF_8);
            logicalSize = target.length;
            contentHash = digest().digest(target);
            totals.logicalBytes = Math.addExact(totals.logicalBytes, logicalSize);
        }

        totals.physicalBytes =
                Math.addExact(totals.physicalBytes, allocatedBytes(path, logicalSize));
        updateManifest(manifest, type, relative, logicalSize, contentHash);

        if (attributes.isDirectory()) {
            List<Path> children = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    children.add(child);
                }
            }
            children.sort(
                    new Comparator<Path>() {
                        @Override
                        public int compare(Path left, Path right) {
                            return left.getFileName()
                                    .toString()
                                    .compareTo(right.getFileName().toString());
                        }
                    });
            for (Path child : children) {
                visit(root, child, manifest, totals);
            }
        }
    }

    private static FileMeasurement digestFile(Path file) throws IOException {
        MessageDigest digest = digest();
        long size = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input =
                java.nio.channels.Channels.newInputStream(
                        Files.newByteChannel(
                                file,
                                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)))) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                digest.update(buffer, 0, count);
                size = Math.addExact(size, count);
            }
        }
        return new FileMeasurement(size, digest.digest());
    }

    private static long allocatedBytes(Path path, long logicalSize) throws IOException {
        try {
            Object blocks = Files.getAttribute(path, "unix:blocks", LinkOption.NOFOLLOW_LINKS);
            return Math.multiplyExact(((Number) blocks).longValue(), FILESYSTEM_BLOCK_BYTES);
        } catch (UnsupportedOperationException | IllegalArgumentException unavailable) {
            return logicalSize;
        }
    }

    private static byte typeOf(BasicFileAttributes attributes) {
        if (attributes.isDirectory()) {
            return (byte) 'd';
        }
        if (attributes.isRegularFile()) {
            return (byte) 'f';
        }
        if (attributes.isSymbolicLink()) {
            return (byte) 'l';
        }
        return (byte) 'o';
    }

    private static void updateManifest(
            MessageDigest manifest,
            byte type,
            String relativePath,
            long logicalSize,
            byte[] contentHash) {
        byte[] name = relativePath.getBytes(StandardCharsets.UTF_8);
        manifest.update(type);
        manifest.update(ByteBuffer.allocate(Integer.BYTES).putInt(name.length).array());
        manifest.update(name);
        manifest.update(ByteBuffer.allocate(Long.BYTES).putLong(logicalSize).array());
        manifest.update(contentHash);
    }

    private static String normalize(Path relative) {
        return relative.toString().replace('\\', '/');
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Measured metadata for one immutable snapshot tree. */
    static final class Measurement {
        private final long logicalBytes;
        private final long physicalBytes;
        private final String sha256;

        Measurement(long logicalBytes, long physicalBytes, String sha256) {
            if (logicalBytes < 0 || physicalBytes < 0) {
                throw new IllegalArgumentException("snapshot sizes must not be negative");
            }
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("sha256 must be a lowercase SHA-256 digest");
            }
            this.logicalBytes = logicalBytes;
            this.physicalBytes = physicalBytes;
            this.sha256 = sha256;
        }

        long logicalBytes() {
            return logicalBytes;
        }

        long physicalBytes() {
            return physicalBytes;
        }

        String sha256() {
            return sha256;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Measurement)) {
                return false;
            }
            Measurement that = (Measurement) other;
            return logicalBytes == that.logicalBytes
                    && physicalBytes == that.physicalBytes
                    && sha256.equals(that.sha256);
        }

        @Override
        public int hashCode() {
            return Objects.hash(logicalBytes, physicalBytes, sha256);
        }

        @Override
        public String toString() {
            return "Measurement[logicalBytes="
                    + logicalBytes
                    + ", physicalBytes="
                    + physicalBytes
                    + ", sha256="
                    + sha256
                    + "]";
        }
    }

    private static final class FileMeasurement {
        private final long size;
        private final byte[] sha256;

        FileMeasurement(long size, byte[] sha256) {
            this.size = size;
            this.sha256 = sha256;
        }

        long size() {
            return size;
        }

        byte[] sha256() {
            return sha256;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FileMeasurement)) {
                return false;
            }
            FileMeasurement that = (FileMeasurement) other;
            return size == that.size && Arrays.equals(sha256, that.sha256);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(size) + Arrays.hashCode(sha256);
        }

        @Override
        public String toString() {
            return "FileMeasurement[size=" + size + "]";
        }
    }

    private static final class Accumulator {
        private long logicalBytes;
        private long physicalBytes;
        private final MessageDigest manifest = digest();
    }
}
