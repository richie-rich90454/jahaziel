/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Durable, owner-only writes for small JSON and key files used by the auth stores. */
public final class AtomicJsonFiles {
    private AtomicJsonFiles() {}

    /** Writes via a staged sibling, fsyncs it, then swaps it into place atomically. */
    public static void writeAtomically(Path path, byte[] content) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        Path staged = Files.createTempFile(parent, path.getFileName().toString() + ".", ".tmp");
        try {
            try (FileChannel channel =
                    FileChannel.open(
                            staged,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            restrict(staged);
            try {
                Files.move(
                        staged,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException fallback) {
                Files.move(staged, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(staged);
            throw failure;
        }
    }

    /** Moves an unreadable file aside so a fresh store can be loaded instead of failing boot. */
    public static void quarantine(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Files.move(
                    path,
                    path.resolveSibling(path.getFileName().toString() + ".corrupt"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            // shortcut: best effort only; the caller still loads empty. Upgrade if silent loss is
            // unacceptable.
        }
    }

    private static void restrict(Path staged) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    staged,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException onWindows) {
            File file = staged.toFile();
            file.setReadable(false, false);
            file.setReadable(true, true);
            file.setWritable(false, false);
            file.setWritable(true, true);
        }
    }
}
